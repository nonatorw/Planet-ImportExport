package com.planet.importexport.exportapi.support;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link XlsxExportWriter} (task C4).
 */
class XlsxExportWriterTest {

    /**
     * Writing XLSX for a set of columns and rows produces a workbook whose
     * first sheet has a header row with the requested column names followed
     * by one data row per input row, in the requested column order.
     */
    @Test
    void write_producesValidXlsxWithHeaderAndRowsInRequestedColumnOrder() throws Exception {
        byte[] output = XlsxExportWriter.write(
                                List.of("id", "name", "country"),
                                List.of(List.of("1", "John Smith", "Portugal"),
                                        List.of("2", "Jane Doe", "Spain")));

        ByteArrayInputStream workbookBytes = new ByteArrayInputStream(output);

        try (XSSFWorkbook workbook = new XSSFWorkbook(workbookBytes)) {
            XSSFSheet sheet = workbook.getSheetAt(0);

            Row header = sheet.getRow(0);

            Assertions.assertThat(header.getCell(0)
                                        .getStringCellValue())
                      .isEqualTo("id");

            Assertions.assertThat(header.getCell(1)
                                        .getStringCellValue())
                      .isEqualTo("name");

            Assertions.assertThat(header.getCell(2)
                                        .getStringCellValue())
                      .isEqualTo("country");

            Row firstDataRow = sheet.getRow(1);

            Assertions.assertThat(firstDataRow.getCell(0)
                                              .getStringCellValue())
                      .isEqualTo("1");

            Assertions.assertThat(firstDataRow.getCell(1)
                                              .getStringCellValue())
                      .isEqualTo("John Smith");

            Assertions.assertThat(firstDataRow.getCell(2)
                                              .getStringCellValue())
                      .isEqualTo("Portugal");

            Row secondDataRow = sheet.getRow(2);

            Assertions.assertThat(secondDataRow.getCell(0)
                                               .getStringCellValue())
                      .isEqualTo("2");

            Assertions.assertThat(secondDataRow.getCell(1)
                                               .getStringCellValue())
                      .isEqualTo("Jane Doe");

            Assertions.assertThat(secondDataRow.getCell(2)
                                               .getStringCellValue())
                      .isEqualTo("Spain");

            Assertions.assertThat(sheet.getPhysicalNumberOfRows())
                      .isEqualTo(3);
        }
    }

    /**
     * Writing XLSX for a column list with no data rows still produces a
     * valid workbook whose sheet contains only the header row.
     */
    @Test
    void write_noRows_stillProducesValidWorkbookWithHeaderOnly() throws Exception {
        byte[] output = XlsxExportWriter.write(List.of("name", "email"),
                                               List.of());

        ByteArrayInputStream workbookBytes = new ByteArrayInputStream(output);

        try (XSSFWorkbook workbook = new XSSFWorkbook(workbookBytes)) {
            XSSFSheet sheet = workbook.getSheetAt(0);

            Assertions.assertThat(sheet.getPhysicalNumberOfRows())
                      .isEqualTo(1);
        }
    }
}
