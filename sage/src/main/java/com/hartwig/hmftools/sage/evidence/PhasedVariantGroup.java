package com.hartwig.hmftools.sage.evidence;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sin;

import java.util.List;
import java.util.StringJoiner;

import com.hartwig.hmftools.common.utils.sv.BaseRegion;

import org.apache.commons.compress.utils.Lists;

public class PhasedVariantGroup
{
    public final int Id;
    public final List<ReadContextCounter> PositiveReadCounters; // supported by the reads
    public final List<ReadContextCounter> NegativeReadCounters; // not supported by the reads

    public int ReadCount; // from uniquely supporting reads
    public double AllocatedReadCount; // allocated from subset groups

    // cache min and max variant positions from the positive read-counters, used for faster group matching
    private int mPosVariantMin;
    private int mPosVariantMax;

    private int mVariantMin;
    private int mVariantMax;
    private final List<Integer> mMergedIds;

    public PhasedVariantGroup(
            final int id, final int minVariantPos, final int maxVariantPos,
            final List<ReadContextCounter> posCounters, final List<ReadContextCounter> negCounters)
    {
        Id = id;
        PositiveReadCounters = posCounters;
        NegativeReadCounters = negCounters;

        mPosVariantMin = minVariantPos;
        mPosVariantMax = maxVariantPos;

        mVariantMin = minVariantPos;
        mVariantMax = maxVariantPos;

        if(!negCounters.isEmpty())
        {
            mVariantMin = min(mVariantMin, negCounters.get(0).position());
            mVariantMax = max(mVariantMax, negCounters.get(negCounters.size() - 1).position());
        }

        ReadCount = 1;
        AllocatedReadCount = 0;
        mMergedIds = Lists.newArrayList();
    }

    public int posVariantMin() { return mPosVariantMin; }
    public int posVariantMax() { return mPosVariantMax; }
    public int variantMin() { return mVariantMin; }
    public int variantMax() { return mVariantMax; }

    public boolean positionsOverlap(final PhasedVariantGroup other)
    {
        return BaseRegion.positionsOverlap(mPosVariantMin, mPosVariantMax, other.posVariantMin(), other.posVariantMax());
    }

    public boolean exactMatch(
            final int minVariantPos, final int maxVariantPos,
            final List<ReadContextCounter> posCounters, final List<ReadContextCounter> negCounters)
    {
        // positives need to match exactly, negatives don't
        if(minVariantPos != mPosVariantMin || maxVariantPos != mPosVariantMax)
            return false;

        if(PositiveReadCounters.size() != posCounters.size() || NegativeReadCounters.size() != negCounters.size())
            return false;

        if(PositiveReadCounters.stream().anyMatch(x -> !posCounters.contains(x)))
            return false;

        if(NegativeReadCounters.stream().anyMatch(x -> !negCounters.contains(x)))
            return false;

        return true;
    }

    public boolean positivesMatch(final PhasedVariantGroup other)
    {
        // positives need to match exactly, negatives don't
        if(other.posVariantMin() != mPosVariantMin || other.posVariantMax() != mPosVariantMax)
            return false;

        if(PositiveReadCounters.size() != other.PositiveReadCounters.size())
            return false;

        if(PositiveReadCounters.stream().anyMatch(x -> !other.PositiveReadCounters.contains(x)))
            return false;

        return true;
    }

    public boolean isSubsetOf(final PhasedVariantGroup other)
    {
        // returns true if this group is a subset of 'other' is a su
        if(other.PositiveReadCounters.size() < PositiveReadCounters.size())
            return false;

        if(!PositiveReadCounters.stream().allMatch(x -> other.PositiveReadCounters.contains(x)))
            return false;

        // cannot have contradictory negatives
        if(hasAnyOverlap(other.PositiveReadCounters, NegativeReadCounters) || hasAnyOverlap(PositiveReadCounters, other.NegativeReadCounters))
            return false;

        return true;
    }

