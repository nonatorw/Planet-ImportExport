package com.planet.importexport.importjob;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import com.planet.importexport.mongo.FlapdoodleMongoTestResource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test for {@link ImportJobRepository} against the embedded
 * Flapdoodle MongoDB instance (ADR-0001), reusing the existing
 * {@link FlapdoodleMongoTestResource} started by Group A0.2 rather than a
 * second test resource.
 *
 * <p>Exercises: persistence + retrieval of the exact document shape from
 * design.md section 1.3 (including {@code idsInFile} and {@code summary}), the
 * status-transition repository methods, and the "find running/pending" queries
 * the ADR-0003 serialization gate (Group B2) will later depend on.</p>
 */
@QuarkusTest
@QuarkusTestResource(FlapdoodleMongoTestResource.class)
class ImportJobRepositoryTest {
    @Inject ImportJobRepository repository;

    private final ImportJobIdGenerator idGenerator = new ImportJobIdGenerator();

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    /**
     * A newly persisted job can be retrieved by its job id with every field
     * (file path, {@code PENDING} status, submission time, ids in file, and
     * a zeroed summary) exactly as given.
     */
    @Test
    void persistsAndRetrievesJobByJobId() {
        String jobId = idGenerator.generate();
        Instant submittedAt = Instant.parse("2026-09-14T10:00:00Z");
        ImportJobDocument job =
                new ImportJobDocument(jobId,
                                      "/data/imports/customers_02.csv",
                                      submittedAt,
                                      List.of("1", "4"));

        repository.persist(job);

        Optional<ImportJobDocument> found = repository.findByJobId(jobId);

        Assertions.assertTrue(found.isPresent());

        ImportJobDocument persisted = found.get();

        Assertions.assertEquals(jobId, persisted.id);

        Assertions.assertEquals("/data/imports/customers_02.csv",
                                persisted.filePath);

        Assertions.assertEquals(ImportJobStatus.PENDING,
                                persisted.status);

        Assertions.assertEquals(submittedAt,
                                persisted.submittedAt);

        Assertions.assertEquals(List.of("1", "4"),
                                persisted.idsInFile);

        Assertions.assertEquals(0,
                                persisted.summary.totalRows);

        Assertions.assertEquals(0,
                                persisted.summary.succeeded);

        Assertions.assertEquals(0,
                                persisted.summary.failed);
    }

    /**
     * Looking up a job id that was never persisted returns an empty result
     * rather than throwing.
     */
    @Test
    void findByJobIdReturnsEmptyForUnknownId() {
        Assertions.assertTrue(repository.findByJobId("job-does-not-exist")
                                        .isEmpty());
    }

    /**
     * A job moves from {@code PENDING} to {@code RUNNING} (recording its
     * start time) and then to {@code COMPLETED} (recording its completion
     * time and final summary), with each transition persisted correctly.
     */
    @Test
    void tracksStatusTransitionsThroughToCompletion() {
        String jobId = idGenerator.generate();
        repository.persist(
                new ImportJobDocument(jobId,
                                      "/data/imports/customers_01.csv",
                                      Instant.now(),
                                      List.of("1")));

        Instant startedAt =
                Instant.parse("2026-09-14T10:00:01Z");

        repository.markRunning(jobId, startedAt);

        ImportJobDocument running =
                repository.findByJobId(jobId).orElseThrow();

        Assertions.assertEquals(ImportJobStatus.RUNNING,
                                running.status);

        Assertions.assertEquals(startedAt,
                                running.startedAt);

        Instant completedAt =
                Instant.parse("2026-09-14T10:00:05Z");

        ImportJobSummary summary = new ImportJobSummary(3,
                                                        2,
                                                        1);

        repository.markCompleted(jobId, completedAt, summary);

        ImportJobDocument completed =
                repository.findByJobId(jobId)
                          .orElseThrow();

        Assertions.assertEquals(ImportJobStatus.COMPLETED,
                                completed.status);

        Assertions.assertEquals(completedAt,
                                completed.completedAt);

        Assertions.assertEquals(3,
                                completed.summary.totalRows);

        Assertions.assertEquals(2,
                                completed.summary.succeeded);

        Assertions.assertEquals(1,
                                completed.summary.failed);
    }

