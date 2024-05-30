package com.hartwig.hmftools.sage.quality;

import static java.lang.Math.min;

import static com.hartwig.hmftools.common.basequal.jitter.JitterModelParams.MAX_SPECIFIC_LENGTH_UNIT;
import static com.hartwig.hmftools.sage.SageCommon.SG_LOGGER;
import static com.hartwig.hmftools.sage.SageConstants.MAX_REPEAT_LENGTH;
import static com.hartwig.hmftools.sage.SageConstants.MIN_REPEAT_COUNT;
import static com.hartwig.hmftools.sage.SageConstants.MSI_JITTER_DEFAULT_ERROR_RATE;
import static com.hartwig.hmftools.sage.SageConstants.MSI_JITTER_MAX_REPEAT_CHANGE;
import static com.hartwig.hmftools.sage.SageConstants.MSI_JITTER_NOISE_RATE;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.basequal.jitter.JitterModelParams;
import com.hartwig.hmftools.common.basequal.jitter.JitterModelParamsFile;
import com.hartwig.hmftools.sage.common.RepeatInfo;
import com.hartwig.hmftools.sage.common.VariantReadContext;

import org.apache.commons.math3.distribution.BinomialDistribution;
import org.jetbrains.annotations.Nullable;

public class MsiJitterCalcs
{
    private final Map<String,List<MsiModelParams>> mSampleParams;

    public MsiJitterCalcs()
    {
        mSampleParams = Maps.newHashMap();
    }

    public static MsiJitterCalcs build(final List<String> sampleIds, @Nullable final String jitterParamsDir)
    {
        MsiJitterCalcs msiJitterCalcs = new MsiJitterCalcs();

        if(jitterParamsDir != null)
        {
            msiJitterCalcs.loadSampleJitterParams(sampleIds, jitterParamsDir);
        }

        return msiJitterCalcs;
    }

    public boolean loadSampleJitterParams(final List<String> sampleIds, final String jitterParamsDir)
    {
        try
        {
            for(String sampleId : sampleIds)
            {
                String jitterParamFile = JitterModelParamsFile.generateFilename(jitterParamsDir, sampleId);

                if(!Files.exists(Paths.get(jitterParamFile)))
                    return false;

                List<JitterModelParams> rawParams = JitterModelParamsFile.read(jitterParamFile);
                List<MsiModelParams> modelParams = rawParams.stream().map(x -> new MsiModelParams(x)).collect(Collectors.toList());
                mSampleParams.put(sampleId, modelParams);
            }

            SG_LOGGER.debug("loaded {} fitter param files", sampleIds.size());
        }
        catch(Exception e)
        {
            return false;
        }

        return true;
    }

    public static RepeatInfo getVariantRepeatInfo(final VariantReadContext readContext)
    {
        if(!readContext.variant().isIndel())
            return null;

        int repeatIndexStart = readContext.variantRefIndex() + 1;

        return RepeatInfo.findMaxRepeat(
                readContext.RefBases, repeatIndexStart, repeatIndexStart, MAX_REPEAT_LENGTH, MIN_REPEAT_COUNT + 1,
                false, repeatIndexStart);
    }

    public double calcErrorRate(final VariantReadContext readContext, final String sampleId)
    {
        if(!readContext.variant().isIndel())
            return 0;

        int repeatIndexStart = readContext.variantRefIndex() + 1;

        RepeatInfo refRepeat = RepeatInfo.findMaxRepeat(
                readContext.RefBases, repeatIndexStart, repeatIndexStart, MAX_REPEAT_LENGTH, MIN_REPEAT_COUNT + 1,
                false, repeatIndexStart);

        if(refRepeat == null)
            return 0;

        // check if the alt adjusts the repeat by +/- one unit
        String altBases = readContext.variant().isInsert() ?
                readContext.variant().Alt.substring(1) : readContext.variant().Ref.substring(1);

        int impliedAltChange = altBases.length() / refRepeat.repeatLength();

        if(impliedAltChange > MSI_JITTER_MAX_REPEAT_CHANGE)
            return 0;

        List<MsiModelParams> allParams = mSampleParams.get(sampleId);

        if(allParams == null)
            return 0;

        MsiModelParams varParams = findApplicableParams(allParams, refRepeat.repeatLength(), refRepeat.Bases);

        if(varParams == null)
            return 0;

        Double fixedScale = getScaleParam(varParams.params(), refRepeat.Count);

        return varParams.calcErrorRate(refRepeat.Count, impliedAltChange, fixedScale);
    }

