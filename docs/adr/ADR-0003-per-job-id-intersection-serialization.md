---
status: "accepted"
date: 2026-09-14
decision-makers: Product owner (user), Solutions Architect agent
consulted: Business Analyst agent (functional-specification.md, decision 7; acceptance-criteria.feature)
informed: Java Full-stack Engineer agent
---

# Serialize whole import jobs by id-set intersection, not by individual row/id locking

## Context and Problem Statement

Two import jobs that both touch record `id = 1` must not create version N and version N+1 concurrently in an order-unsafe way (Root Cause Analysis: overlapping/cumulative batches; confirmed decision 1's versioning depends on a well-defined "immediately preceding version"). The product owner initially described this as "per-id serialized processing" (decision 7) and, when asked to clarify granularity, confirmed explicitly: serialization is at the level of the *whole job*, gated by whether two jobs' id-sets intersect at all — not per-row or per-individual-id locking within a job. What locking/serialization granularity should the implementation use?

## Decision Drivers

* Correctness: version N+1 for a given `id` must always be computed from a fully-settled version N — two jobs racing on the same `id` must not interleave.
* The product owner explicitly ruled out per-row/per-id granular locking as unnecessary complexity for this exercise.
* Jobs with completely disjoint id-sets must be able to run fully in parallel (Quality Attribute Discovery: Performance/Scalability is low priority, but avoiding needless serialization is still an explicit requirement, decision 7 and acceptance-criteria.feature scenario "Import jobs for different ids may run concurrently").
* The mechanism must be checkable before a job starts, using only the file's parsed id-set (or header+rows) — no cross-job coordination service beyond the application's own state.

## Decision Outcome

Chosen option: "Whole-job serialization gated by id-set intersection", because it is the explicit, confirmed instruction from the product owner and it is the simplest mechanism that satisfies both correctness (no interleaved versioning for a shared id) and the parallelism requirement (disjoint jobs never wait on each other). Before a submitted job begins processing, the dispatcher computes the job's id-set (by parsing the `id` column of the file, which is inexpensive relative to full row validation) and checks it against the id-sets of all currently running or already-queued-ahead jobs. If the new job's id-set intersects any of those, the new job waits until every job it intersects with has fully completed; if there is no intersection with any in-flight job, the new job may start immediately, concurrently with unrelated jobs.

### Consequences

* Good, because it directly satisfies the confirmed requirement and the corresponding acceptance scenarios ("Two import jobs for the same id are processed one at a time, in arrival order" and "Import jobs for different ids may run concurrently").
* Good, because it avoids the complexity of fine-grained per-row/per-id locks, lock ordering, and deadlock avoidance that a more granular scheme would require.
* Bad, because a job with a very large id-set can block many other jobs even if the actual overlap is a single `id` — the whole job waits, not just the overlapping rows. This is an accepted, explicit trade-off per the product owner's instruction (simplicity over fine-grained throughput).
* Bad, because computing a job's id-set requires an initial pass over the file (at least the `id` column) before processing can begin, adding a small amount of up-front latency proportional to file size. This is not expected to be material given no volume/throughput target exists (Quality Attribute Discovery: Performance/Scalability, Low priority).
* Neutral, because "arrival order" must be tracked explicitly (e.g., a monotonic submission sequence or timestamp) so that when multiple queued jobs intersect the same running job, they are released in the order they arrived, matching the acceptance scenario's expectation that version 2 (job A) precedes version 3 (job B).

### Confirmation

* An integration test submits job A and job B, both containing `id = 1` with different `country` values, and asserts B does not start processing until A fully completes, and that the resulting versions are numbered/ordered by arrival (A's version precedes B's version).
* An integration test submits job A (id 1 only) and job B (id 2 only) concurrently and asserts neither blocks on the other (e.g., by asserting both complete within a bound that would be violated if they were serialized, or by instrumenting start/end timestamps to show temporal overlap).
* A unit test on the id-set-intersection gate itself asserts: empty intersection to allow immediate start; non-empty intersection to force waiting; and correct release ordering when multiple jobs are queued behind one blocking job.

## Pros and Cons of the Options

### Whole-job serialization by id-set intersection (chosen)

* Good, because it matches the explicit, confirmed product decision.
* Good, because it is simple to reason about and test (one intersection check per job submission).
* Bad, because it can create false contention (large jobs blocking on a single shared id).

### Per-row / per-individual-id locking

* Good, because it would allow finer-grained parallelism — only the specific overlapping ids would serialize, not entire jobs.
* Bad, because the product owner explicitly rejected this granularity as unneeded complexity for this exercise.
* Bad, because it introduces lock-management complexity (acquiring/releasing many fine-grained locks per job, ordering to avoid deadlock) disproportionate to the exercise's scope.

### No serialization (rely on MongoDB per-document atomicity only)

* Bad, because per-document atomicity alone does not guarantee correct "read latest version, then write next version" semantics under concurrent access to the same `id` — a race could compute version N+1 from a stale read of version N-1 if two jobs interleave. Rejected as it would violate the versioning correctness driver.

## More Information

This decision governs only within a single running application instance (see ADR-0002 — the dispatch mechanism is a process-local executor, so the id-set intersection state is naturally process-local too). See `docs/requirements/functional-specification.md`, confirmed decision 7, and `docs/requirements/acceptance-criteria.feature`, Feature "Version a record when the same id is received more than once".
