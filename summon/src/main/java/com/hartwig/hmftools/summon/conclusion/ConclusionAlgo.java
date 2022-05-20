package com.hartwig.hmftools.summon.conclusion;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.chord.ChordAnalysis;
import com.hartwig.hmftools.common.chord.ChordStatus;
import com.hartwig.hmftools.common.cuppa.MolecularTissueOrigin;
import com.hartwig.hmftools.common.drivercatalog.DriverCategory;
import com.hartwig.hmftools.common.drivercatalog.panel.DriverGene;
import com.hartwig.hmftools.common.fusion.KnownFusionType;
import com.hartwig.hmftools.common.linx.ReportableHomozygousDisruption;
import com.hartwig.hmftools.common.purple.copynumber.CopyNumberInterpretation;
import com.hartwig.hmftools.common.purple.copynumber.ReportableGainLoss;
import com.hartwig.hmftools.common.sv.linx.LinxFusion;
import com.hartwig.hmftools.common.variant.DriverInterpretation;
import com.hartwig.hmftools.common.variant.ReportableVariant;
import com.hartwig.hmftools.common.variant.ReportableVariantFactory;
import com.hartwig.hmftools.common.variant.ReportableVariantSource;
import com.hartwig.hmftools.common.variant.msi.MicrosatelliteStatus;
import com.hartwig.hmftools.common.variant.tml.TumorMutationalStatus;
import com.hartwig.hmftools.common.virus.AnnotatedVirus;
import com.hartwig.hmftools.common.virus.VirusLikelihoodType;
import com.hartwig.hmftools.summon.SummonData;
import com.hartwig.hmftools.summon.actionability.ActionabilityEntry;
import com.hartwig.hmftools.summon.actionability.ActionabilityKey;
import com.hartwig.hmftools.summon.actionability.Condition;
import com.hartwig.hmftools.summon.actionability.ImmutableActionabilityKey;
import com.hartwig.hmftools.summon.actionability.TypeAlteration;

import org.jetbrains.annotations.NotNull;

import com.google.common.collect.Sets;

public class ConclusionAlgo {

    private static final Set<String> FUSION_TYPES = Sets.newHashSet(KnownFusionType.PROMISCUOUS_3.toString(),
            KnownFusionType.PROMISCUOUS_5.toString(),
            KnownFusionType.KNOWN_PAIR.toString(),
            KnownFusionType.IG_KNOWN_PAIR.toString(),
            KnownFusionType.IG_PROMISCUOUS.toString());
    private static final Set<String> VIRUS = Sets.newHashSet("HPV", "EBV");

    @NotNull
    public static ActionabilityConclusion generateConclusion(@NotNull SummonData summonData) {
        Map<Integer, String> conclusion = Maps.newHashMap();
        Set<String> oncogenic = Sets.newHashSet();
        Set<String> actionable = Sets.newHashSet();

        Map<ActionabilityKey, ActionabilityEntry> actionabilityMap = generateActionabilityMap(summonData.actionabilityEntries());
        Map<String, DriverGene> driverGenesMap = generateDriverGenesMap(summonData.driverGenes());
        List<ReportableVariant> reportableSomaticVariants = summonData.purple().reportableSomaticVariants();
        List<ReportableVariant> reportableGermlineVariants = summonData.purple().reportableGermlineVariants();
        List<ReportableVariant> reportableVariants =
                ReportableVariantFactory.mergeVariantLists(reportableGermlineVariants, reportableSomaticVariants);
        List<ReportableGainLoss> reportableGainLosses = summonData.purple().reportableGainsLosses();
        List<LinxFusion> reportableFusions = summonData.linx().reportableFusions();
        List<ReportableHomozygousDisruption> homozygousDisruptions = summonData.linx().homozygousDisruptions();
        List<AnnotatedVirus> reportableViruses = summonData.virusInterpreter().reportableViruses();

        genertatePurityConclusion(conclusion, summonData.purple().purity(), actionabilityMap);
        generateCUPPAConclusion(conclusion, summonData.molecularTissueOrigin(), actionabilityMap);
        generateVariantConclusion(conclusion, reportableVariants, actionabilityMap, driverGenesMap, oncogenic, actionable);
        generateCNVConclusion(conclusion, reportableGainLosses, actionabilityMap, oncogenic, actionable);
        generateFusionConclusion(conclusion, reportableFusions, actionabilityMap, oncogenic, actionable);
        generateHomozygousDisruptionConclusion(conclusion, homozygousDisruptions, actionabilityMap, oncogenic, actionable);
        generateVirusConclusion(conclusion, reportableViruses, actionabilityMap, oncogenic, actionable);
        generateHrdConclusion(conclusion, summonData.chord(), actionabilityMap, oncogenic, actionable);
        generateMSIConclusion(conclusion,
                summonData.purple().microsatelliteStatus(),
                summonData.purple().microsatelliteIndelsPerMb(),
                actionabilityMap,
                oncogenic,
                actionable);
        generateTMLConclusion(conclusion,
                summonData.purple().tumorMutationalLoadStatus(),
                summonData.purple().tumorMutationalLoad(),
                actionabilityMap,
                oncogenic,
                actionable);
        generateTMBConclusion(conclusion, summonData.purple().tumorMutationalBurdenPerMb(), actionabilityMap, oncogenic, actionable);

        generateTotalResults(conclusion, actionabilityMap, oncogenic, actionable);
        generateFindings(conclusion, actionabilityMap);

        String conclusionString = generateConslusionString(conclusion);

        return ImmutableActionabilityConclusion.builder().conclusion(conclusionString).build();
    }

