package com.hartwig.hmftools.ckb.classification;

import com.hartwig.hmftools.ckb.datamodel.CkbEntry;
import com.hartwig.hmftools.ckb.datamodel.variant.Variant;
import com.hartwig.hmftools.common.serve.classification.EventClassifier;
import com.hartwig.hmftools.common.serve.classification.EventClassifierFactory;
import com.hartwig.hmftools.common.serve.classification.EventType;

import org.jetbrains.annotations.NotNull;

public final class EventTypeExtractor {

    @NotNull
    private static final EventClassifier CLASSIFIER = EventClassifierFactory.buildClassifier(CkbClassificationConfig.build());

    private EventTypeExtractor() {
    }

    @NotNull
    public static EventType classify(@NotNull CkbEntry entry) {
        if (entry.variants().size() > 1) {
            return EventType.COMBINED;
        } else {
            Variant variant = entry.variants().get(0);

            String gene = EventAndGeneExtractor.extractGene(variant);
            String event = EventAndGeneExtractor.extractEvent(variant);

            return CLASSIFIER.determineType(gene, event);
        }
    }
}
