package io.github.leewyatt.rxcontrols.bbcode;

import java.util.List;
import java.util.Objects;

/**
 * A block-level heading ({@code [h1]}&hellip;{@code [h6]} / {@code [heading]}).
 *
 * <p>{@code level} is clamped into the range {@code 1..6}. The skin seeds an
 * inline style stack from the level (base size + bold), over which inner inline
 * styles still compose.
 *
 * @param level    the heading level, clamped into {@code 1..6}
 * @param children the heading's inline children; never {@code null}
 * @throws NullPointerException if {@code children} is {@code null}
 */
public record RXHeadingNode(int level, List<RXBBInlineNode> children) implements RXBBBlockNode {

    private static final int MIN_LEVEL = 1;
    private static final int MAX_LEVEL = 6;

    /**
     * Creates an immutable heading, clamping {@code level} into {@code 1..6}.
     */
    public RXHeadingNode {
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children);
        level = Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> R accept(RXBBBlockNodeVisitor<R> visitor) {
        return visitor.visitHeading(this);
    }
}
