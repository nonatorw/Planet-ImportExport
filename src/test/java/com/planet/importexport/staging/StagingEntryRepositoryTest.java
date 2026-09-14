package com.planet.importexport.staging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.planet.importexport.mongo.FlapdoodleMongoTestResource;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

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

        assertEquals(2, forJob.size());

        Set<Integer> rowIds = Set.of(forJob.get(0).rowId,
                                     forJob.get(1).rowId);

        assertEquals(Set.of(5, 7), rowIds);

        assertTrue(forJob.stream()
                         .anyMatch(entry -> entry.errorDescription
                                                 .contains("age")));
        assertTrue(forJob.stream()
                         .anyMatch(entry -> entry.errorDescription
                                                 .contains("loyalty_tier")));
        assertTrue(forJob.stream()
                         .allMatch(entry -> entry.id != null));

        List<StagingEntry> forOtherJob =
                repository.findByJobId("job-other456");

        assertEquals(1, forOtherJob.size());

        assertTrue(forOtherJob.get(0)
                              .errorDescription.contains("email"));
    }

    @Test
    void findByJobIdReturnsEmptyListWhenNoEntriesExist() {
        List<StagingEntry> result =
                repository.findByJobId("job-does-not-exist");

        assertTrue(result.isEmpty());
    }

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

        assertNotNull(indexes);

        assertTrue(hasJobIdIndex,
                   "Expected an index on 'jobId' on staging_entries collection");
    }
}
