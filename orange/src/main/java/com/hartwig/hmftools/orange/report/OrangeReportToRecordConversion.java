package com.hartwig.hmftools.orange.report;

import com.hartwig.hmftools.common.chord.ChordData;
import com.hartwig.hmftools.common.drivercatalog.DriverCatalog;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.hla.LilacSummaryData;
import com.hartwig.hmftools.common.purple.GeneCopyNumber;
import com.hartwig.hmftools.common.variant.AllelicDepth;
import com.hartwig.hmftools.datamodel.chord.ChordRecord;
import com.hartwig.hmftools.datamodel.chord.ChordStatus;
import com.hartwig.hmftools.datamodel.chord.ImmutableChordRecord;
import com.hartwig.hmftools.datamodel.hla.ImmutableLilacAllele;
import com.hartwig.hmftools.datamodel.hla.ImmutableLilacRecord;
import com.hartwig.hmftools.datamodel.hla.LilacAllele;
import com.hartwig.hmftools.datamodel.hla.LilacRecord;
import com.hartwig.hmftools.datamodel.orange.ImmutableOrangeRecord;
import com.hartwig.hmftools.datamodel.orange.OrangeRecord;
import com.hartwig.hmftools.datamodel.orange.OrangeRefGenomeVersion;
import com.hartwig.hmftools.datamodel.peach.ImmutablePeachGenotype;
import com.hartwig.hmftools.datamodel.peach.ImmutablePeachRecord;
import com.hartwig.hmftools.datamodel.peach.PeachGenotype;
import com.hartwig.hmftools.datamodel.peach.PeachRecord;
import com.hartwig.hmftools.datamodel.purple.*;
import com.hartwig.hmftools.datamodel.virus.*;
import com.hartwig.hmftools.orange.algo.OrangeReport;
import com.hartwig.hmftools.orange.algo.purple.PurityPloidyFit;
import com.hartwig.hmftools.orange.algo.purple.PurpleInterpretedData;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class OrangeReportToRecordConversion {

    private OrangeReportToRecordConversion() {
    }

    public static OrangeRecord convert(OrangeReport report) {
        RefGenomeVersion refGenomeVersion = report.refGenomeVersion();
        return ImmutableOrangeRecord.builder()
                .sampleId(report.sampleId())
                .experimentDate(report.experimentDate())
                .refGenomeVersion(OrangeRefGenomeVersion.valueOf(refGenomeVersion.name()))
                .purple(convert(report.purple()))
                .linx(report.linx())
                .lilac(convert(report.lilac()))
                .virusInterpreter(Optional.ofNullable(report.virusInterpreter()).map(OrangeReportToRecordConversion::convert).orElse(null))
                .chord(Optional.ofNullable(report.chord()).map(OrangeReportToRecordConversion::convert).orElse(null))
                .cuppa(report.cuppa())
                .peach(Optional.ofNullable(report.peach()).map(OrangeReportToRecordConversion::convert).orElse(null))
                .plots(report.plots())
                .build();
    }

    private static PeachRecord convert(List<com.hartwig.hmftools.common.peach.PeachGenotype> peachGenotypes) {
        return ImmutablePeachRecord.builder()
                .entries(() -> peachGenotypes.stream().map(OrangeReportToRecordConversion::convert).iterator())
                .build();
    }

    private static PeachGenotype convert(com.hartwig.hmftools.common.peach.PeachGenotype peachGenotype) {
        return ImmutablePeachGenotype.builder()
                .gene(peachGenotype.gene())
                .haplotype(peachGenotype.haplotype())
                .function(peachGenotype.function())
                .linkedDrugs(peachGenotype.linkedDrugs())
                .urlPrescriptionInfo(peachGenotype.urlPrescriptionInfo())
                .panelVersion(peachGenotype.panelVersion())
                .repoVersion(peachGenotype.repoVersion())
                .build();
    }

    private static ChordRecord convert(ChordData chordData) {
        return ImmutableChordRecord.builder()
                .hrdValue(chordData.hrdValue())
                .hrStatus(ChordStatus.valueOf(chordData.hrStatus().name()))
                .build();
    }

    private static VirusInterpreterData convert(com.hartwig.hmftools.common.virus.VirusInterpreterData interpreterData) {
        return ImmutableVirusInterpreterData.builder()
                .allViruses(() -> interpreterData.allViruses().stream().map(OrangeReportToRecordConversion::convert).iterator())
                .reportableViruses(() -> interpreterData.reportableViruses().stream().map(OrangeReportToRecordConversion::convert).iterator())
                .build();
    }

    private static AnnotatedVirus convert(com.hartwig.hmftools.common.virus.AnnotatedVirus annotatedVirus) {
        return ImmutableAnnotatedVirus.builder()
                .name(annotatedVirus.name())
                .qcStatus(VirusBreakendQCStatus.valueOf(annotatedVirus.qcStatus().name()))
                .integrations(annotatedVirus.integrations())
                .interpretation(annotatedVirus.interpretation())
                .percentageCovered(annotatedVirus.percentageCovered())
                .reported(annotatedVirus.reported())
                .build();
    }

    private static LilacRecord convert(LilacSummaryData lilacSummaryData) {
        return ImmutableLilacRecord.builder()
                .qc(lilacSummaryData.qc())
                .alleles(() -> lilacSummaryData.alleles().stream().map(OrangeReportToRecordConversion::convert).iterator())
                .build();
    }

    private static LilacAllele convert(com.hartwig.hmftools.common.hla.LilacAllele allele) {
        return ImmutableLilacAllele.builder()
                .allele(allele.allele())
                .tumorCopyNumber(allele.tumorCopyNumber())
                .somaticMissense(allele.somaticMissense())
                .somaticNonsenseOrFrameshift(allele.somaticNonsenseOrFrameshift())
                .somaticSplice(allele.somaticSplice())
                .somaticSynonymous(allele.somaticSynonymous())
                .somaticInframeIndel(allele.somaticInframeIndel())
                .build();
    }

    private static PurpleRecord convert(PurpleInterpretedData purpleInterpretedData) {
        var somaticDriverIterator = purpleInterpretedData.somaticDrivers().stream()
                .map(OrangeReportToRecordConversion::convert)
                .iterator();
        var germlineDriverIterator = Objects.requireNonNullElseGet(purpleInterpretedData.germlineDrivers(), List::<DriverCatalog>of).stream()
                .map(OrangeReportToRecordConversion::convert)
                .iterator();
        var somaticVariantIterator = purpleInterpretedData.allSomaticVariants().stream()
                .map(OrangeReportToRecordConversion::convert)
                .iterator();
        var germlineVariantIterator = Objects.requireNonNullElseGet(purpleInterpretedData.allGermlineVariants(), List::<com.hartwig.hmftools.orange.algo.purple.PurpleVariant>of).stream()
                .map(OrangeReportToRecordConversion::convert)
                .iterator();
        var reportableGermlineVariantIterator = Objects.requireNonNullElseGet(purpleInterpretedData.reportableGermlineVariants(), List::<com.hartwig.hmftools.orange.algo.purple.PurpleVariant>of).stream()
                .map(OrangeReportToRecordConversion::convert)
                .iterator();
        return ImmutablePurpleRecord.builder()
                .fit(convert(purpleInterpretedData.fit()))
                .characteristics(convert(purpleInterpretedData.characteristics()))
                .somaticDrivers(() -> somaticDriverIterator)
                .germlineDrivers(() -> germlineDriverIterator)
                .allSomaticVariants(() -> somaticVariantIterator)
                .reportableSomaticVariants(() -> purpleInterpretedData.reportableSomaticVariants().stream().map(OrangeReportToRecordConversion::convert).iterator())
                .allGermlineVariants(() -> germlineVariantIterator)
                .reportableGermlineVariants(() -> reportableGermlineVariantIterator)
                .allSomaticCopyNumbers(() -> purpleInterpretedData.allSomaticCopyNumbers().stream().map(OrangeReportToRecordConversion::convert).iterator())
                .allSomaticGeneCopyNumbers(() -> purpleInterpretedData.allSomaticGeneCopyNumbers().stream().map(OrangeReportToRecordConversion::convert).iterator())
                .suspectGeneCopyNumbersWithLOH(() -> purpleInterpretedData.suspectGeneCopyNumbersWithLOH().stream().map(OrangeReportToRecordConversion::convert).iterator())
                .allSomaticGainsLosses(purpleInterpretedData.reportableSomaticGainsLosses())
                .reportableSomaticGainsLosses(purpleInterpretedData.reportableSomaticGainsLosses())
                .build();
    }

    private static PurpleFit convert(PurityPloidyFit fit) {
        var qcStatusIterator = fit.qc().status().stream()
                .map(status -> PurpleQCStatus.valueOf(status.name()))
                .iterator();
        return ImmutablePurpleFit.builder()
                .qcStatus(() -> qcStatusIterator)
                .hasSufficientQuality(fit.hasSufficientQuality())
                .containsTumorCells(fit.containsTumorCells())
                .purity(fit.purity())
                .ploidy(fit.ploidy())
                .build();
    }

    private static PurpleCharacteristics convert(com.hartwig.hmftools.orange.algo.purple.PurpleCharacteristics characteristics) {
        return ImmutablePurpleCharacteristics.builder()
                .microsatelliteIndelsPerMb(characteristics.microsatelliteIndelsPerMb())
                .microsatelliteStatus(PurpleMicrosatelliteStatus.valueOf(characteristics.microsatelliteStatus().name()))
                .tumorMutationalBurdenPerMb(characteristics.tumorMutationalBurdenPerMb())
                .tumorMutationalBurdenStatus(PurpleTumorMutationalStatus.valueOf(characteristics.tumorMutationalBurdenStatus().name()))
                .tumorMutationalLoad(characteristics.tumorMutationalLoad())
                .tumorMutationalLoadStatus(PurpleTumorMutationalStatus.valueOf(characteristics.tumorMutationalLoadStatus().name()))
                .build();
    }

    private static PurpleDriver convert(DriverCatalog catalog) {
        return ImmutablePurpleDriver.builder()
                .gene(catalog.gene())
                .transcript(catalog.transcript())
                .driver(PurpleDriverType.valueOf(catalog.driver().name()))
                .transcript(catalog.transcript())
                .build();
    }

    private static PurpleVariant convert(com.hartwig.hmftools.orange.algo.purple.PurpleVariant variant) {
        var otherImpactsIterator = variant.otherImpacts().stream()
                .map(OrangeReportToRecordConversion::convert)
                .iterator();
        return ImmutablePurpleVariant.builder()
                .type(PurpleVariantType.valueOf(variant.type().name()))
                .gene(variant.gene())
                .chromosome(variant.chromosome())
                .position(variant.position())
                .ref(variant.ref())
                .alt(variant.alt())
                .canonicalImpact(convert(variant.canonicalImpact()))
                .otherImpacts(() -> otherImpactsIterator)
                .hotspot(Hotspot.valueOf(variant.hotspot().name()))
                .reported(variant.reported())
                .tumorDepth(convert(variant.tumorDepth()))
                .adjustedCopyNumber(variant.adjustedCopyNumber())
                .minorAlleleCopyNumber(variant.minorAlleleCopyNumber())
                .variantCopyNumber(variant.variantCopyNumber())
                .biallelic(variant.biallelic())
                .genotypeStatus(PurpleGenotypeStatus.valueOf(variant.genotypeStatus().name()))
                .subclonalLikelihood(variant.subclonalLikelihood())
                .localPhaseSets(variant.localPhaseSets())
                .build();
    }

    private static PurpleTranscriptImpact convert(com.hartwig.hmftools.orange.algo.purple.PurpleTranscriptImpact transcriptImpact) {
        var effectIterator = transcriptImpact.effects().stream().map(effect -> PurpleVariantEffect.valueOf(effect.name())).iterator();
        return ImmutablePurpleTranscriptImpact.builder()
                .transcript(transcriptImpact.transcript())
                .hgvsCodingImpact(transcriptImpact.hgvsCodingImpact())
                .hgvsProteinImpact(transcriptImpact.hgvsProteinImpact())
                .affectedCodon(transcriptImpact.affectedCodon())
                .affectedExon(transcriptImpact.affectedExon())
                .spliceRegion(transcriptImpact.spliceRegion())
                .effects(() -> effectIterator)
                .codingEffect(PurpleCodingEffect.valueOf(transcriptImpact.codingEffect().name()))
                .build();
    }

    private static PurpleAllelicDepth convert(AllelicDepth allelicDepth) {
        return ImmutablePurpleAllelicDepth.builder()
                .alleleReadCount(allelicDepth.alleleReadCount())
                .totalReadCount(allelicDepth.totalReadCount())
                .build();
    }

    private static PurpleCopyNumber convert(com.hartwig.hmftools.common.purple.PurpleCopyNumber copyNumber) {
        return ImmutablePurpleCopyNumber.builder()
                .chromosome(copyNumber.chromosome())
                .start(copyNumber.start())
                .end(copyNumber.end())
                .build();
    }

    private static PurpleGeneCopyNumber convert(GeneCopyNumber geneCopyNumber) {
        return ImmutablePurpleGeneCopyNumber.builder()
                .chromosome(geneCopyNumber.chromosome())
                .chromosomeBand(geneCopyNumber.chromosomeBand())
                .gene(geneCopyNumber.geneName())
                .minCopyNumber(geneCopyNumber.minCopyNumber())
                .minMinorAlleleCopyNumber(geneCopyNumber.minMinorAlleleCopyNumber())
                .build();
    }
}
