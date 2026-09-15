package com.planet.importexport.jobconfig;

import java.time.Instant;
import java.util.List;

import jakarta.inject.Inject;

import com.planet.importexport.mongo.FlapdoodleMongoTestResource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests proving the startup seed migration (A4.2; design.md
 * section 1.4) is idempotent against the embedded Flapdoodle MongoDB instance:
 * running it twice never duplicates the entry, and never overwrites a value an
 * operator has already changed (ADR-0007's implicit expectation that write-time
 * validation, not a migration re-run, is the single source of truth for the
 * current value).
 *
 * <p>{@link JobConfigurationSeedMigration#onStart} already ran once for real
 * during this {@code @QuarkusTest}'s own application startup (it is a CDI
 * {@code @Observes StartupEvent} bean), so the "already present" branch is
 * exercised implicitly by every test in this class. Each test additionally
 * invokes {@link JobConfigurationSeedMigration#seedChunkSizeIfAbsent()}
 * directly to prove the specific idempotency property under test, without
 * relying on triggering a second real Quarkus startup.</p>
 */
@QuarkusTest
@QuarkusTestResource(FlapdoodleMongoTestResource.class)
class JobConfigurationSeedMigrationIT {
    @Inject
    JobConfigurationRepository repository;

    @Inject
    JobConfigurationSeedMigration migration;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    /**
     * Running the seed migration against an empty collection creates the
     * {@code chunkSize} entry with its default value and {@code INTEGER}
     * valueType.
     */
    @Test
    void seedChunkSizeIfAbsent_seedsDefaultValue_whenAbsent() {
        repository.deleteAll();

        migration.seedChunkSizeIfAbsent();

        JobConfigurationEntry seeded =
                repository.findByKey(JobConfigurationSeedMigration.CHUNK_SIZE_KEY)
                          .orElseThrow();

        Assertions.assertEquals(JobConfigurationSeedMigration.CHUNK_SIZE_DEFAULT_VALUE,
                                seeded.value);

        Assertions.assertEquals(JobConfigurationValueType.INTEGER,
                                seeded.valueType);
    }

    /**
     * Running the seed migration twice in a row still leaves exactly one
     * {@code chunkSize} entry in the collection, never a duplicate.
     */
    @Test
    void seedChunkSizeIfAbsent_isIdempotent_doesNotDuplicateOnSecondRun() {
        repository.deleteAll();

        migration.seedChunkSizeIfAbsent();
        migration.seedChunkSizeIfAbsent();

        List<JobConfigurationEntry> all =
                repository.listAll();

        long chunkSizeEntries =
                all.stream()
                   .filter(e -> JobConfigurationSeedMigration.CHUNK_SIZE_KEY
                                                             .equals(e.key))
                   .count();

        Assertions.assertEquals(1,
                                chunkSizeEntries);
    }

    /**
     * Running the seed migration when {@code chunkSize} already exists with
     * an operator-modified value leaves that value and description
     * untouched, rather than resetting them back to the default.
     */
    @Test
    void seedChunkSizeIfAbsent_doesNotOverwrite_anAlreadyModifiedEntry() {
        repository.deleteAll();
        /*
         * Simulate an operator having already changed chunkSize via the
         * (future) CRUD API before this migration observer runs again on a
         * subsequent application start.
         */
        repository.insert(
                new JobConfigurationEntry(JobConfigurationSeedMigration.CHUNK_SIZE_KEY,
                                          "1000",
                                          JobConfigurationValueType.INTEGER,
                                          "operator-modified",
                                          Instant.now()));

        migration.seedChunkSizeIfAbsent();

        JobConfigurationEntry entry =
                repository.findByKey(JobConfigurationSeedMigration.CHUNK_SIZE_KEY)
                          .orElseThrow();

        Assertions.assertEquals("1000",
                                entry.value);

        Assertions.assertEquals("operator-modified",
                                entry.description);
    }
}
