# Sequence Diagram — Asynchronous Import Flow (with ADR-0003 Serialization Gate)

## Diagram

```mermaid
sequenceDiagram
    actor Caller as User / Other System
    participant API as Import Resource (REST API)
    participant FS as Filesystem
    participant Jobs as import_jobs (MongoDB)
    participant Gate as Serialization Gate
    participant Orch as Import Processing Service (Executor)
    participant Reader as Chunked File Reader
    participant Validator as Row Validator
    participant Records as customer_records (MongoDB)
    participant Staging as staging_entries (MongoDB)
    participant Config as job_configuration (MongoDB)

    Caller ->> API: POST /api/v1/imports { filePath }
    API ->> FS: read header + id column (lightweight pass)
    FS -->> API: idsInFile
    API ->> Jobs: insert { status: PENDING, idsInFile, submittedAt }
    Jobs -->> API: jobId
    API ->> Gate: arrive(jobId, idsInFile)
    Note over API,Gate: arrive() runs synchronously on the request<br/>thread — this is what fixes arrival order<br/>among intersecting jobs (ADR-0003)
    API -->> Caller: 202 Accepted { jobId }
    API ->> Orch: submit processing task (jobId)

    Note over Orch,Jobs: ADR-0003 — whole-job serialization by id-set intersection
    Orch ->> Gate: awaitTurn(jobId)
    alt idsInFile intersects an earlier, still-present arrival
        Gate ->> Gate: block (preserve arrival order<br/>among mutually-intersecting jobs)
        Note over Gate: released only after every<br/>intersecting earlier arrival<br/>reaches COMPLETED/FAILED
    else no intersection
        Note over Gate: cleared to run immediately
    end

    Orch ->> Jobs: update { status: RUNNING, startedAt }
    Orch ->> Config: read chunkSize (re-read per job start)
    Config -->> Orch: chunkSize value

    loop for each batch of chunkSize rows
        Orch ->> Reader: read next batch
        Reader ->> FS: read next batch
        FS -->> Orch: rows
        loop for each row in batch
            Orch ->> Validator: validate row (schema, email, age)
            Validator -->> Orch: outcome (Success/Failure)
            alt row valid (recognized columns only, per ADR-0004)
                Orch ->> Records: insertNextVersion(row)
                Records ->> Records: read current (highest) version,<br/>merge incoming fields onto it (ADR-0004)
                Records -->> Orch: inserted
            else invalid value or unrecognized column (ADR-0005)
                Note over Validator: unrecognized header column fails<br/>the whole row, even if other<br/>fields are valid (decision 17)
                Orch ->> Staging: insert { jobId, rowId, rowData,<br/>errorDescription, processedAt }
            end
        end
    end

    Orch ->> Jobs: update { status: COMPLETED, completedAt, summary }

    opt unexpected failure during chunk processing
        Orch ->> Jobs: update { status: FAILED, completedAt,<br/>partial summary }
    end

    Note over Caller,Jobs: caller polls status independently (see below)
    Caller ->> API: GET /api/v1/imports/{jobId}
    API ->> Jobs: read job document
    API ->> Staging: read all entries for jobId
    Jobs -->> API: status, summary
    Staging -->> API: stagingErrors[]
    API -->> Caller: 200 OK { jobId, status, summary, stagingErrors }
```

## Context

This sequence diagram traces one import job end to end, following `docs/openspec/changes/file-import-export/design.md`, section 3 ("Import processing flow"), steps 1-7, with the ADR-0003 serialization gate expanded in detail as requested. It deliberately separates the synchronous request (submission, returns `202` immediately) from the asynchronous background processing (executor task) and the independent later status poll, matching confirmed decision 3 ("no synchronous response carrying the error list at import time").

Key ordering choices reflected:

