package com.planet.importexport.importjob;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Generates the externally-exposed {@code jobId} string used as {@link ImportJobDocument#id}.
 *
 * <p>No ADR or design.md wording fixes an exact generation scheme — design.md section 1.3 gives
 * only the illustrative shape {@code "job-abc123"}. This class picks the simplest scheme matching
 * that shape: a fixed {@code "job-"} prefix followed by a random UUID, which guarantees
 * practical uniqueness without any coordination (no counter, no database round-trip before the
 * job document itself is inserted).
 *
 * <p>The random source is injected as a {@link Supplier} (defaulting to {@link
 * UUID#randomUUID()}) rather than called statically inline, so tests can assert the exact
 * generated id without relying on Mockito to stub a static/final JDK method (per
 * {@code @421-frameworks-quarkus-testing-unit-tests}, "inject a Supplier so tests control
 * outputs without bytecode manipulation").
 */
public class ImportJobIdGenerator {

    private static final String PREFIX = "job-";

    private final Supplier<UUID> randomSource;

    public ImportJobIdGenerator() {
        this(UUID::randomUUID);
    }

    public ImportJobIdGenerator(Supplier<UUID> randomSource) {
        this.randomSource = randomSource;
    }

    public String generate() {
        return PREFIX + randomSource.get();
    }
}
