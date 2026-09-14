package com.planet.importexport.importapi.support;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Process-local whole-job serialization gate by id-set intersection (ADR-0003;
 * {@code B2}).
 *
 * <p>Usage has two distinct steps, deliberately split so arrival order can be
 * captured on the request thread — before any executor scheduling
 * non-determinism can reorder things — while the actual (potentially long)
 * wait happens on the background executor task, never on the HTTP request
 * thread (ADR-0002: the import endpoint must return immediately):
 *
 * <ol>
 *   <li>{@link #arrive(String, Set)} — called synchronously by the import
 *       endpoint, right after the {@code import_jobs} document is persisted
 *       and before the processing task is submitted to the executor (design.md
 *       section 3, steps 1-2). This assigns the job a fixed position in the
 *       arrival queue, matching submission order exactly.</li>
 *   <li>{@link #awaitTurn(String)} — called by the executor task itself,
 *       before it starts row processing. Blocks until every *earlier* arrival
 *       that intersects this job's id-set has released.</li>
 * </ol>
 *
 * <p><strong>Arrival order (ADR-0003, Consequences: "arrival order must be
 * tracked explicitly").</strong>
 * Because {@link #arrive(String, Set)} runs on the request thread
 * synchronously with request handling, two requests handled one after another
 * are guaranteed to enqueue in that same order, regardless of how the executor
 * later schedules their worker threads. A queued arrival is cleared to proceed
 * only when its id-set is disjoint from every *earlier* arrival still present
 * (running or itself still waiting) — never merely from the currently-running
 * set — which is what prevents a later arrival from jumping ahead of an
 * earlier, still-queued, intersecting one.
 *
 * <p>This is safe-published, in-process-only state (ADR-0002/ADR-0003,
 * "governs only within a single running application instance") — a
 * {@link ReentrantLock} guards the mutable arrival queue, and the
 * {@link Condition} avoids busy-waiting while a job is blocked.
 */
@ApplicationScoped
public class JobIntersectionGate {

    /** Guards {@link #arrivals} against concurrent access from multiple worker threads. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Signaled by {@link #release(String)}; awaited by {@link #awaitTurn(String)}. */
    private final Condition released = lock.newCondition();

    /**
     * Every job that has arrived and not yet released, in strict arrival
     * order. The head of this queue is always either running or eligible to
     * run; an entry only ever leaves via {@link #release(String)}.
     */
    private final Deque<Arrival> arrivals = new ArrayDeque<>();

    /**
     * Registers {@code jobId}'s arrival at the back of the queue.
     * Never blocks — safe to call from the HTTP request thread.
     * Must be called at most once per {@code jobId}, before
     * {@link #awaitTurn(String)}.
     *
     * @param jobId     the arriving job's identifier
     * @param idsInFile the arriving job's full {@code id} column value set,
     *                  used for intersection checks against other arrivals
     */
    public void arrive(String jobId, Set<String> idsInFile) {
        lock.lock();

        try {
            arrivals.addLast(new Arrival(jobId, Set.copyOf(idsInFile)));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Blocks the calling thread until no arrival registered strictly before
     * {@code jobId} (and still present in the queue) intersects its id-set.
     *
     * @param jobId the job whose turn to wait for; must have already called
     *              {@link #arrive(String, Set)}
     * @throws IllegalStateException if {@code jobId} never called
     *                               {@link #arrive(String, Set)}
     * @throws InterruptedException  if the calling thread is interrupted while
     *                               waiting
     */
    public void awaitTurn(String jobId) throws InterruptedException {
        lock.lock();

        try {
            while (intersectsAnyEarlierArrival(jobId)) {
                released.await();
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * Releases the gate for {@code jobId}, waking every thread blocked in
     * {@link #awaitTurn}.
     *
     * @param jobId the job releasing its position in the arrival queue
     */
    public void release(String jobId) {
        lock.lock();

        try {
            arrivals.removeIf(arrival -> arrival.jobId()
                    .equals(jobId));
            released.signalAll();

        } finally {
            lock.unlock();
        }
    }

    /**
     * Whether any arrival strictly before {@code jobId} in the queue
     * intersects its id-set.
     *
     * @param jobId the job to check
     * @return {@code true} if an earlier, still-present arrival intersects
     *         {@code jobId}'s id-set
     * @throws IllegalStateException if {@code jobId} is not present in
     *                               {@link #arrivals}
     */
    private boolean intersectsAnyEarlierArrival(String jobId) {
        Arrival self = null;

        for (Arrival arrival : arrivals) {
            if (arrival.jobId().equals(jobId)) {
                self = arrival;
                break;
            }
        }

        if (self == null) {
            throw new IllegalStateException(
                    "Job did not register arrival before awaiting its turn: " +
                    jobId);
        }

        for (Arrival other : arrivals) {
            if (other.jobId().equals(jobId)) {
                // Reached self with no intersecting predecessor found — clear
                // to proceed.
                return false;
            }

            if (!Collections.disjoint(other.idsInFile(), self.idsInFile())) {
                return true;
            }
        }

        throw new IllegalStateException("Unreachable: self must be present in arrivals: " + jobId);
    }

    /**
     * One queued or running job's arrival record.
     *
     * @param jobId     the job's identifier
     * @param idsInFile the job's full {@code id} column value set, snapshotted
     *                  at arrival time
     */
    private record Arrival(String jobId, Set<String> idsInFile) {}

    /**
     * Test/diagnostic hook: number of jobs currently registered (running or
     * waiting).
     *
     * @return the current arrival queue size
     */
    int registeredCount() {
        lock.lock();

        try {
            return arrivals.size();
        } finally {
            lock.unlock();
        }
    }
}
