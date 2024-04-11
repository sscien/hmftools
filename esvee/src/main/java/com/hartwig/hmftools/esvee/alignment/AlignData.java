package com.hartwig.hmftools.esvee.alignment;

import static java.lang.Math.max;
import static java.lang.String.format;

import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.NEG_ORIENT;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.POS_ORIENT;

import static htsjdk.samtools.CigarOperator.M;
import static htsjdk.samtools.CigarOperator.S;
import static htsjdk.samtools.SAMFlag.READ_REVERSE_STRAND;

import java.util.List;
import java.util.StringJoiner;

import com.hartwig.hmftools.common.bam.CigarUtils;
import com.hartwig.hmftools.common.bam.SamRecordUtils;
import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.region.ChrBaseRegion;

import org.broadinstitute.hellbender.utils.bwa.BwaMemAlignment;

import htsjdk.samtools.CigarElement;

public class AlignData
{
    public final ChrBaseRegion RefLocation;
    public final int SequenceStart;
    public final int SequenceEnd;
    public final int MapQual;
    public final int NMatches;
    public final int Score;
    public final int Flags;
    public final String Cigar;
    public final String XaTag;
    public final String MdTag;

    private final List<CigarElement> mCigarElements;
    private final int mSoftClipLeft;
    private final int mSoftClipRight;

    private final byte mOrientation;
    private final int mAlignedBases;

    private int mAdjustedSequenceStart;
    private int mAdjustedSequenceEnd;

    public AlignData(
            final ChrBaseRegion refLocation, final int sequenceStart, final int sequenceEnd, final int mapQual,
            final int score, final int flags, final String cigar, final int nMatches, final String xaTag, final String mdTag)
    {
        RefLocation = refLocation;
        SequenceStart = sequenceStart;
        SequenceEnd = sequenceEnd;
        MapQual = mapQual;
        NMatches = nMatches;
        Score = score;
        Flags = flags;

        Cigar = cigar;
        XaTag = xaTag;
        MdTag = mdTag;

        mCigarElements = CigarUtils.cigarElementsFromStr(cigar);

        mSoftClipLeft = mCigarElements.get(0).getOperator() == S ? mCigarElements.get(0).getLength() : 0;
        int lastIndex = mCigarElements.size() - 1;
        mSoftClipRight = mCigarElements.get(lastIndex).getOperator() == S ? mCigarElements.get(lastIndex).getLength() : 0;

        mOrientation = SamRecordUtils.isFlagSet(Flags, READ_REVERSE_STRAND) ? NEG_ORIENT : POS_ORIENT;
        mAlignedBases = mCigarElements.stream().filter(x -> x.getOperator() == M).mapToInt(x -> x.getLength()).sum();

        mAdjustedSequenceStart = sequenceStart;
        mAdjustedSequenceEnd = sequenceEnd;
    }

    public byte orientation()
    {
        return mOrientation;
    }

    public int maxSoftClipLength() { return max(mSoftClipLeft, mSoftClipRight); }
    public int leftSoftClipLength() { return mSoftClipLeft; }
    public int rightSoftClipLength() { return mSoftClipRight; }
    public int alignedBases() { return mAlignedBases; }

    public void setAdjustedSequenceCoords(final int fullSequenceLength)
    {
        if(mOrientation == POS_ORIENT)
            return;

        mAdjustedSequenceStart = fullSequenceLength - SequenceStart;
        mAdjustedSequenceEnd = fullSequenceLength - SequenceEnd;
    }

    public int adjustedSequenceStart() { return mAdjustedSequenceStart; }
    public int adjustedSequenceEnd() { return mAdjustedSequenceEnd; }

    public static AlignData from(final BwaMemAlignment alignment, final RefGenomeVersion refGenomeVersion)
    {
        int chrIndex = alignment.getRefId();

        if(chrIndex < 0 || chrIndex >= HumanChromosome.values().length)
            return null;

        String chromosome = refGenomeVersion.versionedChromosome(HumanChromosome.values()[chrIndex].toString());

        return new AlignData(
                new ChrBaseRegion(chromosome, alignment.getRefStart(), alignment.getRefEnd()),
                alignment.getSeqStart(), alignment.getSeqEnd(), alignment.getMapQual(), alignment.getAlignerScore(),
                alignment.getSamFlag(), alignment.getCigar(), alignment.getNMismatches(), alignment.getXATag(), alignment.getMDTag());
    }

    public String altAlignmentStr()
    {
        // in form expected by Linx and other downstream components: 4:9973661|-|26S37M11S|19
        // make common class for this
        StringJoiner sj = new StringJoiner("|");
        sj.add(RefLocation.Chromosome);
        sj.add(String.valueOf(RefLocation.start()));
        sj.add(orientation() == POS_ORIENT ? "+" : "-");
        sj.add(Cigar);
        sj.add(String.valueOf(MapQual));
        return sj.toString();
    }

    public String toString()
    {
        return format("%s %s seq(%d-%d) score(%d) mq(%d) flags(%d)",
                RefLocation, Cigar, SequenceStart, SequenceEnd, Score, MapQual, Flags);
    }
}