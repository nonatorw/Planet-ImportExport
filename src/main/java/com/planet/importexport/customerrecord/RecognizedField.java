package com.planet.importexport.customerrecord;

/**
 * The recognized {@code customer_records} schema fields (design.md section 1.1): the only keys
 * ever populated in a {@link CustomerRecordDocument#fields} map. The business identity key
 * ({@code id}) is a top-level document field, not one of these.
 *
 * <p>This enum is intentionally scoped to the {@code customerrecord} package (Group A1). Per
 * {@code docs/openspec/changes/file-import-export/tasks.md} ("Caveat — shared recognized-schema
 * constant"), the Import (Group B) and Export (Group C) capabilities may define their own copies
 * of this recognized column list rather than share this one, to avoid a cross-group file-contention
 * point; nothing in the design mandates a single shared constant.
 */
public enum RecognizedField {
    NAME("name"),
    EMAIL("email"),
    AGE("age"),
    COUNTRY("country"),
    PHONE("phone");

    private final String fieldName;

    RecognizedField(String fieldName) {
        this.fieldName = fieldName;
    }

    public String fieldName() {
        return fieldName;
    }

    public static boolean isRecognized(String candidateFieldName) {
        for (RecognizedField field : values()) {
            if (field.fieldName.equals(candidateFieldName)) {
                return true;
            }
        }
        return false;
    }
}
