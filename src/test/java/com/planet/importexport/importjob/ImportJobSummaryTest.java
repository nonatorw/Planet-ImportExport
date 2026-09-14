package com.planet.importexport.importjob;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Pure unit test for the {@code import_jobs.summary} value object (design.md section 1.3). */
class ImportJobSummaryTest {

    @Test
    void emptySummaryStartsAtZero() {
        ImportJobSummary summary = ImportJobSummary.empty();

        assertEquals(0, summary.totalRows);
        assertEquals(0, summary.succeeded);
        assertEquals(0, summary.failed);
    }

    @Test
    void constructorAssignsAllCounters() {
        ImportJobSummary summary = new ImportJobSummary(3, 2, 1);

        assertEquals(3, summary.totalRows);
        assertEquals(2, summary.succeeded);
        assertEquals(1, summary.failed);
    }
}
