package io.github.leewyatt.rxcontrols.bbcode;

/**
 * Inline text-style variants carried by a single {@link RXStyleNode}.
 *
 * <p>{@link #BOLD}, {@link #ITALIC}, {@link #UNDERLINE}, and {@link #STRIKETHROUGH}
 * are switch-style styles whose {@link RXStyleNode#value()} is {@code null};
 * {@link #COLOR}, {@link #SIZE}, and {@link #FONT} carry an already-validated
 * value string.
 */
public enum RXStyleType {

    /** Bold weight. */
    BOLD,

    /** Italic posture. */
    ITALIC,

    /** Underline decoration. */
    UNDERLINE,

    /** Strikethrough decoration. */
    STRIKETHROUGH,

    /** Foreground colour; the value is a validated colour token. */
    COLOR,

    /** Font size; the value is a validated pixel or keyword size. */
    SIZE,

    /** Font family; the value is a structurally validated family name. */
    FONT
}
