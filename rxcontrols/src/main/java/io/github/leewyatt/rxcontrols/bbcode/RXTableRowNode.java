package io.github.leewyatt.rxcontrols.bbcode;

import java.util.List;
import java.util.Objects;

/**
 * A single table row. Not part of the {@link RXBBNode} sealed hierarchy: it only
 * appears inside a {@link RXTableNode}.
 *
 * @param cells the row's cells; never {@code null}
 * @throws NullPointerException if {@code cells} is {@code null}
 */
public record RXTableRowNode(List<RXTableCellNode> cells) {

    /**
     * Creates an immutable table row.
     */
    public RXTableRowNode {
        Objects.requireNonNull(cells, "cells");
        cells = List.copyOf(cells);
    }
}