    @NotNull
    public static String generateConslusionString(@NotNull Map<Integer, String> conclusion) {
        StringBuilder conclusionBuilder = new StringBuilder();
        int location = 0;
        for (Map.Entry<Integer, String> entry : conclusion.entrySet()) {
            if (entry.getKey().equals(location)) {
                conclusionBuilder.append(entry.getValue()).append(" <enter> ");
                location += 1;
            }
        }
        return conclusionBuilder.toString();
    }

    @NotNull
    public static Map<ActionabilityKey, ActionabilityEntry> generateActionabilityMap(@NotNull List<ActionabilityEntry> actionabilityDB) {
        Map<ActionabilityKey, ActionabilityEntry> actionabilityMap = Maps.newHashMap();
        for (ActionabilityEntry entry : actionabilityDB) {
            ActionabilityKey key = ImmutableActionabilityKey.builder().gene(entry.match()).type(entry.type()).build();
            actionabilityMap.put(key, entry);
        }
        return actionabilityMap;
    }

    @NotNull
    public static Map<String, DriverGene> generateDriverGenesMap(@NotNull List<DriverGene> driverGenes) {
        Map<String, DriverGene> driverGeneMap = Maps.newHashMap();
        for (DriverGene entry : driverGenes) {
            driverGeneMap.put(entry.gene(), entry);
        }
        return driverGeneMap;
    }

    public static void generateCUPPAConclusion(@NotNull Map<Integer, String> conclusion, MolecularTissueOrigin molecularTissueOrigin,
            @NotNull Map<ActionabilityKey, ActionabilityEntry> actionabilityMap) {

        if (molecularTissueOrigin.conclusion().contains("results inconclusive")) {
            ActionabilityKey keyCuppaInconclusice =
                    ImmutableActionabilityKey.builder().gene("CUPPA_inconclusive").type(TypeAlteration.CUPPA_INCONCLUSIVE).build();

            ActionabilityEntry entry = actionabilityMap.get(keyCuppaInconclusice);
            if (entry != null && entry.condition() == Condition.OTHER) {
                conclusion.put(conclusion.size(), "- " + entry.conclusion());
            }
        } else {
            ActionabilityKey keyCuppa = ImmutableActionabilityKey.builder().gene("CUPPA").type(TypeAlteration.CUPPA).build();

            ActionabilityEntry entry = actionabilityMap.get(keyCuppa);
            if (entry != null && entry.condition() == Condition.OTHER) {
                conclusion.put(conclusion.size(), "- " + entry.conclusion());
            }
        }
    }

    public static void generateVariantConclusion(@NotNull Map<Integer, String> conclusion,
            @NotNull List<ReportableVariant> reportableVariants, @NotNull Map<ActionabilityKey, ActionabilityEntry> actionabilityMap,
            @NotNull Map<String, DriverGene> driverGenesMap, @NotNull Set<String> oncogenic, @NotNull Set<String> actionable) {

        for (ReportableVariant reportableVariant : reportableVariants) {

            oncogenic.add(reportableVariant.source() == ReportableVariantSource.SOMATIC ? "somaticVariant" : "germlineVariant");
            ActionabilityKey keySomaticVariant = ImmutableActionabilityKey.builder()
                    .gene(reportableVariant.gene())
                    .type(driverGenesMap.get(reportableVariant.gene()).likelihoodType().equals(DriverCategory.ONCO)
                            ? TypeAlteration.ACTIVATING_MUTATION
                            : TypeAlteration.INACTIVATION)
                    .build();
            ActionabilityEntry entry = actionabilityMap.get(keySomaticVariant);
            if (entry != null) {
                if ((reportableVariant.driverLikelihoodInterpretation() == DriverInterpretation.HIGH
                        && entry.condition() == Condition.ONLY_HIGH) || entry.condition() == Condition.ALWAYS_NO_ACTIONABLE) {
                    if (entry.condition() == Condition.ONLY_HIGH) {
                        actionable.add(
                                reportableVariant.source() == ReportableVariantSource.SOMATIC ? "somaticVariant" : "germlineVariant");
                    }
                    //TODO: Add sentence about germline findings probably in future
                    conclusion.put(conclusion.size(),
                            "- " + reportableVariant.gene() + "(" + reportableVariant.canonicalHgvsProteinImpact() + ") "
                                    + entry.conclusion());
                }
            }
        }
    }

