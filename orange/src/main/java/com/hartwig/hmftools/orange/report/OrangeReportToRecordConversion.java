package com.hartwig.hmftools.orange.report;

import com.hartwig.hmftools.common.drivercatalog.DriverCatalog;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.purple.GeneCopyNumber;
import com.hartwig.hmftools.common.variant.AllelicDepth;
import com.hartwig.hmftools.datamodel.orange.ImmutableOrangeRecord;
import com.hartwig.hmftools.datamodel.orange.OrangeRecord;
import com.hartwig.hmftools.datamodel.orange.OrangeRefGenomeVersion;
import com.hartwig.hmftools.datamodel.purple.*;
import com.hartwig.hmftools.orange.algo.OrangeReport;
import com.hartwig.hmftools.orange.algo.purple.PurpleInterpretedData;

import java.util.List;
import java.util.Objects;

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
                .lilac(report.lilac())
                .virusInterpreter(report.virusInterpreter())
                .chord(report.chord())
                .cuppa(report.cuppa())
                .peach(report.peach())
                .plots(report.plots())
                .build();
    }

    private static PurpleRecord convert(PurpleInterpretedData purpleInterpretedData) {
        var somaticVariantIterator = purpleInterpretedData.allSomaticVariants().stream()
                .map(OrangeReportToRecordConversion::convert)
                .iterator();
        var germlineVariantIterator = argumentOrEmpty(purpleInterpretedData.allGermlineVariants()).stream()
                .map(OrangeReportToRecordConversion::convert)
                .iterator();
        var reportableGermlineVariantIterator = argumentOrEmpty(purpleInterpretedData.reportableGermlineVariants()).stream()
                .map(OrangeReportToRecordConversion::convert)
                .iterator();
        return ImmutablePurpleRecord.builder()
                .fit(purpleInterpretedData.fit())
                .characteristics(purpleInterpretedData.characteristics())
                .somaticDrivers(purpleInterpretedData.somaticDrivers())
                .germlineDrivers(purpleInterpretedData.germlineDrivers())
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

    private static PurpleVariant convert(com.hartwig.hmftools.orange.algo.purple.PurpleVariant variant) {
        return ImmutablePurpleVariant.builder()
                .type(PurpleVariantType.valueOf(variant.type().name()))
                .gene(variant.gene())
                .chromosome(variant.chromosome())
                .position(variant.position())
                .ref(variant.ref())
                .alt(variant.alt())
                .canonicalImpact(variant.canonicalImpact())
                .otherImpacts(variant.otherImpacts())
                .hotspot(Hotspot.valueOf(variant.hotspot().name()))
                .reported(variant.reported())
                .tumorDepth(variant.tumorDepth())
                .adjustedCopyNumber(variant.adjustedCopyNumber())
                .minorAlleleCopyNumber(variant.minorAlleleCopyNumber())
                .variantCopyNumber(variant.variantCopyNumber())
                .biallelic(variant.biallelic())
                .genotypeStatus(PurpleGenotypeStatus.valueOf(variant.genotypeStatus().name()))
                .subclonalLikelihood(variant.subclonalLikelihood())
                .localPhaseSets(variant.localPhaseSets())
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
                .geneName(geneCopyNumber.geneName())
                .minCopyNumber(geneCopyNumber.minCopyNumber())
                .minMinorAlleleCopyNumber(geneCopyNumber.minMinorAlleleCopyNumber())
                .build();
    }

    private static <T> List<T> argumentOrEmpty(List<T> obj) {
        return Objects.requireNonNullElseGet(obj, List::of);
    }
}
