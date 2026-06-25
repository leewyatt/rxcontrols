package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXMasonryCell;
import io.github.leewyatt.rxcontrols.RXMasonryView;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import io.github.leewyatt.rxcontrols.skins.RXMasonryPlacement.Geometry;
import javafx.animation.Interpolator;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Self-built virtualizing viewport for {@link RXMasonryViewSkin}. It owns the data
 * cell pool, its own vertical {@link ScrollBar}, a content clip and the scroll
 * offset; only the cells intersecting the visible window hold live cells. It folds
 * the scroll offset into each cell's layout position rather than translating a giant
 * content layer.
 *
 * <p>The geometry is supplied by an immutable {@link RXMasonryPlacement} that the
 * skin builds (it is width-dependent and shared with the skin's column / scroll bar
 * decision so the two never disagree). The viewport reports the visible item bounds
 * back so the skin can publish the read-only metrics.</p>
 *
 * <p>Before any structural reflow (a width or column-count change, the scroll bar
 * appearing or disappearing, a placement rebuild) it re-pins the previously
 * top-most visible item to its old screen offset, so the view does not jump at
 * critical widths. The pin is self-correcting: when the geometry is unchanged it is
 * a no-op.</p>
 *
 * <p>On a column-count change, when animation is enabled, the visible cells keep
 * their identity, are repositioned to their new slots and tween from their old
 * position via {@link RXTileReorderAnimator}; cells mid-glide are pinned so the
 * recycler leaves them alone until they land.</p>
 *
 * @param <T> the item type
 */
final class RXMasonryViewport<T> extends Region {

    // Below this px difference the bar value already matches the scroll offset, so a
    // programmatic setValue would be a redundant no-op (and a feedback risk).
    private static final double SCROLL_BAR_SYNC_EPSILON = 1.0e-4;
    // Below this px difference a measured height matches the placed height — no churn.
    private static final double MEASURE_EPSILON = 0.5;
    // Realize and measure cells up to one viewport-height below the fold so the
    // estimate->measured height change happens off-screen (capped for tall viewports).
    private static final double OVERSCAN_BELOW_MAX = 600.0;

    /**
     * Receives a measured cell height for the estimated path. {@code int}/{@code double}
     * to keep the hot measure loop free of boxing.
     */
    @FunctionalInterface
    interface HeightSink {
        void onMeasured(int index, double measuredHeight);
    }

    private final RXMasonryView<T> control;
    private final ScrollBar vbar = new ScrollBar();
    private final Pane contentLayer = new Pane();
    private final Rectangle viewportClip = new Rectangle();
    private final Rectangle contentClip = new Rectangle();

    private final List<RXMasonryCell<T>> cellPool = new ArrayList<>();

    // Cells mid-glide are pinned here and skipped by the recycler so a cell gliding to a
    // new slot is not grabbed and re-bound before it lands.
    private final Set<RXMasonryCell<T>> animating = new HashSet<>();
    private final RXTileReorderAnimator reorderAnimator = new RXTileReorderAnimator();
    // True for the duration of one fillVisible when a column-count change should glide
    // cells from their old positions to the new ones.
    private boolean reorderPass;
    private int lastColumns = -1;

    // Built by the skin each pass and shared so its scroll-bar / column decision and
    // this viewport's geometry use the exact same placement.
    private RXMasonryPlacement placement;

    // The skin's internal focus model; selection comes from the control. Both feed the
    // per-cell :selected / focus-ring state, re-applied on every (re)bind.
    private RXIndexedFocusModel<T> focusModel;

    // Estimated-path measurement: when the gate is open, realized cells are measured and
    // corrections reported to the sink. Both off for the precise provider path.
    private boolean measureGate;
    private HeightSink heightSink;

    private double scrollY;
    private boolean adjustingScrollBar;
    private boolean explicitScrollPending;

    // Anchor pin: the previously top-most visible item and its on-screen offset. Re-set
    // each fill, applied before the next reflow.
    private int anchorIndex = -1;
    private double anchorOffset;

    private double cachedMaxScroll;
    private double chromeLeft;
    private double chromeTop;
    private double chromeRight;
    private double chromeBottom;

    private int visibleFirstIndex = -1;
    private int visibleLastIndex = -1;

    private final ChangeListener<Number> scrollBarValueListener;
    private final EventHandler<ScrollEvent> scrollHandler;

