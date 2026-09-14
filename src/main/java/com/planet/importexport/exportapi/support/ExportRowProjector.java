package com.planet.importexport.exportapi.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.planet.importexport.customerrecord.CustomerRecordDocument;
import com.planet.importexport.exportapi.model.ExportColumn;

/**
 * Projects a {@link CustomerRecordDocument} (the current version of a business
 * {@code id}) into a flat row of string values, in exactly the caller-requested
 * column order (specs/export/spec.md, "Caller-controlled column selection and
 * order"; design.md section 4, step 2).
 *
 * <p>{@code id} is read from the document's top-level
 * {@link CustomerRecordDocument#recordId}; any other recognized column is read
 * from {@link CustomerRecordDocument#fields}. A value absent from a given
 * record's current version (e.g. {@code phone} was never supplied) is
 * projected as an empty string rather than {@code null}, so every output row
 * has exactly one cell per requested column regardless of that record's field
 * history.
 *
 * <p>A pure function with no MongoDB/CDI dependency, directly unit-testable.
 */
public final class ExportRowProjector {

    /** Not instantiable: all behavior is exposed through {@link #project(CustomerRecordDocument, List)}. */
    private ExportRowProjector() {
        // Utility class.
    }

    /**
     * @param record           the current version of a stored customer record
     * @param requestedColumns the requested columns, in the exact order the
     *                         output row must honor
     * @return one row value per requested column, in the same order, with
     *         empty strings standing in for values absent from {@code record}
     */
    public static List<String> project(CustomerRecordDocument record,
                                       List<String> requestedColumns) {
        Objects.requireNonNull(record,
                               "record must not be null");

        Objects.requireNonNull(requestedColumns,
                               "requestedColumns must not be null");

        List<String> row = new ArrayList<>(requestedColumns.size());

        for (String column : requestedColumns) {
            row.add(valueFor(record, column));
        }

        return row;
    }

    private static String valueFor(CustomerRecordDocument record,
                                   String column) {
        if (ExportColumn.ID.columnName()
                           .equals(column)) {
            return record.recordId == null
                 ? ""
                 : record.recordId;
        }

        Object value = record.fields.get(column);

        return value == null
             ? ""
             : String.valueOf(value);
    }
}
