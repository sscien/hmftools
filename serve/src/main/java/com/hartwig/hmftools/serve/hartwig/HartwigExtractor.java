package com.hartwig.hmftools.serve.hartwig;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.variant.hotspot.ImmutableVariantHotspotImpl;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;
import com.hartwig.hmftools.serve.hotspot.ProteinKeyFormatter;
import com.hartwig.hmftools.serve.hotspot.ProteinToHotspotConverter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class HartwigExtractor {

    private static final Logger LOGGER = LogManager.getLogger(HartwigExtractor.class);

    @NotNull
    private final ProteinToHotspotConverter proteinToHotspotConverter;

    public HartwigExtractor(@NotNull final ProteinToHotspotConverter proteinToHotspotConverter) {
        this.proteinToHotspotConverter = proteinToHotspotConverter;
    }

    @NotNull
    public Map<HartwigEntry, List<VariantHotspot>> extractFromHartwigEntries(@NotNull List<? extends HartwigEntry> entries) {
        Map<HartwigEntry, List<VariantHotspot>> hotspotsPerEntry = Maps.newHashMap();
        for (HartwigEntry entry : entries) {
            List<VariantHotspot> hotspots = Lists.newArrayList();
            if (!entry.proteinAnnotation().isEmpty()) {
                if (ProteinToHotspotConverter.isResolvableProteinAnnotation(entry.proteinAnnotation())) {
                    hotspots =
                            proteinToHotspotConverter.resolveProteinAnnotation(entry.gene(), entry.transcript(), entry.proteinAnnotation());
                } else {
                    LOGGER.warn("Cannot resolve Hartwig protein annotation: '{}'",
                            ProteinKeyFormatter.toProteinKey(entry.gene(), entry.transcript(), entry.proteinAnnotation()));
                }
            }

            VariantHotspot impliedHotspot = toHotspot(entry);
            if (!hotspots.contains(impliedHotspot)) {
                if (entry.proteinAnnotation().isEmpty()) {
                    LOGGER.debug("Adding hotspot '{}' since protein annotation is not provided", impliedHotspot);
                } else {
                    LOGGER.warn("Adding hotspot '{}' since it was not generated by protein resolving based on '{}'",
                            impliedHotspot,
                            ProteinKeyFormatter.toProteinKey(entry.gene(), entry.transcript(), entry.proteinAnnotation()));
                }
                hotspots.add(impliedHotspot);
            }
        }

        return hotspotsPerEntry;
    }

    @NotNull
    private static VariantHotspot toHotspot(@NotNull HartwigEntry entry) {
        return ImmutableVariantHotspotImpl.builder()
                .chromosome(entry.chromosome())
                .position(entry.position())
                .ref(entry.ref())
                .alt(entry.alt())
                .build();
    }
}
