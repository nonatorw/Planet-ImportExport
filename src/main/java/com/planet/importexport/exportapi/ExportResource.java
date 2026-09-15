package com.planet.importexport.exportapi;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.planet.importexport.exportapi.dto.ExportRequest;
import com.planet.importexport.exportapi.model.ExportFormat;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkus.security.Authenticated;

/**
 * {@code POST /api/v1/exports} (design.md section 2; specs/export/spec.md).
 *
 * <p>Response {@code Content-Type} matches the requested format's natural
 * media type; the file content itself is the response body (design.md section
 * 2: "body = file content in requested format").
 * No {@code Content-Disposition}/filename is mandated by the spec, so none is
 * set here.</p>
 *
 * <p>{@code E1} (ADR-0006): requires a valid OIDC bearer token. See
 * {@link Authenticated} usage rationale on
 * {@link com.planet.importexport.importapi.ImportResource}. The class-level
 * {@link SecurityRequirement} mirrors that in the OpenAPI contract, referencing
 * the {@code bearerAuth} scheme declared on
 * {@code com.planet.importexport package-info.java}.</p>
 */
@Path("/api/v1/exports")
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Exports",
     description = "Data export in CSV, TXT, or XLSX format")
public class ExportResource {
    private static final String TEXT_CSV = "text/csv";
    private static final String TEXT_PLAIN = "text/plain";
    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExportService exportService;

    /**
     * Creates a new export resource.
     *
     * @param exportService orchestrates validation, projection, and
     *                      serialization for an export request
     */
    @Inject
    public ExportResource(ExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * Serializes the current version of every stored customer record into the
     * requested format and columns, returning the file content as the response
     * body.
     *
     * @param request the validated request body carrying the requested format
     *                and column list
     *
     * @return {@code 200 OK} with the serialized export content and a
     *         {@code Content-Type} matching the requested format
     */
    @POST
    @Operation(summary = "Export data",
               description = "Serializes the requested columns into the " +
                             "requested format (CSV, TXT, or XLSX) and " +
                             "returns the file content as the response body.")
    @APIResponses({
        @APIResponse(responseCode = "200",
                     description = "Export produced successfully; body is " +
                                   "the file content in the requested format"),
        @APIResponse(responseCode = "400",
                     description = "The request body failed validation, " +
                                   "named an unrecognized column, or " +
                                   "requested an unsupported format " +
                                   "(including legacy XLS)")
    })
    public Response exportRequestedColumns(@Valid ExportRequest request) {
        ExportFormat format =
                ExportFormat.fromRequestValue(request.format());

        byte[] content =
                exportService.exportColumns(format,
                                            request.columns());

        return Response.ok(content)
                       .type(mediaTypeFor(format))
                       .build();
    }

    /**
     * Maps an export format to its HTTP {@code Content-Type}.
     *
     * @param format the export format to map; already validated by
     *               {@link ExportService#export} by the time this is called
     *
     * @return the media type to use for the response body.
     */
    private static String mediaTypeFor(ExportFormat format) {
        return switch (format) {
            case CSV -> TEXT_CSV;
            case TXT -> TEXT_PLAIN;
            case XLSX -> XLSX_MEDIA_TYPE;
        };
    }
}
