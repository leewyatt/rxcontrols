package io.github.leewyatt.rxcontrols;

/**
 * Immutable snapshot of the item and row range currently realized in a
 * {@link RXTileView} viewport, published through
 * {@link RXTileView#visibleRangeProperty()} when the realized range changes.
 *
 * <p>Indices are inclusive and refer to item positions in
 * {@link RXTileView#getItems()}; rows are data rows (section header rows are not
 * counted here, unlike {@link RXTileView#rowCountProperty() rowCount}, which
 * counts visual rows). {@link #EMPTY} represents "nothing visible"
 * (no items, or not yet laid out) and reports {@link #isEmpty()} {@code true}
 * with a {@link #size()} of {@code 0}.
 *
 * <p>This object is immutable and replaced when the realized range changes, so
 * a listener always sees a consistent pair of bounds and never a torn read.
 *
 * @param firstIndex  index of the first visible item, or {@code -1} when empty
 * @param lastIndex   index of the last visible item, or {@code -1} when empty
 * @param firstRow    index of the first visible data row, or {@code -1} when empty
 * @param lastRow     index of the last visible data row, or {@code -1} when empty
 * @param columnCount number of columns the range was computed with
 */
public record RXTileVisibleRange(int firstIndex, int lastIndex,
                                 int firstRow, int lastRow, int columnCount) {

    /** The empty range: nothing visible. */
    public static final RXTileVisibleRange EMPTY = new RXTileVisibleRange(-1, -1, -1, -1, 0);

    /**
     * Whether the range contains no items.
     *
     * @return {@code true} if nothing is visible
     */
    public boolean isEmpty() {
        return firstIndex < 0;
    }

    /**
     * Number of items in the range.
     *
     * @return the inclusive item count, or {@code 0} when empty
     */
    public int size() {
        return isEmpty() ? 0 : lastIndex - firstIndex + 1;
    }
}
