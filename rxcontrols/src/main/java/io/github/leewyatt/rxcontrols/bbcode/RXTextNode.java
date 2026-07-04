package io.github.leewyatt.rxcontrols.bbcode;

import java.util.Objects;

/**
 * A plain text run.
 *
 * @param text the run text; never {@code null}
 * @throws NullPointerException if {@code text} is {@code null}
 */
public record RXTextNode(String text) implements RXBBInlineNode {

    /**
     * Creates a text node.
     */
    public RXTextNode {
        Objects.requireNonNull(text, "text");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> R accept(RXBBInlineNodeVisitor<R> visitor) {
        return visitor.visitText(this);
    }
}
