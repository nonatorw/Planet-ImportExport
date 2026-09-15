package com.planet.importexport.customerrecord;

import java.util.Map;

import jakarta.inject.Inject;

import com.planet.importexport.mongo.FlapdoodleMongoTestResource;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for
 * {@link CustomerRecordRepository#findAllCurrentVersions()} (task C2),
 * proving the "current version per distinct id" aggregation against a real
 * (embedded) MongoDB instance (ADR-0001, ADR-0004).
 */
@QuarkusTest
@QuarkusTestResource(FlapdoodleMongoTestResource.class)
class CustomerRecordRepositoryFindAllCurrentVersionsTest {

    @Inject
    CustomerRecordRepository repository;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    /**
     * Across three versions for one id and one version for another id,
     * {@code findAllCurrentVersions} returns exactly one document per
     * distinct id, each holding its highest version's merged fields.
     */
    @Test
    void findAllCurrentVersions_returnsHighestVersionPerDistinctId_notEveryVersion() {
        repository.insertNextVersion("1",
                                     Map.of("name", "John Smith"),
                                     "job-1");
        repository.insertNextVersion("1",
                                     Map.of("country", "Portugal"),
                                     "job-2");
        repository.insertNextVersion("1",
                                     Map.of("country", "Spain"),
                                     "job-3");
        repository.insertNextVersion("2",
                                     Map.of("name", "Jane Doe"),
                                     "job-1");

        var currentVersions = repository.findAllCurrentVersions();

        assertThat(currentVersions).hasSize(2);

        var currentForId1 =
                currentVersions.stream()
                               .filter(document -> document.recordId.equals("1"))
                               .findFirst()
                               .orElseThrow();
        var currentForId2 =
                currentVersions.stream()
                               .filter(document -> document.recordId.equals("2"))
                               .findFirst()
                               .orElseThrow();

        Assertions.assertThat(currentForId1.version)
                  .isEqualTo(3);

        Assertions.assertThat(currentForId1.fields.get("country"))
                  .isEqualTo("Spain");

        Assertions.assertThat(currentForId1.fields.get("name"))
                  .isEqualTo("John Smith");

        Assertions.assertThat(currentForId2.version)
                  .isEqualTo(1);
    }

    /**
     * When the collection holds no records at all, {@code findAllCurrentVersions}
     * returns an empty list rather than throwing.
     */
    @Test
    void findAllCurrentVersions_emptyCollection_returnsEmptyList() {
        assertThat(repository.findAllCurrentVersions()).isEmpty();
    }

    /**
     * With a single id holding exactly one version, that lone version is
     * returned as the id's current version.
     */
    @Test
    void findAllCurrentVersions_singleIdSingleVersion_returnsThatVersion() {
        repository.insertNextVersion("42",
                                     Map.of("name", "Solo Record"),
                                     "job-1");

        var currentVersions = repository.findAllCurrentVersions();

        Assertions.assertThat(currentVersions)
                  .hasSize(1);

        Assertions.assertThat(currentVersions.get(0).recordId)
                  .isEqualTo("42");

        Assertions.assertThat(currentVersions.get(0).version)
                  .isEqualTo(1);
    }
}
