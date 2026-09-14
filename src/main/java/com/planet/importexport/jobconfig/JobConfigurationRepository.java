package com.planet.importexport.jobconfig;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * CRUD access to {@code job_configuration} entries (design.md section 1.4; ADR-0007), plus a
 * typed-read convenience for consumers that need a parsed value rather than the raw string — e.g.
 * the chunked-processing logic (Group B3) reading {@code chunkSize} as an {@code int}.
 *
 * <p>Every write (insert/update) re-validates {@code value} against {@code valueType} via {@link
 * JobConfigurationValueType#validate(String, String)} before persisting, so a malformed entry can
 * never reach storage through this repository (ADR-0007's primary safeguard; it does not claim
 * database-level enforcement).
 */
@ApplicationScoped
public class JobConfigurationRepository implements PanacheMongoRepository<JobConfigurationEntry> {

    public Optional<JobConfigurationEntry> findByKey(String key) {
        return find("_id", key).firstResultOptional();
    }

    public List<JobConfigurationEntry> listAll() {
        return listAll(io.quarkus.panache.common.Sort.by("key"));
    }

    /** Inserts a brand-new entry. {@code entry.value}/{@code entry.valueType} are validated by the entry's own constructor. */
    public JobConfigurationEntry insert(JobConfigurationEntry entry) {
        persist(entry);
        return entry;
    }

    /**
     * Updates an existing entry's value/type/description in place, re-validating {@code newValue}
     * against {@code newValueType} before persisting (ADR-0007, write-time validation applies to
     * updates as well as inserts).
     *
     * @throws java.util.NoSuchElementException if no entry exists for {@code key}
     * @throws InvalidJobConfigurationValueException if {@code newValue} does not parse as {@code newValueType}
     */
    public JobConfigurationEntry update(
            String key, String newValue, JobConfigurationValueType newValueType, String newDescription) {
        JobConfigurationEntry entry = findByKey(key).orElseThrow();
        newValueType.validate(key, newValue);
        entry.value = newValue;
        entry.valueType = newValueType;
        entry.description = newDescription;
        entry.updatedAt = Instant.now();
        update(entry);
        return entry;
    }

    public boolean deleteByKey(String key) {
        return delete("_id", key) > 0;
    }

    /**
     * Reads {@code key}'s value parsed as an {@code int}, per its declared {@code valueType}
     * (ADR-0007, typed read). Used by Group B3 to read {@code chunkSize} without every consumer
     * re-implementing its own parsing/validation.
     *
     * @throws java.util.NoSuchElementException if no entry exists for {@code key}
     * @throws InvalidJobConfigurationValueException if the stored entry's {@code valueType} is not
     *     {@link JobConfigurationValueType#INTEGER}, or its value does not parse as one (defensive:
     *     write-time validation should already prevent this, per ADR-0007's Consequences)
     */
    public int getIntValue(String key) {
        JobConfigurationEntry entry = findByKey(key).orElseThrow();
        JobConfigurationValueType.INTEGER.validate(key, entry.value);
        if (entry.valueType != JobConfigurationValueType.INTEGER) {
            throw new InvalidJobConfigurationValueException(key, entry.valueType, entry.value);
        }
        return Integer.parseInt(entry.value);
    }
}
