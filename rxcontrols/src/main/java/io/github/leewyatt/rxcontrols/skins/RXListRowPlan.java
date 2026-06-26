package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXListSection;

import java.util.List;

/**
 * Immutable visual-row plan for {@link RXListViewport}: the interleaved sequence
 * of section-header rows and single-item data rows, with a per-section offset
 * index so that row&#8596;Y and item&#8596;row lookups cost O(log sections) plus
 * arithmetic (O(sections) memory — never one entry per row, so it scales to
 * millions of items).
 *
 * <p>A flat view (no {@code sectionKeyFactory}) is the degenerate case: the
 * {@code sections} list is empty, no header rows exist, and every lookup reduces
 * to the closed-form arithmetic of a uniform single column (data row index ==
 * item index).
 *
 * <p>Two fixed row heights: a section-header row is {@code headerHeight}, a data
 * row is {@code rowHeight}. Being single-column, every data row holds exactly one
 * item, so a section contributes one header row (when shown) plus
 * {@code itemCount} data rows.
 *
 * <p>{@code sectionSpacing} is extra blank content-space inserted before every
 * group after the first (it is not a visual row, just added to the section's top
 * Y). It only has a visible effect with two or more sections; a flat view or a
 * single group never shows it.
 */
final class RXListRowPlan {

    /**
     * Resolved description of one visual row, produced by {@link #rowInfo(int)}
     * with a single section lookup.
     *
     * @param header    whether this row is a section header (vs a data row)
     * @param section   the section containing this row, or {@code null} when flat
     * @param itemIndex the item index of a data row, or {@code -1} for a header
     * @param top       the content-space Y of the row's top
     * @param height    the row's height (header height or data-row height)
     */
    record RowInfo(boolean header, RXListSection section, int itemIndex, double top, double height) {
    }

    private final List<RXListSection> sections;
    private final boolean grouped;
    private final boolean headersShown;
    private final double headerHeight;
    private final double rowHeight;
    private final int itemCount;

    // Per-section offset index (length == section count; empty when flat).
    private final int[] sectionFirstVisualRow;
    private final double[] sectionTopY;

    private final int totalVisualRows;
    private final double contentHeight;

