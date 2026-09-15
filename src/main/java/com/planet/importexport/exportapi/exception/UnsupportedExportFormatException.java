package com.planet.importexport.exportapi.exception;

/**
 * Thrown when an export request names a format the system does not support —
 * including the legacy binary XLS format, which must be rejected explicitly
 * rather than silently falling back to another format (task C5;
 * specs/export/spec.md, "Requesting the legacy XLS format is rejected").
 *
 * <p>This is an application-level (unchecked) validation failure, not an
 * infrastructure error;
 * {@link com.planet.importexport.exportapi.exception.mapper.UnsupportedFormatMapper}
 * translates it into an HTTP 400 response.</p>
 */
public class UnsupportedExportFormatException extends RuntimeException {

    private final String requestedFormat;

    /**
     * Creates a new exception for an unsupported requested export format.
     *
     * @param requestedFormat the raw, caller-supplied format value that is
     *                        blank, unrecognized, or names the legacy XLS
     *                        format
     */
    public UnsupportedExportFormatException(String requestedFormat) {
        super("unsupported export format: '%s'".formatted(requestedFormat));
        this.requestedFormat = requestedFormat;
    }

    /**
     * Returns the raw, caller-supplied format value that triggered this
     * exception.
     *
     * @return the raw, caller-supplied format value that triggered this
     *         exception
     */
    public String requestedFormat() {
        return requestedFormat;
    }
}
