package com.planet.importexport.importapi.dto;

import java.util.List;

import com.planet.importexport.importjob.ImportJobDocument;
import com.planet.importexport.staging.StagingEntry;

/**
 * Response body for {@code GET /api/v1/imports/{jobId}} (design.md section 2):
 *
 * {@code { "jobId", "status", "summary": {"totalRows","succeeded","failed"},
 * "stagingErrors": [ {"rowId","rowData", "errorDescription","processedAt"} ] }}.
 *
 * @param jobId         the job's identifier, echoed back from the request
 * @param status        {@link com.planet.importexport.importjob.ImportJobStatus}'s
 *                      name (e.g. {@code PENDING}, {@code RUNNING},
 *                      {@code COMPLETED}, {@code FAILED})
 * @param summary       the row-count summary; see {@link JobSummaryResponse}
 * @param stagingErrors every row staged for this job so far, in the order
 *                      returned by the repository
 */
public record JobStatusResponse(String jobId,
                                String status,
                                JobSummaryResponse summary,
                                List<StagingErrorResponse> stagingErrors) {

    /**
     * Builds the response from the persisted job document and its associated
     * staging entries.
     *
     * @param job            the persisted {@code import_jobs} document
     * @param stagingEntries every staging entry currently associated with
     *                       {@code job}
     * @return the assembled response body
     */
    public static JobStatusResponse from(ImportJobDocument job,
                                         List<StagingEntry> stagingEntries) {
        return new JobStatusResponse(job.id,
                                     job.status.name(),
                                     JobSummaryResponse.from(job.summary),
                                     stagingEntries.stream()
                                                   .map(StagingErrorResponse::from)
                                                   .toList());
    }
}
