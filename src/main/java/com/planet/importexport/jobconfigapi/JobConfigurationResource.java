package com.planet.importexport.jobconfigapi;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import com.planet.importexport.jobconfig.JobConfigurationEntry;
import com.planet.importexport.jobconfig.JobConfigurationRepository;
import com.planet.importexport.jobconfigapi.dto.CreateJobConfigurationRequest;
import com.planet.importexport.jobconfigapi.dto.JobConfigurationResponse;
import com.planet.importexport.jobconfigapi.dto.UpdateJobConfigurationRequest;
import com.planet.importexport.jobconfigapi.exception.mapper.InvalidJobConfigurationValueExceptionMapper;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Generic CRUD REST API over {@link JobConfigurationRepository} (design.md
 * section 2, "REST API surface"; spec: job-configuration — "Generic CRUD API
 * not limited to chunk size").
 *
 * <p>Write-time {@code valueType} consistency (ADR-0007) is enforced by
 * {@link JobConfigurationEntry}'s constructor and
 * {@link JobConfigurationRepository#update}; this resource only translates
 * the resulting
 * {@link com.planet.importexport.jobconfig.exception.InvalidJobConfigurationValueException}
 * into an HTTP response — see {@link InvalidJobConfigurationValueExceptionMapper}
 * — since that exception "carries no HTTP concern itself" per its own Javadoc.
 *
 * <p>Not yet OIDC-protected: per docs/openspec/changes/file-import-export/tasks.md
 * ("Execution instructions"), Group E (Authentication) is applied on top of
 * the already-implemented Groups B/C/D resource classes only after they are
 * all complete — adding security annotations here now would be out of this
 * task's scope.
 */
@Path("/api/v1/job-configurations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class JobConfigurationResource {
    private final JobConfigurationRepository repository;

    /**
     * @param repository the CDI-managed repository backing every endpoint in
     *                    this resource
     */
    @Inject
    public JobConfigurationResource(JobConfigurationRepository repository) {
        this.repository = repository;
    }

    /**
     * Lists every configuration entry.
     *
     * @return a {@code 200 OK} body containing all entries, or an empty JSON
     *         array when none exist
     */
    @GET
    public List<JobConfigurationResponse> list() {
        return repository.listAll()
                         .stream()
                         .map(JobConfigurationResponse::from)
                         .toList();
    }

    /**
     * Retrieves a single configuration entry by its key.
     *
     * @param key the entry's unique key (its document {@code _id})
     * @return a {@code 200 OK} response with the entry, or {@code 404 Not
     *         Found} when no entry has that key
     */
    @GET
    @Path("{key}")
    public Response get(@PathParam("key") String key) {
        Optional<JobConfigurationEntry> entry = repository.findByKey(key);

        if (entry.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                           .build();
        }

        return Response.ok(JobConfigurationResponse.from(entry.get()))
                       .build();
    }

    /**
     * Creates a brand-new configuration entry.
     *
     * @param request the validated request body describing the new entry
     * @return a {@code 201 Created} response with a {@code Location} header
     *         pointing at the new entry and the created entry as its body
     * @throws com.planet.importexport.jobconfig.exception.InvalidJobConfigurationValueException
     *         if {@code value} is not consistent with {@code valueType}
     *         (ADR-0007); translated to {@code 400 Bad Request} by
     *         {@link InvalidJobConfigurationValueExceptionMapper}
     */
    @POST
    public Response create(@Valid CreateJobConfigurationRequest request) {
        JobConfigurationEntry entry =
                new JobConfigurationEntry(request.key(),
                                          request.value(),
                                          request.valueType(),
                                          request.description(),
                                          Instant.now());

        repository.insert(entry);

        return Response.created(URI.create("/api/v1/job-configurations/" + entry.key))
                       .entity(JobConfigurationResponse.from(entry))
                       .build();
    }

    /**
     * Updates an existing configuration entry. {@code valueType} and
     * {@code description} are optional in the request; when omitted, the
     * entry's current values are preserved rather than cleared.
     *
     * @param key     the key of the entry to update
     * @param request the validated partial-update request body
     * @return a {@code 200 OK} response with the updated entry
     * @throws NoSuchElementException if no entry has the given {@code key};
     *         translated to {@code 404 Not Found} by
     *         {@link com.planet.importexport.jobconfigapi.exception.mapper.NoSuchJobConfigurationExceptionMapper}
     * @throws com.planet.importexport.jobconfig.exception.InvalidJobConfigurationValueException
     *         if {@code value} is not consistent with the resolved
     *         {@code valueType} (ADR-0007); translated to {@code 400 Bad
     *         Request} by {@link InvalidJobConfigurationValueExceptionMapper}
     */
    @PUT
    @Path("{key}")
    public Response update(@PathParam("key") String key,
                           @Valid UpdateJobConfigurationRequest request) {
        JobConfigurationEntry current =
                repository.findByKey(key)
                          .orElseThrow(NoSuchElementException::new);

        var valueType = request.valueType() != null
                      ? request.valueType()
                      : current.valueType;

        var description = request.description() != null
                        ? request.description()
                        : current.description;

        JobConfigurationEntry updated = repository.update(key,
                                                          request.value(),
                                                          valueType,
                                                          description);

        return Response.ok(JobConfigurationResponse.from(updated))
                       .build();
    }

    /**
     * Deletes a configuration entry by its key.
     *
     * @param key the key of the entry to delete
     * @return a {@code 204 No Content} response when the entry existed and
     *         was removed, or {@code 404 Not Found} when no entry had that
     *         key
     */
    @DELETE
    @Path("{key}")
    public Response delete(@PathParam("key") String key) {
        boolean deleted = repository.deleteByKey(key);

        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND)
                           .build();
        }

        return Response.noContent()
                       .build();
    }
}
