package com.planet.importexport.exportapi.validator;

import java.util.List;

import com.planet.importexport.exportapi.exception.UnknownExportColumnException;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ExportColumnValidator} (task C1).
 */
class ExportColumnValidatorTest {

    /**
     * Validating a column list made up entirely of recognized columns
     * completes without throwing.
     */
    @Test
    void validate_allRecognizedColumns_doesNotThrow() {
        ThrowingCallable shouldRaiseOrNotThrowable =
                () -> ExportColumnValidator.validate(List.of("id",
                                                             "name",
                                                             "email",
                                                             "country"));

        Assertions.assertThatCode(shouldRaiseOrNotThrowable)
                  .doesNotThrowAnyException();
    }

    /**
     * Validating a column list containing exactly one unrecognized column
     * throws an {@link UnknownExportColumnException} that names that column
     * as both the first and only unknown column.
     */
    @Test
    void validate_singleUnknownColumn_throwsNamingIt() {
        ThrowingCallable shouldRaiseThrowable = () ->
                ExportColumnValidator.validate(List.of("id",
                                                       "loyalty_tier"));

        Assertions.assertThatThrownBy(shouldRaiseThrowable)
                  .isInstanceOf(UnknownExportColumnException.class)
                  .satisfies(exception ->
                        validateIfExceptionWasThrownForLoyaltyTier(exception));
    }

    /**
     * Asserts that {@code exception} is an {@link UnknownExportColumnException}
     * naming {@code "loyalty_tier"} as both its first and only unknown column.
     *
     * @param exception the caught exception to inspect
     */
    private void validateIfExceptionWasThrownForLoyaltyTier(Throwable exception) {
        UnknownExportColumnException unknown =
                (UnknownExportColumnException) exception;

        Assertions.assertThat(unknown.firstUnknownColumn())
                  .isEqualTo("loyalty_tier");

        Assertions.assertThat(unknown.unknownColumns())
                  .containsExactly("loyalty_tier");
    }

    /**
     * Validating a column list containing more than one unrecognized column
     * throws an {@link UnknownExportColumnException} whose unknown-columns
     * list captures every one of them, in the order they were requested.
     */
    @Test
    void validate_multipleUnknownColumns_capturesAllOfThem() {
        ThrowingCallable shouldRaiseThrowable = () ->
                ExportColumnValidator.validate(List.of("loyalty_tier",
                                                       "name",
                                                       "vip_flag"));

        Assertions.assertThatThrownBy(shouldRaiseThrowable)
                  .isInstanceOf(UnknownExportColumnException.class)
                  .satisfies(exception ->
                        validateIfExceptionWasThrownForLoyaltyTierOrVipFlag(exception));
    }

    /**
     * Asserts that {@code exception} is an {@link UnknownExportColumnException}
     * whose unknown-columns list contains exactly {@code "loyalty_tier"} and
     * {@code "vip_flag"}, in that order.
     *
     * @param exception the caught exception to inspect
     */
    private void validateIfExceptionWasThrownForLoyaltyTierOrVipFlag(Throwable exception) {
        UnknownExportColumnException unknown =
                (UnknownExportColumnException) exception;

        Assertions.assertThat(unknown.unknownColumns())
                  .containsExactly("loyalty_tier",
                                   "vip_flag");
    }

    /**
     * Validating a column list containing all six recognized columns
     * ({@code id, name, email, age, country, phone}) completes without
     * throwing.
     */
    @Test
    void validate_allSixRecognizedColumns_doesNotThrow() {
        ThrowingCallable shouldRaiseOrNotThrowable = () ->
                ExportColumnValidator.validate(List.of("id",
                                                       "name",
                                                       "email",
                                                       "age",
                                                       "country",
                                                       "phone"));

        Assertions.assertThatCode(shouldRaiseOrNotThrowable)
                  .doesNotThrowAnyException();
    }
}
