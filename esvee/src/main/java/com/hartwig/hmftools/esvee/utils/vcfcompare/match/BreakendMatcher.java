package com.hartwig.hmftools.esvee.utils.vcfcompare.match;

import static java.lang.Math.abs;
import static java.lang.Math.max;

import static com.hartwig.hmftools.common.sv.SvVcfTags.CIPOS;
import static com.hartwig.hmftools.common.sv.SvVcfTags.HOMSEQ;
import static com.hartwig.hmftools.common.sv.SvVcfTags.IHOMPOS;
import static com.hartwig.hmftools.common.sv.SvVcfTags.SVTYPE;
import static com.hartwig.hmftools.common.sv.SvVcfTags.TOTAL_FRAGS;
import static com.hartwig.hmftools.common.utils.file.FileDelimiters.TSV_DELIM;
import static com.hartwig.hmftools.common.utils.file.FileDelimiters.TSV_EXTENSION;
import static com.hartwig.hmftools.esvee.AssemblyConfig.SV_LOGGER;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.utils.file.FileWriterUtils;
import com.hartwig.hmftools.esvee.utils.vcfcompare.CompareConfig;
import com.hartwig.hmftools.esvee.utils.vcfcompare.common.VariantBreakend;

public class BreakendMatcher
{
    public final String mSampleId;
    public final String mOutputDir;
    public final String mOutputId;

    public final RefGenomeVersion mRefGenomeVersion;
    public final boolean mShowNonPass;

    private final BufferedWriter mWriter;

    private final List<BreakendMatch> mBreakendMatches = new ArrayList<>();

    public BreakendMatcher(
            String sampleId, String outputDir, String outputId,
            RefGenomeVersion refGenomeVersion, boolean showNonPass
    )
    {
        mSampleId = sampleId;
        mOutputDir = outputDir;
        mOutputId = outputId;

        mRefGenomeVersion = refGenomeVersion;
        mShowNonPass = showNonPass;

        mWriter = initialiseWriter();
    }

    public BreakendMatcher(CompareConfig config)
    {
        mSampleId = config.SampleId;
        mOutputDir = config.OutputDir;
        mOutputId = config.OutputId;
        mRefGenomeVersion = config.RefGenomeVersion;
        mShowNonPass = config.ShowNonPass;

        mWriter = initialiseWriter();
    }

    public void matchBreakends(
            Map<String, List<VariantBreakend>> oldChrBreakendMap,
            Map<String,List<VariantBreakend>> newChrBreakendMap,
            MatchType matchType,
            boolean checkOtherSide
    )
    {
        MatchFunctions.MatchFunction breakendMatcher = MatchType.getMatcher(matchType);

        int matchedCount = 0;

        for(HumanChromosome chromosome : HumanChromosome.values())
        {
            String chrStr = mRefGenomeVersion.versionedChromosome(chromosome.toString());

            List<VariantBreakend> oldBreakends = oldChrBreakendMap.get(chrStr);
            List<VariantBreakend> newBreakends = newChrBreakendMap.get(chrStr);

            if(oldBreakends == null || newBreakends == null)
                continue;

            for(VariantBreakend oldBreakend : oldBreakends)
            {
                for(VariantBreakend newBreakend : newBreakends)
                {
                    if(oldBreakend.hasMatchedBreakend() || newBreakend.hasMatchedBreakend())
                        continue;

                    boolean hasMatch = breakendMatcher.match(oldBreakend, newBreakend, checkOtherSide);

                    if(hasMatch)
                    {
                        oldBreakend.MatchedBreakend = newBreakend;
                        newBreakend.MatchedBreakend = oldBreakend;

                        if(mShowNonPass || oldBreakend.isPassVariant() || newBreakend.isPassVariant())
                            mBreakendMatches.add(new BreakendMatch(oldBreakend, newBreakend, matchType));

                        matchedCount++;
                    }
                }
            }
        }

        if(matchedCount > 0)
        {
            SV_LOGGER.debug("  Found {} variants with match type: {}", matchedCount, matchType);
        }
    }

    public void matchBreakends(
            Map<String,List<VariantBreakend>> oldChrBreakendMap,
            Map<String,List<VariantBreakend>> newChrBreakendMap,
            boolean checkOtherSide
    )
    {
        matchBreakends(oldChrBreakendMap, newChrBreakendMap, MatchType.EXACT_MATCH, checkOtherSide);
        matchBreakends(oldChrBreakendMap, newChrBreakendMap, MatchType.COORDS_ONLY, checkOtherSide);
        matchBreakends(oldChrBreakendMap, newChrBreakendMap, MatchType.APPROX_MATCH, checkOtherSide);
    }

