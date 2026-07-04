package io.github.leewyatt.rxcontrols.bbcode;

import java.util.Map;
import java.util.Objects;

/**
 * A single BBCode lexer token. Package-private: the token stream is an internal
 * detail between {@link BBCodeLexer} and {@link RXBBCodeParser}.
 */
sealed interface BBToken permits TextToken, TagOpenToken, TagCloseToken, ListItemToken {
}

/**
 * A run of literal text (including stray brackets the lexer could not lex as a tag).
 */
record TextToken(String text) implements BBToken {
    TextToken {
        Objects.requireNonNull(text, "text");
    }
}

/**
 * An opening tag such as {@code [b]}, {@code [url=..]}, or
 * {@code [img alt=.. width=..]}. The tag name and attribute keys are lower-cased;
 * the positional and attribute values are kept verbatim (quotes stripped).
 */
record TagOpenToken(String name, String positionalValue,
                    Map<String, String> attributes, String rawSource) implements BBToken {
    TagOpenToken {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(rawSource, "rawSource");
        attributes = Map.copyOf(attributes);
    }
}

/**
 * A closing tag such as {@code [/b]}. The tag name is lower-cased.
 */
record TagCloseToken(String name, String rawSource) implements BBToken {
    TagCloseToken {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(rawSource, "rawSource");
    }
}

/**
 * A list-item marker ({@code [*]}).
 */
record ListItemToken() implements BBToken {
}
