package io.github.leewyatt.rxcontrols.lrc;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of lenient LRC parsing.
 *
 * @param document the parsed document; never {@code null}
 * @param warnings non-fatal parse warnings
 * @throws NullPointerException if {@code document}, {@code warnings}, or one of the warnings is
 *                              {@code null}
 */
public record LrcParseResult(LrcDocument document, List<LrcParseWarning> warnings) {

    /**
     * Creates an immutable parse result.
     */
    public LrcParseResult {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(warnings, "warnings");
        warnings = List.copyOf(warnings);
    }
}
