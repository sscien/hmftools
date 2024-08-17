package com.hartwig.hmftools.esvee.assembly;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;

import static com.hartwig.hmftools.common.codon.Nucleotides.DNA_BASE_BYTES;
import static com.hartwig.hmftools.common.utils.Arrays.subsetArray;
import static com.hartwig.hmftools.esvee.AssemblyConstants.PRIMARY_ASSEMBLY_MAX_NON_SOFT_CLIP_OVERLAP;
import static com.hartwig.hmftools.esvee.assembly.AssemblyUtils.N_BASE;
import static com.hartwig.hmftools.esvee.common.SvConstants.LOW_BASE_QUAL_THRESHOLD;

import static htsjdk.samtools.CigarOperator.D;
import static htsjdk.samtools.CigarOperator.I;
import static htsjdk.samtools.CigarOperator.M;

import java.util.Collections;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.bam.CigarUtils;
import com.hartwig.hmftools.common.codon.Nucleotides;
import com.hartwig.hmftools.esvee.assembly.read.Read;
import com.hartwig.hmftools.esvee.assembly.types.JunctionAssembly;
import com.hartwig.hmftools.esvee.assembly.types.SupportRead;

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;

public class RefBaseSeqBuilder
{
    private final JunctionAssembly mAssembly;
    private final List<ReadParseState> mReads;

    private final boolean mIsForward;
    private final int mJunctionPosition;

    private byte[] mBases; // starts with the junction ref base
    private byte[] mBaseQuals;
    private final List<CigarElement> mCigarElements;
    private int mRefBasePosition;

    private boolean mIsValid;

    public RefBaseSeqBuilder(final JunctionAssembly assembly)
    {
        mAssembly = assembly;
        mIsForward = mAssembly.junction().isForward();
        mJunctionPosition = mAssembly.junction().Position;
        mRefBasePosition = mJunctionPosition;

        int maxReadBaseLength = 0;

        mReads = Lists.newArrayListWithCapacity(assembly.supportCount());

        for(SupportRead support : assembly.support())
        {
            Read read = support.cachedRead();
            int readJunctionIndex = read.getReadIndexAtReferencePosition(mJunctionPosition, true);

            if(mIsForward)
            {
                // junction reads must overlap the junction by 3+ bases to extend the ref sequence
                if(read.isRightClipped() && read.unclippedEnd() - mJunctionPosition < PRIMARY_ASSEMBLY_MAX_NON_SOFT_CLIP_OVERLAP)
                    continue;
            }
            else
            {
                if(read.isLeftClipped() && mJunctionPosition - read.unclippedStart() < PRIMARY_ASSEMBLY_MAX_NON_SOFT_CLIP_OVERLAP)
                    continue;
            }

            int readRefBaseLength = readRefBaseLength(read, readJunctionIndex, mIsForward);

            maxReadBaseLength = max(maxReadBaseLength, readRefBaseLength);

            mReads.add(new ReadParseState(mIsForward, read, readJunctionIndex, readRefBaseLength));
        }

        int baseLength = maxReadBaseLength + 1; // since the junction base itself is included (which is a ref base)

        mBases = new byte[baseLength];
        mBaseQuals = new byte[baseLength];
        mCigarElements = Lists.newArrayList();

        mIsValid = true;

        buildSequence();

        trimFinalSequence();

        /*
        formConsensusSequence();

        // findRepeats();

        assignReads();

        determineFinalBases();
        */
    }

    public byte[] bases() { return mBases; }
    public byte[] baseQualities() { return mBaseQuals; }
    public int refBaseLength() { return mBases.length; }
    public boolean isValid() { return mIsValid; }
    public int refBasePosition() { return mRefBasePosition; }
    public List<CigarElement> cigarElements() { return mCigarElements; }
    public String cigarStr() { return CigarUtils.cigarElementsToStr(mCigarElements); }

    public static int readRefBaseLength(final Read read, int readJunctionIndex, boolean isForwardJunction)
    {
        if(isForwardJunction)
        {
            return max(readJunctionIndex - read.leftClipLength(), 0);
        }
        else
        {
            return max(read.basesLength() - readJunctionIndex - 1 - read.rightClipLength(), 0);
        }
    }

