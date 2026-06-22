package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXGridScrollAlignment;
import io.github.leewyatt.rxcontrols.RXTileCell;
import io.github.leewyatt.rxcontrols.RXTileSection;
import io.github.leewyatt.rxcontrols.RXTileSectionCell;
import io.github.leewyatt.rxcontrols.RXTileView;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Self-built virtualizing viewport for {@link RXTileViewSkin}. It owns the data
 * cell pool, an independent section-header pool, its own vertical
 * {@link ScrollBar}, a content clip and the scroll offset; only the visual rows
 * intersecting the visible window hold live cells/headers.
 *
 * <p>The visual-row geometry is supplied by a {@link RXTileRowPlan} that the
 * skin builds (it is width-dependent and shared with the skin's column / scroll
 * bar decision so the two never disagree). Flat is the degenerate plan with no
 * section rows. The viewport reports the visible item range, data-row range and
 * top section back so the skin can publish the read-only metrics.
 *
 * <p>On a column-count change the viewport runs a reorder glide: visible cells
 * (and section headers) keep their identity, are repositioned to their new slots,
 * and tween from their old position via {@link RXTileReorderAnimator}. Cells
 * mid-glide are pinned so the recycler leaves them alone until they land.
 *
 * @param <T> the item type
 */
final class RXTileViewport<T> extends Region {

    // Below this px difference the bar value already matches the scroll offset, so
    // a programmatic setValue would be a redundant no-op (and a feedback risk).
    private static final double SCROLL_BAR_SYNC_EPSILON = 1.0e-4;

    private final RXTileView<T> control;
    private final ScrollBar vbar = new ScrollBar();
    private final Rectangle clip = new Rectangle();

    private final List<RXTileCell<T>> cellPool = new ArrayList<>();
    private final List<RXTileSectionCell> headerPool = new ArrayList<>();
    // Cells / headers mid-glide are pinned here and skipped by the recycler so a
    // tile gliding to a new slot is not grabbed and re-bound before it lands.
    private final Set<RXTileCell<T>> animating = new HashSet<>();
    private final Set<RXTileSectionCell> animatingHeaders = new HashSet<>();
    private final RXTileReorderAnimator reorderAnimator = new RXTileReorderAnimator();
    // True for the duration of one fillVisibleRows when a column-count change should
    // glide cells/headers from their old positions to the new ones.
    private boolean reorderPass;

    // Built by the skin each pass and shared so the skin's scroll-bar/column
    // decision and this viewport's geometry use the exact same plan.
    private RXTileRowPlan rowPlan;

    // The skin's internal focus model; selection comes from the control. Both feed
    // the per-cell :selected / focus-ring state, re-applied on every (re)bind.
    private RXTileFocusModel<T> focusModel;

    private double scrollY;
    private boolean adjustingScrollBar;

    private int lastColumnCount = -1;
    private int lastVisibleFirstIndex = -1;
    private boolean explicitScrollPending;

    private double cachedMaxScroll;

    // Published to the skin after each pass (-1 / null when nothing is visible).
    private int visibleFirstIndex = -1;
    private int visibleLastIndex = -1;
    private int visibleFirstRow = -1;
    private int visibleLastRow = -1;
    private RXTileSection topSection;

    private final ChangeListener<Number> scrollBarValueListener;
    private final EventHandler<ScrollEvent> scrollHandler;

    RXTileViewport(RXTileView<T> control) {
        this.control = control;
        getStyleClass().add("viewport");
        setClip(clip);

        vbar.setOrientation(Orientation.VERTICAL);
        vbar.setManaged(false);
        vbar.setVisible(false);
        vbar.setMin(0.0);
        getChildren().add(vbar);

        scrollBarValueListener = (obs, oldValue, newValue) -> onScrollBarValue();
        scrollHandler = this::onScroll;
        vbar.valueProperty().addListener(scrollBarValueListener);
        addEventHandler(ScrollEvent.SCROLL, scrollHandler);
    }

    // ==================== Skin-facing API ====================

    /**
     * The vertical scroll-bar's CSS-preferred breadth (never a hard-coded guess),
     * returned unconditionally so the skin can subtract it when deciding — within
     * a single layout pass — whether the bar is needed and how many columns fit.
     *
     * @return the scroll-bar breadth in pixels
     */
    double scrollBarBreadth() {
        return snapSizeX(vbar.prefWidth(-1));
    }

