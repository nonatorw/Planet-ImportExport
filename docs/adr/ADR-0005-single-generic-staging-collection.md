---
status: "accepted"
date: 2026-09-14
decision-makers: Product owner (user), Solutions Architect agent
consulted: Business Analyst agent (functional-specification.md, decision 2, 13, 14)
informed: Java Full-stack Engineer agent, Technical Writer agent
---

# Route both invalid field values and unknown header columns to one generic staging collection

## Context and Problem Statement

Requirement 2 requires the application to define behavior for rows containing invalid field values (e.g., non-numeric `age`, malformed `email`) and for rows referencing header columns outside the recognized schema. Rows in either category must be neither silently dropped nor hard-rejected — they must remain auditable per job. What storage structure captures these two distinct problem categories?

## Decision Drivers

* Every problem row must be traceable back to its job, its raw content, and a human-readable reason (auditability, Quality Attribute Discovery: High priority).
* The product owner explicitly confirmed a single generic structure for both problem categories, distinguished only by an error description, rather than separate structures per category.
* The staging record must carry enough information to reconstruct or reprocess the row later if needed, without re-reading the original file.

## Decision Outcome

Chosen option: "One generic staging collection for both invalid-value and unknown-column problems", because the product owner explicitly confirmed this grouping (decision 2). A single MongoDB collection stores staging entries with fields: `jobId` (the owning import job's id), `rowId` (the row's identifier within the source file — its position/line, since the row's own `id` field may itself be the invalid or missing value), `rowData` (the raw source row, preserved verbatim), `errorDescription` (free text identifying the problem — e.g., `"invalid age value: 'thirty'"` or `"unknown column: 'loyalty_tier'"`), and `processedAt` (a timestamp). Both problem categories are written to this same collection; only the `errorDescription` text distinguishes them. The product owner has noted this single-collection grouping may be revisited later, but it governs the current design.

### Consequences

* Good, because the status-query endpoint (decision 3) has exactly one source to read for "the detailed list of staging errors for that job" — no need to merge results from multiple structures.
* Good, because adding a new class of validation problem in the future requires no schema change — it is simply a new kind of `errorDescription` text in the same collection.
* Bad, because `errorDescription` is free text (see ADR-0007 for the related job-configuration value-typing decision, which is a separate but analogous simplicity trade-off) — callers cannot filter staging entries by structured error *type* without parsing the description text. Accepted as sufficient for this exercise's stated need ("distinguished only by an error description").
* Neutral, because `rowId` is defined as the row's position within the source file rather than its parsed `id` field, since a row may be invalid precisely because its `id` (or other required identity data) is missing or malformed — the staging record must remain identifiable even when the row's own content is unusable.

### Confirmation

* Covered by `docs/requirements/acceptance-criteria.feature`, Feature "Route invalid values and unknown columns to staging" (all four scenarios), including the explicit "staging entry carries the required fields" scenario asserting `jobId`, `rowId`, `rowData`, `errorDescription`, `processedAt` are all present.
* A unit test for the email validator asserts `marco@example` (no TLD) is rejected per the simplified RFC 5322 pattern (decision 13); a unit test for the age validator asserts non-numeric values and values outside 0–120 are rejected (decision 14); both produce a staging entry via the same code path.

## Pros and Cons of the Options

### One generic staging collection (chosen)

* Good, because it matches the explicit confirmed decision and keeps the status-query contract simple.
* Bad, because error categorization is text-based, not structured/enumerable.

### Separate collections per problem category (e.g., `invalid_values` and `unknown_columns`)

* Good, because it would allow structured, type-safe querying per category.
* Bad, because the product owner explicitly rejected this split in favor of one generic structure.
* Bad, because the status-query endpoint would need to merge two sources to answer "give me all staging errors for this job," adding needless complexity for no requirement that currently needs it.

## More Information

See `docs/requirements/functional-specification.md`, confirmed decisions 2, 13, 14, and `docs/requirements/acceptance-criteria.feature`, Feature "Route invalid values and unknown columns to staging". If category-specific querying becomes a real need later, introducing a structured `errorType` enum field alongside the free-text `errorDescription` (without removing the latter) would be the natural, backward-compatible extension — this is noted here as a future option, not a current decision.