    /*
    public List<Read> mismatchReads()
    {
        return mReads.stream().filter(x -> x.exceedsMaxMismatches()).map(x -> x.mRead).collect(Collectors.toList());
    }
    */

    public int mismatches() { return (int)mReads.stream().filter(x -> x.exceedsMaxMismatches()).count(); }

    private static final byte NO_BASE = 0;

    private void buildSequence()
    {
        int currentIndex = mIsForward ? mBases.length - 1 : 0;
        int refPosition = mJunctionPosition;

        mBases[currentIndex] = mAssembly.bases()[mAssembly.junctionIndex()];
        mBaseQuals[currentIndex] = mAssembly.baseQuals()[mAssembly.junctionIndex()];

        int baseCount = Nucleotides.DNA_BASES.length;

        CigarOperator currentElementType = M;
        int currentElementLength = 0;

        List<ReadParseState> activeReads = Lists.newArrayList(mReads);

        while(!activeReads.isEmpty() && currentIndex >= 0 && currentIndex < mBases.length)
        {
            byte consensusBase = 0;
            int consensusMaxQual = 0;
            int consensusQualTotal = 0;
            int consensusReadCount = 0;

            // per-base arrays are only used for high-qual mismatches
            int[] readCounts = null;
            int[] totalQuals = null;
            int[] maxQuals = null;

            if(currentElementType == M || currentElementType == I)
                currentIndex += mIsForward ? -1 : 1;

            ++currentElementLength;

            // move to the next position or index if during an insert
            progressReadState(activeReads, currentElementType);

            if(activeReads.isEmpty())
            {
                mCigarElements.add(new CigarElement(currentElementLength, currentElementType));
                break;
            }

            // establish new most common operator
            CigarOperator nextElementType = findNextOperator(activeReads);

            if(nextElementType != currentElementType)
            {
                mCigarElements.add(new CigarElement(currentElementLength, currentElementType));
                currentElementLength = 0;
                currentElementType = nextElementType;
            }

            if(currentElementType == D)
            {
                refPosition += mIsForward ? -1 : 1;
                markReadBaseMatches(activeReads, currentElementType, NO_BASE, NO_BASE);
                continue;
            }

            // now establish the consensus base
            for(ReadParseState read : activeReads)
            {
                if(read.operator() != currentElementType)
                    continue;

                byte base = read.currentBase();
                int qual = read.currentQual();

                if(base == N_BASE)
                {
                    base = DNA_BASE_BYTES[0];
                    qual = 0;
                }

                if(readCounts == null)
                {
                    if(consensusBase == NO_BASE
                    || (base != consensusBase && consensusMaxQual < LOW_BASE_QUAL_THRESHOLD && qual >= LOW_BASE_QUAL_THRESHOLD))
                    {
                        // set first or replace with first high qual
                        consensusBase = base;
                        consensusMaxQual = qual;
                        consensusQualTotal = qual;
                        consensusReadCount = 1;
                        continue;
                    }
                    else if(base == consensusBase)
                    {
                        consensusMaxQual = max(qual, consensusMaxQual);
                        consensusQualTotal += qual;
                        ++consensusReadCount;
                        continue;
                    }
                    else if(base != consensusBase && qual < LOW_BASE_QUAL_THRESHOLD)
                    {
                        // low-qual disagreement - ignore regardless of consensus qual
                        continue;
                    }

                    // high-qual mismatch so start tracking frequencies for each base
                    readCounts = new int[baseCount];
                    totalQuals = new int[baseCount];
                    maxQuals = new int[baseCount];

                    // back port existing counts to the per-base arrays
                    int baseIndex = Nucleotides.baseIndex(consensusBase);
                    totalQuals[baseIndex] = consensusQualTotal;
                    maxQuals[baseIndex] = consensusMaxQual;
                    readCounts[baseIndex] = consensusReadCount;
                }

                int baseIndex = Nucleotides.baseIndex(base);

                totalQuals[baseIndex] += qual;
                maxQuals[baseIndex] = max(maxQuals[baseIndex], qual);
                ++readCounts[baseIndex];
            }

            if(readCounts != null)
            {
                // take the bases with the highest qual totals
                int maxQual = 0;
                int maxBaseIndex = 0;
                for(int b = 0; b < baseCount; ++b)
                {
                    if(totalQuals[b] > maxQual)
                    {
                        maxQual = totalQuals[b];
                        maxBaseIndex = b;
                    }
                }

                consensusBase = DNA_BASE_BYTES[maxBaseIndex];
                consensusMaxQual = (byte)maxQuals[maxBaseIndex];
            }

            mBases[currentIndex] = consensusBase;
            mBaseQuals[currentIndex] = (byte)consensusMaxQual;

            // mark active reads as matching or not
            markReadBaseMatches(activeReads, currentElementType, consensusBase, consensusMaxQual);

            if(currentElementType == M || currentElementType == D)
                refPosition += mIsForward ? -1 : 1;
        }

        mRefBasePosition = refPosition;

        if(mIsForward)
            Collections.reverse(mCigarElements);
    }

