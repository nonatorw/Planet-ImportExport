package com.planet.importexport.exportapi.model;

import com.planet.importexport.exportapi.exception.UnsupportedExportFormatException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for {@link ExportFormat#fromRequestValue(String)} (tasks C1, C5). */
class ExportFormatTest {

    @ParameterizedTest
    @CsvSource({"CSV,CSV", "csv,CSV", "TXT,TXT", "txt,TXT", "XLSX,XLSX", "xlsx,XLSX"})
    void fromRequestValue_recognizedFormat_parsesCaseInsensitively(String input,
                                                                   ExportFormat expected) {
        assertThat(ExportFormat.fromRequestValue(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"XLS", "xls", "PDF", "doc"})
    void fromRequestValue_legacyOrUnknownFormat_throwsUnsupportedFormat(String input) {
        assertThatThrownBy(() ->
                ExportFormat.fromRequestValue(input))
                            .isInstanceOf(UnsupportedExportFormatException.class)
                            .satisfies(exception ->
                                    assertThat(((UnsupportedExportFormatException) exception).requestedFormat())
                                    .isEqualTo(input)
                            );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void fromRequestValue_blankOrNull_throwsUnsupportedFormat(String input) {
        assertThatThrownBy(() ->
                ExportFormat.fromRequestValue(input))
                            .isInstanceOf(UnsupportedExportFormatException.class);
    }
}
