package com.planet.importexport.customerrecord;

import io.quarkus.mongodb.panache.common.MongoEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

/**
 * One immutable version of a customer record (design.md section 1.1; ADR-0001; ADR-0004).
 *
 * <p>Every import that changes a given business {@code id} inserts a brand-new document with an
 * incremented {@code version}; existing documents are never updated or deleted by this model. The
 * "current" version of an {@code id} is, by convention, the document with the highest
 * {@code version} for that {@code id} (see {@link CustomerRecordRepository}).
 *
 * <p>{@code fields} holds only keys from the recognized schema ({@link RecognizedField}); any
 * other column found in a source file is routed to {@code staging_entries} instead (Group A2, not
 * part of this document model).
 */
@MongoEntity(collection = "customer_records")
public class CustomerRecordDocument {

    public ObjectId id;

    /**
     * Business identity key from the source CSV {@code id} column. Named {@code recordId} in Java
     * to avoid clashing with the Mongo document {@code id} (the {@link ObjectId} primary key)
     * while still mapping to the {@code id} field in the stored document, per design.md section
     * 1.1.
     */
    @BsonProperty("id")
    public String recordId;

    /** Monotonically increasing per {@link #recordId}, starting at 1. */
    public int version;

    /** Schema-flexible map populated only with {@link RecognizedField} keys. */
    public Map<String, Object> fields = new LinkedHashMap<>();

    /** The import job that created this version. */
    public String sourceJobId;

    public Instant createdAt;

    public CustomerRecordDocument() {
        // Required by the MongoDB POJO codec.
    }

    public CustomerRecordDocument(
            String recordId,
            int version,
            Map<String, Object> fields,
            String sourceJobId,
            Instant createdAt) {
        this.recordId = recordId;
        this.version = version;
        this.fields = new LinkedHashMap<>(fields);
        this.sourceJobId = sourceJobId;
        this.createdAt = createdAt;
    }
}
