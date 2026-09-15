package com.planet.importexport.importapi;

import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.planet.importexport.importapi.dto.JobStatusResponse;
import com.planet.importexport.importapi.dto.SubmitImportRequest;
import com.planet.importexport.importapi.dto.SubmitImportResponse;
import com.planet.importexport.importapi.exception.ImportJobNotFoundException;
import com.planet.importexport.importjob.ImportJobDocument;
import com.planet.importexport.importjob.ImportJobRepository;
import com.planet.importexport.staging.StagingEntryRepository;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.quarkus.security.Authenticated;

/**
 * REST resource for the Import ({@code B1}-{@code B9}) and Job Status
 * ({@code B10}) capabilities.
 * Both share this one resource class deliberately (design.md section 2 shows
 * both endpoints under the same {@code /api/v1/imports} path base;
 * {@code tasks.md}, "Execution instructions" explicitly calls for one resource
 * file, not two).
 *
 * <p>{@code E1} (ADR-0006): every method requires a valid OIDC bearer token.
 * The spec ("Requirement: OAuth2 Client Credentials protection on every
 * endpoint") only mandates "a valid access token", not a specific role or
 * scope, so {@link Authenticated} is the exact fit — no {@code @RolesAllowed}
 * role model is defined anywhere in the design artifacts. The class-level
 * {@link SecurityRequirement} mirrors that in the OpenAPI contract, referencing
 * the {@code bearerAuth} scheme declared on
 * {@code com.planet.importexport package-info.java}.</p>
 */
@Path("/api/v1/imports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Imports", description = "Import job submission and status queries")
public class ImportResource {
    private final ImportProcessingService importProcessingService;
    private final ImportJobRepository importJobRepository;
    private final StagingEntryRepository stagingEntryRepository;

    /**
     * Creates a new import resource.
     *
     * @param importProcessingService orchestrates import job submission and
     *                                processing
     * @param importJobRepository     persists and queries import jobs
     * @param stagingEntryRepository  queries rows staged for a given job
     */
    @Inject
    public ImportResource(ImportProcessingService importProcessingService,
                          ImportJobRepository importJobRepository,
                          StagingEntryRepository stagingEntryRepository) {
        this.importProcessingService = importProcessingService;
        this.importJobRepository = importJobRepository;
        this.stagingEntryRepository = stagingEntryRepository;
    }

    /**
     * {@code B1}: submits a new import job and returns {@code 202 Accepted}
     * with the {@code jobId} immediately — no row-outcome summary or staging
     * list (job-status spec, "Status information is available only via query,
     * not at submission").
     *
     * @param request the validated request body carrying the source file path
     *
     * @return {@code 202 Accepted} with a {@link SubmitImportResponse} body
     */
    @POST
    @Operation(summary = "Submit an import job",
               description = "Accepts a source file path and enqueues it " +
                             "for asynchronous import processing. Status " +
                             "information is available only via a " +
                             "subsequent query, not at submission time.")
    @APIResponses({
        @APIResponse(responseCode = "202",
                     description = "Import job accepted; " +
                                   "body carries the generated jobId"),
        @APIResponse(responseCode = "400",
                     description = "The request body failed validation, or " +
                                   "filePath does not reference a readable file")
    })
    public Response submit(@Valid SubmitImportRequest request) {
        String jobId = importProcessingService.submit(request.filePath());

        return Response.accepted(new SubmitImportResponse(jobId)).build();
    }

    /**
     * {@code B10}: returns the numeric summary and full staging entry list for
     * {@code jobId}.
     *
     * @param jobId the job identifier from the request path
     *
     * @return the job's current status, summary, and staging errors
     *
     * @throws ImportJobNotFoundException if no job exists for {@code jobId}
     */
    @GET
    @Path("/{jobId}")
    @Operation(summary = "Get import job status",
               description = "Returns the current status, numeric outcome " +
                             "summary, and full staging entry list for the " +
                             "given jobId.")
    @APIResponses({
        @APIResponse(responseCode = "200",
                     description = "Job found; body carries its status, " +
                                   "summary, and staging entries"),
        @APIResponse(responseCode = "404",
                     description = "No import job exists for the given jobId")
    })
    public JobStatusResponse status(
            @Parameter(description = "Identifier of the import job to look up",
                       required = true)
            @PathParam("jobId") String jobId) {
        Supplier<ImportJobNotFoundException> exceptionSupplier =
                () -> new ImportJobNotFoundException(jobId);

        ImportJobDocument job =
                importJobRepository.findByJobId(jobId)
                                   .orElseThrow(exceptionSupplier);

        return JobStatusResponse.from(job,
                                      stagingEntryRepository.findByJobId(jobId));
    }
}
