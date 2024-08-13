package com.hartwig.hmftools.esvee.caller;

import static java.lang.Math.max;
import static java.lang.Math.sqrt;

import static com.hartwig.hmftools.common.sv.LineElements.LINE_POLY_AT_TEST_LEN;
import static com.hartwig.hmftools.common.sv.StructuralVariantType.DEL;
import static com.hartwig.hmftools.common.sv.StructuralVariantType.DUP;
import static com.hartwig.hmftools.common.sv.StructuralVariantType.INS;
import static com.hartwig.hmftools.common.sv.SvVcfTags.ASM_LINKS;
import static com.hartwig.hmftools.common.sv.SvVcfTags.STRAND_BIAS;
import static com.hartwig.hmftools.esvee.AssemblyConfig.SV_LOGGER;
import static com.hartwig.hmftools.esvee.assembly.read.ReadUtils.findLineSequenceBase;
import static com.hartwig.hmftools.esvee.assembly.types.RepeatInfo.calcTrimmedBaseLength;
import static com.hartwig.hmftools.esvee.caller.FilterConstants.MIN_TRIMMED_ANCHOR_LENGTH;
import static com.hartwig.hmftools.esvee.common.FilterType.MIN_ANCHOR_LENGTH;
import static com.hartwig.hmftools.esvee.common.FilterType.MIN_LENGTH;
import static com.hartwig.hmftools.esvee.common.FilterType.MIN_QUALITY;
import static com.hartwig.hmftools.esvee.common.FilterType.MIN_AF;
import static com.hartwig.hmftools.esvee.common.FilterType.MIN_SUPPORT;
import static com.hartwig.hmftools.esvee.common.FilterType.SGL;
import static com.hartwig.hmftools.esvee.common.FilterType.SHORT_FRAG_LENGTH;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.genome.region.Orientation;
import com.hartwig.hmftools.esvee.assembly.types.RepeatInfo;
import com.hartwig.hmftools.esvee.common.FilterType;
import com.hartwig.hmftools.esvee.common.FragmentLengthBounds;

import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;

public class VariantFilters
{
    private final FilterConstants mFilterConstants;
    private final FragmentLengthBounds mFragmentLengthBounds;

    public VariantFilters(final FilterConstants filterConstants, final FragmentLengthBounds fragmentLengthBounds)
    {
        mFilterConstants = filterConstants;
        mFragmentLengthBounds = fragmentLengthBounds;
    }

    public void applyFilters(final Variant var)
    {
        if(mFilterConstants.FilterSGLs && var.isSgl())
            var.addFilter(SGL);

        if(belowMinAf(var))
            var.addFilter(MIN_AF);

        if(belowMinSupport(var))
            var.addFilter(MIN_SUPPORT);

        if(belowMinQuality(var))
            var.addFilter(MIN_QUALITY);

        if(hasStrandBias(var))
            var.addFilter(FilterType.STRAND_BIAS);

        if(belowMinLength(var))
            var.addFilter(MIN_LENGTH);

        if(belowMinAnchorLength(var))
            var.addFilter(MIN_ANCHOR_LENGTH);

        if(belowMinFragmentLength(var))
            var.addFilter(SHORT_FRAG_LENGTH);
    }

    private boolean belowMinSupport(final Variant var)
    {
        double supportThreshold = var.isHotspot() ? mFilterConstants.MinSupportHotspot :
                (var.isSgl() ? mFilterConstants.MinSupportSgl : mFilterConstants.MinSupportJunction);

        Breakend breakend = var.breakendStart();

        for(Genotype genotype : breakend.Context.getGenotypes())
        {
            int fragmentCount = breakend.fragmentCount(genotype);

            if(fragmentCount >= supportThreshold)
                return false;
        }

        return true;
    }

    private boolean belowMinAf(final Variant var)
    {
        double afThreshold = var.isHotspot() ? mFilterConstants.MinAfHotspot :
                (var.isSgl() ? mFilterConstants.MinAfSgl : mFilterConstants.MinAfJunction);

        Breakend breakend = var.breakendStart();

        for(Genotype genotype : breakend.Context.getGenotypes())
        {
            double af = breakend.calcAllelicFrequency(genotype);

            if(af >= afThreshold)
                return false;
        }

        return true;
    }