    public boolean populateCommon(final PhasedVariantGroup other, final List<ReadContextCounter> posCounters, final List<ReadContextCounter> negCounters)
    {
        // cannot have contradictory negatives
        if(hasAnyOverlap(other.PositiveReadCounters, NegativeReadCounters) || hasAnyOverlap(PositiveReadCounters, other.NegativeReadCounters))
            return false;

        PositiveReadCounters.stream().filter(x -> other.PositiveReadCounters.contains(x)).forEach(x -> posCounters.add(x));
        NegativeReadCounters.stream().filter(x -> other.NegativeReadCounters.contains(x)).forEach(x -> negCounters.add(x));
        return !posCounters.isEmpty();
    }

    public boolean hasCommonSubset(
            final PhasedVariantGroup other, final List<ReadContextCounter> posCounters, final List<ReadContextCounter> negCounters)
    {
        if(hasAnyOverlap(other.PositiveReadCounters, NegativeReadCounters) || hasAnyOverlap(PositiveReadCounters, other.NegativeReadCounters))
            return false;

        return posCounters.stream().allMatch(x -> PositiveReadCounters.contains(x))
                && negCounters.stream().allMatch(x -> NegativeReadCounters.contains(x));
    }

    private static boolean hasAnyOverlap(final List<ReadContextCounter> counters1, final List<ReadContextCounter> counters2)
    {
        return counters1.stream().anyMatch(x -> counters2.contains(x)) || counters2.stream().anyMatch(x -> counters1.contains(x));
    }

    public void merge(final PhasedVariantGroup other)
    {
        // simple merge without expanding the +ves
        if(other.PositiveReadCounters.size() == PositiveReadCounters.size())
            ReadCount += other.ReadCount;
        else
            AllocatedReadCount += other.ReadCount;

        AllocatedReadCount += other.AllocatedReadCount;

        mergeNegatives(other.NegativeReadCounters);
        mMergedIds.add(other.Id);
    }

    public void merge(final PhasedVariantGroup other, double allocFraction)
    {
        // merge and allocate as directed, expanding +ves as required
        AllocatedReadCount += (other.ReadCount + other.AllocatedReadCount) * allocFraction;

        int index = 0;

        // keep merged positive RCs in positional order
        for(ReadContextCounter readCounter : other.PositiveReadCounters)
        {
            boolean matched = false;

            while(index < PositiveReadCounters.size())
            {
                ReadContextCounter counter = PositiveReadCounters.get(index);
                if(counter == readCounter)
                {
                    matched = true;
                    break;
                }

                if(counter.position() <= readCounter.position())
                {
                    ++index;
                    continue;
                }
                else
                {
                    // new read counter needs to be inserted at this earlier position
                    break;
                }
            }

            if(!matched)
                PositiveReadCounters.add(index, readCounter);
        }

        mPosVariantMin = PositiveReadCounters.get(0).position();
        mPosVariantMax = PositiveReadCounters.get(PositiveReadCounters.size() - 1).position();

        // other.PositiveReadCounters.stream().filter(x -> !PositiveReadCounters.contains(x)).forEach(x -> PositiveReadCounters.add(x));
        mergeNegatives(other.NegativeReadCounters);

        mMergedIds.add(other.Id);
    }

    public void mergeNegatives(final List<ReadContextCounter> negCounters)
    {
        negCounters.stream().filter(x -> !NegativeReadCounters.contains(x)).forEach(x -> NegativeReadCounters.add(x));

        mVariantMin = min(mPosVariantMin, NegativeReadCounters.stream().mapToInt(x -> x.position()).min().orElse(mPosVariantMin));
        mVariantMax = max(mPosVariantMax, NegativeReadCounters.stream().mapToInt(x -> x.position()).max().orElse(mPosVariantMin));
    }

    public String mergedGroupIds()
    {
        StringJoiner sj = new StringJoiner(";");
        mMergedIds.forEach(x -> sj.add(String.valueOf(x)));
        return sj.toString();
    }

    public String toString()
    {
        return String.format("%d: range(%d - %d) pos(%d) neg(%d) rc(%d) alloc(%.1f) fullRange(%s - %s)",
            Id, mPosVariantMin, mPosVariantMax, PositiveReadCounters.size(), NegativeReadCounters.size(), ReadCount, AllocatedReadCount,
                mVariantMin, mVariantMax);
    }
}
