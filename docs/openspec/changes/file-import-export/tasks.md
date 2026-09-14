# Tasks: File Import and Export Service

Checklist ordered by dependency, with an added **Parallel** group per task. Each task references the governing ADR(s) and/or spec delta. This checklist is handed off to the Java Full-stack Engineer for implementation and the DevOps/Platform Engineer for build/CI concerns; the Solutions Architect does not implement these tasks.

Group convention: tasks sharing the same group id are done by the **same** agent/developer, in sequence (they share files and/or are ordered by data dependency within the group). Tasks in different groups with no dependency edge between them (see "Execution instructions" below) may be delegated to **different** agents in parallel. This document maps technical dependencies only; it does not decide how many agents to run at once or how to schedule them — that is an orchestration decision for `plinth-tech-lead`/the user.

## Project scaffolding — Group A0

- [ ] `A0.1` Scaffold the Gradle/Quarkus project skeleton (`src/main/java`, `src/main/resources`, `src/test/java`) per `AGENTS.md` (Java 25, Gradle, Quarkus).
- [ ] `A0.2` Add MongoDB client extension (`quarkus-mongodb-client` or `quarkus-mongodb-panache`) and embedded/in-memory Flapdoodle test-and-runtime support per ADR-0001.
- [ ] `A0.3` Add `quarkus-oidc` extension and Keycloak Dev Services configuration per ADR-0006.

## Data model (ADR-0001, ADR-0004, ADR-0005, ADR-0007; design.md section 1)

- [ ] `A1` Implement the `customer_records` document model and repository, including the "current version = highest version per id" query and the version-N+1 merge computation (ADR-0004).
- [ ] `A2` Implement the `staging_entries` document model and repository, with the fixed field set `jobId`, `rowId`, `rowData`, `errorDescription`, `processedAt` (ADR-0005).
- [ ] `A3` Implement the `import_jobs` document model and repository, including `idsInFile` and `summary` fields (design.md section 1.3).
- [ ] `A4.1` Implement the `job_configuration` document model and repository, including `valueType`-aware write-time validation (ADR-0007).
- [ ] `A4.2` Implement a startup migration seeding the initial `chunkSize` configuration entry (spec: job-configuration).

## Import capability (spec: import) — Group B

- [ ] `B1` Implement `POST /api/v1/imports`: validate file path readability, persist the job record, compute `idsInFile`, submit to the managed executor, return `jobId` immediately (design.md section 3, steps 1-2).
- [ ] `B2` Implement the id-set intersection serialization gate against running/queued-ahead jobs, preserving arrival order among intersecting jobs (ADR-0003).
- [ ] `B3` Implement chunked row processing reading the current `chunkSize` configuration value per job start (design.md section 3, step 4).
- [ ] `B4` Implement header-driven row parsing (no fixed column position assumption).
- [ ] `B5` Implement email validation (simplified RFC 5322: `user@domain.tld`) and age validation (integer 0-120 inclusive).
- [ ] `B5.1` Implement missing-field-value detection: an empty value under a recognized header column (`id`, `name`, `email`, `age`, `country`, `phone`) is treated as missing, distinct from an invalid-but-present value (`B5`) and from an unknown header column (`B6`).
- [ ] `B6` Implement unknown-header-column detection and staging.
- [ ] `B7` Implement invalid-field-value staging (email, age) and missing-field-value staging (empty value in a recognized column), each with an error description distinguishing "missing" from "invalid".
- [ ] `B8` Implement successful-row persistence into `customer_records` via the ADR-0004 merge rule.
- [ ] `B9` Implement job status transitions (`PENDING` → `RUNNING` → `COMPLETED`/`FAILED`) and final summary computation.

## Job status capability (spec: job-status) — Group B (same resource file as import)

- [ ] `B10` Implement `GET /api/v1/imports/{jobId}` returning the numeric summary and the full staging entry list for that job.

## Export capability (spec: export) — Group C

- [ ] `C1` Implement `POST /api/v1/exports`: validate requested columns against the recognized schema, returning HTTP 400 naming the first/any invalid column on failure.
- [ ] `C2` Implement the "current version per distinct id" query feeding the export projection.
- [ ] `C3` Implement CSV and TXT writers honoring exact requested column order.
- [ ] `C4` Implement XLSX (Office Open XML) writer honoring exact requested column order.
- [ ] `C5` Reject the legacy XLS format explicitly (unsupported-format error, not a silent fallback).

## Job configuration capability (spec: job-configuration) — Group D

- [ ] `D1` Implement `GET /api/v1/job-configurations`, `GET /{key}`, `POST`, `PUT /{key}`, `DELETE /{key}` per design.md section 2.
- [ ] `D2` Implement `valueType`-consistent parsing on both write (validation) and read (typed consumption by the chunking logic).

