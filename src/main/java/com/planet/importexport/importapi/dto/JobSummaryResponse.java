package com.planet.importexport.importapi.dto;

import com.planet.importexport.importjob.ImportJobSummary;

/**
 * The {@code summary} object nested in {@link JobStatusResponse}
 * (design.md section 2).
 *
 * @param totalRows the number of data rows read from the source file so far
 * @param succeeded the number of rows successfully persisted as a
 *                  {@code customer_records} version
 * @param failed    the number of rows routed to staging
 */
public record JobSummaryResponse(
    int totalRows,
    int succeeded,
    int failed) {

    /**
     * Converts the internal {@link ImportJobSummary} value into its API
     * representation.
     *
     * @param summary the persisted summary
     *
     * @return the equivalent response DTO
     */
    public static JobSummaryResponse from(ImportJobSummary summary) {
        return new JobSummaryResponse(summary.totalRows,
                                      summary.succeeded,
                                      summary.failed);
    }
}
