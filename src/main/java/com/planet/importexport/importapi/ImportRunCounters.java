package com.planet.importexport.importapi;

import java.util.concurrent.atomic.AtomicInteger;

import com.planet.importexport.importjob.ImportJobSummary;

/**
 * Mutable running totals for one job's chunked processing loop
 * ({@link ImportProcessingService}): {@code totalRows}, {@code succeeded},
 * and {@code failed} always travel together across chunk boundaries, so this
 * groups them into a single value passed to and updated by
 * {@code processChunk}, replacing three separate {@link AtomicInteger}
 * parameters.
 */
final class ImportRunCounters {
    private final AtomicInteger totalRows = new AtomicInteger();
    private final AtomicInteger succeeded = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    /**
     * Records one more row as processed, regardless of outcome.
     */
    void incrementTotal() {
        totalRows.incrementAndGet();
    }

    /**
     * Records one more row as successfully persisted.
     */
    void incrementSucceeded() {
        succeeded.incrementAndGet();
    }

    /**
     * Records one more row as staged due to a validation failure.
     */
    void incrementFailed() {
        failed.incrementAndGet();
    }

    /**
     * Snapshots the current totals as an immutable {@link ImportJobSummary}.
     *
     * @return a summary reflecting the counts at the time of the call
     */
    ImportJobSummary toSummary() {
        return new ImportJobSummary(totalRows.get(),
                                    succeeded.get(),
                                    failed.get());
    }
}
