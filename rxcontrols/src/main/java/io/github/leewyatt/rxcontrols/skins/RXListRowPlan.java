package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXListSection;

import java.util.List;

/**
 * Immutable visual-row plan for {@link RXListViewport}: the interleaved sequence
 * of section-header rows and single-item data rows, answering row&#8596;Y and
 * item&#8596;row lookups for either a uniform fixed row height or per-item variable
 * heights.
 *
 * <p>A flat view (no {@code sectionKeyFactory}) is the degenerate case: the
 * {@code sections} list is empty, no header rows exist, and (in fixed mode) every
 * lookup reduces to closed-form arithmetic (data row index == item index).
 *
 * <p><b>Fixed mode</b> ({@link #fixed}) keeps only the per-section offset index, so
 * row&#8596;Y and item&#8596;row cost O(log sections) plus arithmetic and the memory
 * is O(sections) — it scales to millions of items. A section-header row is
 * {@code headerHeight}; a data row is the uniform {@code rowHeight}.
 *
 * <p><b>Variable mode</b> ({@link #variable(java.util.List, boolean, double, double[], double)})
 * stores a per-item prefix-sum of the supplied heights (measured or estimated), so it is
 * O(items) in memory — the cost of non-uniform rows — and answers a content-space query
 * by a binary search over the per-item tops. Section-header rows stay the fixed
 * {@code headerHeight}; only data rows vary. The structural lookups
 * ({@link #visualRowOfItem}, {@link #visualRowOfSection}, {@link #sectionOf}) are
 * height-independent and identical in both modes.
 *
 * <p>Grouped variable mode requires the {@code sections} to exactly partition
 * {@code [0, itemCount)} (each section a contiguous run, every item in one section) —
 * the invariant {@code recomputeSections} guarantees. Both the prefix-sum build and the
 * per-item lookups rely on it.
 *
 * <p>{@code sectionSpacing} is extra blank content-space inserted before every group
 * after the first (it is not a visual row, just added to the section's top Y). It only
 * has a visible effect with two or more sections.
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
    private final boolean variable;

    // Per-section offset index (length == section count; empty when flat).
    private final int[] sectionFirstVisualRow;
    private final double[] sectionTopY;

    // Variable mode only (null when fixed): per-item content-space top and height,
    // both length itemCount. itemTops is ascending so a content-space query binary-searches it.
    private final double[] itemTops;
    private final double[] itemHeights;

    private final int totalVisualRows;
    private final double contentHeight;

    /**
     * Creates a fixed (uniform row height) plan.
     *
     * @param sections       the sections (empty for a flat view)
     * @param showHeaders    whether header rows are rendered (only honored when grouped)
     * @param headerHeight   the height of one section-header row
     * @param rowHeight      the fixed data-row height; a non-positive value is clamped
     *                       to {@code 1} so the row math never divides by zero
     * @param itemCount      the total item count
     * @param sectionSpacing the extra blank space inserted before each section after the first
     * @return the fixed-height plan
     */
    static RXListRowPlan fixed(List<RXListSection> sections, boolean showHeaders, double headerHeight,
                               double rowHeight, int itemCount, double sectionSpacing) {
        return new RXListRowPlan(sections, showHeaders, headerHeight, rowHeight, itemCount, null, sectionSpacing);
    }

    /**
     * Creates a variable (per-item height) plan. The item count is taken from
     * {@code itemHeights.length}; the heights are the effective per-item heights
     * (measured or estimated).
     *
     * @param sections       the sections (empty for a flat view)
     * @param showHeaders    whether header rows are rendered (only honored when grouped)
     * @param headerHeight   the height of one section-header row
     * @param itemHeights    the effective per-item data-row heights
     * @param sectionSpacing the extra blank space inserted before each section after the first
     * @return the variable-height plan
     */
    static RXListRowPlan variable(List<RXListSection> sections, boolean showHeaders, double headerHeight,
                                  double[] itemHeights, double sectionSpacing) {
        return new RXListRowPlan(sections, showHeaders, headerHeight, 0.0, itemHeights.length, itemHeights,
                sectionSpacing);
    }

    private RXListRowPlan(List<RXListSection> sections, boolean showHeaders, double headerHeight,
                          double rowHeight, int itemCount, double[] itemHeights, double sectionSpacing) {
        this.sections = List.copyOf(sections);
        this.grouped = !this.sections.isEmpty();
        this.headersShown = grouped && showHeaders;
        this.headerHeight = headerHeight;
        this.rowHeight = rowHeight > 0.0 ? rowHeight : 1.0;
        this.itemCount = Math.max(0, itemCount);
        this.variable = itemHeights != null;

        if (variable) {
            this.itemHeights = new double[this.itemCount];
            this.itemTops = new double[this.itemCount];
            for (int i = 0; i < this.itemCount; i++) {
                double h = i < itemHeights.length ? itemHeights[i] : 0.0;
                // Heights enter the prefix sum, so coerce non-finite / negative to 0 to
                // keep itemTops monotonic (the binary-search visibility query needs it).
                this.itemHeights[i] = Double.isFinite(h) && h > 0.0 ? h : 0.0;
            }
        } else {
            this.itemHeights = null;
            this.itemTops = null;
        }

        int count = this.sections.size();
        sectionFirstVisualRow = new int[count];
        sectionTopY = new double[count];

        if (!grouped) {
            totalVisualRows = this.itemCount;
            contentHeight = variable ? fillFlatItemTops() : this.itemCount * this.rowHeight;
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
                RXListSection section = this.sections.get(s);
                int dataRows = section.itemCount();
                if (variable) {
                    // Sections partition [0, itemCount) exactly (class doc), so every item
                    // index is in range and each itemTops slot is written exactly once.
                    int firstItem = section.firstItemIndex();
                    for (int k = 0; k < dataRows; k++) {
                        int itemIndex = firstItem + k;
                        itemTops[itemIndex] = y;
                        y += itemHeights[itemIndex];
                    }
                } else {
                    y += dataRows * this.rowHeight;
                }
                visualRow += dataRows;
            }
            totalVisualRows = visualRow;
            contentHeight = y;
        }
    }

    // Lays out the flat (ungrouped) per-item tops and returns the total content height.
    private double fillFlatItemTops() {
        double y = 0.0;
        for (int i = 0; i < itemCount; i++) {
            itemTops[i] = y;
            y += itemHeights[i];
        }
        return y;
    }

    int itemCount() {
        return itemCount;
    }

    boolean variable() {
        return variable;
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
     * The content-space top Y of the data row holding {@code itemIndex}, used by the
     * variable-height anchor pin.
     *
     * @param itemIndex an item index
     * @return the data row's content-space top, or {@code -1} when out of range
     */
    double itemTop(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= itemCount) {
            return -1.0;
        }
        if (variable) {
            return itemTops[itemIndex];
        }
        return rowInfo(visualRowOfItem(itemIndex)).top();
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
            if (variable) {
                return clamp(lastItemAtMost(y, 0, itemCount), 0, totalVisualRows - 1);
            }
            int row = (int) Math.floor(y / rowHeight);
            return clamp(row, 0, totalVisualRows - 1);
        }
        int s = sectionAtY(y);
        double dy = y - sectionTopY[s];
        int baseVisualRow = sectionFirstVisualRow[s];
        if (headersShown && dy < headerHeight) {
            return baseVisualRow;
        }
        int firstDataVisualRow = baseVisualRow + (headersShown ? 1 : 0);
        RXListSection section = sections.get(s);
        int firstItem = section.firstItemIndex();
        int dataRows = section.itemCount();
        if (variable) {
            int item = lastItemAtMost(y, firstItem, firstItem + dataRows);
            return firstDataVisualRow + clamp(item - firstItem, 0, Math.max(0, dataRows - 1));
        }
        double dataDy = dy - (headersShown ? headerHeight : 0.0);
        int local = clamp((int) Math.floor(dataDy / rowHeight), 0, dataRows - 1);
        return firstDataVisualRow + local;
    }

    /**
     * Resolves a visual row to its kind, geometry and (for data rows) item index.
     *
     * @param visualRow a valid visual-row index
     * @return the row description
     */
    RowInfo rowInfo(int visualRow) {
        if (!grouped) {
            if (variable) {
                return new RowInfo(false, null, visualRow, itemTops[visualRow], itemHeights[visualRow]);
            }
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
        if (variable) {
            return new RowInfo(false, section, itemIndex, itemTops[itemIndex], itemHeights[itemIndex]);
        }
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

    // ==================== Lookups ====================

    // Largest index i in [lo, hiExclusive) with itemTops[i] <= y, or lo when y is below
    // the first (itemTops is ascending). Variable mode only.
    private int lastItemAtMost(double y, int lo, int hiExclusive) {
        int low = lo;
        int high = hiExclusive - 1;
        int result = lo;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (itemTops[mid] <= y) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }

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
