# Functional Specification — File Import and Export

**Source material:** `docs/requirements/Senior Software Engineer Exercise.pdf` ("Senior Software Engineer Exercise — File Import and Export"), read in full (2 pages). This is the primary source. Where the PDF left a decision open, the user (product owner for this exercise) has since made an explicit, confirmed decision; such decisions are recorded below as **Confirmed decisions**, not as PDF text, and are binding requirements from this point forward. No business stakeholders, SLAs, or production context exist otherwise; this is an isolated technical assessment (candidate/implementing agents vs. technical evaluators/reviewers).

---

## 1. Problem Framing

**Problem statement**
A backend service must receive CSV data files from an external system, persist the contained records into storage, and later allow users or other systems to export the stored data in multiple formats (CSV, TXT, XLS/XLSX), with the caller controlling which columns are exported and in what order.

**Current state**
No service exists. The repository (`planet-ImportExport`) has no `src/` and no build files yet — only the exercise PDF and repository scaffolding conventions (`AGENTS.md`) are present. The target stack is fixed by the repository: Java 25, Gradle, Quarkus, MongoDB (embedded in-memory via Flapdoodle).

**Desired state**
A working backend service, exposed via a REST API (confirmed decision — see Assumption Analysis), that:

- Imports a single CSV file per call, asynchronously: the import call accepts a file reference and returns a `jobId` immediately; a background process performs the actual processing in batches (chunks).
- Lets the caller query job status afterwards, receiving a numeric summary (rows processed, succeeded, failed) plus the detailed list of staging errors for that job.
- Routes any row with an invalid field value or an unrecognized header column to a generic staging table (not rejected, not silently dropped), distinguished only by an error description.
- Applies an explicit, documented versioning policy for records/data received more than once across files (new version per re-received `id`, full history retained).
- Exports stored data as CSV, TXT, and XLSX, with caller-specified column selection and ordering honored exactly, rejecting unrecognized requested columns with an explicit error.
- Protects the REST API with OAuth2 Client Credentials, including token issuance endpoints.
- Exposes a CRUD API to manage job configuration (e.g., chunk size), generalized for future job settings.

**Stakeholders**

- Candidate / implementing agents (solutions-architect, java-fullstack-engineer, and related delivery agents) — build the solution.
- Technical evaluators / reviewers — assess the solution against the PDF's requirements and the candidate's documented design choices.

No end users, external system owners, or business sponsors exist as real stakeholders for this exercise; the "external system" and "users/other systems" mentioned in the PDF are simulated roles for scoping purposes only, not real integration partners.

**Success criteria** (objective and verifiable only)

- All six numbered requirement areas in the PDF (Import, Input format, Multiple imports, Storage, Export, Interface) are addressed by the implementation, now governed by the confirmed decisions recorded in this specification rather than left as open policy questions.
- Automated tests (unit/integration) pass via `./gradlew test`.
- The application builds and runs locally via the documented Gradle commands (`./gradlew build`, `./gradlew quarkusDev`).
- Import and export behavior is demonstrable using the exact sample files given in the PDF (`customers_01.csv`, `customers_02.csv`, `customers_03.csv`) and the two sample export requests given in the PDF, under the asynchronous job flow and OAuth2-protected API confirmed below.
- All design choices previously flagged as open (the customer record storage model, see Assumption Analysis decision 16) are now governed by confirmed decisions; no pending architectural question remains open in this specification.

No business KPIs, SLAs, throughput targets, or uptime commitments are defined by the source material; none are assumed.

---

## 2. Root Cause Analysis

This lens is not applicable in its usual "symptom vs. underlying defect" sense: there is no existing broken system or incident to trace back. Reframed for a greenfield build, the relevant question is *why does this problem exist / why is it non-trivial*, per the PDF's own framing:

