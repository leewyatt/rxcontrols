package io.github.leewyatt.rxcontrols;

/**
 * Immutable snapshot of the item and row range currently realized in a
 * {@link RXGridView} viewport, published through
 * {@link RXGridView#visibleRangeProperty()} after each layout pass.
 *
 * <p>Indices are inclusive. {@link #EMPTY} represents "nothing visible" (no
 * items, or not yet laid out) and reports {@link #isEmpty()} {@code true} with a
 * {@link #size()} of {@code 0}.
 *
 * @param firstIndex  index of the first visible item, or {@code -1} when empty
 * @param lastIndex   index of the last visible item, or {@code -1} when empty
 * @param firstRow    index of the first visible row, or {@code -1} when empty
 * @param lastRow     index of the last visible row, or {@code -1} when empty
 * @param columnCount number of columns the range was computed with
 */
public record RXGridVisibleRange(int firstIndex, int lastIndex,
                                 int firstRow, int lastRow, int columnCount) {

    /** The empty range: nothing visible. */
    public static final RXGridVisibleRange EMPTY = new RXGridVisibleRange(-1, -1, -1, -1, 0);

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
