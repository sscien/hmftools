package com.hartwig.hmftools.esvee.read;

import static java.lang.String.format;

public class ReadStats
{
    public int TotalReads;
    public int PolyGTrimmed;
    public int LowBaseQualTrimmed;
    public int IndelSoftClipConverted;
    public int DecoySequences;

    public ReadStats()
    {
        TotalReads = 0;
        PolyGTrimmed = 0;
        LowBaseQualTrimmed = 0;
        IndelSoftClipConverted = 0;
        DecoySequences = 0;
    }

    public void merge(final ReadStats other)
    {
        TotalReads += other.TotalReads;
        PolyGTrimmed += other.PolyGTrimmed;
        LowBaseQualTrimmed += other.LowBaseQualTrimmed;
        IndelSoftClipConverted += other.IndelSoftClipConverted;
        DecoySequences += other.DecoySequences;
    }

    public String toString()
    {
        return format("reads(%d) trim(polyG=%d lowBase=%d) indelSoftClip(%d) decoySequences(%d)",
                TotalReads, PolyGTrimmed, LowBaseQualTrimmed, IndelSoftClipConverted, DecoySequences);
    }
}
