package com.planet.importexport.jobconfigapi.exception.mapper;

import java.util.Map;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;

/**
 * Translates a MongoDB duplicate-key write failure — raised when {@code POST
 * /api/v1/job-configurations} supplies a {@code key} that already exists,
 * since {@code key} is the document's {@code _id} (design.md section 1.4) —
 * into a {@code 409 Conflict}, per {@code @402-frameworks-quarkus-rest}
 * ("409 Conflict for state collisions"). Any other {@link MongoWriteException}
 * category is not this mapper's concern and is rethrown for the default
 * unhandled-exception path to report as a server error.
 */
@Provider
public class DuplicateJobConfigurationKeyExceptionMapper
        implements ExceptionMapper<MongoWriteException> {

    /**
     * @param exception the caught {@link MongoWriteException}
     * @return a {@code 409 Conflict} JSON response when the failure is a
     *         duplicate-key write; the same exception rethrown unchanged for
     *         any other {@link MongoWriteException} category
     */
    @Override
    public Response toResponse(MongoWriteException exception) {
        if (ErrorCategory.fromErrorCode(exception.getCode()) != ErrorCategory.DUPLICATE_KEY) {
            throw exception;
        }

        return Response.status(Response.Status.CONFLICT)
                       .type(MediaType.APPLICATION_JSON)
                       .entity(Map.of("error",
                                      "job configuration key already exists"))
                       .build();
    }
}
