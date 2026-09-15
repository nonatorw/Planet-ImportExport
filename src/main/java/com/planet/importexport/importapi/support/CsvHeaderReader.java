package com.planet.importexport.importapi.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.planet.importexport.importapi.model.RecognizedImportField;

/**
 * Reads only the header line of a CSV file ({@code B6}: unknown-header-column
 * detection is a whole-file, header-level check, not a per-row check — every
 * row in a file shares the same header).
 */
public final class CsvHeaderReader {

    /**
     * Not instantiable: all behavior is exposed through the static methods.
     */
    private CsvHeaderReader() {
        // Utility class.
    }

    /**
     * Returns the header column names (in file order) as an ordered set, or
     * empty if the file has no header/is empty.
     *
     * @param filePath the source CSV file to read
     *
     * @return the header's column names, in file order; empty if the file
     *         has no header
     *
     * @throws UncheckedIOException if the file cannot be read
     */
    public static Set<String> readHeader(Path filePath) {
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            return Optional.ofNullable(reader.readLine())
                           .map(CsvHeaderReader::splitColumns)
                           .orElseGet(Set::of);

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to read header from file: " + filePath, e);
        }
    }

    /**
     * The subset of {@code header} that falls outside
     * {@link RecognizedImportField} ({@code B6}).
     *
     * @param header the file's header column names, e.g. from
     *               {@link #readHeader(Path)}
     *
     * @return the columns in {@code header} that are not part of the
     *         recognized schema, in the order they appear in {@code header}
     */
    public static Set<String> unknownColumns(Set<String> header) {
        return header.stream()
                     .filter(column -> !RecognizedImportField.isRecognized(column))
                     .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Splits one header line into trimmed column names, preserving file
     * order.
     *
     * @param headerLine the raw, unsplit header line
     *
     * @return the header's column names, in file order
     */
    private static Set<String> splitColumns(String headerLine) {
        return Arrays.stream(headerLine.split(",", -1))
                     .map(String::trim)
                     .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
