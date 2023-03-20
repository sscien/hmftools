package com.hartwig.hmftools.datamodel.linx;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;

@Gson.TypeAdapters
@Value.Immutable
public interface LinxFusion
{

    @NotNull
    String name();

    boolean reported();

    @NotNull
    LinxFusionType reportedType();

    @NotNull
    FusionPhasedType phased();

    @NotNull
    FusionLikelihoodType likelihood();

    int fusedExonUp();

    int fusedExonDown();

    // for orange report
    int chainLinks();

    boolean chainTerminated();

    @NotNull
    String domainsKept();

    @NotNull
    String domainsLost();

    // for patient report
    @NotNull
    String geneStart();

    @NotNull
    String geneContextStart();

    @NotNull
    String geneTranscriptStart();

    @NotNull
    String geneEnd();

    @NotNull
    String geneContextEnd();

    @NotNull
    String geneTranscriptEnd();

    double junctionCopyNumber();
}
