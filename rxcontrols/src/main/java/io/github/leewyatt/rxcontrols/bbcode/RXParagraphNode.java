package io.github.leewyatt.rxcontrols.bbcode;

import java.util.List;
import java.util.Objects;

/**
 * A paragraph: a run of inline content rendered into one {@code TextFlow}.
 *
 * @param children the paragraph's inline children; never {@code null}
 * @throws NullPointerException if {@code children} is {@code null}
 */
public record RXParagraphNode(List<RXBBInlineNode> children) implements RXBBBlockNode {

    /**
     * Creates an immutable paragraph.
     */
    public RXParagraphNode {
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> R accept(RXBBBlockNodeVisitor<R> visitor) {
        return visitor.visitParagraph(this);
    }
}
