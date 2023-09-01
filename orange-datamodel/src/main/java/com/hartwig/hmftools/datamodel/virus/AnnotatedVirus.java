package com.hartwig.hmftools.datamodel.virus;

import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Gson.TypeAdapters
@Value.Immutable
@Value.Style(allParameters = true, passAnnotations = { NotNull.class, Nullable.class })
public interface AnnotatedVirus {

    @NotNull
    String name();

    @NotNull
    VirusBreakendQCStatus qcStatus();

    int integrations();

    @Nullable
    String interpretation();

    double percentageCovered();

    double meanCoverage();

    @Nullable
    Double expectedClonalCoverage();

    boolean reported();

    @NotNull
    VirusLikelihoodType virusDriverLikelihoodType();
}