    public void matchBreakends(
            Map<String,List<VariantBreakend>> oldChrBreakendMap,
            Map<String,List<VariantBreakend>> newChrBreakendMap
    )
    {
        matchBreakends(oldChrBreakendMap, newChrBreakendMap, true);
    }

    private void gatherUnmatchedVariants(Map<String,List<VariantBreakend>> chrBreakendMap, boolean isOld)
    {
        int unmatchedVariantsCount = 0;

        for(List<VariantBreakend> breakends : chrBreakendMap.values())
        {
            if(breakends == null)
                continue;

            for(VariantBreakend breakend : breakends)
            {
                if(breakend.hasMatchedBreakend())
                    continue;

                if(mShowNonPass || breakend.isPassVariant())
                {
                    if(isOld)
                        mBreakendMatches.add(new BreakendMatch(breakend, null, MatchType.NO_MATCH));
                    else
                        mBreakendMatches.add(new BreakendMatch(null, breakend, MatchType.NO_MATCH));

                    unmatchedVariantsCount++;
                }
            }
        }

        if(unmatchedVariantsCount > 0)
        {
            SV_LOGGER.debug("Writing {} unmatched variants", unmatchedVariantsCount);
        }
    }

    public void gatherUnmatchedVariants(
            Map<String,List<VariantBreakend>> oldChrBreakendMap,
            Map<String,List<VariantBreakend>> newChrBreakendMap
    )
    {
        gatherUnmatchedVariants(oldChrBreakendMap, true);
        gatherUnmatchedVariants(newChrBreakendMap, false);
    }

    private BufferedWriter initialiseWriter()
    {
        try
        {
            String fileName = mOutputDir + mSampleId + ".sv_compare";

            if(mOutputId != null)
                fileName += "." + mOutputId;

            fileName += TSV_EXTENSION;

            SV_LOGGER.info("Writing comparison file: {}", fileName);

            BufferedWriter writer = FileWriterUtils.createBufferedWriter(fileName, false);

            String header = String.join(
                    TSV_DELIM, "SampleId",
                    "OldId",       "NewId",
                    "MatchType",
                    "Diffs",
                    "OldSvCoords", "NewSvCoords",
                    "OldCoords",   "NewCoords",
                    "OldCipos",    "NewCipos",
                    "OldIhompos",  "NewIhompos",
                    "OldHomSeq",   "NewHomSeq",
                    "OldInsSeq",   "NewInsSeq",
                    "OldSvType",   "NewSvType",
                    "OldFilter",   "NewFilter",
                    "OldVcfType",  "NewVcfType",
                    "OldQual",     "NewQual",
                    "OldVF",       "NewVF",
                    "OldLineLinkCoords", "NewLineLinkCoords"
                    );

            writer.write(header);
            writer.newLine();

            return writer;
        }
        catch(IOException e)
        {
            SV_LOGGER.error("failed to initialise output file: {}", e.toString());
            return null;
        }
    }

    public void closeWriter() { FileWriterUtils.closeBufferedWriter(mWriter); }

    public void writeBreakends()
    {
        for(BreakendMatch breakendMatch : mBreakendMatches)
        {
            writeBreakend(breakendMatch.OldBreakend, breakendMatch.NewBreakend, breakendMatch.Type);
        }
    }

