package com.planet.importexport.importjob;

/**
 * The {@code import_jobs.summary} nested object (design.md section 1.3): row-outcome counters for
 * a job, finalized once the job reaches a terminal status (design.md section 3, step 6).
 *
 * <p>A no-args constructor and public mutable fields are used (rather than a record) so the BSON
 * POJO codec Quarkus MongoDB Panache configures by default can decode this as a nested document
 * without extra codec registration, matching the plain-field style already used by sibling
 * document classes in this codebase (e.g. {@code CustomerRecordDocument}).
 */
public class ImportJobSummary {

    public int totalRows;
    public int succeeded;
    public int failed;

    public ImportJobSummary() {
        // Required by the BSON POJO codec.
    }

    public ImportJobSummary(int totalRows, int succeeded, int failed) {
        this.totalRows = totalRows;
        this.succeeded = succeeded;
        this.failed = failed;
    }

    /** A zero-valued summary, the starting point before any row has been processed. */
    public static ImportJobSummary empty() {
        return new ImportJobSummary(0, 0, 0);
    }
}