    public static void generateCNVConclusion(@NotNull Map<Integer, String> conclusion,
            @NotNull List<ReportableGainLoss> reportableGainLosses, @NotNull Map<ActionabilityKey, ActionabilityEntry> actionabilityMap,
            @NotNull Set<String> oncogenic, @NotNull Set<String> actionable) {
        for (ReportableGainLoss gainLoss : reportableGainLosses) {
            oncogenic.add("CNV");

            if (gainLoss.interpretation().display().equals(CopyNumberInterpretation.FULL_LOSS.display()) || gainLoss.interpretation()
                    .display()
                    .equals(CopyNumberInterpretation.PARTIAL_LOSS.display())) {

                ActionabilityKey keyVirus = ImmutableActionabilityKey.builder().gene(gainLoss.gene()).type(TypeAlteration.LOSS).build();
                ActionabilityEntry entry = actionabilityMap.get(keyVirus);

                if (entry != null && entry.condition() == Condition.ALWAYS) {
                    conclusion.put(conclusion.size(), "- " + gainLoss.gene() + " " + entry.conclusion());
                    actionable.add("CNV");
                }
            }

            if (gainLoss.interpretation().display().equals(CopyNumberInterpretation.FULL_GAIN.display()) || gainLoss.interpretation()
                    .display()
                    .equals(CopyNumberInterpretation.PARTIAL_GAIN.display())) {
                ActionabilityKey keyVirus =
                        ImmutableActionabilityKey.builder().gene(gainLoss.gene()).type(TypeAlteration.AMPLIFICATION).build();
                ActionabilityEntry entry = actionabilityMap.get(keyVirus);

                if (entry != null && entry.condition() == Condition.ALWAYS) {
                    conclusion.put(conclusion.size(), "- " + gainLoss.gene() + " " + entry.conclusion());
                    actionable.add("CNV");
                }
            }
        }
    }

    public static void generateFusionConclusion(@NotNull Map<Integer, String> conclusion, @NotNull List<LinxFusion> reportableFusions,
            @NotNull Map<ActionabilityKey, ActionabilityEntry> actionabilityMap, @NotNull Set<String> oncogenic,
            @NotNull Set<String> actionable) {
        for (LinxFusion fusion : reportableFusions) {
            oncogenic.add("fusion");

            if (fusion.reportedType().equals(KnownFusionType.EXON_DEL_DUP.toString())) {
                ActionabilityKey keyFusion =
                        ImmutableActionabilityKey.builder().gene(fusion.geneStart()).type(TypeAlteration.INTERNAL_DELETION).build();
                ActionabilityEntry entry = actionabilityMap.get(keyFusion);
                if (entry != null && entry.condition() == Condition.ALWAYS) {
                    conclusion.put(conclusion.size(), "- " + fusion.name() + " " + entry.conclusion());
                    actionable.add("fusion");
                }
            }
            if (fusion.reportedType().equals(KnownFusionType.EXON_DEL_DUP.toString()) && fusion.geneStart().equals("EGFR") && (
                    fusion.fusedExonUp() == 25 && fusion.fusedExonDown() == 14) || (fusion.fusedExonUp() == 26
                    && fusion.fusedExonDown() == 18)) {
                ActionabilityKey keyFusion =
                        ImmutableActionabilityKey.builder().gene(fusion.geneStart()).type(TypeAlteration.KINASE_DOMAIN_DUPLICATION).build();
                ActionabilityEntry entry = actionabilityMap.get(keyFusion);
                if (entry != null && entry.condition() == Condition.ALWAYS) {
                    conclusion.put(conclusion.size(), "- " + fusion.name() + " " + entry.conclusion());
                    actionable.add("fusion");
                }
            }
            if (FUSION_TYPES.contains(fusion.reportedType())) {
                ActionabilityKey keyFusionStart =
                        ImmutableActionabilityKey.builder().gene(fusion.geneStart()).type(TypeAlteration.FUSION).build();
                ActionabilityKey keyFusionEnd =
                        ImmutableActionabilityKey.builder().gene(fusion.geneEnd()).type(TypeAlteration.FUSION).build();

                ActionabilityEntry entryStart = actionabilityMap.get(keyFusionStart);
                ActionabilityEntry entryEnd = actionabilityMap.get(keyFusionEnd);

                if (entryStart != null && entryStart.condition() == Condition.ALWAYS) {
                    conclusion.put(conclusion.size(), "- " + fusion.name() + " " + entryStart.conclusion());
                    actionable.add("fusion");
                } else if (entryEnd != null && entryEnd.condition() == Condition.ALWAYS) {
                    conclusion.put(conclusion.size(), "- " + fusion.name() + " " + entryEnd.conclusion());
                    actionable.add("fusion");
                }
            }
        }
    }

