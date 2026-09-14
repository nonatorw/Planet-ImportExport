# Design: File Import and Export Service

Governing ADRs: `docs/adr/ADR-0001` (storage), `ADR-0002` (async mechanism), `ADR-0003` (id-intersection serialization), `ADR-0004` (versioning/merge), `ADR-0005` (staging), `ADR-0006` (OAuth2/Keycloak), `ADR-0007` (job configuration value shape).

## 1. Data model (MongoDB, embedded in-memory via Flapdoodle)

### 1.1 `customer_records` collection — one document per version (ADR-0001, ADR-0004)

```jsonc
{
  "_id": ObjectId,
  "id": "1",                 // business identity key from the source CSV "id" column (string, to tolerate any source format)
  "version": 2,               // monotonically increasing per business id, starting at 1
  "fields": {                 // schema-flexible: only known fields are populated from confirmed schema
    "name": "John Smith",
    "email": "john@example.com",
    "age": 35,
    "country": "Portugal",
    "phone": "+351910000000"
  },
  "sourceJobId": "job-abc123", // the import job that created this version
  "createdAt": "2026-09-14T10:00:00Z"
}
```

- Unique compound index on `(id, version)`. Query for "current version" = highest `version` for a given `id` (index on `(id, version desc)`).
- `fields` holds only keys from the recognized schema (`name`, `email`, `age`, `country`, `phone`); unrecognized columns never reach this collection (they are staged instead, see 1.2).
- Version N+1 is computed by the import worker as: `fields(N+1) = fields(N) overridden by any recognized fields present in the new row`.

### 1.2 `staging_entries` collection (ADR-0005)

```jsonc
{
  "_id": ObjectId,
  "jobId": "job-abc123",
  "rowId": 5,                          // 1-based row position within the source file (header excluded)
  "rowData": { "id": "5", "name": "Marco Rossi", "age": "thirty", "...": "..." }, // raw parsed row, verbatim
  "errorDescription": "invalid age value: 'thirty' is not an integer in range 0-120",
  "processedAt": "2026-09-14T10:00:03Z"
}
```

- Index on `jobId` (status queries fetch all staging entries for a job).
- Both invalid-value and unknown-column problems share this collection; only `errorDescription` text distinguishes them (ADR-0005).

### 1.3 `import_jobs` collection

```jsonc
{
  "_id": "job-abc123",           // jobId, exposed externally
  "filePath": "/data/imports/customers_02.csv",
  "status": "PENDING | RUNNING | COMPLETED | FAILED",
  "submittedAt": "2026-09-14T10:00:00Z",
  "startedAt": "2026-09-14T10:00:01Z",
  "completedAt": "2026-09-14T10:00:05Z",
  "idsInFile": ["1", "4"],        // id-set computed up front, used by the ADR-0003 serialization gate
  "summary": {
    "totalRows": 3,
    "succeeded": 2,
    "failed": 1
  }
}
```

- `idsInFile` is populated by an initial lightweight pass over the `id` column before chunked processing starts (ADR-0003).
- `status` transitions: `PENDING` (persisted, not yet dispatched or waiting on id-intersection) → `RUNNING` (executing chunks) → `COMPLETED`/`FAILED`.

### 1.4 `job_configuration` collection (ADR-0007)

```jsonc
{
  "_id": "chunkSize",             // key doubles as document id for direct lookup
  "value": "500",
  "valueType": "INTEGER",         // STRING | INTEGER | BOOLEAN
  "description": "Number of rows processed per batch during async import",
  "updatedAt": "2026-09-14T09:00:00Z"
}
```

- Seeded at startup by a migration (Mongock or an idempotent Quarkus startup event — implementation detail for the Java Full-stack Engineer) with `chunkSize = 500` (default value is illustrative; not fixed by ADR-0007).

## 2. REST API surface

All endpoints below are OIDC-protected resource-server endpoints (ADR-0006) except the Keycloak-issued token endpoint itself, which is exposed by Keycloak (via Dev Services), not by this application.

| Method | Path | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| `POST` | `/api/v1/imports` | Submit an import job | `{ "filePath": "/data/imports/customers_02.csv" }` | `202 Accepted`, `{ "jobId": "job-abc123" }` |
| `GET` | `/api/v1/imports/{jobId}` | Query job status | — | `200 OK`, `{ "jobId", "status", "summary": {"totalRows","succeeded","failed"}, "stagingErrors": [ {"rowId","rowData","errorDescription","processedAt"} ] }` |
| `POST` | `/api/v1/exports` | Export stored data | `{ "format": "CSV\|TXT\|XLSX", "columns": ["id","name","email","country"] }` | `200 OK`, body = file content in requested format; `400 Bad Request` with `{ "error": "unknown column", "column": "loyalty_tier" }` on unrecognized column |
| `GET` | `/api/v1/job-configurations` | List all job configuration entries | — | `200 OK`, array of config entries |
| `GET` | `/api/v1/job-configurations/{key}` | Read one entry | — | `200 OK`, config entry, or `404` |
| `POST` | `/api/v1/job-configurations` | Create a new entry | `{ "key", "value", "valueType", "description" }` | `201 Created` |
| `PUT` | `/api/v1/job-configurations/{key}` | Update an entry | `{ "value", "valueType"?, "description"? }` | `200 OK` |
| `DELETE` | `/api/v1/job-configurations/{key}` | Delete an entry | — | `204 No Content` |

