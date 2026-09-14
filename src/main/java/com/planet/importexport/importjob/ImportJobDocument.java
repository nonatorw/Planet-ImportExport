package com.planet.importexport.importjob;

import io.quarkus.mongodb.panache.common.MongoEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code import_jobs} document (design.md section 1.3; ADR-0002; ADR-0003).
 *
 * <p>Unlike {@code customer_records} (whose primary key is an {@link org.bson.types.ObjectId}),
 * this document's {@code _id} <strong>is</strong> the externally-exposed {@code jobId} string
 * (e.g. {@code "job-abc123"}, design.md section 1.3) — there is no separate business-key field.
 * See {@link ImportJobIdGenerator} for how that string is produced.
 *
 * <p>{@code idsInFile} is populated by an initial lightweight pass over the {@code id} column
 * before chunked processing starts (design.md section 1.3) and is later read by the ADR-0003
 * per-job id-intersection serialization gate (Group B2, not implemented here) to decide whether a
 * newly submitted job must wait on any currently running/queued-ahead job. This document model and
 * repository only need to expose that field and a query for in-flight jobs — the gate's waiting
 * logic itself is out of this task's scope.
 *
 * <p>{@code summary} starts as {@link ImportJobSummary#empty()} and is finalized once the job
 * reaches {@link ImportJobStatus#COMPLETED} or {@link ImportJobStatus#FAILED} (design.md section 3,
 * step 6).
 */
@MongoEntity(collection = "import_jobs")
public class ImportJobDocument {

    /** The jobId itself, e.g. {@code "job-abc123"} — not an {@link org.bson.types.ObjectId}. */
    public String id;

    public String filePath;

    public ImportJobStatus status;

    public Instant submittedAt;

    /** Null until the job leaves {@link ImportJobStatus#PENDING}. */
    public Instant startedAt;

    /** Null until the job reaches a terminal status. */
    public Instant completedAt;

    /**
     * The id-set computed up front from the source file's {@code id} column, used by the ADR-0003
     * serialization gate. Empty (never null) until that initial pass populates it.
     */
    public List<String> idsInFile = new ArrayList<>();

    public ImportJobSummary summary = ImportJobSummary.empty();

    public ImportJobDocument() {
        // Required by the MongoDB POJO codec.
    }

    /**
     * Creates a new job record in {@link ImportJobStatus#PENDING}, per design.md section 3 step 1
     * ("persists an {@code import_jobs} document with {@code status = PENDING}"). {@code idsInFile}
     * is supplied here since design.md step 1 populates it before the job is even submitted to the
     * executor, i.e. before {@link ImportJobStatus#RUNNING}.
     */
    public ImportJobDocument(String id, String filePath, Instant submittedAt, List<String> idsInFile) {
        this.id = id;
        this.filePath = filePath;
        this.status = ImportJobStatus.PENDING;
        this.submittedAt = submittedAt;
        this.idsInFile = new ArrayList<>(idsInFile);
        this.summary = ImportJobSummary.empty();
    }
}
