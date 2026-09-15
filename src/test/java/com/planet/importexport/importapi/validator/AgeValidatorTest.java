package com.planet.importexport.importapi.validator;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link AgeValidator} ({@code B5};
 * ADR-0005 confirmed decision 14: integer 0-120 inclusive).
 */
class AgeValidatorTest {

    /**
     * Every integer within the inclusive {@code 0-120} range is accepted.
     */
    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "35", "120"})
    void acceptsInRangeIntegers(String age) {
        Assertions.assertThat(AgeValidator.isValid(age))
                  .isTrue();
    }

    /**
     * A non-numeric, non-integer, or out-of-range value is rejected.
     */
    @ParameterizedTest
    @ValueSource(strings = {"thirty", "121", "-1", "35.5", "", "  "})
    void rejectsNonNumericOrOutOfRange(String age) {
        Assertions.assertThat(AgeValidator.isValid(age))
                  .isFalse();
    }

    /**
     * A {@code null} value is rejected.
     */
    @ParameterizedTest
    @NullSource
    void rejectsNull(String age) {
        Assertions.assertThat(AgeValidator.isValid(age))
                  .isFalse();
    }
}