- The `idsInFile` lightweight pass happens **before** the job document is persisted with a `PENDING` status, and **before** the task is submitted to the executor (design.md, section 3, step 1) — this is what makes the id-set available for the ADR-0003 gate check without re-parsing the file inside the executor.
- `Gate.arrive()` is called synchronously on the **request thread**, immediately after the job is persisted and before the `202 Accepted` response is returned — this is the actual mechanism that fixes arrival order among intersecting jobs (ADR-0003); it is a distinct, earlier call than `Gate.awaitTurn()`, which runs inside the executor task and may block.
- The Serialization Gate check (`awaitTurn`) happens **before** `status` transitions to `RUNNING`, and blocks only on jobs whose `idsInFile` intersects an earlier, still-present arrival — disjoint jobs are never delayed (ADR-0003).
- `chunkSize` is read from `job_configuration` fresh at the start of processing, not cached across jobs (design.md, section 3, step 4; ADR-0007).
- Row validation branches into exactly two outcomes per ADR-0004 (versioned merge) and ADR-0005 (generic staging) — there is no third "partial success" path, and an unrecognized header column fails the entire row (confirmed decision 17), not just the offending field.

## Sources used

- `docs/openspec/changes/file-import-export/design.md`, sections 1 (data model), 3 (import processing flow), 6 (concurrency model summary).
- `docs/adr/ADR-0002-async-import-processing-mechanism.md` — executor-based dispatch, no polling/broker.
- `docs/adr/ADR-0003-per-job-id-intersection-serialization.md` — gate mechanics, arrival-order release.
- `docs/adr/ADR-0004-record-versioning-and-merge-policy.md` — merge-onto-prior-version rule.
- `docs/adr/ADR-0005-single-generic-staging-collection.md` — staging entry shape.
- `docs/adr/ADR-0007-job-configuration-value-shape.md` — chunk size re-read per job.
- `docs/requirements/functional-specification.md` — confirmed decisions 3, 10, 13, 14, 17.

## ADRs / decisions reflected

- **ADR-0002** — dispatch is a direct executor submission immediately after job persistence; no scheduler tick or message broker appears anywhere in the flow.
- **ADR-0003** — the gate is drawn as two distinct calls (`arrive()` on the request thread, `awaitTurn()` inside the executor task), with the `alt` block on `awaitTurn()` showing both the wait path (intersection) and the immediate-start path (no intersection), and noting arrival-order release explicitly.
- **ADR-0004** — the "row valid" branch reads the current highest version and computes the merge inside the repository (`CustomerRecordRepository.insertNextVersion`), never an in-place update; the orchestrator only decides *whether* to call it, based on the validator's outcome.
- **ADR-0005** — both invalid-value and unknown-column outcomes converge on the same `staging_entries` insert with the same field shape, issued by the orchestrator after receiving a `Failure` outcome from the validator.
- **ADR-0007** — `chunkSize` is fetched from `job_configuration`, not from static application configuration, and fetched once per job start (not per batch), matching design.md's "re-read per job start, not cached indefinitely."

## Limitations / notes

- No diagram-validation tooling applies to this repository (see `docs/diagrams/c4/context.md`, Limitations). Mermaid `sequenceDiagram` syntax was validated by manual inspection (matched `alt`/`opt`/`else`/`end` and `loop`/`end` blocks, valid arrow types `->>`/`-->>`, no unescaped colons inside message text outside of labels).
- The diagram shows one representative row per branch inside the innermost loop for readability; it does not enumerate every row of the PDF's sample files (that level of worked example belongs in acceptance criteria / test fixtures, not an architecture diagram).
- OAuth2 bearer-token validation on both the submission and status-query calls is omitted from this diagram for clarity and is shown separately in `docs/diagrams/sequence/oauth2-token-flow.md`.
- "Import Processing Service (Executor)", "Chunked File Reader", and "Row Validator" correspond to `ImportProcessingService`, `CsvFileReader`, and `ImportRowValidator` respectively — the orchestrator (`ImportProcessingService`) does the actual chunk-size lookup, calls the reader/validator, and decides whether to call the repository or the staging collection based on the validator's return value; the reader and validator are stateless helpers, not independent actors that call the database themselves.
