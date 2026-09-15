package com.planet.importexport.exportapi.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * Request body for {@code POST /api/v1/exports} (design.md section 2).
 *
 * <p>{@code format} is validated for presence here (Bean Validation); the
 * actual set of supported formats — and the explicit rejection of legacy XLS —
 * is enforced by
 * {@code com.planet.importexport.exportapi.model.ExportFormat#fromRequestValue(String)},
 * since "is this a known format" is a domain rule, not a shape constraint.
 * Likewise {@code columns} is validated for presence/non-blank entries here,
 * while "is this a recognized column" is enforced by
 * {@code com.planet.importexport.exportapi.validator.ExportColumnValidator}.</p>
 *
 * @param format  the requested output format; must not be blank
 * @param columns the requested columns, in the exact order the output must
 *                honor; must not be empty and every entry must not be blank
 */
public record ExportRequest(
        @NotBlank(message = "format must not be blank")
        String format,
        @NotEmpty(message = "columns must not be empty")
        List<@NotBlank(message = "column name must not be blank") String> columns) {}
