package io.github.leewyatt.rxcontrols;

/**
 * Supplies the exact pixel height of each masonry cell, given its item and the
 * effective width of its slot. This is the primary, precise height contract of
 * {@link RXMasonryView}: when set, the view places every item without ever
 * measuring a live cell, so scrolling, the scroll bar and hit-testing are exact and
 * the layout never jumps. When {@code null}, the view seeds each item at the
 * {@link RXMasonryView#estimatedCellHeightProperty() estimatedCellHeight} and then
 * measures each realized cell, re-packing to converge on the real heights.
 *
 * @param <T> the item type
 */
@FunctionalInterface
public interface CellHeightProvider<T> {

    /**
     * Returns the pixel height the cell for the given context should occupy.
     *
     * @param context the item, its index and its resolved slot width
     * @return the cell height in pixels; non-finite or negative values are resolved
     *         to the estimated cell height at layout time
     */
    double computeHeight(CellHeightContext<T> context);
}
