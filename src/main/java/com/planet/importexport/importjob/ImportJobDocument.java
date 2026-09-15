package com.planet.importexport.importjob;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * The {@code import_jobs} document (design.md section 1.3; ADR-0002; ADR-0003).
 *
 * <p>Unlike {@code customer_records} (whose primary key is an
 * {@link org.bson.types.ObjectId}), this document's {@code _id}
 * <strong>is</strong> the externally-exposed {@code jobId} string (e.g.
 * {@code "job-abc123"}, design.md section 1.3) — there is no separate
 * business-key field.</p>
 *
 * <p>See {@link ImportJobIdGenerator} for how that string is produced.</p>
 *
 * <p>{@code idsInFile} is populated by an initial lightweight pass over the
 * {@code id} column before chunked processing starts (design.md section 1.3)
 * and is later read by the ADR-0003 per-job id-intersection serialization gate
 * (Group B2, not implemented here) to decide whether a newly submitted job
 * must wait on any currently running/queued-ahead job. This document model
 * and repository only need to expose that field and a query for in-flight jobs
 * — the gate's waiting logic itself is out of this task's scope.</p>
 *
 * <p>{@code summary} starts as {@link ImportJobSummary#empty()} and is
 * finalized once the job reaches {@link ImportJobStatus#COMPLETED} or
 * {@link ImportJobStatus#FAILED} (design.md section 3, step 6).</p>
 */
@MongoEntity(collection = "import_jobs")
public class ImportJobDocument {
    /**
     * The jobId itself, e.g. {@code "job-abc123"} — not an
     * {@link org.bson.types.ObjectId}.
     */
    public String id;

    /**
     * Absolute or resolvable path to the source file being imported
     * (design.md section 1.3).
     */
    public String filePath;

    /**
     * Current point in the {@link ImportJobStatus} lifecycle. Transition
     * legality is validated by {@link ImportJobRepository}, delegating to
     * {@link ImportJobStatus#canTransitionTo(ImportJobStatus)}.
     */
    public ImportJobStatus status;

    /**
     * When the job was accepted and persisted as
     * {@link ImportJobStatus#PENDING}, per design.md section 3 step 1.
     * Also the ordering key used by
     * {@link ImportJobRepository#findPendingOrderedByArrival()}.
     */
    public Instant submittedAt;

    /**
     * Null until the job leaves {@link ImportJobStatus#PENDING}.
     */
    public Instant startedAt;

    /**
     * Null until the job reaches a terminal status.
     */
    public Instant completedAt;

    /**
     * The id-set computed up front from the source file's {@code id} column,
     * used by the ADR-0003 serialization gate. Empty (never null) until that
     * initial pass populates it.
     */
    public List<String> idsInFile = new ArrayList<>();

    /**
     * Row-outcome counters for this job. Starts as
     * {@link ImportJobSummary#empty()} and is finalized once the job reaches
     * {@link ImportJobStatus#COMPLETED} or {@link ImportJobStatus#FAILED}
     * (design.md section 3, step 6).
     */
    public ImportJobSummary summary = ImportJobSummary.empty();

    /**
     * No-args constructor required by the MongoDB POJO codec.
     */
    public ImportJobDocument() {
        // Required by the MongoDB POJO codec.
    }

    /**
     * Creates a new job record in {@link ImportJobStatus#PENDING}, per
     * design.md section 3 step 1 ("persists an {@code import_jobs} document
     * with {@code status = PENDING}"). {@code idsInFile} is supplied here
     * since design.md step 1 populates it before the job is even submitted to
     * the executor, i.e. before {@link ImportJobStatus#RUNNING}.
     *
     * @param id           the externally-exposed job id, used as {@code _id}.
     * @param filePath     path to the source file being imported.
     * @param submittedAt  when the job was accepted.
     * @param idsInFile    the id-set computed up front from the source file's
     *                     {@code id} column; defensively copied.
     */
    public ImportJobDocument(String id,
                             String filePath,
                             Instant submittedAt,
                             List<String> idsInFile) {
        this.id = id;
        this.filePath = filePath;
        this.status = ImportJobStatus.PENDING;
        this.submittedAt = submittedAt;
        this.idsInFile = new ArrayList<>(idsInFile);
        this.summary = ImportJobSummary.empty();
    }
}
