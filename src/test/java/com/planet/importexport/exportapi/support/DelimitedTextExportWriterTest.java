package com.planet.importexport.exportapi.support;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link DelimitedTextExportWriter} (task C3). */
class DelimitedTextExportWriterTest {

    @Test
    void writeCsv_producesHeaderAndRowsInRequestedColumnOrder() {
        byte[] output =
                DelimitedTextExportWriter.writeCsv(
                        List.of("id", "name", "email", "country"),
                        List.of(List.of("1", "John Smith", "john@example.com", "Portugal"),
                                List.of("2", "Jane Doe", "jane@example.com", "Spain")));

        String csv = new String(output, StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("id,name,email,country\r\n" +
                                  "1,John Smith,john@example.com,Portugal\r\n" +
                                  "2,Jane Doe,jane@example.com,Spain\r\n");
    }

    @Test
    void writeCsv_valueContainingDelimiter_isQuoted() {
        byte[] output =
                DelimitedTextExportWriter.writeCsv(
                        List.of("name", "email"),
                        List.of(List.of("Smith, John", "john@example.com")));

        String csv = new String(output, StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("name,email\r\n\"Smith, John\",john@example.com\r\n");
    }

    @Test
    void writeCsv_valueContainingQuote_isEscapedByDoubling() {
        byte[] output =
                DelimitedTextExportWriter.writeCsv(
                        List.of("name"),
                        List.of(List.of("Say \"hi\"")));

        String csv = new String(output, StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("name\r\n\"Say \"\"hi\"\"\"\r\n");
    }

    @Test
    void writeTxt_producesTabSeparatedHeaderAndRowsInRequestedColumnOrder() {
        byte[] output =
                DelimitedTextExportWriter.writeTxt(
                        List.of("name", "email"),
                        List.of(List.of("John Smith", "john@example.com")));

        String txt = new String(output, StandardCharsets.UTF_8);

        assertThat(txt).isEqualTo("name\temail\r\nJohn Smith\tjohn@example.com\r\n");
    }

    @Test
    void write_noRows_stillWritesHeaderOnly() {
        byte[] output =
                DelimitedTextExportWriter.writeCsv(
                        List.of("id", "name"),
                        List.of());

        String csv = new String(output, StandardCharsets.UTF_8);

        assertThat(csv).isEqualTo("id,name\r\n");
    }
}