    /**
     * Creates a plan for the given sections and geometry.
     *
     * @param sections       the sections (empty for a flat view)
     * @param showHeaders    whether header rows are rendered (only honored when grouped)
     * @param headerHeight   the height of one section-header row
     * @param rowHeight      the fixed data-row height; a non-positive value is clamped
     *                       to {@code 1} so the row math never divides by zero
     * @param itemCount      the total item count
     * @param sectionSpacing the extra blank space inserted before each section after the first
     */
    RXListRowPlan(List<RXListSection> sections, boolean showHeaders, double headerHeight,
                  double rowHeight, int itemCount, double sectionSpacing) {
        this.sections = List.copyOf(sections);
        this.grouped = !this.sections.isEmpty();
        this.headersShown = grouped && showHeaders;
        this.headerHeight = headerHeight;
        this.rowHeight = rowHeight > 0.0 ? rowHeight : 1.0;
        this.itemCount = Math.max(0, itemCount);

        int count = this.sections.size();
        sectionFirstVisualRow = new int[count];
        sectionTopY = new double[count];

        if (!grouped) {
            totalVisualRows = this.itemCount;
            contentHeight = this.itemCount * this.rowHeight;
        } else {
            int visualRow = 0;
            double y = 0.0;
            for (int s = 0; s < count; s++) {
                // Blank separator before every group after the first — not a visual
                // row, just extra content-space Y folded into the section's top.
                if (s > 0) {
                    y += sectionSpacing;
                }
                sectionFirstVisualRow[s] = visualRow;
                sectionTopY[s] = y;
                if (headersShown) {
                    visualRow += 1;
                    y += this.headerHeight;
                }
                int dataRows = this.sections.get(s).itemCount();
                visualRow += dataRows;
                y += dataRows * this.rowHeight;
            }
            totalVisualRows = visualRow;
            contentHeight = y;
        }
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

    int totalVisualRows() {
        return totalVisualRows;
    }

    boolean headersShown() {
        return headersShown;
    }

    int sectionCount() {
        return sections.size();
    }

    double headerHeight() {
        return headerHeight;
    }

    /**
     * The content-space top Y of the section at {@code sectionIndex} — the top of
     * its header row (it already folds in {@code sectionSpacing}). Used by the
     * sticky-header handoff math.
     *
     * @param sectionIndex a valid section index
     * @return the section's content-space top Y, or {@code 0} when out of range / flat
     */
    double sectionTop(int sectionIndex) {
        if (!grouped || sectionIndex < 0 || sectionIndex >= sectionTopY.length) {
            return 0.0;
        }
        return sectionTopY[sectionIndex];
    }

    /**
     * The visual row whose band contains content-space {@code y}, clamped to the
     * valid range, or {@code -1} when there are no rows.
     *
     * @param y content-space offset (already resolved from the scroll position)
     * @return the visual-row index, or {@code -1} when empty
     */
    int firstVisualRowAt(double y) {
        if (totalVisualRows == 0) {
            return -1;
        }
        if (!grouped) {
            int row = (int) Math.floor(y / rowHeight);
            return clamp(row, 0, totalVisualRows - 1);
        }
        int s = sectionAtY(y);
        double dy = y - sectionTopY[s];
        int visualRow = sectionFirstVisualRow[s];
        if (headersShown) {
            if (dy < headerHeight) {
                return visualRow;
            }
            dy -= headerHeight;
            visualRow += 1;
        }
        int dataRows = sections.get(s).itemCount();
        int local = clamp((int) Math.floor(dy / rowHeight), 0, dataRows - 1);
        return visualRow + local;
    }

    /**
     * Resolves a visual row to its kind, geometry and (for data rows) item index.
     *
     * @param visualRow a valid visual-row index
     * @return the row description
     */
    RowInfo rowInfo(int visualRow) {
        if (!grouped) {
            return new RowInfo(false, null, visualRow, visualRow * rowHeight, rowHeight);
        }
        int s = sectionAtVisualRow(visualRow);
        RXListSection section = sections.get(s);
        int local = visualRow - sectionFirstVisualRow[s];
        if (headersShown && local == 0) {
            return new RowInfo(true, section, -1, sectionTopY[s], headerHeight);
        }
        int dataLocal = headersShown ? local - 1 : local;
        int itemIndex = section.firstItemIndex() + dataLocal;
        double top = sectionTopY[s] + (headersShown ? headerHeight : 0.0) + dataLocal * rowHeight;
        return new RowInfo(false, section, itemIndex, top, rowHeight);
    }

    /**
     * The visual row holding the data item at {@code itemIndex}.
     *
     * @param itemIndex a valid item index
     * @return the visual-row index of the data row containing the item
     */
    int visualRowOfItem(int itemIndex) {
        if (!grouped) {
            return itemIndex;
        }
        int s = sectionAtItem(itemIndex);
        RXListSection section = sections.get(s);
        int localRow = itemIndex - section.firstItemIndex();
        return sectionFirstVisualRow[s] + (headersShown ? 1 : 0) + localRow;
    }

    /**
     * The first visual row belonging to the section at {@code sectionIndex}.
     *
     * @param sectionIndex the section index
     * @return the section's first visual row, or {@code -1} when unavailable
     */
    int visualRowOfSection(int sectionIndex) {
        if (!grouped || sectionIndex < 0 || sectionIndex >= sectionFirstVisualRow.length) {
            return -1;
        }
        return sectionFirstVisualRow[sectionIndex];
    }

    /**
     * The section containing the given visual row, or {@code null} when flat.
     *
     * @param visualRow a valid visual-row index
     * @return the containing section, or {@code null}
     */
    RXListSection sectionOf(int visualRow) {
        if (!grouped) {
            return null;
        }
        return sections.get(sectionAtVisualRow(visualRow));
    }

    // ==================== Section lookups ====================

    // Last section whose top Y is <= y (so an exact boundary selects the new section).
    private int sectionAtY(double y) {
        int lo = 0;
        int hi = sectionTopY.length - 1;
        int result = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (sectionTopY[mid] <= y) {
                result = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return result;
    }

    // Last section whose first visual row is <= visualRow.
    private int sectionAtVisualRow(int visualRow) {
        int lo = 0;
        int hi = sectionFirstVisualRow.length - 1;
        int result = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (sectionFirstVisualRow[mid] <= visualRow) {
                result = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return result;
    }

    // Last section whose first item index is <= itemIndex.
    private int sectionAtItem(int itemIndex) {
        int lo = 0;
        int hi = sections.size() - 1;
        int result = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (sections.get(mid).firstItemIndex() <= itemIndex) {
                result = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return result;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
