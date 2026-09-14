package com.planet.importexport.exportapi.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.planet.importexport.customerrecord.CustomerRecordDocument;

/**
 * Unit tests for {@link ExportRowProjector} (task C3/C4 shared projection
 * logic).
 */
class ExportRowProjectorTest {

    @Test
    void project_honorsExactRequestedColumnOrder_regardlessOfStorageOrder() {
        CustomerRecordDocument record =
                new CustomerRecordDocument("1",
                                           2,
                                           Map.of("name", "John Smith",
                                                  "email", "john@example.com",
                                                  "country", "Portugal"),
                                          "job-1",
                                          Instant.parse("2026-09-14T10:00:00Z"));

        List<String> row =
                ExportRowProjector.project(record,
                                           List.of("email", "id", "name"));

        assertThat(row).containsExactly("john@example.com", "1", "John Smith");
    }

    @Test
    void project_fieldAbsentFromCurrentVersion_isProjectedAsEmptyString() {
        CustomerRecordDocument record =
                new CustomerRecordDocument("2",
                                           1,
                                           Map.of("name", "Jane Doe"),
                                           "job-1",
                                           Instant.parse("2026-09-14T10:00:00Z"));

        List<String> row =
                ExportRowProjector.project(record,
                                           List.of("id", "name", "phone"));

        assertThat(row).containsExactly("2", "Jane Doe", "");
    }

    @Test
    void project_ageField_isStringified() {
        CustomerRecordDocument record =
                new CustomerRecordDocument("3",
                                           1,
                                           Map.of("age", 35),
                                           "job-1",
                                           Instant.parse("2026-09-14T10:00:00Z"));

        List<String> row = ExportRowProjector.project(record, List.of("age"));

        assertThat(row).containsExactly("35");
    }
}
