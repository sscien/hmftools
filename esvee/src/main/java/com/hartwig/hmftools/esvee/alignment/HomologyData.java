package com.hartwig.hmftools.esvee.alignment;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;

import static com.hartwig.hmftools.esvee.alignment.BreakendBuilder.segmentOrientation;
import static com.hartwig.hmftools.esvee.alignment.MdTag.MATCH_BYTE;

import com.hartwig.hmftools.common.codon.Nucleotides;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeInterface;
import com.hartwig.hmftools.common.genome.region.Orientation;

public class HomologyData
{
    public final String Homology;
    public final int ExactStart;
    public final int ExactEnd;
    public final int InexactStart;
    public final int InexactEnd;

    public HomologyData(final String homology, final int exactStart, final int exactEnd, final int inexactStart, final int inexactEnd)
    {
        Homology = homology;
        ExactStart = exactStart;
        ExactEnd = exactEnd;
        InexactStart = inexactStart;
        InexactEnd = inexactEnd;
    }

    public String toString() { return format("%s exact(%d,%d) inexact(%d,%d)",
            Homology.isEmpty() ? "none" : Homology, ExactStart, ExactEnd, InexactStart, InexactEnd); }

    public boolean isSymmetrical() { return abs(InexactStart) == InexactEnd; }

    public int positionAdjustment(final Orientation orientation)
    {
        return orientation.isForward() ? -InexactEnd : abs(InexactStart);
    }

    public HomologyData invert(final boolean reversePositions, final boolean reverseBases)
    {
        return new HomologyData(
                reverseBases ? Nucleotides.reverseComplementBases(Homology) : Homology,
                reversePositions ? -ExactEnd : ExactStart, reversePositions ? abs(ExactStart) : ExactEnd,
                reversePositions ? -InexactEnd : InexactStart, reversePositions ? abs(InexactStart) : InexactEnd);
    }

