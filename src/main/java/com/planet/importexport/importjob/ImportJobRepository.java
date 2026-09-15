package com.planet.importexport.importjob;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import io.quarkus.panache.common.Sort;

/**
 * Repository for the {@code import_jobs} collection (design.md section 1.3;
 * ADR-0002; ADR-0003).
 *
 * <p>Implements {@link PanacheMongoRepositoryBase} rather than
 * {@link io.quarkus.mongodb.panache.PanacheMongoRepository} because
 * {@link ImportJobDocument#id} is a {@link String} (the {@code jobId} itself,
 * e.g. {@code "job-abc123"}), not an {@link org.bson.types.ObjectId} —
 * {@code PanacheMongoRepositoryBase<Entity, Id>} is the Quarkus MongoDB
 * Panache extension point for a custom id type (see
 * {@code @415-frameworks-quarkus-mongodb}).
 */
@ApplicationScoped
public class ImportJobRepository
        implements PanacheMongoRepositoryBase<ImportJobDocument, String> {

    /**
     * Convenience alias over Panache's inherited {@code findByIdOptional},
     * by {@code jobId}.
     *
     * @param jobId the externally-exposed job id (the document's {@code _id}).
     * @return the matching job, or {@link Optional#empty()} if none exists.
     */
    public Optional<ImportJobDocument> findByJobId(String jobId) {
        return findByIdOptional(jobId);
    }

    /**
     * Jobs currently {@link ImportJobStatus#RUNNING}, needed by the ADR-0003
     * id-intersection serialization gate (Group B2) to check a newly submitted
     * job's {@code idsInFile} against every in-flight job's {@code idsInFile}
     * before letting it start. This repository exposes the query;
     * the gate's wait/release logic is Group B2's responsibility, not this
     * task's.
     *
     * @return every job currently {@link ImportJobStatus#RUNNING}.
     */
    public List<ImportJobDocument> findRunning() {
        return find("status", ImportJobStatus.RUNNING).list();
    }

    /**
     * Jobs still {@link ImportJobStatus#PENDING}, ordered by
     * {@code submittedAt} ascending (arrival order). ADR-0003 requires
     * "queued-ahead" jobs to also be considered by the serialization gate,
     * and arrival order must be preserved among mutually-intersecting jobs
     * (design.md section 3 step 3) — this method exposes both the queued-ahead
     * set and a stable arrival ordering for Group B2 to consume.
     *
     * @return every job currently {@link ImportJobStatus#PENDING}, ordered by
     *         {@code submittedAt} ascending.
     */
    public List<ImportJobDocument> findPendingOrderedByArrival() {
        return find("status",
                    Sort.ascending("submittedAt"),
                    ImportJobStatus.PENDING).list();
    }

    /**
     * Moves a job to {@link ImportJobStatus#RUNNING} and stamps
     * {@code startedAt}, per design.md section 3 step 4 ("Once cleared to run,
     * the task sets {@code status = RUNNING}, {@code startedAt = now}").
     *
     * @param jobId     the job to transition.
     * @param startedAt the instant to stamp as {@code startedAt}.
     * @return the updated job document.
     * @throws IllegalArgumentException if no job exists for {@code jobId}.
     * @throws IllegalStateException if the job is not currently
     *                               {@link ImportJobStatus#PENDING} —
     *                               {@link ImportJobStatus#canTransitionTo(ImportJobStatus)}
     *                               is the single source of truth for legal
     *                               moves.
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
     * Moves a job to {@link ImportJobStatus#COMPLETED}, stamps
     * {@code completedAt}, and finalizes {@code summary}, per design.md
     * section 3 step 6.
     *
     * @param jobId       the job to transition.
     * @param completedAt the instant to stamp as {@code completedAt}.
     * @param summary     the final row-outcome counters for the job.
     * @return the updated job document.
     * @throws IllegalArgumentException if no job exists for {@code jobId}.
     * @throws IllegalStateException if the job is not currently
     *                               {@link ImportJobStatus#RUNNING}.
     */
    public ImportJobDocument markCompleted(String jobId,
                                           Instant completedAt,
                                           ImportJobSummary summary) {
        ImportJobDocument job = requireJob(jobId);
        requireTransition(job, ImportJobStatus.COMPLETED);
        job.status = ImportJobStatus.COMPLETED;
        job.completedAt = completedAt;
        job.summary = summary;
        update(job);

        return job;
    }

    /**
     * Moves a job to {@link ImportJobStatus#FAILED}, stamps
     * {@code completedAt}, and records {@code summary} (the partial counters
     * known at the point of failure).
     *
     * @param jobId       the job to transition.
     * @param completedAt the instant to stamp as {@code completedAt}.
     * @param summary     the partial row-outcome counters known at the point
     *                    of failure.
     * @return the updated job document.
     * @throws IllegalArgumentException if no job exists for {@code jobId}.
     * @throws IllegalStateException if the job is not currently
     *                               {@link ImportJobStatus#RUNNING}.
     */
    public ImportJobDocument markFailed(String jobId,
                                        Instant completedAt,
                                        ImportJobSummary summary) {
        ImportJobDocument job = requireJob(jobId);
        requireTransition(job, ImportJobStatus.FAILED);
        job.status = ImportJobStatus.FAILED;
        job.completedAt = completedAt;
        job.summary = summary;
        update(job);

        return job;
    }

    /**
     * Looks up a job by {@code jobId} or throws if it does not exist.
     *
     * @param jobId the job id to look up.
     * @return the matching job document.
     * @throws IllegalArgumentException if no job exists for {@code jobId}.
     */
    private ImportJobDocument requireJob(String jobId) {
        Supplier<IllegalArgumentException> exceptionSupplier =
                () -> new IllegalArgumentException("No import job found for jobId: " +
                                                   jobId);
        return findByJobId(jobId).orElseThrow(exceptionSupplier);
    }

    /**
     * Guards a status transition, delegating legality to
     * {@link ImportJobStatus#canTransitionTo(ImportJobStatus)}.
     *
     * @param job    the job whose current status is being validated.
     * @param target the status the job is about to move to.
     * @throws IllegalStateException if {@code job.status} cannot legally
     *                                move to {@code target}.
     */
    private void requireTransition(ImportJobDocument job,
                                   ImportJobStatus target) {
        if (!job.status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Illegal import job status transition for " + job.id +
                    ": " + job.status +
                    " -> " + target);
        }
    }
}
