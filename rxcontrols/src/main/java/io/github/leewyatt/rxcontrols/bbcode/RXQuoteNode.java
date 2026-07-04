package io.github.leewyatt.rxcontrols.bbcode;

import java.util.List;
import java.util.Objects;

/**
 * A block quote, optionally attributed to an author.
 *
 * @param author   the quoted author, or {@code null} if none was given
 * @param children the quote's block-level children; never {@code null}
 * @throws NullPointerException if {@code children} is {@code null}
 */
public record RXQuoteNode(String author, List<RXBBBlockNode> children) implements RXBBBlockNode {

    /**
     * Creates an immutable quote.
     */
    public RXQuoteNode {
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> R accept(RXBBBlockNodeVisitor<R> visitor) {
        return visitor.visitQuote(this);
    }
}
