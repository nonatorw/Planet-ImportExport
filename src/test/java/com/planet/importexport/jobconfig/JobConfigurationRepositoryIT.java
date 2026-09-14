package com.planet.importexport.jobconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.planet.importexport.mongo.FlapdoodleMongoTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link JobConfigurationRepository} against the embedded Flapdoodle MongoDB
 * instance (ADR-0001), proving real persistence — not mocked repository behaviour, per
 * {@code @415-frameworks-quarkus-mongodb} ("never mock PanacheMongoRepository inside a persistence
 * test").
 */
@QuarkusTest
@QuarkusTestResource(FlapdoodleMongoTestResource.class)
class JobConfigurationRepositoryIT {

    @Inject
    JobConfigurationRepository repository;

    // The Quarkus test context (including JobConfigurationSeedMigration's @Observes StartupEvent,
    // which seeds "chunkSize" on first boot) is shared across the whole suite, not per test method.
    // Without this @BeforeEach, whichever test runs first after startup would collide with that
    // seeded entry (JUnit5 does not guarantee method execution order by default).
    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    void insertAndFindByKey_roundTripsAllFields() {
        JobConfigurationEntry entry = new JobConfigurationEntry(
                "chunkSize", "500", JobConfigurationValueType.INTEGER, "batch size", Instant.now());

        repository.insert(entry);

        Optional<JobConfigurationEntry> found = repository.findByKey("chunkSize");
        assertTrue(found.isPresent());
        assertEquals("chunkSize", found.get().key);
        assertEquals("500", found.get().value);
        assertEquals(JobConfigurationValueType.INTEGER, found.get().valueType);
        assertEquals("batch size", found.get().description);
    }

    @Test
    void findByKey_returnsEmpty_whenAbsent() {
        assertTrue(repository.findByKey("doesNotExist").isEmpty());
    }

    @Test
    void listAll_returnsEveryEntry() {
        repository.insert(new JobConfigurationEntry(
                "chunkSize", "500", JobConfigurationValueType.INTEGER, "d1", Instant.now()));
        repository.insert(
                new JobConfigurationEntry("retryEnabled", "true", JobConfigurationValueType.BOOLEAN, "d2", Instant.now()));

        List<JobConfigurationEntry> all = repository.listAll();

        assertEquals(2, all.size());
    }

    @Test
    void update_changesValueAndRevalidatesAgainstNewType() {
        repository.insert(new JobConfigurationEntry(
                "chunkSize", "500", JobConfigurationValueType.INTEGER, "d1", Instant.now()));

        repository.update("chunkSize", "1000", JobConfigurationValueType.INTEGER, "updated description");

        JobConfigurationEntry updated = repository.findByKey("chunkSize").orElseThrow();
        assertEquals("1000", updated.value);
        assertEquals("updated description", updated.description);
    }

    @Test
    void update_rejectsValueInconsistentWithDeclaredType() {
        repository.insert(new JobConfigurationEntry(
                "chunkSize", "500", JobConfigurationValueType.INTEGER, "d1", Instant.now()));

        assertThrows(
                InvalidJobConfigurationValueException.class,
                () -> repository.update("chunkSize", "not-a-number", JobConfigurationValueType.INTEGER, "d1"));

        // The write is rejected before persisting — the original value must remain untouched.
        assertEquals("500", repository.findByKey("chunkSize").orElseThrow().value);
    }

    @Test
    void update_throwsNoSuchElementException_whenKeyAbsent() {
        assertThrows(
                NoSuchElementException.class,
                () -> repository.update("doesNotExist", "1", JobConfigurationValueType.INTEGER, "d"));
    }

    @Test
    void deleteByKey_removesEntry() {
        repository.insert(new JobConfigurationEntry(
                "chunkSize", "500", JobConfigurationValueType.INTEGER, "d1", Instant.now()));

        boolean deleted = repository.deleteByKey("chunkSize");

        assertTrue(deleted);
        assertTrue(repository.findByKey("chunkSize").isEmpty());
    }

    @Test
    void deleteByKey_returnsFalse_whenAbsent() {
        assertFalse(repository.deleteByKey("doesNotExist"));
    }

    @Test
    void getIntValue_parsesIntegerTypedEntry() {
        repository.insert(new JobConfigurationEntry(
                "chunkSize", "500", JobConfigurationValueType.INTEGER, "d1", Instant.now()));

        assertEquals(500, repository.getIntValue("chunkSize"));
    }

    @Test
    void getIntValue_throwsNoSuchElementException_whenKeyAbsent() {
        assertThrows(NoSuchElementException.class, () -> repository.getIntValue("doesNotExist"));
    }
}
