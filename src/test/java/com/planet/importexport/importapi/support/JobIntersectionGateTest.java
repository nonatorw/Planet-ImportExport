package com.planet.importexport.importapi.support;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JobIntersectionGate} (ADR-0003; {@code B2}):
 * empty intersection allows immediate start, non-empty intersection forces
 * waiting, and release ordering matches arrival order among
 * mutually-intersecting jobs — the three assertions ADR-0003's own
 * Confirmation section calls for on this gate specifically.
 *
 * <p>Polls manually (bounded loop + short sleep) rather than pulling in a
 * dedicated poll/await-condition test library — no such dependency exists on
 * this project's classpath and AGENTS.md requires asking before adding one.
 */
class JobIntersectionGateTest {
    private final ExecutorService executor =
            Executors.newFixedThreadPool(4);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void disjointJobsBothProceedWithoutWaiting() throws InterruptedException {
        JobIntersectionGate gate = new JobIntersectionGate();
        gate.arrive("job-a", Set.of("1"));
        gate.arrive("job-b", Set.of("2"));

        // Neither should block: both are cleared immediately since arrivals
        // ahead of each are disjoint from it.
        gate.awaitTurn("job-a");
        gate.awaitTurn("job-b");

        assertThat(gate.registeredCount()).isEqualTo(2);
    }

    @Test
    @Timeout(10)
    void intersectingSecondArrivalWaitsUntilFirstReleases()
            throws Exception {
        JobIntersectionGate gate = new JobIntersectionGate();
        gate.arrive("job-a", Set.of("1"));
        gate.arrive("job-b", Set.of("1")); // intersects job-a

        CountDownLatch jobBStarted = new CountDownLatch(1);
        CopyOnWriteArrayList<String> executionOrder = new CopyOnWriteArrayList<>();

        // job-a proceeds immediately (nothing ahead of it).
        gate.awaitTurn("job-a");
        executionOrder.add("job-a-started");

        executor.submit(() -> {
            try {
                jobBStarted.countDown();
                gate.awaitTurn("job-b");
                executionOrder.add("job-b-started");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(jobBStarted.await(5, TimeUnit.SECONDS)).isTrue();

        // Give job-b's awaitTurn a moment to actually block on the condition
        // before releasing job-a.
        Thread.sleep(200);
        assertThat(executionOrder).containsExactly("job-a-started");

        gate.release("job-a");

        waitUntil(() -> executionOrder.contains("job-b-started"),
                  5000);

        assertThat(executionOrder)
                .containsExactly("job-a-started", "job-b-started");
    }

    @Test
    @Timeout(10)
    void arrivalOrderIsPreservedAmongIntersectingJobsEvenIfLaterJobsWorkerRunsFirst()
            throws Exception {
        JobIntersectionGate gate = new JobIntersectionGate();

        // Arrival order: A, B, C — all share id "1", so they must clear the
        // gate strictly in that order even if the executor happens to schedule
        // C's or B's worker thread before A's.
        gate.arrive("job-a", Set.of("1"));
        gate.arrive("job-b", Set.of("1"));
        gate.arrive("job-c", Set.of("1"));

        CopyOnWriteArrayList<String> clearedOrder =
                new CopyOnWriteArrayList<>();

        CountDownLatch allSubmitted =
                new CountDownLatch(3);

        // Submit C and B's waiters first (reverse of arrival order) to prove
        // the *arrival queue* position — not submission/scheduling order —
        // governs release order.
        executor.submit(() -> waitAndRecord(gate,
                                            "job-c",
                                            clearedOrder,
                                            allSubmitted));

        executor.submit(() -> waitAndRecord(gate,
                                            "job-b",
                                            clearedOrder,
                                            allSubmitted));

        executor.submit(() -> waitAndRecord(gate,
                                            "job-a",
                                            clearedOrder,
                                            allSubmitted));

        assertThat(allSubmitted.await(5, TimeUnit.SECONDS)).isTrue();

        // job-a has nothing ahead of it, so it should clear almost
        // immediately; b and c must wait.
        waitUntil(() -> clearedOrder.contains("job-a"),
                  5000);
        Thread.sleep(200);

        assertThat(clearedOrder)
                .containsExactly("job-a");

        gate.release("job-a");

        waitUntil(() -> clearedOrder.contains("job-b"),
                  5000);
        Thread.sleep(200);

        assertThat(clearedOrder)
                .containsExactly("job-a", "job-b");

        gate.release("job-b");

        waitUntil(() -> clearedOrder.contains("job-c"),
                  5000);

        assertThat(clearedOrder)
                .containsExactly("job-a", "job-b", "job-c");

        gate.release("job-c");
    }

    @Test
    void awaitTurnThrowsIfJobNeverArrived() {
        JobIntersectionGate gate = new JobIntersectionGate();

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> gate.awaitTurn("never-arrived"));
    }

    private void waitAndRecord(JobIntersectionGate gate,
                               String jobId,
                               CopyOnWriteArrayList<String> order,
                               CountDownLatch latch) {
        latch.countDown();

        try {
            gate.awaitTurn(jobId);
            order.add(jobId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Polls {@code condition} every 20ms until true or {@code timeoutMillis}
     * elapses.
     */
    private static void waitUntil(java.util.function.BooleanSupplier condition,
                                  long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;

        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Condition not met within " + timeoutMillis + "ms");
            }

            Thread.sleep(20);
        }
    }
}
