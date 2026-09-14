package com.planet.importexport.importapi.dto;

import java.time.Instant;
import java.util.Map;

import com.planet.importexport.staging.StagingEntry;

/**
 * One entry of {@link JobStatusResponse#stagingErrors()} (job-status spec,
 * "Detailed staging error list on status query": "each carrying {@code jobId},
 * {@code rowId}, {@code rowData}, {@code errorDescription}, and
 * {@code processedAt}").
 *
 * @param jobId            the import job the staged row belongs to
 * @param rowId            the row's 1-based position within the source file,
 *                         header excluded
 * @param rowData          the row's raw values, keyed by source column name
 * @param errorDescription free text explaining why the row was staged
 * @param processedAt      when the row was staged
 */
public record StagingErrorResponse(String jobId,
                                   int rowId,
                                   Map<String, Object> rowData,
                                   String errorDescription,
                                   Instant processedAt) {

    /**
     * Converts a persisted {@link StagingEntry} into its API representation.
     *
     * @param entry the persisted staging entry
     * @return the equivalent response DTO
     */
    public static StagingErrorResponse from(StagingEntry entry) {
        return new StagingErrorResponse(entry.jobId,
                                        entry.rowId,
                                        entry.rowData,
                                        entry.errorDescription,
                                        entry.processedAt);
    }
}
