package com.planet.importexport.importapi.exception;

/**
 * Thrown by {@code GET /api/v1/imports/{jobId}} ({@code B10}) when
 * {@code jobId} is unknown.
 */
public class ImportJobNotFoundException extends RuntimeException {

    /**
     * Creates a new exception for a job identifier that has no matching import
     * job.
     *
     * @param jobId the requested job identifier that has no matching
     *              {@code import_jobs} document
     */
    public ImportJobNotFoundException(String jobId) {
        super("No import job found for jobId: " + jobId);
    }
}
