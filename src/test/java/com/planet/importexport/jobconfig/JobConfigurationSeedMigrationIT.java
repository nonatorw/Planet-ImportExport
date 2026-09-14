package com.planet.importexport.jobconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.planet.importexport.mongo.FlapdoodleMongoTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests proving the startup seed migration (A4.2; design.md section 1.4) is idempotent
 * against the embedded Flapdoodle MongoDB instance: running it twice never duplicates the entry,
 * and never overwrites a value an operator has already changed (ADR-0007's implicit expectation
 * that write-time validation, not a migration re-run, is the single source of truth for the current
 * value).
 *
 * <p>{@link JobConfigurationSeedMigration#onStart} already ran once for real during this
 * {@code @QuarkusTest}'s own application startup (it is a CDI {@code @Observes StartupEvent}
 * bean), so the "already present" branch is exercised implicitly by every test in this class. Each
 * test additionally invokes {@link JobConfigurationSeedMigration#seedChunkSizeIfAbsent()} directly
 * to prove the specific idempotency property under test, without relying on triggering a second
 * real Quarkus startup.
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

    @Test
    void seedChunkSizeIfAbsent_seedsDefaultValue_whenAbsent() {
        repository.deleteAll();

        migration.seedChunkSizeIfAbsent();

        JobConfigurationEntry seeded =
                repository.findByKey(JobConfigurationSeedMigration.CHUNK_SIZE_KEY).orElseThrow();
        assertEquals(JobConfigurationSeedMigration.CHUNK_SIZE_DEFAULT_VALUE, seeded.value);
        assertEquals(JobConfigurationValueType.INTEGER, seeded.valueType);
    }

    @Test
    void seedChunkSizeIfAbsent_isIdempotent_doesNotDuplicateOnSecondRun() {
        repository.deleteAll();

        migration.seedChunkSizeIfAbsent();
        migration.seedChunkSizeIfAbsent();

        List<JobConfigurationEntry> all = repository.listAll();
        long chunkSizeEntries = all.stream()
                .filter(e -> JobConfigurationSeedMigration.CHUNK_SIZE_KEY.equals(e.key))
                .count();
        assertEquals(1, chunkSizeEntries);
    }

    @Test
    void seedChunkSizeIfAbsent_doesNotOverwrite_anAlreadyModifiedEntry() {
        repository.deleteAll();
        // Simulate an operator having already changed chunkSize via the (future) CRUD API before
        // this migration observer runs again on a subsequent application start.
        repository.insert(new JobConfigurationEntry(
                JobConfigurationSeedMigration.CHUNK_SIZE_KEY,
                "1000",
                JobConfigurationValueType.INTEGER,
                "operator-modified",
                Instant.now()));

        migration.seedChunkSizeIfAbsent();

        JobConfigurationEntry entry =
                repository.findByKey(JobConfigurationSeedMigration.CHUNK_SIZE_KEY).orElseThrow();
        assertEquals("1000", entry.value);
        assertEquals("operator-modified", entry.description);
    }
}
