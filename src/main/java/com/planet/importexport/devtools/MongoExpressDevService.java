package com.planet.importexport.devtools;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

/**
 * Starts a Mongo Express container alongside {@code quarkusDev}, giving a
 * point-and-click UI to inspect the data MongoDB Dev Services provisions for
 * manual/demo runs — the same "browse without writing a query" convenience
 * Keycloak Dev Services already gives for auth (a login/token UI included
 * with the container), which plain MongoDB does not ship on its own.
 *
 * <p>{@code %dev}-only ({@link IfBuildProfile @IfBuildProfile("dev")}):
 * automated tests use embedded Flapdoodle MongoDB, not a real container (see
 * {@code FlapdoodleMongoTestResource}, ADR-0001), so there is no Mongo Dev
 * Services container to point Mongo Express at under {@code %test}.</p>
 *
 * <p>Implemented as a plain CDI {@link StartupEvent}/{@link ShutdownEvent}
 * observer pair invoking the {@code docker} CLI directly via
 * {@link ProcessBuilder}, rather than as a real Quarkus Dev Services
 * {@code BuildStep} or via the Testcontainers library: a build-time Dev
 * Services processor requires restructuring this single-module application
 * into a Quarkus extension ({@code deployment}/{@code runtime} modules), and
 * a Testcontainers-based runtime alternative would add a new Gradle
 * dependency — both disproportionate for a local convenience tool, and the
 * latter crosses AGENTS.md's "ask first" boundary on new dependencies. The
 * {@code docker} binary is already a hard requirement for this project (it
 * is what runs the Keycloak and MongoDB Dev Services containers themselves),
 * so invoking it directly adds no new tooling requirement.</p>
 *
 * <p>Connects to Mongo Express's own container using
 * {@code host.docker.internal} (via {@code --add-host}, since MongoDB Dev
 * Services publishes its container port on the host, not on a fixed
 * inter-container network) and {@code directConnection=true} (confirmed
 * empirically necessary: MongoDB Dev Services' container runs as a
 * single-node replica set, and without {@code directConnection=true} the
 * MongoDB driver performs replica-set discovery using the container's
 * internal hostname, which is unresolvable from outside its network).</p>
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class MongoExpressDevService {
    private static final Logger LOG =
            Logger.getLogger(MongoExpressDevService.class);

    private static final String CONTAINER_NAME =
            "planet-importexport-mongo-express-dev";
    private static final String IMAGE = "mongo-express:latest";
    private static final int HOST_PORT = 8081;
    private static final int CONTAINER_PORT = 8081;
    private static final Pattern PORT_PATTERN =
            Pattern.compile(":(\\d+)(?:/|$|\\?)");

    @ConfigProperty(name = "quarkus.mongodb.connection-string")
    String mongoConnectionString;

    void onStart(@Observes StartupEvent event) {
        String mongoPort = extractPort(mongoConnectionString);

        if (mongoPort == null) {
            LOG.warnf("could not determine MongoDB port from connection " +
                      "string '%s'; skipping Mongo Express dev service",
                      mongoConnectionString);

            return;
        }

        removeExistingContainer();

        String mongoUrl = "mongodb://host.docker.internal:" + mongoPort +
                          "/?directConnection=true";

        int exitCode = runDocker("run", "-d",
                                 "--name", CONTAINER_NAME,
                                 "--add-host=host.docker.internal:host-gateway",
                                 "-p", HOST_PORT + ":" + CONTAINER_PORT,
                                 "-e", "ME_CONFIG_MONGODB_URL=" + mongoUrl,
                                 "-e", "ME_CONFIG_BASICAUTH=false",
                                 IMAGE);

        if (exitCode == 0) {
            LOG.infof("Mongo Express started: http://localhost:%d",
                      HOST_PORT);
        } else {
            LOG.warnf("failed to start Mongo Express dev service " +
                      "(docker exit code %d)", exitCode);
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        removeExistingContainer();
    }

    private void removeExistingContainer() {
        runDocker("rm", "-f", CONTAINER_NAME);
    }

    private static String extractPort(String connectionString) {
        Matcher matcher = PORT_PATTERN.matcher(connectionString);

        return matcher.find() ? matcher.group(1) : null;
    }

    private int runDocker(String... args) {
        String[] command = new String[args.length + 1];
        command[0] = "docker";
        System.arraycopy(args, 0, command, 1, args.length);

        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();

            return process.waitFor();
        } catch (IOException e) {
            LOG.warnf(e, "failed to invoke 'docker %s'",
                      String.join(" ", args));

            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            return -1;
        }
    }
}
