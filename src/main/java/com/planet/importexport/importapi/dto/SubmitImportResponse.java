package com.planet.importexport.importapi.dto;

/**
 * Response body for {@code POST /api/v1/imports} (design.md section 2;
 * job-status spec, "Status information is available only via query, not at
 * submission" — this response carries only the jobId, no summary or staging
 * list).
 *
 * @param jobId the identifier assigned to the newly accepted import job
 */
public record SubmitImportResponse(
    String jobId) {
    }
