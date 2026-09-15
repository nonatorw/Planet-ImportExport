package com.planet.importexport.importapi.validator;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link EmailValidator} ({@code B5}; ADR-0005 confirmed decision 13: simplified
 * RFC 5322, {@code user@domain.tld}).
 */
class EmailValidatorTest {

    /**
     * A well-formed {@code local-part@domain.tld} address is accepted.
     */
    @ParameterizedTest
    @ValueSource(strings = {"john@example.com", "jane.doe@example.co.uk", "a@b.co"})
    void acceptsValidAddresses(String email) {
        Assertions.assertThat(EmailValidator.isValid(email))
                  .isTrue();
    }

    /**
     * A value missing a top-level domain, an {@code @}, or otherwise
     * malformed is rejected.
     */
    @ParameterizedTest
    @CsvSource({
        "marco@example",   // no top-level domain — the spec's own negative example
        "plainstring",
        "@example.com",
        "john@.com",
        "john@example.",
        "'john doe@example.com'",
    })
    void rejectsInvalidAddresses(String email) {
        Assertions.assertThat(EmailValidator.isValid(email))
                  .isFalse();
    }

    /**
     * A {@code null} value is rejected.
     */
    @ParameterizedTest
    @NullSource
    void rejectsNull(String email) {
        Assertions.assertThat(EmailValidator.isValid(email))
                  .isFalse();
    }
}
