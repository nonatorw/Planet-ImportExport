package com.planet.importexport.mongo;

import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import de.flapdoodle.embed.mongo.commands.ServerAddress;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;

/**
 * Starts an embedded, in-memory MongoDB instance (Flapdoodle) for
 * {@code @QuarkusTest} runs and exposes its connection string as
 * {@code quarkus.mongodb.connection-string}, per ADR-0001 (embedded in-memory
 * MongoDB, no external database infrastructure).
 *
 * <p>Quarkus MongoDB Dev Services is disabled
 * ({@code quarkus.mongodb.devservices.enabled=false}, see
 * {@code application.yml}) so this resource is the sole source of the
 * test-time connection string.</p>
 *
 * <p>Flapdoodle's OS detection ({@code de.flapdoodle.os}) ships a fixed table
 * of known Linux distribution releases and does not yet know every current
 * host release (e.g. an Ubuntu point release newer than the library's latest
 * known {@code Ubuntu_25_10} entry), which would otherwise fail with
 * {@code IllegalArgumentException: could not resolve package for ...}.
 * Flapdoodle's own documented escape hatch for this is the
 * {@code de.flapdoodle.os.override} system property (format
 * {@code <OS>|<Architecture>|<Dist>|<Version>|}), which we set defensively —
 * and only if not already provided by the caller/CI — to the newest Ubuntu
 * release Flapdoodle 4.24.0 / de.flapdoodle.os 1.10.1 recognizes, which
 * remains binary-compatible with newer Ubuntu point releases for MongoDB's
 * purposes.</p>
 */
public class FlapdoodleMongoTestResource
        implements QuarkusTestResourceLifecycleManager {
    private static final String OS_OVERRIDE_PROPERTY =
            "de.flapdoodle.os.override";
    private static final String OS_OVERRIDE_VALUE =
            "Linux|X86_64|Ubuntu|Ubuntu_25_10";
    private static final String MONGODB_CONNECTION_STRING_PROPERTY =
            "quarkus.mongodb.connection-string";

    private TransitionWalker.ReachedState<RunningMongodProcess> runningMongod;

    /**
     * Starts the embedded MongoDB instance, defensively setting Flapdoodle's
     * OS-override system property first (see class Javadoc), and returns the
     * connection string property Quarkus should use for this test run.
     *
     * @return a single-entry map providing
     *         {@code quarkus.mongodb.connection-string}
     */
    @Override
    public Map<String, String> start() {
        if (System.getProperty(OS_OVERRIDE_PROPERTY) == null) {
            System.setProperty(OS_OVERRIDE_PROPERTY, OS_OVERRIDE_VALUE);
        }

        runningMongod = Mongod.instance().start(Version.Main.V7_0);

        ServerAddress serverAddress = runningMongod.current()
                                                   .getServerAddress();

        String connectionString = "mongodb://" + serverAddress.getHost() +
                                  ":" + serverAddress.getPort();

        return Map.of(MONGODB_CONNECTION_STRING_PROPERTY,
                      connectionString);
    }

    /**
     * Stops the embedded MongoDB instance started by {@link #start()}, if
     * one is running.
     */
    @Override
    public void stop() {
        if (runningMongod != null) {
            runningMongod.close();
        }
    }
}
