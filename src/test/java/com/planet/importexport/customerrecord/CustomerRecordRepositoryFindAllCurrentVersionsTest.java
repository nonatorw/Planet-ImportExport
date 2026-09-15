package com.planet.importexport.customerrecord;

import java.util.Map;

import jakarta.inject.Inject;

import com.planet.importexport.mongo.FlapdoodleMongoTestResource;

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

        assertThat(currentForId1.version).isEqualTo(3);
        assertThat(currentForId1.fields.get("country")).isEqualTo("Spain");
        assertThat(currentForId1.fields.get("name")).isEqualTo("John Smith");
        assertThat(currentForId2.version).isEqualTo(1);
    }

    @Test
    void findAllCurrentVersions_emptyCollection_returnsEmptyList() {
        assertThat(repository.findAllCurrentVersions()).isEmpty();
    }

    @Test
    void findAllCurrentVersions_singleIdSingleVersion_returnsThatVersion() {
        repository.insertNextVersion("42", Map.of("name", "Solo Record"), "job-1");

        var currentVersions = repository.findAllCurrentVersions();

        assertThat(currentVersions).hasSize(1);
        assertThat(currentVersions.get(0).recordId).isEqualTo("42");
        assertThat(currentVersions.get(0).version).isEqualTo(1);
    }
}
