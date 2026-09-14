package com.planet.importexport.importapi.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * The recognized CSV header columns for the Import capability
 * (design.md sections 3 and 4):
 * {@code id, name, email, age, country, phone}.
 *
 * <p>{@code id} is the business identity key (a top-level {@code
 * customer_records} field, not a {@code fields} entry — see {@code
 * CustomerRecordDocument#recordId}); the remaining five are the
 * schema-flexible fields recognized by {@code customer_records.fields}
 * (mirroring {@code com.planet.importexport.customerrecord.RecognizedField}).
 *
 * <p>This is a deliberate local copy, not a reuse of {@code
 * com.planet.importexport.customerrecord.RecognizedField}: per {@code
 * tasks.md}'s "Caveat — shared recognized-schema constant", Import (Group B)
 * and Export (Group C) each define their own copy of this column list to avoid
 * a cross-group file-contention point. Nothing in the design mandates a single
 * shared constant.
 */
public enum RecognizedImportField {
    ID("id"),
    NAME("name"),
    EMAIL("email"),
    AGE("age"),
    COUNTRY("country"),
    PHONE("phone");

    /** The exact CSV header column name this constant recognizes. */
    private final String columnName;

    /**
     * @param columnName the exact CSV header column name this constant
     *                   recognizes
     */
    RecognizedImportField(String columnName) {
        this.columnName = columnName;
    }

    /**
     * @return the exact CSV header column name this constant recognizes
     */
    public String columnName() {
        return columnName;
    }

    /**
     * @param candidateColumnName a header column name to test
     * @return {@code true} if {@code candidateColumnName} matches a
     *         recognized field's {@link #columnName()}
     */
    public static boolean isRecognized(String candidateColumnName) {
        return findByColumnName(candidateColumnName).isPresent();
    }

    /**
     * @param candidateColumnName a header column name to look up
     * @return the matching constant, or {@link Optional#empty()} if
     *         {@code candidateColumnName} matches no recognized field
     */
    public static Optional<RecognizedImportField> findByColumnName(String candidateColumnName) {
        return Arrays.stream(values())
                     .filter(field -> field.columnName.equals(candidateColumnName))
                     .findFirst();
    }
}
