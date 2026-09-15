package com.planet.importexport.importapi.model;

import java.util.Map;

/**
 * The result of validating one {@link CsvRow} against the recognized schema
 * (design.md section 3, step 5): either the row's recognized fields are ready
 * to persist as the next {@code customer_records} version ({@link Success}),
 * or the row must be staged with a human-readable reason ({@link Failure}) —
 * {@code B5} (invalid value), {@code B5.1} (missing value), or {@code B6}
 * (unknown header column).
 *
 * <p>Modeled as a sealed result rather than throwing, per {@code
 * @143-java-functional-exception-handling}: a row failing validation is an
 * expected outcome of import processing, not a programming error or
 * infrastructure fault.</p>
 */
public sealed interface RowOutcome {
    /**
     * A row that passed validation and is ready to persist as the next
     * {@code customer_records} version.
     *
     * @param recordId         the row's {@code id} column value
     * @param recognizedFields only {@link RecognizedImportField} keys other
     *                         than {@code id} that were present (non-blank)
     *                         in this row; ready to pass directly to
     * {@link
     *     com.planet.importexport.customerrecord.CustomerRecordRepository#insertNextVersion}
     */
    record Success(String recordId,
                   Map<String, Object> recognizedFields)
            implements RowOutcome {}

    /**
     * A row that failed validation and must be staged instead of persisted.
     *
     * @param errorDescription free text distinguishing
     *                         missing/invalid/unknown-column (ADR-0005)
     */
    record Failure(String errorDescription)
            implements RowOutcome {}
}
