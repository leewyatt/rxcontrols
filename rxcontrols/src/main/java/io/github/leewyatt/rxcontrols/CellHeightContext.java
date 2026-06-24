package io.github.leewyatt.rxcontrols;

/**
 * Context handed to a {@link CellHeightProvider} so it can return the exact pixel
 * height a masonry cell needs at its resolved width.
 *
 * <p>The width is span-aware: {@link #cellWidth()} is the effective width of the
 * slot the item occupies, already accounting for a multi-column span
 * ({@code span * trackWidth + (span - 1) * hgap}), not a single column track. An
 * image gallery typically returns {@code cellWidth() / aspectRatio}.
 *
 * @param <T>         the item type
 * @param item        the item whose height is requested, possibly {@code null}
 * @param index       the item's index in the items list
 * @param cellWidth   the effective width of the item's slot, spanning its columns
 * @param columnSpan  the number of columns the item spans, at least one
 * @param trackWidth  the width of a single column track
 * @param columnCount the resolved number of columns this layout pass
 */
public record CellHeightContext<T>(T item, int index, double cellWidth, int columnSpan,
                                   double trackWidth, int columnCount) {
}
