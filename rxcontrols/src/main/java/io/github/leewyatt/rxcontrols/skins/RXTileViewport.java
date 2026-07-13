package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import io.github.leewyatt.rxcontrols.RXTileCell;
import io.github.leewyatt.rxcontrols.RXTileSection;
import io.github.leewyatt.rxcontrols.RXTileSectionCell;
import io.github.leewyatt.rxcontrols.RXTileView;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import io.github.leewyatt.rxcontrols.SmoothScrollMode;
import javafx.animation.Interpolator;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.MultipleSelectionModel;
import javafx.util.Callback;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Self-built virtualizing viewport for {@link RXTileViewSkin}. On top of the
 * {@link RXVirtualViewportBase} shell it adds a multi-column tile layout
 * ({@link ItemsJustify} distribution), an independent section-header pool with a
 * pinned sticky header, and a reorder glide on column-count changes.
 *
 * <p>The visual-row geometry is supplied by a {@link RXTileRowPlan} that the
 * skin builds (it is width-dependent and shared with the skin's column / scroll
 * bar decision so the two never disagree). Flat is the degenerate plan with no
 * section rows. The viewport reports the visible item range, data-row range and
 * top section back so the skin can publish the read-only metrics.
 *
 * <p>On a column-count change the viewport runs a reorder glide: visible cells
 * (and section headers) keep their identity, are repositioned to their new slots,
 * and tween from their old position via {@link ViewportReorderAnimator}. Cells
 * mid-glide are pinned: a same-item re-placement keeps the glide running, while
 * any rebind to a different item or a park cancels the glide first.
 *
 * @param <T> the item type
 */
final class RXTileViewport<T> extends RXVirtualViewportBase<T, RXTileCell<T>> {

    // Sub-pixel threshold for the sticky header's :pinned state: it only elevates
    // once content has actually scrolled under it, not when a section rests at the top.
    private static final double STICKY_PINNED_EPSILON = 0.5;

    private static final PseudoClass PINNED_PSEUDO_CLASS = PseudoClass.getPseudoClass("pinned");

    private final RXTileView<T> control;

    private final List<RXTileSectionCell> headerPool = new ArrayList<>();
    // The single pinned section header (sticky subheader). Created lazily only when
    // enabled, removed when disabled, recreated on a header-factory change. It lives
    // in the overlay layer (above content + marquee, below the scroll bar), so it
    // never joins the recycling pool or the reorder glide.
    private RXTileSectionCell stickyHeader;
    // Cells / headers mid-glide are pinned here: the reorder fallback allocation
    // skips them and a same-item re-placement keeps the glide, while a rebind to
    // a different item or a park cancels the glide first.
    private final Set<RXTileCell<T>> animating = new HashSet<>();
    private final Set<RXTileSectionCell> animatingHeaders = new HashSet<>();
    private final ViewportReorderAnimator reorderAnimator = new ViewportReorderAnimator();
    // True for the duration of one fillVisibleRows when a column-count change should
    // glide cells/headers from their old positions to the new ones.
    private boolean reorderPass;

    // Supplied by the skin after its scroll-bar/column decision so the skin and
    // this viewport use the exact same geometry plan.
    private RXTileRowPlan rowPlan;

    private int lastColumnCount = -1;
    private int lastVisibleFirstIndex = -1;

    // Published to the skin after each pass (-1 / null when nothing is visible).
    private int visibleFirstRow = -1;
    private int visibleLastRow = -1;
    private RXTileSection topSection;

    RXTileViewport(RXTileView<T> control) {
        this.control = control;
    }

    // ==================== Skin-facing API ====================

    /**
     * The pixel height of one data-row slot (cell height + vgap), snapped, and the
     * single source of truth shared with the skin's plan + overflow check. Never
     * returns a non-positive value — that would break the row math; prefTileHeight's
     * layout-time fallback normally guarantees it, and the guard covers any
     * unexpected arithmetic edge.
     *
     * @return the data-row slot height, always {@code > 0}
     */
    double slotHeight() {
        double slot = snapSizeY(RXTileViewSkin.prefTileHeightOrDefault(control)
                + RXTileViewSkin.gapOrZero(control.getVgap()));
        return slot > 0.0 ? slot : 1.0;
    }

    /**
     * Installs the visual-row plan for this pass. The skin builds it so its
     * column / scroll-bar decision and this viewport's geometry stay consistent.
     *
     * @param plan the row plan, never {@code null}
     */
    void setRowPlan(RXTileRowPlan plan) {
        this.rowPlan = plan;
    }

