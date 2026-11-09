package io.github.leewyatt.rxcontrols;

/**
 * Context handed to a {@link CellHeightProvider} so it can return the pixel height a
 * masonry cell needs for its slot.
 *
 * <p>{@link #cellWidth()} is the slot's nominal width, span-aware:
 * {@code span * trackWidth + (span - 1) * hgap}, where {@code hgap} is the gap as
 * laid out. An image gallery typically returns {@code cellWidth() / aspectRatio}.</p>
 *
 * <p>A single-column slot is laid out at exactly this width. The height is asked
 * before the item is assigned a column, and column edges snap to the device-pixel
 * grid, so a multi-column slot can be laid out up to one device pixel narrower or
 * wider than this nominal width, depending on the columns it lands in.</p>
 *
 * @param <T>         the item type
 * @param item        the item whose height is requested, possibly {@code null}
 * @param index       the item's index in the items list
 * @param cellWidth   the nominal width of the item's slot, spanning its columns
 * @param columnSpan  the number of columns the item spans, at least one
 * @param trackWidth  the width of a single column track
 * @param columnCount the resolved number of columns this layout pass
 */
public record CellHeightContext<T>(T item, int index, double cellWidth, int columnSpan,
                                   double trackWidth, int columnCount) {
}
