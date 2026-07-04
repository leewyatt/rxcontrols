package io.github.leewyatt.rxcontrols.bbcode;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of lenient BBCode parsing.
 *
 * @param document the parsed document; never {@code null}
 * @param warnings non-fatal parse warnings
 * @throws NullPointerException if {@code document}, {@code warnings}, or one of the warnings is
 *                              {@code null}
 */
public record RXBBCodeParseResult(RXBBDocument document, List<RXBBCodeParseWarning> warnings) {

    /**
     * Creates an immutable parse result.
     */
    public RXBBCodeParseResult {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(warnings, "warnings");
        warnings = List.copyOf(warnings);
    }
}