    RXTileRowPlan.ItemPosition itemPositionOf(int itemIndex) {
        RXTileRowPlan plan = rowPlan;
        if (plan == null) {
            return new RXTileRowPlan.ItemPosition(-1, -1);
        }
        return plan.itemPositionOf(itemIndex);
    }

    int columnCount() {
        RXTileRowPlan plan = rowPlan;
        return plan == null ? -1 : plan.columns();
    }

    int verticalNeighborOf(int itemIndex, int direction, int preferredColumn) {
        RXTileRowPlan plan = rowPlan;
        if (plan == null) {
            return -1;
        }
        return plan.verticalNeighborOfItem(itemIndex, direction, preferredColumn);
    }

    boolean isSelectableBlankPoint(double viewportX, double viewportY) {
        if (viewportX < 0.0 || viewportY < 0.0 || viewportY > getHeight()) {
            return false;
        }
        double contentWidth = contentWidth();
        if (viewportX > contentWidth || contentWidth <= 0.0) {
            return false;
        }
        RXTileRowPlan plan = rowPlan;
        if (plan == null || plan.totalVisualRows() == 0) {
            return false;
        }
        double contentY = viewportY + scrollY;
        if (itemIndexAtContentPoint(viewportX, contentY) >= 0) {
            return false;
        }
        return !isHeaderAtContentY(contentY);
    }

    int itemIndexAtContentPoint(double x, double contentY) {
        RXTileRowPlan plan = rowPlan;
        if (plan == null || plan.totalVisualRows() == 0
                || contentY < 0.0 || contentY >= plan.contentHeight()) {
            return -1;
        }
        CellGeometry geometry = cellGeometry(contentWidth());
        RXTileRowPlan.RowInfo info = plan.rowInfo(plan.firstVisualRowAt(contentY));
        if (info.header() || contentY >= info.top() + geometry.cellHeight()) {
            return -1;
        }
        int column = columnAtX(x, geometry, info.cellCount());
        return column < 0 ? -1 : info.firstItemIndex() + column;
    }

    List<Integer> itemIndicesIntersectingContentRect(double x1, double y1, double x2, double y2) {
        RXTileRowPlan plan = rowPlan;
        if (plan == null || plan.totalVisualRows() == 0) {
            return List.of();
        }
        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2);
        double maxY = Math.max(y1, y2);
        if (maxX < 0.0 || maxY < 0.0 || minY >= plan.contentHeight()) {
            return List.of();
        }

        CellGeometry geometry = cellGeometry(contentWidth());
        if (geometry.cellWidth() <= 0.0 || geometry.cellHeight() <= 0.0) {
            return List.of();
        }

        double clampedMinY = Math.max(0.0, minY);
        double clampedMaxY = Math.min(maxY, Math.nextDown(plan.contentHeight()));
        int firstRow = plan.firstVisualRowAt(clampedMinY);
        int lastRow = plan.firstVisualRowAt(clampedMaxY);
        if (firstRow < 0 || lastRow < firstRow) {
            return List.of();
        }

