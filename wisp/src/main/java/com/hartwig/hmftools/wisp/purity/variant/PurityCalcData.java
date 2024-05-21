package com.hartwig.hmftools.wisp.purity.variant;

import java.util.List;

import com.google.common.collect.Lists;

public class PurityCalcData
{
    public double RawPurityEstimate;
    public double PurityEstimate;
    public double PurityRangeLow;
    public double PurityRangeHigh;
    public double Probability;
    public double LodPurityEstimate;
    public double DualProbability;
    public int BqrQualThreshold;

    public ClonalityData Clonality;

    public final List<String> BqrExtraInfo;

    public static final double CALC_NO_SET = -1;

    public PurityCalcData()
    {
        RawPurityEstimate = 0;
        PurityEstimate = 0;
        PurityRangeLow = 0;
        PurityRangeHigh = 0;
        LodPurityEstimate = CALC_NO_SET;
        Probability = CALC_NO_SET;
        DualProbability = CALC_NO_SET;
        BqrQualThreshold = 0;

        Clonality = ClonalityData.NO_RESULT;
        BqrExtraInfo = Lists.newArrayList();
    }
}
