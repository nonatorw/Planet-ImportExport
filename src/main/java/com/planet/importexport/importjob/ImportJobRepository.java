package com.planet.importexport.importjob;

import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@code import_jobs} collection (design.md section 1.3; ADR-0002; ADR-0003).
 *
 * <p>Implements {@link PanacheMongoRepositoryBase} rather than {@link
 * io.quarkus.mongodb.panache.PanacheMongoRepository} because {@link ImportJobDocument#id} is a
 * {@link String} (the {@code jobId} itself, e.g. {@code "job-abc123"}), not an {@link
 * org.bson.types.ObjectId} — {@code PanacheMongoRepositoryBase<Entity, Id>} is the Quarkus
 * MongoDB Panache extension point for a custom id type (see {@code
 * @415-frameworks-quarkus-mongodb}).
 */
@ApplicationScoped
public class ImportJobRepository
        implements PanacheMongoRepositoryBase<ImportJobDocument, String> {

    /** Convenience alias over Panache's inherited {@code findByIdOptional}, by {@code jobId}. */
    public Optional<ImportJobDocument> findByJobId(String jobId) {
        return findByIdOptional(jobId);
    }

    /**
     * Jobs currently {@link ImportJobStatus#RUNNING}, needed by the ADR-0003 id-intersection
     * serialization gate (Group B2) to check a newly submitted job's {@code idsInFile} against
     * every in-flight job's {@code idsInFile} before letting it start. This repository exposes the
     * query; the gate's wait/release logic is Group B2's responsibility, not this task's.
     */
    public List<ImportJobDocument> findRunning() {
        return find("status", ImportJobStatus.RUNNING).list();
    }

    /**
     * Jobs still {@link ImportJobStatus#PENDING}, ordered by {@code submittedAt} ascending
     * (arrival order). ADR-0003 requires "queued-ahead" jobs to also be considered by the
     * serialization gate, and arrival order must be preserved among mutually-intersecting jobs
     * (design.md section 3 step 3) — this method exposes both the queued-ahead set and a stable
     * arrival ordering for Group B2 to consume.
     */
    public List<ImportJobDocument> findPendingOrderedByArrival() {
        return find("status", Sort.ascending("submittedAt"), ImportJobStatus.PENDING).list();
    }

    /**
     * Moves a job to {@link ImportJobStatus#RUNNING} and stamps {@code startedAt}, per design.md
     * section 3 step 4 ("Once cleared to run, the task sets {@code status = RUNNING}, {@code
     * startedAt = now}").
     *
     * @throws IllegalStateException if the job is not currently {@link ImportJobStatus#PENDING} —
     *     {@link ImportJobStatus#canTransitionTo(ImportJobStatus)} is the single source of truth
     *     for legal moves.
     */
    public ImportJobDocument markRunning(String jobId, Instant startedAt) {
        ImportJobDocument job = requireJob(jobId);
        requireTransition(job, ImportJobStatus.RUNNING);
        job.status = ImportJobStatus.RUNNING;
        job.startedAt = startedAt;
        update(job);
        return job;
    }

    /**
     * Moves a job to {@link ImportJobStatus#COMPLETED}, stamps {@code completedAt}, and finalizes
     * {@code summary}, per design.md section 3 step 6.
     *
     * @throws IllegalStateException if the job is not currently {@link ImportJobStatus#RUNNING}.
     */
    public ImportJobDocument markCompleted(
            String jobId, Instant completedAt, ImportJobSummary summary) {
        ImportJobDocument job = requireJob(jobId);
        requireTransition(job, ImportJobStatus.COMPLETED);
        job.status = ImportJobStatus.COMPLETED;
        job.completedAt = completedAt;
        job.summary = summary;
        update(job);
        return job;
    }

    /**
     * Moves a job to {@link ImportJobStatus#FAILED}, stamps {@code completedAt}, and records
     * {@code summary} (the partial counters known at the point of failure).
     *
     * @throws IllegalStateException if the job is not currently {@link ImportJobStatus#RUNNING}.
     */
    public ImportJobDocument markFailed(
            String jobId, Instant completedAt, ImportJobSummary summary) {
        ImportJobDocument job = requireJob(jobId);
        requireTransition(job, ImportJobStatus.FAILED);
        job.status = ImportJobStatus.FAILED;
        job.completedAt = completedAt;
        job.summary = summary;
        update(job);
        return job;
    }

    private ImportJobDocument requireJob(String jobId) {
        return findByJobId(jobId)
                .orElseThrow(
                        () -> new IllegalArgumentException("No import job found for jobId: " + jobId));
    }

    private void requireTransition(ImportJobDocument job, ImportJobStatus target) {
        if (!job.status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Illegal import job status transition for "
                            + job.id
                            + ": "
                            + job.status
                            + " -> "
                            + target);
        }
    }
}
