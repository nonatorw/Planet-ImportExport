---
status: "accepted"
date: 2026-09-14
decision-makers: Solutions Architect agent
consulted: Product owner (user) — no objection raised when offered as a standard engineering decision
informed: Java Full-stack Engineer agent
---

# Store job configuration values as free-form strings with an explicit declared type field

## Context and Problem Statement

The job configuration CRUD API (confirmed decisions 10 and 11) must be generic enough to hold settings beyond `chunkSize` (an integer today, but future settings are unknown in shape). Each configuration entry needs a `key` and a `value`. Should the `value` be stored as an untyped free-form string, or as a field carrying an explicit declared type?

## Decision Drivers

* The API must remain generic across future, currently-unknown job settings (confirmed decision 11) without a schema migration each time a new setting is introduced.
* Consumers of a configuration value (e.g., the chunking logic reading `chunkSize`) need a predictable way to parse the value back into the type they expect (integer, boolean, string), and a way to detect a misconfigured value early rather than fail deep inside processing logic.
* This is a genuinely open engineering choice with no PDF or product-owner constraint dictating one shape over the other; the product owner was offered the option to weigh in and raised no objection to treating it as a standard technical decision.

## Decision Outcome

Chosen option: "Free-form string value with an explicit declared `valueType` field", because it keeps the storage document generic (MongoDB document per config entry: `key`, `value` as string, `valueType` as an enum such as `STRING` / `INTEGER` / `BOOLEAN`, `description`, `updatedAt`) while still letting the application validate and parse a value predictably at write time and at read time, rather than only discovering a malformed value when a job tries to use it. `chunkSize` is seeded (decision 10) as `{ key: "chunkSize", value: "500", valueType: "INTEGER" }` (the numeric default is an implementation detail for the seeding migration, not fixed by this ADR). Write-time validation checks that `value` parses according to `valueType` before persisting; read-time consumers parse `value` according to `valueType` rather than assuming a type per call site.

### Consequences

* Good, because the API stays generic — introducing a new setting is a new document, not a schema change, satisfying decision 11 directly.
* Good, because `valueType` gives both the CRUD API (on write) and consumers (on read) a single, predictable place to validate/parse, rather than each future setting needing bespoke type-guessing logic.
* Bad, because it is still up to each consumer to know which `key` it expects and to handle a `valueType` mismatch gracefully (e.g., someone sets `chunkSize` to a non-numeric string despite the declared `INTEGER` type via a direct database edit bypassing the API) — the CRUD API's write-time validation is the primary safeguard against this, not a database-level constraint.
* Neutral, because this is a reversible, low-stakes implementation convention rather than a structural decision — it can be refined later (e.g., adding more `valueType` variants) without affecting other architectural decisions in this document set.

### Confirmation

* Covered by `docs/requirements/acceptance-criteria.feature`, Feature "Manage job configuration via a CRUD API" (seeding, runtime update, generic entry creation/update/delete).
* A unit test on the configuration write path asserts a value that does not parse according to its declared `valueType` (e.g., `valueType: "INTEGER"`, `value: "not-a-number"`) is rejected at write time with a clear validation error, rather than persisted and failing later during job execution.

## Pros and Cons of the Options

### Free-form string value with declared `valueType` (chosen)

* Good, because it stays generic while enabling early, explicit validation and predictable parsing.
* Bad, because it still relies on application-level (not database-level) enforcement between `value` and `valueType`.

### Pure free-form string value, no declared type

* Good, because it is the simplest possible shape — one less field to manage.
* Bad, because every consumer must independently decide how to parse/validate a value with no declared contract, increasing the risk of inconsistent handling across current and future settings (e.g., one consumer accepting `"true"/"1"/"yes"` as boolean-truthy while another does not).

### Strongly-typed polymorphic value (e.g., separate `stringValue`/`intValue`/`boolValue` fields per document)

* Good, because it would let MongoDB's native BSON typing enforce the value's type directly.
* Bad, because it complicates the generic CRUD contract (clients must know which field to populate/read depending on type) and adds schema noise disproportionate to this exercise's scope, for a benefit (BSON-level type enforcement) that write-time application validation already provides adequately here.

## More Information

See `docs/requirements/functional-specification.md`, confirmed decisions 10 and 11. This decision was explicitly flagged to the product owner as one the Solutions Architect would decide freely absent an objection; none was raised, so it is recorded here as accepted rather than left pending.
