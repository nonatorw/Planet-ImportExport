package com.planet.importexport.jobconfigapi.dto;

import com.planet.importexport.jobconfig.JobConfigurationValueType;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PUT /api/v1/job-configurations/{key}} (design.md section 2). {@code
 * valueType} and {@code description} are optional per the design table (marked {@code
 * "valueType"?, "description"?}); when omitted, the resource preserves the entry's current
 * {@code valueType}/{@code description} rather than clearing them — a sensible default for a
 * partial update, not a business rule fixed by the spec/design.
 *
 * @param value       the entry's new raw value; must not be {@code null}
 * @param valueType   the new declared value type, or {@code null} to keep the
 *                    entry's current type
 * @param description the new description, or {@code null} to keep the
 *                    entry's current description
 */
public record UpdateJobConfigurationRequest(@NotNull String value,
                                            JobConfigurationValueType valueType,
                                            String description) {}