- **Why import is non-trivial:** the external system's CSV files are not uniform. The three sample files show different column sets and column orders (`customers_01.csv` has 5 columns without `phone`; `customers_02.csv` adds `phone` as a 6th column; `customers_03.csv` reorders columns and places `phone` 3rd). The root cause of import complexity is schema drift across files from the same external source, which the service must tolerate by reading each file according to its own header row rather than assuming a fixed column order (PDF: "must be processed according to the information they contain").
- **Why data quality handling is non-trivial:** the sample data intentionally contains missing values (`customers_02.csv` row for "Ana Costa" has an empty `age`), an invalid/unparseable value (`customers_03.csv` row for "Marco Rossi" has `age = "thirty"`, non-numeric), and a malformed email (`marco@example`, missing a top-level domain). The root cause is that the external system does not guarantee clean data, so the service must classify each field/record outcome explicitly rather than fail silently or crash the whole import.
- **Why multiple-import handling is non-trivial:** the same logical entity (`id = 1`, "John Smith") appears identically in all three files, and `id = 4` ("Ana Costa") / `id = 5` ("Marco Rossi") appear in only one file each with partial data. The root cause is that the external system sends overlapping, cumulative, or corrective batches rather than one authoritative full snapshot, so the service must define an explicit handling policy keyed on identity. This is now resolved by a confirmed decision: a versioning policy (see Assumption Analysis) — a new record version is created on each re-received `id`, merging incoming fields onto the prior version's snapshot and preserving full history.

No further Five Whys / Fishbone / constraint-tree decomposition is warranted beyond this: the PDF supplies the causal picture directly through its example data, and inventing deeper organizational or technical root causes beyond what the sample illustrates would not be supported by the source material.

---

## 3. Assumption Analysis

**Assumptions** (reasonable readings of the PDF, to be honored unless the PDF is contradicted)

- The identity key for a customer record is the `id` column, since it is the only column present and consistent across all three sample files and is the natural candidate key for detecting "the same data received more than once" (Requirement 3).
- The candidate fields observed across all sample files form the full known schema for this exercise: `id`, `name`, `email`, `age`, `country`, `phone`. No field outside this set appears in the PDF. (The storage model for this schema — fixed relational columns vs. a flexible document — is now settled by confirmed decision 16 below.)
- The fixed technology stack (Java 25, Gradle, Quarkus, MongoDB embedded in-memory via Flapdoodle) comes from `AGENTS.md`, not from the PDF, which explicitly leaves technology choice free ("You are free to choose the technologies, libraries, storage solution and architecture"). Both are honored: the stack is a project-level constraint layered on top of the PDF's open choice, not a contradiction of it.

**Confirmed decisions** (made explicitly by the user/product owner; these are binding requirements, not open policy — they supersede any prior assumption, `[POLICY]` marker, or `@unresolved` marker on the same topic)

