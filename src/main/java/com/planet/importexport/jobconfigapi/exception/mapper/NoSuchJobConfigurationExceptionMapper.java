package com.planet.importexport.jobconfigapi.exception.mapper;

import java.util.Map;
import java.util.NoSuchElementException;

import com.planet.importexport.jobconfigapi.JobConfigurationResource;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Translates {@link NoSuchElementException} — thrown by
 * {@link com.planet.importexport.jobconfig.JobConfigurationRepository#update}
 * when the target {@code key} does not exist, and by
 * {@link JobConfigurationResource#update} itself for the same reason — into a
 * {@code 404 Not Found} (design.md section 2's {@code PUT /{key}} implicitly
 * requires this: an update to a non-existent key cannot succeed).
 */
@Provider
public class NoSuchJobConfigurationExceptionMapper
        implements ExceptionMapper<NoSuchElementException> {

    /**
     * @param exception the caught {@link NoSuchElementException}
     * @return a {@code 404 Not Found} JSON response
     */
    @Override
    public Response toResponse(NoSuchElementException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                       .type(MediaType.APPLICATION_JSON)
                       .entity(Map.of("error",
                                      "job configuration entry not found"))
                       .build();
    }
}
