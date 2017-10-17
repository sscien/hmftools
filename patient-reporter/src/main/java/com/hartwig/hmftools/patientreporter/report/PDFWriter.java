package com.hartwig.hmftools.patientreporter.report;

import static com.hartwig.hmftools.patientreporter.report.Commons.DATE_TIME_FORMAT;
import static com.hartwig.hmftools.patientreporter.report.Commons.HEADER_TO_DETAIL_VERTICAL_GAP;
import static com.hartwig.hmftools.patientreporter.report.Commons.SECTION_VERTICAL_GAP;
import static com.hartwig.hmftools.patientreporter.report.Commons.baseTable;
import static com.hartwig.hmftools.patientreporter.report.Commons.dataStyle;
import static com.hartwig.hmftools.patientreporter.report.Commons.dataTableStyle;
import static com.hartwig.hmftools.patientreporter.report.Commons.fontStyle;
import static com.hartwig.hmftools.patientreporter.report.Commons.linkStyle;
import static com.hartwig.hmftools.patientreporter.report.Commons.sectionHeaderStyle;
import static com.hartwig.hmftools.patientreporter.report.Commons.tableHeaderStyle;

import static net.sf.dynamicreports.report.builder.DynamicReports.cmp;
import static net.sf.dynamicreports.report.builder.DynamicReports.col;
import static net.sf.dynamicreports.report.builder.DynamicReports.hyperLink;
import static net.sf.dynamicreports.report.builder.DynamicReports.report;
import static net.sf.dynamicreports.report.builder.DynamicReports.stl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.hartwig.hmftools.patientreporter.HmfReporterData;
import com.hartwig.hmftools.patientreporter.NotSequencedPatientReport;
import com.hartwig.hmftools.patientreporter.PatientReport;
import com.hartwig.hmftools.patientreporter.PatientReporterApplication;
import com.hartwig.hmftools.patientreporter.SequencedPatientReport;
import com.hartwig.hmftools.patientreporter.report.components.GenePanelSection;
import com.hartwig.hmftools.patientreporter.report.components.MainPageTopSection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.base.expression.AbstractSimpleExpression;
import net.sf.dynamicreports.report.builder.FieldBuilder;
import net.sf.dynamicreports.report.builder.component.ComponentBuilder;
import net.sf.dynamicreports.report.builder.component.VerticalListBuilder;
import net.sf.dynamicreports.report.constant.HorizontalTextAlignment;
import net.sf.dynamicreports.report.datasource.DRDataSource;
import net.sf.dynamicreports.report.definition.ReportParameters;
import net.sf.dynamicreports.report.exception.DRException;

public class PDFWriter implements ReportWriter {

    private static final Logger LOGGER = LogManager.getLogger(PDFWriter.class);

    private static final int TEXT_HEADER_INDENT = 30;
    private static final int TEXT_DETAIL_INDENT = 40;
    private static final int TEXT_END_OF_LINE_GAP = 30;
    private static final int LIST_INDENT = 5;
    private static final int DETAIL_TO_DETAIL_VERTICAL_GAP = 4;

    @NotNull
    private final String reportDirectory;

    public PDFWriter(@NotNull final String reportDirectory) {
        this.reportDirectory = reportDirectory;
    }

    @Override
    public void writeSequenceReport(@NotNull final SequencedPatientReport report, @NotNull final HmfReporterData reporterData)
            throws IOException, DRException {
        final JasperReportBuilder reportBuilder = generatePatientReport(report, reporterData);
        writeReport(fileName(report.sampleReport().sampleCode(), "_hmf_report.pdf"), reportBuilder);
        final JasperReportBuilder supplementaryBuilder = generateSupplementaryReport(report);
        writeReport(fileName(report.sampleReport().sampleCode(), "_hmf_report_supplementary.pdf"), supplementaryBuilder);
        final JasperReportBuilder evidenceReportBuilder = EvidenceReport.generate(report);
        writeReport(fileName(report.sampleReport().sampleCode(), "_evidence_items.pdf"), evidenceReportBuilder);
    }

    @Override
    public void writeNonSequenceableReport(@NotNull final NotSequencedPatientReport report) throws IOException, DRException {
        final JasperReportBuilder reportBuilder = generateNotSequenceableReport(report);
        writeReport(fileName(report.sampleReport().sampleCode(), "_hmf_report.pdf"), reportBuilder);
    }

