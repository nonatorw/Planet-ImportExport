package com.planet.importexport.importapi.validator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.planet.importexport.importapi.model.CsvRow;
import com.planet.importexport.importapi.model.RowOutcome;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ImportRowValidator} covering {@code B4}-{@code B6}:
 * header-driven parsing result validation, missing-value detection,
 * invalid-value detection, and unknown-column routing.
 */
class ImportRowValidatorTest {

    /**
     * A row with every recognized field present and valid validates as a
     * {@link RowOutcome.Success} carrying the record id and the recognized
     * fields converted to their target types (e.g. {@code age} as an int).
     */
    @Test
    void succeedsWithAllRecognizedFieldsPresentAndValid() {
        CsvRow row = new CsvRow(1,
                                orderedMap("id", "1",
                                           "name", "John Smith",
                                           "email", "john@example.com",
                                           "age", "35",
                                           "country", "Portugal",
                                           "phone", "+351910000000"));

        RowOutcome outcome = ImportRowValidator.validate(row, Set.of());

        Assertions.assertThat(outcome)
                  .isInstanceOf(RowOutcome.Success.class);

        RowOutcome.Success success = (RowOutcome.Success) outcome;

        Assertions.assertThat(success.recordId())
                  .isEqualTo("1");

        Assertions.assertThat(success.recognizedFields())
                  .containsEntry("name", "John Smith")
                  .containsEntry("email", "john@example.com")
                  .containsEntry("age", 35)
                  .containsEntry("country", "Portugal")
                  .containsEntry("phone", "+351910000000");
    }

    /**
     * A recognized field that is entirely absent from the header (not merely
     * blank) is simply left out of the resulting recognized fields, rather
     * than causing validation to fail.
     */
    @Test
    void onlyIncludesRecognizedFieldsActuallyPresentInHeader() {
        // Header reordered and missing "phone" entirely (B4: header-driven, not positional).
        CsvRow row = new CsvRow(1,
                                orderedMap("id", "1",
                                           "name", "John Smith",
                                           "email", "john@example.com",
                                           "age", "35",
                                           "country", "Portugal"));

        RowOutcome outcome = ImportRowValidator.validate(row, Set.of());

        Assertions.assertThat(outcome)
                  .isInstanceOf(RowOutcome.Success.class);

        RowOutcome.Success success = (RowOutcome.Success) outcome;

        Assertions.assertThat(success.recognizedFields())
                  .doesNotContainKey("phone");
    }

    /**
     * A row whose {@code id} value is blank fails validation as a
     * {@link RowOutcome.Failure} whose description mentions both "missing"
     * and "id".
     */
    @Test
    void stagesRowWithMissingIdValue() {
        CsvRow row = new CsvRow(4,
                                orderedMap("id", "",
                                           "name", "Ana Costa"));

        RowOutcome outcome = ImportRowValidator.validate(row, Set.of());
        Assertions.assertThat(outcome)
                  .isInstanceOf(RowOutcome.Failure.class);

        Assertions.assertThat(((RowOutcome.Failure) outcome).errorDescription())
                  .contains("missing")
                  .contains("id");
    }

    /**
     * A row whose {@code age} value is present in the header but blank fails
     * validation as a {@link RowOutcome.Failure} whose description mentions
     * both "missing" and "age".
     */
    @Test
    void stagesRowWithMissingRecognizedFieldValue() {
        // customers_02.csv scenario: id "4" has an empty age.
        CsvRow row = new CsvRow(4,
                                orderedMap("id", "4",
                                           "name", "Ana Costa",
                                           "email", "ana@example.com",
                                           "age", "",
                                           "country", "Portugal"));

        RowOutcome outcome = ImportRowValidator.validate(row, Set.of());
        Assertions.assertThat(outcome)
                  .isInstanceOf(RowOutcome.Failure.class);

        String description = ((RowOutcome.Failure) outcome).errorDescription();
        Assertions.assertThat(description)
                  .contains("missing")
                  .contains("age");
    }

    /**
     * A row whose {@code email} value is not a well-formed email address
     * fails validation as a {@link RowOutcome.Failure} whose description
     * mentions "email".
     */
    @Test
    void stagesRowWithInvalidEmail() {
        CsvRow row = new CsvRow(5,
                                orderedMap("id", "5",
                                           "name", "Marco Rossi",
                                           "phone", "+39000000000",
                                           "email", "marco@example",
                                           "age", "40",
                                           "country", "Italy"));

        RowOutcome outcome = ImportRowValidator.validate(row, Set.of());
        Assertions.assertThat(outcome)
                  .isInstanceOf(RowOutcome.Failure.class);

        Assertions.assertThat(((RowOutcome.Failure) outcome).errorDescription())
                  .contains("email");
    }

    /**
     * A row whose {@code age} value is not numeric fails validation as a
     * {@link RowOutcome.Failure} whose description mentions "age".
     */
    @Test
    void stagesRowWithNonNumericAge() {
        CsvRow row = new CsvRow(5,
                                orderedMap("id", "5",
                                           "email", "marco@example.com",
                                           "age", "thirty"));

        RowOutcome outcome = ImportRowValidator.validate(row, Set.of());
        Assertions.assertThat(outcome)
                  .isInstanceOf(RowOutcome.Failure.class);

        Assertions.assertThat(((RowOutcome.Failure) outcome).errorDescription())
                  .contains("age");
    }

    /**
     * A row whose {@code age} value is numeric but outside the accepted
     * range (e.g. 121) fails validation as a {@link RowOutcome.Failure}
     * whose description mentions "age".
     */
    @Test
    void stagesRowWithOutOfRangeAge() {
        CsvRow row = new CsvRow(5,
                                orderedMap("id", "5",
                                           "email", "marco@example.com",
                                           "age", "121"));

        RowOutcome outcome = ImportRowValidator.validate(row, Set.of());
        Assertions.assertThat(outcome)
                  .isInstanceOf(RowOutcome.Failure.class);

        Assertions.assertThat(((RowOutcome.Failure) outcome).errorDescription())
                  .contains("age");
    }

    /**
     * A row under a header that declares a column not in the recognized set
     * fails validation as a {@link RowOutcome.Failure} whose description
     * mentions both "unknown column" and the offending column's name.
     */
    @Test
    void stagesRowWhenHeaderDeclaresUnknownColumn() {
        CsvRow row = new CsvRow(6,
                                orderedMap("id", "6",
                                           "name", "New Customer",
                                           "email", "new@example.com",
                                           "age", "30",
                                           "country", "Germany",
                                           "loyalty_tier", "Gold"));

        RowOutcome outcome =
                ImportRowValidator.validate(row, Set.of("loyalty_tier"));

        Assertions.assertThat(outcome)
                  .isInstanceOf(RowOutcome.Failure.class);

        Assertions.assertThat(((RowOutcome.Failure) outcome).errorDescription())
                  .contains("unknown column")
                  .contains("loyalty_tier");
    }

    /**
     * Builds an order-preserving map from alternating key/value arguments,
     * used to assemble a {@link CsvRow}'s column values in a fixed order.
     *
     * @param keyValuePairs an even-length sequence of alternating keys and
     *                      values
     *
     * @return the assembled map, in the given key order
     */
    private static Map<String, String> orderedMap(String... keyValuePairs) {
        Map<String, String> map = new LinkedHashMap<>();

        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }

        return map;
    }
}
