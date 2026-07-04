package io.github.leewyatt.rxcontrols.bbcode;

import java.util.List;
import java.util.Objects;

/**
 * A table with no cell spanning or nesting.
 *
 * @param rows the table rows; never {@code null}
 * @throws NullPointerException if {@code rows} is {@code null}
 */
public record RXTableNode(List<RXTableRowNode> rows) implements RXBBBlockNode {

    /**
     * Creates an immutable table.
     */
    public RXTableNode {
        Objects.requireNonNull(rows, "rows");
        rows = List.copyOf(rows);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> R accept(RXBBBlockNodeVisitor<R> visitor) {
        return visitor.visitTable(this);
    }
}
