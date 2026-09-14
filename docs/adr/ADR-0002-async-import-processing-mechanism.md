---
status: "accepted"
date: 2026-09-14
decision-makers: Solutions Architect agent
consulted: Product owner (user) — explicitly delegated this choice as a standard engineering decision
informed: Java Full-stack Engineer agent, DevOps/Platform Engineer agent
---

# Use a dedicated managed executor (thread pool) to drive asynchronous, chunked import processing

## Context and Problem Statement

Import is asynchronous (confirmed decision 3): the REST call accepts a file path, persists a job record, and returns a `jobId` immediately; a background process then reads the file in configurable batches (chunks, decision 10), validates/stages/persists rows, and updates job status. The product owner explicitly delegated the choice of triggering/dispatch mechanism for this background work to the Solutions Architect, naming three candidate approaches to choose freely among: a dedicated executor/thread pool, internal messaging (SmallRye Reactive Messaging), or a scheduler polling a pending-jobs collection. Which mechanism should drive the async chunked processing, and why?

## Decision Drivers

* No external message broker or infrastructure may be introduced solely for internal job dispatch — the exercise must stay runnable with only Docker for Keycloak (ADR-0006) and embedded MongoDB (ADR-0001); adding e.g. Kafka or AMQP purely for internal job handoff would violate that footprint.
* The mechanism must support "start processing immediately after the job is accepted" (no arbitrary polling delay before a job begins).
* The mechanism must support "per-job-pair serialization when they share an id, full parallelism when they don't" (ADR-0003) — the dispatch layer must know a job's id-set before deciding whether it may start.
* Simplicity and testability matter: this is a bounded exercise; the simplest mechanism that satisfies the drivers above is preferred over more elaborate infrastructure.
* Quarkus-native fit: the mechanism should use idiomatic Quarkus/CDI/Jakarta EE building blocks rather than a hand-rolled scheduler loop.

## Considered Options

* Dedicated managed executor / thread pool (Quarkus `ManagedExecutor` or a bounded `ExecutorService` CDI bean) triggered directly by the import endpoint
* SmallRye Reactive Messaging (in-memory channel) with a `@Incoming`/`@Outgoing` pipeline
* Scheduler (Quarkus Scheduler, `@Scheduled`) polling a "pending jobs" collection at a fixed interval

## Decision Outcome

Chosen option: "Dedicated managed executor / thread pool", because it satisfies every driver with the least incidental complexity: the import endpoint submits the job to the executor synchronously after persisting the job record, so processing begins immediately (no polling latency); the executor task itself is the natural place to perform the id-intersection serialization check (ADR-0003) before starting; and it introduces no new infrastructure or broker semantics (at-least-once delivery, message acknowledgment, dead-letter handling) that would be pure overhead for an in-process, single-node exercise.

### Consequences

* Good, because job start latency is bounded only by executor availability, not by a polling interval.
* Good, because the executor task is a single, easily testable unit (submit → acquire per-id serialization lock(s) → process in chunks → update job status), with no message-broker abstraction to mock in tests.
* Good, because it requires no additional dependency beyond what Quarkus/Jakarta already provide (`jakarta.enterprise.concurrent.ManagedExecutorService` or a CDI-produced bounded `ExecutorService`).
* Bad, because in-flight jobs are lost if the application process crashes or restarts — there is no durable queue to resume from. This is accepted for the exercise's scope (no HA/durability requirement was stated); if durability across restarts were required, the scheduler-over-a-jobs-collection or a real message broker would need reconsideration.
* Bad, because horizontal scale-out (multiple application instances) is not supported by this mechanism — a shared thread pool is process-local. This is acceptable because the exercise defines no multi-instance or scaling requirement (Quality Attribute Discovery rates Performance/Scalability as Low priority, with no numeric targets).
* Neutral, because the job record (status, summary counters) is still persisted to MongoDB regardless of dispatch mechanism, so job status is queryable even though the dispatch/execution state itself is in-memory only.

### Confirmation

* An integration test submits an import job and asserts the job transitions from `PENDING`/`RUNNING` to `COMPLETED` without any external polling trigger from the test itself (proving the executor starts work on submission).
* A test with a bounded executor pool (e.g., pool size 1) submits two jobs touching disjoint ids and asserts both complete without one waiting on an artificial scheduler tick, demonstrating parallel dispatch is limited only by pool size and id-intersection locking (ADR-0003), not by a polling cadence.

## Pros and Cons of the Options

### Dedicated managed executor / thread pool

* Good, because processing starts immediately on submission.
* Good, because it colocates naturally with the id-intersection serialization gate (ADR-0003).
* Good, because it needs no new runtime dependency.
* Bad, because it offers no durability across process restarts.
* Bad, because it does not scale beyond a single application instance.

### SmallRye Reactive Messaging (in-memory channel)

* Good, because it decouples "accept the job" from "process the job" through a well-known Quarkus abstraction, and could later be repointed at a real broker (Kafka, AMQP) for durability/scale with modest code change.
* Neutral, because an in-memory channel still runs in-process, so it shares the durability and scale-out limitations of the executor option while adding the conceptual overhead of channels, connectors, and message acknowledgment semantics.
* Bad, because implementing "start job only if no in-flight job shares an id" inside a reactive messaging pipeline (which processes messages independently by design) is materially more complex than a direct pre-submission check in an executor task — it would likely require an additional coordination component regardless.

### Scheduler (Quarkus `@Scheduled`) polling a pending-jobs collection

* Good, because job dispatch state is fully persisted (the "pending" collection is the durable queue), so in-flight/queued jobs survive an application restart.
* Bad, because it introduces polling latency between job submission and the next scheduler tick, which the async-but-prompt expectation (decision 3: "returns a jobId immediately" and processing happens "in the background", with no stated tolerance for added latency) does not require and which adds needless delay for this exercise's scope.
* Bad, because it is more code (a poller, a pending-state model, a claim/lock-per-poll-cycle mechanism to avoid double-picking a job) for no benefit given the exercise has no durability or multi-instance requirement.

## More Information

If a future iteration of this service requires durability across restarts or horizontal scale-out, this decision should be revisited — the scheduler-over-collection or a real message broker (via SmallRye Reactive Messaging pointed at Kafka/AMQP) would become the stronger candidates at that point. See `docs/requirements/functional-specification.md`, confirmed decision 3 (async flow) and confirmed decision 10 (chunk size configuration). Related: ADR-0003 (per-job id-intersection serialization), ADR-0001 (MongoDB job/status persistence).