## Authentication capability (spec: authentication) — Group E

- [ ] `E1` Configure `quarkus-oidc` as a resource server protecting all `/api/v1/**` endpoints.
- [ ] `E2` Configure Quarkus Dev Services for Keycloak with a realm/client supporting the `client_credentials` grant for local/dev/test runs.
- [ ] `E3` Confirm (and document, for the Technical Writer) whether the Dev-Services-managed realm's client issues a refresh token for the Client Credentials grant by default, or whether explicit realm/client configuration is needed (ADR-0006, Consequences).

## Cross-cutting — Group F

- [ ] `F1` Integration tests covering every scenario in `docs/requirements/acceptance-criteria.feature` and every scenario in each `specs/<capability>/spec.md` file under this change.
- [ ] `F2` Unit tests for the id-intersection gate (ADR-0003), the version-merge computation (ADR-0004), and the email/age validators.
- [ ] `F3` Confirm `./gradlew build` and `./gradlew test` pass, including Dev-Services-dependent tests (requires Docker available in the environment/CI — a DevOps/Platform Engineer precondition, see ADR-0006 Confirmation).

## Documentation handoff — Group G

- [ ] `G1` Technical Writer: document the OAuth2 token-acquisition flow, the job configuration API, and the staging-entry model for API consumers, based on this design and its ADRs (not invented independently).
- [ ] `G2` DevOps/Platform Engineer: confirm CI runners have Docker available for Dev Services-backed tests, and document the local prerequisite (Docker) for running `quarkusDev`/`test`.

## Execution instructions (dependency map for `plinth-tech-lead`)

**Serialized prerequisite:**

- `A0` (scaffolding) has no upstream dependency and must complete and be verified before every other group starts — there is no project skeleton, MongoDB extension, or OIDC extension for any other group to build on.

**Data model — internal parallelism within Group A:**

- `A1` (`customer_records`), `A2` (`staging_entries`), `A3` (`import_jobs`), and `A4.1`+`A4.2` (`job_configuration` + its seed migration) each touch a distinct MongoDB collection, a distinct document model class, and a distinct repository class, per design.md section 1. Cross-checked against design.md: no sub-task reads or writes another sub-task's collection, and none is referenced by another's repository at the data-model layer (the *consumers* of these repositories — Import, Export, Job Configuration — are downstream capabilities, not part of Group A itself). `A1`–`A4` may run in parallel with each other, each as its own sub-group, once `A0` is complete and verified. `A4.1` must precede `A4.2` (the migration seeds a document shaped by the model `A4.1` defines) — same developer/agent, in sequence.
- All of `A1`–`A4` must complete and be verified before any of Groups B, C, D starts, since each of those groups' first task reads/writes one of these repositories.

**Business capabilities — parallel across Groups B, C, D, subject to the caveat below:**

- `B` (Import + Job Status) requires `A1`, `A2`, `A3` complete and verified (writes `customer_records` and `staging_entries`, reads/writes `import_jobs`). Does not require `A4` (job configuration) to be *complete*, only that the `job_configuration` repository contract exists — see caveat.
- `B10` (Job Status `GET /api/v1/imports/{jobId}`) is listed as its own capability in `specs/job-status/spec.md`, but design.md section 2 shows it sharing the same path base (`/api/v1/imports`) as `B1` (`POST /api/v1/imports`). In a JAX-RS/Quarkus resource class, these two endpoints are almost always the same `@Path("/api/v1/imports")` resource file. Treat `B10` as part of Group B, done by the same developer/agent as `B1`–`B9`, in sequence after `B9` (it reads the summary/status fields `B9` finalizes) — do **not** delegate `B10` to a separate agent in parallel with the rest of Group B; that would very likely produce a merge conflict on the same resource class.
- `C` (Export) requires only `A1` (`customer_records`) complete and verified. It does not read or write `staging_entries`, `import_jobs`, or `job_configuration`, and its REST path (`/api/v1/exports`) is a distinct resource file from Import's. `C` may run fully in parallel with `B` and `D`.
- `D` (Job Configuration) requires `A4.1`+`A4.2` complete and verified. Its REST path (`/api/v1/job-configurations`) is a distinct resource file from Import, Job Status, and Export. `D` may run fully in parallel with `B` and `C`.
- **Caveat — shared recognized-schema constant:** Import (`B4`, `B6`) and Export (`C1`) both validate against the identical recognized column/field set `id, name, email, age, country, phone` (design.md sections 3 and 4). Nothing in the design mandates a single shared constant/class for this list — each capability could legitimately define its own — but if the Full-stack Engineer factors it into one shared file (e.g., a `RecognizedSchema` enum/constant class), that file becomes a real point of contention between Groups B and C. Flag this explicitly to whichever agents take B and C: either (a) each group defines its own local copy of the column list to avoid any shared file, or (b) if a shared constant class is introduced, it must be created once (ideally as part of `A0` or a preceding shared step) before B and C both start, not concurrently by both. Do not assume this is automatically conflict-free.
- **Caveat — job configuration read from Import:** `B3` (chunked processing reads the current `chunkSize` value) reads the `job_configuration` repository built in `A4.1`, and `D2` (typed read/write consistency) also touches that same repository's read path. This is a shared-repository read dependency, not a shared-file-write conflict — both B and D only need `A4` complete first; B and D do not need to coordinate with each other, since neither modifies the other's files. No serialization needed between B and D beyond both depending on `A4`.

