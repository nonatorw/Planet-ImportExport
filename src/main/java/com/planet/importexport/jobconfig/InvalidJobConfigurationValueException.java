package com.planet.importexport.jobconfig;

/**
 * Thrown when a job configuration entry's {@code value} does not parse according to its declared
 * {@code valueType} (ADR-0007, write-time validation). This is an application-level (unchecked)
 * validation failure, not an infrastructure/Mongo error — callers (e.g. the future Group D REST
 * layer) are expected to translate it into their own transport-specific error response; this
 * exception carries no HTTP concern itself.
 */
public class InvalidJobConfigurationValueException extends RuntimeException {

    private final String key;
    private final JobConfigurationValueType valueType;
    private final String value;

    public InvalidJobConfigurationValueException(String key, JobConfigurationValueType valueType, String value) {
        super(message(key, valueType, value));
        this.key = key;
        this.valueType = valueType;
        this.value = value;
    }

    private static String message(String key, JobConfigurationValueType valueType, String value) {
        return "job configuration '%s': value '%s' does not parse as declared valueType %s"
                .formatted(key, value, valueType);
    }

    public String key() {
        return key;
    }

    public JobConfigurationValueType valueType() {
        return valueType;
    }

    public String value() {
        return value;
    }
}
