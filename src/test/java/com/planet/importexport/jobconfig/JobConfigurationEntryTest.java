package com.planet.importexport.jobconfig;

import java.time.Instant;

import com.planet.importexport.jobconfig.exception.InvalidJobConfigurationValueException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests proving the {@link JobConfigurationEntry} constructor itself
 * enforces ADR-0007's write-time validation — a mismatched value/valueType
 * combination can never be constructed, let alone reach the repository.
 */
class JobConfigurationEntryTest {

    /**
     * Constructing an entry with valueType {@code INTEGER} but a value that
     * does not parse as an integer is rejected at construction time.
     */
    @Test
    void constructor_rejectsIntegerValueTypeWithNonIntegerValue() {
        Assertions.assertThrows(
            InvalidJobConfigurationValueException.class,
            () -> new JobConfigurationEntry("chunkSize",
                                            "not-a-number",
                                            JobConfigurationValueType.INTEGER,
                                            "desc",
                                            Instant.now()));
    }

    /**
     * Constructing an entry with valueType {@code BOOLEAN} but a value that
     * does not parse as a boolean is rejected at construction time.
     */
    @Test
    void constructor_rejectsBooleanValueTypeWithNonBooleanValue() {
        Assertions.assertThrows(
            InvalidJobConfigurationValueException.class,
            () -> new JobConfigurationEntry("flag",
                                            "maybe",
                                            JobConfigurationValueType.BOOLEAN,
                                            "desc",
                                            Instant.now()));
    }

    /**
     * Constructing an entry whose value is consistent with its declared
     * valueType succeeds without throwing.
     */
    @Test
    void constructor_acceptsMatchingValueAndValueType() {
        Assertions.assertDoesNotThrow(
            () -> new JobConfigurationEntry("chunkSize",
                                            "500",
                                            JobConfigurationValueType.INTEGER,
                                            "desc",
                                            Instant.now()));
    }
}
