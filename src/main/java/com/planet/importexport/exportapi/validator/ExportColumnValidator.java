package com.planet.importexport.exportapi.validator;

import java.util.ArrayList;
import java.util.List;

import com.planet.importexport.exportapi.exception.UnknownExportColumnException;
import com.planet.importexport.exportapi.model.ExportColumn;

/**
 * Validates a requested column list against the recognized export schema
 * (task C1; specs/export/spec.md, "Unrecognized requested columns are rejected
 * explicitly").
 *
 * <p>A pure function with no CDI/MongoDB dependency, directly unit-testable.
 * Storage is never touched before this validation passes (design.md section 4,
 * step 1).
 */
public final class ExportColumnValidator {

    /** Not instantiable: all behavior is exposed through {@link #validate(List)}. */
    private ExportColumnValidator() {
        // Utility class.
    }

    /**
     * @param requestedColumns the caller-requested column names, in the order
     *                         requested
     * @throws UnknownExportColumnException if any requested column is outside
     *                                      the recognized schema ({@code id,
     *                                      name, email, age, country, phone});
     *                                      the exception carries every invalid
     *                                      column found, not just the first
     */
    public static void validate(List<String> requestedColumns) {
        List<String> unknownColumns = new ArrayList<>();

        for (String requestedColumn : requestedColumns) {
            if (!ExportColumn.isRecognized(requestedColumn)) {
                unknownColumns.add(requestedColumn);
            }
        }

        if (!unknownColumns.isEmpty()) {
            throw new UnknownExportColumnException(unknownColumns);
        }
    }
}