    private void writeBreakend(VariantBreakend oldBreakend, VariantBreakend newBreakend, MatchType matchType)
    {
        String oldId       = "";
        String oldSvCoords = "";
        String oldCoords   = "";
        String oldCipos    = "";
        String oldIhompos  = "";
        String oldHomSeq   = "";
        String oldInsSeq   = "";
        String oldSvtype   = "";
        String oldFilter   = "";
        String oldVcfType  = "";
        String oldQual     = "";
        String oldVF       = "";
        String oldLineLinkCoords = "";
        if(oldBreakend != null)
        {
            oldId = oldBreakend.Context.getID();
            oldSvCoords = oldBreakend.svCoordStr();
            oldCoords = oldBreakend.coordStr();
            oldCipos = Arrays.toString(oldBreakend.Cipos);
            oldIhompos = Arrays.toString(oldBreakend.Ihompos);
            oldHomSeq = oldBreakend.Homseq;
            oldInsSeq = oldBreakend.InsertSequence;
            oldSvtype = oldBreakend.SvType;
            oldFilter = oldBreakend.filtersStr();
            oldVcfType = oldBreakend.SourceVcfType.toString();
            oldQual = oldBreakend.qualStr();
            oldVF = oldBreakend.getExtendedAttributeAsString(mSampleId, TOTAL_FRAGS);
            oldLineLinkCoords = oldBreakend.lineLinkCoordStr();
        }

        String newId       = "";
        String newSvCoords = "";
        String newCoords   = "";
        String newCipos    = "";
        String newIhompos  = "";
        String newHomSeq   = "";
        String newInsSeq   = "";
        String newSvtype   = "";
        String newFilter   = "";
        String newVcfType  = "";
        String newQual     = "";
        String newVF       = "";
        String newLineLinkCoords = "";
        if(newBreakend != null)
        {
            newId = newBreakend.Context.getID();
            newSvCoords = newBreakend.svCoordStr();
            newCoords = newBreakend.coordStr();
            newCipos = Arrays.toString(newBreakend.Cipos);
            newIhompos = Arrays.toString(newBreakend.Ihompos);
            newHomSeq = newBreakend.Homseq;
            newInsSeq = newBreakend.InsertSequence;
            newSvtype = newBreakend.SvType;
            newFilter = newBreakend.filtersStr();
            newVcfType = newBreakend.SourceVcfType.toString();
            newQual = newBreakend.qualStr();
            newVF = newBreakend.getExtendedAttributeAsString(mSampleId, TOTAL_FRAGS);
            newLineLinkCoords = newBreakend.lineLinkCoordStr();
        }

        String diffs = "";
        if(oldBreakend != null && newBreakend != null)
        {
            diffs = String.join(",", compareBreakendAttributes(oldBreakend, newBreakend, matchType));
        }

        try
        {
            String line = String.join(
                    TSV_DELIM,
                    mSampleId,
                    oldId,      newId,
                    matchType.toString(),
                    diffs,
                    oldSvCoords, newSvCoords,
                    oldCoords,   newCoords,
                    oldCipos,    newCipos,
                    oldIhompos,  newIhompos,
                    oldHomSeq,   newHomSeq,
                    oldInsSeq,   newInsSeq,
                    oldSvtype,   newSvtype,
                    oldFilter,   newFilter,
                    oldVcfType,  newVcfType,
                    oldQual,     newQual,
                    oldVF,       newVF,
                    oldLineLinkCoords, newLineLinkCoords
            );

            mWriter.write(line);
            mWriter.newLine();
        }
        catch(IOException e)
        {
            SV_LOGGER.error("Failed to write output file: {}", e.toString());
        }
    }

    private static final String DIFF_PASS = "PASS_FILTER";
    private static final String DIFF_COORDS = "COORDS";
    private static final String DIFF_INSSEQ = "INSSEQ";

    private static boolean hasDiffWithinTolerance(double value1, double value2, double maxDiff, double maxDiffPerc)
    {
        double diff = abs(value1 - value2);
        double diffPerc = diff / max(value1, value2);
        return diff > maxDiff && diffPerc > maxDiffPerc;
    }

    private static final int DEFAULT_MAX_DIFF = 20;
    private static final double DEFAULT_MAX_DIFF_PERC = 0.2;

    // TODO: Make this a method in `BreakendMatch`
    private List<String> compareBreakendAttributes(VariantBreakend oldBreakend, VariantBreakend newBreakend, MatchType matchType)
    {
        List<String> diffSet = new ArrayList<>();

        if((oldBreakend.isPassVariant() & !newBreakend.isPassVariant()) || (!oldBreakend.isPassVariant() & newBreakend.isPassVariant()))
        {
            diffSet.add(DIFF_PASS);
        }

        if((matchType == MatchType.APPROX_MATCH ||  matchType == MatchType.COORDS_ONLY))
        {
            if(!oldBreakend.coordStr().equals(newBreakend.coordStr()))
                diffSet.add(DIFF_COORDS);

            if(!Arrays.equals(oldBreakend.Cipos, newBreakend.Cipos))
                diffSet.add(CIPOS);

            if(!Arrays.equals(oldBreakend.Ihompos, newBreakend.Ihompos))
                diffSet.add(IHOMPOS);

            if(!oldBreakend.Homseq.equals(newBreakend.Homseq))
                diffSet.add(HOMSEQ);

            if(!oldBreakend.InsertSequence.equals(newBreakend.InsertSequence))
                diffSet.add(DIFF_INSSEQ);

            if(!oldBreakend.SvType.equals(newBreakend.SvType))
                diffSet.add(SVTYPE);
        }

        if(hasDiffWithinTolerance(
                oldBreakend.getExtendedAttributeAsDouble(mSampleId, TOTAL_FRAGS),
                newBreakend.getExtendedAttributeAsDouble(mSampleId, TOTAL_FRAGS),
                DEFAULT_MAX_DIFF,
                DEFAULT_MAX_DIFF_PERC
        ))
        {
            diffSet.add(TOTAL_FRAGS);
        }

        return diffSet;
    }

    public List<BreakendMatch> getBreakendMatches(){ return mBreakendMatches; }
}