    /**
     * The pixel height of one data-row slot (cell height + vgap), snapped, and the
     * single source of truth shared with the skin's plan + overflow check. Never
     * returns a non-positive value — that would break the row math; cellHeight's
     * coerce+throw normally guarantees it, the guard covers a bound-illegal source.
     *
     * @return the data-row slot height, always {@code > 0}
     */
    double slotHeight() {
        double slot = snapSizeY(RXTileViewSkin.cellHeightOrDefault(control)
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

    int verticalNeighborOf(int itemIndex, int direction, int preferredColumn) {
        RXTileRowPlan plan = rowPlan;
        if (plan == null) {
            return -1;
        }
        return plan.verticalNeighborOfItem(itemIndex, direction, preferredColumn);
    }

    void setFocusModel(RXTileFocusModel<T> focusModel) {
        this.focusModel = focusModel;
    }

    /**
     * Re-applies the {@code :selected} state and keyboard focus ring to every
     * realized cell from the current selection model and focus model. Cheap path
     * for selection/focus changes that do not need a full relayout.
     */
    void refreshSelectionAndFocus() {
        for (RXTileCell<T> cell : cellPool) {
            int index = cell.getIndex();
            if (cell.isVisible() && index >= 0) {
                applyCellState(cell, index);
            }
        }
    }

    /**
     * The realized data cell under the given event target, or {@code null} when
     * the target is not a (non-empty) cell of this viewport.
     *
     * @param target the event target (typically {@code MouseEvent.getTarget()})
     * @return the hit cell, or {@code null}
     */
    RXTileCell<T> cellAt(EventTarget target) {
        Node node = target instanceof Node ? (Node) target : null;
        while (node != null && node != this) {
            if (node instanceof RXTileCell) {
                @SuppressWarnings("unchecked")
                RXTileCell<T> cell = (RXTileCell<T>) node;
                if (!cell.isEmpty() && cell.getIndex() >= 0) {
                    return cell;
                }
                return null;
            }
            node = node.getParent();
        }
        return null;
    }

    private void applyCellState(RXTileCell<T> cell, int index) {
        MultipleSelectionModel<T> selectionModel = control.getSelectionModel();
        cell.updateSelected(selectionModel != null && selectionModel.isSelected(index));
        // The keyboard focus ring tracks the focused index regardless of whether the
        // control currently owns scene focus (the grid keeps its cursor position).
        cell.updateTileFocus(focusModel != null && focusModel.getFocusedIndex() == index);
    }

    int getVisibleFirstIndex() {
        return visibleFirstIndex;
    }

    int getVisibleLastIndex() {
        return visibleLastIndex;
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
    boolean scrollToIndex(int index, RXGridScrollAlignment alignment) {
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
        double targetTop = info.top();
        double maxScroll = Math.max(0.0, plan.contentHeight() - viewportHeight);

        double target;
        if (alignment == RXGridScrollAlignment.NEAREST) {
            if (targetTop < scrollY) {
                target = targetTop;
            } else if (targetTop + info.height() > scrollY + viewportHeight) {
                target = targetTop + info.height() - viewportHeight;
            } else {
                target = scrollY;
            }
        } else {
            // START, and the interim CENTER / END which currently behave as START.
            target = targetTop;
        }
        scrollY = clamp(target, 0.0, maxScroll);
        explicitScrollPending = true;
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
    boolean scrollToSectionIndex(int sectionIndex, RXGridScrollAlignment alignment) {
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
        double targetTop = info.top();
        double maxScroll = Math.max(0.0, plan.contentHeight() - viewportHeight);

        double target;
        if (alignment == RXGridScrollAlignment.NEAREST) {
            if (targetTop < scrollY) {
                target = targetTop;
            } else if (targetTop + info.height() > scrollY + viewportHeight) {
                target = targetTop + info.height() - viewportHeight;
            } else {
                target = scrollY;
            }
        } else {
            target = targetTop;
        }
        scrollY = clamp(target, 0.0, maxScroll);
        explicitScrollPending = true;
        requestLayout();
        return true;
    }

    /**
     * Discards the data cell pool (the only path that drops cell instances); used
     * when the cell factory changes. A normal layout repopulates it.
     */
    void recreateCells() {
        snapAllGlides();
        getChildren().removeAll(cellPool);
        cellPool.clear();
        requestLayout();
    }

    /**
     * Discards the section-header pool; used when the section-header factory
     * changes. A normal layout repopulates it.
     */
    void recreateHeaders() {
        snapAllGlides();
        getChildren().removeAll(headerPool);
        headerPool.clear();
        requestLayout();
    }

    void dispose() {
        vbar.valueProperty().removeListener(scrollBarValueListener);
        removeEventHandler(ScrollEvent.SCROLL, scrollHandler);
        snapAllGlides();
        getChildren().removeAll(cellPool);
        getChildren().removeAll(headerPool);
        cellPool.clear();
        headerPool.clear();
        setClip(null);
    }

    // ==================== Scrolling ====================

    private void onScrollBarValue() {
        if (adjustingScrollBar) {
            return;
        }
        scrollY = vbar.getValue();
        explicitScrollPending = true;
        // Dirties this viewport (so it re-fills) and propagates to the control (so
        // the skin republishes the visible range).
        requestLayout();
    }

    private void onScroll(ScrollEvent event) {
        double maxScroll = cachedMaxScroll;
        if (maxScroll <= 0.0) {
            // Nothing to scroll; leave the event for an enclosing scroll surface.
            return;
        }
        double deltaY = event.getDeltaY();
        if (deltaY == 0.0) {
            return;
        }
        double target = clamp(scrollY - deltaY, 0.0, maxScroll);
        if (target != scrollY) {
            scrollY = target;
            explicitScrollPending = true;
            requestLayout();
        }
        event.consume();
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        clip.setX(0.0);
        clip.setY(0.0);
        clip.setWidth(w);
        clip.setHeight(h);

        RXTileRowPlan plan = rowPlan;
        if (w <= 0.0 || h <= 0.0 || plan == null) {
            cachedMaxScroll = 0.0;
            explicitScrollPending = false;
            adjustingScrollBar = true;
            vbar.setMax(0.0);
            vbar.setVisibleAmount(0.0);
            adjustingScrollBar = false;
            parkCellsFrom(0);
            parkHeadersFrom(0);
            vbar.setVisible(false);
            clearVisibleMetrics();
            return;
        }
        if (plan.totalVisualRows() == 0) {
            scrollY = 0.0;
            cachedMaxScroll = 0.0;
            explicitScrollPending = false;
            adjustingScrollBar = true;
            vbar.setMax(0.0);
            vbar.setVisibleAmount(0.0);
            if (Math.abs(vbar.getValue()) > SCROLL_BAR_SYNC_EPSILON) {
                vbar.setValue(0.0);
            }
            adjustingScrollBar = false;
            parkCellsFrom(0);
            parkHeadersFrom(0);
            vbar.setVisible(false);
            clearVisibleMetrics();
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
            scrollY = plan.rowInfo(plan.visualRowOfItem(lastVisibleFirstIndex)).top();
        }
        // The same column-count change is the reorder-glide trigger when animation
        // is on; otherwise cells snap to their new slots as before.
        reorderPass = columnsChanged && animationEnabled();
        lastColumnCount = cols;
        explicitScrollPending = false;

        scrollY = clamp(scrollY, 0.0, maxScroll);

        boolean needBar = maxScroll > 0.0;
        double barBreadth = needBar ? scrollBarBreadth() : 0.0;
        if (needBar) {
            adjustingScrollBar = true;
            vbar.setMax(maxScroll);
            vbar.setVisibleAmount(h);
            vbar.setUnitIncrement(slotHeight());
            vbar.setBlockIncrement(h);
            if (Math.abs(vbar.getValue() - scrollY) > SCROLL_BAR_SYNC_EPSILON) {
                vbar.setValue(scrollY);
            }
            adjustingScrollBar = false;
            vbar.resizeRelocate(w - barBreadth, 0.0, barBreadth, h);
            vbar.setVisible(true);
        } else {
            vbar.setVisible(false);
        }

        double contentWidth = Math.max(0.0, w - barBreadth);

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
        lastVisibleFirstIndex = visibleFirstIndex;
    }

    private void fillVisibleRows(RXTileRowPlan plan, int first, int last, double contentWidth) {
        boolean stretch = control.isStretchCells();
        double hgap = snapSpaceX(RXTileViewSkin.gapOrZero(control.getHgap()));
        double cellHeight = snapSizeY(RXTileViewSkin.cellHeightOrDefault(control));
        int cols = plan.columns();

        double cellWidth;
        double startX;
        if (stretch) {
            cellWidth = snapSizeX(Math.max(0.0, (contentWidth - (cols - 1) * hgap) / cols));
            startX = 0.0;
        } else {
            cellWidth = snapSizeX(RXTileViewSkin.cellWidthOrDefault(control));
            double slack = Math.max(0.0, contentWidth - (cols * cellWidth + (cols - 1) * hgap));
            // V1 positions the fixed-width block; SPACE_* / STRETCH fall back to START.
            switch (RXTileViewSkin.justifyOrDefault(control.getItemsJustify())) {
                case CENTER -> startX = slack / 2.0;
                case END -> startX = slack;
                default -> startX = 0.0;
            }
        }

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
                RXTileSectionCell header = acquireHeader(headerCursor++);
                header.updateSection(info.section());
                header.setVisible(true);
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
                    cell.updateGridPosition(info.dataRowIndex(), column);
                    cell.updateIndex(itemIndex);
                    cell.setVisible(true);
                    applyCellState(cell, itemIndex);
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

    private void clearVisibleMetrics() {
        visibleFirstIndex = -1;
        visibleLastIndex = -1;
        visibleFirstRow = -1;
        visibleLastRow = -1;
        lastVisibleFirstIndex = -1;
        topSection = null;
    }

    // ==================== Cell pool ====================

    /**
     * Returns the cell for the {@code slotIndex}-th visible tile of this pass
     * (sequential assignment), growing the pool as needed. Used on normal passes;
     * a reorder pass uses {@link #acquireCellForItem} to keep cell identity.
     *
     * @param slotIndex the zero-based position in this pass's visible cell sequence
     * @return a cell to bind
     */
    private RXTileCell<T> acquireCell(int slotIndex) {
        while (cellPool.size() <= slotIndex) {
            RXTileCell<T> cell = createCell();
            cellPool.add(cell);
            getChildren().add(cell);
        }
        return cellPool.get(slotIndex);
    }

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
        getChildren().add(created);
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
                    control.getAnimationDuration(), this::onGlideFinished);
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

    private void parkCellsFrom(int from) {
        for (int i = from; i < cellPool.size(); i++) {
            RXTileCell<T> cell = cellPool.get(i);
            if (animating.contains(cell)) {
                continue;
            }
            parkCell(cell);
        }
    }

    private void parkUnusedCells(Set<RXTileCell<T>> used) {
        for (RXTileCell<T> cell : cellPool) {
            if (used.contains(cell) || animating.contains(cell)) {
                continue;
            }
            parkCell(cell);
        }
    }

    private void parkCell(RXTileCell<T> cell) {
        if (cell.isVisible() || cell.getIndex() != -1) {
            cell.setVisible(false);
            cell.updateTileFocus(false);
            cell.setTranslateX(0.0);
            cell.setTranslateY(0.0);
            cell.updateIndex(-1);
        }
    }

    private RXTileCell<T> createCell() {
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
            getChildren().add(header);
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
                control.getAnimationDuration(), this::onHeaderGlideFinished);
    }

    private void onHeaderGlideFinished(Node node) {
        animatingHeaders.remove(node);
        requestLayoutIfGlidesDone();
    }

    private void parkHeadersFrom(int from) {
        for (int i = from; i < headerPool.size(); i++) {
            RXTileSectionCell header = headerPool.get(i);
            if (animatingHeaders.contains(header)) {
                continue;
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

    // ==================== Reorder animation ====================

    private boolean animationEnabled() {
        return control.isAnimated() && getScene() != null && isAnimationDurationPositive();
    }

    private boolean isAnimationDurationPositive() {
        Duration duration = control.getAnimationDuration();
        return duration != null && !duration.isUnknown() && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO);
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

    // ==================== Geometry helpers ====================

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== Sizing ====================

    @Override
    protected double computeMaxWidth(double height) {
        return Double.MAX_VALUE;
    }

    @Override
    protected double computeMaxHeight(double width) {
        return Double.MAX_VALUE;
    }
}
