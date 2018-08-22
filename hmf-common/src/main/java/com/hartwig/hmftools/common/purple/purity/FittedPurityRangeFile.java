package com.hartwig.hmftools.common.purple.purity;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.StringJoiner;

import com.google.common.collect.Lists;

import org.jetbrains.annotations.NotNull;

public enum FittedPurityRangeFile {
    ;

    private static final int MAX_RECORDS = 100;
    private static final String DELIMITER = "\t";
    static final String HEADER_PREFIX = "#";
    private static final String EXTENSION = ".purple.purity.range";

    @NotNull
    public static List<FittedPurity> read(@NotNull final String basePath, @NotNull final String sample) throws IOException {
        final String filePath = basePath + File.separator + sample + EXTENSION;
        return fromLines(Files.readAllLines(new File(filePath).toPath()));
    }

    public static void write(@NotNull final String basePath, @NotNull final String sample, @NotNull final List<FittedPurity> purity)
            throws IOException {
        final String filePath = basePath + File.separator + sample + EXTENSION;
        Files.write(new File(filePath).toPath(), toLines(purity));
    }

    @NotNull
    static List<String> toLines(@NotNull final List<FittedPurity> purity) {
        final List<String> lines = Lists.newArrayList();
        lines.add(header());
        purity.stream().limit(MAX_RECORDS).map(FittedPurityRangeFile::toString).forEach(lines::add);
        return lines;
    }

    @NotNull
    static List<FittedPurity> fromLines(@NotNull List<String> lines) {
        return lines.stream().filter(x -> !x.startsWith(HEADER_PREFIX)).map(FittedPurityRangeFile::fromString).collect(toList());
    }

    @NotNull
    private static String header() {
        return new StringJoiner(DELIMITER, HEADER_PREFIX, "").add("Purity")
                .add("NormFactor")
                .add("Score")
                .add("DiploidProportion")
                .add("Ploidy")
                .add("SomaticDeviation")
                .toString();
    }

    @NotNull
    private static String toString(@NotNull final FittedPurity purity) {
        return new StringJoiner(DELIMITER).add(String.valueOf(purity.purity()))
                .add(String.valueOf(purity.normFactor()))
                .add(String.valueOf(purity.score()))
                .add(String.valueOf(purity.diploidProportion()))
                .add(String.valueOf(purity.ploidy()))
                .add(String.valueOf(purity.somaticDeviation()))
                .toString();
    }

    @NotNull
    private static FittedPurity fromString(@NotNull final String purity) {
        String[] values = purity.split(DELIMITER);
        return ImmutableFittedPurity.builder()
                .purity(Double.valueOf(values[0]))
                .normFactor(Double.valueOf(values[1]))
                .score(Double.valueOf(values[2]))
                .diploidProportion(Double.valueOf(values[3]))
                .ploidy(Double.valueOf(values[4]))
                .somaticDeviation(Double.valueOf(values[5]))
                .build();
    }
}
