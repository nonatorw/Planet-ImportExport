package com.planet.importexport.jobconfigapi.exception.mapper;

import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import com.planet.importexport.jobconfig.exception.InvalidJobConfigurationValueException;

/**
 * Translates {@link InvalidJobConfigurationValueException} (ADR-0007
 * write-time validation — a plain {@code RuntimeException} carrying no HTTP
 * concern, per its own Javadoc) into a {@code 400 Bad Request}, per spec:
 * job-configuration, "Declared value type per configuration entry" — "the
 * write is rejected with a validation error" — and the D2 task's mapping
 * requirement.
 */
@Provider
public class InvalidJobConfigurationValueExceptionMapper
        implements ExceptionMapper<InvalidJobConfigurationValueException> {

    /**
     * @param exception the caught {@link InvalidJobConfigurationValueException}
     * @return a {@code 400 Bad Request} JSON response naming the offending
     *         key, declared value type, and rejected value
     */
    @Override
    public Response toResponse(InvalidJobConfigurationValueException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                       .type(MediaType.APPLICATION_JSON)
                       .entity(Map.of("error", "invalid job configuration value",
                                      "key", exception.key(),
                                      "valueType", exception.valueType().name(),
                                      "value", exception.value()))
                       .build();
    }
}
