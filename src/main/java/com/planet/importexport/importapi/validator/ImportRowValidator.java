package com.planet.importexport.importapi.validator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.planet.importexport.importapi.ImportProcessingService;
import com.planet.importexport.importapi.model.CsvRow;
import com.planet.importexport.importapi.model.RecognizedImportField;
import com.planet.importexport.importapi.model.RowOutcome;

/**
 * Validates one {@link CsvRow} per design.md section 3, step 5, producing a
 * {@link RowOutcome}.
 *
 * <p>Validation order (any one failure routes the whole row to staging —
 * {@code B6} explicitly requires this even when other columns in the same row
 * are otherwise fine):
 *
 * <ol>
 *   <li>{@code B6} unknown header column: any column name in the file's header
 *       outside {@link RecognizedImportField} fails the row, regardless of
 *       that column's value in this row.</li>
 *   <li>{@code B5.1} missing value: an empty value under a recognized column
 *        fails the row.</li>
 *   <li>{@code B5} invalid value: a present {@code email} or {@code age} value
 *       failing its format check fails the row.</li>
 * </ol>
 *
 * <p>Unknown-column detection is checked once per file header by the caller
 * ({@link ImportProcessingService}), not per row, since the header does not
 * change row to row; this class receives the already-detected unknown columns
 * (if any) to keep the per-row error description consistent with {@code B6}'s
 * required wording.
 */
public final class ImportRowValidator {

    /**
     * Not instantiable: all behavior is exposed through
     * {@link #validate(CsvRow, Set)}.
     */
    private ImportRowValidator() {
        // Utility class.
    }

    /**
     * Validates one row and reports the outcome — either the row's recognized
     * fields, ready to persist, or a human-readable reason it was routed to
     * staging.
     *
     * @param row            the parsed row
     * @param unknownColumns header columns (if any) outside the recognized
     *                       schema — computed once per file by the caller,
     *                       per {@code B6}
     * @return {@link RowOutcome.Success} when every recognized, present field
     *         is valid; {@link RowOutcome.Failure} otherwise
     */
    public static RowOutcome validate(CsvRow row, Set<String> unknownColumns) {
        if (!unknownColumns.isEmpty()) {
            return new RowOutcome.Failure("unknown column(s) in header: " +
                                          String.join(", ",
                                          unknownColumns));
        }

        String recordId = row.valuesByColumnName()
                             .get(RecognizedImportField.ID.columnName());

        if (recordId == null
        ||  recordId.isBlank()) {
            return new RowOutcome.Failure(
                    "missing value for recognized field 'id'");
        }

        Map<String, Object> recognizedFields = new LinkedHashMap<>();

        for (RecognizedImportField field : RecognizedImportField.values()) {
            if (field == RecognizedImportField.ID) {
                continue;
            }

            String columnName = field.columnName();

            if (!row.valuesByColumnName().containsKey(columnName)) {
                // Column not present in this file's header at all — nothing to
                // validate or merge; ADR-0004's merge rule inherits it from
                // the previous version.
                continue;
            }

            String value = row.valuesByColumnName().get(columnName);

            if (value == null
            ||  value.isBlank()) {
                return new RowOutcome.Failure(
                        "missing value for recognized field '" + columnName +
                        "'");
            }

            switch (field) {
                case EMAIL -> {
                    if (!EmailValidator.isValid(value)) {
                        return new RowOutcome.Failure(
                                "invalid email value: '" + value + "'");
                    }

                    recognizedFields.put(columnName, value);
                }

                case AGE -> {
                    if (!AgeValidator.isValid(value)) {
                        return new RowOutcome.Failure(
                                "invalid age value: '" + value +
                                "' is not an integer in range 0-120");
                    }

                    recognizedFields.put(columnName,
                                         Integer.parseInt(value.trim()));
                }

                default -> recognizedFields.put(columnName, value);
            }
        }

        return new RowOutcome.Success(recordId, recognizedFields);
    }
}
