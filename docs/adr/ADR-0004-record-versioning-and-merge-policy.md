---
status: "accepted"
date: 2026-09-14
decision-makers: Product owner (user), Solutions Architect agent
consulted: Business Analyst agent (functional-specification.md, decision 1)
informed: Java Full-stack Engineer agent, Technical Writer agent
---

# Create a new merged version document on every repeated id, never overwrite or reject

## Context and Problem Statement

The same logical customer (`id`) can appear in more than one imported file, sometimes with additional fields (e.g., `phone` added later) and sometimes with a changed value for an existing field (Root Cause Analysis, sample data). Requirement 3 requires the application to define how repeated data is handled. What is the storage/merge policy when an `id` already known to the system is re-imported?

## Decision Drivers

* Full audit history must be retained — no silent overwrite, no silent merge without trace, no rejection of legitimately repeated data.
* Fields omitted from a later file (e.g., a file that only updates `country`) must not be treated as "field cleared" — the source files are cumulative/corrective, not authoritative full snapshots (Root Cause Analysis).
* Conflicting values for the same field across files must resolve predictably without a bespoke per-field conflict-resolution rule.
* The policy must compose with the chosen storage model (ADR-0001: one document per version) and the chosen concurrency model (ADR-0003: whole-job serialization by id intersection).

## Decision Outcome

Chosen option: "New version per re-received id, computed as a merge of the incoming row onto the immediately preceding version's snapshot", because it satisfies every driver directly and was explicitly confirmed by the product owner (decision 1). Concretely: on import, for each row's `id`, the service looks up the current (highest-version) document for that `id`; if none exists, it inserts version 1 using the row's fields as given. If a current version exists, the service builds a new document with version = (current version + 1), where each schema field's value is taken from the incoming row if present in that row, and otherwise carried forward from the current version's value for that field. The new document is inserted (not an update-in-place); the previous version's document is left untouched. A changed value for an existing field is resolved simply by the new version recording the new value — no separate conflict-resolution rule is needed, since the old value remains visible in the preserved prior version.

### Consequences

* Good, because every historical state of a customer record remains queryable by version number, satisfying auditability (Quality Attribute Discovery: Observability/auditability, High priority).
* Good, because "conflicting value" is not a distinct case requiring special handling — it is the normal outcome of the merge rule.
* Good, because the rule is simple to state and test: "new version = incoming fields override; missing fields inherit from prior version."
* Bad, because there is no way to distinguish, from the data alone, "the source file intentionally omitted this field" from "the source file has no opinion on this field" — both are treated identically (inherit prior value). This is accepted because the PDF and the confirmed decisions give no signal that source files ever intend to clear a previously-set field; this is documented explicitly for the Technical Writer to state as a known modeling limitation.
* Neutral, because version documents accumulate without bound for frequently-updated ids; no retention/archival policy is defined or required by the exercise (no such requirement was stated).

### Confirmation

* Covered by `docs/requirements/acceptance-criteria.feature`, Feature "Version a record when the same id is received more than once" (all four scenarios): additive-field re-import, conflicting-value re-import, arrival-order serialization (ADR-0003), and cross-id parallelism.
* An integration test asserts that after three files import the same `id = 1` cumulatively (per the PDF's worked example), the current version contains the union of all fields ever supplied, with the most recent file's values winning on overlap.

## Pros and Cons of the Options

### New version per re-received id, merge onto immediately preceding version (chosen)

* Good, because it matches the confirmed decision and the worked example in the PDF exactly.
* Good, because it requires no per-field conflict-resolution table.
* Bad, because a field cannot be explicitly "unset" by a later file (see Consequences) — accepted, documented limitation.

### Overwrite in place (no history)

* Bad, because it destroys audit history, directly contradicting Requirement 3's evaluation intent and the product owner's explicit rejection of overwrite semantics.

### Reject the re-import outright

* Bad, because the PDF's own sample data relies on the same id legitimately appearing across multiple files (e.g., `id = 1` in all three sample files) — rejecting repeated ids would make the documented worked example fail.

## More Information

See `docs/requirements/functional-specification.md`, confirmed decision 1, and Root Cause Analysis section 2. Depends on ADR-0001 (document-per-version storage) and is protected by ADR-0003 (per-job id-intersection serialization), which guarantees the "immediately preceding version" read is never racing a concurrent writer for the same id.
