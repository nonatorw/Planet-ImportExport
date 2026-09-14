package com.planet.importexport.importjob;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pure unit tests for the {@code import_jobs} status lifecycle (design.md
 * section 1.3, section 3):
 * {@code PENDING -> RUNNING -> COMPLETED/FAILED}. No Quarkus container needed
 * — this is plain enum logic (per
 * {@code @421-frameworks-quarkus-testing-unit-tests}, prefer JUnit 5 alone for
 * container-free logic).
 */
class ImportJobStatusTest {
    @ParameterizedTest
    @CsvSource({"PENDING, RUNNING", "RUNNING, COMPLETED", "RUNNING, FAILED"})
    void allowsDocumentedTransitions(ImportJobStatus from,
                                     ImportJobStatus to) {
        assertTrue(from.canTransitionTo(to));
    }

    @ParameterizedTest
    @CsvSource({
        "PENDING, COMPLETED",
        "PENDING, FAILED",
        "PENDING, PENDING",
        "RUNNING, PENDING",
        "RUNNING, RUNNING",
        "COMPLETED, RUNNING",
        "COMPLETED, FAILED",
        "COMPLETED, PENDING",
        "COMPLETED, COMPLETED",
        "FAILED, RUNNING",
        "FAILED, COMPLETED",
        "FAILED, PENDING",
        "FAILED, FAILED"
    })
    void rejectsUndocumentedTransitions(ImportJobStatus from, ImportJobStatus to) {
        assertFalse(from.canTransitionTo(to));
    }
}
