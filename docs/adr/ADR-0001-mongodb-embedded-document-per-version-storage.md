---
status: "accepted"
date: 2026-09-14
decision-makers: Product owner (user), Solutions Architect agent
consulted: Business Analyst agent (functional-specification.md, decision 16)
informed: Java Full-stack Engineer agent, DevOps/Platform Engineer agent, Technical Writer agent
---

# Use embedded in-memory MongoDB (Flapdoodle) with one document per record version

## Context and Problem Statement

The service must persist customer records read from CSV files whose column sets vary across files (schema drift), and must retain a full version history whenever the same `id` is re-imported (Requirement 3 / decision 1). The repository's original technical assumption pointed at an embedded relational store (H2) with a fixed column schema. The product owner has since confirmed a stack change: storage moves from H2 to MongoDB, running embedded in-memory via Flapdoodle for automated tests, so the exercise requires no external database infrastructure for that path (no Docker, no Testcontainers, no real MongoDB server). `quarkusDev`/manual runs are a separate, later decision (see "More Information"): they use real MongoDB Dev Services (a Testcontainers/Podman-provisioned container), since Flapdoodle's embedded instance is only wired up on the test classpath. How should customer records and their version history be modeled and stored under this constraint?

## Decision Drivers

* No fixed schema across source files — new/unexpected columns must not break storage or require a migration (Root Cause Analysis: schema drift).
* Full version history must be retained per `id`, never overwritten (confirmed decision 1).
* The exercise must keep running locally without external database infrastructure (functional-specification.md, Context Mapping — External dependencies).
* Avoid the complexity of an EAV (entity-attribute-value) model or an auxiliary JSON column bolted onto a relational schema.
* Query need is simple: fetch the current (highest-version) document per `id`, and fetch all versions of an `id` for history/audit.

## Considered Options

* Embedded H2 (relational) with a fixed-column `customers` table plus a `customers_history` table
* Embedded H2 (relational) with an EAV or auxiliary JSON column to tolerate variable fields
* Embedded in-memory MongoDB (Flapdoodle), one document per record version, native variable fields

## Decision Outcome

Chosen option: "Embedded in-memory MongoDB (Flapdoodle), one document per record version, native variable fields", because it is the option the product owner explicitly selected (confirmed decision 16), and it is also the only option that satisfies both the schema-drift driver and the "no external database infrastructure" driver without introducing EAV or auxiliary-JSON workarounds. Each import that changes a given `id` creates a new version document; it does not overwrite the previous one. The "current" version of an `id` is the document with the highest version number. A new version's fields are computed by merging the incoming row onto the immediately preceding version's snapshot (fields absent from the new file are inherited), consistent with confirmed decision 1.

### Consequences

* Good, because variable/unexpected columns from source files do not require a schema migration — MongoDB documents accept variable fields natively per document.
* Good, because full version history is a natural consequence of "insert new document per version" rather than requiring a separate history table and change-tracking triggers.
* Good, because the embedded (Flapdoodle) mode keeps the "runs locally without external database infrastructure" property intact for the storage layer specifically.
* Bad, because Flapdoodle embedded MongoDB is intended for testing/development, not production; if this service were ever promoted beyond the exercise, the storage layer would need to move to a real, externally hosted MongoDB (Atlas, self-hosted, or containerized) — this is accepted as out of scope for this exercise.
* Bad, because "current version" queries require an application-level convention (highest `version` number per `id`) rather than a database-enforced single-current-row constraint; this must be encoded consistently in the repository/query layer and validated by tests.
* Neutral, because MongoDB's lack of cross-document ACID transactions (beyond single-document atomicity and multi-document transactions within a replica set, which Flapdoodle can still provide) is not a concern here: exactly one process (the serialized job for a given `id`, per ADR-0003) writes new versions for that `id` at a time.

### Confirmation

* Integration tests assert: (a) importing a new `id` creates version 1; (b) re-importing the same `id` creates version 2 without mutating version 1; (c) fields absent from the second import are inherited from version 1 in the resulting version 2 document; (d) a query for the "current" record returns the document with the highest version number for that `id`.
* A repository-level test enumerates all versions for a given `id` and asserts strictly increasing, gapless version numbers with no duplicate version numbers per `id`.

## Pros and Cons of the Options

### Embedded H2, fixed-column tables + history table

* Good, because relational tooling (schema migrations, SQL joins) is mature and familiar.
* Bad, because every new/unexpected column from a source file (schema drift) requires a schema migration — directly contradicts the Root Cause Analysis finding that source files are not uniform.
* Bad, because the product owner has explicitly superseded this approach (was the original assumption, now replaced by decision 16).

### Embedded H2 with EAV or auxiliary JSON column

* Good, because it tolerates variable fields without a migration per new column.
* Bad, because EAV models are explicitly rejected by the product owner's decision 16 ("no EAV, no auxiliary JSON column").
* Bad, because EAV/JSON-in-relational approaches sacrifice most of the benefit of a relational schema (typed columns, straightforward queries) while keeping its operational weight.

### Embedded in-memory MongoDB (Flapdoodle), document-per-version

* Good, because it is a native fit for variable-field documents without EAV or auxiliary-JSON workarounds.
* Good, because "new document per version" directly expresses the versioning policy without extra history-table plumbing.
* Neutral, because it introduces a new technology (MongoDB driver, Flapdoodle) not previously part of the stack, requiring the team to pick up Quarkus MongoDB client conventions.
* Bad, because embedded/in-memory MongoDB is not representative of a production deployment topology (see Consequences).

## More Information

This decision supersedes any earlier reference to H2 as the storage engine in prior drafts of `AGENTS.md` or the functional specification; `AGENTS.md` has already been updated to say "MongoDB (embedded in-memory via Flapdoodle)". Related decisions: ADR-0003 (per-job id-intersection serialization) depends on this document-per-version model to determine which jobs "touch the same id". See `docs/requirements/functional-specification.md`, Assumption Analysis, confirmed decision 16, and Traceability table (Requirement 4).
