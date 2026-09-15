package com.planet.importexport.importapi.support;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.jboss.logging.Logger;

/**
 * CDI-produced bounded {@link ExecutorService} driving asynchronous import
 * job processing (ADR-0002): the import endpoint submits a job's processing
 * task to this executor synchronously on acceptance, so processing begins
 * immediately with no polling delay and no message-broker infrastructure.
 *
 * <p>A fixed-size pool (rather than {@code newCachedThreadPool}) bounds
 * resource usage under an unbounded number of concurrently in-flight import
 * jobs — the exercise sets no numeric throughput target (Quality Attribute
 * Discovery: Performance/Scalability, Low priority), so a modest fixed size is
 * a deliberate, simple default rather than a tuned value.</p>
 */
@ApplicationScoped
public class ImportExecutorProducer {

    private static final Logger LOG =
            Logger.getLogger(ImportExecutorProducer.class);

    /**
     * Fixed worker-thread count backing {@link #executor}; see class Javadoc.
     */
    private static final int POOL_SIZE = 4;

    /**
     * The single bounded executor instance produced for the whole
     * application.
     */
    private final ExecutorService executor =
            Executors.newFixedThreadPool(POOL_SIZE, new ImportThreadFactory());

    /**
     * Exposes {@link #executor} as the CDI bean qualified by
     * {@link ImportExecutor}.
     *
     * @return the shared bounded executor
     */
    @Produces
    @ImportExecutor
    public ExecutorService importExecutor() {
        return executor;
    }

    /**
     * Gracefully shuts down {@link #executor} on container shutdown,
     * escalating to a forced shutdown if graceful termination does not
     * complete within 30 seconds.
     */
    @PreDestroy
    void shutdown() {
        executor.shutdown();

        try {
            if (!executor.awaitTermination(30,
                                           TimeUnit.SECONDS)) {
                LOG.warn("Import executor did not terminate gracefully within 30s; forcing shutdown");

                executor.shutdownNow();
            }

        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
