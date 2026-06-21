package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXGridScrollAlignment;
import io.github.leewyatt.rxcontrols.RXGridView;
import io.github.leewyatt.rxcontrols.RXGridVisibleRange;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.layout.StackPane;

/**
 * Skin for {@link RXGridView}. It virtualizes by row through a JavaFX
 * {@link VirtualFlow}: items are wrapped into {@link RXGridRow rows}, only the
 * visible rows hold live {@link io.github.leewyatt.rxcontrols.RXGridCell cells},
 * and the resolved column count, row count and visible range are published back
 * to the control after each layout pass.
 *
 * <p>The column count is derived from {@code cellWidth}, the available content
 * width and the <em>measured</em> vertical scroll-bar width (never a hard-coded
 * guess), or taken from a forced {@code columnCount}. The slot height fed to the
 * flow is {@code cellHeight + vgap}, which keeps the scroll bar exact.
 *
 * @param <T> the item type
 */
public class RXGridViewSkin<T> extends RXSkinBase<RXGridView<T>> {

    private static final PseudoClass EMPTY_PSEUDO_CLASS = PseudoClass.getPseudoClass("empty");
    private static final int MAX_RESOLVED_COLUMNS = 4096;
    private static final int DEFAULT_PREF_COLUMNS = 3;
    private static final int DEFAULT_VISIBLE_ROWS = 4;

    private final GridFlow flow;
    private final StackPane placeholderRegion;

    private final ListChangeListener<T> itemsListener = change -> onItemsContentChanged();
    private ObservableList<T> observedItems;

    private int lastResolvedColumns = -1;

    /**
     * Creates the skin for the given grid view.
     *
     * @param control the grid view
     */
    public RXGridViewSkin(RXGridView<T> control) {
        super(control);

        flow = new GridFlow();
        flow.setVertical(true);
        flow.setPannable(false);
        flow.setFocusTraversable(control.isFocusTraversable());
        flow.setCellFactory(ignored -> createRow());
        getChildren().add(flow);

        placeholderRegion = new StackPane();
        placeholderRegion.getStyleClass().add("placeholder");
        placeholderRegion.setVisible(false);
        getChildren().add(placeholderRegion);

        attachItems(control.getItems());
        updateFixedCellSize();
        updatePlaceholder();
        registerListeners(control);
    }

    private void registerListeners(RXGridView<T> control) {
        disposer.registerListener(control.itemsProperty(), this::onItemsListSwapped);
        disposer.registerListener(control.cellFactoryProperty(), this::onCellFactoryChanged);
        disposer.registerListener(control.cellWidthProperty(), this::onColumnsAffectingChange);
        disposer.registerListener(control.hgapProperty(), this::onColumnsAffectingChange);
        disposer.registerListener(control.columnCountProperty(), this::onColumnsAffectingChange);
        disposer.registerListener(control.maxColumnsProperty(), this::onColumnsAffectingChange);
        disposer.registerListener(control.cellHeightProperty(), this::onCellSizeChange);
        disposer.registerListener(control.vgapProperty(), this::onCellSizeChange);
        disposer.registerListener(control.maxCellWidthProperty(), this::onCellLayoutChange);
        disposer.registerListener(control.itemsJustifyProperty(), this::onCellLayoutChange);
        disposer.registerListener(control.placeholderProperty(), this::onPlaceholderChanged);

        ScrollBar vbar = flow.verticalScrollBar();
        // The measured scroll-bar width feeds the column count; recompute when it
        // appears/disappears or resizes so the count converges within 1-2 passes.
        disposer.registerListener(vbar.visibleProperty(), this::requestParentLayoutPass);
        disposer.registerListener(vbar.widthProperty(), this::requestParentLayoutPass);
        // A scroll is itself a layout pass, which republishes the visible range.
        disposer.registerListener(flow.positionProperty(), this::requestParentLayoutPass);
    }

    private RXGridRow<T> createRow() {
        RXGridRow<T> row = new RXGridRow<>();
        row.updateGridView(getSkinnable());
        return row;
    }

    private void requestParentLayoutPass() {
        getSkinnable().requestLayout();
    }

    // ==================== Items ====================

    private void onItemsListSwapped() {
        attachItems(getSkinnable().getItems());
        updateColumns();
        flow.rebuildCells();
        updatePlaceholder();
        getSkinnable().requestLayout();
    }

