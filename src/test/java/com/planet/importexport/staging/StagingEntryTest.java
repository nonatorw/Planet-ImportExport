package com.planet.importexport.staging;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test for the {@link StagingEntry} document shape — no Quarkus
 * container needed.
 *
 * Fixes the exact field set required by ADR-0005 and design.md section 1.2:
 * {@code jobId}, {@code rowId}, {@code rowData}, {@code errorDescription},
 * {@code processedAt}.
 */
class StagingEntryTest {

    /**
     * A {@link StagingEntry} holds and returns exactly the values assigned
     * to its {@code jobId}, {@code rowId}, {@code rowData},
     * {@code errorDescription}, and {@code processedAt} fields, while its
     * {@code id} stays {@code null} until persisted.
     */
    @Test
    void holdsAllFieldsRequiredByAdr0005() {
        StagingEntry entry = new StagingEntry();
        entry.jobId = "job-abc123";
        entry.rowId = 5;

        entry.rowData = Map.of("id", "5",
                               "name", "Marco Rossi",
                               "age", "thirty");

        entry.errorDescription =
                "invalid age value: 'thirty' is not an integer in range 0-120";

        entry.processedAt =
                Instant.parse("2026-09-14T10:00:03Z");

        Assertions.assertEquals("job-abc123",
                                entry.jobId);

        Assertions.assertEquals(5,
                                entry.rowId);

        Assertions.assertEquals("thirty",
                                entry.rowData.get("age"));

        Assertions.assertTrue(entry.errorDescription.contains("age"));

        Assertions.assertEquals(Instant.parse("2026-09-14T10:00:03Z"),
                                entry.processedAt);

        Assertions.assertNull(entry.id);
    }

    /**
     * A column outside the recognized schema is preserved verbatim in
     * {@code rowData} rather than being dropped from the staged entry.
     */
    @Test
    void rowDataPreservesUnknownColumnsVerbatim() {
        /*
         * ADR-0005 / design.md 1.2: unknown-column rows are staged with their
         * full raw row, including the column outside the recognized schema.
         */
        StagingEntry entry = new StagingEntry();
        entry.jobId = "job-xyz789";
        entry.rowId = 2;

        entry.rowData = Map.of("id", "2",
                               "name", "Ana",
                               "loyalty_tier", "gold");

        entry.errorDescription = "unknown column: 'loyalty_tier'";

        entry.processedAt = Instant.now();

        Assertions.assertEquals("gold",
                                entry.rowData.get("loyalty_tier"));

        Assertions.assertTrue(entry.errorDescription.contains("loyalty_tier"));
    }

    /**
     * {@code rowId} reflects the row's file position and stays set even
     * when the row's own parsed {@code id} value is missing or blank.
     */
    @Test
    void rowIdIsFilePositionNotParsedIdField() {
        /*
         * ADR-0005 Consequences: rowId must remain identifiable even when the
         * row's own "id" content is missing or malformed, so it is defined as
         * file position, not parsed content.
         */
        StagingEntry entry = new StagingEntry();
        entry.jobId = "job-abc123";
        entry.rowId = 3;
        entry.rowData = Map.of("id", "",
                               "name", "Missing Id Row");

        entry.errorDescription = "missing field value: 'id' is empty";

        entry.processedAt = Instant.now();

        Assertions.assertEquals(3,
                                entry.rowId);

        Assertions.assertEquals("",
                                entry.rowData.get("id"));
    }
}
