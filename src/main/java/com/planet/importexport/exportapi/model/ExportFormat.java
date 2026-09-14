package com.planet.importexport.exportapi.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import com.planet.importexport.exportapi.exception.UnsupportedExportFormatException;

/**
 * The export output formats the system supports (specs/export/spec.md, "Export
 * stored data as CSV, TXT, or XLSX"; proposal.md Non-goals, decision 6: no
 * legacy binary XLS).
 *
 * <p>{@code XLS} is deliberately NOT a constant here: legacy binary XLS must
 * be rejected with an explicit unsupported-format error (task C5), not
 * silently mapped to any of these three formats.
 * {@link #fromRequestValue(String)} is the single place that draws this
 * distinction.
 */
public enum ExportFormat {
    CSV,
    TXT,
    XLSX;

    /**
     * Parses the caller-supplied {@code format} value from
     * {@code com.planet.importexport.exportapi.dto.ExportRequest}.
     *
     * @param requestedFormat the raw, caller-supplied format value
     * @return the recognized {@link ExportFormat} matching {@code
     *         requestedFormat}, case-insensitively
     * @throws UnsupportedExportFormatException if {@code requestedFormat} is
     *                                          blank, unknown, or names the
     *                                          legacy XLS format explicitly
     *                                          (task C5: a clear error, never
     *                                          a silent fallback to
     *                                          CSV/TXT/XLSX)
     */
    public static ExportFormat fromRequestValue(String requestedFormat) {
        if (requestedFormat == null || requestedFormat.isBlank()) {
            throw new UnsupportedExportFormatException(requestedFormat);
        }

        String normalized = requestedFormat.trim()
                                           .toUpperCase(Locale.ROOT);

        Optional<ExportFormat> match = Arrays.stream(values())
                                             .filter(format -> format.name().equals(normalized))
                                             .findFirst();

        // Covers "XLS" explicitly as well as any other unrecognized format
        // token; both are reported identically as "unsupported format"
        // (spec.md: "rejected as an unsupported format", no distinction
        // required between "legacy" and "unknown").
        return match.orElseThrow(() -> new UnsupportedExportFormatException(requestedFormat));
    }
}
