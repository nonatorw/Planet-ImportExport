package com.planet.importexport.importjob;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test for the {@code import_jobs.summary} value object (design.md
 * section 1.3).
 */
class ImportJobSummaryTest {

    /**
     * {@link ImportJobSummary#empty()} produces a summary with
     * {@code totalRows}, {@code succeeded}, and {@code failed} all at zero.
     */
    @Test
    void emptySummaryStartsAtZero() {
        ImportJobSummary summary = ImportJobSummary.empty();

        Assertions.assertEquals(0, summary.totalRows);
        Assertions.assertEquals(0, summary.succeeded);
        Assertions.assertEquals(0, summary.failed);
    }

    /**
     * The constructor assigns {@code totalRows}, {@code succeeded}, and
     * {@code failed} exactly as given, in that order.
     */
    @Test
    void constructorAssignsAllCounters() {
        ImportJobSummary summary = new ImportJobSummary(3,
                                                        2,
                                                        1);

        Assertions.assertEquals(3, summary.totalRows);
        Assertions.assertEquals(2, summary.succeeded);
        Assertions.assertEquals(1, summary.failed);
    }
}
