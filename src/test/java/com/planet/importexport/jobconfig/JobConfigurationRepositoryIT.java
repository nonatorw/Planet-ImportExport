package com.planet.importexport.jobconfig;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import jakarta.inject.Inject;

import com.planet.importexport.jobconfig.exception.InvalidJobConfigurationValueException;
import com.planet.importexport.mongo.FlapdoodleMongoTestResource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for {@link JobConfigurationRepository} against the
 * embedded Flapdoodle MongoDB instance (ADR-0001), proving real persistence —
 * not mocked repository behaviour, per {@code @415-frameworks-quarkus-mongodb}
 * ("never mock PanacheMongoRepository inside a persistence test").
 */
@QuarkusTest
@QuarkusTestResource(FlapdoodleMongoTestResource.class)
class JobConfigurationRepositoryIT {
    @Inject
    JobConfigurationRepository repository;

    /*
     * The Quarkus test context (including JobConfigurationSeedMigration's
     * @Observes StartupEvent, which seeds "chunkSize" on first boot) is shared
     * across the whole suite, not per test method.
     * Without this @BeforeEach, whichever test runs first after startup would
     * with that seeded entry (JUnit5 does not guarantee method execution order
     * by default).
     */
    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    /**
     * Clears every configuration entry after each test, so one test's
     * writes cannot leak into the next.
     */
    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    /**
     * An entry inserted through the repository can be found back by its key,
     * with every field (value, valueType, description) round-tripped intact.
     */
    @Test
    void insertAndFindByKey_roundTripsAllFields() {
        JobConfigurationEntry entry =
                new JobConfigurationEntry("chunkSize",
                                          "500",
                                          JobConfigurationValueType.INTEGER,
                                          "batch size",
                                          Instant.now());

        repository.insert(entry);

        Optional<JobConfigurationEntry> found =
                repository.findByKey("chunkSize");

        Assertions.assertTrue(found.isPresent());

        Assertions.assertEquals("chunkSize",
                                found.get().key);

        Assertions.assertEquals("500",
                                found.get().value);

        Assertions.assertEquals(JobConfigurationValueType.INTEGER,
                                found.get().valueType);

        Assertions.assertEquals("batch size",
                                found.get().description);
    }

    /**
     * Looking up a key with no stored entry returns an empty result rather
     * than throwing.
     */
    @Test
    void findByKey_returnsEmpty_whenAbsent() {
        Assertions.assertTrue(repository.findByKey("doesNotExist")
                                        .isEmpty());
    }

    /**
     * Listing all entries returns every entry that has been inserted, not
     * just the most recently inserted one.
     */
    @Test
    void listAll_returnsEveryEntry() {
        repository.insert(
                new JobConfigurationEntry("chunkSize",
                                          "500",
                                          JobConfigurationValueType.INTEGER,
                                          "d1",
                                          Instant.now()));

        repository.insert(
                new JobConfigurationEntry("retryEnabled",
                                          "true",
                                          JobConfigurationValueType.BOOLEAN,
                                          "d2",
                                          Instant.now()));

        List<JobConfigurationEntry> all =
                repository.listAll();

        Assertions.assertEquals(2,
                                all.size());
    }

    /**
     * Updating an existing entry's value and description persists both new
     * values, validated against the (re-supplied) declared type.
     */
    @Test
    void update_changesValueAndRevalidatesAgainstNewType() {
        repository.insert(
                new JobConfigurationEntry("chunkSize",
                                          "500",
                                          JobConfigurationValueType.INTEGER,
                                          "d1",
                                          Instant.now()));

        repository.update("chunkSize",
                          "1000",
                          JobConfigurationValueType.INTEGER,
                          "updated description");

        JobConfigurationEntry updated =
                repository.findByKey("chunkSize")
                          .orElseThrow();

        Assertions.assertEquals("1000",
                                updated.value);
        Assertions.assertEquals("updated description",
                                updated.description);
    }

    /**
     * Updating an entry with a value inconsistent with its declared type is
     * rejected, and the previously stored value is left untouched.
     */
    @Test
    void update_rejectsValueInconsistentWithDeclaredType() {
        repository.insert(
                new JobConfigurationEntry("chunkSize",
                                          "500",
                                          JobConfigurationValueType.INTEGER,
                                          "d1",
                                          Instant.now()));

        Assertions.assertThrows(
            InvalidJobConfigurationValueException.class,
            () -> repository.update("chunkSize",
                                    "not-a-number",
                                    JobConfigurationValueType.INTEGER,
                                    "d1"));

        /*
         * The write is rejected before persisting — the original value must
          remain untouched.
         */
        Assertions.assertEquals("500",
                                repository.findByKey("chunkSize")
                                          .orElseThrow()
                                          .value);
    }

    /**
     * Updating a key with no stored entry throws {@link NoSuchElementException}
     * instead of silently creating one.
     */
    @Test
    void update_throwsNoSuchElementException_whenKeyAbsent() {
        Assertions.assertThrows(
            NoSuchElementException.class,
            () -> repository.update("doesNotExist",
                                    "1",
                                    JobConfigurationValueType.INTEGER,
                                    "d"));
    }

    /**
     * Deleting an existing key reports success and leaves no trace of the
     * entry behind.
     */
    @Test
    void deleteByKey_removesEntry() {
        repository.insert(
                new JobConfigurationEntry("chunkSize",
                                          "500",
                                          JobConfigurationValueType.INTEGER,
                                          "d1",
                                          Instant.now()));

        boolean deleted =
                repository.deleteByKey("chunkSize");

        Assertions.assertTrue(deleted);
        Assertions.assertTrue(repository.findByKey("chunkSize").isEmpty());
    }

    /**
     * Deleting a key with no stored entry returns {@code false} rather than
     * throwing.
     */
    @Test
    void deleteByKey_returnsFalse_whenAbsent() {
        Assertions.assertFalse(repository.deleteByKey("doesNotExist"));
    }

    /**
     * Reading an {@code INTEGER}-typed entry's value through
     * {@code getIntValue} returns it parsed as an {@code int}.
     */
    @Test
    void getIntValue_parsesIntegerTypedEntry() {
        repository.insert(
                new JobConfigurationEntry("chunkSize",
                                          "500",
                                          JobConfigurationValueType.INTEGER,
                                          "d1",
                                          Instant.now()));

        Assertions.assertEquals(500,
                                repository.getIntValue("chunkSize"));
    }

    /**
     * Reading an integer value for a key with no stored entry throws
     * {@link NoSuchElementException} instead of returning a default.
     */
    @Test
    void getIntValue_throwsNoSuchElementException_whenKeyAbsent() {
        Assertions.assertThrows(
            NoSuchElementException.class,
            () -> repository.getIntValue("doesNotExist"));
    }
}
