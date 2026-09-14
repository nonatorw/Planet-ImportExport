package com.planet.importexport.importapi;

import java.util.function.Supplier;

import com.planet.importexport.importapi.dto.JobStatusResponse;
import com.planet.importexport.importapi.dto.SubmitImportRequest;
import com.planet.importexport.importapi.dto.SubmitImportResponse;
import com.planet.importexport.importapi.exception.ImportJobNotFoundException;
import com.planet.importexport.importjob.ImportJobDocument;
import com.planet.importexport.importjob.ImportJobRepository;
import com.planet.importexport.staging.StagingEntryRepository;

import io.quarkus.security.Authenticated;
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
 * role model is defined anywhere in the design artifacts.</p>
 */
@Path("/api/v1/imports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class ImportResource {
    private final ImportProcessingService importProcessingService;
    private final ImportJobRepository importJobRepository;
    private final StagingEntryRepository stagingEntryRepository;

    /**
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
     * @return {@code 202 Accepted} with a {@link SubmitImportResponse} body
     */
    @POST
    public Response submit(@Valid SubmitImportRequest request) {
        String jobId = importProcessingService.submit(request.filePath());

        return Response.accepted(new SubmitImportResponse(jobId)).build();
    }

    /**
     * {@code B10}: returns the numeric summary and full staging entry list for
     * {@code jobId}.
     *
     * @param jobId the job identifier from the request path
     * @return the job's current status, summary, and staging errors
     * @throws ImportJobNotFoundException if no job exists for {@code jobId}
     */
    @GET
    @Path("/{jobId}")
    public JobStatusResponse status(@PathParam("jobId") String jobId) {
        Supplier<ImportJobNotFoundException> exceptionSupplier =
                () -> new ImportJobNotFoundException(jobId);

        ImportJobDocument job =
                importJobRepository.findByJobId(jobId)
                                   .orElseThrow(exceptionSupplier);

        return JobStatusResponse.from(job,
                                      stagingEntryRepository.findByJobId(jobId));
    }
}
