package com.planet.importexport.jobconfig.exception;

import com.planet.importexport.jobconfig.JobConfigurationValueType;

/**
 * Thrown when a job configuration entry's {@code value} does not parse
 * according to its declared {@code valueType} (ADR-0007, write-time
 * validation). This is an application-level (unchecked) validation failure,
 * not an infrastructure/Mongo error — callers (e.g. the future Group D REST
 * layer) are expected to translate it into their own transport-specific error
 * response; this exception carries no HTTP concern itself.
 */
public class InvalidJobConfigurationValueException extends RuntimeException {
    /**
     * The configuration key whose value failed validation.
     */
    private final String key;

    /**
     * The declared type {@link #value} was validated against.
     */
    private final JobConfigurationValueType valueType;

    /**
     * The raw, string-encoded value that failed to parse as {@link #valueType}.
     */
    private final String value;

    /**
     * Builds the exception, capturing the offending {@code key}/{@code value}/
     * {@code valueType} triple and deriving a diagnostic message from them
     * (ADR-0007, write-time validation).
     *
     * @param key       the configuration key whose value failed validation
     * @param valueType the declared type {@code value} was validated against
     * @param value     the raw, string-encoded value that failed to parse
     */
    public InvalidJobConfigurationValueException(String key,
                                                 JobConfigurationValueType valueType,
                                                 String value) {
        super(message(key, valueType, value));
        this.key = key;
        this.valueType = valueType;
        this.value = value;
    }

    /**
     * Builds the diagnostic message embedded in this exception. Kept free of
     * any transport-specific wording since this exception carries no HTTP
     * concern itself (see class-level Javadoc).
     *
     * @param key       the configuration key whose value failed validation
     * @param valueType the declared type {@code value} was validated against
     * @param value     the raw, string-encoded value that failed to parse
     * @return a human-readable description of the validation failure
     */
    private static String message(String key,
                                  JobConfigurationValueType valueType,
                                  String value) {
        return "job configuration '%s': value '%s' does not parse as declared valueType %s"
                .formatted(key, value, valueType);
    }

    /**
     * Returns the configuration key whose value failed validation.
     *
     * @return the configuration key
     */
    public String key() {
        return key;
    }

    /**
     * Returns the declared type {@link #value} was validated against.
     *
     * @return the declared value type
     */
    public JobConfigurationValueType valueType() {
        return valueType;
    }

    /**
     * Returns the raw, string-encoded value that failed to parse as
     * {@link #valueType}.
     *
     * @return the offending value
     */
    public String value() {
        return value;
    }
}
