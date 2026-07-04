package io.github.leewyatt.rxcontrols.bbcode;

import java.util.Objects;

/**
 * A literal source echo, produced when {@code showMalformedTagsAsText} is enabled
 * for an unknown or orphaned tag so the original markup is shown verbatim.
 *
 * @param literal the literal source text; never {@code null}
 * @throws NullPointerException if {@code literal} is {@code null}
 */
public record RXRawTextNode(String literal) implements RXBBInlineNode {

    /**
     * Creates a raw-text node.
     */
    public RXRawTextNode {
        Objects.requireNonNull(literal, "literal");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> R accept(RXBBInlineNodeVisitor<R> visitor) {
        return visitor.visitRawText(this);
    }
}
