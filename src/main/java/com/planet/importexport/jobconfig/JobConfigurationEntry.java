package com.planet.importexport.jobconfig;

import java.time.Instant;
import java.util.Objects;

import org.bson.codecs.pojo.annotations.BsonId;

import com.planet.importexport.jobconfig.exception.InvalidJobConfigurationValueException;

import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * One generic job configuration entry (design.md section 1.4; ADR-0007).
 *
 * <p>Unlike the other collections in this change, the document's own
 * {@code _id} doubles as the configuration key (e.g. {@code "chunkSize"})
 * rather than an {@link org.bson.types.ObjectId} — see design.md section
 * 1.4's example document. {@code value} is always stored as a string; the
 * declared {@link #valueType} tells both the write path (validation, see
 * {@link JobConfigurationValueType#validate(String, String)}) and read-path
 * consumers (e.g. {@link JobConfigurationRepository#getIntValue(String)}) how
 * to interpret it, per ADR-0007's "free-form string value with an explicit
 * declared valueType" decision.</p>
 */
@MongoEntity(collection = "job_configuration")
public class JobConfigurationEntry {
    /**
     * The configuration key itself (e.g. {@code "chunkSize"}), stored as the
     * Mongo {@code _id}.
     */
    @BsonId
    public String key;

    /**
     * String-encoded value; interpreted according to {@link #valueType}
     * (ADR-0007).
     */
    public String value;

    /**
     * Declared type governing how {@link #value} is validated and parsed
     * (ADR-0007).
     */
    public JobConfigurationValueType valueType;

    /**
     * Human-readable explanation of what this setting controls.
     */
    public String description;

    /**
     * When this entry was created or last updated.
     */
    public Instant updatedAt;

    /**
     * No-argument constructor required by the MongoDB POJO codec for
     * deserialization; not intended for direct application use.
     */
    public JobConfigurationEntry() {
        // Required by the MongoDB POJO codec.
    }

    /**
     * Creates a fully-populated entry, validating {@code value} against
     * {@code valueType} before assigning any field (ADR-0007, write-time
     * validation).
     *
     * @param key         the configuration key, stored as the Mongo
     *                    {@code _id}
     * @param value       string-encoded value; must conform to
     *                    {@code valueType} per
     *                    {@link JobConfigurationValueType#validate(String, String)}
     * @param valueType   the declared type governing how {@code value} is
     *                    validated and parsed
     * @param description human-readable explanation of what this setting
     *                    controls
     * @param updatedAt   when this entry was created or last updated
     * @throws NullPointerException                  if {@code key} or
     *                                                {@code valueType} is
     *                                                {@code null}
     * @throws InvalidJobConfigurationValueException if {@code value} does not
     *                                                parse as {@code valueType}
     */
    public JobConfigurationEntry(String key,
                                 String value,
                                 JobConfigurationValueType valueType,
                                 String description,
                                 Instant updatedAt) {
        this.key = Objects.requireNonNull(key,
                                          "key must not be null");
        this.valueType = Objects.requireNonNull(valueType,
                                                "valueType must not be null");

        valueType.validate(key, value);
        this.value = value;
        this.description = description;
        this.updatedAt = updatedAt;
    }
}
