package io.github.leewyatt.rxcontrols;

/**
 * Context handed to a {@link CellHeightProvider} so it can return the pixel height a
 * masonry cell needs for its slot.
 *
 * <p>{@link #cellWidth()} is the width the item is laid out at, span-aware: it runs
 * from the left edge of the item's first column to the right edge of its last, gaps
 * included. The height is asked after the item's columns are chosen, so this width is
 * exact. An image gallery typically returns {@code cellWidth() / aspectRatio}.</p>
 *
 * @param <T>         the item type
 * @param item        the item whose height is requested, possibly {@code null}
 * @param index       the item's index in the items list
 * @param cellWidth   the width the item is laid out at, spanning its columns
 * @param columnSpan  the number of columns the item spans, at least one
 * @param trackWidth  the width of a single column track
 * @param columnCount the resolved number of columns this layout pass
 */
public record CellHeightContext<T>(T item, int index, double cellWidth, int columnSpan,
                                   double trackWidth, int columnCount) {
}