    private boolean belowMinQuality(final Variant var)
    {
        double qualThreshold = mFilterConstants.MinQual;

        Breakend breakend = var.breakendStart();
        Breakend otherBreakend = var.breakendEnd();

        if(mFilterConstants.LowQualRegion.containsPosition(breakend.Chromosome, breakend.Position))
            qualThreshold *= 0.5;
        else if(otherBreakend != null && mFilterConstants.LowQualRegion.containsPosition(otherBreakend.Chromosome, otherBreakend.Position))
            qualThreshold *= 0.5;

        return var.qual() < qualThreshold;
    }

    private boolean belowMinLength(final Variant var)
    {
        if(var.type() == DEL || var.type() == DUP || var.type() == INS)
            return var.adjustedLength() < mFilterConstants.MinLength;

        return false;
    }

    private boolean belowMinAnchorLength(final Variant var)
    {
        // skip for chained breakends
        if(belowMinAnchorLength(var.breakendStart()))
            return false;

        if(var.isSgl())
        {
            byte[] insertSequence = var.insertSequence().getBytes();

            // skip if a line site
            if(isLineInsertion(insertSequence, var.breakendStart().Orient))
                return false;

            List<RepeatInfo> repeats = RepeatInfo.findRepeats(insertSequence);
            int trimmedSequenceLength = calcTrimmedBaseLength(0, insertSequence.length - 1, repeats);

            return trimmedSequenceLength < MIN_TRIMMED_ANCHOR_LENGTH;
        }
        else
        {
            return belowMinAnchorLength(var.breakendEnd());
        }
    }

    private boolean belowMinAnchorLength(final Breakend breakend)
    {
        if(breakend.Context.hasAttribute(ASM_LINKS))
            return false;

        return breakend.anchorLength() < MIN_TRIMMED_ANCHOR_LENGTH;
    }

    private static boolean isLineInsertion(final byte[] insertSequence, final Orientation orientation)
    {
        int indexStart, indexEnd;

        if(orientation.isForward())
        {
            indexStart = 0;
            indexEnd = LINE_POLY_AT_TEST_LEN - 1;
        }
        else
        {
            indexEnd = insertSequence.length - 1;
            indexStart = indexEnd - LINE_POLY_AT_TEST_LEN + 1;
        }

        return findLineSequenceBase(insertSequence, indexStart, indexEnd) != null;
    }

    private boolean belowMinFragmentLength(final Variant var)
    {
        int svAvgLength = var.averageFragmentLength();

        if(svAvgLength == 0) // for now treat is this as a pass
            return false;

        int medianLength = mFragmentLengthBounds.Median;
        double stdDeviation = mFragmentLengthBounds.StdDeviation;

        int totalSplitFrags = var.splitFragmentCount();
        double lowerLengthLimit = medianLength - (mFilterConstants.MinAvgFragFactor * stdDeviation / sqrt(totalSplitFrags));

        return svAvgLength < lowerLengthLimit;
    }

    private boolean hasStrandBias(final Variant var)
    {
        /*
        private boolean singleStrandBias(final Breakend breakend)
        {
            if(!breakend.isSgl() || breakend.IsLineInsertion)
                return false;

            if(mFilterConstants.LowQualRegion.containsPosition(breakend.Chromosome, breakend.Position))
                return false;

            double strandBias = calcStrandBias(breakend.Context);
            return strandBias < SGL_MIN_STRAND_BIAS || strandBias > SGL_MAX_STRAND_BIAS;
        }

        if(sv.isShortLocal())
        {
            double strandBias = breakend.Context.getAttributeAsDouble(STRAND_BIAS, 0.5);
            return max(strandBias, 1 - strandBias) > MAX_STRAND_BIAS;
        }
        */

        return false;
    }

    private static double calcStrandBias(final VariantContext variantContext)
    {
        double strandBias = variantContext.getAttributeAsDouble(STRAND_BIAS, 0.5);
        return max(strandBias, 1 - strandBias);
    }

    public static void logFilterTypeCounts(final List<Variant> variantList)
    {
        Map<FilterType,Integer> filterCounts = Maps.newHashMap();

        for(Variant var : variantList)
        {
            for(FilterType filterType : var.filters())
            {
                int count = filterCounts.getOrDefault(filterType, 0);
                filterCounts.put(filterType, count + 1);
            }
        }

        for(Map.Entry<FilterType,Integer> entry : filterCounts.entrySet())
        {
            SV_LOGGER.debug("variant filter {}: count({})", entry.getKey(), entry.getValue());
        }
    }
}
