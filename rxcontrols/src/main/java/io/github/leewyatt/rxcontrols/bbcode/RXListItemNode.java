package io.github.leewyatt.rxcontrols.bbcode;

import java.util.List;
import java.util.Objects;

/**
 * A single item of a {@link RXListNode}. Not part of the {@link RXBBNode} sealed
 * hierarchy: it only appears inside a list and has no standalone visitor method.
 *
 * @param children the item's block-level children; never {@code null}
 * @throws NullPointerException if {@code children} is {@code null}
 */
public record RXListItemNode(List<RXBBBlockNode> children) {

    /**
     * Creates an immutable list item.
     */
    public RXListItemNode {
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children);
    }
}