    public static HomologyData determineHomology(final String fullSequence, final AlignData leftAlignment, final AlignData rightAlignment)
    {
        int overlapLength = leftAlignment.sequenceEnd() - rightAlignment.sequenceStart() + 1;

        if(overlapLength <= 0)
            return null;

        int leftIndexEnd = leftAlignment.segmentLength() - 1;
        int leftIndexStart = leftIndexEnd - overlapLength + 1;

        int rightIndexStart = 0;
        int rightIndexEnd = overlapLength - 1;

        String overlapBases = fullSequence.substring(rightAlignment.sequenceStart(), leftAlignment.sequenceEnd() + 1);

        if(leftAlignment.isReverse())
            overlapBases = Nucleotides.reverseComplementBases(overlapBases);

        MdTag leftMdTag = new MdTag(leftAlignment.mdTag());
        MdTag rightMdTag = new MdTag(rightAlignment.mdTag());

        if(!leftMdTag.hasMismatches() && !rightMdTag.hasMismatches())
        {
            int halfOverlap = overlapLength / 2;
            int exactStart = (overlapLength % 2) == 0 ? halfOverlap : halfOverlap + 1; // round up if an odd length
            int exactEnd = overlapLength - exactStart;
            return new HomologyData(overlapBases, -exactStart, exactEnd, -exactStart, exactEnd);
        }

        byte[] leftMdSeq = leftMdTag.extractSubSequence(leftIndexStart, leftIndexEnd, leftAlignment.isReverse());
        byte[] rightMdSeq = rightMdTag.extractSubSequence(rightIndexStart, rightIndexEnd, rightAlignment.isReverse());

        if(leftMdSeq.length != rightMdSeq.length)
            return null;

        // count mismatches between the 2 sequence arrays progressively along the full sequence
        int[] indexMismatches = new int[leftMdSeq.length + 1];
        int minMismatchCount = -1;
        int minMismatchEndIndex = -1;

        for(int endIndex = 0; endIndex <= leftMdSeq.length; ++endIndex)
        {
            int mismatchCount = 0;

            for(int i = 0; i < leftMdSeq.length; ++i)
            {
                if(i < endIndex)
                {
                    if(leftMdSeq[i] != MATCH_BYTE)
                        ++mismatchCount;
                }
                else
                {
                    if(rightMdSeq[i] != MATCH_BYTE)
                        ++mismatchCount;
                }
            }

            indexMismatches[endIndex] = mismatchCount;

            if(minMismatchCount == -1 || mismatchCount <= minMismatchCount)
            {
                minMismatchCount = mismatchCount;
                minMismatchEndIndex = endIndex;
            }
        }

        // first a simple check for full exact homology
        if(minMismatchCount == 0 && minMismatchEndIndex == overlapLength - 1)
        {
            int halfOverlap = overlapLength / 2;
            int exactStart = (overlapLength % 2) == 0 ? halfOverlap : halfOverlap + 1; // round up if an odd length
            int exactEnd = overlapLength - exactStart;
            return new HomologyData(overlapBases, -exactStart, exactEnd, -exactStart, exactEnd);
        }

        // find the range with minimum mismatches
        int rangeStart = -1;
        int rangeEnd = -1;
        int longestRangeStart = -1;
        int longestRangeEnd = -1;

        for(int i = 0; i < indexMismatches.length; ++i)
        {
            if(indexMismatches[i] == minMismatchCount)
            {
                if(rangeStart < 0)
                    rangeStart = i;
            }
            else if(rangeStart >= 0)
            {
                rangeEnd = i - 1;

                if(longestRangeStart < 0 || (rangeEnd - rangeStart + 1 > longestRangeEnd - longestRangeStart + 1))
                {
                    longestRangeStart = rangeStart;
                    longestRangeEnd = rangeEnd;
                }

                // reset
                rangeStart = -1;
                rangeEnd = -1;
            }
        }

        if(rangeStart >= 0 && rangeEnd < 0)
        {
            // extended until the end
            longestRangeStart = rangeStart;
            longestRangeEnd = indexMismatches.length - 1;
        }

        int rangeLength = longestRangeEnd - longestRangeStart + 1;
        int halfRange = rangeLength / 2;

        int exactStart = 0;
        int exactEnd = 0;
        String exactHomology = "";

        if(longestRangeStart >= 0 && longestRangeEnd > longestRangeStart)
        {
            exactStart = max(halfRange, 1); // round up if an odd length
            exactEnd = rangeLength - exactStart - 1;

            exactHomology = overlapBases.substring(longestRangeStart, longestRangeEnd);
        }

        int inexactStart = longestRangeStart + halfRange;
        int inexactEnd = overlapLength - inexactStart;

        return new HomologyData(exactHomology, -exactStart, exactEnd, -inexactStart, inexactEnd);
    }

    public boolean matches(final HomologyData other)
    {
        return other.Homology.equals(Homology) && ExactStart == other.ExactStart && ExactEnd == other.ExactEnd
                && InexactStart == other.InexactStart && InexactEnd == other.InexactEnd;
    }

    @Deprecated
    public static HomologyData determineHomology(
            final String assemblyOverlap, final AlignData alignStart, final AlignData alignEnd, final RefGenomeInterface refGenome)
    {
        int overlap = alignStart.sequenceEnd() - alignEnd.sequenceStart() + 1;

        if(overlap <= 0 || assemblyOverlap.isEmpty())
            return null;

        Orientation orientationStart = segmentOrientation(alignStart, true);
        String basesStart = getOverlapBases(alignStart, orientationStart, overlap, refGenome);

        Orientation orientationEnd = segmentOrientation(alignEnd, false);
        String basesEnd = getOverlapBases(alignEnd, orientationEnd, overlap, refGenome);

        if(orientationStart == orientationEnd)
        {
            basesEnd = Nucleotides.reverseComplementBases(basesEnd);
        }

        return determineHomology(assemblyOverlap, basesStart, basesEnd, overlap);
    }

