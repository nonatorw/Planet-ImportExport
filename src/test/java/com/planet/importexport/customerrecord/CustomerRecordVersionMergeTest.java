package com.planet.importexport.customerrecord;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Pure unit tests for the ADR-0004 version-N+1 merge rule:
 * "fields(N+1) = fields(N) overridden by any recognized fields present in the
 * new row." No MongoDB or CDI container required.
 *
 * <p>Uses plain JUnit 5 assertions: AssertJ is not currently on the test
 * classpath (not declared in {@code build.gradle}) — see this task's final
 * report for the flagged gap.</p>
 */
class CustomerRecordVersionMergeTest {

    /**
     * When there is no prior version (empty previous fields), the merge
     * result is exactly the incoming row's fields, unchanged.
     */
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

        Assertions.assertEquals(incomingRow, merged);
    }

    /**
     * A field present only in the incoming row is added to the merged
     * result while every field from the previous version is preserved.
     */
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

        Assertions.assertEquals("John Smith", merged.get("name"));
        Assertions.assertEquals("Portugal", merged.get("country"));
        Assertions.assertEquals("+351910000000", merged.get("phone"));
    }

    /**
     * When the same field is present in both the previous version and the
     * incoming row with different values, the incoming row's value wins.
     */
    @Test
    void conflictingValue_incomingRowWins_overPreviousVersion() {
        Map<String, Object> previousVersionFields =
                Map.of("country", "Portugal");

        Map<String, Object> incomingRow =
                Map.of("country", "Spain");

        Map<String, Object> merged =
                CustomerRecordVersionMerge.merge(previousVersionFields,
                                                 incomingRow);

        Assertions.assertEquals("Spain", merged.get("country"));
    }

    /**
     * A field from the previous version that is absent from the incoming
     * row is inherited unchanged into the merged result, not cleared.
     */
    @Test
    void fieldAbsentFromIncomingRow_isInheritedUnchanged_notCleared() {
        Map<String, Object> previousVersionFields = new LinkedHashMap<>();
        previousVersionFields.put("name", "John Smith");
        previousVersionFields.put("age", 35);
        Map<String, Object> incomingRow = Map.of("age", 36);

        Map<String, Object> merged =
                CustomerRecordVersionMerge.merge(previousVersionFields,
                                                 incomingRow);

        Assertions.assertEquals("John Smith", merged.get("name"));
        Assertions.assertEquals(36, merged.get("age"));
    }

    /**
     * Chaining {@code merge} across three successive imports for the same id
     * yields the union of every field ever supplied, with the most recent
     * import's value winning whenever a field was supplied more than once.
     */
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

        Assertions.assertEquals("John Smith", version3.get("name"));
        Assertions.assertEquals("+351910000000", version3.get("phone"));
        Assertions.assertEquals("Spain", version3.get("country"));
    }

    /**
     * An incoming row containing a key that is not a recognized field is
     * rejected with an {@link IllegalArgumentException} naming that key.
     */
    @Test
    void unrecognizedIncomingKey_isRejected() {
        Map<String, Object> previousVersionFields = Map.of();
        Map<String, Object> incomingRow = Map.of("loyalty_tier", "gold");

        Executable executable =
                () -> CustomerRecordVersionMerge.merge(previousVersionFields,
                                                       incomingRow);

        IllegalArgumentException exception =
                Assertions.assertThrows(IllegalArgumentException.class,
                                        executable);

        Assertions.assertTrue(exception.getMessage()
                                       .contains("loyalty_tier"));
    }

    /**
     * Merging does not mutate either input map: both the previous version's
     * fields and the incoming row keep their original keys afterward.
     */
    @Test
    void inputMaps_areNotMutated() {
        Map<String, Object> previousVersionFields =
                new LinkedHashMap<>(Map.of("name", "John Smith"));

        Map<String, Object> incomingRow =
                new LinkedHashMap<>(Map.of("country", "Portugal"));

        CustomerRecordVersionMerge.merge(previousVersionFields, incomingRow);

        Assertions.assertEquals(Set.of("name"),
                                previousVersionFields.keySet());

        Assertions.assertEquals(Set.of("country"),
                                incomingRow.keySet());
    }
}
