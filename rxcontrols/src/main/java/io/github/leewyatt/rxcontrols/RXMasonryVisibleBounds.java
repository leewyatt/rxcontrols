package io.github.leewyatt.rxcontrols;

/**
 * Immutable snapshot of the visible index bounds of a {@link RXMasonryView}
 * viewport, published through {@link RXMasonryView#visibleBoundsProperty()}
 * when the realized bounds change.
 *
 * <p>Indices are inclusive positions in {@link RXMasonryView#getItems()}. The
 * pair is a <em>bound</em>, not a contiguous range: because tall items in one
 * column can span the viewport while shorter columns advance further, indices
 * between {@link #firstIndex()} and {@link #lastIndex()} are not necessarily
 * visible. {@link #EMPTY} represents "nothing visible" (no items, or not yet
 * laid out).
 *
 * <p>This object is immutable and replaced when the bounds change, so a
 * listener always sees a consistent pair and never a torn read.
 *
 * @param firstIndex index of the first visible item, or {@code -1} when empty
 * @param lastIndex  index of the last visible item, or {@code -1} when empty
 */
public record RXMasonryVisibleBounds(int firstIndex, int lastIndex) {

    /** The empty bounds: nothing visible. */
    public static final RXMasonryVisibleBounds EMPTY = new RXMasonryVisibleBounds(-1, -1);

    /**
     * Whether the bounds contain no items.
     *
     * @return {@code true} if nothing is visible
     */
    public boolean isEmpty() {
        return firstIndex < 0;
    }
}
