package com.planet.importexport.importapi.validator;

import java.util.regex.Pattern;

/**
 * Simplified RFC 5322 email validation ({@code B5}; ADR-0005 confirmed
 * decision 13): {@code user@domain.tld} — requires an {@code @} and a domain
 * with at least one dot separating a label from a top-level domain (e.g.
 * {@code marco@example} is rejected: no TLD).
 *
 * <p>This intentionally does not implement full RFC 5322 (which permits quoted
 * local parts, comments, IP-literal domains, etc.) — the import spec
 * explicitly calls for a "simplified" pattern, and the only documented
 * negative example (import spec scenario "A row with an invalid email is
 * staged") is a missing top-level domain.</p>
 */
public final class EmailValidator {

    /**
     * {@code local-part @ label(.label)+} — at least one dot after
     * {@code @}, each label non-empty.
     */
    private static final Pattern SIMPLIFIED_RFC_5322 =
            Pattern.compile("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$");

    /**
     * Not instantiable: all behavior is exposed through
     * {@link #isValid(String)}.
     */
    private EmailValidator() {
        // Utility class.
    }

    /**
     * Validates a raw {@code email} cell value against the simplified RFC 5322
     * pattern.
     *
     * @param email the raw string value from the source row; may be
     *              {@code null}
     *
     * @return {@code true} if {@code email} matches
     *         {@code user@domain.tld}, {@code false} otherwise (including
     *         {@code null} input)
     */
    public static boolean isValid(String email) {
        return email != null
            && SIMPLIFIED_RFC_5322.matcher(email).matches();
    }
}
