package com.planet.importexport.importapi.validator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.planet.importexport.importapi.model.CsvRow;
import com.planet.importexport.importapi.model.RowOutcome;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ImportRowValidator} covering {@code B4}-{@code B6}:
 * header-driven parsing result validation, missing-value detection,
 * invalid-value detection, and unknown-column routing.
 */
class ImportRowValidatorTest {

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

        assertThat(outcome).isInstanceOf(RowOutcome.Success.class);
        RowOutcome.Success success = (RowOutcome.Success) outcome;
        assertThat(success.recordId()).isEqualTo("1");
        assertThat(success.recognizedFields())
                .containsEntry("name", "John Smith")
                .containsEntry("email", "john@example.com")
                .containsEntry("age", 35)
                .containsEntry("country", "Portugal")
                .containsEntry("phone", "+351910000000");
    }

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
        assertThat(outcome)
                .isInstanceOf(RowOutcome.Success.class);

        RowOutcome.Success success = (RowOutcome.Success) outcome;
        assertThat(success.recognizedFields())
                .doesNotContainKey("phone");
    }

    @Test
    void stagesRowWithMissingIdValue() {
        CsvRow row = new CsvRow(4,
                                orderedMap("id", "",
                                           "name", "Ana Costa"));

        RowOutcome outcome = ImportRowValidator.validate(row, Set.of());
        assertThat(outcome)
                .isInstanceOf(RowOutcome.Failure.class);

        assertThat(((RowOutcome.Failure) outcome).errorDescription())
                .contains("missing")
                .contains("id");
    }

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
        assertThat(outcome)
                .isInstanceOf(RowOutcome.Failure.class);

        String description = ((RowOutcome.Failure) outcome).errorDescription();
        assertThat(description)
                .contains("missing")
                .contains("age");
    }

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
        assertThat(outcome)
                .isInstanceOf(RowOutcome.Failure.class);

        assertThat(((RowOutcome.Failure) outcome).errorDescription())
                .contains("email");
    }

    @Test
    void stagesRowWithNonNumericAge() {
        CsvRow row = new CsvRow(5,
                                orderedMap("id", "5",
                                           "email", "marco@example.com",
                                           "age", "thirty"));

        RowOutcome outcome = ImportRowValidator.validate(row, Set.of());
        assertThat(outcome)
                .isInstanceOf(RowOutcome.Failure.class);

        assertThat(((RowOutcome.Failure) outcome).errorDescription())
                .contains("age");
    }

    @Test
    void stagesRowWithOutOfRangeAge() {
        CsvRow row = new CsvRow(5,
                                orderedMap("id", "5",
                                           "email", "marco@example.com",
                                           "age", "121"));

        RowOutcome outcome = ImportRowValidator.validate(row, Set.of());
        assertThat(outcome)
                .isInstanceOf(RowOutcome.Failure.class);

        assertThat(((RowOutcome.Failure) outcome).errorDescription())
                .contains("age");
    }

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

        assertThat(outcome)
                .isInstanceOf(RowOutcome.Failure.class);

        assertThat(((RowOutcome.Failure) outcome).errorDescription())
                .contains("unknown column")
                .contains("loyalty_tier");
    }

    private static Map<String, String> orderedMap(String... keyValuePairs) {
        Map<String, String> map = new LinkedHashMap<>();

        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }

        return map;
    }
}