    public static HomologyData determineHomology(
            final String assemblyOverlap, final String refBasesStart, final String refBasesEnd, final int overlap)
    {
        if(assemblyOverlap.length() != refBasesStart.length() || assemblyOverlap.length() != refBasesEnd.length())
            return null;

        // first a simple check for full exact homology
        if(assemblyOverlap.equals(refBasesStart) && refBasesStart.equals(refBasesEnd))
        {
            int halfOverlap = overlap / 2;
            int exactStart = (overlap % 2) == 0 ? halfOverlap : halfOverlap + 1; // round up if an odd length
            int exactEnd = overlap - exactStart;
            return new HomologyData(assemblyOverlap, -exactStart, exactEnd, -exactStart, exactEnd);
        }

        // routine:
        // 1. determine region of overlap - done prior as assemblies are matched
        // 2. determine the combined # of mismatches on each breakend at each position in the overlap
        //  - start the comparison at each index in the overlapping region, simulating moving the position progressively forward
        // 3. find the range with the lowest # of mismatches. If there is more than 1 find the longest range
        // 4. place the breakpoint at the centre of the range
        //  Exact homology = range length / 2, with start + 1 if an odd length range
        //  Inexact homology = distance to start, end of overlap

        int exactMatch = 0;
        StringBuilder sb = new StringBuilder();

        for(int i = 0; i < overlap; ++i)
        {
            if(refBasesStart.charAt(i) == refBasesEnd.charAt(i))
            {
                sb.append(refBasesStart.charAt(i));
                ++exactMatch;
            }
            else
            {
                break;
            }
        }

        if(exactMatch == 0)
        {
            return new HomologyData("", 0, 0, 0, overlap);
        }

        int[] indexMismatches = new int[overlap + 1];
        int minMismatchCount = overlap;

        // the convention is that the start ref bases are from the first assembly's extension sequence, and so that by moving
        // the index forward it will check to see if there are mismatches in this extended ref base sequence, as the
        // end sequence's bases similarly become extension bases
        for(int i = 0; i <= overlap; ++i)
        {
            int mismatches = 0;

            for(int j = 0; j < overlap; ++j)
            {
                if(j < i && assemblyOverlap.charAt(j) != refBasesStart.charAt(j))
                    ++mismatches;
                else if(j >= i && assemblyOverlap.charAt(j) != refBasesEnd.charAt(j))
                    ++mismatches;
            }

            indexMismatches[i] = mismatches;
            minMismatchCount = min(minMismatchCount, mismatches);
        }

        // find the range with minimum mismatches
        int rangeStart = -1;
        int rangeEnd = -1;
        int longestRangeStart = -1;
        int longestRangeEnd = -1;

        for(int i = 0; i <= overlap; ++i)
        {
            if(indexMismatches[i] == minMismatchCount)
            {
                if(rangeStart < 0)
                    rangeStart = i;
            }
            else if(rangeStart >= 0)
            {
                rangeEnd = i - 1;

                if(longestRangeStart < 0 || (rangeEnd - rangeStart + 1 > longestRangeEnd - longestRangeStart + 1))
                {
                    longestRangeStart = rangeStart;
                    longestRangeEnd = rangeEnd;
                }

                // reset
                rangeStart = -1;
                rangeEnd = -1;
            }
        }

        if(rangeStart >= 0 && rangeEnd < 0)
        {
            // extended until the end
            longestRangeStart = rangeStart;
            longestRangeEnd = overlap;
        }

        int rangeLength = longestRangeEnd - longestRangeStart + 1;

        int halfRange = rangeLength / 2;
        int exactStart = max(halfRange, 1); // round up if an odd length
        int exactEnd = rangeLength - exactStart - 1;

        int inexactStart = longestRangeStart + halfRange;
        int inexactEnd = overlap - inexactStart;

        return new HomologyData(sb.toString(), -exactStart, exactEnd, -inexactStart, inexactEnd);
    }

    private static HomologyData invert(final HomologyData homologyData, final boolean reverseBases)
    {
        return new HomologyData(
                reverseBases ? Nucleotides.reverseComplementBases(homologyData.Homology) : homologyData.Homology,
                -homologyData.ExactEnd, abs(homologyData.ExactStart), -homologyData.InexactEnd, abs(homologyData.InexactStart));
    }

    private static String getOverlapBases(
            final AlignData alignment, final Orientation orientation, final int overlap, final RefGenomeInterface refGenome)
    {
        if(orientation.isForward())
        {
            return refGenome.getBaseString(
                    alignment.refLocation().Chromosome, alignment.refLocation().end() - overlap + 1, alignment.refLocation().end());
        }
        else
        {
            return refGenome.getBaseString(
                    alignment.refLocation().Chromosome, alignment.refLocation().start(), alignment.refLocation().start() + overlap - 1);
        }
    }
}
