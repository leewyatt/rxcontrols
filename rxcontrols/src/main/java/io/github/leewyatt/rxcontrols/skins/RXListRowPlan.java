package io.github.leewyatt.rxcontrols.skins;

/**
 * Immutable geometry plan for {@link RXListViewport}: the single-column,
 * fixed-height row layout. Every data row holds one item and is
 * {@code rowHeight} tall, so the item&#8596;Y mapping is closed-form arithmetic
 * (O(1) memory, scaling to millions of items).
 *
 * <p>This is the degenerate (flat, single-column, uniform height) member of the
 * tile family's row-plan idea; the section-interleaving and variable-height
 * variants are introduced in later milestones. The skin builds the plan from the
 * item count and the resolved row height and shares it with the viewport so the
 * scroll-bar decision and the viewport geometry never disagree.
 */
final class RXListRowPlan {

    private final int itemCount;
    private final double rowHeight;
    private final double contentHeight;

    /**
     * Creates a plan for {@code itemCount} rows of {@code rowHeight} pixels each.
     *
     * @param itemCount the number of items (rows); negative values are treated as zero
     * @param rowHeight the fixed row height; a non-positive value is clamped to {@code 1}
     *                  so the row math never divides by zero (the skin normally resolves
     *                  a positive height before this point)
     */
    RXListRowPlan(int itemCount, double rowHeight) {
        this.itemCount = Math.max(0, itemCount);
        this.rowHeight = rowHeight > 0.0 ? rowHeight : 1.0;
        this.contentHeight = this.itemCount * this.rowHeight;
    }

    int itemCount() {
        return itemCount;
    }

    double rowHeight() {
        return rowHeight;
    }

    double contentHeight() {
        return contentHeight;
    }

    /**
     * The item whose row band contains content-space {@code y}, clamped to the
     * valid range, or {@code -1} when there are no items.
     *
     * @param y content-space offset (already resolved from the scroll position)
     * @return the item index, or {@code -1} when empty
     */
    int firstItemAt(double y) {
        if (itemCount == 0) {
            return -1;
        }
        int index = (int) Math.floor(y / rowHeight);
        return clamp(index, 0, itemCount - 1);
    }

    /**
     * The content-space Y of the top of the row holding {@code index}.
     *
     * @param index an item index
     * @return the row's top in content space
     */
    double topOfItem(int index) {
        return index * rowHeight;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
