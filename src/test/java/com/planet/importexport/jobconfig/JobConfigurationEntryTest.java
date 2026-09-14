package com.planet.importexport.jobconfig;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests proving the {@link JobConfigurationEntry} constructor itself enforces ADR-0007's
 * write-time validation — a mismatched value/valueType combination can never be constructed, let
 * alone reach the repository.
 */
class JobConfigurationEntryTest {

    @Test
    void constructor_rejectsIntegerValueTypeWithNonIntegerValue() {
        assertThrows(
                InvalidJobConfigurationValueException.class,
                () -> new JobConfigurationEntry(
                        "chunkSize", "not-a-number", JobConfigurationValueType.INTEGER, "desc", Instant.now()));
    }

    @Test
    void constructor_rejectsBooleanValueTypeWithNonBooleanValue() {
        assertThrows(
                InvalidJobConfigurationValueException.class,
                () -> new JobConfigurationEntry(
                        "flag", "maybe", JobConfigurationValueType.BOOLEAN, "desc", Instant.now()));
    }

    @Test
    void constructor_acceptsMatchingValueAndValueType() {
        assertDoesNotThrow(() -> new JobConfigurationEntry(
                "chunkSize", "500", JobConfigurationValueType.INTEGER, "desc", Instant.now()));
    }
}
