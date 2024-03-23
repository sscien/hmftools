package com.hartwig.hmftools.esvee.prep.types;

import static java.lang.Math.abs;
import static java.lang.String.format;

import static com.hartwig.hmftools.common.samtools.CigarUtils.maxIndelLength;
import static com.hartwig.hmftools.common.samtools.SamRecordUtils.SUPPLEMENTARY_ATTRIBUTE;
import static com.hartwig.hmftools.common.samtools.SamRecordUtils.firstInPair;
import static com.hartwig.hmftools.common.samtools.SamRecordUtils.mateUnmapped;
import static com.hartwig.hmftools.common.sv.LineElements.isMobileLineElement;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_END;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_START;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.NEG_ORIENT;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.POS_ORIENT;
import static com.hartwig.hmftools.esvee.prep.PrepConstants.MIN_INDEL_SUPPORT_LENGTH;

import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.samtools.CigarUtils;
import com.hartwig.hmftools.common.samtools.SupplementaryReadData;
import com.hartwig.hmftools.esvee.types.IndelCoords;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.SAMFlag;
import htsjdk.samtools.SAMRecord;

public class PrepRead
{
    public final String Chromosome;
    public final int[] Positions;

    public String MateChromosome;
    public int MatePosStart;

    private final SAMRecord mRecord;
    private int mFragmentInsertSize;
    private final SupplementaryReadData mSupplementaryAlignment;

    private boolean mCheckedIndelCoords;
    private IndelCoords mIndelCoords;

    private int mFilters;
    private ReadType mReadType;
    private boolean mWritten;

    public static PrepRead from(final SAMRecord record) { return new PrepRead(record); }

    public static final String UNMAPPED_CHR = "-1";

    public PrepRead(final SAMRecord record)
    {
        mRecord = record;

        if(!record.getReadUnmappedFlag())
        {
            Chromosome = record.getReferenceName();
            Positions = new int[] { record.getStart(), record.getEnd() };
        }
        else
        {
            Chromosome = UNMAPPED_CHR;
            Positions = new int[] { 0, 0 };
        }

        if(!mateUnmapped(record) && record.getMateAlignmentStart() > 0)
        {
            MateChromosome = record.getMateReferenceName();
            MatePosStart = record.getMateAlignmentStart();
        }
        else
        {
            MateChromosome = UNMAPPED_CHR;
            MatePosStart = 0;
        }

        mFragmentInsertSize = abs(record.getInferredInsertSize());
        mSupplementaryAlignment = SupplementaryReadData.extractAlignment(record.getStringAttribute(SUPPLEMENTARY_ATTRIBUTE));

        mCheckedIndelCoords = false;
        mIndelCoords = null;

        mFilters = 0;
        mReadType = ReadType.NO_SUPPORT;
        mWritten = false;
    }

    public String id() { return mRecord.getReadName(); }
    public final SAMRecord record() { return mRecord; }
    public int start() { return Positions[SE_START]; }
    public int end() { return Positions[SE_END]; }

    public byte orientation() { return !isReadReversed() ? POS_ORIENT : NEG_ORIENT; }
    public byte mateOrientation() { return !hasFlag(SAMFlag.MATE_REVERSE_STRAND) ? POS_ORIENT : NEG_ORIENT; }

    public int flags() { return mRecord.getFlags(); }
    public Cigar cigar() { return mRecord.getCigar(); }
    public boolean isReadReversed() { return ( mRecord.getFlags() & SAMFlag.READ_REVERSE_STRAND.intValue()) != 0; }
    public boolean isFirstOfPair() { return firstInPair(mRecord); }
    public boolean isSupplementaryAlignment() { return (mRecord.getFlags() & SAMFlag.SUPPLEMENTARY_ALIGNMENT.intValue()) != 0; }
    public boolean isUnmapped() { return (mRecord.getFlags() & SAMFlag.READ_UNMAPPED.intValue()) != 0; }

    public boolean hasMate() { return MatePosStart > 0; }
    public boolean isMateUnmapped() { return (mRecord.getFlags() & SAMFlag.MATE_UNMAPPED.intValue()) != 0; }

    public boolean hasFlag(final SAMFlag flag) { return (mRecord.getFlags() & flag.intValue()) != 0; }

    public SupplementaryReadData supplementaryAlignment() { return mSupplementaryAlignment; }
    public boolean hasSuppAlignment() { return mSupplementaryAlignment != null && HumanChromosome.contains(mSupplementaryAlignment.Chromosome); }

    public String readBases() { return mRecord.getReadString(); }
    public byte[] baseQualities() { return mRecord.getBaseQualities(); }

    public void setFilters(int filters) { mFilters = filters; }
    public int filters() { return mFilters; }

    public void setReadType(ReadType type) { setReadType(type, false); }

    public void setReadType(ReadType type, boolean checkRank)
    {
        if(!checkRank || ReadType.rank(type) > ReadType.rank(mReadType)) // keep the highest
            mReadType = type;
    }

    public ReadType readType() { return mReadType; }

    public void setWritten() { mWritten = true; }
    public boolean written() { return mWritten; }

    public short mapQuality() { return (short)mRecord.getMappingQuality(); }

    public int fragmentInsertSize() { return mFragmentInsertSize; }

    public IndelCoords indelCoords()
    {
        if(!mCheckedIndelCoords)
        {
            int[] indelCoords = CigarUtils.findIndelCoords(start(), cigar().getCigarElements(), MIN_INDEL_SUPPORT_LENGTH);

            if(indelCoords != null)
                mIndelCoords = new IndelCoords(indelCoords[SE_START], indelCoords[SE_END], maxIndelLength(cigar().getCigarElements()));

            mCheckedIndelCoords = true;
        }

        return mIndelCoords;
    }

    public String toString()
    {
        return format("coords(%s:%d-%d) cigar(%s) mate(%s:%d) id(%s) flags(first=%s supp=%s reversed=%s) hasSupp(%s) type(%s)",
                Chromosome, start(), end(), cigar().toString(), MateChromosome, MatePosStart, id(),
                isFirstOfPair(), isSupplementaryAlignment(), isReadReversed(), mSupplementaryAlignment != null, mReadType);
    }

    public static String getSoftClippedBases(final SAMRecord record, final boolean isClippedLeft)
    {
        int scLength = isClippedLeft ? record.getCigar().getFirstCigarElement().getLength() : record.getCigar().getLastCigarElement().getLength();
        int readLength = record.getReadBases().length;
        int scStart = isClippedLeft ? 0 : readLength - scLength;
        int scEnd = isClippedLeft ? scLength : readLength;
        return record.getReadString().substring(scStart, scEnd);
    }

    public static boolean hasPolyATSoftClip(final PrepRead read, final boolean isClippedLeft)
    {
        byte orientation = isClippedLeft ? NEG_ORIENT : POS_ORIENT;
        String scBases = getSoftClippedBases(read.record(), isClippedLeft);
        return isMobileLineElement(orientation, scBases);
    }

    /*
    public static int[] findIndelCoords(final ReadRecord read, int minIndelLength)
    {
        return CigarUtils.findIndelCoords(read.start(), read.cigar().getCigarElements(), minIndelLength);
    }
    */
}
