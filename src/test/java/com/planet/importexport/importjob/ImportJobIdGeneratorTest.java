package com.planet.importexport.importjob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test for {@link ImportJobIdGenerator}. The random source is injected as a {@code
 * Supplier<UUID>} so the exact generated id can be asserted deterministically, without relying on
 * Mockito to stub the static/final {@code UUID.randomUUID()} (per
 * {@code @421-frameworks-quarkus-testing-unit-tests}, Example 2).
 */
class ImportJobIdGeneratorTest {

    @Test
    void prefixesGeneratedIdWithJobDash() {
        UUID fixedUuid = UUID.fromString("abc12345-0000-0000-0000-000000000000");
        ImportJobIdGenerator generator = new ImportJobIdGenerator(() -> fixedUuid);

        assertEquals("job-abc12345-0000-0000-0000-000000000000", generator.generate());
    }

    @Test
    void defaultConstructorProducesUniqueIds() {
        ImportJobIdGenerator generator = new ImportJobIdGenerator();

        String first = generator.generate();
        String second = generator.generate();

        assertTrue(first.startsWith("job-"));
        assertNotEquals(first, second);
    }
}