    private void writeReport(@NotNull final String fileName, @NotNull final JasperReportBuilder report)
            throws FileNotFoundException, DRException {
        if (Files.exists(new File(fileName).toPath())) {
            LOGGER.warn(" Could not write " + fileName + " as it already exists.");
        } else {
            report.toPdf(new FileOutputStream(fileName));
            LOGGER.info(" Created patient report at " + fileName);
        }
    }

    @NotNull
    private String fileName(@NotNull final String sample, @NotNull final String suffix) {
        return reportDirectory + File.separator + sample + suffix;
    }

    @VisibleForTesting
    @NotNull
    static JasperReportBuilder generateNotSequenceableReport(@NotNull final NotSequencedPatientReport report) throws IOException {
        // @formatter:off
        final ComponentBuilder<?, ?> finalReport =
                cmp.verticalList(
                        MainPageTopSection.build("HMF Sequencing Report", report.sampleReport()),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        mainPageNotSequenceableSection(report));
        // @formatter:on

        // MIVO: hack to get page footers working; the footer band and noData bands are exclusive, see additional comment below for details
        final DRDataSource singleItemDataSource = new DRDataSource("item");
        singleItemDataSource.add(new Object());

        return report().pageFooter(cmp.pageXslashY())
                .lastPageFooter(cmp.verticalList(signatureFooter(report.signaturePath()), cmp.pageXslashY(),
                        cmp.text("End of report.").setStyle(stl.style().setHorizontalTextAlignment(HorizontalTextAlignment.CENTER))))
                .addDetail(finalReport)
                .setDataSource(singleItemDataSource);
    }

    @VisibleForTesting
    @NotNull
    static JasperReportBuilder generatePatientReport(@NotNull final SequencedPatientReport report,
            @NotNull final HmfReporterData reporterData) throws IOException {
        final String additionalPagesTitleStart = "HMF Sequencing Report v" + PatientReporterApplication.VERSION;
        // @formatter:off
        final ComponentBuilder<?, ?> reportMainPage =
                cmp.verticalList(
                        MainPageTopSection.build("HMF Sequencing Report", report.sampleReport()),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        mainPageAboutSection(),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        variantReport(report, reporterData),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        copyNumberReport(report));

        final ComponentBuilder<?, ?> genePanelPage =
                cmp.verticalList(
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        cmp.text(additionalPagesTitleStart + " - Gene Panel Information")
                                .setStyle(sectionHeaderStyle()),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        GenePanelSection.build(reporterData)
                );

        final ComponentBuilder<?, ?> additionalInfoPage =
                cmp.verticalList(
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        cmp.text(additionalPagesTitleStart + " - Field Explanations")
                                .setStyle(sectionHeaderStyle()),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        variantFieldExplanationSection(),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        copyNumberExplanationSection()
                );

        final ComponentBuilder<?, ?> sampleDetailsPage =
                cmp.verticalList(
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        cmp.text(additionalPagesTitleStart + " - Sample Details & Disclaimer")
                                .setStyle(sectionHeaderStyle()),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        sampleDetailsSection(report),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        disclaimerSection()
                );

        final ComponentBuilder<?, ?> totalReport =
                cmp.multiPageList().add(reportMainPage)
                        .newPage().add(genePanelPage)
                        .newPage().add(additionalInfoPage)
                        .newPage().add(sampleDetailsPage);
        // @formatter:on

        // MIVO: hack to get page footers working; the footer band and noData bands are exclusive:
        //  - footerBand, detailBand, etc are shown when data source is not empty
        //  - noData band is shown when data source is empty; intended to be used when there is no data to show in the report
        //  (e.g. would be appropriate to be used for notSequenceableReport)
        //
        // more info: http://www.dynamicreports.org/examples/bandreport
        //
        // todo: fix when implementing new report layout

        final DRDataSource singleItemDataSource = new DRDataSource("item");
        singleItemDataSource.add(new Object());

        return report().pageFooter(cmp.pageXslashY())
                .lastPageFooter(cmp.verticalList(signatureFooter(report.signaturePath()), cmp.pageXslashY(),
                        cmp.text("End of report.").setStyle(stl.style().setHorizontalTextAlignment(HorizontalTextAlignment.CENTER))))
                .addDetail(totalReport)
                .setDataSource(singleItemDataSource);
    }

