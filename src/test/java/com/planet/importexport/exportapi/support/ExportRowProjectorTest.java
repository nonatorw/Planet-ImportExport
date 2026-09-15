package com.planet.importexport.exportapi.support;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.planet.importexport.customerrecord.CustomerRecordDocument;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ExportRowProjector} (task C3/C4 shared projection
 * logic).
 */
class ExportRowProjectorTest {

    /**
     * Projecting a record's fields onto a requested column list returns the
     * values in exactly that requested order, regardless of the order the
     * fields are stored in the document's map.
     */
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

        Assertions.assertThat(row)
                  .containsExactly("john@example.com",
                                   "1",
                                   "John Smith");
    }

    /**
     * A requested column whose field is absent from the record's current
     * version is projected as an empty string rather than {@code null} or
     * throwing.
     */
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

        Assertions.assertThat(row)
                  .containsExactly("2",
                                   "Jane Doe", "");
    }

    /**
     * A non-string field value such as {@code age} (stored as an
     * {@code Integer}) is converted to its string representation when
     * projected.
     */
    @Test
    void project_ageField_isStringified() {
        CustomerRecordDocument record =
                new CustomerRecordDocument("3",
                                           1,
                                           Map.of("age", 35),
                                           "job-1",
                                           Instant.parse("2026-09-14T10:00:00Z"));

        List<String> row = ExportRowProjector.project(record, List.of("age"));

        Assertions.assertThat(row)
                  .containsExactly("35");
    }
}
