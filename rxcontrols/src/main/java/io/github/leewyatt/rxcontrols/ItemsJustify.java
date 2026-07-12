package io.github.leewyatt.rxcontrols;

/**
 * How an item row or track group uses its spare horizontal width - the
 * difference between the content width and the space the cells plus the base
 * {@code hgap} gaps consume.
 *
 * <p>The first three values keep cells at the host's fixed cell width (its
 * preferred tile or column width) and only position the block.
 * The {@code SPACE_*} values keep the fixed width but grow the gaps (the
 * {@code hgap} acts as a minimum that the extra width is added on top of),
 * mirroring CSS flexbox {@code justify-content}. {@link #STRETCH} instead grows
 * the cells themselves. Values map to CSS as
 * {@code flex-start / center / flex-end / space-between / space-around /
 * space-evenly} plus {@code stretch}.
 *
 * <p>When a control uses row-based layout with several rows, a short final row
 * is laid out with the same metrics as a full row, so a given column stays
 * vertically aligned across every row; when the whole content is a single
 * partial row there is no cross-row alignment to preserve, and the block spans
 * the actual cells. Because the block never spans more than the resolvable
 * column count, {@code CENTER} / {@code END} shift full rows by less than one
 * cell step; pair them with a column cap (such as {@code maxColumns}) when a
 * large block offset is the goal.
 */
public enum ItemsJustify {

    /** Pack cells against the leading edge; trailing space stays empty. */
    START,

    /** Center the block of cells, splitting the spare width before and after it. */
    CENTER,

    /** Pack cells against the trailing edge; leading space stays empty. */
    END,

    /**
     * Distribute the spare width into the gaps between cells; the first and last
     * cells sit flush against the content edges. With a single cell this falls
     * back to {@link #START}.
     */
    SPACE_BETWEEN,

    /**
     * Give every cell an equal amount of space on both sides, so the edge gaps
     * are half the size of the gaps between cells. With a single cell the cell is
     * centered.
     */
    SPACE_AROUND,

    /**
     * Distribute the spare width so every gap, including the two edges, is
     * equal. With a single cell the cell is centered.
     */
    SPACE_EVENLY,

    /**
     * Size the cells equally so they fill the row, keeping {@code hgap} between
     * them. Cells only grow: the column count drops before a cell would shrink
     * below the host's fixed cell width. The growth is capped by the host's max
     * cell width, after which the resulting block is centered.
     */
    STRETCH
}