    RXMasonryViewport(RXMasonryView<T> control) {
        this.control = control;
        getStyleClass().add("viewport");
        setClip(viewportClip);

        contentLayer.getStyleClass().add("content");
        contentLayer.setManaged(false);
        contentLayer.setPickOnBounds(false);
        contentLayer.setClip(contentClip);

        vbar.setOrientation(Orientation.VERTICAL);
        vbar.setManaged(false);
        vbar.setVisible(false);
        vbar.setMin(0.0);
        getChildren().addAll(contentLayer, vbar);

        scrollBarValueListener = (obs, oldValue, newValue) -> onScrollBarValue();
        scrollHandler = this::onScroll;
        vbar.valueProperty().addListener(scrollBarValueListener);
        addEventHandler(ScrollEvent.SCROLL, scrollHandler);
    }

    // ==================== Skin-facing API ====================

    /**
     * The vertical scroll-bar's CSS-preferred breadth, returned unconditionally so the
     * skin can subtract it when deciding — within a single layout pass — whether the
     * bar is needed and how many columns fit.
     *
     * @return the scroll-bar breadth in pixels
     */
    double scrollBarBreadth() {
        return snapSizeX(vbar.prefWidth(-1));
    }

    /**
     * Installs the placement for this pass. The skin builds it so its column / scroll
     * bar decision and this viewport's geometry stay consistent.
     *
     * @param placement the placement, never {@code null}
     */
    void setPlacement(RXMasonryPlacement placement) {
        this.placement = placement;
    }

    void setChromeInsets(double left, double top, double right, double bottom) {
        chromeLeft = Math.max(0.0, left);
        chromeTop = Math.max(0.0, top);
        chromeRight = Math.max(0.0, right);
        chromeBottom = Math.max(0.0, bottom);
    }

    /**
     * Drops the anchor pin so the next fill re-pins fresh. The skin calls this on an
     * items-list change, whose index shifts would otherwise make the pin re-target a
     * different item and jump the scroll.
     */
    void resetAnchor() {
        anchorIndex = -1;
        anchorOffset = 0.0;
    }

    void setFocusModel(RXIndexedFocusModel<T> focusModel) {
        this.focusModel = focusModel;
    }

    void setHeightSink(HeightSink heightSink) {
        this.heightSink = heightSink;
    }

    void setMeasureGate(boolean measureGate) {
        this.measureGate = measureGate;
    }

    double scrollOffset() {
        return scrollY;
    }

    int getVisibleFirstIndex() {
        return visibleFirstIndex;
    }

    int getVisibleLastIndex() {
        return visibleLastIndex;
    }

    int itemIndexAtContentPoint(double x, double contentY) {
        RXMasonryPlacement current = placement;
        return current == null ? -1 : current.itemAtPoint(x, contentY);
    }

    List<Integer> itemIndicesIntersectingContentRect(double x1, double y1, double x2, double y2) {
        RXMasonryPlacement current = placement;
        if (current == null) {
            return List.of();
        }
        int[] hits = current.itemsIntersecting(Math.min(x1, x2), Math.min(y1, y2),
                Math.max(x1, x2), Math.max(y1, y2));
        List<Integer> result = new ArrayList<>(hits.length);
        for (int hit : hits) {
            result.add(hit);
        }
        return result;
    }

    boolean isSelectableBlankPoint(double viewportX, double viewportY) {
        if (viewportX < 0.0 || viewportY < 0.0 || viewportY > getHeight()) {
            return false;
        }
        double contentWidth = contentWidth();
        if (viewportX > contentWidth || contentWidth <= 0.0) {
            return false;
        }
        RXMasonryPlacement current = placement;
        if (current == null || current.itemCount() == 0) {
            return false;
        }
        double contentY = viewportY + scrollY;
        return current.itemAtPoint(viewportX, contentY) < 0;
    }

    /**
     * Re-applies the {@code :selected} state and keyboard focus ring to every realized
     * cell. Cheap path for selection / focus changes that do not need a relayout.
     */
    void refreshSelectionAndFocus() {
        for (RXMasonryCell<T> cell : cellPool) {
            int index = cell.getIndex();
            if (cell.isVisible() && index >= 0) {
                applyCellState(cell, index);
            }
        }
    }

