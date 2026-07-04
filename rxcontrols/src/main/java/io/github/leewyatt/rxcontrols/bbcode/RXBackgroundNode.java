package io.github.leewyatt.rxcontrols.bbcode;

import java.util.List;
import java.util.Objects;

/**
 * A block whose children are painted over a solid background colour.
 *
 * <p>The colour is a pre-validated CSS colour token (as accepted by {@code [color]}),
 * so the renderer applies it with a typed {@code setBackground} call — never inline CSS.
 * A {@code null} colour renders a plain container with no tint.
 *
 * @param color    the validated background colour, or {@code null} if none / invalid
 * @param children the block-level children; never {@code null}
 * @throws NullPointerException if {@code children} is {@code null}
 */
public record RXBackgroundNode(String color, List<RXBBBlockNode> children) implements RXBBBlockNode {

    /**
     * Creates an immutable background block.
     */
    public RXBackgroundNode {
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> R accept(RXBBBlockNodeVisitor<R> visitor) {
        return visitor.visitBackground(this);
    }
}
