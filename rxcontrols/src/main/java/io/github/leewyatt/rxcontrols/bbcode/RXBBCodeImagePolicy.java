package io.github.leewyatt.rxcontrols.bbcode;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable image policy: the scheme allow-list and the master load switch.
 *
 * <p>Images are rendered at their natural size, exactly as a bare {@code ImageView}
 * would — no size or decode ceiling is imposed here. Bounding an image's <em>display</em>
 * size is the caller's job via the view's {@code imageMaxWidth} / {@code imageMaxHeight},
 * which cap the {@code ImageView} fit — not the decoded bitmap. <em>Memory</em> is not
 * bounded here at all: the bitmap is always decoded at the source resolution, so capping
 * it requires {@code loadImages=false} or an upstream proxy that downscales.
 *
 * @param allowedSchemes the permitted image URL schemes; never {@code null}
 * @param loadImages     whether images are loaded at all; {@code false} renders alt placeholders with no network access
 * @throws NullPointerException if {@code allowedSchemes} or one of its elements is {@code null}
 */
public record RXBBCodeImagePolicy(Set<String> allowedSchemes, boolean loadImages) {

    /**
     * Creates an image policy, normalizing schemes to lower case.
     */
    public RXBBCodeImagePolicy {
        Objects.requireNonNull(allowedSchemes, "allowedSchemes");
        allowedSchemes = allowedSchemes.stream()
                .map(scheme -> scheme.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns the default image policy: {@code http} / {@code https}, loading enabled.
     *
     * @return the default image policy
     */
    public static RXBBCodeImagePolicy defaults() {
        return new RXBBCodeImagePolicy(Set.of("http", "https"), true);
    }

    /**
     * Returns whether the given scheme is an allowed image scheme. The comparison
     * is case-insensitive.
     *
     * @param scheme the scheme to test, or {@code null}
     * @return {@code true} if {@code scheme} is non-null and allow-listed
     */
    public boolean isSchemeAllowed(String scheme) {
        return scheme != null && allowedSchemes.contains(scheme.toLowerCase(Locale.ROOT));
    }
}
