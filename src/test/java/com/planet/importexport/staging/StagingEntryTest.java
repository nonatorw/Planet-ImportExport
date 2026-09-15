package com.planet.importexport.staging;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit test for the {@link StagingEntry} document shape — no Quarkus
 * container needed. Fixes the exact field set required by ADR-0005 and
 * design.md section 1.2: {@code jobId}, {@code rowId}, {@code rowData},
 * {@code errorDescription}, {@code processedAt}.
 */
class StagingEntryTest {

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

        assertEquals("job-abc123", entry.jobId);
        assertEquals(5, entry.rowId);
        assertEquals("thirty", entry.rowData.get("age"));
        assertTrue(entry.errorDescription.contains("age"));

        assertEquals(Instant.parse("2026-09-14T10:00:03Z"),
                     entry.processedAt);

        assertNull(entry.id);
    }

    @Test
    void rowDataPreservesUnknownColumnsVerbatim() {
        // ADR-0005 / design.md 1.2: unknown-column rows are staged with their
        // full raw row, including the column outside the recognized schema.
        StagingEntry entry = new StagingEntry();
        entry.jobId = "job-xyz789";
        entry.rowId = 2;

        entry.rowData = Map.of("id", "2",
                               "name", "Ana",
                               "loyalty_tier", "gold");

        entry.errorDescription = "unknown column: 'loyalty_tier'";

        entry.processedAt = Instant.now();

        assertEquals("gold", entry.rowData.get("loyalty_tier"));

        assertTrue(entry.errorDescription.contains("loyalty_tier"));
    }

    @Test
    void rowIdIsFilePositionNotParsedIdField() {
        // ADR-0005 Consequences: rowId must remain identifiable even when the
        // row's own "id" content is missing or malformed, so it is defined as
        // file position, not parsed content.
        StagingEntry entry = new StagingEntry();
        entry.jobId = "job-abc123";
        entry.rowId = 3;
        entry.rowData = Map.of("id", "",
                               "name", "Missing Id Row");

        entry.errorDescription = "missing field value: 'id' is empty";

        entry.processedAt = Instant.now();

        assertEquals(3, entry.rowId);
        assertEquals("", entry.rowData.get("id"));
    }
}
