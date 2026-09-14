package com.planet.importexport.staging;

import io.quarkus.mongodb.panache.common.MongoEntity;
import java.time.Instant;
import java.util.Map;
import org.bson.types.ObjectId;

/**
 * A single generic staging document (ADR-0005) recording one row that could not be persisted into
 * {@code customer_records} — whether because a recognized field was missing, a recognized field
 * held an invalid value, or the row referenced a header column outside the recognized schema. All
 * three problem categories share this one document shape; only {@link #errorDescription}'s text
 * distinguishes them (ADR-0005 explicitly rejects separate collections/subtypes per category).
 *
 * <p>Matches design.md section 1.2 exactly: {@code jobId}, {@code rowId}, {@code rowData},
 * {@code errorDescription}, {@code processedAt}.
 */
@MongoEntity(collection = "staging_entries")
public class StagingEntry {

    public ObjectId id;

    /** The owning import job's id (design.md section 1.2; ADR-0005). */
    public String jobId;

    /**
     * The row's 1-based position within the source file, header excluded. Deliberately the row's
     * file position rather than its parsed {@code id} field, since a row may be invalid or missing
     * precisely because its own {@code id} value is unusable (ADR-0005, Consequences).
     */
    public int rowId;

    /**
     * The raw parsed row, verbatim, keyed by source header column name — including any column
     * outside the recognized schema, since unknown-column rows are staged with their full row
     * content (ADR-0005; design.md section 1.2 example).
     */
    public Map<String, Object> rowData;

    /**
     * Free text identifying the problem, e.g. {@code "invalid age value: 'thirty' is not an
     * integer in range 0-120"} or {@code "unknown column: 'loyalty_tier'"} (ADR-0005). This is the
     * only field distinguishing the missing/invalid/unknown-column categories.
     */
    public String errorDescription;

    /** When this staging entry was recorded. */
    public Instant processedAt;
}
