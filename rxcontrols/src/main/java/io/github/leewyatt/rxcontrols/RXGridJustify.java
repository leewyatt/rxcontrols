package io.github.leewyatt.rxcontrols;

/**
 * How a grid row uses its spare horizontal width — the difference between the
 * content width and the space the cells plus the base {@code hgap} gaps consume.
 *
 * <p>The first three values keep cells at their fixed
 * {@code cellWidth} and only position the block.
 * The {@code SPACE_*} values keep the fixed width but grow the gaps (the
 * {@code hgap} acts as a minimum that the extra width is added on top of),
 * mirroring CSS flexbox {@code justify-content}. {@link #STRETCH} instead grows
 * the cells themselves. Values map to CSS as
 * {@code flex-start / center / flex-end / space-between / space-around /
 * space-evenly} plus {@code stretch}.
 *
 * <p>A short final row is laid out with the same metrics as a full row, so a
 * given column stays vertically aligned across every row.
 */
public enum RXGridJustify {

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
     * Distribute the spare width so every gap — including the two edges — is
     * equal. With a single cell the cell is centered.
     */
    SPACE_EVENLY,

    /**
     * Size the cells equally so they fill the row, keeping {@code hgap} between
     * them. In adaptive mode cells only grow — the column count drops before a
     * cell would shrink below {@code cellWidth} — up to
     * max cell width, after which the resulting block is centered. Under a
     * forced column count that does not fit, cells may shrink below
     * {@code cellWidth} to keep that column count.
     */
    STRETCH
}
