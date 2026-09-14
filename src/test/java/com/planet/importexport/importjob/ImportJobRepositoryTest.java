package com.planet.importexport.importjob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.planet.importexport.mongo.FlapdoodleMongoTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for {@link ImportJobRepository} against the embedded Flapdoodle MongoDB
 * instance (ADR-0001), reusing the existing {@link FlapdoodleMongoTestResource} started by Group
 * A0.2 rather than a second test resource.
 *
 * <p>Exercises: persistence + retrieval of the exact document shape from design.md section 1.3
 * (including {@code idsInFile} and {@code summary}), the status-transition repository methods,
 * and the "find running/pending" queries the ADR-0003 serialization gate (Group B2) will later
 * depend on.
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

    @Test
    void persistsAndRetrievesJobByJobId() {
        String jobId = idGenerator.generate();
        Instant submittedAt = Instant.parse("2026-09-14T10:00:00Z");
        ImportJobDocument job =
                new ImportJobDocument(
                        jobId,
                        "/data/imports/customers_02.csv",
                        submittedAt,
                        List.of("1", "4"));

        repository.persist(job);

        Optional<ImportJobDocument> found = repository.findByJobId(jobId);
        assertTrue(found.isPresent());
        ImportJobDocument persisted = found.get();
        assertEquals(jobId, persisted.id);
        assertEquals("/data/imports/customers_02.csv", persisted.filePath);
        assertEquals(ImportJobStatus.PENDING, persisted.status);
        assertEquals(submittedAt, persisted.submittedAt);
        assertEquals(List.of("1", "4"), persisted.idsInFile);
        assertEquals(0, persisted.summary.totalRows);
        assertEquals(0, persisted.summary.succeeded);
        assertEquals(0, persisted.summary.failed);
    }

    @Test
    void findByJobIdReturnsEmptyForUnknownId() {
        assertTrue(repository.findByJobId("job-does-not-exist").isEmpty());
    }

    @Test
    void tracksStatusTransitionsThroughToCompletion() {
        String jobId = idGenerator.generate();
        repository.persist(
                new ImportJobDocument(
                        jobId, "/data/imports/customers_01.csv", Instant.now(), List.of("1")));

        Instant startedAt = Instant.parse("2026-09-14T10:00:01Z");
        repository.markRunning(jobId, startedAt);

        ImportJobDocument running = repository.findByJobId(jobId).orElseThrow();
        assertEquals(ImportJobStatus.RUNNING, running.status);
        assertEquals(startedAt, running.startedAt);

        Instant completedAt = Instant.parse("2026-09-14T10:00:05Z");
        ImportJobSummary summary = new ImportJobSummary(3, 2, 1);
        repository.markCompleted(jobId, completedAt, summary);

        ImportJobDocument completed = repository.findByJobId(jobId).orElseThrow();
        assertEquals(ImportJobStatus.COMPLETED, completed.status);
        assertEquals(completedAt, completed.completedAt);
        assertEquals(3, completed.summary.totalRows);
        assertEquals(2, completed.summary.succeeded);
        assertEquals(1, completed.summary.failed);
    }

    @Test
    void tracksStatusTransitionThroughToFailure() {
        String jobId = idGenerator.generate();
        repository.persist(
                new ImportJobDocument(
                        jobId, "/data/imports/customers_01.csv", Instant.now(), List.of("1")));
        repository.markRunning(jobId, Instant.now());

        repository.markFailed(jobId, Instant.now(), new ImportJobSummary(2, 0, 2));

        ImportJobDocument failed = repository.findByJobId(jobId).orElseThrow();
        assertEquals(ImportJobStatus.FAILED, failed.status);
        assertEquals(2, failed.summary.failed);
    }

    @Test
    void rejectsIllegalTransitionFromPendingToCompleted() {
        String jobId = idGenerator.generate();
        repository.persist(
                new ImportJobDocument(
                        jobId, "/data/imports/customers_01.csv", Instant.now(), List.of("1")));

        assertThrows(
                IllegalStateException.class,
                () -> repository.markCompleted(jobId, Instant.now(), ImportJobSummary.empty()));
    }

    @Test
    void findRunningReturnsOnlyRunningJobs() {
        String runningJobId = idGenerator.generate();
        String pendingJobId = idGenerator.generate();
        repository.persist(
                new ImportJobDocument(
                        runningJobId, "/data/imports/a.csv", Instant.now(), List.of("1")));
        repository.markRunning(runningJobId, Instant.now());
        repository.persist(
                new ImportJobDocument(
                        pendingJobId, "/data/imports/b.csv", Instant.now(), List.of("2")));

        List<ImportJobDocument> running = repository.findRunning();

        assertEquals(1, running.size());
        assertEquals(runningJobId, running.get(0).id);
    }

    @Test
    void findPendingOrderedByArrivalPreservesSubmissionOrder() {
        String firstJobId = idGenerator.generate();
        String secondJobId = idGenerator.generate();
        Instant firstSubmittedAt = Instant.parse("2026-09-14T10:00:00Z");
        Instant secondSubmittedAt = Instant.parse("2026-09-14T10:00:01Z");
        repository.persist(
                new ImportJobDocument(
                        secondJobId, "/data/imports/b.csv", secondSubmittedAt, List.of("1")));
        repository.persist(
                new ImportJobDocument(
                        firstJobId, "/data/imports/a.csv", firstSubmittedAt, List.of("1")));

        List<ImportJobDocument> pending = repository.findPendingOrderedByArrival();

        assertEquals(2, pending.size());
        assertEquals(firstJobId, pending.get(0).id);
        assertEquals(secondJobId, pending.get(1).id);
    }
}
