package io.github.leewyatt.rxcontrols.bbcode;

import java.util.Objects;

/**
 * Immutable snapshot of a non-fatal issue found while parsing BBCode text.
 *
 * @param position the 0-based character offset of the issue, or {@code -1} for
 *                 document-level warnings
 * @param fragment the offending token text, or {@code ""} if none applies
 * @param code     the warning category
 * @param message  the human-readable warning message
 * @throws NullPointerException if {@code fragment}, {@code code}, or {@code message} is {@code null}
 */
public record RXBBCodeParseWarning(int position, String fragment,
                                   RXBBWarningCode code, String message) {

    /**
     * Creates an immutable parse warning.
     */
    public RXBBCodeParseWarning {
        Objects.requireNonNull(fragment, "fragment");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
