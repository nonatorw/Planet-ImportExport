# Proposal: File Import and Export Service

## Why

The repository currently has no application code (`AGENTS.md`: "the Gradle/Quarkus skeleton has not been scaffolded yet"). The exercise (`docs/requirements/Senior Software Engineer Exercise.pdf`) requires a backend service that imports CSV files asynchronously into storage, tolerates heterogeneous/invalid input without rejecting whole files, applies an explicit versioning policy for repeated records, exports stored data in multiple formats with caller-controlled column selection, and protects all of this behind OAuth2. Every point the PDF left open has since been resolved through explicit, confirmed decisions with the product owner (`docs/requirements/functional-specification.md`, Assumption Analysis, confirmed decisions 1-16) and captured as seven ADRs (`docs/adr/ADR-0001` through `ADR-0007`). This proposal turns that approved design into an implementation-ready OpenSpec change.

## What changes

- **Import capability**: a REST endpoint accepting a local/mounted filesystem path to a single CSV file, persisting a job record, and returning a `jobId` immediately; a background executor (ADR-0002) processes the file in configurable chunks (ADR-0007-governed configuration), staging invalid rows (ADR-0005) and creating versioned customer documents (ADR-0001, ADR-0004), serialized against other jobs only when their id-sets intersect (ADR-0003).
- **Job status capability**: a REST endpoint returning a numeric summary (processed/succeeded/failed) and the detailed staging error list for a given `jobId`.
- **Export capability**: a REST endpoint accepting a format (`CSV`, `TXT`, `XLSX`) and an ordered column list, returning the current version of every stored record with exactly the requested columns in the requested order, rejecting unrecognized columns with HTTP 400.
- **Job configuration capability**: a generic CRUD REST endpoint over job settings (chunk size today, extensible later), backed by a MongoDB collection seeded via a startup migration (ADR-0007).
- **Authentication capability**: OAuth2 Client Credentials protecting every endpoint above, backed by Quarkus OIDC and a Keycloak instance provisioned through Quarkus Dev Services (ADR-0006).
- **Data model**: MongoDB collections for customer record versions, staging entries, job records, and job configuration entries, running on embedded in-memory MongoDB (Flapdoodle) with no external database infrastructure (ADR-0001).

This is a single, atomic, reviewable change: all five capabilities are part of one cohesive greenfield deliverable with no independent release timing, ownership, or rollback boundary between them (they share one codebase, one build, one deployment unit, and were approved together as one design).

## Non-goals (explicitly out of scope per confirmed decisions)

- No CLI interface (decision 5) — REST only.
- No legacy binary XLS export (decision 6) — XLSX only.
- No multipart file upload or remote URL fetch for import (decision 8) — filesystem path reference only.
- No multi-file import requests (decision 9) — exactly one CSV file per call.
- No file size or row-count admission limits (decision 15).
- No per-row/per-individual-id lock granularity (ADR-0003) — whole-job serialization by id-set intersection only.
- No hand-built OAuth2/token-signing implementation (decision 12, ADR-0006) — Quarkus OIDC + Keycloak only.
- No schema registry / schema management API (decision 16) — MongoDB's native variable fields remove the need.

## Impact

- **Affected capabilities (new)**: `import`, `job-status`, `export`, `job-configuration`, `authentication`.
- **Affected systems**: none pre-existing (greenfield). New runtime dependency: Docker, required only for Keycloak via Quarkus Dev Services (ADR-0006) — the database remains infrastructure-free (embedded MongoDB, ADR-0001).
- **Breaking changes**: none (no prior API or data model exists).

## Source artifacts and derivation

- `docs/requirements/functional-specification.md` (confirmed decisions 1-16) — requirements authority.
- `docs/requirements/acceptance-criteria.feature` — behavior/scenario authority.
- `docs/adr/ADR-0001` through `ADR-0007` — architecture decision authority for storage, concurrency, versioning, staging, authentication, and configuration shape.
- `AGENTS.md` — tech stack and repository convention authority (Java 25, Gradle, Quarkus, MongoDB embedded).

No requirement, scenario, or constraint in this proposal was invented beyond these source artifacts. Derivation direction: functional specification + acceptance criteria + ADRs → this OpenSpec change (one-way; this change does not modify those source artifacts).

## Open questions

None blocking. All architecturally significant decisions needed to design this change were either explicitly confirmed by the product owner or delegated to the Solutions Architect with no objection raised (see ADR-0002 and ADR-0007). Any further ambiguity discovered during implementation must be raised as a question rather than assumed, per the project's standing instruction.
