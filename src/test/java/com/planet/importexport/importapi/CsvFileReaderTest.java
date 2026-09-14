package com.planet.importexport.importapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.planet.importexport.importapi.model.CsvRow;
import com.planet.importexport.importapi.support.CsvFileReader;
import com.planet.importexport.importapi.support.CsvHeaderReader;

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

    @Test
    void readIdColumn_readsOnlyIdColumnRegardlessOfPosition() throws IOException {
        Path file =
                writeFile("""
                          id,name,email,age,country,phone
                          1,John Smith,john@example.com,35,Portugal,+351910000000
                          4,Ana Costa,ana@example.com,,Portugal
                          """);

        List<String> ids = CsvFileReader.readIdColumn(file);

        assertThat(ids).containsExactly("1", "4");
    }

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

        assertThat(ids).containsExactly("1", "2");
    }

    @Test
    void readIdColumn_returnsEmptyWhenNoIdColumnInHeader() throws IOException {
        Path file = writeFile("name,email\nAlice,alice@example.com\n");

        assertThat(CsvFileReader.readIdColumn(file)).isEmpty();
    }

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

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).hasSize(2);
        assertThat(chunks.get(1)).hasSize(2);
        assertThat(chunks.get(2)).hasSize(1);
        assertThat(chunks.get(0).get(0).rowId()).isEqualTo(1);
        assertThat(chunks.get(2).get(0).rowId()).isEqualTo(5);
    }

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
        assertThat(row.valuesByColumnName())
                .containsEntry("id", "1")
                .containsEntry("name", "John Smith");
    }

    @Test
    void readInChunks_rejectsNonPositiveChunkSize() throws IOException {
        Path file = writeFile("id\n1\n");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> CsvFileReader.readInChunks(file,
                                                 0,
                                                 chunk -> {}));
    }

    @Test
    void headerReader_detectsUnknownColumns() throws IOException {
        Path file = writeFile("id,name,email,age,country,loyalty_tier\n6,New,new@example.com,30,Germany,Gold\n");

        Set<String> header = CsvHeaderReader.readHeader(file);
        Set<String> unknown = CsvHeaderReader.unknownColumns(header);

        assertThat(unknown).containsExactly("loyalty_tier");
    }

    @Test
    void headerReader_returnsEmptyWhenAllColumnsRecognized() throws IOException {
        Path file = writeFile("id,name,email,age,country,phone\n1,A,a@example.com,1,Portugal,123\n");

        Set<String> unknown = CsvHeaderReader.unknownColumns(CsvHeaderReader.readHeader(file));

        assertThat(unknown).isEmpty();
    }

    private Path writeFile(String content) throws IOException {
        Path file = Files.createTempFile(tempDir, "import", ".csv");
        Files.writeString(file, content);
        filesToCleanUp.add(file);

        return file;
    }
}
