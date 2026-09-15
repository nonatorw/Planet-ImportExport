package com.planet.importexport.staging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

import com.planet.importexport.mongo.FlapdoodleMongoTestResource;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test proving {@link StagingEntryRepository} persists
 * {@link StagingEntry} documents into the {@code staging_entries} collection
 * and can fetch them back by {@code jobId} (ADR-0005;
 * design.md section 1.2 — the query the job-status endpoint will rely on).
 * Reuses the project-wide embedded MongoDB test resource; does not stand up a
 * second Mongo instance.
 */
@QuarkusTest
@QuarkusTestResource(FlapdoodleMongoTestResource.class)
class StagingEntryRepositoryTest {
    @Inject
    StagingEntryRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    /**
     * Persisting staging entries for two different jobs and then fetching by
     * {@code jobId} returns only the entries for that job, each keeping its
     * row data, error description, and assigned id, without picking up
     * entries staged under a different job id.
     */
    @Test
    void persistsAndFindsByJobId() {
        StagingEntry invalidAge = new StagingEntry();
        invalidAge.jobId = "job-abc123";
        invalidAge.rowId = 5;

        invalidAge.rowData =
                Map.of("id", "5",
                       "name", "Marco Rossi",
                       "age", "thirty");

        invalidAge.errorDescription =
                "invalid age value: 'thirty' is not an integer in range 0-120";

        invalidAge.processedAt =
                Instant.parse("2026-09-14T10:00:03Z");

        StagingEntry unknownColumn = new StagingEntry();
        unknownColumn.jobId = "job-abc123";
        unknownColumn.rowId = 7;

        unknownColumn.rowData =
                Map.of("id", "7",
                       "name", "Ana",
                       "loyalty_tier", "gold");

        unknownColumn.errorDescription =
                "unknown column: 'loyalty_tier'";

        unknownColumn.processedAt =
                Instant.parse("2026-09-14T10:00:04Z");

        StagingEntry otherJob = new StagingEntry();
        otherJob.jobId = "job-other456";
        otherJob.rowId = 1;

        otherJob.rowData =
                Map.of("id", "1",
                       "email", "marco@example");

        otherJob.errorDescription =
                "invalid email value: 'marco@example' is missing " +
                "a top-level domain";

        otherJob.processedAt =
                Instant.parse("2026-09-14T11:00:00Z");

        repository.persist(invalidAge,
                           unknownColumn,
                           otherJob);

        List<StagingEntry> forJob =
                repository.findByJobId("job-abc123");

        Assertions.assertEquals(2,
                                forJob.size());

        Set<Integer> rowIds = Set.of(forJob.get(0).rowId,
                                     forJob.get(1).rowId);

        Assertions.assertEquals(Set.of(5, 7),
                                rowIds);

        Assertions.assertTrue(forJob.stream()
                                    .anyMatch(entry ->
                                        entry.errorDescription
                                             .contains("age")));
        Assertions.assertTrue(forJob.stream()
                                    .anyMatch(entry ->
                                        entry.errorDescription
                                             .contains("loyalty_tier")));
        Assertions.assertTrue(forJob.stream()
                                    .allMatch(entry -> entry.id != null));

        List<StagingEntry> forOtherJob =
                repository.findByJobId("job-other456");

        Assertions.assertEquals(1, forOtherJob.size());

        Assertions.assertTrue(forOtherJob.get(0)
                                         .errorDescription.contains("email"));
    }

    /**
     * Looking up a job id with no staged entries returns an empty list
     * rather than throwing or returning {@code null}.
     */
    @Test
    void findByJobIdReturnsEmptyListWhenNoEntriesExist() {
        List<StagingEntry> result =
                repository.findByJobId("job-does-not-exist");

        Assertions.assertTrue(result.isEmpty());
    }

    /**
     * The {@code staging_entries} collection has an index defined on the
     * {@code jobId} field, backing the job-status lookup query.
     */
    @Test
    void jobIdIndexExistsOnCollection() {
        List<Document> indexes =
                repository.mongoCollection()
                          .listIndexes()
                          .into(new ArrayList<>());

        boolean hasJobIdIndex =
                indexes.stream()
                       .map(index -> index.get("key",
                                               Document.class))
                       .anyMatch(key -> key.containsKey("jobId"));

        Assertions.assertNotNull(indexes);

        Assertions.assertTrue(hasJobIdIndex,
                              "Expected an index on 'jobId' on staging_entries collection");
    }
}