    @VisibleForTesting
    @NotNull
    static JasperReportBuilder generateSupplementaryReport(@NotNull final SequencedPatientReport report) throws IOException {
        // @formatter:off
        final ComponentBuilder<?, ?> structuralVariantPage =
                cmp.verticalList(
                        MainPageTopSection.build("HMF Supplement", report.sampleReport()),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        supplementDisclaimerSection(),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        geneFusionReport(report),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        geneDisruptionReport(report),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        svExplanation(),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        geneFusionExplanation(),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        geneDisruptionExplanation()
                );
        // @formatter:on

        final DRDataSource singleItemDataSource = new DRDataSource("item");
        singleItemDataSource.add(new Object());

        return report().addDetail(structuralVariantPage).setDataSource(singleItemDataSource);
    }

    @NotNull
    private static ComponentBuilder<?, ?> mainPageAboutSection() {
        return toList("About this report",
                Lists.newArrayList("This test is performed for research purpose and is not meant to be used for clinical decision making.",
                        "Detailed information on the various fields can be found on the additional pages of this report.",
                        "For DRUP-specific questions, please contact the DRUP study team at DRUP@nki.nl.",
                        "For other questions, please contact us via info@hartwigmedicalfoundation.nl."));
    }

    @NotNull
    private static ComponentBuilder<?, ?> mainPageNotSequenceableSection(@NotNull final NotSequencedPatientReport report) {
        if (report.sampleReport().recipient() == null) {
            throw new IllegalStateException("No recipient address present for sample " + report.sampleReport().sampleCode());
        }

        final String title;
        final String subTitle;
        final String message;

        switch (report.reason()) {
            case LOW_DNA_YIELD: {
                title = "Notification tumor sample on hold for sequencing";
                subTitle = "Insufficient amount of DNA";
                message = "The amount of isolated DNA was <300 ng, which is insufficient for sequencing. "
                        + "This sample is on hold for further processing awaiting optimization of protocols.";
                break;
            }
            case LOW_TUMOR_PERCENTAGE: {
                title = "Notification of inadequate tumor sample";
                subTitle = "Insufficient percentage of tumor cells";
                message = "For sequencing we require a minimum of 30% tumor cells.";
                break;
            }
            case POST_ISOLATION_FAIL: {
                title = "Notification of inadequate tumor sample";
                subTitle = "Analysis has failed post DNA isolation";
                message = "This sample could not be processed to completion successfully.";
                break;
            }
            default: {
                title = "TITLE";
                subTitle = "SUB_TITLE";
                message = "MESSAGE";
            }
        }

        // @formatter:off
        return cmp.verticalList(
                cmp.text(title).setStyle(tableHeaderStyle().setFontSize(12))
                        .setHeight(20),
                cmp.text(subTitle).setStyle(dataTableStyle().setFontSize(12))
                        .setHeight(20),
                cmp.verticalGap(SECTION_VERTICAL_GAP),
                cmp.text(message).setStyle(fontStyle()),
                cmp.verticalGap(SECTION_VERTICAL_GAP),
                cmp.text("The received biopsies for the tumor sample for this patient were inadequate to obtain a reliable sequencing " +
                                "result. Therefore whole genome sequencing cannot be performed, " +
                                "unless additional fresh tumor material can be provided for a new assessment.")
                        .setStyle(fontStyle()),
                cmp.verticalGap(SECTION_VERTICAL_GAP),
                cmp.text("When possible, please resubmit using the same " + report.study().studyName() + "-number. " +
                        "In case additional tumor material cannot be provided, please be notified that the patient will not be " +
                        "evaluable for the " + report.study().studyCode() + " study.").setStyle(fontStyle()),
                cmp.verticalGap(SECTION_VERTICAL_GAP),
                cmp.text("The biopsies evaluated for this sample have arrived on " +
                        toFormattedDate(report.sampleReport().tumorArrivalDate())).setStyle(fontStyle()),
                cmp.verticalGap(SECTION_VERTICAL_GAP),
                cmp.text("This report is generated and verified by: " + report.user() + " and is addressed at " +
                        report.sampleReport().recipient()).setStyle(fontStyle()),
                cmp.verticalGap(SECTION_VERTICAL_GAP),
                cmp.text("For questions, please contact us via info@hartwigmedicalfoundation.nl")
                        .setStyle(fontStyle()));
        // @formatter:on
    }

