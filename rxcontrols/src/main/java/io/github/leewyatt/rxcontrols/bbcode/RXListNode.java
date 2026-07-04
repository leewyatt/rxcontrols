package io.github.leewyatt.rxcontrols.bbcode;

import java.util.List;
import java.util.Objects;

/**
 * An ordered or unordered list.
 *
 * @param kind  whether the list is ordered or unordered; never {@code null}
 * @param items the list items; never {@code null}
 * @throws NullPointerException if {@code kind} or {@code items} is {@code null}
 */
public record RXListNode(RXListKind kind, List<RXListItemNode> items) implements RXBBBlockNode {

    /**
     * Creates an immutable list.
     */
    public RXListNode {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(items, "items");
        items = List.copyOf(items);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> R accept(RXBBBlockNodeVisitor<R> visitor) {
        return visitor.visitList(this);
    }
}
