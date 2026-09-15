package com.planet.importexport.importapi.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/imports} (design.md section 2):
 * {@code { "filePath": "/data/imports/customers_02.csv" } }.
 *
 * @param filePath the absolute or relative path (resolved on the server) of
 *                 the CSV file to import; must not be blank
 */
public record SubmitImportRequest(
    @NotBlank(message = "filePath must not be blank")
    String filePath) {
    }