**Authentication — fixed sequencing (decided): after B, C, and D**

- `E1` (protecting `/api/v1/**`) is transversal: it wraps endpoints that Groups B, C, D create. This is now a **decided** ordering, not an open choice: `E1` (and, by extension, the rest of Group E) starts only **after** Groups B, C, and D have all completed and been verified — Authentication is applied on top of the already-implemented business endpoints (Import + Job Status, Export, Job Configuration), not built ahead of them.
- Rationale for the fixed order: `E1` typically touches `application.properties`/security-annotation additions (e.g. `@Authenticated`) on resource classes that B/C/D create — applying it after those classes exist avoids any double-edit race on the same file between a B/C/D agent finishing business logic and an E agent adding security annotations concurrently. Building E first was technically valid but is no longer the chosen path.
- `E2` (Keycloak Dev Services realm/client configuration) and `E3` (refresh-token confirmation/documentation) only touch Keycloak Dev Services configuration and documentation notes, with no file overlap with B/C/D. They are still constrained only by `A0.3`, but for consistency and to keep Group E as a single unit handed to one agent in sequence, run all of `E1`-`E3` after B, C, and D — do not start any part of Group E early.
- Updated dependency: Group E requires **all of B, C, and D complete and verified** (in addition to `A0.3`, already satisfied by that point). No part of Group E may start concurrently with B, C, or D.

**Cross-cutting — Group F:**

- `F1`, `F2`, `F3` require Groups A, B, C, D, and E (Authentication) to be complete and verified — integration tests exercise the full acceptance-criteria feature file across every capability, including authenticated access, and the build/test gate (`F3`) is meaningless before all code exists. No parallelism opportunity across F1-F3 is asserted here beyond what the Full-stack Engineer/DevOps agent can internally sequence; F1 and F2 touch the test tree broadly enough (cross-capability integration scenarios) that assigning them to different agents concurrently risks overlapping test-fixture/setup files — treat `F1`+`F2` as one sequential group unless whoever executes them confirms no shared test-support file is touched by both.

**Documentation handoff — Group G:**

- `G1` (Technical Writer) and `G2` (DevOps/Platform Engineer) both require the full implementation (Groups A-F) complete, since both document what was actually built, not what was designed. `G1` and `G2` touch entirely different documentation artifacts (API/OAuth/staging-model docs vs. CI/Docker prerequisite docs) with no file overlap between them, so they may run in parallel with each other once Groups A-F are done.

### Summary of parallelizable groups

| Can run in parallel | Condition |
| --- | --- |
| `A1`, `A2`, `A3`, `A4.1`→`A4.2` | After `A0` verified. Independent collections/repositories per design.md section 1. |
| `B`, `C`, `D` | After their respective data-model prerequisites verified (`B`: A1+A2+A3; `C`: A1; `D`: A4). Subject to the shared recognized-schema-constant caveat above. |
| `G1`, `G2` | After Groups A-F complete. |

Note: `E1`-`E3` (Group E, Authentication) are **not** listed here as parallelizable with anything — the decided execution order runs Group E as one sequential unit only after B, C, and D are all complete and verified. See the fixed dependency below.

### Summary of mandatory serialization (not parallelizable, despite appearing separable)

| Serialized pair/set | Reason |
| --- | --- |
| `A0` before everything | No skeleton/extensions to build on. |
| `A1`-`A4` before `B`/`C`/`D` | Each capability group's first task reads/writes a Group-A repository. |
| `A4.1` before `A4.2` | Migration seeds a document shaped by the model task defines. |
| `B1`...`B9` before `B10` | Same likely resource file (`/api/v1/imports` path base); `B10` also reads fields `B9` finalizes. |
| `B`, `C`, `D` (all three, complete and verified) before `E1`-`E3` | Decided execution order: Authentication is applied on top of the already-implemented business endpoints, not built ahead of or alongside them — avoids double-edit races on the same resource classes. |
| Groups A-F before `G1`/`G2` | Documentation describes what was actually implemented. |
| `F1`+`F2` treated as one sequential unit | Broad cross-capability integration/unit test surface risks shared test-fixture file conflicts; confirm before splitting. |
