# Sequence Diagram — Asynchronous Import Flow (with ADR-0003 Serialization Gate)

## Diagram

```mermaid
sequenceDiagram
    actor Caller as User / Other System
    participant API as Import Resource (REST API)
    participant FS as Filesystem
    participant Jobs as import_jobs (MongoDB)
    participant Gate as Serialization Gate (Executor)
    participant Reader as Chunked File Reader (Executor)
    participant Validator as Row Validator (Executor)
    participant Records as customer_records (MongoDB)
    participant Staging as staging_entries (MongoDB)
    participant Config as job_configuration (MongoDB)

    Caller ->> API: POST /api/v1/imports { filePath }
    API ->> FS: read header + id column (lightweight pass)
    FS -->> API: idsInFile
    API ->> Jobs: insert { status: PENDING, idsInFile, submittedAt }
    Jobs -->> API: jobId
    API -->> Caller: 202 Accepted { jobId }
    API ->> Gate: submit processing task (jobId)

    Note over Gate,Jobs: ADR-0003 — whole-job serialization by id-set intersection
    Gate ->> Jobs: read idsInFile of every RUNNING /<br/>queued-ahead job
    alt idsInFile intersects an in-flight/queued-ahead job
        Gate ->> Gate: wait (preserve arrival order<br/>among mutually-intersecting jobs)
        Note over Gate: released only after every<br/>intersecting job reaches<br/>COMPLETED/FAILED
    else no intersection
        Note over Gate: cleared to run immediately
    end

    Gate ->> Jobs: update { status: RUNNING, startedAt }
    Gate ->> Reader: begin chunked processing

    Reader ->> Config: read chunkSize (re-read per job start)
    Config -->> Reader: chunkSize value

    loop for each batch of chunkSize rows
        Reader ->> FS: read next batch
        loop for each row in batch
            Reader ->> Validator: validate row (schema, email, age)
            alt row valid (recognized columns only, per ADR-0004)
                Validator ->> Records: read current (highest) version for id
                Records -->> Validator: current version or none
                Validator ->> Validator: merge incoming fields onto<br/>prior version (ADR-0004)
                Validator ->> Records: insert next version document
            else invalid value or unrecognized column (ADR-0005)
                Note over Validator: unrecognized header column fails<br/>the whole row, even if other<br/>fields are valid (decision 17)
                Validator ->> Staging: insert { jobId, rowId, rowData,<br/>errorDescription, processedAt }
            end
        end
    end

    Reader ->> Jobs: update { status: COMPLETED, completedAt, summary }

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
- The Serialization Gate check happens **before** `status` transitions to `RUNNING`, and blocks only on jobs whose `idsInFile` intersects the new job's — disjoint jobs are never delayed (ADR-0003).
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
- **ADR-0003** — the gate is drawn as a distinct phase with its own `alt` block, showing both the wait path (intersection) and the immediate-start path (no intersection), and noting arrival-order release explicitly.
- **ADR-0004** — the "row valid" branch always reads the current highest version first, then computes the merge, before inserting — never an in-place update.
- **ADR-0005** — both invalid-value and unknown-column outcomes converge on the same `staging_entries` insert with the same field shape.
- **ADR-0007** — `chunkSize` is fetched from `job_configuration`, not from static application configuration, and fetched once per job start (not per batch), matching design.md's "re-read per job start, not cached indefinitely."

## Limitations / notes

- No diagram-validation tooling applies to this repository (no `src/`, no build files yet — see `docs/diagrams/c4/context.md`, Limitations). Mermaid `sequenceDiagram` syntax was validated by manual inspection (matched `alt`/`else`/`end` and `loop`/`end` blocks, valid arrow types `->>`/`-->>`, no unescaped colons inside message text outside of labels).
- The diagram shows one representative row per branch inside the innermost loop for readability; it does not enumerate every row of the PDF's sample files (that level of worked example belongs in acceptance criteria / test fixtures, not an architecture diagram).
- OAuth2 bearer-token validation on both the submission and status-query calls is omitted from this diagram for clarity and is shown separately in `docs/diagrams/sequence/oauth2-token-flow.md`.
