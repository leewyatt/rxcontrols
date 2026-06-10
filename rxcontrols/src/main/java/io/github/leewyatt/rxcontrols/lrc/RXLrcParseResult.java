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
public record RXLrcParseResult(RXLrcDocument document, List<RXLrcParseWarning> warnings) {

    /**
     * Creates an immutable parse result.
     */
    public RXLrcParseResult {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(warnings, "warnings");
        warnings = List.copyOf(warnings);
    }
}
