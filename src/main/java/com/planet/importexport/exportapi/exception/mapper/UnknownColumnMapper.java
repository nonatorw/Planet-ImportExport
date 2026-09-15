package com.planet.importexport.exportapi.exception.mapper;

import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import com.planet.importexport.exportapi.exception.UnknownExportColumnException;

/**
 * Maps {@link UnknownExportColumnException} to {@code 400 Bad Request}
 * (task C1; specs/export/spec.md; design.md section 2: {@code 400 Bad
 * Request with {"error": "unknown column", "column": "loyalty_tier"}}).
 *
 * <p>No {@code quarkus-http-problem} (RFC 7807) dependency is available in
 * this project yet (build.gradle has no such extension), so this mapper
 * returns the plain JSON error shape design.md explicitly specifies rather
 * than a Problem Details envelope — introducing that extension would itself
 * be a new-dependency decision outside task C1's scope.</p>
 */
@Provider
public class UnknownColumnMapper
        implements ExceptionMapper<UnknownExportColumnException> {

    /**
     * Builds the JSON error response for an unrecognized export column.
     *
     * @param exception the caught {@link UnknownExportColumnException}
     *
     * @return a {@code 400 Bad Request} JSON response naming the first
     *         unrecognized column
     */
    @Override
    public Response toResponse(UnknownExportColumnException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                       .type(MediaType.APPLICATION_JSON)
                       .entity(Map.of("error",
                                      "unknown column",
                                      "column",
                                      exception.firstUnknownColumn()))
                       .build();
    }
}
