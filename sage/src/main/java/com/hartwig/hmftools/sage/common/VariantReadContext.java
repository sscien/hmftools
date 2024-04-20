package com.hartwig.hmftools.sage.common;

import static java.lang.String.format;

import static com.hartwig.hmftools.sage.SageConstants.MIN_CORE_DISTANCE;
import static com.hartwig.hmftools.sage.common.VariantReadContextBuilder.findPositionEnd;
import static com.hartwig.hmftools.sage.common.VariantReadContextBuilder.findPositionStart;

import java.util.List;

import com.hartwig.hmftools.common.bam.CigarUtils;
import com.hartwig.hmftools.common.utils.Arrays;
import com.hartwig.hmftools.sage.evidence.ArtefactContext;
import com.hartwig.hmftools.sage.quality.UltimaQualModel;

import htsjdk.samtools.CigarElement;

public class VariantReadContext
{
    public final int AlignmentStart;
    public final int AlignmentEnd;

    public final byte[] RefBases;

    // read bases and info
    public final byte[] ReadBases;
    public final int CoreIndexStart; // in the read to the start of the core
    public final int VarReadIndex; // index in the read bases of the variant's position
    public final int CoreIndexEnd;

    public final Microhomology Homology;
    public final RepeatInfo MaxRepeat;

    public final int AltIndexLower; // the first lower base relative to the index where the ref and alt differ
    public final int AltIndexUpper;

    public final int CorePositionStart;
    public final int CorePositionEnd;

    private final SimpleVariant mVariant;
    private final List<CigarElement> mReadCigar;
    private final String mReadCigarStr;

    private final ArtefactContext mArtefactContext;
    private final UltimaQualModel mUltimaQualModel;

    public  VariantReadContext(
            final SimpleVariant variant, final int alignmentStart, final int alignmentEnd, final byte[] refBases,
            final byte[] readBases, final List<CigarElement> readCigar,
            final int coreIndexStart, final int varReadIndex, final int coreIndexEnd,
            final Microhomology homology, final RepeatInfo maxRepeat)
    {
        mVariant = variant;
        AlignmentStart = alignmentStart;
        AlignmentEnd = alignmentEnd;
        RefBases = refBases;
        ReadBases = readBases;
        CoreIndexStart = coreIndexStart;
        VarReadIndex = varReadIndex;
        CoreIndexEnd = coreIndexEnd;
        Homology = homology;
        MaxRepeat = maxRepeat;

        mReadCigar = readCigar;
        mReadCigarStr = CigarUtils.cigarStringFromElements(readCigar);

        // CLEAN-UP
        mArtefactContext = null; // ArtefactContext.buildContext(variant, readContext.indexedBases());
        mUltimaQualModel = null; // qualityCalculator.createUltimateQualModel(variant);

        AltIndexLower = VarReadIndex;

        if(mVariant.isInsert())
            AltIndexUpper = VarReadIndex + mVariant.Alt.length();
        else if(mVariant.isDelete())
            AltIndexUpper = VarReadIndex + 1;
        else
            AltIndexUpper = VarReadIndex + mVariant.Alt.length() - 1;

        CorePositionStart = findPositionStart(mVariant.Position, leftCoreLength(), AlignmentStart, mReadCigar, CoreIndexStart);
        CorePositionEnd = findPositionEnd(mVariant.Position, rightCoreLength(), AlignmentStart, mReadCigar, CoreIndexEnd);
    }

    // read context methods
    public SimpleVariant variant() { return mVariant; }
    public String ref() { return mVariant.ref(); }
    public String alt() { return mVariant.alt(); }

    public boolean hasHomology() { return Homology != null; }
    public int coreLength() { return CoreIndexEnd - CoreIndexStart + 1; }
    public int leftCoreLength() { return VarReadIndex - CoreIndexStart; }
    public int rightCoreLength() { return CoreIndexEnd - VarReadIndex; }
    public int leftFlankLength() { return CoreIndexStart; }
    public int rightFlankLength() { return ReadBases.length - CoreIndexEnd - 1; }
    public int leftLength() { return VarReadIndex; } // distance from position index to first read base
    public int rightLength() { return ReadBases.length - VarReadIndex; } // distance to last base
    public int totalLength() { return ReadBases.length; }

    public int refIndex() { return mVariant.Position - AlignmentStart; }

    public boolean isValid()
    {
        if(CoreIndexStart <= 0 || CoreIndexEnd >= ReadBases.length - 1)
            return false; // implies no flank

        int minCoreLength = mVariant.isIndel() ? MIN_CORE_DISTANCE * 2 : MIN_CORE_DISTANCE * 2 + 1;
        if(coreLength() < minCoreLength)
            return false;

        if(VarReadIndex <= CoreIndexStart || CoreIndexEnd <= VarReadIndex) // invalid var index
            return false;

        if(AltIndexLower > AltIndexUpper)
            return false;

        return true;
    }

    public String coreStr() { return new String(ReadBases, CoreIndexStart, coreLength()); }
    public String leftFlankStr() { return new String(ReadBases, 0, leftFlankLength()); }
    public String rightFlankStr() { return new String(ReadBases, CoreIndexEnd + 1, rightFlankLength()); }

    public String readBases() { return new String(ReadBases); }
    public String refBases() { return new String(RefBases); }

    public String homologyBases() { return Homology != null ? Homology.Bases : ""; }
    public int maxRepeatCount() { return MaxRepeat != null ? MaxRepeat.Count : 0; }

    public final byte[] trinucleotide()
    {
        int refIndex = refIndex();
        return Arrays.subsetArray(RefBases, refIndex - 1, refIndex + 1);
    }

    public final String trinucleotideStr() { return new String(trinucleotide()); }
    public final String readCigar() { return mReadCigarStr; }

    public ArtefactContext artefactContext() { return mArtefactContext; }
    public UltimaQualModel ultimaQualModel() { return mUltimaQualModel; }

    public String toString()
    {
        return format("%s-%s-%s %s pos(%d-%d) index(%d-%d-%d) repeat(%s) homology(%s)",
                leftFlankStr(), coreStr(), rightFlankStr(), mReadCigarStr, AlignmentStart, AlignmentEnd,
                CoreIndexStart, VarReadIndex, CoreIndexEnd, MaxRepeat != null ? MaxRepeat : "", Homology != null ? Homology : "");
    }
}
