package com.planet.importexport.importapi.validator;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AgeValidator} ({@code B5};
 * ADR-0005 confirmed decision 14: integer 0-120 inclusive).
 */
class AgeValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "35", "120"})
    void acceptsInRangeIntegers(String age) {
        assertThat(AgeValidator.isValid(age)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"thirty", "121", "-1", "35.5", "", "  "})
    void rejectsNonNumericOrOutOfRange(String age) {
        assertThat(AgeValidator.isValid(age)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    void rejectsNull(String age) {
        assertThat(AgeValidator.isValid(age)).isFalse();
    }
}