    /**
     * A job moves from {@code PENDING} to {@code RUNNING} and then to
     * {@code FAILED}, with the failure summary's failed-row count persisted
     * correctly.
     */
    @Test
    void tracksStatusTransitionThroughToFailure() {
        String jobId = idGenerator.generate();
        repository.persist(
                new ImportJobDocument(jobId,
                                      "/data/imports/customers_01.csv",
                                      Instant.now(),
                                      List.of("1")));

        repository.markRunning(jobId,
                               Instant.now());

        repository.markFailed(jobId,
                              Instant.now(),
                              new ImportJobSummary(2,
                                                   0,
                                                   2));

        ImportJobDocument failed = repository.findByJobId(jobId)
                                             .orElseThrow();

        Assertions.assertEquals(ImportJobStatus.FAILED,
                                failed.status);

        Assertions.assertEquals(2,
                                failed.summary.failed);
    }

    /**
     * Marking a still-{@code PENDING} job as completed directly, skipping
     * the {@code RUNNING} state, throws {@link IllegalStateException}.
     */
    @Test
    void rejectsIllegalTransitionFromPendingToCompleted() {
        String jobId = idGenerator.generate();

        repository.persist(
                new ImportJobDocument(jobId,
                                      "/data/imports/customers_01.csv",
                                      Instant.now(),
                                      List.of("1")));

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> repository.markCompleted(jobId,
                                               Instant.now(),
                                               ImportJobSummary.empty()));
    }

    /**
     * {@code findRunning} returns only the job that was marked
     * {@code RUNNING}, excluding a second job left in {@code PENDING}.
     */
    @Test
    void findRunningReturnsOnlyRunningJobs() {
        String runningJobId = idGenerator.generate();
        String pendingJobId = idGenerator.generate();

        repository.persist(new ImportJobDocument(runningJobId,
                                                 "/data/imports/a.csv",
                                                 Instant.now(),
                                                 List.of("1")));

        repository.markRunning(runningJobId,
                               Instant.now());

        repository.persist(new ImportJobDocument(pendingJobId,
                                                 "/data/imports/b.csv",
                                                 Instant.now(),
                                                 List.of("2")));

        List<ImportJobDocument> running = repository.findRunning();

        Assertions.assertEquals(1,
                                running.size());

        Assertions.assertEquals(runningJobId,
                                running.get(0).id);
    }

    /**
     * {@code findPendingOrderedByArrival} returns pending jobs ordered by
     * their submission time, not by the order in which they were persisted.
     */
    @Test
    void findPendingOrderedByArrivalPreservesSubmissionOrder() {
        String firstJobId = idGenerator.generate();
        String secondJobId = idGenerator.generate();
        Instant firstSubmittedAt = Instant.parse("2026-09-14T10:00:00Z");
        Instant secondSubmittedAt = Instant.parse("2026-09-14T10:00:01Z");

        repository.persist(new ImportJobDocument(secondJobId,
                                                 "/data/imports/b.csv",
                                                 secondSubmittedAt,
                                                 List.of("1")));

        repository.persist(new ImportJobDocument(firstJobId,
                                                 "/data/imports/a.csv",
                                                 firstSubmittedAt,
                                                 List.of("1")));

        List<ImportJobDocument> pending =
                repository.findPendingOrderedByArrival();

        Assertions.assertEquals(2,
                                pending.size());

        Assertions.assertEquals(firstJobId,
                                pending.get(0).id);

        Assertions.assertEquals(secondJobId,
                                pending.get(1).id);
    }
}
