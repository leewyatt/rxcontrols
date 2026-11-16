package io.github.leewyatt.rxcontrols;

/**
 * Immutable snapshot of the item and row range currently realized in a
 * {@link RXListView} viewport, published through
 * {@link RXListView#visibleRangeProperty()} when the realized range changes.
 *
 * <p>Item indices are inclusive and refer to item positions in
 * {@link RXListView#getItems()}. They are the <em>bounds</em> of the visible items:
 * when a collapsed section (see {@link RXListView#collapsibleSectionsProperty()}) lies
 * on screen between two visible items, its hidden items fall inside the bounds
 * without being visible. Rows are the shown data rows — one per visible item,
 * skipping section headers and the items of collapsed sections — numbered
 * contiguously, so {@code lastRow - firstRow + 1 == visibleRowCount}. With nothing
 * collapsed a row number equals the item index. {@link #EMPTY} represents "no item
 * row in the window" (no items, not yet laid out, or only section headers in view —
 * for example every section collapsed) and reports {@link #isEmpty()} {@code true}
 * with a {@link #size()} of {@code 0}.
 *
 * <p>This object is immutable and replaced when the realized range changes, so a
 * listener always sees a consistent pair of bounds and never a torn read.
 *
 * @param firstIndex      index of the first visible item, or {@code -1} when empty
 * @param lastIndex       index of the last visible item, or {@code -1} when empty
 * @param firstRow        shown data-row number of the first visible item, or
 *                        {@code -1} when empty
 * @param lastRow         shown data-row number of the last visible item, or
 *                        {@code -1} when empty
 * @param visibleRowCount number of visible items (shown data rows) in the realized window
 */
public record RXListVisibleRange(int firstIndex, int lastIndex,
                                 int firstRow, int lastRow, int visibleRowCount) {

    /** The empty range: nothing visible. */
    public static final RXListVisibleRange EMPTY = new RXListVisibleRange(-1, -1, -1, -1, 0);

    /**
     * Whether the range contains no items.
     *
     * @return {@code true} if nothing is visible
     */
    public boolean isEmpty() {
        return firstIndex < 0;
    }

    /**
     * Number of visible items in the range — {@link #visibleRowCount()}. Items hidden
     * in a collapsed section between the bounds are not counted; with nothing
     * collapsed this equals {@code lastIndex - firstIndex + 1}.
     *
     * @return the visible item count, or {@code 0} when empty
     */
    public int size() {
        return isEmpty() ? 0 : visibleRowCount;
    }
}
