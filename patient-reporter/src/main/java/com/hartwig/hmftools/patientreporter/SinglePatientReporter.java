package com.hartwig.hmftools.patientreporter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.copynumber.CopyNumber;
import com.hartwig.hmftools.common.ecrf.CpctEcrfModel;
import com.hartwig.hmftools.common.exception.HartwigException;
import com.hartwig.hmftools.common.variant.vcf.VCFFileWriter;
import com.hartwig.hmftools.common.variant.vcf.VCFSomaticFile;
import com.hartwig.hmftools.patientreporter.copynumber.CopyNumberAnalysis;
import com.hartwig.hmftools.patientreporter.copynumber.CopyNumberAnalyzer;
import com.hartwig.hmftools.patientreporter.copynumber.CopyNumberReport;
import com.hartwig.hmftools.patientreporter.lims.TumorPercentages;
import com.hartwig.hmftools.patientreporter.variants.VariantAnalysis;
import com.hartwig.hmftools.patientreporter.variants.VariantAnalyzer;
import com.hartwig.hmftools.patientreporter.variants.VariantReport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class SinglePatientReporter {
    private static final Logger LOGGER = LogManager.getLogger(SinglePatientReporter.class);

    @NotNull
    private final CpctEcrfModel cpctEcrfModel;
    @NotNull
    private final VariantAnalyzer variantAnalyzer;
    @NotNull
    private final CopyNumberAnalyzer copyNumberAnalyzer;
    @NotNull
    private final TumorPercentages tumorPercentages;
    @Nullable
    private final String tmpDirectory;

    SinglePatientReporter(@NotNull final CpctEcrfModel cpctEcrfModel, @NotNull final VariantAnalyzer variantAnalyzer,
            @NotNull final CopyNumberAnalyzer copyNumberAnalyzer, @NotNull final TumorPercentages tumorPercentages,
            @Nullable final String tmpDirectory) {
        this.cpctEcrfModel = cpctEcrfModel;
        this.variantAnalyzer = variantAnalyzer;
        this.copyNumberAnalyzer = copyNumberAnalyzer;
        this.tumorPercentages = tumorPercentages;
        this.tmpDirectory = tmpDirectory;
    }

    @NotNull
    PatientReport run(@NotNull final String runDirectory) throws IOException, HartwigException {
        final VariantAnalysis variantAnalysis = runVariantAnalysis(runDirectory);
        // TODO (KODU): Retrieve sample from metadata if possible.
        final String sample = variantAnalysis.sample();
        final CopyNumberAnalysis copyNumberAnalysis = runCopyNumberAnalysis(runDirectory, sample);

        final int passedCount = variantAnalysis.passedVariants().size();
        final int consensusPassedCount = variantAnalysis.consensusPassedVariants().size();
        final int mutationalLoad = variantAnalysis.missenseVariants().size();
        final int consequentialVariantCount = variantAnalysis.consequentialVariants().size();
        final int potentialMNVCount = variantAnalysis.potentialConsequentialMNVs().size();

        LOGGER.info(" Printing analysis results:");
        LOGGER.info("  Number of variants after applying pass-only filter : " + Integer.toString(passedCount));
        LOGGER.info("  Number of variants after applying consensus rule : " + Integer.toString(consensusPassedCount));
        LOGGER.info("  Number of missense variants in consensus rule (mutational load) : " + Integer.toString(
                mutationalLoad));
        LOGGER.info("  Number of consequential variants to report : " + Integer.toString(consequentialVariantCount));
        LOGGER.info("  Number of potential consequential MNVs : " + Integer.toString(potentialMNVCount));
        if (potentialMNVCount > 0) {
            LOGGER.warn(" !! Non-zero number of potentials MNV ");
        }
        LOGGER.info("  Determined copy number stats for " + Integer.toString(copyNumberAnalysis.stats().size())
                + " regions which led to " + Integer.toString(copyNumberAnalysis.findings().size()) + " findings.");

        if (tmpDirectory != null) {
            writeIntermediateDataToTmpFiles(tmpDirectory + File.separator + sample, variantAnalysis,
                    copyNumberAnalysis);
        }

        final String tumorType = PatientReporterHelper.extractTumorType(cpctEcrfModel, sample);
        final double tumorPercentage = tumorPercentages.findTumorPercentageForSample(sample);
        return new PatientReport(sample, variantAnalysis.findings(), copyNumberAnalysis.findings(), mutationalLoad,
                tumorType, tumorPercentage);
    }

    @NotNull
    VariantAnalysis runVariantAnalysis(@NotNull final String runDirectory) throws IOException, HartwigException {
        LOGGER.info(" Loading somatic variants...");
        final VCFSomaticFile variantFile = PatientReporterHelper.loadVariantFile(runDirectory);
        final String sample = variantFile.sample();
        LOGGER.info("  " + variantFile.variants().size() + " somatic variants loaded for sample " + sample);

        LOGGER.info(" Analyzing somatic variants...");
        return variantAnalyzer.run(sample, variantFile.variants());
    }

    @NotNull
    private CopyNumberAnalysis runCopyNumberAnalysis(@NotNull final String runDirectory, @NotNull final String sample)
            throws IOException, HartwigException {
        LOGGER.info(" Loading somatic copy numbers...");
        final List<CopyNumber> copyNumbers = PatientReporterHelper.loadCNVFile(runDirectory, sample);
        LOGGER.info("  " + copyNumbers.size() + " copy number regions loaded for sample " + sample);

        LOGGER.info(" Analyzing somatic copy numbers...");
        return copyNumberAnalyzer.run(copyNumbers);
    }

    private static void writeIntermediateDataToTmpFiles(@NotNull final String baseName,
            @NotNull final VariantAnalysis variantAnalysis, @NotNull final CopyNumberAnalysis copyNumberAnalysis)
            throws IOException {
        final String consensusVCF = baseName + "_consensus_variants.vcf";
        VCFFileWriter.writeSomaticVCF(consensusVCF, variantAnalysis.consensusPassedVariants());
        LOGGER.info("    Written consensus-passed variants to " + consensusVCF);

        final String consequentialVCF = baseName + "_consequential_variants.vcf";
        VCFFileWriter.writeSomaticVCF(consequentialVCF, variantAnalysis.consequentialVariants());
        LOGGER.info("    Written consequential variants to " + consequentialVCF);

        final String varReportFile = baseName + "_variant_report.csv";
        final List<VariantReport> varFindings = variantAnalysis.findings();
        Files.write(new File(varReportFile).toPath(), varToCSV(varFindings));
        LOGGER.info("    Written " + varFindings.size() + " variants to report to " + varReportFile);

        final String cnvReportFile = baseName + "_copynumber_report.csv";
        final List<CopyNumberReport> cnvFindings = copyNumberAnalysis.findings();
        Files.write(new File(cnvReportFile).toPath(), cnvToCSV(cnvFindings));
        LOGGER.info("    Written " + cnvFindings.size() + " copy-numbers to report to " + cnvReportFile);
    }

    @NotNull
    private static List<String> varToCSV(@NotNull final List<VariantReport> reports) {
        final List<String> lines = Lists.newArrayList();
        lines.add("GENE,POSITION,REF,ALT,TRANSCRIPT,CDS,AA,CONSEQUENCE,COSMIC_ID,ALLELE_READ_COUNT,TOTAL_READ_COUNT");
        lines.addAll(reports.stream().map(
                report -> report.gene() + "," + report.position() + "," + report.ref() + "," + report.alt() + ","
                        + report.transcript() + "," + report.hgvsCoding() + "," + report.hgvsProtein() + ","
                        + report.consequence() + "," + report.cosmicID() + "," + Integer.toString(
                        report.alleleReadCount()) + "," + Integer.toString(report.totalReadCount())).
                collect(Collectors.toList()));
        return lines;
    }

    @NotNull
    private static List<String> cnvToCSV(@NotNull final List<CopyNumberReport> reports) {
        final List<String> lines = Lists.newArrayList();
        lines.add("GENE,TRANSCRIPT,FINDING");
        lines.addAll(reports.stream().map(report -> report.gene() + "," + report.transcript() + "," + Integer.toString(
                report.copyNumber())).collect(Collectors.toList()));
        return lines;
    }
}
