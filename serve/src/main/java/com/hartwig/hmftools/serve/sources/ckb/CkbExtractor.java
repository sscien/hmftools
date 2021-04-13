package com.hartwig.hmftools.serve.sources.ckb;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.ckb.classification.EventAndGeneExtractor;
import com.hartwig.hmftools.ckb.datamodel.CkbEntry;
import com.hartwig.hmftools.ckb.datamodel.variant.Variant;
import com.hartwig.hmftools.common.refseq.RefSeq;
import com.hartwig.hmftools.common.serve.Knowledgebase;
import com.hartwig.hmftools.common.serve.classification.EventType;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;
import com.hartwig.hmftools.serve.actionability.ActionableEvent;
import com.hartwig.hmftools.serve.actionability.characteristic.ActionableCharacteristic;
import com.hartwig.hmftools.serve.actionability.fusion.ActionableFusion;
import com.hartwig.hmftools.serve.actionability.gene.ActionableGene;
import com.hartwig.hmftools.serve.actionability.hotspot.ActionableHotspot;
import com.hartwig.hmftools.serve.actionability.range.ActionableRange;
import com.hartwig.hmftools.serve.extraction.ActionableEventFactory;
import com.hartwig.hmftools.serve.extraction.EventExtractorOutput;
import com.hartwig.hmftools.serve.extraction.ExtractionFunctions;
import com.hartwig.hmftools.serve.extraction.ExtractionResult;
import com.hartwig.hmftools.serve.extraction.ImmutableExtractionResult;
import com.hartwig.hmftools.serve.extraction.codon.CodonAnnotation;
import com.hartwig.hmftools.serve.extraction.codon.CodonFunctions;
import com.hartwig.hmftools.serve.extraction.codon.ImmutableKnownCodon;
import com.hartwig.hmftools.serve.extraction.codon.KnownCodon;
import com.hartwig.hmftools.serve.extraction.copynumber.CopyNumberFunctions;
import com.hartwig.hmftools.serve.extraction.copynumber.ImmutableKnownCopyNumber;
import com.hartwig.hmftools.serve.extraction.copynumber.KnownCopyNumber;
import com.hartwig.hmftools.serve.extraction.exon.ExonAnnotation;
import com.hartwig.hmftools.serve.extraction.exon.ExonFunctions;
import com.hartwig.hmftools.serve.extraction.exon.ImmutableKnownExon;
import com.hartwig.hmftools.serve.extraction.exon.KnownExon;
import com.hartwig.hmftools.serve.extraction.fusion.FusionFunctions;
import com.hartwig.hmftools.serve.extraction.fusion.ImmutableKnownFusionPair;
import com.hartwig.hmftools.serve.extraction.fusion.KnownFusionPair;
import com.hartwig.hmftools.serve.extraction.hotspot.HotspotFunctions;
import com.hartwig.hmftools.serve.extraction.hotspot.ImmutableKnownHotspot;
import com.hartwig.hmftools.serve.extraction.hotspot.KnownHotspot;
import com.hartwig.hmftools.serve.util.ProgressTracker;
import com.hartwig.hmftools.vicc.annotation.ProteinAnnotationExtractor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CkbExtractor {

    private static final Logger LOGGER = LogManager.getLogger(CkbExtractor.class);

    @NotNull
    private final com.hartwig.hmftools.serve.extraction.EventExtractor eventExtractor;
    @NotNull
    private final ActionableEvidenceFactory actionableEvidenceFactory;

    public CkbExtractor(@NotNull final com.hartwig.hmftools.serve.extraction.EventExtractor eventExtractor,
            @NotNull final ActionableEvidenceFactory actionableEvidenceFactory) {
        this.eventExtractor = eventExtractor;
        this.actionableEvidenceFactory = actionableEvidenceFactory;
    }

    @NotNull
    public ExtractionResult extract(@NotNull List<CkbEntry> ckbEntries, @NotNull List<RefSeq> refSeqMatchFile) {
        List<ExtractionResult> extractions = Lists.newArrayList();

        ProgressTracker tracker = new ProgressTracker("CKB", ckbEntries.size());
        for (CkbEntry entry : ckbEntries) {
            if (entry.variants().size() == 1) {
                Variant variant = entry.variants().get(0);
                String gene = EventAndGeneExtractor.extractGene(variant);
                String event = EventAndGeneExtractor.extractEvent(variant);
                String canonicalTranscript = extractCanonicalTranscript(variant.gene().canonicalTranscript(), refSeqMatchFile);

                EventExtractorOutput eventExtractorOutput = eventExtractor.extract(gene, canonicalTranscript, entry.type(), event);
                Set<ActionableEvent> actionableEvents = actionableEvidenceFactory.toActionableEvents(entry);

                CkbExtractorResult ckbExtractorResult = toResult(eventExtractorOutput, actionableEvents);

                extractions.add(toExtractionResult(actionableEvents, ckbExtractorResult));
                extractions.add(ImmutableExtractionResult.builder()
                        .knownHotspots(convertToHotspots(ckbExtractorResult, entry))
                        .knownCodons(convertToCodons(ckbExtractorResult))
                        .knownExons(convertToExons(ckbExtractorResult))
                        .knownCopyNumbers(convertToKnownAmpsDels(ckbExtractorResult))
                        .knownFusionPairs(convertToKnownFusions(ckbExtractorResult))
                        .build());

                if (entry.type() == EventType.UNKNOWN) {
                    LOGGER.warn("No event type known for '{}' on '{}'", variant.variant(), variant.gene().geneSymbol());
                }
            }
            tracker.update();
        }

        actionableEvidenceFactory.evaluateCuration();

        return ExtractionFunctions.merge(extractions);
    }

    @Nullable
    private static String extractCanonicalTranscript(@NotNull String refseqToMatch, @NotNull List<RefSeq> refSeqMatchFile) {
        for (RefSeq refSeq : refSeqMatchFile) {
            if (refSeq.dbPrimaryAcc().equals(refseqToMatch)) {
                return refSeq.transcriptId();
            }
        }
        return null;
    }

    @NotNull
    private static Set<KnownHotspot> convertToHotspots(@NotNull CkbExtractorResult ckbExtractorResult, @NotNull CkbEntry entry) {
        ProteinAnnotationExtractor proteinExtractor = new ProteinAnnotationExtractor();
        Set<KnownHotspot> hotspots = Sets.newHashSet();
        if (ckbExtractorResult.hotspots() != null) {
            for (VariantHotspot hotspot : ckbExtractorResult.hotspots()) {
                hotspots.add(ImmutableKnownHotspot.builder()
                        .from(hotspot)
                        .addSources(Knowledgebase.CKB)
                        .gene(entry.variants().get(0).gene().geneSymbol())
                        .transcript(entry.variants().get(0).gene().canonicalTranscript())
                        .proteinAnnotation(proteinExtractor.apply(entry.variants().get(0).variant()))
                        .build());
            }
        }
        return HotspotFunctions.consolidate(hotspots);
    }

    @NotNull
    private static Set<KnownCodon> convertToCodons(@NotNull CkbExtractorResult ckbExtractorResult) {
        Set<KnownCodon> codons = Sets.newHashSet();

        if (ckbExtractorResult.codons() != null) {
            for (CodonAnnotation codonAnnotation : ckbExtractorResult.codons()) {
                codons.add(ImmutableKnownCodon.builder().annotation(codonAnnotation).addSources(Knowledgebase.CKB).build());
            }
        }
        return CodonFunctions.consolidate(codons);
    }

    @NotNull
    private static Set<KnownExon> convertToExons(@NotNull CkbExtractorResult ckbExtractorResult) {
        Set<KnownExon> exons = Sets.newHashSet();

        if (ckbExtractorResult.exons() != null) {
            for (ExonAnnotation exonAnnotation : ckbExtractorResult.exons()) {
                exons.add(ImmutableKnownExon.builder().annotation(exonAnnotation).addSources(Knowledgebase.CKB).build());
            }
        }
        return ExonFunctions.consolidate(exons);
    }

    @NotNull
    private static Set<KnownCopyNumber> convertToKnownAmpsDels(@NotNull CkbExtractorResult ckbExtractorResult) {
        Set<KnownCopyNumber> copyNumbers = Sets.newHashSet();
        if (ckbExtractorResult.knownCopyNumber() != null) {
            copyNumbers.add(ImmutableKnownCopyNumber.builder()
                    .from(ckbExtractorResult.knownCopyNumber())
                    .addSources(Knowledgebase.CKB)
                    .build());

        }
        return CopyNumberFunctions.consolidate(copyNumbers);
    }

    @NotNull
    private static Set<KnownFusionPair> convertToKnownFusions(@NotNull CkbExtractorResult ckbExtractorResult) {
        Set<KnownFusionPair> fusions = Sets.newHashSet();
        if (ckbExtractorResult.knownFusionPair() != null) {
            fusions.add(ImmutableKnownFusionPair.builder()
                    .from(ckbExtractorResult.knownFusionPair())
                    .addSources(Knowledgebase.CKB)
                    .build());
        }
        return FusionFunctions.consolidate(fusions);
    }

    @NotNull
    private static CkbExtractorResult toResult(@NotNull EventExtractorOutput eventExtractorOutput,
            @NotNull Set<ActionableEvent> actionableEvents) {
        return ImmutableCkbExtractorResult.builder()
                .hotspots(eventExtractorOutput.hotspots())
                .codons(eventExtractorOutput.codons())
                .exons(eventExtractorOutput.exons())
                .geneLevelEvent(eventExtractorOutput.geneLevelEvent())
                .knownCopyNumber(eventExtractorOutput.knownCopyNumber())
                .knownFusionPair(eventExtractorOutput.knownFusionPair())
                .characteristic(eventExtractorOutput.characteristic())
                .actionableEvents(actionableEvents)
                .build();
    }

    @NotNull
    private static ExtractionResult toExtractionResult(@NotNull Set<ActionableEvent> actionableEvents,
            @NotNull CkbExtractorResult ckbExtractorResult) {
        Set<ActionableHotspot> actionableHotspots = Sets.newHashSet();
        Set<ActionableRange> actionableRanges = Sets.newHashSet();
        Set<ActionableGene> actionableGenes = Sets.newHashSet();
        Set<ActionableFusion> actionableFusions = Sets.newHashSet();
        Set<ActionableCharacteristic> actionableCharacteristics = Sets.newHashSet();

        for (ActionableEvent event : actionableEvents) {
            actionableHotspots.addAll(ActionableEventFactory.toActionableHotspots(event, ckbExtractorResult.hotspots()));
            actionableRanges.addAll(ActionableEventFactory.toActionableRanges(event, ckbExtractorResult.codons()));
            actionableRanges.addAll(ActionableEventFactory.toActionableRanges(event, ckbExtractorResult.exons()));

            if (ckbExtractorResult.geneLevelEvent() != null) {
                actionableGenes.add(ActionableEventFactory.geneLevelEventToActionableGene(event, ckbExtractorResult.geneLevelEvent()));
            }

            if (ckbExtractorResult.knownCopyNumber() != null) {
                actionableGenes.add(ActionableEventFactory.copyNumberToActionableGene(event, ckbExtractorResult.knownCopyNumber()));
            }

            if (ckbExtractorResult.knownFusionPair() != null) {
                actionableFusions.add(ActionableEventFactory.toActionableFusion(event, ckbExtractorResult.knownFusionPair()));
            }

            if (ckbExtractorResult.characteristic() != null) {
                actionableCharacteristics.add(ActionableEventFactory.toActionableCharacteristic(event,
                        ckbExtractorResult.characteristic()));
            }
        }

        return ImmutableExtractionResult.builder()
                .actionableHotspots(actionableHotspots)
                .actionableRanges(actionableRanges)
                .actionableGenes(actionableGenes)
                .actionableFusions(actionableFusions)
                .actionableCharacteristics(actionableCharacteristics)
                .build();
    }
}