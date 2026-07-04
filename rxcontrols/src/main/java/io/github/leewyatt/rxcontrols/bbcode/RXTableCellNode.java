package io.github.leewyatt.rxcontrols.bbcode;

import java.util.List;
import java.util.Objects;

/**
 * A single table cell. Not part of the {@link RXBBNode} sealed hierarchy: it only
 * appears inside a {@link RXTableRowNode}.
 *
 * @param header   whether this is a header cell ({@code [th]})
 * @param children the cell's block-level children; never {@code null}
 * @throws NullPointerException if {@code children} is {@code null}
 */
public record RXTableCellNode(boolean header, List<RXBBBlockNode> children) {

    /**
     * Creates an immutable table cell.
     */
    public RXTableCellNode {
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children);
    }
}
