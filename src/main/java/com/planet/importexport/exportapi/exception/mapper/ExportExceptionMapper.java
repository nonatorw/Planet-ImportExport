package com.planet.importexport.exportapi.exception.mapper;

import java.util.Map;

import com.planet.importexport.exportapi.exception.UnknownExportColumnException;
import com.planet.importexport.exportapi.exception.UnsupportedExportFormatException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Translates export-specific validation failures into HTTP 400 responses
 * (task C1, C5; specs/export/spec.md; design.md section 2: {@code 400 Bad
 * Request with {"error": "unknown column", "column": "loyalty_tier"}}).
 *
 * <p>Two narrowly-typed {@code @Provider} beans (one per exception type)
 * rather than a single mapper over a common supertype: JAX-RS selects the most
 * specific registered {@link ExceptionMapper} for a thrown exception's type,
 * so keeping each mapper scoped to exactly one exception type avoids any need
 * to rethrow for "not my case" — a mapper that rethrows the same
 * runtime-exception family it declared itself for would be re-invoked by the
 * JAX-RS runtime, which is fragile and unnecessary here. Both mappers are
 * intentionally kept as nested classes of this single {@code
 * ExportExceptionMapper} type, since they together cover the complete
 * export-validation error surface (tasks C1 and C5) and share the same JSON
 * error shape and design rationale.
 *
 * <p>No {@code quarkus-http-problem} (RFC 7807) dependency is available in
 * this project yet (build.gradle has no such extension), so these mappers
 * return the plain JSON error shape design.md explicitly specifies rather than
 * a Problem Details envelope — introducing that extension would itself be a
 * new-dependency decision outside task C1-C5's scope.
 */
public final class ExportExceptionMapper {

    /** Not instantiable: a namespace for the nested mapper providers below. */
    private ExportExceptionMapper() {
        // Namespace for the nested mapper providers below.
    }

    /**
     * Maps {@link UnknownExportColumnException} to {@code 400 Bad Request}
     * (task C1).
     */
    @Provider
    public static class UnknownColumnMapper
            implements ExceptionMapper<UnknownExportColumnException> {

        /**
         * @param exception the caught {@link UnknownExportColumnException}
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

    /**
     * Maps {@link UnsupportedExportFormatException} to {@code 400 Bad
     * Request} (task C5).
     */
    @Provider
    public static class UnsupportedFormatMapper
            implements ExceptionMapper<UnsupportedExportFormatException> {

        /**
         * @param exception the caught {@link UnsupportedExportFormatException}
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
}
