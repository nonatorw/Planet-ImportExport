package com.planet.importexport.staging;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.runtime.StartupEvent;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

/**
 * Repository for the single generic {@code staging_entries} collection
 * (ADR-0005). Exposes insertion and a fetch-all-by-job query; the latter is
 * what the job-status endpoint
 * ({@code GET /api/v1/imports/{jobId}}, Group B/{@code B10}) will use to
 * return every staging error recorded for a job — that endpoint is out of this
 * task's scope and is only a downstream consumer of this repository.
 */
@ApplicationScoped
public class StagingEntryRepository
        implements PanacheMongoRepository<StagingEntry> {

    /**
     * Ensures the {@code jobId} index required by design.md section 1.2 exists
     * (status queries fetch all staging entries for a job). Runs on
     * application startup so the index is present before any import job writes
     * staging entries. Safe to call repeatedly — MongoDB is idempotent when
     * creating an index that already exists with the same keys/options.
     */
    void ensureIndexes(@Observes StartupEvent event) {
        mongoCollection().createIndex(Indexes.ascending("jobId"),
                                      new IndexOptions().background(true));
    }

    /**
     * Fetches every staging entry recorded for the given import job, in
     * insertion order (natural order matches {@code processedAt} order since
     * rows are staged as they are processed).
     */
    public List<StagingEntry> findByJobId(String jobId) {
        return find("jobId", jobId).list();
    }
}
