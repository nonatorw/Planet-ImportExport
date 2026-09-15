package com.planet.importexport.jobconfigapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.planet.importexport.jobconfig.JobConfigurationValueType;

/**
 * Request body for {@code POST /api/v1/job-configurations}
 * (design.md section 2): creates a brand-new, generic configuration entry —
 * the spec's "Generic CRUD API not limited to chunk size" requirement means
 * any {@code key} is accepted here, not just {@code chunkSize}.
 *
 * <p>{@code value} itself is intentionally not constrained beyond
 * {@code @NotNull} here (it may legitimately be an empty string for
 * {@code STRING}-typed entries): the value/valueType consistency check
 * (ADR-0007) is enforced downstream by {@link
 * com.planet.importexport.jobconfig.JobConfigurationEntry}'s constructor, not
 * by Bean Validation, since that check depends on two fields together and the
 * exact parse rule is owned by {@link JobConfigurationValueType}.
 *
 * @param key         the new entry's unique key; must not be blank
 * @param value       the new entry's raw value; must not be {@code null}
 *                    (an empty string is allowed for {@code STRING}-typed
 *                    entries)
 * @param valueType   the declared type used to parse/validate {@code value};
 *                    must not be {@code null}
 * @param description an optional human-readable description of the entry
 */
public record CreateJobConfigurationRequest(@NotBlank String key,
                                            @NotNull String value,
                                            @NotNull JobConfigurationValueType valueType,
                                            String description) {}