    /**
     * The realized cell under the given event target, or {@code null} when the target
     * is not a (non-empty) cell of this viewport.
     *
     * @param target the event target (typically {@code MouseEvent.getTarget()})
     * @return the hit cell, or {@code null}
     */
    RXMasonryCell<T> cellAt(EventTarget target) {
        Node node = target instanceof Node ? (Node) target : null;
        while (node != null && node != this) {
            if (node instanceof RXMasonryCell) {
                @SuppressWarnings("unchecked")
                RXMasonryCell<T> cell = (RXMasonryCell<T>) node;
                if (!cell.isEmpty() && cell.getIndex() >= 0) {
                    return cell;
                }
                return null;
            }
            node = node.getParent();
        }
        return null;
    }

    /**
     * Scrolls so the item at {@code index} lands per {@code alignment}, using the
     * current placement so it is correct even before this pass's
     * {@link #layoutChildren()} runs (the pending-scroll path).
     *
     * @param index     a valid item index
     * @param alignment where the target should land
     * @return {@code true} if applied; {@code false} when the viewport has no height
     *         yet, so the caller should keep the request pending
     */
    boolean scrollToIndex(int index, ScrollAlignment alignment) {
        double viewportHeight = getHeight();
        if (viewportHeight <= 0.0) {
            return false;
        }
        RXMasonryPlacement current = placement;
        if (current == null || current.itemCount() == 0) {
            return true;
        }
        Geometry geometry = current.geometryOf(index);
        if (geometry == null) {
            return true;
        }
        double maxScroll = Math.max(0.0, current.contentHeight() - viewportHeight);
        scrollY = clamp(targetScrollFor(geometry, viewportHeight, alignment), 0.0, maxScroll);
        explicitScrollPending = true;
        requestLayout();
        return true;
    }

    private double targetScrollFor(Geometry geometry, double viewportHeight, ScrollAlignment alignment) {
        double targetTop = geometry.y();
        double targetBottom = targetTop + geometry.height();
        return switch (alignment) {
            case CENTER -> targetTop - (viewportHeight - geometry.height()) / 2.0;
            case END -> targetBottom - viewportHeight;
            case NEAREST -> {
                if (targetTop < scrollY) {
                    yield targetTop;
                }
                if (targetBottom > scrollY + viewportHeight) {
                    yield targetBottom - viewportHeight;
                }
                yield scrollY;
            }
            default -> targetTop;
        };
    }

    /**
     * Discards the cell pool (the only path that drops cell instances); used when the
     * cell factory changes. A normal layout repopulates it.
     */
    void recreateCells() {
        snapAllGlides();
        contentLayer.getChildren().removeAll(cellPool);
        cellPool.clear();
        requestLayout();
    }

    void dispose() {
        vbar.valueProperty().removeListener(scrollBarValueListener);
        removeEventHandler(ScrollEvent.SCROLL, scrollHandler);
        snapAllGlides();
        contentLayer.getChildren().removeAll(cellPool);
        cellPool.clear();
        contentLayer.setClip(null);
        setClip(null);
    }

    // ==================== Scrolling ====================

    private void onScrollBarValue() {
        if (adjustingScrollBar) {
            return;
        }
        scrollY = vbar.getValue();
        explicitScrollPending = true;
        requestLayout();
    }

