package com.hartwig.hmftools.sieve.annotate;

import static com.hartwig.hmftools.sieve.annotate.Util.isNotProperReadPair;
import static com.hartwig.hmftools.sieve.annotate.Util.isSoftClipped;

import htsjdk.samtools.SAMRecord;

public class AnnotateStatistics
{
    public static final String TSV_HEADER =
            "PrimaryReadCount\tPrimarySoftClippedCount\tPrimaryImproperPairCount\tPrimarySoftClippedANDImproperPairCount\tSupplementaryCount";

    private long mPrimaryReadCount;
    private long mPrimarySoftClippedCount;
    private long mPrimaryImproperPairCount;
    private long mPrimarySoftClippedAndImproperPairCount;
    private long mSupplementaryCount;

    public AnnotateStatistics()
    {
        mPrimaryReadCount = 0;
        mPrimarySoftClippedCount = 0;
        mPrimaryImproperPairCount = 0;
        mPrimarySoftClippedAndImproperPairCount = 0;
        mSupplementaryCount = 0;
    }

    public void matchedRead(final SAMRecord read)
    {
        if(read.getSupplementaryAlignmentFlag())
        {
            mSupplementaryCount++;
            return;
        }

        mPrimaryReadCount++;

        boolean softClipped = isSoftClipped(read);
        boolean improperPair = isNotProperReadPair(read);

        if(softClipped && improperPair)
        {
            mPrimarySoftClippedAndImproperPairCount++;
        }

        if(softClipped)
        {
            mPrimarySoftClippedCount++;
        }

        if(improperPair)
        {
            mPrimaryImproperPairCount++;
        }
    }

    public String getTSVFragment()
    {
        return String.valueOf(mPrimaryReadCount) + '\t' + mPrimarySoftClippedCount + '\t' + mPrimaryImproperPairCount + '\t'
                + mPrimarySoftClippedAndImproperPairCount + '\t' + mSupplementaryCount;
    }

    public long getPrimaryReadCount()
    {
        return mPrimaryReadCount;
    }

    public long getPrimarySoftClippedCount()
    {
        return mPrimarySoftClippedCount;
    }

    public long getPrimaryImproperPairCount()
    {
        return mPrimaryImproperPairCount;
    }

    public long getPrimarySoftClippedAndImproperPairCount()
    {
        return mPrimarySoftClippedAndImproperPairCount;
    }

    public long getSupplementaryCount()
    {
        return mSupplementaryCount;
    }
}
