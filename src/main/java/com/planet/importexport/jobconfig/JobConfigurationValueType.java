package com.planet.importexport.jobconfig;

/**
 * The declared type of a {@link JobConfigurationEntry#value} (ADR-0007): the storage document keeps
 * {@code value} as a free-form string, but this declared type lets both the write path (validation)
 * and the read path (typed parsing) agree on a single, predictable interpretation of that string.
 *
 * <p>Per ADR-0007, {@code STRING} accepts any value unconditionally; {@code INTEGER} requires the
 * value to parse as a Java {@code int}; {@code BOOLEAN} requires the value to parse as {@code true}
 * or {@code false} (case-insensitive), rejecting anything else — including numeric/truthy surrogates
 * such as {@code "1"} or {@code "yes"}, since ADR-0007 explicitly cites inconsistent boolean-truthy
 * handling across consumers as the risk a declared type is meant to remove.
 */
public enum JobConfigurationValueType {
    STRING {
        @Override
        public boolean isValid(String value) {
            return value != null;
        }
    },
    INTEGER {
        @Override
        public boolean isValid(String value) {
            if (value == null) {
                return false;
            }
            try {
                Integer.parseInt(value);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    },
    BOOLEAN {
        @Override
        public boolean isValid(String value) {
            return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
        }
    };

    /** Whether {@code value} parses according to this declared type (ADR-0007, write-time check). */
    public abstract boolean isValid(String value);

    /**
     * Validates {@code value} against this type, throwing if it does not conform.
     *
     * @throws InvalidJobConfigurationValueException if {@code value} does not parse as this type
     */
    public void validate(String key, String value) {
        if (!isValid(value)) {
            throw new InvalidJobConfigurationValueException(key, this, value);
        }
    }
}
