package com.planet.importexport.jobconfig;

import java.time.Clock;
import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;

/**
 * Seeds the initial {@code chunkSize} job configuration entry at application
 * startup (design.md section 1.4; spec: job-configuration, "Database-backed,
 * migration-seeded job configuration").
 *
 * <p>design.md leaves the migration mechanism open ("Mongock or an idempotent
 * Quarkus startup event — implementation detail for the Java Full-stack
 * Engineer"). No ADR mandates Mongock, and adding it would introduce a new
 * Gradle dependency in {@code build.gradle} — a shared file other Group A
 * agents are not touching, per AGENTS.md's "ask first" boundary on new
 * dependencies. This migration therefore uses a CDI {@link StartupEvent}
 * observer: a check-and-insert that only creates the entry if absent, so it is
 * safe to run on every application start.</p>
 *
 * <p>Idempotency semantics (ADR-0007 does not fix a numeric default, only that
 * it is "illustrative — not fixed by ADR-0007"): running this observer twice —
 * or on every restart — never duplicates or overwrites the entry. If an
 * operator has since changed {@code chunkSize} via the (future) Group D CRUD
 * API, this migration leaves that modified value untouched; it only ever
 * inserts the default when the key does not exist yet.</p>
 */
@ApplicationScoped
public class JobConfigurationSeedMigration {
    private static final Logger LOG =
            Logger.getLogger(JobConfigurationSeedMigration.class);

    private final Clock clock;
    private final JobConfigurationRepository repository;

    static final String CHUNK_SIZE_KEY = "chunkSize";
    static final String CHUNK_SIZE_DEFAULT_VALUE = "500";
    static final String CHUNK_SIZE_DESCRIPTION =
            "Number of rows processed per batch during async import";

    /**
     * CDI-injected constructor used in production, wiring a system UTC
     * {@link Clock} for {@link #seedChunkSizeIfAbsent()}'s {@code updatedAt}
     * timestamp.
     *
     * @param repository the repository used to check for and insert the seed
     *                   entry
     */
    @Inject
    public JobConfigurationSeedMigration(JobConfigurationRepository repository) {
        this(repository, Clock.systemUTC());
    }

    /**
     * Package-visible constructor for tests to inject a fixed {@link Clock}.
     *
     * @param repository the repository used to check for and insert the seed
     *                   entry
     * @param clock      the clock used to timestamp the seeded entry
     */
    JobConfigurationSeedMigration(JobConfigurationRepository repository,
                                  Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * CDI {@link StartupEvent} observer that triggers the seed check on
     * application start (see class-level Javadoc for why a startup event is
     * used instead of a Mongock migration).
     *
     * @param event the startup event; unused beyond triggering this observer
     */
    void onStart(@Observes StartupEvent event) {
        seedChunkSizeIfAbsent();
    }

    /**
     * Inserts the default {@code chunkSize} entry only if no entry for that
     * key exists yet.
     * Package-visible so tests can invoke it directly (twice) to prove
     * idempotency without depending on Quarkus's own startup sequencing.
     */
    void seedChunkSizeIfAbsent() {
        if (repository.findByKey(CHUNK_SIZE_KEY).isPresent()) {
            LOG.debugf("job configuration '%s' already present; skipping seed",
                       CHUNK_SIZE_KEY);

            return;
        }

        JobConfigurationEntry entry =
                new JobConfigurationEntry(CHUNK_SIZE_KEY,
                                          CHUNK_SIZE_DEFAULT_VALUE,
                                          JobConfigurationValueType.INTEGER,
                                          CHUNK_SIZE_DESCRIPTION,
                                          Instant.now(clock));

        repository.insert(entry);

        LOG.infof("seeded job configuration '%s' = %s",
                  CHUNK_SIZE_KEY,
                  CHUNK_SIZE_DEFAULT_VALUE);
    }
}
