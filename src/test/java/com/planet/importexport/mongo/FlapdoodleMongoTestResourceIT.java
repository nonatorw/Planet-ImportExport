package com.planet.importexport.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mongodb.client.MongoClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/**
 * Scaffolding-level smoke test (Group A0.2): confirms the Flapdoodle-backed embedded MongoDB
 * instance actually starts and is reachable through the Quarkus-managed {@link MongoClient},
 * per ADR-0001. Does not exercise any business repository — those are built in Group A1-A4.
 */
@QuarkusTest
@QuarkusTestResource(FlapdoodleMongoTestResource.class)
class FlapdoodleMongoTestResourceIT {

    @Inject
    MongoClient mongoClient;

    @Test
    void embeddedMongoRespondsToPing() {
        Document result = mongoClient.getDatabase("admin").runCommand(new Document("ping", 1));
        assertEquals(1.0, result.getDouble("ok"));
    }
}
