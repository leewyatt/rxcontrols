package io.github.leewyatt.rxcontrols.lrc;

import java.util.Objects;

/**
 * Immutable snapshot of a non-fatal issue found while parsing LRC text.
 *
 * @param lineNumber the 1-based source line number, or {@code -1} for document-level warnings
 * @param line       the source line or fragment associated with this warning
 * @param code       the warning category
 * @param message    the human-readable warning message
 * @throws NullPointerException if {@code line}, {@code code}, or {@code message} is {@code null}
 */
public record LrcParseWarning(int lineNumber, String line,
                              LrcWarningCode code, String message) {

    /**
     * Creates an immutable parse warning.
     */
    public LrcParseWarning {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
