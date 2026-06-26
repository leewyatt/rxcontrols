package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Self-built virtualizing viewport for {@link RXListViewSkin}. It owns the data
 * cell pool, its own vertical {@link ScrollBar}, a content clip and the scroll
 * offset; only the rows intersecting the visible window hold live cells.
 *
 * <p>The single-column fixed-height geometry is supplied by a
 * {@link RXListRowPlan} that the skin builds (item count + resolved row height);
 * the viewport itself decides whether the vertical scroll bar is needed (its
 * content height is width-independent, so unlike the multi-column tile viewport
 * there is no two-pass column/scroll-bar interplay). The viewport reports the
 * visible item range back so the skin can publish the read-only metrics.
 *
 * <p>This is the list-shaped sibling of {@code RXTileViewport}: a single column,
 * one item per row, uniform height, with the multi-column / justify / reorder
 * machinery removed.
 *
 * @param <T> the item type
 */
final class RXListViewport<T> extends Region {

    // Below this px difference the bar value already matches the scroll offset, so
    // a programmatic setValue would be a redundant no-op (and a feedback risk).
    private static final double SCROLL_BAR_SYNC_EPSILON = 1.0e-4;

    private final RXListView<T> control;
    private final ScrollBar vbar = new ScrollBar();
    private final Pane contentLayer = new Pane();
    private final Rectangle viewportClip = new Rectangle();
    private final Rectangle contentClip = new Rectangle();

    private final List<RXListCell<T>> cellPool = new ArrayList<>();

    // Supplied by the skin (item count + resolved row height) as the geometry
    // source of truth; this viewport then decides the scroll bar from it.
    private RXListRowPlan rowPlan;

    // The skin's internal focus model; selection comes from the control. Both feed
    // the per-cell :selected / focus-ring state, re-applied on every (re)bind.
    private RXIndexedFocusModel<T> focusModel;

    private double scrollY;
    private boolean adjustingScrollBar;

    private double cachedMaxScroll;
    private double chromeLeft;
    private double chromeTop;
    private double chromeRight;
    private double chromeBottom;

    // Published to the skin after each pass (-1 when nothing is visible).
    private int visibleFirstIndex = -1;
    private int visibleLastIndex = -1;

    private final ChangeListener<Number> scrollBarValueListener;
    private final EventHandler<ScrollEvent> scrollHandler;

