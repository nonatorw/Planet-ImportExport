package com.planet.importexport.exportapi.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Writes export output as a valid XLSX (Office Open XML) spreadsheet, honoring
 * the exact requested column order (task C4; specs/export/spec.md, "Export
 * selected columns as XLSX").
 *
 * <p><strong>Dependency note:</strong> this class requires
 * {@code org.apache.poi:poi-ooxml}, added to {@code build.gradle} to satisfy
 * this task and approved per AGENTS.md ("Ask first: Adding new
 * dependencies").</p>
 *
 * <p>All requested columns are written as text cells (no numeric/date
 * cell-type inference), since the recognized schema's only numeric-looking
 * field ({@code age}) is stored as an already-decided string value in the
 * export projection ({@link ExportRowProjector}) — consistent with CSV/TXT
 * output where every cell is plain text.</p>
 */
public final class XlsxExportWriter {

    private static final String SHEET_NAME = "Export";

    /**
     * Not instantiable: all behavior is exposed through
     * {@link #write(List, List)}.
     */
    private XlsxExportWriter() {
        // Utility class.
    }

    /**
     * Writes {@code columns} and {@code rows} as a valid XLSX workbook with a
     * single sheet.
     *
     * @param columns the requested column names, written as the header row
     * @param rows    the data rows to write, each already ordered to match
     *                {@code columns}
     *
     * @return the serialized XLSX workbook bytes, header row first
     *
     * @throws UncheckedIOException if the in-memory workbook cannot be
     *                              serialized
     */
    public static byte[] write(List<String> columns,
                               List<List<String>> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet(SHEET_NAME);

            writeRow(sheet, 0, columns);
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                writeRow(sheet,
                         rowIndex + 1,
                         rows.get(rowIndex));
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            workbook.write(buffer);

            return buffer.toByteArray();

        } catch (IOException e) {
            throw new UncheckedIOException("failed to write XLSX export output", e);
        }
    }

    /**
     * Writes {@code values} as text cells on the row at {@code rowIndex} of
     * {@code sheet}, creating the row and each cell.
     *
     * @param sheet    the sheet to write the row into
     * @param rowIndex the zero-based index of the row to create
     * @param values   the cell values for this row, in column order; a
     *                 {@code null} value is written as an empty cell
     */
    private static void writeRow(XSSFSheet sheet,
                                 int rowIndex,
                                 List<String> values) {
        Row row = sheet.createRow(rowIndex);

        for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
            Cell cell = row.createCell(columnIndex);
            String value = values.get(columnIndex);
            cell.setCellValue(value == null ? "" : value);
        }
    }
}
