package com.hartwig.hmftools.patientreporter;

import java.util.List;

import com.hartwig.hmftools.common.purple.purity.FittedPurity;
import com.hartwig.hmftools.patientreporter.copynumber.CopyNumberReport;
import com.hartwig.hmftools.patientreporter.util.PatientReportFormat;
import com.hartwig.hmftools.patientreporter.variants.StructuralVariantAnalysis;
import com.hartwig.hmftools.patientreporter.variants.VariantReport;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PatientReport {

    @NotNull
    private final String sample;
    @NotNull
    private final List<VariantReport> variants;
    @NotNull
    private final List<StructuralVariantAnalysis.GeneDisruption> geneDisruptions;
    @NotNull
    private final List<CopyNumberReport> copyNumbers;
    private final int mutationalLoad;
    @NotNull
    private final String tumorType;
    @Nullable
    private final Double tumorPercentage;
    @NotNull
    private final FittedPurity fittedPurity;

    public PatientReport(@NotNull final String sample, @NotNull final List<VariantReport> variants,
            @NotNull final List<StructuralVariantAnalysis.GeneDisruption> geneDisruptions,
            @NotNull final List<CopyNumberReport> copyNumbers, final int mutationalLoad, @NotNull final String tumorType,
            @Nullable final Double tumorPercentage, @NotNull final FittedPurity fittedPurity) {
        this.sample = sample;
        this.variants = variants;
        this.geneDisruptions = geneDisruptions;
        this.copyNumbers = copyNumbers;
        this.mutationalLoad = mutationalLoad;
        this.tumorType = tumorType;
        this.tumorPercentage = tumorPercentage;
        this.fittedPurity = fittedPurity;
    }

    @NotNull
    public String sample() {
        return sample;
    }

    @NotNull
    public List<VariantReport> variants() {
        return variants;
    }

    public List<StructuralVariantAnalysis.GeneDisruption> geneDisruptions() {
        return geneDisruptions;
    }

    @NotNull
    public List<CopyNumberReport> copyNumbers() {
        return copyNumbers;
    }

    public int mutationalLoad() {
        return mutationalLoad;
    }

    @NotNull
    public String tumorType() {
        return tumorType;
    }

    public String tumorPercentageString() {
        return PatientReportFormat.formatNullablePercent(tumorPercentage);
    }

    public String impliedPurityString() {
        return PatientReportFormat.formatPercent(fittedPurity.purity());
    }
}
