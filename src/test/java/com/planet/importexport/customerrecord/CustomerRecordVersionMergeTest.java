package com.planet.importexport.customerrecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Pure unit tests for the ADR-0004 version-N+1 merge rule:
 * "fields(N+1) = fields(N) overridden by any recognized fields present in the
 * new row." No MongoDB or CDI container required.
 *
 * <p>Uses plain JUnit 5 assertions: AssertJ is not currently on the test
 * classpath (not declared in {@code build.gradle}) — see this task's final
 * report for the flagged gap.
 */
class CustomerRecordVersionMergeTest {

    @Test
    void firstVersion_hasNoPredecessor_usesIncomingFieldsAsGiven() {
        Map<String, Object> previousVersionFields =
                Map.of();
        Map<String, Object> incomingRow =
                Map.of("name", "John Smith",
                       "email", "john@example.com");

        Map<String, Object> merged =
                CustomerRecordVersionMerge.merge(previousVersionFields,
                                                 incomingRow);

        assertEquals(incomingRow, merged);
    }

    @Test
    void additiveField_notPresentBefore_isAddedWithoutLosingPriorFields() {
        Map<String, Object> previousVersionFields =
                Map.of("name", "John Smith",
                       "country", "Portugal");

        Map<String, Object> incomingRow =
                Map.of("phone", "+351910000000");

        Map<String, Object> merged =
                CustomerRecordVersionMerge.merge(previousVersionFields,
                                                 incomingRow);

        assertEquals("John Smith", merged.get("name"));
        assertEquals("Portugal", merged.get("country"));
        assertEquals("+351910000000", merged.get("phone"));
    }

    @Test
    void conflictingValue_incomingRowWins_overPreviousVersion() {
        Map<String, Object> previousVersionFields =
                Map.of("country", "Portugal");

        Map<String, Object> incomingRow =
                Map.of("country", "Spain");

        Map<String, Object> merged =
                CustomerRecordVersionMerge.merge(previousVersionFields,
                                                 incomingRow);

        assertEquals("Spain", merged.get("country"));
    }

    @Test
    void fieldAbsentFromIncomingRow_isInheritedUnchanged_notCleared() {
        Map<String, Object> previousVersionFields = new LinkedHashMap<>();
        previousVersionFields.put("name", "John Smith");
        previousVersionFields.put("age", 35);
        Map<String, Object> incomingRow = Map.of("age", 36);

        Map<String, Object> merged =
                CustomerRecordVersionMerge.merge(previousVersionFields,
                                                 incomingRow);

        assertEquals("John Smith", merged.get("name"));
        assertEquals(36, merged.get("age"));
    }

    @Test
    void cumulativeThreeFileWorkedExample_unionOfAllFieldsEverSupplied_latestWinsOnOverlap() {
        // Mirrors the PDF's worked example (id = 1 across three files) referenced by ADR-0004.
        Map<String, Object> version1 =
                CustomerRecordVersionMerge.merge(
                        Map.of(),
                        Map.of("name", "John Smith",
                               "country", "Portugal"));

        Map<String, Object> version2 =
                CustomerRecordVersionMerge.merge(
                        version1,
                        Map.of("phone", "+351910000000"));

        Map<String, Object> version3 =
                CustomerRecordVersionMerge.merge(
                        version2,
                        Map.of("country", "Spain"));

        assertEquals("John Smith", version3.get("name"));
        assertEquals("+351910000000", version3.get("phone"));
        assertEquals("Spain", version3.get("country"));
    }

    @Test
    void unrecognizedIncomingKey_isRejected() {
        Map<String, Object> previousVersionFields = Map.of();
        Map<String, Object> incomingRow = Map.of("loyalty_tier", "gold");

        Executable executable =
                () -> CustomerRecordVersionMerge.merge(previousVersionFields,
                                                       incomingRow);

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class,
                             executable);

        assertTrue(exception.getMessage()
                            .contains("loyalty_tier"));
    }

    @Test
    void inputMaps_areNotMutated() {
        Map<String, Object> previousVersionFields =
                new LinkedHashMap<>(Map.of("name", "John Smith"));
        Map<String, Object> incomingRow =
                new LinkedHashMap<>(Map.of("country", "Portugal"));

        CustomerRecordVersionMerge.merge(previousVersionFields, incomingRow);

        assertEquals(Set.of("name"), previousVersionFields.keySet());
        assertEquals(Set.of("country"), incomingRow.keySet());
    }
}
