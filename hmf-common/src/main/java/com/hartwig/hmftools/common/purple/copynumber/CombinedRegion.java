package com.hartwig.hmftools.common.purple.copynumber;

import com.hartwig.hmftools.common.numeric.Doubles;
import com.hartwig.hmftools.common.purple.region.FittedRegion;
import com.hartwig.hmftools.common.purple.region.ModifiableFittedRegion;
import com.hartwig.hmftools.common.purple.region.ObservedRegionStatus;
import com.hartwig.hmftools.common.region.GenomeRegion;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
class CombinedRegion implements GenomeRegion {

    private final boolean bafWeighted;
    private ModifiableFittedRegion combined;
    private int unweightedCount = 1;

    private CombinedRegionMethod copyNumberMethod = CombinedRegionMethod.NONE;
    private boolean inferredBAF;

    @Deprecated
    CombinedRegion(final boolean bafWeighted, final FittedRegion region) {
        this(bafWeighted, region, region.status() != ObservedRegionStatus.SOMATIC);
    }

    CombinedRegion(final boolean bafWeighted, final FittedRegion region, final boolean clearCopyNumber) {
        this.bafWeighted = bafWeighted;
        this.combined = ModifiableFittedRegion.create().from(region);
        if (clearCopyNumber) {
            clearCopyNumber();
        }

        if (region.status() != ObservedRegionStatus.SOMATIC) {
            clearBAFValues();
        }
    }

    @NotNull
    @Override
    public String chromosome() {
        return combined.chromosome();
    }

    @Override
    public long start() {
        return combined.start();
    }

    @Override
    public long end() {
        return combined.end();
    }

    boolean isInferredBAF() {
        return inferredBAF;
    }

    public double tumorCopyNumber() {
        return combined.tumorCopyNumber();
    }

    public double tumorBAF() {
        return combined.tumorBAF();
    }

    public int bafCount() {
        return region().bafCount();
    }

    CombinedRegionMethod copyNumberMethod() {
        return copyNumberMethod;
    }

    boolean isProcessed() {
        return copyNumberMethod != CombinedRegionMethod.NONE;
    }

    void setCopyNumberMethod(CombinedRegionMethod copyNumberMethod) {
        this.copyNumberMethod = copyNumberMethod;
    }

    FittedRegion region() {
        return combined;
    }

    @Deprecated
    void combine(final FittedRegion region) {
        if (region.status() == ObservedRegionStatus.SOMATIC) {
            extendWithBAFWeightedAverage(region);
        } else {
            extend(region);
        }
    }

    void extend(final FittedRegion region) {
        combined.setStart(Math.min(combined.start(), region.start()));
        combined.setEnd(Math.max(combined.end(), region.end()));

        if (region.start() <= combined.start()) {
            combined.setSupport(region.support());
            combined.setRatioSupport(region.ratioSupport());
        }
    }

    void extendWithUnweightedAverage(final FittedRegion region) {
        extend(region);
        applyWeightedAverage(region, unweightedCount, 1);
        unweightedCount++;
    }

    void extendWithBAFWeightedAverage(final FittedRegion region) {
        long currentBases = combined.bases();

        extend(region);

        combined.setStatus(ObservedRegionStatus.SOMATIC); //TODO Remove this
        combined.setObservedTumorRatioCount(combined.observedTumorRatioCount() + region.observedTumorRatioCount());

        final long currentWeight;
        final long newWeight;
        if (bafWeighted && (combined.bafCount() > 0 || region.bafCount() > 0)) {
            currentWeight = combined.bafCount();
            newWeight = region.bafCount();
        } else {
            currentWeight = currentBases;
            newWeight = region.bases();
        }

        applyWeightedAverage(region, currentWeight, newWeight);
    }

    private void applyWeightedAverage(final FittedRegion region, long currentWeight, long newWeight) {

        if (!Doubles.isZero(region.observedBAF())) {
            combined.setObservedBAF(weightedAverage(currentWeight, combined.observedBAF(), newWeight, region.observedBAF()));
        }

        if (!Doubles.isZero(region.tumorBAF())) {
            combined.setTumorBAF(weightedAverage(currentWeight, combined.tumorBAF(), newWeight, region.tumorBAF()));
        }

        if (!Doubles.isZero(region.tumorCopyNumber())) {
            combined.setTumorCopyNumber(weightedAverage(currentWeight, combined.tumorCopyNumber(), newWeight, region.tumorCopyNumber()));
        }

        if (!Doubles.isZero(region.refNormalisedCopyNumber())) {
            combined.setRefNormalisedCopyNumber(weightedAverage(currentWeight,
                    combined.refNormalisedCopyNumber(),
                    newWeight,
                    region.refNormalisedCopyNumber()));
        }

        combined.setBafCount(combined.bafCount() + region.bafCount());
    }

    void setTumorCopyNumber(@NotNull final CombinedRegionMethod method, double copyNumber) {
        setCopyNumberMethod(method);
        combined.setTumorCopyNumber(copyNumber);
    }

    void setInferredTumorBAF(double baf) {
        inferredBAF = true;
        combined.setTumorBAF(baf);
        combined.setBafCount(0);
        combined.setObservedBAF(0);
    }

    private double weightedAverage(long currentWeight, double currentValue, long newWeight, double newValue) {
        if (Doubles.isZero(currentValue)) {
            return newValue;
        }

        long totalWeight = currentWeight + newWeight;
        return (currentWeight * currentValue + newWeight * newValue) / totalWeight;
    }

    @Deprecated
    private void clearCopyNumber() {
        combined.setRefNormalisedCopyNumber(0);
        combined.setObservedTumorRatioCount(0);
        combined.setTumorCopyNumber(0);
    }

    private void clearBAFValues() {
        combined.setBafCount(0);
    }
}
