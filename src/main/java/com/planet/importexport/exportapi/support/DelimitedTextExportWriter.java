package com.planet.importexport.exportapi.support;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes CSV and TXT export output as a delimited text stream, honoring the
 * exact requested column order (task C3; specs/export/spec.md,
 * "Export selected columns as CSV"/"...as TXT").
 *
 * <p>Delimiter choice: CSV uses a comma with RFC 4180-style quoting (quote a
 * field if it contains the delimiter, a quote character, or a newline; double
 * up embedded quotes). TXT uses a tab character, unquoted — design.md
 * section 4 leaves the exact delimited-writer library/format as "an
 * implementation-time decision" for CSV/TXT alike, and neither the spec nor
 * design.md mandates a specific TXT delimiter; tab-separated is the
 * conventional plain-text tabular choice distinct from CSV's comma, keeping
 * the two formats visibly different as the spec's scenarios expect.
 * A header row (the requested column names) is written first so the output
 * is self-describing.</p>
 */
public final class DelimitedTextExportWriter {

    /**
     * Not instantiable: all behavior is exposed through the static write
     * methods.
     */
    private DelimitedTextExportWriter() {
        // Utility class.
    }

    /**
     * Writes {@code columns} and {@code rows} as comma-delimited, RFC
     * 4180-quoted CSV content.
     *
     * @param columns the requested column names, used as the header row and
     *                to determine the number of cells per row
     * @param rows    the data rows to write, each already ordered to match
     *                {@code columns}
     *
     * @return the UTF-8 encoded CSV content, header row first
     */
    public static byte[] writeCsv(List<String> columns,
                                  List<List<String>> rows) {
        return write(columns,
                     rows,
                     ',',
                     true);
    }

    /**
     * Writes {@code columns} and {@code rows} as tab-separated, unquoted TXT
     * content.
     *
     * @param columns the requested column names, used as the header row and
     *                to determine the number of cells per row
     * @param rows    the data rows to write, each already ordered to match
     *                {@code columns}
     *
     * @return the UTF-8 encoded tab-separated content, header row first
     */
    public static byte[] writeTxt(List<String> columns, List<List<String>> rows) {
        return write(columns,
                     rows,
                     '\t',
                     false);
    }

    /**
     * Writes {@code columns} as the header row followed by every row in
     * {@code rows}, using {@code delimiter} to separate cells.
     *
     * @param columns       the header row cell values
     * @param rows          the data rows to write, each already ordered to
     *                      match {@code columns}
     * @param delimiter     the character separating cells on each line
     * @param quoteIfNeeded whether a cell value should be RFC 4180-style
     *                      quoted when it contains {@code delimiter}, a quote
     *                      character, or a newline
     *
     * @return the UTF-8 encoded delimited content, header row first
     */
    private static byte[] write(List<String> columns,
                                List<List<String>> rows,
                                char delimiter,
                                boolean quoteIfNeeded) {
        // ByteArrayOutputStream never throws IOException, so PrintWriter over
        // it cannot either; no try-with-resources exception path exists to
        // handle here.
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (PrintWriter writer = new PrintWriter(buffer,
                                                  false,
                                                  StandardCharsets.UTF_8)) {

            writeRow(writer,
                     columns,
                     delimiter,
                     quoteIfNeeded);

            for (List<String> row : rows) {
                writeRow(writer,
                         row,
                         delimiter,
                         quoteIfNeeded);
            }

            writer.flush();
        }

        return buffer.toByteArray();
    }

    /**
     * Writes a single line to {@code writer}: {@code values} joined by
     * {@code delimiter} (quoting each cell first when {@code quoteIfNeeded}
     * is {@code true}), terminated by a CRLF line ending.
     *
     * @param writer        the target to append the line to
     * @param values        the cell values for this line, in order; a
     *                      {@code null} value is written as an empty cell
     * @param delimiter     the character separating cells on the line
     * @param quoteIfNeeded whether each cell should be passed through
     *                      {@link #quoteIfNecessary(String, char)} before
     *                      being written
     */
    private static void writeRow(PrintWriter writer,
                                 List<String> values,
                                 char delimiter,
                                 boolean quoteIfNeeded) {
        StringBuilder line = new StringBuilder();

        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                line.append(delimiter);
            }

            String value = values.get(i) == null ? "" : values.get(i);

            line.append(quoteIfNeeded ? quoteIfNecessary(value, delimiter) : value);
        }

        writer.print(line);
        writer.print("\r\n");
    }

    /**
     * RFC 4180-style quotes {@code value} when it contains {@code delimiter},
     * a double-quote character, or a newline; any embedded double quote is
     * doubled up as required by that quoting rule.
     *
     * @param value     the raw cell value to inspect and, if needed, quote
     * @param delimiter the character separating cells on the line, checked
     *                  as one of the triggers for quoting
     *
     * @return {@code value} unchanged when quoting is not needed, otherwise
     *         the quoted and escaped form
     */
    private static String quoteIfNecessary(String value,
                                           char delimiter) {
        boolean needsQuoting = value.indexOf(delimiter) >= 0 ||
                               value.indexOf('"')    >= 0 ||
                               value.indexOf('\n')   >= 0 ||
                               value.indexOf('\r')   >= 0;

        if (!needsQuoting) {
            return value;
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