Token issuance (ADR-0006): the client obtains an access token (and, if the Dev-Services-managed Keycloak realm is configured to issue one, a refresh token) directly from Keycloak's own `/realms/{realm}/protocol/openid-connect/token` endpoint using `grant_type=client_credentials`. The application does not implement its own token endpoint; it only validates bearer tokens issued by that realm (`quarkus-oidc` resource-server mode).

## 3. Import processing flow (ties ADR-0002 + ADR-0003 + ADR-0004 + ADR-0005 together)

1. `POST /api/v1/imports` validates the file path is readable, persists an `import_jobs` document with `status = PENDING`, performs the lightweight `id`-column pass to populate `idsInFile`, and returns `jobId` (HTTP 202) immediately.
2. The endpoint submits a processing task to the managed executor (ADR-0002).
3. Before the task body runs, it acquires the ADR-0003 serialization gate: check `idsInFile` against every currently `RUNNING` (and already-queued-ahead) job's `idsInFile`; if any intersection exists, wait for those jobs to reach `COMPLETED`/`FAILED` before proceeding, preserving arrival order among mutually-intersecting jobs.
4. Once cleared to run, the task sets `status = RUNNING`, `startedAt = now`, then reads the file in batches sized by the current `chunkSize` job-configuration value (re-read per job start, not cached indefinitely, so a runtime configuration change takes effect on the next job).
5. For each row in a batch: validate recognized-schema fields (email pattern, age range — confirmed decisions 13/14) and header columns (reject any column outside `id, name, email, age, country, phone`); on success, upsert the next version of `customer_records` for that row's `id` per ADR-0004's merge rule; on failure, insert a `staging_entries` document per ADR-0005.
6. After all batches complete, set `status = COMPLETED`, `completedAt = now`, and the final `summary` counts.
7. `GET /api/v1/imports/{jobId}` reads the `import_jobs` document plus all matching `staging_entries` and returns them together.

## 4. Export flow

1. `POST /api/v1/exports` validates every requested column against the recognized schema (`id, name, email, age, country, phone`); on any unrecognized column, return `400` naming it (decision 4) without touching storage.
2. On success, query the current (highest-version) document per distinct `id` in `customer_records`, project only the requested fields, and order columns exactly as requested.
3. Serialize the result according to the requested format: CSV/TXT via a straightforward delimited writer (library choice is an implementation-time decision, per the PDF's own "free to choose libraries" framing — not fixed by this design), XLSX via a POI-based (or equivalent) writer producing valid Office Open XML.

## 5. Authentication integration (ADR-0006)

- `quarkus-oidc` extension configured as a resource server; all `/api/v1/**` endpoints require a valid bearer token (`@Authenticated` / `SecurityIdentity` or `@RolesAllowed` as appropriate — exact role/scope model is an implementation detail left to the Java Full-stack Engineer unless the product owner specifies role-based restrictions beyond "authenticated client").
- Quarkus Dev Services for Keycloak auto-provisions a Keycloak container plus a preconfigured realm/client for `quarkusDev` and `@QuarkusTest` runs; Docker must be running (functional-specification.md, Context Mapping — External dependencies).
- No custom token endpoint is implemented by this application (ADR-0006 rules this out explicitly).

## 6. Concurrency model summary (ADR-0002 + ADR-0003)

- Dispatch: process-local managed executor/thread pool, submitted synchronously on job acceptance (ADR-0002). No message broker, no scheduler/polling loop.
- Serialization: whole-job granularity gated by id-set intersection against in-flight/queued-ahead jobs (ADR-0003), not per-row/per-id locking.
- Both decisions are process-local; multi-instance/horizontal scale-out is explicitly out of scope (no such requirement exists; Quality Attribute Discovery rates Performance/Scalability Low priority).

## Alternatives considered

See each governing ADR's "Considered Options" / "Pros and Cons" sections for the alternatives evaluated and rejected (H2 relational and EAV storage; SmallRye Reactive Messaging and Scheduler-based dispatch; per-row locking; separate staging structures per error category; a hand-built OAuth2 server or a statically-configured external Keycloak; pure free-form or fully polymorphic job-configuration value shapes).

## Traceability

Every capability in this design traces to a confirmed decision or ADR — see the "Traceability to source requirements" table in `docs/requirements/functional-specification.md` and the ADR set in `docs/adr/`. No requirement or behavior in this design was invented beyond those sources.
