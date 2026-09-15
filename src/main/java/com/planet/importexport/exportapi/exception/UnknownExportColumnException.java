package com.planet.importexport.exportapi.exception;

import java.util.List;

/**
 * Thrown when an export request names one or more columns outside the
 * recognized schema (specs/export/spec.md, "Unrecognized requested columns are
 * rejected explicitly"; design.md
 * section 2: {@code {"error": "unknown column", "column": "loyalty_tier"}}).
 *
 * <p>This is an application-level (unchecked) validation failure;
 * {@link com.planet.importexport.exportapi.exception.mapper.UnknownColumnMapper}
 * translates it into an HTTP 400 response naming the invalid column(s).</p>
 */
public class UnknownExportColumnException extends RuntimeException {

    private final List<String> unknownColumns;

    /**
     * Creates a new exception for a set of unrecognized requested columns.
     *
     * @param unknownColumns every requested column name found outside the
     *                       recognized export schema, in the order they were
     *                       requested; must not be empty
     */
    public UnknownExportColumnException(List<String> unknownColumns) {
        super("unknown export column(s): " + unknownColumns);
        this.unknownColumns = List.copyOf(unknownColumns);
    }

    /**
     * Returns the first unrecognized column requested.
     *
     * @return the first unrecognized column, for the single-column error body
     *         shape (design.md section 2)
     */
    public String firstUnknownColumn() {
        return unknownColumns.get(0);
    }

    /**
     * Returns every unrecognized column requested.
     *
     * @return every unrecognized column found in the request, in the order
     *         they were requested
     */
    public List<String> unknownColumns() {
        return unknownColumns;
    }
}