    public static void generateHomozygousDisruptionConclusion(@NotNull Map<Integer, String> conclusion,
            @NotNull List<ReportableHomozygousDisruption> homozygousDisruptions,
            @NotNull Map<ActionabilityKey, ActionabilityEntry> actionabilityMap, @NotNull Set<String> oncogenic,
            @NotNull Set<String> actionable) {
        for (ReportableHomozygousDisruption homozygousDisruption : homozygousDisruptions) {
            oncogenic.add("homozygousDisruption");

            ActionabilityKey keyHomozygousDisruption =
                    ImmutableActionabilityKey.builder().gene(homozygousDisruption.gene()).type(TypeAlteration.INACTIVATION).build();
            ActionabilityEntry entry = actionabilityMap.get(keyHomozygousDisruption);
            if (entry != null && entry.condition() == Condition.ALWAYS) {
                conclusion.put(conclusion.size(), "- " + homozygousDisruption.gene() + " " + entry.conclusion());
                actionable.add("homozygousDisruption");
            }
        }
    }

    public static void generateVirusConclusion(@NotNull Map<Integer, String> conclusion, @NotNull List<AnnotatedVirus> reportableViruses,
            @NotNull Map<ActionabilityKey, ActionabilityEntry> actionabilityMap, @NotNull Set<String> oncogenic,
            @NotNull Set<String> actionable) {
        for (AnnotatedVirus annotatedVirus : reportableViruses) {
            oncogenic.add("virus");

            if (annotatedVirus.virusDriverLikelihoodType() == VirusLikelihoodType.HIGH && VIRUS.contains(annotatedVirus.interpretation())) {
                ActionabilityKey keyVirus =
                        ImmutableActionabilityKey.builder().gene(annotatedVirus.interpretation()).type(TypeAlteration.POSITIVE).build();
                ActionabilityEntry entry = actionabilityMap.get(keyVirus);
                if (entry != null && entry.condition() == Condition.ALWAYS) {
                    if ((annotatedVirus.virusDriverLikelihoodType() == VirusLikelihoodType.HIGH)) {
                        conclusion.put(conclusion.size(), "- " + annotatedVirus.interpretation() + " " + entry.conclusion());
                        actionable.add("virus");
                    }
                }
            }
        }
    }

    public static void generateHrdConclusion(@NotNull Map<Integer, String> conclusion, @NotNull ChordAnalysis chordAnalysis,
            @NotNull Map<ActionabilityKey, ActionabilityEntry> actionabilityMap, @NotNull Set<String> oncogenic,
            @NotNull Set<String> actionable) {
        if (chordAnalysis.hrStatus() == ChordStatus.HR_DEFICIENT) {
            ActionabilityKey keyHRD = ImmutableActionabilityKey.builder().gene("HRD").type(TypeAlteration.POSITIVE).build();
            ActionabilityEntry entry = actionabilityMap.get(keyHRD);
            if (entry != null && entry.condition() == Condition.ALWAYS) {
                conclusion.put(conclusion.size(), "- " + "HRD(" + chordAnalysis.hrdValue() + ") " + entry.conclusion());
                actionable.add("HRD");
                oncogenic.add("HRD");
            }
        }
    }

    public static void generateMSIConclusion(@NotNull Map<Integer, String> conclusion, @NotNull MicrosatelliteStatus microsatelliteStatus,
            double microsatelliteMb, @NotNull Map<ActionabilityKey, ActionabilityEntry> actionabilityMap, @NotNull Set<String> oncogenic,
            @NotNull Set<String> actionable) {
        if (microsatelliteStatus == MicrosatelliteStatus.MSI) {
            ActionabilityKey keyMSI = ImmutableActionabilityKey.builder().gene("MSI").type(TypeAlteration.POSITIVE).build();
            ActionabilityEntry entry = actionabilityMap.get(keyMSI);
            if (entry != null && entry.condition() == Condition.ALWAYS) {
                conclusion.put(conclusion.size(), "- " + "MSI(" + microsatelliteMb + ")" + entry.conclusion());
                actionable.add("MSI");
                oncogenic.add("MSI");
            }
        }
    }

