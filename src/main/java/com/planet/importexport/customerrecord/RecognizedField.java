package com.planet.importexport.customerrecord;

import java.util.Arrays;

/**
 * The recognized {@code customer_records} schema fields (design.md section 1.1):
 * the only keys ever populated in a {@link CustomerRecordDocument#fields} map.
 * The business identity key ({@code id}) is a top-level document field, not
 * one of these.
 *
 * <p>This enum is intentionally scoped to the {@code customerrecord} package
 * (Group A1). Per {@code docs/openspec/changes/file-import-export/tasks.md}
 * ("Caveat — shared recognized-schema constant"), the Import (Group B) and
 * Export (Group C) capabilities may define their own copies of this recognized
 * column list rather than share this one, to avoid a cross-group
 * file-contention point; nothing in the design mandates a single shared
 * constant.
 */
public enum RecognizedField {
    NAME("name"),
    EMAIL("email"),
    AGE("age"),
    COUNTRY("country"),
    PHONE("phone");

    private final String fieldName;

    /**
     * Associates this constant with its recognized-schema field name.
     *
     * @param fieldName the field name as it appears in {@link CustomerRecordDocument#fields}
     */
    RecognizedField(String fieldName) {
        this.fieldName = fieldName;
    }

    /**
     * The field name as it appears in {@link CustomerRecordDocument#fields}.
     *
     * @return the recognized-schema field name
     */
    public String fieldName() {
        return fieldName;
    }

    /**
     * Checks whether a candidate field name matches one of the recognized-schema
     * fields.
     *
     * @param candidateFieldName the field name to check
     * @return {@code true} if {@code candidateFieldName} equals the
     *         {@link #fieldName()} of some constant of this enum, {@code false}
     *         otherwise
     */
    public static boolean isRecognized(String candidateFieldName) {
        return Arrays.stream(values())
                     .anyMatch(field -> field.fieldName.equals(candidateFieldName));
    }
}
