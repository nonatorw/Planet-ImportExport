package com.planet.importexport.importapi.exception.mapper;

import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import com.planet.importexport.importapi.exception.ImportJobNotFoundException;

/**
 * Maps {@link ImportJobNotFoundException} to {@code 404 Not Found}
 * ({@code B10}).
 */
@Provider
public class ImportJobNotFoundExceptionMapper
        implements ExceptionMapper<ImportJobNotFoundException> {

    /**
     * Converts the caught exception into a client-facing error response.
     *
     * @param exception the caught {@link ImportJobNotFoundException}
     *
     * @return a {@code 404 Not Found} JSON response carrying the exception's
     *         own message under an {@code "error"} key
     */
    @Override
    public Response toResponse(ImportJobNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                       .type(MediaType.APPLICATION_JSON)
                       .entity(Map.of("error",
                                      exception.getMessage()))
                       .build();
    }
}
