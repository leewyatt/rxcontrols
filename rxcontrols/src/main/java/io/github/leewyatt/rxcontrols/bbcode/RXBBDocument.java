package io.github.leewyatt.rxcontrols.bbcode;

import java.util.List;
import java.util.Objects;

/**
 * Immutable root of a parsed BBCode document: a sequence of block-level nodes.
 *
 * @param children the top-level block children; never {@code null}
 * @throws NullPointerException if {@code children} is {@code null}
 */
public record RXBBDocument(List<RXBBBlockNode> children) {

    private static final RXBBDocument EMPTY = new RXBBDocument(List.of());

    /**
     * Creates an immutable document.
     */
    public RXBBDocument {
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children);
    }

    /**
     * Returns the shared empty document.
     *
     * @return an immutable document with no block children
     */
    public static RXBBDocument empty() {
        return EMPTY;
    }

    /**
     * Returns whether this document has no block children.
     *
     * @return {@code true} if the document is empty
     */
    public boolean isEmpty() {
        return children.isEmpty();
    }
}
