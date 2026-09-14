package com.planet.importexport.jobconfigapi.dto;

import java.time.Instant;

import com.planet.importexport.jobconfig.JobConfigurationEntry;
import com.planet.importexport.jobconfig.JobConfigurationValueType;

/**
 * Outbound representation of a {@link JobConfigurationEntry} (design.md
 * section 2, REST API surface). Decouples the wire contract from the
 * persistence entity per {@code @402-frameworks-quarkus-rest}
 * ("Decouple API contracts from persistence entities") — even though today
 * the field sets happen to match, a response DTO keeps that an implementation
 * detail rather than an accidental API contract.
 *
 * @param key         the entry's unique key
 * @param value       the entry's raw value
 * @param valueType   the declared type used to parse/validate {@code value}
 * @param description a human-readable description of the entry, or
 *                    {@code null} when none was set
 * @param updatedAt   the instant this entry was last written
 */
public record JobConfigurationResponse(String key,
                                       String value,
                                       JobConfigurationValueType valueType,
                                       String description,
                                       Instant updatedAt) {

    /**
     * @param entry the persisted entity to project
     * @return the equivalent outbound response DTO
     */
    public static JobConfigurationResponse from(JobConfigurationEntry entry) {
        return new JobConfigurationResponse(entry.key,
                                            entry.value,
                                            entry.valueType,
                                            entry.description,
                                            entry.updatedAt);
    }
}
