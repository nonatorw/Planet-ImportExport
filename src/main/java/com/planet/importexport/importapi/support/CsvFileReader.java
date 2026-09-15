package com.planet.importexport.importapi.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.planet.importexport.importapi.model.CsvRow;
import com.planet.importexport.importapi.model.RecognizedImportField;

/**
 * Header-driven CSV reading for the Import capability (design.md section 3,
 * step 1 and step 4;
 * {@code B4}).
 *
 * <p>No fixed column position or fixed column set is assumed: every row is
 * interpreted strictly according to the file's own first (header) line, and
 * columns are matched by name, not position ({@code B4}'s explicit
 * requirement — see the import spec scenario "Import a file whose columns are
 * reordered and include an extra field").</p>
 *
 * <p>This is a deliberately simple comma-delimited parser (split on {@code ,},
 * no quoted-field or embedded-delimiter support): every sample and scenario
 * in {@code docs/requirements/acceptance-criteria.feature} uses plain unquoted
 * comma-separated values, and no ADR or spec requires full RFC 4180 quoting
 * support. Adding a CSV parsing library was not justified for this scope
 * (AGENTS.md: ask before adding new dependencies).</p>
 */
public final class CsvFileReader {

    /**
     * The single-character field delimiter this parser understands.
     */
    private static final String DELIMITER = ",";

    /**
     * Not instantiable: all behavior is exposed through the static methods.
     */
    private CsvFileReader() {
        // Utility class.
    }

    /**
     * Reads only the file's header and the {@code id} column values, in file
     * order, without materializing full rows — the lightweight up-front pass
     * design.md section 3 step 1 requires to populate {@code
     * import_jobs.idsInFile} before chunked processing starts (ADR-0003).
     *
     * <p>A row whose {@code id} column is absent from the header, or whose
     * {@code id} cell is blank, contributes no entry to the returned list;
     * such rows are still routed to staging later during full row processing
     * ({@code B5.1}/{@code B6}) — this pass exists only to compute the
     * serialization gate's id-set as cheaply as possible, not to validate rows.
     * </p>
     *
     * @param filePath the source CSV file to read
     *
     * @return every non-blank {@code id} cell value, in file order; empty if
     *         the file has no header, no rows, or no recognized {@code id}
     *         column
     *
     * @throws UncheckedIOException if the file cannot be read
     */
    public static List<String> readIdColumn(Path filePath) {
        List<String> ids = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String headerLine = reader.readLine();

            if (headerLine == null) {
                return ids;
            }

            List<String> header = splitLine(headerLine);
            int idColumnIndex =
                    header.indexOf(RecognizedImportField.ID.columnName());

            if (idColumnIndex < 0) {
                return ids;
            }

            String line;

            while ((line = reader.readLine()) != null) {
                List<String> cells = splitLine(line);

                if (idColumnIndex < cells.size()) {
                    String idValue = cells.get(idColumnIndex);

                    if (!idValue.isBlank()) {
                        ids.add(idValue);
                    }
                }
            }

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to read id column from file: " + filePath, e);
        }

        return ids;
    }

    /**
     * Reads every data row of {@code filePath} (header excluded), grouped into
     * chunks of at most {@code chunkSize} rows, invoking {@code chunkConsumer}
     * once per chunk in file order (design.md section 3, step 4; {@code B3}).
     *
     * <p>Rows are streamed chunk-by-chunk rather than materialized entirely in
     * memory up front, consistent with the "no file size or row-count admission
     * limit" non-goal (proposal.md) — an arbitrarily large file must not
     * require loading the whole file into memory at once.
     *
     * @param filePath     the source CSV file to read
     * @param chunkSize    the maximum number of rows per chunk; must be
     *                     positive
     * @param chunkConsumer invoked once per chunk, in file order, with at
     *                      most {@code chunkSize} rows each (the final chunk
     *                      may be smaller)
     *
     * @throws IllegalArgumentException if {@code chunkSize} is not positive
     * @throws UncheckedIOException     if the file cannot be read
     */
    public static void readInChunks(Path filePath,
                                    int chunkSize,
                                    Consumer<List<CsvRow>> chunkConsumer) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive, was: " + chunkSize);
        }

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String headerLine = reader.readLine();
            List<String> header = headerLine == null
                                           ? List.of()
                                           : splitLine(headerLine);

            List<CsvRow> currentChunk = new ArrayList<>(chunkSize);
            int rowId = 0;
            String line;

            while ((line = reader.readLine()) != null) {
                rowId++;

                currentChunk.add(toRow(rowId,
                                       header,
                                       splitLine(line)));

                if (currentChunk.size() == chunkSize) {
                    chunkConsumer.accept(currentChunk);
                    currentChunk = new ArrayList<>(chunkSize);
                }
            }

            if (!currentChunk.isEmpty()) {
                chunkConsumer.accept(currentChunk);
            }

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to read rows from file: " + filePath, e);
        }
    }

    /**
     * Zips one data row's cells with the file's header into a {@link CsvRow},
     * padding any cell missing past the end of the line with an empty string.
     *
     * @param rowId  the row's 1-based position within the file, header
     *               excluded
     * @param header the file's header columns, in file order
     * @param cells  the row's raw cell values, in file order
     *
     * @return the assembled row
     */
    private static CsvRow toRow(int rowId,
                                List<String> header,
                                List<String> cells) {
        Map<String, String> valuesByColumnName = new LinkedHashMap<>();

        for (int columnIndex = 0; columnIndex < header.size(); columnIndex++) {
            String value = columnIndex < cells.size()
                         ? cells.get(columnIndex)
                         : "";

            valuesByColumnName.put(header.get(columnIndex), value);
        }

        return new CsvRow(rowId, valuesByColumnName);
    }

    /**
     * Splits one raw line on {@link #DELIMITER}, trimming each resulting
     * field.
     *
     * @param line the raw line to split
     *
     * @return the line's fields, in order
     */
    private static List<String> splitLine(String line) {
        // -1 limit preserves trailing empty fields (e.g. a blank last column
        // must still be present).
        return Arrays.stream(line.split(DELIMITER, -1))
                     .map(String::trim)
                     .toList();
    }
}
