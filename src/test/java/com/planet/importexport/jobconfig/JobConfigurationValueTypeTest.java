package com.planet.importexport.jobconfig;

import com.planet.importexport.jobconfig.exception.InvalidJobConfigurationValueException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

/**
 * Pure unit tests for the ADR-0007 write-time validation rules — no CDI/Mongo
 * bootstrap needed, per {@code @421-frameworks-quarkus-testing-unit-tests}
 * ("never boot Quarkus for pure domain logic"). Uses plain JUnit 5 assertions
 * rather than AssertJ/Mockito, since neither is currently a project test
 * dependency in {@code build.gradle} and adding one is out of this task's
 * scope (shared-file boundary, see AGENTS.md "ask first: adding new
 * dependencies").
 */
class JobConfigurationValueTypeTest {
    @ParameterizedTest
    @CsvSource({"0", "1", "-1", "500", "2147483647", "-2147483648"})
    void integerType_acceptsParsableIntegers(String value) {
        Assertions.assertTrue(JobConfigurationValueType.INTEGER.isValid(value));
    }

    @ParameterizedTest
    @CsvSource({"not-a-number", "1.5", "1e3", "0x10"})
    void integerType_rejectsNonParsableValues(String value) {
        Assertions.assertFalse(JobConfigurationValueType.INTEGER.isValid(value));
    }

    @ParameterizedTest
    @NullSource
    void integerType_rejectsNullValue(String value) {
        Assertions.assertFalse(JobConfigurationValueType.INTEGER.isValid(value));
    }

    @ParameterizedTest
    @CsvSource({"true", "false", "TRUE", "FALSE", "True", "False"})
    void booleanType_acceptsTrueOrFalseCaseInsensitively(String value) {
        Assertions.assertTrue(JobConfigurationValueType.BOOLEAN.isValid(value));
    }

    @ParameterizedTest
    @CsvSource({"1", "0", "yes", "no", "on", "off", "''"})
    void booleanType_rejectsTruthySurrogates(String value) {
        /*
         * ADR-0007 explicitly calls out inconsistent boolean-truthy handling
         * as the risk a declared valueType removes — "1"/"yes"/"on" must not
         * be silently accepted as true.
         */
        Assertions.assertFalse(JobConfigurationValueType.BOOLEAN.isValid(value));
    }

    @ParameterizedTest
    @NullSource
    void booleanType_rejectsNullValue(String value) {
        Assertions.assertFalse(JobConfigurationValueType.BOOLEAN.isValid(value));
    }

    @ParameterizedTest
    @CsvSource({"''", "anything", "500", "true", "not-a-number"})
    void stringType_acceptsAnyNonNullValue(String value) {
        Assertions.assertTrue(JobConfigurationValueType.STRING.isValid(value));
    }

    @ParameterizedTest
    @NullSource
    void stringType_rejectsNullValue(String value) {
        Assertions.assertFalse(JobConfigurationValueType.STRING.isValid(value));
    }

    @ParameterizedTest
    @EnumSource(JobConfigurationValueType.class)
    void validate_throwsInvalidJobConfigurationValueException_whenValueDoesNotParse(
            JobConfigurationValueType valueType) {

        /*
         * STRING accepts everything except null, so use null as the
         * universally-invalid case.
         */
        InvalidJobConfigurationValueException exception =
                Assertions.assertThrows(
                    InvalidJobConfigurationValueException.class,
                    () -> valueType.validate("someKey",
                                             null));

        Assertions.assertTrue(exception.getMessage().contains("someKey"));

        Assertions.assertTrue(exception.getMessage().contains(valueType.name()));
    }

    /**
     * Validating a value declared as {@code INTEGER} that does not parse as
     * an integer throws {@link InvalidJobConfigurationValueException}.
     */
    @Test
    void validate_rejectsNonIntegerValueDeclaredAsInteger() {
        // The exact scenario from ADR-0007's Confirmation section.
        Assertions.assertThrows(
            InvalidJobConfigurationValueException.class,
            () -> JobConfigurationValueType.INTEGER.validate("chunkSize",
                                                             "not-a-number"));
    }

    /**
     * Validating a value that matches its declared type — for
     * {@code INTEGER}, {@code BOOLEAN}, and {@code STRING} alike — does not
     * throw.
     */
    @Test
    void validate_doesNotThrow_whenValueMatchesDeclaredType() {
        JobConfigurationValueType.INTEGER.validate("chunkSize", "500");
        JobConfigurationValueType.BOOLEAN.validate("flag", "true");
        JobConfigurationValueType.STRING.validate("anything", "whatever");
    }
}
