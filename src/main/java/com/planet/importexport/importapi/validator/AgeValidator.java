package com.planet.importexport.importapi.validator;

/**
 * Age validation ({@code B5}; ADR-0005 confirmed decision 14): must be an
 * integer in the range 0-120 inclusive.
 * Non-numeric values (e.g. {@code "thirty"}) and out-of-range values (e.g.
 * {@code "121"}, {@code "-1"}) are both invalid.
 */
public final class AgeValidator {

    /** Inclusive lower bound of a valid age ({@code B5}; ADR-0005 decision 14). */
    private static final int MIN_AGE = 0;

    /** Inclusive upper bound of a valid age ({@code B5}; ADR-0005 decision 14). */
    private static final int MAX_AGE = 120;

    /** Not instantiable: all behavior is exposed through {@link #isValid(String)}. */
    private AgeValidator() {
        // Utility class.
    }

    /**
     * Validates a raw {@code age} cell value against the recognized range.
     *
     * @param age the raw string value from the source row; may be
     *            {@code null}
     * @return {@code true} if {@code age} parses as an integer in
     *         {@code [0, 120]}, {@code false} otherwise (including
     *         {@code null} or non-numeric input)
     */
    public static boolean isValid(String age) {
        if (age == null) {
            return false;
        }

        try {
            int parsed = Integer.parseInt(age.trim());
            return parsed >= MIN_AGE && parsed <= MAX_AGE;

        } catch (NumberFormatException e) {
            return false;
        }
    }
}
