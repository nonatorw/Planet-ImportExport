package com.planet.importexport.customerrecord;

import com.mongodb.client.model.IndexOptions;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.panache.common.Sort;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;

/**
 * Repository for the {@code customer_records} collection (design.md section 1.1; ADR-0001;
 * ADR-0004).
 *
 * <p>Every write is an insert of a brand-new version document; this repository never updates or
 * deletes an existing version.
 */
@ApplicationScoped
public class CustomerRecordRepository implements PanacheMongoRepository<CustomerRecordDocument> {

    /**
     * Ensures the indexes design.md section 1.1 requires: a unique compound index on
     * {@code (id, version)} enforcing "no duplicate version numbers per id" (ADR-0001,
     * Confirmation), and a supporting index for descending-version lookups (the "current version"
     * query below sorts by {@code version desc}).
     *
     * <p>{@code createIndex} is idempotent — recreating an index with the same keys/options on
     * every startup is a no-op once it already exists, which is the standard Quarkus MongoDB
     * Panache idiom for index setup (no declarative index annotation exists in
     * quarkus-mongodb-panache).
     */
    void ensureIndexes(@Observes StartupEvent startupEvent) {
        mongoCollection()
                .createIndex(
                        new Document("id", 1).append("version", 1), new IndexOptions().unique(true));
        mongoCollection().createIndex(new Document("id", 1).append("version", -1));
    }

    /**
     * The current version of a business {@code id} is the document with the highest
     * {@code version} for that id (design.md section 1.1; ADR-0001).
     */
    public Optional<CustomerRecordDocument> findCurrentVersion(String recordId) {
        return find("id", Sort.descending("version"), recordId).firstResultOptional();
    }

    /** Fetches one specific version of a business {@code id}, if it exists. */
    public Optional<CustomerRecordDocument> findVersion(String recordId, int version) {
        return find("id = ?1 and version = ?2", recordId, version).firstResultOptional();
    }

    /**
     * Inserts the next version of a business {@code id}'s record, computed per ADR-0004: if no
     * version exists yet, version 1 is inserted using the incoming recognized fields as given;
     * otherwise version (current + 1) is inserted with fields computed by
     * {@link CustomerRecordVersionMerge#merge(Map, Map)}.
     *
     * <p>Callers are responsible for ensuring only one job writes a given {@code id} at a time
     * (ADR-0003's whole-job serialization gate, implemented outside this repository) — this method
     * performs a plain read-then-insert with no optimistic-locking retry, which is safe only under
     * that external serialization guarantee.
     *
     * @param recordId the business identity key
     * @param incomingRecognizedFields the recognized fields present in the newly imported row
     * @param sourceJobId the import job creating this version
     * @return the newly inserted version document
     */
    public CustomerRecordDocument insertNextVersion(
            String recordId, Map<String, Object> incomingRecognizedFields, String sourceJobId) {
        Optional<CustomerRecordDocument> currentVersion = findCurrentVersion(recordId);

        int nextVersion = currentVersion.map(document -> document.version + 1).orElse(1);
        Map<String, Object> previousFields =
                currentVersion.map(document -> document.fields).orElseGet(LinkedHashMap::new);
        Map<String, Object> mergedFields =
                CustomerRecordVersionMerge.merge(previousFields, incomingRecognizedFields);

        CustomerRecordDocument nextVersionDocument =
                new CustomerRecordDocument(
                        recordId, nextVersion, mergedFields, sourceJobId, Instant.now());
        persist(nextVersionDocument);
        return nextVersionDocument;
    }
}