    private static CigarOperator findNextOperator(final List<ReadParseState> reads)
    {
        int inserts = 0;
        int deletes = 0;
        int aligned = 0;

        for(ReadParseState read : reads)
        {
            if(read.operator() == M)
                ++aligned;
            else if(read.operator() == D)
                ++deletes;
            else if(read.operator() == I)
                ++inserts;
        }

        if(aligned >= inserts && aligned >= deletes)
            return M;

        return inserts >= deletes ? I : D;
    }

    private static void progressReadState(final List<ReadParseState> reads, final CigarOperator currentElementType)
    {
        // move to the next position or index if during an insert
        int index = 0;

        while(index < reads.size())
        {
            ReadParseState read = reads.get(index);

            if(currentElementType == M || currentElementType == D)
            {
                if(read.operator() == I)
                    read.skipInsert();
                else
                    read.moveNext();
            }
            else // insert
            {
                if(read.operator() == I)
                    read.moveNext();
            }

            if(read.exhausted())
                reads.remove(index);
            else
                ++index;
        }
    }

    private static void markReadBaseMatches(
            final List<ReadParseState> reads, final CigarOperator currentElementType, final byte consensusBase, final int consensusQual)
    {
        for(ReadParseState read : reads)
        {
            if(currentElementType == D)
            {
                if(read.operator() != currentElementType)
                    read.addMismatch();

                continue;
            }

            boolean aboveMinQual = read.currentQual() >= LOW_BASE_QUAL_THRESHOLD;
            boolean consensusAboveMinQual = consensusQual >= LOW_BASE_QUAL_THRESHOLD;

            if(read.operator() != currentElementType)
            {
                if(read.operator() == D || aboveMinQual)
                    read.addMismatch();
            }
            else if(read.currentBase() != consensusBase)
            {
                if(aboveMinQual && consensusAboveMinQual)
                    read.addMismatch();
            }
            else
            {
                if(aboveMinQual)
                    read.addHighQualMatch();
            }
        }
    }

    private void trimFinalSequence()
    {
        int currentIndex = mIsForward ? mBases.length - 1 : 0;
        int validLength = 0;

        while(currentIndex >= 0 && currentIndex < mBases.length)
        {
            if(mBases[currentIndex] == 0)
                break;

            ++validLength;
            currentIndex += mIsForward ? -1 : 1;
        }

        if(validLength == mBases.length)
            return;

        int reduction = mBases.length - validLength;
        int startIndex = mIsForward ? reduction : 0;
        int endIndex = startIndex + validLength - 1;
        mBases = subsetArray(mBases, startIndex, endIndex);
        mBaseQuals = subsetArray(mBaseQuals, startIndex, endIndex);
    }

    @VisibleForTesting
    public String refBaseSequence() { return new String(mBases); }

    public List<ReadParseState> reads() { return mReads; }
}