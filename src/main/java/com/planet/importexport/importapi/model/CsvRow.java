package com.planet.importexport.importapi.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One parsed data row of a source CSV file, per {@code B4} (header-driven row
 * parsing).
 *
 * @param rowId              the row's 1-based position within the source file,
 *                           header excluded (matches
 *                  {@link com.planet.importexport.staging.StagingEntry#rowId})
 * @param valuesByColumnName the raw string value for every column present in
 *                           the file's header, keyed by that column's exact
 *                           header name — including columns outside the
 *                           recognized schema ({@code B6}), and including
 *                           empty strings for a present-but-blank cell
 *                           ({@code B5.1}); a column entirely absent from the
 *                           header never appears as a key here
 */
public record CsvRow(int rowId, Map<String, String> valuesByColumnName) {

    /**
     * Defensively copies {@code valuesByColumnName} into a new,
     * insertion-ordered map so the record cannot be mutated through a
     * reference the caller retains.
     */
    public CsvRow {
        valuesByColumnName = new LinkedHashMap<>(valuesByColumnName);
    }

    /**
     * Widens this row's values to {@code Map<String, Object>} for storage in
     * {@link com.planet.importexport.staging.StagingEntry#rowData}, which
     * holds heterogeneous field types across the codebase (unlike this
     * class's raw, always-{@code String} CSV cell values).
     *
     * @return a new, independent copy of {@link #valuesByColumnName()}
     */
    public Map<String, Object> asRowData() {
        return new LinkedHashMap<>(valuesByColumnName);
    }
}
