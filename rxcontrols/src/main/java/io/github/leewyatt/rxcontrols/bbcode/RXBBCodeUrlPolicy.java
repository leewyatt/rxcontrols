package io.github.leewyatt.rxcontrols.bbcode;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable link-scheme allow-list for {@code [url]} / {@code [email]}.
 *
 * <p>Schemes are normalized to lower case in the constructor so a caller passing
 * {@code Set.of("HTTPS")} still matches {@code https}. A link whose scheme is not
 * in the allow-list is dropped at parse time (no link node is produced).
 *
 * @param allowedSchemes the permitted URL schemes; never {@code null}
 * @throws NullPointerException if {@code allowedSchemes} or one of its elements is {@code null}
 */
public record RXBBCodeUrlPolicy(Set<String> allowedSchemes) {

    /**
     * Creates a URL policy, normalizing schemes to lower case and defensively copying.
     */
    public RXBBCodeUrlPolicy {
        Objects.requireNonNull(allowedSchemes, "allowedSchemes");
        allowedSchemes = allowedSchemes.stream()
                .map(scheme -> scheme.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns the default URL policy: {@code http}, {@code https}, and {@code mailto}.
     *
     * @return the default URL policy
     */
    public static RXBBCodeUrlPolicy defaults() {
        return new RXBBCodeUrlPolicy(Set.of("http", "https", "mailto"));
    }

    /**
     * Returns whether the given scheme is allowed. The comparison is case-insensitive.
     *
     * @param scheme the scheme to test, or {@code null}
     * @return {@code true} if {@code scheme} is non-null and allow-listed
     */
    public boolean isSchemeAllowed(String scheme) {
        return scheme != null && allowedSchemes.contains(scheme.toLowerCase(Locale.ROOT));
    }
}
