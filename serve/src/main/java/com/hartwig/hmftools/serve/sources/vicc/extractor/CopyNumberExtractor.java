package com.hartwig.hmftools.serve.sources.vicc.extractor;

import java.util.Map;

import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.genome.region.HmfTranscriptRegion;
import com.hartwig.hmftools.common.serve.classification.MutationType;
import com.hartwig.hmftools.serve.copynumber.CopyNumberType;
import com.hartwig.hmftools.serve.copynumber.ImmutableKnownCopyNumber;
import com.hartwig.hmftools.serve.copynumber.KnownCopyNumber;
import com.hartwig.hmftools.serve.sources.vicc.check.CheckGenes;
import com.hartwig.hmftools.vicc.datamodel.Feature;
import com.hartwig.hmftools.vicc.datamodel.ViccEntry;

import org.jetbrains.annotations.NotNull;

public class CopyNumberExtractor {

    @NotNull
    private final Map<String, HmfTranscriptRegion> transcriptPerGeneMap;

    public CopyNumberExtractor(@NotNull Map<String, HmfTranscriptRegion> transcriptPerGeneMap) {
        this.transcriptPerGeneMap = transcriptPerGeneMap;
    }

    @NotNull
    public Map<Feature, KnownCopyNumber> extractAmplificationsDeletions(@NotNull ViccEntry viccEntry) {
        Map<Feature, KnownCopyNumber> ampsDelsPerFeature = Maps.newHashMap();

        for (Feature feature : viccEntry.features()) {

            HmfTranscriptRegion canonicalTranscript = transcriptPerGeneMap.get(feature.geneSymbol());
            if (feature.type() == MutationType.AMPLIFICATION) {
                if (canonicalTranscript == null) {
                    CheckGenes.checkGensInPanel(feature.geneSymbol(), feature.name());
                } else {
                    ampsDelsPerFeature.put(feature,
                            ImmutableKnownCopyNumber.builder().gene(feature.geneSymbol()).type(CopyNumberType.AMPLIFICATION).build());
                }

            } else if (feature.type() == MutationType.DELETION) {
                if (canonicalTranscript == null) {
                    CheckGenes.checkGensInPanel(feature.geneSymbol(), feature.name());
                } else {
                    ampsDelsPerFeature.put(feature,
                            ImmutableKnownCopyNumber.builder().gene(feature.geneSymbol()).type(CopyNumberType.DELETION).build());
                }

            }
        }

        return ampsDelsPerFeature;
    }
}
