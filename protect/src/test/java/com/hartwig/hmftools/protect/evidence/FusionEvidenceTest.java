package com.hartwig.hmftools.protect.evidence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.linx.LinxTestFactory;
import com.hartwig.hmftools.common.protect.ProtectEvidence;
import com.hartwig.hmftools.common.protect.ProtectEvidenceType;
import com.hartwig.hmftools.common.sv.linx.ImmutableLinxFusion;
import com.hartwig.hmftools.common.sv.linx.LinxFusion;
import com.hartwig.hmftools.serve.ServeTestFactory;
import com.hartwig.hmftools.serve.actionability.fusion.ActionableFusion;
import com.hartwig.hmftools.serve.actionability.fusion.ImmutableActionableFusion;
import com.hartwig.hmftools.serve.actionability.gene.ActionableGene;
import com.hartwig.hmftools.serve.actionability.gene.ImmutableActionableGene;
import com.hartwig.hmftools.serve.extraction.gene.GeneLevelEvent;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class FusionEvidenceTest {

    @Test
    public void canDetermineFusionEvidence() {
        String geneUp = "geneUp";
        String geneDown = "geneDown";
        String genePromiscuous = "genePromiscuous";
        ActionableGene promiscuous = ImmutableActionableGene.builder()
                .from(ServeTestFactory.createTestActionableGene())
                .gene(genePromiscuous)
                .event(GeneLevelEvent.FUSION)
                .build();
        ActionableGene amp = ImmutableActionableGene.builder()
                .from(ServeTestFactory.createTestActionableGene())
                .gene(genePromiscuous)
                .event(GeneLevelEvent.AMPLIFICATION)
                .build();
        ActionableFusion fusion = ImmutableActionableFusion.builder()
                .from(ServeTestFactory.createTestActionableFusion())
                .geneUp(geneUp)
                .geneDown(geneDown)
                .build();

        FusionEvidence fusionEvidence = new FusionEvidence(EvidenceTestFactory.createTestEvidenceFactory(),
                Lists.newArrayList(promiscuous, amp),
                Lists.newArrayList(fusion));

        LinxFusion reportedFusionMatch = create(geneUp, geneDown, true);
        LinxFusion reportedPromiscuousMatch = create(genePromiscuous, "other gene", true);
        LinxFusion reportedPromiscuousNonMatch = create("other gene", geneDown, true);
        LinxFusion unreportedPromiscuousMatch = create("other gene", genePromiscuous, false);

        List<ProtectEvidence> evidences =
                fusionEvidence.evidence(Lists.newArrayList(reportedFusionMatch, reportedPromiscuousMatch, reportedPromiscuousNonMatch),
                        Lists.newArrayList(unreportedPromiscuousMatch));

        assertEquals(3, evidences.size());

        ProtectEvidence evidence1 = findByEvent(evidences, geneUp + " - " + geneDown + " fusion");
        assertTrue(evidence1.reported());
        assertEquals(ProtectEvidenceType.FUSION_PAIR, evidence1.evidenceType());

        ProtectEvidence evidence2 = findByEvent(evidences, genePromiscuous + " - other gene fusion");
        assertTrue(evidence2.reported());
        assertEquals(ProtectEvidenceType.PROMISCUOUS_FUSION, evidence2.evidenceType());

        ProtectEvidence evidence3 = findByEvent(evidences, "other gene - " + genePromiscuous + " fusion");
        assertFalse(evidence3.reported());
        assertEquals(ProtectEvidenceType.PROMISCUOUS_FUSION, evidence3.evidenceType());
    }

    @NotNull
    private static ProtectEvidence findByEvent(@NotNull List<ProtectEvidence> evidences, @NotNull String event) {
        for (ProtectEvidence evidence : evidences) {
            if (evidence.event().equals(event)) {
                return evidence;
            }
        }

        throw new IllegalStateException("Cannot find evidence with event: " + event);
    }

    @Test
    public void canCorrectlyFilterOnExonRange() {
        String geneUp = "geneUp";
        String geneDown = "geneDown";
        int minExonUp = 5;
        int maxExonUp = 7;
        int minExonDown = 2;
        int maxExonDown = 4;

        ActionableFusion fusion = ImmutableActionableFusion.builder()
                .from(ServeTestFactory.createTestActionableFusion())
                .geneUp(geneUp)
                .minExonUp(minExonUp)
                .maxExonUp(maxExonUp)
                .geneDown(geneDown)
                .minExonDown(minExonDown)
                .maxExonDown(maxExonDown)
                .build();

        FusionEvidence fusionEvidence =
                new FusionEvidence(EvidenceTestFactory.createTestEvidenceFactory(), Lists.newArrayList(), Lists.newArrayList(fusion));

        ImmutableLinxFusion.Builder builder = linxFusionBuilder(geneUp, geneDown, true);

        List<LinxFusion> onMinRange = Lists.newArrayList(builder.fusedExonUp(minExonUp).fusedExonDown(minExonDown).build());
        assertEquals(1, fusionEvidence.evidence(onMinRange, Lists.newArrayList()).size());

        List<LinxFusion> onMaxRange = Lists.newArrayList(builder.fusedExonUp(maxExonUp).fusedExonDown(maxExonDown).build());
        assertEquals(1, fusionEvidence.evidence(onMaxRange, Lists.newArrayList()).size());

        List<LinxFusion> upGeneExonTooLow = Lists.newArrayList(builder.fusedExonUp(minExonUp - 1).fusedExonDown(minExonDown).build());
        assertEquals(0, fusionEvidence.evidence(upGeneExonTooLow, Lists.newArrayList()).size());

        List<LinxFusion> upGeneExonTooHigh = Lists.newArrayList(builder.fusedExonUp(maxExonUp + 1).fusedExonDown(minExonDown).build());
        assertEquals(0, fusionEvidence.evidence(upGeneExonTooHigh, Lists.newArrayList()).size());

        List<LinxFusion> downGeneExonTooLow = Lists.newArrayList(builder.fusedExonUp(minExonUp).fusedExonDown(minExonDown - 1).build());
        assertEquals(0, fusionEvidence.evidence(downGeneExonTooLow, Lists.newArrayList()).size());

        List<LinxFusion> downGeneExonTooHigh = Lists.newArrayList(builder.fusedExonUp(maxExonUp).fusedExonDown(maxExonDown + 1).build());
        assertEquals(0, fusionEvidence.evidence(downGeneExonTooHigh, Lists.newArrayList()).size());
    }

    @NotNull
    private static LinxFusion create(@NotNull String geneStart, @NotNull String geneEnd, boolean reported) {
        return linxFusionBuilder(geneStart, geneEnd, reported).build();
    }

    @NotNull
    private static ImmutableLinxFusion.Builder linxFusionBuilder(@NotNull String geneStart, @NotNull String geneEnd, boolean reported) {
        return ImmutableLinxFusion.builder()
                .from(LinxTestFactory.createMinimalTestFusion())
                .geneStart(geneStart)
                .geneEnd(geneEnd)
                .reported(reported);
    }
}