    private Double getScaleParam(final JitterModelParams params, int repeatCount)
    {
        if(repeatCount == 4)
            return params.OptimalScaleRepeat4;
        else if(repeatCount == 5)
            return params.OptimalScaleRepeat5;
        else if(repeatCount == 6)
            return params.OptimalScaleRepeat6;
        else
            return null;
    }

    private static MsiModelParams findApplicableParams(final List<MsiModelParams> allParams, final int repeatLength, final String repeatBases)
    {
        for(MsiModelParams params : allParams)
        {
            if(repeatLength <= MAX_SPECIFIC_LENGTH_UNIT)
            {
                if(params.params().repeatUnitLength() == repeatLength)
                {
                    if(params.params().RepeatUnit.contains(repeatBases))
                        return params;
                }
            }
            else if(params.params().aboveSpecificLength())
            {
                return params;
            }
        }

        return null;
    }

    public Boolean isWithinJitterNoise(
            final String sampleId, final RepeatInfo maxRepeat, int fullSupport, int shortened, int lengthened)
    {
        List<MsiModelParams> allParams = mSampleParams.get(sampleId);

        if(allParams == null)
            return null;

        double shortenedErrorRate = getErrorRate(allParams, maxRepeat, 1);
        double lengthenedErrorRate = getErrorRate(allParams, maxRepeat, -1);

        if(shortened > fullSupport && isWithinNoise(fullSupport, shortened, shortenedErrorRate))
            return true;

        if(lengthened > fullSupport && isWithinNoise(fullSupport, lengthened, lengthenedErrorRate))
            return true;

        if(min(shortened, lengthened) >= fullSupport)
        {
            int total = fullSupport + shortened + lengthened;
            double jitterRatio = fullSupport / (double)total;
            double avgErrorRate = (shortenedErrorRate + lengthenedErrorRate) * 0.5;

            if(jitterRatio < 2 * avgErrorRate)
                return true;

            BinomialDistribution distribution = new BinomialDistribution(total, avgErrorRate);

            double prob = 1 - distribution.cumulativeProbability(fullSupport - 1);

            return prob > MSI_JITTER_NOISE_RATE;
        }

        return false;
    }

    private double getErrorRate(final List<MsiModelParams> allParams, final RepeatInfo maxRepeat, int repeatChangeVsRef)
    {
        // if variant measures shortened count for say a repeat of 5, then implies ref was 4 so get the error rate for 4 going to 5
        int refRepeatCount = maxRepeat.Count - repeatChangeVsRef;

        double errorRate = MSI_JITTER_DEFAULT_ERROR_RATE;

        if(refRepeatCount < MIN_REPEAT_COUNT)
            return errorRate;

        MsiModelParams modelParams = findApplicableParams(allParams, refRepeatCount, maxRepeat.Bases);

        if(modelParams == null)
            return errorRate;

        Double fixedScale = getScaleParam(modelParams.params(), refRepeatCount);

        return modelParams.calcErrorRate(refRepeatCount, repeatChangeVsRef, fixedScale);
    }

    private boolean isWithinNoise(int fullSupport, int jitterCount, double errorRate)
    {
        // a low full count relative to the total will be classified as within noise
        double jitterRatio = fullSupport / (double)(fullSupport + jitterCount);
        if(jitterRatio < 2 * errorRate)
            return true;

        // also test a p-value of jitter vs the full support counts
        BinomialDistribution distribution = new BinomialDistribution(fullSupport + jitterCount, errorRate);

        double prob = 1 - distribution.cumulativeProbability(min(fullSupport, jitterCount) - 1);

        return prob > MSI_JITTER_NOISE_RATE;
    }

    @VisibleForTesting
    public void setSampleParams(final String sampleId, final List<JitterModelParams> params)
    {
        mSampleParams.put(sampleId, params.stream().map(x -> new MsiModelParams(x)).collect(Collectors.toList()));
    }
}