    private void onItemsContentChanged() {
        updateColumns();
        flow.rebuildCells();
        updatePlaceholder();
        getSkinnable().requestLayout();
    }

    private void attachItems(ObservableList<T> items) {
        detachItems();
        observedItems = items;
        if (items != null) {
            items.addListener(itemsListener);
        }
    }

    private void detachItems() {
        if (observedItems != null) {
            observedItems.removeListener(itemsListener);
            observedItems = null;
        }
    }

    // ==================== Refresh paths ====================

    private void onCellFactoryChanged() {
        flow.recreateCells();
        getSkinnable().requestLayout();
    }

    private void onColumnsAffectingChange() {
        // updateColumns rebuilds when the column or row count changes; otherwise
        // the geometry changed within the same grid, so re-lay-out the live rows.
        if (!updateColumns()) {
            requestRowLayout();
        }
        getSkinnable().requestLayout();
    }

    private void onCellSizeChange() {
        updateColumns();
        requestRowLayout();
        getSkinnable().requestLayout();
    }

    private void onCellLayoutChange() {
        requestRowLayout();
        getSkinnable().requestLayout();
    }

    private void onPlaceholderChanged() {
        updatePlaceholder();
        getSkinnable().requestLayout();
    }

    private void requestRowLayout() {
        RXGridRow<T> first = flow.getFirstVisibleCell();
        RXGridRow<T> last = flow.getLastVisibleCell();
        if (first == null || last == null) {
            return;
        }
        for (int i = first.getIndex(); i <= last.getIndex(); i++) {
            RXGridRow<T> row = flow.getVisibleCell(i);
            if (row != null) {
                row.requestLayout();
            }
        }
    }

    // ==================== Column derivation ====================

    /**
     * Recomputes the column count, row count and slot height and pushes them to
     * the control and flow. Returns {@code true} when the structure (cell count)
     * or column count changed and the cells were rebuilt, so callers can decide
     * whether a lighter in-row relayout still needs to be requested.
     */
    private boolean updateColumns() {
        RXGridView<T> grid = getSkinnable();
        double contentWidth = grid.getWidth() - snappedLeftInset() - snappedRightInset()
                - flow.verticalScrollBarWidth();
        int columns = computeColumns(contentWidth);
        ObservableList<T> items = grid.getItems();
        int itemCount = items == null ? 0 : items.size();
        int rows = itemCount <= 0 ? 0 : (int) Math.ceil((double) itemCount / columns);

        grid.setActualColumnCount(columns);
        grid.setRowCount(rows);
        updateFixedCellSize();

        boolean structureChanged = flow.getCellCount() != rows;
        boolean columnsChanged = columns != lastResolvedColumns;
        lastResolvedColumns = columns;
        if (structureChanged || columnsChanged) {
            if (structureChanged) {
                flow.setCellCount(rows);
            }
            flow.rebuildCells();
            return true;
        }
        return false;
    }

    private int computeColumns(double contentWidth) {
        RXGridView<T> grid = getSkinnable();
        int columns;
        int forced = grid.getColumnCount();
        if (forced >= 1) {
            columns = forced;
        } else {
            double track = snapSizeX(grid.getCellWidth());
            double gap = snapSpaceX(Math.max(0.0, grid.getHgap()));
            if (contentWidth <= 0.0 || track <= 0.0) {
                columns = 1;
            } else {
                columns = (int) Math.floor((contentWidth + gap) / (track + gap));
            }
        }
        if (columns < 1) {
            columns = 1;
        }
        int max = grid.getMaxColumns();
        if (max > 0 && columns > max) {
            columns = max;
        }
        if (columns > MAX_RESOLVED_COLUMNS) {
            columns = MAX_RESOLVED_COLUMNS;
        }
        return columns;
    }

    private void updateFixedCellSize() {
        RXGridView<T> grid = getSkinnable();
        double slot = grid.getCellHeight() + Math.max(0.0, grid.getVgap());
        if (flow.getFixedCellSize() != slot) {
            flow.setFixedCellSize(slot);
        }
    }

    // ==================== Visible range ====================

