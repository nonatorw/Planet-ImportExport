package com.planet.importexport.exportapi.support;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DelimitedTextExportWriter} (task C3).
 */
class DelimitedTextExportWriterTest {

    /**
     * Writing CSV for a set of columns and rows produces a header line
     * followed by one line per row, values comma-separated in the requested
     * column order and lines terminated with CRLF.
     */
    @Test
    void writeCsv_producesHeaderAndRowsInRequestedColumnOrder() {
        byte[] output =
                DelimitedTextExportWriter.writeCsv(
                        List.of("id", "name", "email", "country"),
                        List.of(List.of("1", "John Smith", "john@example.com", "Portugal"),
                                List.of("2", "Jane Doe", "jane@example.com", "Spain")));

        String csv = new String(output, StandardCharsets.UTF_8);

        Assertions.assertThat(csv)
                  .isEqualTo("id,name,email,country\r\n" +
                             "1,John Smith,john@example.com,Portugal\r\n" +
                             "2,Jane Doe,jane@example.com,Spain\r\n");
    }

    /**
     * A value containing the comma delimiter is wrapped in double quotes in
     * the CSV output so it is not mistaken for a field separator.
     */
    @Test
    void writeCsv_valueContainingDelimiter_isQuoted() {
        byte[] output =
                DelimitedTextExportWriter.writeCsv(
                        List.of("name", "email"),
                        List.of(List.of("Smith, John", "john@example.com")));

        String csv = new String(output, StandardCharsets.UTF_8);

        Assertions.assertThat(csv)
                  .isEqualTo("name,email\r\n\"Smith, John\",john@example.com\r\n");
    }

    /**
     * A value containing a double quote character has that quote escaped by
     * doubling it, and the whole value wrapped in quotes, in the CSV output.
     */
    @Test
    void writeCsv_valueContainingQuote_isEscapedByDoubling() {
        byte[] output =
                DelimitedTextExportWriter.writeCsv(
                        List.of("name"),
                        List.of(List.of("Say \"hi\"")));

        String csv = new String(output, StandardCharsets.UTF_8);

        Assertions.assertThat(csv)
                  .isEqualTo("name\r\n\"Say \"\"hi\"\"\"\r\n");
    }

    /**
     * Writing TXT for a set of columns and rows produces a header line
     * followed by one line per row, values tab-separated in the requested
     * column order and lines terminated with CRLF.
     */
    @Test
    void writeTxt_producesTabSeparatedHeaderAndRowsInRequestedColumnOrder() {
        byte[] output =
                DelimitedTextExportWriter.writeTxt(
                        List.of("name", "email"),
                        List.of(List.of("John Smith", "john@example.com")));

        String txt = new String(output, StandardCharsets.UTF_8);

        Assertions.assertThat(txt)
                  .isEqualTo("name\temail\r\nJohn Smith\tjohn@example.com\r\n");
    }

    /**
     * Writing CSV for a column list with no data rows still writes the
     * header line, with no trailing row content.
     */
    @Test
    void write_noRows_stillWritesHeaderOnly() {
        byte[] output =
                DelimitedTextExportWriter.writeCsv(
                        List.of("id", "name"),
                        List.of());

        String csv = new String(output, StandardCharsets.UTF_8);

        Assertions.assertThat(csv)
                  .isEqualTo("id,name\r\n");
    }
}
