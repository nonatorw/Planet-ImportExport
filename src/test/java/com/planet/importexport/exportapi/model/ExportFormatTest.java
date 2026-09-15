package com.planet.importexport.exportapi.model;

import com.planet.importexport.exportapi.exception.UnsupportedExportFormatException;

import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link ExportFormat#fromRequestValue(String)} (tasks C1, C5).
 */
class ExportFormatTest {

    /**
     * A recognized format value parses to its enum constant regardless of
     * the case it was submitted in.
     */
    @ParameterizedTest
    @CsvSource({"CSV,CSV", "csv,CSV", "TXT,TXT", "txt,TXT", "XLSX,XLSX", "xlsx,XLSX"})
    void fromRequestValue_recognizedFormat_parsesCaseInsensitively(String input,
                                                                   ExportFormat expected) {
        Assertions.assertThat(ExportFormat.fromRequestValue(input))
                  .isEqualTo(expected);
    }

    /**
     * The legacy XLS format and any other unrecognized format value both
     * throw {@link UnsupportedExportFormatException} naming the requested
     * value.
     */
    @ParameterizedTest
    @ValueSource(strings = {"XLS", "xls", "PDF", "doc"})
    void fromRequestValue_legacyOrUnknownFormat_throwsUnsupportedFormat(String input) {
        ThrowingCallable shouldRaiseThrowable = () ->
                ExportFormat.fromRequestValue(input);

        Assertions.assertThatThrownBy(shouldRaiseThrowable)
                  .isInstanceOf(UnsupportedExportFormatException.class)
                  .satisfies(exception -> assertRequestedFormatMatches(input, exception));
    }

    /**
     * Asserts that {@code exception}'s {@code requestedFormat} matches the
     * raw format value that triggered it.
     *
     * @param input     the raw format value that was submitted
     * @param exception the caught exception to inspect
     *
     * @return the fluent assertion, for further chaining if needed
     */
    private AbstractStringAssert<?> assertRequestedFormatMatches(String input, Throwable exception) {
        String requestedFormat =
                ((UnsupportedExportFormatException) exception).requestedFormat();

        return Assertions.assertThat(requestedFormat)
                         .isEqualTo(input);
    }

    /**
     * A blank, empty, or {@code null} format value throws
     * {@link UnsupportedExportFormatException}.
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void fromRequestValue_blankOrNull_throwsUnsupportedFormat(String input) {
        Assertions.assertThatThrownBy(() -> ExportFormat.fromRequestValue(input))
                  .isInstanceOf(UnsupportedExportFormatException.class);
    }
}