        List<Integer> indices = new ArrayList<>();
        double step = geometry.cellWidth() + geometry.hgap();
        for (int visualRow = firstRow; visualRow <= lastRow; visualRow++) {
            RXTileRowPlan.RowInfo info = plan.rowInfo(visualRow);
            if (info.header() || info.cellCount() <= 0) {
                continue;
            }
            double cellTop = info.top();
            double cellBottom = cellTop + geometry.cellHeight();
            if (!rangesIntersect(minY, maxY, cellTop, cellBottom)) {
                continue;
            }
            for (int column = 0; column < info.cellCount(); column++) {
                double cellX = geometry.startX() + column * step;
                if (rangesIntersect(minX, maxX, cellX, cellX + geometry.cellWidth())) {
                    indices.add(info.firstItemIndex() + column);
                }
            }
        }
        return indices;
    }

    int getVisibleFirstRow() {
        return visibleFirstRow;
    }

    int getVisibleLastRow() {
        return visibleLastRow;
    }

    RXTileSection getTopSection() {
        return topSection;
    }

    /**
     * Scrolls so the row holding {@code index} lands per {@code alignment}, using
     * the current row plan so it is correct even before this pass's
     * {@link #layoutChildren()} runs (the pending-scroll path).
     *
     * @param index     a valid item index
     * @param alignment where the target row should land
     * @return {@code true} if the request was applied; {@code false} when the
     *         viewport has no height yet, so the caller should keep it pending
     */
    boolean scrollToIndex(int index, ScrollAlignment alignment) {
        double viewportHeight = getHeight();
        if (viewportHeight <= 0.0) {
            // Geometry is not known yet; leave the request armed for a sized pass.
            return false;
        }
        RXTileRowPlan plan = rowPlan;
        if (plan == null || plan.totalVisualRows() == 0) {
            return true;
        }
        int visualRow = plan.visualRowOfItem(index);
        RXTileRowPlan.RowInfo info = plan.rowInfo(visualRow);
        double maxScroll = Math.max(0.0, plan.contentHeight() - viewportHeight);

        // Land the item within the area below an active sticky header instead of under it.
        double target = targetScrollFor(info.top(), info.height(), viewportHeight,
                stickyOverlayHeight(plan), alignment);
        stopSmoothScrolling();
        setVerticalScrollOffset(RXMath.clamp(target, 0.0, maxScroll), ScrollOffsetWriteReason.PROGRAMMATIC_JUMP);
        requestLayout();
        return true;
    }

    /**
     * Scrolls so the section's first visual row lands per {@code alignment}. When
     * headers are shown, that first row is the header; otherwise it is the first
     * data row of the section.
     *
     * @param sectionIndex the section index
     * @param alignment    where the target row should land
     * @return {@code true} if the request was applied; {@code false} when the
     *         viewport has no height yet, so the caller should keep it pending
     */
    boolean scrollToSectionIndex(int sectionIndex, ScrollAlignment alignment) {
        double viewportHeight = getHeight();
        if (viewportHeight <= 0.0) {
            return false;
        }
        RXTileRowPlan plan = rowPlan;
        if (plan == null || plan.totalVisualRows() == 0) {
            return true;
        }
        int visualRow = plan.visualRowOfSection(sectionIndex);
        if (visualRow < 0) {
            return true;
        }
        RXTileRowPlan.RowInfo info = plan.rowInfo(visualRow);
        double maxScroll = Math.max(0.0, plan.contentHeight() - viewportHeight);

        double target = targetScrollFor(info.top(), info.height(), viewportHeight, alignment);
        stopSmoothScrolling();
        setVerticalScrollOffset(RXMath.clamp(target, 0.0, maxScroll), ScrollOffsetWriteReason.PROGRAMMATIC_JUMP);
        requestLayout();
        return true;
    }

    /**
     * Discards the data cell pool (the only path that drops cell instances); used
     * when the cell factory changes. A normal layout repopulates it.
     */
    @Override
    protected void recreateCells() {
        snapAllGlides();
        super.recreateCells();
    }

    /**
     * Discards the section-header pool; used when the section-header factory
     * changes. A normal layout repopulates it.
     */
    void recreateHeaders() {
        snapAllGlides();
        contentLayer.getChildren().removeAll(headerPool);
        headerPool.clear();
        // Drop the sticky too; the next layout rebuilds it with the new factory.
        if (stickyHeader != null) {
            getChildren().remove(stickyHeader);
            stickyHeader = null;
        }
        requestLayout();
    }

    @Override
    protected void dispose() {
        // Snap glides before the base removes cells, so no Timeline points at a
        // detached node; then drop the header pool and sticky overlay.
        snapAllGlides();
        contentLayer.getChildren().removeAll(headerPool);
        headerPool.clear();
        if (stickyHeader != null) {
            getChildren().remove(stickyHeader);
            stickyHeader = null;
        }
        super.dispose();
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        syncViewportClip(w, h);

        RXTileRowPlan plan = rowPlan;
        if (w <= 0.0 || h <= 0.0 || plan == null) {
            resetToEmptyState(0.0, 0.0, false);
            return;
        }
        if (plan.totalVisualRows() == 0) {
            resetToEmptyState(w, h, true);
            return;
        }

        int cols = plan.columns();
        double contentHeight = plan.contentHeight();
        double maxScroll = Math.max(0.0, contentHeight - h);
        cachedMaxScroll = maxScroll;

        // Resize anchor: when the column count changes (a width change, or the
        // scroll bar appearing), keep the item that was at the top of the viewport
        // at the top so the view does not jump. Skipped on the pass that just
        // applied an explicit scroll (a scroll request, wheel or bar drag).
        boolean columnsChanged = !explicitScrollPending && lastColumnCount > 0 && lastColumnCount != cols
                && lastVisibleFirstIndex >= 0;
        if (columnsChanged) {
            double corrected = RXMath.clamp(plan.rowInfo(plan.visualRowOfItem(lastVisibleFirstIndex)).top(),
                    0.0, maxScroll);
            double correction = corrected - scrollY;
            scrollY = corrected;
            shiftSmoothScrollBy(correction);
        }
        // The same column-count change is the reorder-glide trigger when animation
        // is on; otherwise cells snap to their new slots as before.
        reorderPass = columnsChanged && animationEnabled();
        lastColumnCount = cols;
        explicitScrollPending = false;

        scrollY = RXMath.clamp(scrollY, 0.0, maxScroll);

        double barBreadth = configureAndPositionScrollBar(maxScroll, w, h);
        double contentWidth = Math.max(0.0, w - barBreadth);
        layoutContentLayer(contentWidth, h);

        int first = plan.firstVisualRowAt(scrollY);
        int last = plan.firstVisualRowAt(scrollY + h - 1.0);
        int maxRow = plan.totalVisualRows() - 1;
        if (last > maxRow) {
            last = maxRow;
        }
        if (last < first) {
            last = first;
        }

        topSection = plan.sectionOf(first);
        fillVisibleRows(plan, first, last, contentWidth);
        layoutStickyHeader(plan, contentWidth);
        lastVisibleFirstIndex = visibleFirstIndex;
    }

    private void fillVisibleRows(RXTileRowPlan plan, int first, int last, double contentWidth) {
        CellGeometry geometry = cellGeometry(contentWidth);
        double hgap = geometry.hgap();
        double cellWidth = geometry.cellWidth();
        double cellHeight = geometry.cellHeight();
        double startX = geometry.startX();

        // When the sticky overlay is active it is the sole renderer of the top
        // section's header, so the in-flow copy of that header row is skipped here.
        boolean stickyActive = isStickyHeaderActive(plan);

        // On a reorder pass, snapshot which cell rendered each item BEFORE rebinding,
        // so the same node can be re-found for its item and glide to the new slot.
        Map<Integer, RXTileCell<T>> priorItemToCell = null;
        Set<RXTileCell<T>> usedThisPass = null;
        if (reorderPass) {
            priorItemToCell = new HashMap<>();
            for (RXTileCell<T> cell : cellPool) {
                if (cell.getIndex() >= 0) {
                    priorItemToCell.put(cell.getIndex(), cell);
                }
            }
            usedThisPass = new HashSet<>();
        }

        int cellCursor = 0;
        int headerCursor = 0;
        int firstItem = -1;
        int lastItem = -1;
        int firstDataRow = -1;
        int lastDataRow = -1;
        for (int visualRow = first; visualRow <= last; visualRow++) {
            RXTileRowPlan.RowInfo info = plan.rowInfo(visualRow);
            double rowTop = snapPositionY(info.top() - scrollY);
            if (info.header()) {
                if (stickyActive && info.section().sectionIndex() == topSection.sectionIndex()) {
                    continue;
                }
                RXTileSectionCell header = acquireHeader(headerCursor++);
                // Rebinding a gliding header to a different section invalidates its
                // glide: the tween belongs to the old section's move and would drag
                // the new section in with the leftover translate. On a reorder pass
                // placeHeader re-aims from the live visual position instead, so
                // cancelling here would break the FLIP continuity.
                if (!reorderPass && header.getItem() != info.section() && animatingHeaders.remove(header)) {
                    reorderAnimator.cancel(header);
                }
                String oldStyle = header.getStyle();
                header.updateSection(info.section());
                header.setVisible(true);
                applyCssAfterCellUpdate(header, oldStyle);
                placeHeader(header, rowTop, contentWidth, snapSizeY(info.height()));
            } else {
                int rowStart = info.firstItemIndex();
                int cellsInRow = info.cellCount();
                if (firstItem < 0) {
                    firstItem = rowStart;
                    firstDataRow = info.dataRowIndex();
                }
                double x = snapPositionX(startX);
                for (int column = 0; column < cellsInRow; column++) {
                    int itemIndex = rowStart + column;
                    RXTileCell<T> prior = reorderPass ? priorItemToCell.get(itemIndex) : null;
                    RXTileCell<T> cell = reorderPass
                            ? acquireCellForItem(itemIndex, priorItemToCell, usedThisPass)
                            : acquireCell(cellCursor++);
                    // Rebinding a gliding cell to a different item invalidates its
                    // glide: the tween belongs to the old item's move and would
                    // drag the new item in with the leftover translate. Same-item
                    // re-placement keeps the glide running (reorder carry-overs
                    // are re-aimed by placeCell instead).
                    if (!reorderPass && cell.getIndex() != itemIndex && animating.remove(cell)) {
                        reorderAnimator.cancel(cell);
                    }
                    String oldStyle = cell.getStyle();
                    cell.updateGridPosition(info.dataRowIndex(), column);
                    cell.updateIndex(itemIndex);
                    cell.setVisible(true);
                    applyCellState(cell, itemIndex);
                    applyCssAfterCellUpdate(cell, oldStyle);
                    // Only a carry-over cell (the one that rendered this item last pass)
                    // glides; a freshly repurposed or entering cell pops in at its slot.
                    placeCell(cell, x, rowTop, cellWidth, cellHeight, cell == prior);
                    x = snapPositionX(x + cellWidth + hgap);
                }
                if (cellsInRow > 0) {
                    lastItem = rowStart + cellsInRow - 1;
                    lastDataRow = info.dataRowIndex();
                }
            }
        }
        if (reorderPass) {
            parkUnusedCells(usedThisPass);
        } else {
            parkCellsFrom(cellCursor);
        }
        parkHeadersFrom(headerCursor);

        visibleFirstIndex = firstItem;
        visibleLastIndex = lastItem;
        visibleFirstRow = firstDataRow;
        visibleLastRow = lastDataRow;
    }

    private CellGeometry cellGeometry(double contentWidth) {
        double hgap = snapSpaceX(RXTileViewSkin.gapOrZero(control.getHgap()));
        double cellHeight = snapSizeY(RXTileViewSkin.prefTileHeightOrDefault(control));
        int resolvedCols = Math.max(1, rowPlan == null ? 1 : rowPlan.columns());
        // A single partial data row has no cross-row column alignment to preserve,
        // so the justify block spans the actual cells rather than the resolvable
        // column count — mirroring RXTilePane. With multiple data rows the short
        // final row keeps the full-row metrics so columns stay aligned.
        int cols = resolvedCols;
        if (rowPlan != null && rowPlan.totalDataRows() == 1) {
            cols = Math.max(1, Math.min(resolvedCols, rowPlan.itemCount()));
        }
        double baseWidth = snapSizeX(RXTileViewSkin.prefTileWidthOrDefault(control));
        ItemsJustify mode = RXTileViewSkin.justifyOrDefault(control.getItemsJustify());

        double cellWidth;
        double effectiveGap;
        double startX;
        double preferredRowWidth = cols * baseWidth + (cols - 1) * hgap;
        if (cols == 1 && preferredRowWidth > contentWidth) {
            // Emergency shrink: a single column wider than the content area shrinks
            // to fit; the configured gap is preserved (mirrors RXTilePane). A
            // one-column row has no inter-cell gaps, so the gap is moot in practice.
            // Multi-column overflow never reaches here — computeColumns drops a
            // column before a cell would shrink.
            cellWidth = Math.max(0.0, contentWidth);
            effectiveGap = hgap;
            startX = 0.0;
        } else if (mode == ItemsJustify.STRETCH) {
            double ideal = (contentWidth - (cols - 1) * hgap) / cols;
            double cap = maxTileWidthOrUnbounded(control);
            double effectiveCap = cap > 0.0 ? Math.max(snapSizeX(cap), baseWidth) : 0.0;
            effectiveGap = hgap;
            if (effectiveCap > 0.0 && ideal > effectiveCap) {
                cellWidth = effectiveCap;
                double used = cols * cellWidth + (cols - 1) * hgap;
                startX = Math.max(0.0, (contentWidth - used) / 2.0);
            } else {
                cellWidth = snapSizeX(Math.max(0.0, ideal));
                startX = 0.0;
            }
        } else {
            cellWidth = baseWidth;
            double slack = Math.max(0.0, contentWidth - (cols * cellWidth + (cols - 1) * hgap));
            effectiveGap = hgap;
            startX = 0.0;
            switch (mode) {
                case CENTER -> startX = slack / 2.0;
                case END -> startX = slack;
                case SPACE_BETWEEN -> effectiveGap = hgap + (cols > 1 ? slack / (cols - 1) : 0.0);
                case SPACE_AROUND -> {
                    effectiveGap = hgap + slack / cols;
                    startX = slack / (2.0 * cols);
                }
                case SPACE_EVENLY -> {
                    effectiveGap = hgap + slack / (cols + 1);
                    startX = slack / (cols + 1);
                }
                default -> {
                    // START: the block hugs the leading edge (defaults stand).
                }
            }
        }
        return new CellGeometry(cellWidth, cellHeight, effectiveGap, startX);
    }

    private static double maxTileWidthOrUnbounded(RXTileView<?> control) {
        double value = control.getMaxTileWidth();
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }

    private boolean isHeaderAtContentY(double contentY) {
        RXTileRowPlan plan = rowPlan;
        if (plan == null || plan.totalVisualRows() == 0
                || contentY < 0.0 || contentY >= plan.contentHeight()) {
            return false;
        }
        return plan.rowInfo(plan.firstVisualRowAt(contentY)).header();
    }

    private static int columnAtX(double x, CellGeometry geometry, int cellCount) {
        double step = geometry.cellWidth() + geometry.hgap();
        if (x < geometry.startX() || step <= 0.0) {
            return -1;
        }
        int column = (int) Math.floor((x - geometry.startX()) / step);
        if (column < 0 || column >= cellCount) {
            return -1;
        }
        double cellX = geometry.startX() + column * step;
        return x <= cellX + geometry.cellWidth() ? column : -1;
    }

    private static boolean rangesIntersect(double aMin, double aMax, double bMin, double bMax) {
        return aMax >= bMin && aMin <= bMax;
    }

    private record CellGeometry(double cellWidth, double cellHeight, double hgap, double startX) {
    }

    @Override
    protected void clearVisibleMetrics() {
        super.clearVisibleMetrics();
        visibleFirstRow = -1;
        visibleLastRow = -1;
        lastVisibleFirstIndex = -1;
        topSection = null;
    }

    // ==================== Cell pool ====================

    // Reorder pass: reuse the node that rendered this item last pass so the SAME
    // node glides to its new slot; otherwise take a free, non-gliding pool cell.
    private RXTileCell<T> acquireCellForItem(int itemIndex, Map<Integer, RXTileCell<T>> prior,
                                            Set<RXTileCell<T>> used) {
        RXTileCell<T> cell = prior.get(itemIndex);
        if (cell != null && !used.contains(cell)) {
            used.add(cell);
            return cell;
        }
        for (RXTileCell<T> candidate : cellPool) {
            if (!used.contains(candidate) && !animating.contains(candidate)) {
                used.add(candidate);
                return candidate;
            }
        }
        RXTileCell<T> created = createCell();
        cellPool.add(created);
        contentLayer.getChildren().add(created);
        used.add(created);
        return created;
    }

    // Sets the cell's final geometry. A carry-over cell on a reorder pass captures
    // its old visual position and glides translate back to zero (FLIP). A fresh cell
    // entering on a reorder pops in (translate cleared). A non-reorder pass places
    // directly and never touches translate, so an in-flight glide keeps running.
    private void placeCell(RXTileCell<T> cell, double x, double y, double width, double height, boolean glide) {
        if (glide) {
            double oldVisualX = cell.getLayoutX() + cell.getTranslateX();
            double oldVisualY = cell.getLayoutY() + cell.getTranslateY();
            cell.resizeRelocate(x, y, width, height);
            animating.add(cell);
            reorderAnimator.animate(cell, oldVisualX - cell.getLayoutX(), oldVisualY - cell.getLayoutY(),
                    control.getAnimationDuration(), interpolatorOrDefault(), this::onGlideFinished);
            return;
        }
        if (reorderPass) {
            cell.setTranslateX(0.0);
            cell.setTranslateY(0.0);
        }
        cell.resizeRelocate(x, y, width, height);
    }

    private void onGlideFinished(Node node) {
        animating.remove(node);
        requestLayoutIfGlidesDone();
    }

    private void parkUnusedCells(Set<RXTileCell<T>> used) {
        for (RXTileCell<T> cell : cellPool) {
            if (used.contains(cell)) {
                continue;
            }
            // A gliding cell the pass no longer shows must not stay visible on a
            // stale item; cancel its glide so it parks like any other unused cell.
            if (animating.contains(cell)) {
                cancelGlide(cell);
            }
            parkCell(cell);
        }
    }

    @Override
    protected boolean isPinnedForAnimation(RXTileCell<T> cell) {
        return animating.contains(cell);
    }

    @Override
    protected void cancelGlide(RXTileCell<T> cell) {
        reorderAnimator.cancel(cell);
        animating.remove(cell);
    }

    @Override
    protected void onCellParked(RXTileCell<T> cell) {
        cell.updateTileFocus(false);
        cell.setTranslateX(0.0);
        cell.setTranslateY(0.0);
        cell.updateGridPosition(-1, -1);
    }

    @Override
    protected void parkAllRealized() {
        parkCellsFrom(0);
        parkHeadersFrom(0);
        parkStickyHeader();
    }

    @Override
    protected RXTileCell<T> createCell() {
        Callback<RXTileView<T>, RXTileCell<T>> factory = control.getCellFactory();
        RXTileCell<T> cell = factory != null ? factory.call(control) : createDefaultCell();
        cell.updateTileView(control);
        cell.setManaged(false);
        return cell;
    }

    private RXTileCell<T> createDefaultCell() {
        return new RXTileCell<T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty) {
                    setText(item == null ? "" : item.toString());
                }
            }
        };
    }

    // ==================== Header pool ====================

    // Headers translate as whole rows on a reorder (no per-item identity), so a
    // plain sequential pool is sufficient — no acquireCell-style identity seam.
    private RXTileSectionCell acquireHeader(int slotIndex) {
        while (headerPool.size() <= slotIndex) {
            RXTileSectionCell header = createHeader();
            headerPool.add(header);
            contentLayer.getChildren().add(header);
        }
        return headerPool.get(slotIndex);
    }

    // Full-width header placement; glides vertically only on a reorder pass.
    private void placeHeader(RXTileSectionCell header, double y, double width, double height) {
        if (!reorderPass) {
            header.resizeRelocate(snapPositionX(0.0), y, width, height);
            return;
        }
        double oldVisualY = header.getLayoutY() + header.getTranslateY();
        header.resizeRelocate(snapPositionX(0.0), y, width, height);
        animatingHeaders.add(header);
        reorderAnimator.animate(header, 0.0, oldVisualY - header.getLayoutY(),
                control.getAnimationDuration(), interpolatorOrDefault(), this::onHeaderGlideFinished);
    }

    private void onHeaderGlideFinished(Node node) {
        animatingHeaders.remove(node);
        requestLayoutIfGlidesDone();
    }

    private void parkHeadersFrom(int from) {
        for (int i = from; i < headerPool.size(); i++) {
            RXTileSectionCell header = headerPool.get(i);
            // A gliding header the pass no longer shows must not stay visible on
            // a stale section; cancel its glide so it parks normally.
            if (animatingHeaders.remove(header)) {
                reorderAnimator.cancel(header);
            }
            if (header.isVisible() || header.getItem() != null) {
                header.setVisible(false);
                header.setTranslateX(0.0);
                header.setTranslateY(0.0);
                header.updateSection(null);
            }
        }
    }

    private RXTileSectionCell createHeader() {
        Callback<RXTileView<T>, RXTileSectionCell> factory = control.getSectionHeaderFactory();
        RXTileSectionCell header = factory != null ? factory.call(control) : createDefaultHeader();
        header.setManaged(false);
        return header;
    }

    private RXTileSectionCell createDefaultHeader() {
        return new RXTileSectionCell() {
            @Override
            protected void updateItem(RXTileSection section, boolean empty) {
                super.updateItem(section, empty);
                if (!empty && section != null) {
                    setText(section.key() == null ? "" : String.valueOf(section.key()));
                }
            }
        };
    }

    // ==================== Sticky section header ====================

    /**
     * Adds or removes the pinned sticky header in response to the control's
     * {@code stickySectionHeader} property; the skin wires its listener and an
     * initial sync to this.
     *
     * @param enabled whether sticky headers are enabled
     */
    void setStickyEnabled(boolean enabled) {
        // Like recreateHeaders() / dispose(): this structural change snaps any in-flight
        // reorder glide first, so a gliding header is never left mid-flight (and skipped
        // by parkHeadersFrom) once the sticky takes over the top section's header.
        snapAllGlides();
        if (enabled) {
            ensureStickyHeader();
        } else if (stickyHeader != null) {
            getChildren().remove(stickyHeader);
            stickyHeader = null;
        }
        requestLayout();
    }

    // Single creation entry. The sticky shares the section-header factory and style
    // class but adds a 'sticky' class so tests / CSS can target it; pickOnBounds
    // makes the whole strip block clicks from reaching the cells scrolled under it.
    private void ensureStickyHeader() {
        if (stickyHeader == null) {
            stickyHeader = createHeader();
            stickyHeader.getStyleClass().add("sticky");
            stickyHeader.setPickOnBounds(true);
            addOverlay(stickyHeader);
        }
    }

    // The single gate shared by the fill skip (RXTileViewport#fillVisibleRows) and
    // the sticky placement so the two never disagree (no "skipped the in-flow header
    // but the sticky did not show" hole).
    private boolean isStickyHeaderActive(RXTileRowPlan plan) {
        return control.isStickySectionHeader()
                && plan != null && plan.headersShown()
                && plan.totalVisualRows() > 0 && topSection != null;
    }

    // The height the sticky overlay occupies at the top after a scroll (0 when it
    // will not be shown). Used by item scroll-to so the target lands below the
    // pinned header. Deliberately independent of the current topSection (which is
    // stale on the pending-scroll path) — only whether the sticky will be shown.
    private double stickyOverlayHeight(RXTileRowPlan plan) {
        return control.isStickySectionHeader() && plan.headersShown()
                ? snapSizeY(plan.headerHeight()) : 0.0;
    }

    private void layoutStickyHeader(RXTileRowPlan plan, double contentWidth) {
        if (!isStickyHeaderActive(plan)) {
            parkStickyHeader();
            return;
        }
        ensureStickyHeader();
        double stickyH = snapSizeY(plan.headerHeight());
        int topIndex = topSection.sectionIndex();

        // Handoff: once the next section's header rises into the [0, stickyH] band it
        // pushes the pinned header up; before that the pinned header rests at the top.
        double stickyY = 0.0;
        if (topIndex + 1 < plan.sectionCount()) {
            double nextY = plan.sectionTop(topIndex + 1) - scrollY;
            if (nextY < stickyH) {
                stickyY = nextY - stickyH;
            }
        }

        // Re-bind only when the pinned section actually changes; the sticky usually
        // shows the same section across many scroll frames, so this skips a per-frame
        // updateItem on the (possibly heavy) factory cell. Section records are fresh
        // instances after a recompute, so reference inequality is the right signal.
        if (stickyHeader.getItem() != topSection) {
            String oldStyle = stickyHeader.getStyle();
            stickyHeader.updateSection(topSection);
            applyCssAfterCellUpdate(stickyHeader, oldStyle);
        }
        stickyHeader.setVisible(true);
        stickyHeader.resizeRelocate(snapPositionX(0.0), snapPositionY(stickyY), contentWidth, stickyH);

        boolean pinned = scrollY > plan.sectionTop(topIndex) + STICKY_PINNED_EPSILON;
        stickyHeader.pseudoClassStateChanged(PINNED_PSEUDO_CLASS, pinned);
    }

    private void parkStickyHeader() {
        if (stickyHeader == null) {
            return;
        }
        if (stickyHeader.isVisible() || stickyHeader.getItem() != null) {
            stickyHeader.setVisible(false);
            stickyHeader.updateSection(null);
        }
        stickyHeader.pseudoClassStateChanged(PINNED_PSEUDO_CLASS, false);
    }

    // ==================== Reorder animation ====================

    private boolean animationEnabled() {
        return control.isAnimated() && getScene() != null && isAnimationDurationPositive();
    }

    private boolean isAnimationDurationPositive() {
        Duration duration = control.getAnimationDuration();
        return duration != null && !duration.isUnknown() && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO);
    }

    private Interpolator interpolatorOrDefault() {
        Interpolator value = control.getAnimationInterpolator();
        return value == null ? Interpolator.EASE_BOTH : value;
    }

    private void requestLayoutIfGlidesDone() {
        if (animating.isEmpty() && animatingHeaders.isEmpty()) {
            requestLayout();
        }
    }

    private void snapAllGlides() {
        reorderAnimator.snapAll();
        animating.clear();
        animatingHeaders.clear();
    }

    /**
     * Stops any in-flight reorder glide and snaps every cell / header to its final
     * position.
     */
    void snapReorderAnimation() {
        snapAllGlides();
        requestLayout();
    }

    /**
     * Re-evaluates the animation settings after {@code animated} or
     * {@code animationDuration} changed; snaps any in-flight glide if animation is
     * now disabled. The skin wires this to both property changes.
     */
    void onAnimationSettingsChanged() {
        if (!animationEnabled()) {
            snapReorderAnimation();
        }
    }

    // ==================== Geometry hooks ====================

    @Override
    protected double unitScrollIncrement() {
        return slotHeight();
    }

    @Override
    protected boolean smoothScrollingEnabled() {
        return control.isSmoothScrolling();
    }

    @Override
    protected SmoothScrollMode smoothScrollMode() {
        return control.getSmoothScrollMode();
    }

    @Override
    protected boolean isOwnCell(Node node) {
        return node instanceof RXTileCell;
    }

    @Override
    protected void applyCellState(RXTileCell<T> cell, int index) {
        MultipleSelectionModel<T> selectionModel = control.getSelectionModel();
        cell.updateSelected(selectionModel != null && selectionModel.isSelected(index));
        // The keyboard focus ring tracks the focused index regardless of whether the
        // control currently owns scene focus (the grid keeps its cursor position).
        cell.updateTileFocus(focusModel != null && focusModel.getFocusedIndex() == index);
    }
}
