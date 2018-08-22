package com.hartwig.hmftools.common.purple.purity;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.StringJoiner;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.purple.gender.Gender;

import org.jetbrains.annotations.NotNull;

public enum FittedPurityFile {
    ;

    static final String DELIMITER = "\t";
    static final String HEADER_PREFIX = "#";
    private static final String EXTENSION = ".purple.purity";

    @NotNull
    public static PurityContext read(@NotNull final String basePath, @NotNull final String sample) throws IOException {
        final String filePath = basePath + File.separator + sample + EXTENSION;
        final String line =  Files.readAllLines(new File(filePath).toPath()).get(1);
        return fromLine(line);
    }

    @NotNull
    static PurityContext fromLine(@NotNull String line) {
        final String[] values = line.split(DELIMITER);
        return ImmutablePurityContext.builder()
                .score(score(values))
                .bestFit(bestFit(values))
                .gender(gender(values))
                .status(status(values))
                .polyClonalProportion(polyClonalProportion(values))
                .version(values[14])
                .build();
    }

    public static void write(@NotNull final String basePath, @NotNull final String sample, @NotNull final PurityContext context)
            throws IOException {
        writeBestPurity(basePath, sample, context);
    }

    private static void writeBestPurity(@NotNull final String basePath, @NotNull final String sample, @NotNull final PurityContext context)
            throws IOException {
        final String filePath = basePath + File.separator + sample + EXTENSION;
        Files.write(new File(filePath).toPath(), toLines(context));
    }

    @NotNull
    static List<String> toLines(@NotNull final PurityContext context) {
        return Lists.newArrayList(header(), toString(context));
    }

    @NotNull
    private static String header() {
        return new StringJoiner(DELIMITER, HEADER_PREFIX, "").add("Purity")
                .add("NormFactor")
                .add("Score")
                .add("DiploidProportion")
                .add("Ploidy")
                .add("Gender")
                .add("Status")
                .add("PolyclonalProportion")
                .add("MinPurity")
                .add("MaxPurity")
                .add("MinPloidy")
                .add("MaxPloidy")
                .add("MinDiploidProportion")
                .add("MaxDiploidProportion")
                .add("Version")
                .add("SomaticDeviation")
                .toString();
    }

    @NotNull
    private static String toString(@NotNull final PurityContext context) {
        final FittedPurity purity = context.bestFit();
        final FittedPurityScore score = context.score();
        return new StringJoiner(DELIMITER).add(String.valueOf(purity.purity()))
                .add(String.valueOf(purity.normFactor()))
                .add(String.valueOf(purity.score()))
                .add(String.valueOf(purity.diploidProportion()))
                .add(String.valueOf(purity.ploidy()))
                .add(String.valueOf(context.gender()))
                .add(String.valueOf(context.status()))
                .add(String.valueOf(context.polyClonalProportion()))
                .add(String.valueOf(score.minPurity()))
                .add(String.valueOf(score.maxPurity()))
                .add(String.valueOf(score.minPloidy()))
                .add(String.valueOf(score.maxPloidy()))
                .add(String.valueOf(score.minDiploidProportion()))
                .add(String.valueOf(score.maxDiploidProportion()))
                .add(String.valueOf(context.version()))
                .add(String.valueOf(purity.somaticDeviation()))
                .toString();
    }

    @NotNull
    @VisibleForTesting
    static FittedPurity bestFit(@NotNull final String[] values) {
        return ImmutableFittedPurity.builder()
                .purity(Double.valueOf(values[0]))
                .normFactor(Double.valueOf(values[1]))
                .score(Double.valueOf(values[2]))
                .diploidProportion(Double.valueOf(values[3]))
                .ploidy(Double.valueOf(values[4]))
                .somaticDeviation(Double.valueOf(values[15]))
                .build();
    }

    @NotNull
    @VisibleForTesting
    static Gender gender(@NotNull final String[] values) {
        return Gender.valueOf(values[5]);
    }

    @NotNull
    @VisibleForTesting
    static FittedPurityStatus status(@NotNull final String[] values) {
        return FittedPurityStatus.valueOf(values[6]);
    }

    @NotNull
    @VisibleForTesting
    static FittedPurityScore score(@NotNull final String[] values) {
        return ImmutableFittedPurityScore.builder()
                .minPurity(Double.valueOf(values[8]))
                .maxPurity(Double.valueOf(values[9]))
                .minPloidy(Double.valueOf(values[10]))
                .maxPloidy(Double.valueOf(values[11]))
                .minDiploidProportion(Double.valueOf(values[12]))
                .maxDiploidProportion(Double.valueOf(values[13]))
                .build();
    }

    @VisibleForTesting
    static double polyClonalProportion(@NotNull final String[] values) {
        return Double.valueOf(values[7]);
    }
}
