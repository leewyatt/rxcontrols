package io.github.leewyatt.rxcontrols.enums;

import java.util.Locale;

/**
 * Visual variant of a Material-style field
 * ({@code RXMaterialTextField} / {@code RXMaterialPasswordField}).
 *
 * <ul>
 *   <li>{@link #UNDERLINE} — transparent container with a bottom activation
 *       line (the default; the M2 / JFoenix desktop look).</li>
 *   <li>{@link #FILLED} — lightly filled container with a bottom activation
 *       line.</li>
 *   <li>{@link #OUTLINED} — four-side outline with a notch at the top edge
 *       (reserved; not yet rendered).</li>
 * </ul>
 */
public enum RXFieldVariant {

    UNDERLINE,
    FILLED,
    OUTLINED;

    /**
     * Resolves a CSS keyword to a variant. The keyword is lower-cased and
     * underscores are accepted in place of hyphens; an unknown or {@code null}
     * keyword returns {@code null}, which use-sites treat as "fall back to the
     * default".
     * <p>
     * This deliberately does not delegate to {@link Enum#valueOf(Class, String)}:
     * the JDK method is case-sensitive and throws
     * {@link IllegalArgumentException} for unknown values, whereas
     * {@code io.github.leewyatt.rxcontrols.internal.KeywordConverter} calls the
     * resolver without a {@code try}/{@code catch} and expects {@code null} for
     * unknown keywords.
     *
     * @param keyword the CSS keyword, may be {@code null}
     * @return the matching variant, or {@code null} if the keyword is unknown
     */
    public static RXFieldVariant fromKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        switch (keyword.trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "underline":
                return UNDERLINE;
            case "filled":
                return FILLED;
            case "outlined":
                return OUTLINED;
            default:
                return null;
        }
    }
}