    RXListViewport(RXListView<T> control) {
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
     * The vertical scroll-bar's CSS-preferred breadth (never a hard-coded guess),
     * so the skin can subtract it when deciding whether the bar is needed.
     *
     * @return the scroll-bar breadth in pixels
     */
    double scrollBarBreadth() {
        return snapSizeX(vbar.prefWidth(-1));
    }

    /**
     * Installs the geometry plan for this pass. The skin builds it (item count +
     * resolved row height) as the single geometry source; this viewport derives
     * its scroll bar and visible window from it.
     *
     * @param plan the row plan, never {@code null}
     */
    void setRowPlan(RXListRowPlan plan) {
        this.rowPlan = plan;
    }

    void setChromeInsets(double left, double top, double right, double bottom) {
        chromeLeft = Math.max(0.0, left);
        chromeTop = Math.max(0.0, top);
        chromeRight = Math.max(0.0, right);
        chromeBottom = Math.max(0.0, bottom);
    }

    void setFocusModel(RXIndexedFocusModel<T> focusModel) {
        this.focusModel = focusModel;
    }

    int getVisibleFirstIndex() {
        return visibleFirstIndex;
    }

    int getVisibleLastIndex() {
        return visibleLastIndex;
    }

    /**
     * Re-applies the {@code :selected} state and keyboard focus ring to every
     * realized cell from the current selection model and focus model. Cheap path
     * for selection/focus changes that do not need a full relayout.
     */
    void refreshSelectionAndFocus() {
        for (RXListCell<T> cell : cellPool) {
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
    RXListCell<T> cellAt(EventTarget target) {
        Node node = target instanceof Node ? (Node) target : null;
        while (node != null && node != this) {
            if (node instanceof RXListCell) {
                @SuppressWarnings("unchecked")
                RXListCell<T> cell = (RXListCell<T>) node;
                if (!cell.isEmpty() && cell.getIndex() >= 0) {
                    return cell;
                }
                return null;
            }
            node = node.getParent();
        }
        return null;
    }

    private void applyCellState(RXListCell<T> cell, int index) {
        MultipleSelectionModel<T> selectionModel = control.getSelectionModel();
        cell.updateSelected(selectionModel != null && selectionModel.isSelected(index));
        // The keyboard focus ring tracks the focused index regardless of whether the
        // control currently owns scene focus (the list keeps its cursor position).
        cell.updateListFocus(focusModel != null && focusModel.getFocusedIndex() == index);
    }

    private static void applyCssAfterCellUpdate(Control cell, String oldInlineStyle) {
        // Match VirtualFlow.setCellIndex: updateItem can mutate CSS while the
        // virtualizer is already in layout, so apply before this frame is painted.
        if (cell.getScene() != null
                && (cell.isNeedsLayout() || !Objects.equals(oldInlineStyle, cell.getStyle()))) {
            cell.applyCss();
        }
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
        RXListRowPlan plan = rowPlan;
        if (plan == null || plan.itemCount() == 0) {
            return true;
        }
        double maxScroll = Math.max(0.0, plan.contentHeight() - viewportHeight);
        double target = targetScrollFor(plan.topOfItem(index), plan.rowHeight(), viewportHeight, alignment);
        scrollY = clamp(target, 0.0, maxScroll);
        requestLayout();
        return true;
    }

    private double targetScrollFor(double targetTop, double rowHeight, double viewportHeight,
                                   ScrollAlignment alignment) {
        double targetBottom = targetTop + rowHeight;
        return switch (alignment) {
            case CENTER -> targetTop - (viewportHeight - rowHeight) / 2.0;
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
     * Applies a relative pixel scroll, clamped to the scrollable range, computing
     * the range fresh from the current plan and height so it is correct on the
     * pending-scroll path (before this pass's {@link #layoutChildren()} runs).
     *
     * @param deltaY the signed pixel delta (positive scrolls down)
     * @return {@code true} if the request was applied (so the caller can clear it);
     *         {@code false} when the viewport has no height yet, so the caller
     *         should keep it pending
     */
    boolean scrollByPixels(double deltaY) {
        double viewportHeight = getHeight();
        if (viewportHeight <= 0.0) {
            // Geometry is not known yet; leave the request armed for a sized pass.
            return false;
        }
        RXListRowPlan plan = rowPlan;
        if (plan == null || plan.itemCount() == 0) {
            return true;
        }
        double maxScroll = Math.max(0.0, plan.contentHeight() - viewportHeight);
        double target = clamp(scrollY + deltaY, 0.0, maxScroll);
        if (target != scrollY) {
            scrollY = target;
            requestLayout();
        }
        return true;
    }

    /**
     * Discards the data cell pool (the only path that drops cell instances); used
     * when the cell factory changes. A normal layout repopulates it.
     */
    void recreateCells() {
        contentLayer.getChildren().removeAll(cellPool);
        cellPool.clear();
        requestLayout();
    }

    void dispose() {
        vbar.valueProperty().removeListener(scrollBarValueListener);
        removeEventHandler(ScrollEvent.SCROLL, scrollHandler);
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
            requestLayout();
        }
        event.consume();
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

        RXListRowPlan plan = rowPlan;
        if (w <= 0.0 || h <= 0.0 || plan == null) {
            layoutContentLayer(0.0, 0.0);
            cachedMaxScroll = 0.0;
            adjustingScrollBar = true;
            vbar.setMax(0.0);
            vbar.setVisibleAmount(0.0);
            adjustingScrollBar = false;
            parkCellsFrom(0);
            vbar.setVisible(false);
            clearVisibleMetrics();
            return;
        }
        if (plan.itemCount() == 0) {
            layoutContentLayer(w, h);
            scrollY = 0.0;
            cachedMaxScroll = 0.0;
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

        double contentHeight = plan.contentHeight();
        double maxScroll = Math.max(0.0, contentHeight - h);
        cachedMaxScroll = maxScroll;
        scrollY = clamp(scrollY, 0.0, maxScroll);

        boolean needBar = maxScroll > 0.0;
        double barBreadth = needBar ? scrollBarBreadth() : 0.0;
        if (needBar) {
            adjustingScrollBar = true;
            vbar.setMax(maxScroll);
            vbar.setVisibleAmount(h);
            vbar.setUnitIncrement(plan.rowHeight());
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

        int first = plan.firstItemAt(scrollY);
        int last = plan.firstItemAt(scrollY + h - 1.0);
        int maxItem = plan.itemCount() - 1;
        if (last > maxItem) {
            last = maxItem;
        }
        if (last < first) {
            last = first;
        }
        fillVisibleRows(plan, first, last, contentWidth);
    }

    private void layoutContentLayer(double width, double height) {
        contentLayer.resizeRelocate(0.0, 0.0, width, height);
        contentClip.setX(0.0);
        contentClip.setY(0.0);
        contentClip.setWidth(width);
        contentClip.setHeight(height);
    }

    private void fillVisibleRows(RXListRowPlan plan, int first, int last, double contentWidth) {
        double rowHeight = snapSizeY(plan.rowHeight());
        int cellCursor = 0;
        for (int index = first; index <= last; index++) {
            RXListCell<T> cell = acquireCell(cellCursor++);
            String oldStyle = cell.getStyle();
            cell.updateIndex(index);
            cell.setVisible(true);
            applyCellState(cell, index);
            applyCssAfterCellUpdate(cell, oldStyle);
            double y = snapPositionY(plan.topOfItem(index) - scrollY);
            cell.resizeRelocate(snapPositionX(0.0), y, contentWidth, rowHeight);
        }
        parkCellsFrom(cellCursor);
        visibleFirstIndex = first;
        visibleLastIndex = last;
    }

    private void clearVisibleMetrics() {
        visibleFirstIndex = -1;
        visibleLastIndex = -1;
    }

    // ==================== Cell pool ====================

    /**
     * Returns the cell for the {@code slotIndex}-th visible row of this pass
     * (sequential assignment), growing the pool as needed.
     *
     * @param slotIndex the zero-based position in this pass's visible cell sequence
     * @return a cell to bind
     */
    private RXListCell<T> acquireCell(int slotIndex) {
        while (cellPool.size() <= slotIndex) {
            RXListCell<T> cell = createCell();
            cellPool.add(cell);
            contentLayer.getChildren().add(cell);
        }
        return cellPool.get(slotIndex);
    }

    private void parkCellsFrom(int from) {
        for (int i = from; i < cellPool.size(); i++) {
            parkCell(cellPool.get(i));
        }
    }

    private void parkCell(RXListCell<T> cell) {
        if (cell.isVisible() || cell.getIndex() != -1) {
            cell.setVisible(false);
            // updateIndex(-1) delivers an empty update so the cell clears its text,
            // graphic, :selected / focus ring — no stale visual on a parked slot.
            cell.updateIndex(-1);
        }
    }

    private RXListCell<T> createCell() {
        Callback<RXListView<T>, RXListCell<T>> factory = control.getCellFactory();
        RXListCell<T> cell = factory != null ? factory.call(control) : createDefaultCell();
        cell.updateListView(control);
        cell.setManaged(false);
        return cell;
    }

    private RXListCell<T> createDefaultCell() {
        return new RXListCell<>();
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
