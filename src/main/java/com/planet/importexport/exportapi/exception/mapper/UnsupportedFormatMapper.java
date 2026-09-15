package com.planet.importexport.exportapi.exception.mapper;

import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import com.planet.importexport.exportapi.exception.UnsupportedExportFormatException;

/**
 * Maps {@link UnsupportedExportFormatException} to {@code 400 Bad Request}
 * (task C5; specs/export/spec.md; design.md section 2: {@code 400 Bad
 * Request with {"error": "unsupported format", "format": "XLS"}}).
 *
 * <p>No {@code quarkus-http-problem} (RFC 7807) dependency is available in
 * this project yet (build.gradle has no such extension), so this mapper
 * returns the plain JSON error shape design.md explicitly specifies rather
 * than a Problem Details envelope — introducing that extension would itself
 * be a new-dependency decision outside task C5's scope.</p>
 */
@Provider
public class UnsupportedFormatMapper
        implements ExceptionMapper<UnsupportedExportFormatException> {

    /**
     * Builds the JSON error response for an unsupported export format.
     *
     * @param exception the caught {@link UnsupportedExportFormatException}
     *
     * @return a {@code 400 Bad Request} JSON response naming the
     *         unsupported requested format
     */
    @Override
    public Response toResponse(UnsupportedExportFormatException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                       .type(MediaType.APPLICATION_JSON)
                       .entity(Map.of("error",
                                      "unsupported format",
                                      "format",
                                      String.valueOf(exception.requestedFormat())))
                       .build();
    }
}
