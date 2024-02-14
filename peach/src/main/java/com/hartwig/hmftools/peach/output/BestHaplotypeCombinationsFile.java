package com.hartwig.hmftools.peach.output;

import com.hartwig.hmftools.peach.HaplotypeAnalysis;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static com.hartwig.hmftools.peach.PeachUtils.GERMLINE_TOTAL_COPY_NUMBER;
import static com.hartwig.hmftools.peach.PeachUtils.TSV_DELIMITER;

public class BestHaplotypeCombinationsFile
{
    private static final String UNKNOWN_ALLELE_STRING = "UNRESOLVED";

    public static void write(@NotNull String filePath, @NotNull Map<String, HaplotypeAnalysis> geneToHaplotypeAnalysis) throws IOException
    {
        Files.write(new File(filePath).toPath(), toLines(geneToHaplotypeAnalysis));
    }

    @NotNull
    public static List<String> toLines(@NotNull Map<String, HaplotypeAnalysis> geneToHaplotypeAnalysis)
    {
        List<String> lines = new ArrayList<>();
        lines.add(header());
        geneToHaplotypeAnalysis.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> toLines(e.getKey(), e.getValue()))
                .flatMap(Collection::stream)
                .forEach(lines::add);
        return lines;
    }

    @NotNull
    private static String header()
    {
        return new StringJoiner(TSV_DELIMITER).add("gene").add("allele").add("count").toString();
    }

    @NotNull
    private static List<String> toLines(@NotNull String gene, @NotNull HaplotypeAnalysis analysis)
    {
        StringJoiner joiner = new StringJoiner(TSV_DELIMITER);
        if(analysis.hasBestHaplotypeCombination())
        {
            return analysis.getBestHaplotypeCombination()
                    .getHaplotypeNameToCount()
                    .entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> toLine(gene, e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
        }
        else
        {
            String unknownAlleleString =
                    joiner.add(gene).add(UNKNOWN_ALLELE_STRING).add(Integer.toString(GERMLINE_TOTAL_COPY_NUMBER)).toString();
            return List.of(unknownAlleleString);
        }
    }

    @NotNull
    private static String toLine(@NotNull String gene, @NotNull String haplotypeName, int count)
    {
        return new StringJoiner(TSV_DELIMITER).add(gene).add(haplotypeName).add(Integer.toString(count)).toString();
    }
}