    public static void generateTMLConclusion(@NotNull Map<Integer, String> conclusion, @NotNull TumorMutationalStatus tumorMutationalStatus,
            int tumorMutationalLoad, @NotNull Map<ActionabilityKey, ActionabilityEntry> actionabilityMap, @NotNull Set<String> oncogenic,
            @NotNull Set<String> actionable) {
        if (tumorMutationalStatus == TumorMutationalStatus.HIGH) {
            ActionabilityKey keyTML = ImmutableActionabilityKey.builder().gene("High-TML").type(TypeAlteration.POSITIVE).build();
            ActionabilityEntry entry = actionabilityMap.get(keyTML);
            if (entry != null && entry.condition() == Condition.ALWAYS) {
                conclusion.put(conclusion.size(), "- " + "TML(" + tumorMutationalLoad + ") " + entry.conclusion());
                actionable.add("TML");
                oncogenic.add("TML");
            }
        }
    }

    public static void generateTMBConclusion(@NotNull Map<Integer, String> conclusion, double tumorMutationalBurden,
            @NotNull Map<ActionabilityKey, ActionabilityEntry> actionabilityMap, @NotNull Set<String> oncogenic,
            @NotNull Set<String> actionable) {
        if (tumorMutationalBurden >= 10) {
            ActionabilityKey keyTMB = ImmutableActionabilityKey.builder().gene("High-TMB").type(TypeAlteration.POSITIVE).build();
            ActionabilityEntry entry = actionabilityMap.get(keyTMB);
            if (entry != null && entry.condition() == Condition.ALWAYS) {
                conclusion.put(conclusion.size(), "- " + "TMB( " + tumorMutationalBurden + ")" + entry.conclusion());
                actionable.add("TMB");
                oncogenic.add("TMB");
            }
        }
    }

    public static void genertatePurityConclusion(@NotNull Map<Integer, String> conclusion, double purity,
            @NotNull Map<ActionabilityKey, ActionabilityEntry> actionabilityMap) {
        if (purity < 0.195) {
            ActionabilityKey keyPurity = ImmutableActionabilityKey.builder().gene("purity").type(TypeAlteration.PURITY).build();

            ActionabilityEntry entry = actionabilityMap.get(keyPurity);
            if (entry != null && entry.condition() == Condition.OTHER) {
                conclusion.put(conclusion.size(), "- " + entry.conclusion().replace("XX%", purity + "%"));
            }
        }
    }

    public static void generateTotalResults(@NotNull Map<Integer, String> conclusion,
            @NotNull Map<ActionabilityKey, ActionabilityEntry> actionabilityMap, @NotNull Set<String> oncogenic,
            @NotNull Set<String> actionable) {
        if (oncogenic.size() == 0) {
            ActionabilityKey keyOncogenic =
                    ImmutableActionabilityKey.builder().gene("no_oncogenic").type(TypeAlteration.NO_ONCOGENIC).build();

            ActionabilityEntry entry = actionabilityMap.get(keyOncogenic);
            if (entry != null && entry.condition() == Condition.OTHER) {
                conclusion.put(conclusion.size(), "- " + entry.conclusion());
            }
        } else if (actionable.size() == 0) {
            ActionabilityKey keyActionable =
                    ImmutableActionabilityKey.builder().gene("no_actionable").type(TypeAlteration.NO_ACTIONABLE).build();
            ActionabilityEntry entry = actionabilityMap.get(keyActionable);
            if (entry != null && entry.condition() == Condition.OTHER) {
                conclusion.put(conclusion.size(), "- " + entry.conclusion());
            }
        }
    }

    public static void generateFindings(@NotNull Map<Integer, String> conclusion,
            @NotNull Map<ActionabilityKey, ActionabilityEntry> actionabilityMap) {
        ActionabilityKey keyOncogenic = ImmutableActionabilityKey.builder().gene("findings").type(TypeAlteration.FINDINGS).build();

        ActionabilityEntry entry = actionabilityMap.get(keyOncogenic);
        if (entry != null && entry.condition() == Condition.OTHER) {
            conclusion.put(conclusion.size(), "- " + entry.conclusion());
        }
    }
}