    @NotNull
    private static ComponentBuilder<?, ?> variantReport(@NotNull final SequencedPatientReport report,
            @NotNull final HmfReporterData reporterData) {
        final String mutationalLoadAddition =
                "Patients with a mutational load over 140 could be " + "eligible for immunotherapy within DRUP.";

        final String geneMutationAddition = "Marked genes (*) are included in the DRUP study and indicate potential "
                + "eligibility in DRUP. Please note that the marking is NOT based on the specific variant reported for "
                + "this sample, but only on a gene-level.";

        // @formatter:off
        final ComponentBuilder<?, ?> table = report.variants().size() > 0 ?
                cmp.subreport(baseTable().fields(PatientDataSource.variantFields())
                        .columns(
                            col.column("Gene", PatientDataSource.GENE_FIELD).setFixedWidth(50),
                            col.column("Position", PatientDataSource.POSITION_FIELD),
                            col.column("Variant", PatientDataSource.VARIANT_FIELD),
                            col.column("Depth (VAF)", PatientDataSource.DEPTH_VAF_FIELD),
                            col.componentColumn("Predicted Effect", predictedEffectColumn()),
                            col.column("Cosmic", PatientDataSource.COSMIC_FIELD)
                                    .setHyperLink(hyperLink(new COSMICLinkExpression())).setStyle(linkStyle()),
                            col.column("Ploidy (TAF)", PatientDataSource.PLOIDY_TAF_FIELD)))
                        .setDataSource(PatientDataSource.fromVariants(report.variants(), reporterData)) :
                cmp.text("None").setStyle(fontStyle().setHorizontalTextAlignment(HorizontalTextAlignment.CENTER));

        return cmp.verticalList(
                cmp.text("Somatic Variants").setStyle(sectionHeaderStyle()),
                cmp.verticalGap(6),
                table,
                cmp.verticalGap(15),
                cmp.horizontalList(cmp.horizontalGap(10),
                        cmp.text("*").setStyle(fontStyle()).setWidth(2),
                        cmp.text(geneMutationAddition).setStyle(fontStyle())),
                cmp.verticalGap(15),
                cmp.text("Implied Tumor Purity: " + report.impliedPurityString())
                        .setStyle(tableHeaderStyle()),
                cmp.verticalGap(15),
                cmp.text("Tumor Mutational Load: " + Integer.toString(report.mutationalLoad()) + " **")
                        .setStyle(tableHeaderStyle()),
                cmp.verticalGap(15),
                cmp.horizontalList(cmp.horizontalGap(10),
                        cmp.text("**").setStyle(fontStyle()).setWidth(2),
                        cmp.text(mutationalLoadAddition).setStyle(fontStyle()))
        );
        // @formatter:on
    }

