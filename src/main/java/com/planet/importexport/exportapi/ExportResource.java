package com.planet.importexport.exportapi;

import com.planet.importexport.exportapi.dto.ExportRequest;
import com.planet.importexport.exportapi.model.ExportFormat;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * {@code POST /api/v1/exports} (design.md section 2; specs/export/spec.md).
 *
 * <p>Response {@code Content-Type} matches the requested format's natural
 * media type; the file content itself is the response body (design.md section
 * 2: "body = file content in requested format").
 * No {@code Content-Disposition}/filename is mandated by the spec, so none is
 * set here.
 */
@Path("/api/v1/exports")
@Consumes(MediaType.APPLICATION_JSON)
public class ExportResource {

    private static final String TEXT_CSV = "text/csv";
    private static final String TEXT_PLAIN = "text/plain";
    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExportService exportService;

    /**
     * @param exportService orchestrates validation, projection, and
     *                      serialization for an export request
     */
    @Inject
    public ExportResource(ExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * @param request the validated request body carrying the requested format
     *                and column list
     * @return {@code 200 OK} with the serialized export content and a
     *         {@code Content-Type} matching the requested format
     */
    @POST
    public Response export(@Valid ExportRequest request) {
        byte[] content = exportService.export(request.format(),
                                              request.columns());

        String mediaType = mediaTypeFor(request.format());

        return Response.ok(content)
                       .type(mediaType)
                       .build();
    }

    private static String mediaTypeFor(String requestedFormat) {
        // request.format() has already been validated by ExportService.export
        // (via ExportFormat.fromRequestValue) by the time this is called, so
        // re-parsing here is safe.
        ExportFormat format = ExportFormat.fromRequestValue(requestedFormat);

        return switch (format) {
            case CSV -> TEXT_CSV;
            case TXT -> TEXT_PLAIN;
            case XLSX -> XLSX_MEDIA_TYPE;
        };
    }
}