    private void updateVisibleRange() {
        RXGridView<T> grid = getSkinnable();
        RXGridRow<T> first = flow.getFirstVisibleCell();
        RXGridRow<T> last = flow.getLastVisibleCell();
        int columns = grid.getActualColumnCount();
        ObservableList<T> items = grid.getItems();
        int itemCount = items == null ? 0 : items.size();
        if (first == null || last == null || columns <= 0 || itemCount == 0
                || first.getIndex() < 0 || last.getIndex() < 0) {
            grid.setVisibleRange(RXGridVisibleRange.EMPTY);
            return;
        }
        int firstRow = first.getIndex();
        int lastRow = last.getIndex();
        int firstIndex = firstRow * columns;
        int lastIndex = Math.min(lastRow * columns + columns - 1, itemCount - 1);
        if (firstIndex > lastIndex) {
            // The first visible row can momentarily point past a freshly shrunk
            // list before the flow re-clamps its position; publish empty rather
            // than a malformed (first > last) range.
            grid.setVisibleRange(RXGridVisibleRange.EMPTY);
            return;
        }
        grid.setVisibleRange(new RXGridVisibleRange(firstIndex, lastIndex, firstRow, lastRow, columns));
    }

    // ==================== Placeholder / :empty ====================

    private void updatePlaceholder() {
        RXGridView<T> grid = getSkinnable();
        ObservableList<T> items = grid.getItems();
        boolean empty = items == null || items.isEmpty();
        grid.pseudoClassStateChanged(EMPTY_PSEUDO_CLASS, empty);

        Node placeholder = grid.getPlaceholder();
        boolean showPlaceholder = empty && placeholder != null;
        if (showPlaceholder) {
            placeholderRegion.getChildren().setAll(placeholder);
        } else {
            placeholderRegion.getChildren().clear();
        }
        placeholderRegion.setVisible(showPlaceholder);
        flow.setVisible(!showPlaceholder);
    }

    // ==================== Scrolling ====================

    private void consumePendingScroll() {
        RXGridView<T> grid = getSkinnable();
        if (!grid.hasPendingScroll()) {
            return;
        }
        ObservableList<T> items = grid.getItems();
        int itemCount = items == null ? 0 : items.size();
        if (itemCount == 0) {
            grid.clearPendingScroll();
            return;
        }
        int columns = Math.max(1, grid.getActualColumnCount());
        int index = Math.max(0, Math.min(grid.getPendingScrollIndex(), itemCount - 1));
        int row = index / columns;
        if (grid.getPendingScrollAlignment() == RXGridScrollAlignment.NEAREST) {
            flow.scrollTo(row);
        } else {
            // START, and the interim CENTER / END which currently behave as START.
            flow.scrollToTop(row);
        }
        grid.clearPendingScroll();
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        updateColumns();
        flow.resizeRelocate(x, y, w, h);
        placeholderRegion.resizeRelocate(x, y, w, h);
        consumePendingScroll();
        // Force the flow to realize cells now so the scroll request and the
        // published visible range reflect this pass, not the previous one.
        flow.layout();
        updateVisibleRange();
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        RXGridView<T> grid = getSkinnable();
        double cellWidth = grid.getCellWidth();
        double gap = Math.max(0.0, grid.getHgap());
        return leftInset + DEFAULT_PREF_COLUMNS * cellWidth
                + (DEFAULT_PREF_COLUMNS - 1) * gap + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        RXGridView<T> grid = getSkinnable();
        double slot = grid.getCellHeight() + Math.max(0.0, grid.getVgap());
        return topInset + DEFAULT_VISIBLE_ROWS * slot + bottomInset;
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + getSkinnable().getCellWidth() + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    @Override
    protected void disposeSkin() {
        flow.setCellFactory(null);
        detachItems();
        placeholderRegion.getChildren().clear();
    }

    // ==================== VirtualFlow subclass ====================

    /**
     * Re-exposes the {@link VirtualFlow} refresh hooks (protected since JFX 12)
     * to the enclosing skin and reads the real vertical scroll-bar width,
     * replacing the hard-coded {@code 18} guess used by ControlsFX.
     */
    private final class GridFlow extends VirtualFlow<RXGridRow<T>> {

        @Override
        protected void recreateCells() {
            super.recreateCells();
        }

        @Override
        protected void rebuildCells() {
            super.rebuildCells();
        }

        ScrollBar verticalScrollBar() {
            return getVbar();
        }

        double verticalScrollBarWidth() {
            ScrollBar vbar = getVbar();
            return vbar != null && vbar.isVisible() ? vbar.getWidth() : 0.0;
        }
    }
}
