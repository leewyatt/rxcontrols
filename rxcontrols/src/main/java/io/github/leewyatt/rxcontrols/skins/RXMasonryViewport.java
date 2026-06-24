package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXMasonryCell;
import io.github.leewyatt.rxcontrols.RXMasonryView;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import io.github.leewyatt.rxcontrols.skins.RXMasonryPlacement.Geometry;
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
 * a no-op. Reorder glide animation is added in a later phase.</p>
 *
 * @param <T> the item type
 */
final class RXMasonryViewport<T> extends Region {

    // Below this px difference the bar value already matches the scroll offset, so a
    // programmatic setValue would be a redundant no-op (and a feedback risk).
    private static final double SCROLL_BAR_SYNC_EPSILON = 1.0e-4;

    private final RXMasonryView<T> control;
    private final ScrollBar vbar = new ScrollBar();
    private final Pane contentLayer = new Pane();
    private final Rectangle viewportClip = new Rectangle();
    private final Rectangle contentClip = new Rectangle();

    private final List<RXMasonryCell<T>> cellPool = new ArrayList<>();

    // Built by the skin each pass and shared so its scroll-bar / column decision and
    // this viewport's geometry use the exact same placement.
    private RXMasonryPlacement placement;

    // The skin's internal focus model; selection comes from the control. Both feed the
    // per-cell :selected / focus-ring state, re-applied on every (re)bind.
    private RXIndexedFocusModel<T> focusModel;

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
        double contentWidth = currentContentWidth();
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
        int[] visible = current.visibleItems(scrollY, viewportHeight);
        int cursor = 0;
        int anchorCandidate = -1;
        double anchorCandidateTop = Double.POSITIVE_INFINITY;
        for (int itemIndex : visible) {
            Geometry geometry = current.geometryOf(itemIndex);
            if (geometry == null) {
                continue;
            }
            RXMasonryCell<T> cell = acquireCell(cursor++);
            String oldStyle = cell.getStyle();
            cell.updateMasonryPosition(current.startColumnOf(itemIndex), current.spanOf(itemIndex));
            cell.updateIndex(itemIndex);
            cell.setVisible(true);
            applyCellState(cell, itemIndex);
            applyCssAfterCellUpdate(cell, oldStyle);
            cell.resizeRelocate(snapPositionX(geometry.x()), snapPositionY(geometry.y() - scrollY),
                    snapSizeX(geometry.width()), snapSizeY(geometry.height()));
            if (geometry.y() < anchorCandidateTop) {
                anchorCandidateTop = geometry.y();
                anchorCandidate = itemIndex;
            }
        }
        parkCellsFrom(cursor);

        visibleFirstIndex = visible.length == 0 ? -1 : visible[0];
        visibleLastIndex = visible.length == 0 ? -1 : visible[visible.length - 1];
        anchorIndex = anchorCandidate;
        anchorOffset = anchorCandidate >= 0 ? anchorCandidateTop - scrollY : 0.0;
    }

    private double currentContentWidth() {
        double barBreadth = cachedMaxScroll > 0.0 ? scrollBarBreadth() : 0.0;
        return Math.max(0.0, getWidth() - barBreadth);
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

    private void parkCellsFrom(int from) {
        for (int i = from; i < cellPool.size(); i++) {
            parkCell(cellPool.get(i));
        }
    }

    private void parkCell(RXMasonryCell<T> cell) {
        if (cell.isVisible() || cell.getIndex() != -1) {
            cell.setVisible(false);
            cell.updateMasonryFocus(false);
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
