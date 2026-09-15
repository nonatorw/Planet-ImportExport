package com.planet.importexport.customerrecord;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Computes the fields of version N+1 of a {@code customer_records} document,
 * per ADR-0004: "new version = incoming fields override; missing fields
 * inherit from prior version."
 *
 * <p>Concretely: {@code fields(N+1) = fields(N) overridden by any recognized
 * fields present in the new row} (design.md section 1.1, step 5).
 * Only entries whose key is a {@link RecognizedField} are ever considered from
 * the incoming row; anything else is expected to have already been filtered
 * out upstream (unknown columns are staged, not merged —
 * see {@code staging_entries}, Group A2).</p>
 *
 * <p>This is a pure function with no MongoDB or CDI dependency, so it is
 * directly unit-testable without an embedded database.</p>
 */
public final class CustomerRecordVersionMerge {

    /**
     * Prevents instantiation; this class exposes only the static
     * {@link #merge(Map, Map)} function.
     */
    private CustomerRecordVersionMerge() {
        // Utility class.
    }

    /**
     * Merges an incoming row's recognized fields onto the previous version's
     * fields.
     *
     * @param previousVersionFields    the fields of version N, or an empty map
     *                                 if this is the first version ever created
     *                                 for the business id (version 1 has no
     *                                 predecessor to inherit from)
     * @param incomingRecognizedFields the recognized fields present in the
     *                                 newly imported row; a field absent from
     *                                 this map is inherited unchanged from
     *                                 {@code previousVersionFields}
     *
     * @return a new map representing {@code fields(N+1)}; neither input map
     *         is mutated
     *
     * @throws IllegalArgumentException if {@code incomingRecognizedFields}
     *                                  contains a key that is not a
     *                                  {@link RecognizedField}
     */
    public static Map<String, Object> merge(Map<String, Object> previousVersionFields,
                                            Map<String, Object> incomingRecognizedFields) {
        Objects.requireNonNull(previousVersionFields,
                               "previousVersionFields must not be null");

        Objects.requireNonNull(incomingRecognizedFields,
                               "incomingRecognizedFields must not be null");

        Map<String, Object> merged = new LinkedHashMap<>(previousVersionFields);

        incomingRecognizedFields.forEach((fieldName, fieldValue) -> {
            requireRecognized(fieldName);
            merged.put(fieldName, fieldValue);
        });

        return merged;
    }

    /**
     * Validates that a field name from an incoming row belongs to the
     * recognized schema.
     *
     * @param fieldName the incoming row's field name to validate
     *
     * @throws IllegalArgumentException if {@code fieldName} is not a
     *                                  {@link RecognizedField}
     */
    private static void requireRecognized(String fieldName) {
        if (!RecognizedField.isRecognized(fieldName)) {
            throw new IllegalArgumentException("Unrecognized field in incoming row: " + fieldName);
        }
    }
}
