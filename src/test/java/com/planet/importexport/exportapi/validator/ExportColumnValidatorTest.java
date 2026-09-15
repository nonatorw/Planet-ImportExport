package com.planet.importexport.exportapi.validator;

import java.util.List;

import com.planet.importexport.exportapi.exception.UnknownExportColumnException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for {@link ExportColumnValidator} (task C1). */
class ExportColumnValidatorTest {

    @Test
    void validate_allRecognizedColumns_doesNotThrow() {
        assertThatCode(
                () -> ExportColumnValidator.validate(List.of("id", "name", "email", "country")))
                                           .doesNotThrowAnyException();
    }

    @Test
    void validate_singleUnknownColumn_throwsNamingIt() {
        assertThatThrownBy(() ->
                ExportColumnValidator.validate(List.of("id", "loyalty_tier")))
                                     .isInstanceOf(UnknownExportColumnException.class)
                                     .satisfies(exception -> {
                                          UnknownExportColumnException unknown =
                                                  (UnknownExportColumnException) exception;
                                          assertThat(unknown.firstUnknownColumn())
                                                  .isEqualTo("loyalty_tier");
                                          assertThat(unknown.unknownColumns())
                                                  .containsExactly("loyalty_tier");
                                      });
    }

    @Test
    void validate_multipleUnknownColumns_capturesAllOfThem() {
        assertThatThrownBy(() ->
                ExportColumnValidator.validate(List.of("loyalty_tier", "name", "vip_flag")))
                                     .isInstanceOf(UnknownExportColumnException.class)
                                     .satisfies(exception -> {
                                          UnknownExportColumnException unknown =
                                                  (UnknownExportColumnException) exception;
                                          assertThat(unknown.unknownColumns())
                                                  .containsExactly("loyalty_tier", "vip_flag");
                                      });
    }

    @Test
    void validate_allSixRecognizedColumns_doesNotThrow() {
        assertThatCode(() ->
                ExportColumnValidator.validate(List.of("id", "name", "email", "age", "country", "phone")))
                                     .doesNotThrowAnyException();
    }
}
