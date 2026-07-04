package io.github.leewyatt.rxcontrols.bbcode;

import java.util.List;
import java.util.Objects;

/**
 * A collapsible spoiler, initially hidden and revealed on click.
 *
 * @param label    the header label, or {@code null} to use the default label
 * @param children the spoiler's block-level children; never {@code null}
 * @throws NullPointerException if {@code children} is {@code null}
 */
public record RXSpoilerNode(String label, List<RXBBBlockNode> children) implements RXBBBlockNode {

    /**
     * Creates an immutable spoiler.
     */
    public RXSpoilerNode {
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> R accept(RXBBBlockNodeVisitor<R> visitor) {
        return visitor.visitSpoiler(this);
    }
}
