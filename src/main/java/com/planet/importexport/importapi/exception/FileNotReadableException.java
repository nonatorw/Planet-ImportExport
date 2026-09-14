package com.planet.importexport.importapi.exception;

/**
 * Thrown by {@code POST /api/v1/imports} ({@code B1}) when the requested
 * {@code filePath} does not reference a file that exists and can be read at
 * submission time (design.md section 3, step 1: "validates the file path is
 * readable").
 *
 * <p>Deliberately does not include the underlying filesystem error message in
 * the exposed detail beyond the path itself — avoids leaking host filesystem
 * internals to API clients (per {@code @124-java-secure-coding}, secure
 * exception handling). </p>
 */
public class FileNotReadableException extends RuntimeException {

    /**
     * @param filePath the submitted file path that could not be read; echoed
     *                 verbatim in the exception message since it is the only
     *                 detail the client-supplied request itself already
     *                 contains
     */
    public FileNotReadableException(String filePath) {
        super("File path is not readable: " + filePath);
    }
}
