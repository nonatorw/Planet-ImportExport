package com.planet.importexport.customerrecord;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import com.planet.importexport.mongo.FlapdoodleMongoTestResource;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.panache.common.Sort;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test proving {@link CustomerRecordRepository} persistence and
 * the "current version = highest version per id" query actually work against
 * a real (embedded) MongoDB instance (ADR-0001), reusing the existing
 * {@link FlapdoodleMongoTestResource} started for Group A0.
 */
@QuarkusTest
@QuarkusTestResource(FlapdoodleMongoTestResource.class)
class CustomerRecordRepositoryTest {

    @Inject
    CustomerRecordRepository repository;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void insertNextVersion_withNoPriorVersion_insertsVersion1AsGiven() {
        CustomerRecordDocument inserted =
                repository.insertNextVersion("1",
                                             Map.of("name", "John Smith",
                                                    "country", "Portugal"),
                                             "job-1");

        assertEquals(1, inserted.version);
        assertEquals("John Smith", inserted.fields.get("name"));
        assertEquals("Portugal", inserted.fields.get("country"));
        assertEquals("job-1", inserted.sourceJobId);
    }

    @Test
    void insertNextVersion_reImportedId_createsVersion2WithoutMutatingVersion1() {
        repository.insertNextVersion("1",
                                     Map.of("name", "John Smith",
                                            "country", "Portugal"),
                                     "job-1");

        CustomerRecordDocument secondVersion =
                repository.insertNextVersion("1",
                                             Map.of("phone", "+351910000000"),
                                             "job-2");

        assertEquals(2, secondVersion.version);

        Optional<CustomerRecordDocument> version1 =
                repository.findVersion("1", 1);

        assertTrue(version1.isPresent());
        assertEquals("John Smith", version1.get().fields.get("name"));
        assertFalse(version1.get().fields.containsKey("phone"));
    }

    @Test
    void insertNextVersion_fieldAbsentFromLaterFile_isInheritedFromPriorVersion() {
        repository.insertNextVersion("1",
                                     Map.of("name", "John Smith",
                                            "country", "Portugal"),
                                     "job-1");

        CustomerRecordDocument secondVersion =
                repository.insertNextVersion("1",
                                             Map.of("country", "Spain"),
                                             "job-2");

        assertEquals("John Smith", secondVersion.fields.get("name"));
        assertEquals("Spain", secondVersion.fields.get("country"));
    }

    @Test
    void findCurrentVersion_returnsHighestVersionDocument() {
        repository.insertNextVersion("1",
                                     Map.of("name", "John Smith"),
                                     "job-1");
        repository.insertNextVersion("1",
                                     Map.of("country", "Portugal"),
                                     "job-2");
        repository.insertNextVersion("1",
                                     Map.of("country", "Spain"),
                                     "job-3");

        Optional<CustomerRecordDocument> current =
                repository.findCurrentVersion("1");

        assertTrue(current.isPresent());
        assertEquals(3, current.get().version);
        assertEquals("Spain", current.get().fields.get("country"));
    }

    @Test
    void findCurrentVersion_unknownId_returnsEmpty() {
        assertTrue(repository.findCurrentVersion("does-not-exist")
                             .isEmpty());
    }

    @Test
    void distinctIds_versionAcrossIds_doNotInterfere() {
        repository.insertNextVersion("1",
                                     Map.of("name", "John Smith"),
                                     "job-1");
        repository.insertNextVersion("2",
                                     Map.of("name", "Jane Doe"),
                                     "job-1");
        repository.insertNextVersion("1",
                                     Map.of("country", "Portugal"),
                                     "job-2");

        Optional<CustomerRecordDocument> currentForId1 =
                repository.findCurrentVersion("1");

        Optional<CustomerRecordDocument> currentForId2 =
                repository.findCurrentVersion("2");

        assertEquals(2, currentForId1.orElseThrow().version);
        assertEquals(1, currentForId2.orElseThrow().version);
    }

    @Test
    void uniqueCompoundIndex_onIdAndVersion_rejectsDuplicateVersionForSameId() {
        repository.insertNextVersion("1",
                                     Map.of("name", "John Smith"),
                                     "job-1");

        // Bypass the repository's own version-computation to directly attempt
        // a duplicate (id, version) pair, proving the unique compound index
        // (design.md section 1.1) is the enforcement mechanism, not merely
        // application-level convention.
        Document duplicateVersion1 =
                new Document("id", "1")
                        .append("version", 1)
                        .append("fields", new Document("name", "Someone Else"))
                        .append("sourceJobId", "job-2")
                        .append("createdAt", new Date());

        MongoWriteException exception =
                assertThrows(MongoWriteException.class,
                             () -> repository.mongoCollection()
                                             .withDocumentClass(Document.class)
                                             .insertOne(duplicateVersion1));

        assertEquals(ErrorCategory.DUPLICATE_KEY, exception.getError().getCategory());
    }

    @Test
    void enumeratingAllVersions_forGivenId_isStrictlyIncreasingAndGapless() {
        repository.insertNextVersion("1",
                                     Map.of("name", "John Smith"),
                                     "job-1");
        repository.insertNextVersion("1",
                                     Map.of("country", "Portugal"),
                                     "job-2");
        repository.insertNextVersion("1",
                                     Map.of("phone", "+351910000000"),
                                     "job-3");

        List<CustomerRecordDocument> allVersions =
                repository.find("id",
                                Sort.ascending("version"), "1")
                          .list();

        assertEquals(3, allVersions.size());
        assertEquals(1, allVersions.get(0).version);
        assertEquals(2, allVersions.get(1).version);
        assertEquals(3, allVersions.get(2).version);
    }
}