    @NotNull
    private static ComponentBuilder<?, ?> geneFusionReport(@NotNull final SequencedPatientReport report) {
        // @formatter:off
        final int fontSize = 6;
        final ComponentBuilder<?, ?> table;
        if (report.geneFusions().size() > 0) {
            table = cmp.subreport(
                         baseTable().setColumnStyle(dataStyle().setFontSize(fontSize))
                            .fields(PatientDataSource.geneFusionFields())
                            .columns(
                                col.column("5' Gene", PatientDataSource.GENE_FIELD).setFixedWidth(50),
                                col.column("5' Transcript", PatientDataSource.TRANSCRIPT_FIELD)
                                        .setHyperLink(hyperLink(fieldTranscriptLink(PatientDataSource.TRANSCRIPT_FIELD)))
                                        .setStyle(linkStyle().setFontSize(fontSize)),
                                col.column("5' Position", PatientDataSource.POSITION_FIELD),
                                col.column("5' Gene Context", PatientDataSource.SV_GENE_CONTEXT),
                                col.column("3' Gene", PatientDataSource.SV_PARTNER_GENE_FIELD).setFixedWidth(50),
                                col.column("3' Transcript", PatientDataSource.SV_PARTNER_TRANSCRIPT_FIELD)
                                        .setHyperLink(hyperLink(fieldTranscriptLink(PatientDataSource.SV_PARTNER_TRANSCRIPT_FIELD)))
                                        .setStyle(linkStyle().setFontSize(fontSize)),
                                col.column("3' Position", PatientDataSource.SV_PARTNER_POSITION_FIELD),
                                col.column("3' Gene Context", PatientDataSource.SV_PARTNER_CONTEXT_FIELD),
                                col.column("SV Type", PatientDataSource.SV_TYPE_FIELD).setFixedWidth(30),
                                col.column("VAF", PatientDataSource.SV_VAF).setFixedWidth(30)
                            )
                            .setDataSource(PatientDataSource.fromGeneFusions(report.geneFusions()))
                    );
        } else {
            table = cmp.text("None").setStyle(fontStyle().setHorizontalTextAlignment(HorizontalTextAlignment.CENTER));
        }
        // @formatter:on

        return cmp.verticalList(cmp.text("Gene Fusions").setStyle(sectionHeaderStyle()), cmp.verticalGap(6), table);
    }

    @NotNull
    private static ComponentBuilder<?, ?> geneDisruptionReport(@NotNull final SequencedPatientReport report) {
        // @formatter:off
        final int fontSize = 6;
        final ComponentBuilder<?, ?> table;
        if (report.geneDisruptions().size() > 0) {
            table = cmp.subreport(
                        baseTable().setColumnStyle(dataStyle().setFontSize(fontSize))
                            .fields(PatientDataSource.geneDisruptionFields())
                            .columns(
                                col.column("Gene", PatientDataSource.GENE_FIELD).setFixedWidth(50),
                                col.column("Transcript", PatientDataSource.TRANSCRIPT_FIELD)
                                        .setHyperLink(hyperLink(fieldTranscriptLink(PatientDataSource.TRANSCRIPT_FIELD)))
                                        .setStyle(linkStyle().setFontSize(fontSize)),
                                col.column("Position", PatientDataSource.POSITION_FIELD),
                                col.column("Gene Context", PatientDataSource.SV_GENE_CONTEXT),
                                col.column("Orientation", PatientDataSource.SV_ORIENTATION_FIELD),
                                col.column("Partner", PatientDataSource.SV_PARTNER_POSITION_FIELD),
                                col.column("Type", PatientDataSource.SV_TYPE_FIELD).setFixedWidth(30),
                                col.column("VAF", PatientDataSource.SV_VAF).setFixedWidth(30)
                            )
                            .setDataSource(PatientDataSource.fromGeneDisruptions(report.geneDisruptions()))
                    );
        } else {
            table = cmp.text("None").setStyle(fontStyle().setHorizontalTextAlignment(HorizontalTextAlignment.CENTER));
        }
        // @formatter:on

        return cmp.verticalList(cmp.text("Gene Disruptions").setStyle(sectionHeaderStyle()), cmp.verticalGap(6), table);
    }

    @NotNull
    private static ComponentBuilder<?, ?> svExplanation() {
        return toList("Details on structural variants", Lists.newArrayList("The analysis is based on reference genome version GRCh37.",
                "Reported variants are only indicative and have NOT been verified via RNA sequencing."));
    }

    @NotNull
    private static ComponentBuilder<?, ?> geneFusionExplanation() {
        return toList("Details on reported gene fusions",
                Lists.newArrayList("Only intronic in-frame fusions or whole exon deletions are reported.",
                        "The canonical, or otherwise longest transcript validly fused is reported.",
                        "Fusions are restricted to those in the Fusion Gene list curated by COSMIC.",
                        "We additionally select fusions where one partner occurs in the 5' or 3' position in COSMIC >3 times.",
                        "Whole exon deletions are also restricted to this list.",
                        "See http://cancer.sanger.ac.uk/cosmic/fusion for more information."));
    }

    @NotNull
    private static ComponentBuilder<?, ?> geneDisruptionExplanation() {
        return toList("Details on reported gene disruptions",
                Lists.newArrayList("Only the canonical transcript of disrupted genes are reported.",
                        "Reported gene disruptions are restricted to those that occur in the HMF Panel."));
    }

