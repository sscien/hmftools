package com.hartwig.hmftools.common.metrics;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import com.hartwig.hmftools.common.utils.io.exception.MalformedFileException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

class WGSMetricsLines {

    private static final Logger LOGGER = LogManager.getLogger(WGSMetricsLines.class);

    private static final String VALUE_SEPARATOR = "\t";

    @NotNull
    private final String[] headers;
    @NotNull
    private final String[] values;

    @NotNull
    static WGSMetricsLines fromFile(@NotNull String path) throws IOException {
        List<String> lines = Files.readAllLines(new File(path).toPath());
        if (lines.isEmpty()) {
            throw new IOException("WGSMetrics file seems empty on " + path);
        }
        int index = findHeaderLineIndex(lines);
        if (index >= lines.size()) {
            throw new MalformedFileException(String.format("No value line found after header line in WGS Metrics file %s.", path));
        }

        String[] headers = lines.get(index).split(VALUE_SEPARATOR);
        String[] values = lines.get(index+1).split(VALUE_SEPARATOR);
        if (headers.length != values.length) {
            throw new IllegalStateException("Mismatch in number of headers and number of values in " + path);
        }

        return new WGSMetricsLines(headers, values);
    }

    private static int findHeaderLineIndex(@NotNull final List<String> lines) throws MalformedFileException {
        final Optional<Integer> lineNumbers =
                IntStream.range(0, lines.size()).filter(index -> lines.get(index).contains("MEAN_COVERAGE")).boxed().findFirst();
        if (!lineNumbers.isPresent()) {
            throw new MalformedFileException(String.format("Could not find header line in WGS Metrics file with %s lines.", lines.size()));
        }
        return lineNumbers.get();
    }

    private WGSMetricsLines(@NotNull final String[] headers, @NotNull final String[] values) {
        this.headers = headers;
        this.values = values;

        assert this.headers.length == this.values.length;
    }

    @NotNull
    String findValueByHeader(@NotNull String header) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].equals(header)) {
                return values[i];
            }
        }
        LOGGER.warn("Could not find header {} in WGSMetricsLines!", header);
        return Strings.EMPTY;
    }
}