    private void onScroll(ScrollEvent event) {
        double maxScroll = cachedMaxScroll;
        if (maxScroll <= 0.0) {
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

    boolean scrollByPixels(double deltaY) {
        double maxScroll = cachedMaxScroll;
        if (maxScroll <= 0.0 || deltaY == 0.0) {
            return false;
        }
        double target = clamp(scrollY + deltaY, 0.0, maxScroll);
        if (target == scrollY) {
            return false;
        }
        scrollY = target;
        explicitScrollPending = true;
        requestLayout();
        return true;
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        viewportClip.setX(-chromeLeft);
        viewportClip.setY(-chromeTop);
        viewportClip.setWidth(w + chromeLeft + chromeRight);
        viewportClip.setHeight(h + chromeTop + chromeBottom);

        RXMasonryPlacement current = placement;
        if (w <= 0.0 || h <= 0.0) {
            // Not sized yet: preserve scrollY and the bar value for the first sized pass.
            layoutContentLayer(Math.max(0.0, w), Math.max(0.0, h));
            cachedMaxScroll = 0.0;
            explicitScrollPending = false;
            adjustingScrollBar = true;
            vbar.setMax(0.0);
            vbar.setVisibleAmount(0.0);
            adjustingScrollBar = false;
            parkCellsFrom(0);
            vbar.setVisible(false);
            clearVisibleMetrics();
            return;
        }
        if (current == null || current.itemCount() == 0) {
            // Genuinely empty: reset the scroll offset to the top.
            layoutContentLayer(w, h);
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
            vbar.setVisible(false);
            clearVisibleMetrics();
            return;
        }

        double contentHeight = current.contentHeight();
        double maxScroll = Math.max(0.0, contentHeight - h);
        cachedMaxScroll = maxScroll;

        // Anchor pin: keep the previously top-most visible item at its old screen
        // offset across a reflow. Skipped right after an explicit scroll (wheel, bar
        // drag, scrollTo). Self-correcting: a no-op when the geometry is unchanged.
        if (!explicitScrollPending && anchorIndex >= 0 && anchorIndex < current.itemCount()) {
            Geometry anchor = current.geometryOf(anchorIndex);
            if (anchor != null) {
                scrollY = anchor.y() - anchorOffset;
            }
        }
        // A column-count change is the reorder-glide trigger when animation is on;
        // otherwise cells snap to their new slots. The first sized pass (lastColumns < 0)
        // never animates. Skipped right after an explicit scroll.
        int columns = current.columns();
        boolean columnsChanged = !explicitScrollPending && lastColumns > 0 && lastColumns != columns;
        reorderPass = columnsChanged && animationEnabled();
        lastColumns = columns;
        explicitScrollPending = false;
        scrollY = clamp(scrollY, 0.0, maxScroll);

        boolean needBar = maxScroll > 0.0;
        double barBreadth = needBar ? scrollBarBreadth() : 0.0;
        if (needBar) {
            adjustingScrollBar = true;
            vbar.setMax(maxScroll);
            vbar.setVisibleAmount(h);
            vbar.setUnitIncrement(unitScrollIncrement());
            vbar.setBlockIncrement(h);
            if (Math.abs(vbar.getValue() - scrollY) > SCROLL_BAR_SYNC_EPSILON) {
                vbar.setValue(scrollY);
            }
            adjustingScrollBar = false;
            double barX = w - barBreadth + (chromeRight < 1.0 ? 0.0 : chromeRight - 1.0);
            vbar.resizeRelocate(barX, -chromeTop, barBreadth, h + chromeTop + chromeBottom);
            vbar.setVisible(true);
        } else {
            vbar.setVisible(false);
        }

        double contentWidth = Math.max(0.0, w - barBreadth);
        layoutContentLayer(contentWidth, h);
        fillVisible(current, h);
    }

    private void layoutContentLayer(double width, double height) {
        contentLayer.resizeRelocate(0.0, 0.0, width, height);
        contentClip.setX(0.0);
        contentClip.setY(0.0);
        contentClip.setWidth(width);
        contentClip.setHeight(height);
    }

    private void fillVisible(RXMasonryPlacement current, double viewportHeight) {
        // Extend the realized window DOWNWARD only, so the estimate->measured change for
        // items about to scroll into view from below (the dominant direction) happens
        // off-screen; items entering from the top are measured on entry and the anchor pin
        // keeps the visible content put.
        double overscanBelow = measureGate ? Math.min(viewportHeight, OVERSCAN_BELOW_MAX) : 0.0;
        int[] visible = current.visibleItems(scrollY, viewportHeight + overscanBelow);

        // On a reorder pass, snapshot which cell rendered each item BEFORE rebinding, so
        // the same node can be re-found for its item and glide to the new slot.
        Map<Integer, RXMasonryCell<T>> priorItemToCell = null;
        Set<RXMasonryCell<T>> usedThisPass = null;
        if (reorderPass) {
            priorItemToCell = new HashMap<>();
            for (RXMasonryCell<T> cell : cellPool) {
                if (cell.getIndex() >= 0) {
                    priorItemToCell.put(cell.getIndex(), cell);
                }
            }
            usedThisPass = new HashSet<>();
        }

        int cursor = 0;
        int anchorCandidate = -1;
        double anchorCandidateTop = Double.POSITIVE_INFINITY;
        int trueFirst = -1;
        int trueLast = -1;
        double trueBottom = scrollY + viewportHeight;
        for (int itemIndex : visible) {
            Geometry geometry = current.geometryOf(itemIndex);
            if (geometry == null) {
                continue;
            }
            RXMasonryCell<T> prior = reorderPass ? priorItemToCell.get(itemIndex) : null;
            RXMasonryCell<T> cell = reorderPass
                    ? acquireCellForItem(itemIndex, priorItemToCell, usedThisPass)
                    : acquireCell(cursor++);
            String oldStyle = cell.getStyle();
            cell.updateMasonryPosition(current.startColumnOf(itemIndex), current.spanOf(itemIndex));
            cell.updateIndex(itemIndex);
            cell.setVisible(true);
            applyCellState(cell, itemIndex);
            applyCssAfterCellUpdate(cell, oldStyle);
            // Only a carry-over cell (the one that rendered this item last pass) glides;
            // a freshly repurposed or entering cell pops in at its slot.
            placeCell(cell, snapPositionX(geometry.x()), snapPositionY(geometry.y() - scrollY),
                    snapSizeX(geometry.width()), snapSizeY(geometry.height()), cell == prior);
            measureCell(cell, itemIndex, geometry);
            if (geometry.y() < anchorCandidateTop) {
                anchorCandidateTop = geometry.y();
                anchorCandidate = itemIndex;
            }
            // True visible window (excluding the overscan band) for the published metrics.
            if (geometry.y() < trueBottom && geometry.y() + geometry.height() > scrollY) {
                if (trueFirst < 0 || itemIndex < trueFirst) {
                    trueFirst = itemIndex;
                }
                if (itemIndex > trueLast) {
                    trueLast = itemIndex;
                }
            }
        }
        if (reorderPass) {
            parkUnusedCells(usedThisPass);
        } else {
            parkCellsFrom(cursor);
        }

        visibleFirstIndex = trueFirst;
        visibleLastIndex = trueLast;
        anchorIndex = anchorCandidate;
        anchorOffset = anchorCandidate >= 0 ? anchorCandidateTop - scrollY : 0.0;
    }

    // Estimated path only: measure the realized cell's real pref height at its effective
    // width and, when it differs from the placed (cached/estimated) height, report the
    // correction so the cache can re-pack on the next pass.
    private void measureCell(RXMasonryCell<T> cell, int itemIndex, Geometry geometry) {
        if (!measureGate || heightSink == null) {
            return;
        }
        // A custom cell may switch a style class (padding / font / wrap) in updateItem
        // without dirtying layout, which the inline-style check above does not catch;
        // force CSS now (cheap when already clean) so the measured height is not stale.
        if (cell.getScene() != null) {
            cell.applyCss();
        }
        double real = snapSizeY(cell.prefHeight(geometry.width()));
        if (Math.abs(real - geometry.height()) > MEASURE_EPSILON) {
            heightSink.onMeasured(itemIndex, real);
        }
    }

    double contentWidth() {
        double barBreadth = cachedMaxScroll > 0.0 ? scrollBarBreadth() : 0.0;
        return Math.max(0.0, getWidth() - barBreadth);
    }

    int verticalNeighbor(int index, int direction, double referenceX) {
        RXMasonryPlacement current = placement;
        return current == null ? -1 : current.verticalNeighbor(index, direction, referenceX);
    }

    double itemCenterX(int index) {
        RXMasonryPlacement current = placement;
        return current == null ? Double.NaN : current.itemCenterX(index);
    }

    double itemTop(int index) {
        RXMasonryPlacement current = placement;
        if (current == null) {
            return Double.NaN;
        }
        Geometry geometry = current.geometryOf(index);
        return geometry == null ? Double.NaN : geometry.y();
    }

    // A reasonable wheel/arrow step that does not depend on variable cell heights.
    private double unitScrollIncrement() {
        double estimated = RXMasonryViewSkin.estimatedCellHeightOrDefault(control);
        double step = snapSizeY(estimated / 3.0);
        return step > 0.0 ? step : 1.0;
    }

    private void applyCellState(RXMasonryCell<T> cell, int index) {
        MultipleSelectionModel<T> selectionModel = control.getSelectionModel();
        cell.updateSelected(selectionModel != null && selectionModel.isSelected(index));
        cell.updateMasonryFocus(focusModel != null && focusModel.getFocusedIndex() == index);
    }

    private static void applyCssAfterCellUpdate(Control cell, String oldInlineStyle) {
        // Match VirtualFlow.setCellIndex: updateItem can mutate CSS while the
        // virtualizer is already in layout, so apply before this frame is painted.
        if (cell.getScene() != null
                && (cell.isNeedsLayout() || !Objects.equals(oldInlineStyle, cell.getStyle()))) {
            cell.applyCss();
        }
    }

    private void clearVisibleMetrics() {
        visibleFirstIndex = -1;
        visibleLastIndex = -1;
        anchorIndex = -1;
        anchorOffset = 0.0;
    }

    // ==================== Cell pool ====================

    private RXMasonryCell<T> acquireCell(int slotIndex) {
        while (cellPool.size() <= slotIndex) {
            RXMasonryCell<T> cell = createCell();
            cellPool.add(cell);
            contentLayer.getChildren().add(cell);
        }
        return cellPool.get(slotIndex);
    }

    // Reorder pass: reuse the node that rendered this item last pass so the SAME node
    // glides to its new slot; otherwise take a free, non-gliding pool cell.
    private RXMasonryCell<T> acquireCellForItem(int itemIndex, Map<Integer, RXMasonryCell<T>> prior,
                                                Set<RXMasonryCell<T>> used) {
        RXMasonryCell<T> cell = prior.get(itemIndex);
        if (cell != null && !used.contains(cell)) {
            used.add(cell);
            return cell;
        }
        for (RXMasonryCell<T> candidate : cellPool) {
            if (!used.contains(candidate) && !animating.contains(candidate)) {
                used.add(candidate);
                return candidate;
            }
        }
        RXMasonryCell<T> created = createCell();
        cellPool.add(created);
        contentLayer.getChildren().add(created);
        used.add(created);
        return created;
    }

    // Sets the cell's final geometry. A carry-over cell on a reorder pass captures its
    // old visual position and glides translate back to zero (FLIP). A fresh cell entering
    // on a reorder pops in (translate cleared). A non-reorder pass places directly and
    // never touches translate, so an in-flight glide keeps running.
    private void placeCell(RXMasonryCell<T> cell, double x, double y, double width, double height,
                           boolean glide) {
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

    private void parkCellsFrom(int from) {
        for (int i = from; i < cellPool.size(); i++) {
            RXMasonryCell<T> cell = cellPool.get(i);
            if (animating.contains(cell)) {
                continue;
            }
            parkCell(cell);
        }
    }

    private void parkUnusedCells(Set<RXMasonryCell<T>> used) {
        for (RXMasonryCell<T> cell : cellPool) {
            if (used.contains(cell) || animating.contains(cell)) {
                continue;
            }
            parkCell(cell);
        }
    }

    private void parkCell(RXMasonryCell<T> cell) {
        if (cell.isVisible() || cell.getIndex() != -1) {
            cell.setVisible(false);
            cell.updateMasonryFocus(false);
            cell.setTranslateX(0.0);
            cell.setTranslateY(0.0);
            // Reset the slot position too, so a parked (empty) cell honors the
            // columnIndex == -1 / columnSpan == 1 contract and updateItem(empty) never
            // sees a stale column.
            cell.updateMasonryPosition(-1, 1);
            cell.updateIndex(-1);
        }
    }

    private RXMasonryCell<T> createCell() {
        Callback<RXMasonryView<T>, RXMasonryCell<T>> factory = control.getCellFactory();
        RXMasonryCell<T> cell = factory != null ? factory.call(control) : createDefaultCell();
        cell.updateMasonryView(control);
        cell.setManaged(false);
        return cell;
    }

    private RXMasonryCell<T> createDefaultCell() {
        return new RXMasonryCell<T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty) {
                    setText(item == null ? "" : item.toString());
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

    private Interpolator interpolatorOrDefault() {
        Interpolator value = control.getAnimationInterpolator();
        return value == null ? Interpolator.EASE_BOTH : value;
    }

    private void requestLayoutIfGlidesDone() {
        if (animating.isEmpty()) {
            requestLayout();
        }
    }

    private void snapAllGlides() {
        reorderAnimator.snapAll();
        animating.clear();
    }

    /**
     * Re-evaluates the animation settings after {@code animated} or
     * {@code animationDuration} changed; snaps any in-flight glide if animation is now
     * disabled. The skin wires this to both property changes.
     */
    void onAnimationSettingsChanged() {
        if (!animationEnabled()) {
            snapAllGlides();
            requestLayout();
        }
    }

    // ==================== Geometry helpers ====================

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected double computeMaxWidth(double height) {
        return Double.MAX_VALUE;
    }

    @Override
    protected double computeMaxHeight(double width) {
        return Double.MAX_VALUE;
    }
}
