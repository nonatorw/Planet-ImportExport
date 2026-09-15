package com.planet.importexport.importjob;

import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test for {@link ImportJobIdGenerator}. The random source is
 * injected as a {@code Supplier<UUID>} so the exact generated id can be
 * asserted deterministically, without relying on Mockito to stub the
 * static/final {@code UUID.randomUUID()} (per
 * {@code @421-frameworks-quarkus-testing-unit-tests}, Example 2).
 */
class ImportJobIdGeneratorTest {
    /**
     * With the random source stubbed to a fixed {@link UUID}, the generated
     * id is exactly {@code "job-"} followed by that UUID's string form.
     */
    @Test
    void prefixesGeneratedIdWithJobDash() {
        UUID fixedUuid =
                UUID.fromString("abc12345-0000-0000-0000-000000000000");

        ImportJobIdGenerator generator =
                new ImportJobIdGenerator(() -> fixedUuid);

        Assertions.assertEquals("job-abc12345-0000-0000-0000-000000000000",
                                generator.generate());
    }

    /**
     * A generator built with the default (real random) constructor produces
     * ids that start with {@code "job-"} and differ from one call to the
     * next.
     */
    @Test
    void defaultConstructorProducesUniqueIds() {
        ImportJobIdGenerator generator = new ImportJobIdGenerator();

        String first = generator.generate();
        String second = generator.generate();

        Assertions.assertTrue(first.startsWith("job-"));

        Assertions.assertNotEquals(first, second);
    }
}