    @NotNull
    private static ComponentBuilder<?, ?> copyNumberReport(@NotNull final SequencedPatientReport report) {
        // @formatter:off
        final ComponentBuilder<?, ?> table = report.copyNumbers().size() > 0 ?
                cmp.subreport(baseTable().fields(PatientDataSource.copyNumberFields())
                        .columns(
                            col.column("Chromosome", PatientDataSource.CHROMOSOME_FIELD),
                            col.column("Band", PatientDataSource.BAND_FIELD),
                            col.column("Gene", PatientDataSource.GENE_FIELD),
                            col.column("Type", PatientDataSource.COPY_NUMBER_TYPE_FIELD),
                            col.column("Copies", PatientDataSource.COPY_NUMBER_FIELD))
                        .setDataSource(PatientDataSource.fromCopyNumbers(report.copyNumbers()))) :
                cmp.text("None").setStyle(fontStyle().setHorizontalTextAlignment(HorizontalTextAlignment.CENTER));

        return cmp.verticalList(
                cmp.text("Somatic Copy Numbers").setStyle(sectionHeaderStyle()),
                cmp.verticalGap(6),
                table);
        // @formatter:on
    }

    @NotNull
    private static ComponentBuilder<?, ?> variantFieldExplanationSection() {
        return toList("Details on reported genomic variant fields",
                Lists.newArrayList("The analysis is based on reference genome version GRCh37.",
                        "The 'position' refers to the chromosome and start base of the variant with " + "respect to this reference genome.",
                        "The 'variant' displays what was expected as reference base and what " + "was found instead ('ref' > 'alt').",
                        "The 'depth (VAF)' displays the number of observations of the specific variant versus "
                                + "the total number of reads in this location in the format 'alt / total (%)'.",
                        "The 'predicted effect' provides additional information on the variant, including "
                                + "the change in coding sequence ('c.'), the change in protein ('p.') and "
                                + "the predicted impact on the final protein on the second line of this field.",
                        "The 'cosmic' fields display a link to the COSMIC database which contains "
                                + "additional information on the variant. If the variant could not be found in the "
                                + "COSMIC database, this field will be left blank. The Cosmic v76 database is used "
                                + "to look-up these IDs.",
                        "The implied tumor purity is the percentage of tumor DNA in the biopsy based on analysis of "
                                + "whole genome data.",
                        "The 'Ploidy (TAF)' field displays the tumor ploidy for the observed variant. The ploidy "
                                + "has been adjusted for the implied tumor purity (see above) and is shown as a "
                                + "proportion of A’s and B’s (e.g. AAABB for 3 copies A, and 2 copies B). "
                                + "The copy number is the sum of A’s and B’s. The TAF (Tumor adjusted Alternative "
                                + "Frequency) value refers to the alternative allele frequency after correction " + "for tumor purity.",
                        "The tumor mutational load is the total number of somatic missense variants found across"
                                + " the whole genome of the tumor biopsy."));
    }

    @NotNull
    private static ComponentBuilder<?, ?> copyNumberExplanationSection() {
        return toList("Details on reported copy numbers", Lists.newArrayList(
                "The lowest copy number value along the exonic regions of the canonical transcript is determined as "
                        + "a measure for the gene's copy number.",
                "Copy numbers are corrected for the implied tumor purity and represent the number of copies in the tumor DNA.",
                "Any gene with no copies is reported as loss.", "Any gene with at least 8 copies is reported as a gain.",
                "Any gene with more copies than 2.2 times the average tumor ploidy is reported as a gain."));
    }

    @NotNull
    private static ComponentBuilder<?, ?> supplementDisclaimerSection() {
        //@formatter:off
        final List<String> lines = Lists.newArrayList("This supplement is a prototype for new types of reporting that " +
                        "may or may not eventually end up in the actual sequencing report",
                "Findings should be considered suspicious and should not be used for clinical decision making without " +
                        "validation using certified assays"
                );
        //@formatter:on

        return toList("Disclaimer", lines);
    }

