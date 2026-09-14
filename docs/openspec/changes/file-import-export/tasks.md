# Tasks: File Import and Export Service

Single checklist, ordered by dependency. Each task references the governing ADR(s) and/or spec delta. This checklist is handed off to the Java Full-stack Engineer for implementation and the DevOps/Platform Engineer for build/CI concerns; the Solutions Architect does not implement these tasks.

## Project scaffolding

- [ ] Scaffold the Gradle/Quarkus project skeleton (`src/main/java`, `src/main/resources`, `src/test/java`) per `AGENTS.md` (Java 25, Gradle, Quarkus).
- [ ] Add MongoDB client extension (`quarkus-mongodb-client` or `quarkus-mongodb-panache`) and embedded/in-memory Flapdoodle test-and-runtime support per ADR-0001.
- [ ] Add `quarkus-oidc` extension and Keycloak Dev Services configuration per ADR-0006.

## Data model (ADR-0001, ADR-0004, ADR-0005, ADR-0007; design.md section 1)

- [ ] Implement the `customer_records` document model and repository, including the "current version = highest version per id" query and the version-N+1 merge computation (ADR-0004).
- [ ] Implement the `staging_entries` document model and repository, with the fixed field set `jobId`, `rowId`, `rowData`, `errorDescription`, `processedAt` (ADR-0005).
- [ ] Implement the `import_jobs` document model and repository, including `idsInFile` and `summary` fields (design.md section 1.3).
- [ ] Implement the `job_configuration` document model and repository, including `valueType`-aware write-time validation (ADR-0007).
- [ ] Implement a startup migration seeding the initial `chunkSize` configuration entry (spec: job-configuration).

## Import capability (spec: import)

- [ ] Implement `POST /api/v1/imports`: validate file path readability, persist the job record, compute `idsInFile`, submit to the managed executor, return `jobId` immediately (design.md section 3, steps 1-2).
- [ ] Implement the id-set intersection serialization gate against running/queued-ahead jobs, preserving arrival order among intersecting jobs (ADR-0003).
- [ ] Implement chunked row processing reading the current `chunkSize` configuration value per job start (design.md section 3, step 4).
- [ ] Implement header-driven row parsing (no fixed column position assumption).
- [ ] Implement email validation (simplified RFC 5322: `user@domain.tld`) and age validation (integer 0-120 inclusive).
- [ ] Implement unknown-header-column detection and staging.
- [ ] Implement invalid-field-value staging (email, age).
- [ ] Implement successful-row persistence into `customer_records` via the ADR-0004 merge rule.
- [ ] Implement job status transitions (`PENDING` → `RUNNING` → `COMPLETED`/`FAILED`) and final summary computation.

## Job status capability (spec: job-status)

- [ ] Implement `GET /api/v1/imports/{jobId}` returning the numeric summary and the full staging entry list for that job.

## Export capability (spec: export)

- [ ] Implement `POST /api/v1/exports`: validate requested columns against the recognized schema, returning HTTP 400 naming the first/any invalid column on failure.
- [ ] Implement the "current version per distinct id" query feeding the export projection.
- [ ] Implement CSV and TXT writers honoring exact requested column order.
- [ ] Implement XLSX (Office Open XML) writer honoring exact requested column order.
- [ ] Reject the legacy XLS format explicitly (unsupported-format error, not a silent fallback).

## Job configuration capability (spec: job-configuration)

- [ ] Implement `GET /api/v1/job-configurations`, `GET /{key}`, `POST`, `PUT /{key}`, `DELETE /{key}` per design.md section 2.
- [ ] Implement `valueType`-consistent parsing on both write (validation) and read (typed consumption by the chunking logic).

## Authentication capability (spec: authentication)

- [ ] Configure `quarkus-oidc` as a resource server protecting all `/api/v1/**` endpoints.
- [ ] Configure Quarkus Dev Services for Keycloak with a realm/client supporting the `client_credentials` grant for local/dev/test runs.
- [ ] Confirm (and document, for the Technical Writer) whether the Dev-Services-managed realm's client issues a refresh token for the Client Credentials grant by default, or whether explicit realm/client configuration is needed (ADR-0006, Consequences).

## Cross-cutting

- [ ] Integration tests covering every scenario in `docs/requirements/acceptance-criteria.feature` and every scenario in each `specs/<capability>/spec.md` file under this change.
- [ ] Unit tests for the id-intersection gate (ADR-0003), the version-merge computation (ADR-0004), and the email/age validators.
- [ ] Confirm `./gradlew build` and `./gradlew test` pass, including Dev-Services-dependent tests (requires Docker available in the environment/CI — a DevOps/Platform Engineer precondition, see ADR-0006 Confirmation).

## Documentation handoff

- [ ] Technical Writer: document the OAuth2 token-acquisition flow, the job configuration API, and the staging-entry model for API consumers, based on this design and its ADRs (not invented independently).
- [ ] DevOps/Platform Engineer: confirm CI runners have Docker available for Dev Services-backed tests, and document the local prerequisite (Docker) for running `quarkusDev`/`test`.
