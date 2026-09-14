package com.planet.importexport.exportapi.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link XlsxExportWriter} (task C4).
 */
class XlsxExportWriterTest {

    @Test
    void write_producesValidXlsxWithHeaderAndRowsInRequestedColumnOrder() throws Exception {
        byte[] output = XlsxExportWriter.write(
                                List.of("id", "name", "country"),
                                List.of(List.of("1", "John Smith", "Portugal"),
                                        List.of("2", "Jane Doe", "Spain")));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            XSSFSheet sheet = workbook.getSheetAt(0);

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0)
                             .getStringCellValue()).isEqualTo("id");
            assertThat(header.getCell(1)
                             .getStringCellValue()).isEqualTo("name");
            assertThat(header.getCell(2)
                             .getStringCellValue()).isEqualTo("country");

            Row firstDataRow = sheet.getRow(1);
            assertThat(firstDataRow.getCell(0)
                                   .getStringCellValue()).isEqualTo("1");
            assertThat(firstDataRow.getCell(1)
                                   .getStringCellValue()).isEqualTo("John Smith");
            assertThat(firstDataRow.getCell(2)
                                   .getStringCellValue()).isEqualTo("Portugal");

            Row secondDataRow = sheet.getRow(2);
            assertThat(secondDataRow.getCell(0)
                                    .getStringCellValue()).isEqualTo("2");
            assertThat(secondDataRow.getCell(1)
                                    .getStringCellValue()).isEqualTo("Jane Doe");
            assertThat(secondDataRow.getCell(2)
                                    .getStringCellValue()).isEqualTo("Spain");

            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);
        }
    }

    @Test
    void write_noRows_stillProducesValidWorkbookWithHeaderOnly() throws Exception {
        byte[] output = XlsxExportWriter.write(List.of("name", "email"), List.of());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1);
        }
    }
}
