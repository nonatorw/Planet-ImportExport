package com.planet.importexport.importjob;

/**
 * The {@code import_jobs.summary} nested object (design.md section 1.3):
 * row-outcome counters for a job, finalized once the job reaches a terminal
 * status (design.md section 3, step 6).
 *
 * <p>A no-args constructor and public mutable fields are used (rather than a
 * record) so the BSON POJO codec Quarkus MongoDB Panache configures by default
 * can decode this as a nested document without extra codec registration,
 * matching the plain-field style already used by sibling document classes in
 * this codebase (e.g. {@code CustomerRecordDocument}).
 */
public class ImportJobSummary {
    /**
     * Total number of rows read from the source file for this job.
     */
    public int totalRows;

    /**
     * Number of rows imported successfully.
     */
    public int succeeded;

    /**
     * Number of rows that failed to import.
     */
    public int failed;

    /**
     * No-args constructor required by the BSON POJO codec.
     */
    public ImportJobSummary() {
        // Required by the BSON POJO codec.
    }

    /**
     * Creates a summary with explicit counters.
     *
     * @param totalRows total number of rows read from the source file.
     * @param succeeded number of rows imported successfully.
     * @param failed    number of rows that failed to import.
     */
    public ImportJobSummary(int totalRows, int succeeded, int failed) {
        this.totalRows = totalRows;
        this.succeeded = succeeded;
        this.failed = failed;
    }

    /**
     * A zero-valued summary, the starting point before any row has been
     * processed.
     *
     * @return a new {@link ImportJobSummary} with every counter at zero.
     */
    public static ImportJobSummary empty() {
        return new ImportJobSummary(0, 0, 0);
    }
}