1. **Duplicate/repeated data — versioning policy (Requirement 3).** When the same `id` is received again, a new version of the record is created; full history is retained (no overwrite, no silent merge, no rejection). Each new version is a complete snapshot: fields present in the new file are updated; fields absent from the new file are carried forward/inherited from the immediately preceding version (a merge onto history, not an isolated raw snapshot). This also resolves the previously open "conflicting value in the same field across versions" case: the new version simply records the new value for that field; the old value is preserved only in the prior version (history), with no separate conflict-resolution rule needed.
2. **Invalid/missing field and unknown header column — staging model (Requirement 2).** A row with a problem (an invalid field value, or an unknown column in the header) is neither rejected nor routed to a separate quarantine structure — it is recorded in one generic staging table with columns: `jobId`, `rowId` (the row's identifier within the source file), `rowData` (the raw source row), `errorDescription` (free text describing the problem), `processedAt` (timestamp). Both "invalid field value" (e.g., `age = "thirty"`) and "unknown header column" (e.g., a `loyalty_tier` column outside the schema) go to this same staging table, distinguished only by the `errorDescription` text. The user has flagged that this single-table grouping may be revisited later, but it is the governing decision for now. `jobId` in staging is exactly the identifier of the asynchronous import job the row belongs to.
3. **Import flow — asynchronous with staging.** Import is asynchronous: the initial REST call accepts a file reference (see decision 8) and returns a `jobId` immediately; processing happens in the background. The caller later queries a status endpoint for that `jobId` and receives (a) a numeric summary (total rows processed, total succeeded, total failed) and (b) the detailed list of staging errors for that job. There is no synchronous response carrying the error list at import time — that information exists only via the later status query.
4. **Export with unknown requested column (Requirement 5).** If an export request names a column outside the recognized schema, the API returns an explicit HTTP 400 error identifying the invalid column. Silently ignoring it or emitting it empty are both explicitly rejected behaviors.
5. **Interface (Requirement 6).** Only a REST API is implemented (no CLI). The PDF's "REST API or CLI" choice is resolved in favor of REST API.
6. **Spreadsheet format (Requirement 5).** Only XLSX (modern Office Open XML) is supported. Legacy binary XLS is out of scope.
7. **Concurrency.** Handled explicitly via a per-`id` serialized queue: import jobs touching the same `id` are processed strictly in arrival order, one at a time, to avoid race conditions in version creation. Jobs touching different `id`s may run in parallel.
8. **Import file delivery.** The file is neither uploaded as multipart nor fetched from a remote URL. The REST API accepts only a reference (path) to a file already available on the local/mounted filesystem, directly accessible to the application. This is the only supported delivery mechanism in this version (no S3, no HTTP URL support at this time).
9. **File cardinality per call.** Exactly one CSV file per import call (no multi-file requests).
10. **Chunk size / batch processing.** The asynchronous job processes the file in batches (chunks). The chunk size (rows per batch) is a configuration value stored in a database table, not in `application.properties`. A database migration seeds an initial value; the value can subsequently be changed at runtime via a service (API) call, without redeploy.
11. **Job configuration CRUD API (new scope item).** A CRUD API for managing job configuration must exist, designed generically (not limited to chunk size) so it can accommodate other job settings introduced later.
12. **Authentication (new scope item, supersedes any prior "out of scope" treatment).** The REST API is protected via OAuth2 Client Credentials (machine-to-machine, no end user). An API/endpoint for issuing Access Tokens and Refresh Tokens must exist. Implementation must use a ready-made Quarkus ecosystem extension (e.g., Quarkus OIDC) rather than a hand-built OAuth2 server. This is definitively in scope.
13. **Email validation (Requirement 2).** A simplified RFC 5322 pattern (`user@domain.tld`), rejecting cases such as the PDF's own sample (`marco@example`, missing a TLD).
14. **Age validation (Requirement 2).** Must be an integer in the range 0–120 inclusive. Non-numeric values (e.g., `"thirty"`) or values outside the range are invalid and are routed to staging.
15. **File size/volume limits.** There is no maximum file size or row-count limit; files are never rejected for size. The only related control is the chunk size from decision 10, which governs batch processing, not file admission.
16. **Customer record storage model (Requirement 4 — resolves the previously open key/value schema question).** The customer record is stored as a MongoDB document per version, with native variable fields (no fixed relational schema, no EAV). Full versioning is retained: each import that changes a given `id` creates a new version document for that `id` rather than overwriting the previous one, preserving complete history; the "current version" of an `id` is the document with the highest version number. This is consistent with confirmed decision 1 (versioning with merge: fields absent from a new import inherit the value of the immediately preceding version). Because MongoDB accepts variable fields natively per document, there is no centralized schema registry to manage, and therefore no separate "schema management API" pending item — if one is needed in the future, that would be a new decision, not an inherited unknown.
17. **Unknown header column — whole-row failure scope (Requirement 2).** When a row's header contains any column outside the recognized schema (e.g., a `loyalty_tier` column), the entire row is treated as a failure and routed to staging (the same generic staging structure as decisions 2/13/14), regardless of whether the row's other fields are individually valid. The unknown column is not silently dropped/ignored while processing the rest of the row; no record document is created for that row. This behavior was already implicit in the PDF's/Gherkin's original worked example (a row with an unrecognized `loyalty_tier` column fails in full and goes to staging even though its other fields are valid) and has now been explicitly confirmed by the user.

**Validation plan**
The 17 decisions above were validated directly with the user (acting as product owner for this exercise) and are now treated as confirmed requirements, not assumptions — they must be honored as-is by downstream design and implementation. There are no remaining unknowns on this specification. Per this engagement's standing instruction, any further ambiguous point discovered while elaborating this specification is to be raised as a question to the user rather than assumed.

---

## 4. Context Mapping

**Existing systems**
None. This is a greenfield service; no pre-existing codebase, database, or running system is being extended.

**Integrations**

- **Inbound:** an "external system" that produces CSV files, which are made available on the local/mounted filesystem the application can read directly; the REST API's import call accepts a path reference to that file (confirmed decision 8) and returns a `jobId` for asynchronous processing (confirmed decision 3). Exactly one CSV file per import call (confirmed decision 9).
- **Outbound:** "users or other systems" that request exports in CSV, TXT, or XLSX, specifying columns and order via a structured REST request body (the PDF shows a JSON-shaped example: `{ "format": "CSV", "columns": [...] }`); the same caller also queries job status (numeric summary + staging error list) and calls the job-configuration CRUD API and the OAuth2 token endpoints (confirmed decisions 3, 11, 12).

**Ownership**

- Implementation and design ownership sit entirely with the candidate/implementing agents for this exercise. There is no external team, product owner, or platform team with a competing claim, except for the confirmed decisions above (including the customer record storage model, decision 16), all of which were raised and settled directly by the user acting as product owner.
- Per `AGENTS.md`, architecture-level decisions (component boundaries, storage schema, consistency/merge policy, OAuth2/Quarkus OIDC integration, job queueing) are the Solutions Architect agent's responsibility; application code and tactical design are the Java Full-stack Engineer agent's responsibility; this Functional Specification and its acceptance criteria are the Business Analyst's (this) responsibility and are read-only with respect to implementation.

**External dependencies**

- Fixed runtime dependencies from the repository conventions: Java 25, Gradle (build tool), Quarkus (application framework), MongoDB embedded in-memory via Flapdoodle (database, in-process — not a real MongoDB server via Docker/Testcontainers).
- A ready-made OAuth2/OIDC extension from the Quarkus ecosystem (e.g., Quarkus OIDC) is required for authentication (confirmed decision 12), rather than a custom-built OAuth2 server.
- Library choices for CSV parsing, XLSX generation, and REST framework wiring beyond the above are open ("free to choose... libraries") and are an implementation-time decision, not a requirement of this specification.
- No external network services, third-party APIs, or cloud infrastructure are implied by the PDF for data storage: the embedded, in-process MongoDB (Flapdoodle) keeps the database self-contained and runnable locally without external database infrastructure. The import file source is local/mounted filesystem only (confirmed decision 8) — no S3 or remote HTTP fetch in this version. This "no external infrastructure" property does **not** extend to the whole exercise, however: confirmed decision 12 (OAuth2 Client Credentials) requires a Keycloak instance provisioned via Quarkus Dev Services, which in turn requires Docker to be available. The exercise therefore is not free of external infrastructure end to end — the database is self-contained, but authentication depends on Docker/Dev Services.

---

## 5. Quality Attribute Discovery

Candidate quality attributes, derived only from what the PDF states or directly implies — prioritized as they matter to fulfilling the stated requirements and to a technical evaluator assessing the exercise:

| Quality attribute | Why it applies (PDF-grounded / confirmed decisions) | Priority |
| --- | --- | --- |
| **Security (authN/authZ)** | New scope item per confirmed decision 12: the REST API must be protected by OAuth2 Client Credentials via a Quarkus-native OIDC extension, with token/refresh-token issuance endpoints. Previously out of scope; now a high-priority, directly mandated attribute. | High |
| **Correctness / Data integrity** | Import must persist data faithfully across versions (confirmed decision 1); export must return "the requested fields, in the requested order" exactly, rejecting unknown columns explicitly (confirmed decision 4). This is the most literal, testable requirement in the PDF, now sharpened by explicit versioning and export-error rules. | High |
| **Observability of outcome (auditability)** | Requirement 1, now realized asynchronously (confirmed decision 3): the application must expose a numeric job summary and a detailed staging error list per `jobId` through a status query. | High |
| **Robustness to malformed/heterogeneous input** | Requirement 2, now realized via a single staging table for both invalid values and unknown columns (confirmed decision 2), with explicit validation rules for email (decision 13) and age (decision 14). | High |
| **Consistency policy under repeated/overlapping data** | Requirement 3, now realized as an explicit versioning policy with full history and per-`id` serialized processing to avoid race conditions (confirmed decisions 1 and 7). | High |
| **Interoperability of export formats** | Requirement 5 requires CSV, TXT, and XLSX (legacy XLS explicitly out of scope per decision 6) sharing the same column-selection contract; correctness must hold across all three. | Medium-High |
| **Configurability of operational parameters** | New scope item per confirmed decisions 10 and 11: chunk size and other job settings are stored in a database table (seeded by migration) and managed at runtime via a dedicated CRUD API, without redeploy. | Medium-High |
| **Usability of the interface contract** | Requirement 6, resolved to REST API only (confirmed decision 5); the export example shows a clear, minimal request shape the interface should mirror faithfully. | Medium |
| **Testability** | Not stated directly, but implied by Requirement 1's "provide enough information to determine" success/failure, and by repository conventions (`./gradlew test` is a documented, required command in `AGENTS.md`). | Medium |
| **Maintainability / Explainability of design choices** | The PDF explicitly asks the candidate to "explain your choices where appropriate" for technology, libraries, storage, and architecture — a direct instruction that documentation quality itself is evaluated. | Medium |
| **Performance / Scalability** | No volume, throughput, or latency figures are given. Confirmed decision 15 explicitly removes any file size/row-count limit, and decision 7 requires per-`id` serialization with cross-`id` parallelism — a concurrency-correctness concern more than a raw throughput target. Not a priority beyond that; no numeric targets are assumed or invented. | Low |

---

## Traceability to source requirements

| PDF Requirement | Section(s) addressing it | Governing confirmed decision(s) |
| --- | --- | --- |
| 1. Import | Problem Framing (Desired state), Quality Attribute Discovery (Observability) | 3 (async + status query), 8 (file path delivery), 9 (single file per call), 10 (chunk size config) |
| 2. Input format | Root Cause Analysis, Assumption Analysis (Confirmed decisions) | 2 (staging model), 13 (email validation), 14 (age validation), 17 (unknown header column — whole-row failure scope) |
| 3. Multiple imports | Root Cause Analysis, Assumption Analysis (Confirmed decisions), Quality Attribute Discovery | 1 (versioning policy), 7 (per-id serialized concurrency) |
| 4. Storage | Context Mapping (External dependencies) | 1 (versioned history), 16 (MongoDB document-per-version storage model) |
| 5. Export | Problem Framing, Assumption Analysis, Quality Attribute Discovery | 4 (HTTP 400 on unknown column), 6 (XLSX only, no legacy XLS) |
| 6. Interface | Assumption Analysis, Context Mapping (Integrations) | 5 (REST API only), 12 (OAuth2 Client Credentials), 11 (job configuration CRUD API) |
| — (new scope, not in original PDF numbering) | Assumption Analysis (Confirmed decisions 11, 12), Quality Attribute Discovery (Security, Configurability) | 11 (job configuration CRUD), 12 (OAuth2 authentication) |

---

*Prepared by: Business Analyst agent. No requirements, acceptance criteria, or behavior were invented beyond what `docs/requirements/Senior Software Engineer Exercise.pdf` states or directly implies through its worked example, and beyond the confirmed decisions the user explicitly provided for every point previously marked as an open policy question. The customer record storage model, previously an open point under Assumption Analysis, is now resolved by confirmed decision 16 (MongoDB document-per-version storage). No open point remains in this specification. Per the user's standing instruction, any further ambiguous point found while maintaining this specification is to be raised as a question rather than assumed.*