    @NotNull
    private static ComponentBuilder<?, ?> disclaimerSection() {
        //@formatter:off
        final List<String> lines = Lists.newArrayList("This test is not certified for diagnostic purposes.",
                "The data on which this report is based has passed all internal quality controls.",
                "When no mutations are reported, the absence of mutations is not guaranteed.",
                "The findings in this report are not meant to be used for clinical decision making without validation of "
                        + "findings using certified assays.");
        //@formatter:on
        return toList("Disclaimer", lines);
    }

    @NotNull
    private static ComponentBuilder<?, ?> sampleDetailsSection(@NotNull final PatientReport report) {
        if (report.sampleReport().recipient() == null) {
            throw new IllegalStateException("No recipient address present for sample " + report.sampleReport().sampleCode());
        }
        final List<String> lines = Lists.newArrayList();

        if (report instanceof SequencedPatientReport) {
            lines.addAll(
                    Lists.newArrayList("The samples have been sequenced at Hartwig Medical Foundation, Science Park 408, 1098XH Amsterdam",
                            "The samples have been analyzed by Next Generation Sequencing"));
        }

        //@formatter:off
        lines.addAll(Lists.newArrayList(
                "This experiment is performed on the tumor sample which arrived on " +
                        toFormattedDate(report.sampleReport().tumorArrivalDate()),
                "This experiment is performed on the blood sample which arrived on " +
                        toFormattedDate(report.sampleReport().bloodArrivalDate()),
                "This experiment is performed according to lab procedures: " + report.sampleReport().labProcedures(),
                "This report is generated and verified by: " + report.user(),
                "This report is addressed at: " + report.sampleReport().recipient()));
        //@formatter:on
        report.comments().ifPresent(comments -> lines.add("Comments: " + comments));

        return toList("Sample details", lines);
    }

    @NotNull
    private static String toFormattedDate(@Nullable final LocalDate date) {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
        return date != null ? formatter.format(date) : "?";
    }

    @NotNull
    private static ComponentBuilder<?, ?> signatureFooter(@NotNull final String signaturePath) {
        // @formatter:off
        return cmp.horizontalList(
                cmp.horizontalGap(370),
                cmp.xyList()
                    .add(40, 5, cmp.image(signaturePath))
                    .add(0, 0,cmp.text("Edwin Cuppen,"))
                    .add(0, 15, cmp.text("Director Hartwig Medical Foundation").setWidth(190)),
                cmp.horizontalGap(10));
        // @formatter:on
    }

    @NotNull
    public static VerticalListBuilder toList(@NotNull final String title, @NotNull final Iterable<String> lines) {
        final VerticalListBuilder list = cmp.verticalList();
        list.add(cmp.horizontalList(cmp.horizontalGap(TEXT_HEADER_INDENT), cmp.text(title).setStyle(fontStyle().bold().setFontSize(11))),
                cmp.verticalGap(HEADER_TO_DETAIL_VERTICAL_GAP));
        boolean isFirst = true;
        for (final String line : lines) {
            if (!isFirst) {
                list.add(cmp.verticalGap(DETAIL_TO_DETAIL_VERTICAL_GAP));
            }
            list.add(cmp.horizontalList(cmp.horizontalGap(TEXT_DETAIL_INDENT), cmp.text("- ").setStyle(fontStyle()).setWidth(LIST_INDENT),
                    cmp.text(line).setStyle(fontStyle()), cmp.horizontalGap(TEXT_END_OF_LINE_GAP)));

            isFirst = false;
        }
        return list;
    }

    @NotNull
    private static AbstractSimpleExpression<String> fieldTranscriptLink(@NotNull final FieldBuilder<?> field) {
        return new AbstractSimpleExpression<String>() {
            @Override
            public String evaluate(@NotNull final ReportParameters data) {
                return "http://grch37.ensembl.org/Homo_sapiens/Transcript/Summary?db=core;t=" + data.getValue(field.getName());
            }
        };
    }

    @NotNull
    private static ComponentBuilder<?, ?> predictedEffectColumn() {
        return cmp.verticalList(cmp.horizontalList(cmp.text(DataExpression.fromField(PatientDataSource.HGVS_CODING_FIELD)),
                cmp.text(DataExpression.fromField(PatientDataSource.HGVS_PROTEIN_FIELD))),
                cmp.text(DataExpression.fromField(PatientDataSource.CONSEQUENCE_FIELD))).setFixedWidth(170);
    }
}
