package com.planet.importexport.importapi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.planet.importexport.importapi.model.CsvRow;
import com.planet.importexport.importapi.support.CsvFileReader;
import com.planet.importexport.importapi.support.CsvHeaderReader;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Unit tests for {@link CsvFileReader} and {@link CsvHeaderReader}
 * ({@code B3}, {@code B4}).
 */
class CsvFileReaderTest {

    @TempDir Path tempDir;

    private final List<Path> filesToCleanUp = new ArrayList<>();

    @AfterEach
    void cleanup() throws IOException {
        for (Path path : filesToCleanUp) {
            Files.deleteIfExists(path);
        }
    }

    /**
     * Reading the id column returns only the {@code id} values, in file
     * order, regardless of where that column sits among the other headers.
     */
    @Test
    void readIdColumn_readsOnlyIdColumnRegardlessOfPosition() throws IOException {
        Path file =
                writeFile("""
                          id,name,email,age,country,phone
                          1,John Smith,john@example.com,35,Portugal,+351910000000
                          4,Ana Costa,ana@example.com,,Portugal
                          """);

        List<String> ids = CsvFileReader.readIdColumn(file);

        Assertions.assertThat(ids)
                  .containsExactly("1", "4");
    }

    /**
     * A row whose {@code id} value is blank is skipped entirely when reading
     * the id column, rather than being returned as an empty string.
     */
    @Test
    void readIdColumn_skipsRowsWithBlankId() throws IOException {
        Path file =
                writeFile("""
                          id,name
                          1,Alice
                          ,NoId
                          2,Bob
                          """);

        List<String> ids = CsvFileReader.readIdColumn(file);

        Assertions.assertThat(ids)
                  .containsExactly("1", "2");
    }

    /**
     * Reading the id column of a file whose header has no {@code id} column
     * returns an empty list instead of failing.
     */
    @Test
    void readIdColumn_returnsEmptyWhenNoIdColumnInHeader() throws IOException {
        Path file = writeFile("name,email\nAlice,alice@example.com\n");

        Assertions.assertThat(CsvFileReader.readIdColumn(file))
                  .isEmpty();
    }

    /**
     * Reading a 5-row file in chunks of 2 produces three chunks (2, 2, and 1
     * rows), with each row's id preserved in its original position.
     */
    @Test
    void readInChunks_splitsRowsIntoChunksOfConfiguredSize() throws IOException {
        Path file =
                writeFile("""
                          id,name
                          1,A
                          2,B
                          3,C
                          4,D
                          5,E
                          """);

        List<List<CsvRow>> chunks = new ArrayList<>();
        CsvFileReader.readInChunks(file, 2, chunks::add);

        Assertions.assertThat(chunks)
                  .hasSize(3);

        Assertions.assertThat(chunks.get(0))
                  .hasSize(2);

        Assertions.assertThat(chunks.get(1))
                  .hasSize(2);

        Assertions.assertThat(chunks.get(2))
                  .hasSize(1);

        Assertions.assertThat(chunks.get(0).get(0).rowId())
                  .isEqualTo(1);

        Assertions.assertThat(chunks.get(2).get(0).rowId())
                  .isEqualTo(5);
    }

    /**
     * Each row's values are mapped to their column names as declared in the
     * header, so reordered columns are still keyed correctly by name rather
     * than by their positional index.
     */
    @Test
    void readInChunks_mapsColumnsByHeaderNameNotPosition() throws IOException {
        Path file =
                writeFile("""
                          name,id,phone
                          John Smith,1,+351910000000
                          """);

        List<List<CsvRow>> chunks = new ArrayList<>();
        CsvFileReader.readInChunks(file, 10, chunks::add);

        CsvRow row = chunks.get(0).get(0);

        Assertions.assertThat(row.valuesByColumnName())
                  .containsEntry("id", "1")
                  .containsEntry("name", "John Smith");
    }

    /**
     * Requesting chunked reading with a non-positive chunk size throws
     * {@link IllegalArgumentException} instead of silently misbehaving.
     */
    @Test
    void readInChunks_rejectsNonPositiveChunkSize() throws IOException {
        Path file = writeFile("id\n1\n");

        assertThrows(
                IllegalArgumentException.class,
                () -> CsvFileReader.readInChunks(file,
                                                 0,
                                                 chunk -> {}));
    }

    /**
     * A header column that is not among the recognized fields is reported by
     * {@link CsvHeaderReader#unknownColumns(Set)} as unknown.
     */
    @Test
    void headerReader_detectsUnknownColumns() throws IOException {
        Path file =
                writeFile("""
                          id,name,email,age,country,loyalty_tier
                          6,New,new@example.com,30,Germany,Gold
                          """);

        Set<String> header = CsvHeaderReader.readHeader(file);
        Set<String> unknown = CsvHeaderReader.unknownColumns(header);

        Assertions.assertThat(unknown)
                  .containsExactly("loyalty_tier");
    }

    /**
     * When every header column is a recognized field,
     * {@link CsvHeaderReader#unknownColumns(Set)} returns an empty set.
     */
    @Test
    void headerReader_returnsEmptyWhenAllColumnsRecognized() throws IOException {
        Path file =
                writeFile("""
                          id,name,email,age,country,phone
                          1,A,a@example.com,1,Portugal,123
                          """);

        Set<String> unknown =
                CsvHeaderReader.unknownColumns(
                        CsvHeaderReader.readHeader(file));

        Assertions.assertThat(unknown)
                  .isEmpty();
    }

    /**
     * Writes {@code content} to a new temporary file, tracked for cleanup.
     *
     * @param content the file content to write
     *
     * @return the path of the newly written file
     *
     * @throws IOException if the file cannot be written
     */
    private Path writeFile(String content) throws IOException {
        Path file = Files.createTempFile(tempDir,
                                         "import",
                                         ".csv");

        Files.writeString(file, content);

        filesToCleanUp.add(file);

        return file;
    }
}
