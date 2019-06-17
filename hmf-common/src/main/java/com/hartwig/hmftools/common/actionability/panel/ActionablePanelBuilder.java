package com.hartwig.hmftools.common.actionability.panel;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.actionability.ActionabilityAnalyzer;
import com.hartwig.hmftools.common.actionability.Actionable;
import com.hartwig.hmftools.common.actionability.cnv.CopyNumberEvidenceAnalyzer;
import com.hartwig.hmftools.common.actionability.cnv.CopyNumberType;
import com.hartwig.hmftools.common.actionability.drup.DrupActionabilityModel;
import com.hartwig.hmftools.common.actionability.drup.DrupActionabilityModelFactory;
import com.hartwig.hmftools.common.actionability.fusion.FusionEvidenceAnalyzer;
import com.hartwig.hmftools.common.actionability.somaticvariant.SomaticVariantEvidenceAnalyzer;
import com.hartwig.hmftools.common.drivercatalog.DriverCategory;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

public class ActionablePanelBuilder {

    private final Map<String, ImmutableActionablePanel.Builder> result = Maps.newHashMap();

    public ActionablePanelBuilder(@NotNull final String knowledgebaseDirector, @NotNull final String drupLocation) throws IOException {
        final ActionabilityAnalyzer actionabilityAnalyzer = ActionabilityAnalyzer.fromKnowledgebase(knowledgebaseDirector);
        final DrupActionabilityModel drup = DrupActionabilityModelFactory.buildFromCsv(drupLocation);

        addCopyNumbers(actionabilityAnalyzer.cnvAnalyzer());
        addFusions(actionabilityAnalyzer.fusionAnalyzer());
        addVariants(actionabilityAnalyzer.variantAnalyzer());
        addDrup(drup);
    }

    @NotNull
    public ActionablePanelBuilder addCopyNumbers(@NotNull final CopyNumberEvidenceAnalyzer copyNumberEvidenceAnalyzer) {
        copyNumberEvidenceAnalyzer.actionableCopyNumbers().forEach(x -> {
            final ImmutableActionablePanel.Builder builder = addActionable(x.gene(), x);
            if (x.type() == CopyNumberType.AMPLIFICATION) {
                builder.amplification(true);
            } else {
                builder.deletion(true);
            }
        });
        return this;
    }

    @NotNull
    public ActionablePanelBuilder addFusions(@NotNull final FusionEvidenceAnalyzer fusionAnalyser) {
        fusionAnalyser.actionablePromiscuousThree().forEach(x -> addActionable(x.gene(), x).fusion(true));
        fusionAnalyser.actionablePromiscuousFive().forEach(x -> addActionable(x.gene(), x).fusion(true));
        fusionAnalyser.actionableFusionPairs().forEach(x -> {
            addActionable(x.threeGene(), x).fusion(true);
            addActionable(x.fiveGene(), x).fusion(true);
        });
        return this;
    }

    @NotNull
    public ActionablePanelBuilder addVariants(@NotNull final SomaticVariantEvidenceAnalyzer variantEvidenceAnalyzer) {
        variantEvidenceAnalyzer.actionableRanges().forEach(x -> addActionable(x.gene(), x).variant(true));
        variantEvidenceAnalyzer.actionableVariants().forEach(x -> addActionable(x.gene(), x).variant(true));
        return this;
    }

    @NotNull
    public ActionablePanelBuilder addDrup(@NotNull final DrupActionabilityModel drup) {
        drup.geneDriverCategoryMap().forEach(this::addDrup);
        return this;
    }

    @NotNull
    public List<ActionablePanel> build() {
        return result.values().stream().map(ImmutableActionablePanel.Builder::build).collect(Collectors.toList());
    }

    @NotNull
    private ImmutableActionablePanel.Builder addActionable(@NotNull final String gene, @NotNull final Actionable actionable) {
        final ImmutableActionablePanel.Builder builder = select(gene);
        final ActionablePanel current = builder.build();

        if (actionable.response().equals("Responsive")) {
            boolean currentEmpty = current.responsive().isEmpty();
            boolean higherLevel = actionable.level().compareTo(current.responsive()) < 0;
            if (currentEmpty || higherLevel) {
                builder.responsive(actionable.level()).responsiveSource(actionable.source());
            }
        } else {
            boolean currentEmpty = current.resistant().isEmpty();
            boolean higherLevel = actionable.level().compareTo(current.resistant()) < 0;
            if (currentEmpty || higherLevel) {
                builder.resistant(actionable.level()).resistantSource(actionable.source());
            }
        }

        return builder;
    }

    @NotNull
    private ActionablePanelBuilder addDrup(@NotNull final String gene, @NotNull final DriverCategory category) {
        select(gene).drup(true).drupCategory(category.toString());
        return this;
    }

    @NotNull
    private ImmutableActionablePanel.Builder select(@NotNull final String gene) {
        return result.computeIfAbsent(gene, this::create);
    }

    @NotNull
    private ImmutableActionablePanel.Builder create(@NotNull final String gene) {
        return ImmutableActionablePanel.builder()
                .gene(gene)
                .amplification(false)
                .deletion(false)
                .fusion(false)
                .variant(false)
                .drup(false)
                .drupCategory(Strings.EMPTY)
                .responsive(Strings.EMPTY)
                .responsiveSource(Strings.EMPTY)
                .resistant(Strings.EMPTY)
                .resistantSource(Strings.EMPTY);
    }
}
