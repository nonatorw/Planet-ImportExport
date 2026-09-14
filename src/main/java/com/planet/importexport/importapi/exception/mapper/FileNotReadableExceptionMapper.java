package com.planet.importexport.importapi.exception.mapper;

import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import com.planet.importexport.importapi.exception.FileNotReadableException;

/**
 * Maps {@link FileNotReadableException} to {@code 400 Bad Request} ({@code B1}).
 */
@Provider
public class FileNotReadableExceptionMapper
        implements ExceptionMapper<FileNotReadableException> {

    /**
     * @param exception the caught {@link FileNotReadableException}
     * @return a {@code 400 Bad Request} JSON response carrying the
     *         exception's own message under an {@code "error"} key
     */
    @Override
    public Response toResponse(FileNotReadableException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                       .type(MediaType.APPLICATION_JSON)
                       .entity(Map.of("error",
                                      exception.getMessage()))
                       .build();
    }
}
