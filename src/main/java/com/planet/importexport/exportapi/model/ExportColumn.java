package com.planet.importexport.exportapi.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * The recognized {@code customer_records} schema columns that an export
 * request may select (design.md sections 1.1 and 4; specs/export/spec.md,
 * "Unrecognized requested columns are rejected explicitly").
 *
 * <p>Unlike {@code customerrecord.RecognizedField}, this set includes
 * {@code id} — the business identity key is a top-level
 * {@code CustomerRecordDocument} field, not one of the {@code fields} map
 * entries, but it is still a column a caller may request in an export
 * (design.md section 2: {@code "columns": ["id","name","email","country"]}).</p>
 *
 * <p>This enum is intentionally a local copy scoped to the {@code exportapi}
 * package (Group C), not a reuse of {@code customerrecord.RecognizedField}.
 * Per {@code docs/openspec/changes/file-import-export/tasks.md} ("Caveat —
 * shared recognized-schema constant"),
 * Import (Group B) and Export (Group C) each define their own copy of the
 * recognized column list rather than share one, to avoid a cross-group
 * file-contention point while both groups implement in parallel.</p>
 */
public enum ExportColumn {
    ID("id"),
    NAME("name"),
    EMAIL("email"),
    AGE("age"),
    COUNTRY("country"),
    PHONE("phone");

    /**
     * The exact column name this constant recognizes.
     */
    private final String columnName;

    /**
     * Creates a new export column constant.
     *
     * @param columnName the exact column name this constant recognizes
     */
    ExportColumn(String columnName) {
        this.columnName = columnName;
    }

    /**
     * Returns the exact column name this constant recognizes.
     *
     * @return the exact column name this constant recognizes
     */
    public String columnName() {
        return columnName;
    }

    /**
     * Checks whether a candidate column name matches a recognized column.
     *
     * @param candidateColumnName a column name to test
     *
     * @return {@code true} if {@code candidateColumnName} matches a
     *         recognized column's {@link #columnName()}
     */
    public static boolean isRecognized(String candidateColumnName) {
        return findByColumnName(candidateColumnName).isPresent();
    }

    /**
     * Looks up the export column constant matching a candidate column name.
     *
     * @param candidateColumnName a column name to look up
     *
     * @return the matching constant, or {@link Optional#empty()} if
     *         {@code candidateColumnName} matches no recognized column
     */
    public static Optional<ExportColumn> findByColumnName(String candidateColumnName) {
        return Arrays.stream(values())
                     .filter(column -> column.columnName.equals(candidateColumnName))
                     .findFirst();
    }
}
