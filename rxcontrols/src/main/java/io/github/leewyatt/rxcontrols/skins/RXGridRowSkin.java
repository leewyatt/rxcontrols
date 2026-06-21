package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXGridCell;
import io.github.leewyatt.rxcontrols.RXGridJustify;
import io.github.leewyatt.rxcontrols.RXGridView;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.skin.CellSkinBase;
import javafx.util.Callback;

import java.util.List;

/**
 * Skin for {@link RXGridRow}. It owns the row's {@link RXGridCell} children,
 * reusing them across reconfigurations, and lays them out in fixed columns so a
 * given column aligns vertically across every row (including a short final row).
 *
 * <p>This skin registers <em>no</em> external listeners and reads the grid's
 * state live on every pass. {@code VirtualFlow} recycles and drops rows without
 * disposing their skins, so any listener attached here to the shared grid (or
 * its items) would accumulate across cell rebuilds and leak.
 *
 * @param <T> the item type
 */
class RXGridRowSkin<T> extends CellSkinBase<RXGridRow<T>> {

    RXGridRowSkin(RXGridRow<T> row) {
        super(row);
        // Drop the LabeledText the cell skin installs by default; this row renders
        // only its grid cells.
        getChildren().clear();
        updateCells();
    }

    private RXGridView<T> gridView() {
        return getSkinnable().getGridView();
    }

    /**
     * Reconciles this row's cells with its current row index and the grid's
     * column count: creates or removes cells so there is exactly one per occupied
     * column, then points each at its item. Cells are reused, and each is
     * configured with a single {@code updateIndex} call (no throwaway -1 flush).
     */
    void updateCells() {
        RXGridView<T> grid = gridView();
        int rowIndex = getSkinnable().getIndex();
        List<Node> children = getChildren();
        if (grid == null || rowIndex < 0) {
            children.clear();
            return;
        }

        int columns = Math.max(1, grid.getActualColumnCount());
        ObservableList<T> items = grid.getItems();
        int itemCount = items == null ? 0 : items.size();
        int start = rowIndex * columns;
        int cellsInRow = Math.max(0, Math.min(columns, itemCount - start));

        while (children.size() < cellsInRow) {
            children.add(createCell());
        }
        while (children.size() > cellsInRow) {
            children.remove(children.size() - 1);
        }

        for (int column = 0; column < cellsInRow; column++) {
            @SuppressWarnings("unchecked")
            RXGridCell<T> cell = (RXGridCell<T>) children.get(column);
            cell.updateGridPosition(rowIndex, column);
            cell.updateIndex(start + column);
        }
    }

    private RXGridCell<T> createCell() {
        RXGridView<T> grid = gridView();
        Callback<RXGridView<T>, RXGridCell<T>> factory = grid.getCellFactory();
        RXGridCell<T> cell = factory != null ? factory.call(grid) : createDefaultCell();
        cell.updateGridView(grid);
        return cell;
    }

    private RXGridCell<T> createDefaultCell() {
        return new RXGridCell<T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty) {
                    setText(item == null ? "" : item.toString());
                }
            }
        };
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        RXGridView<T> grid = gridView();
        List<Node> children = getChildren();
        int cellsInRow = children.size();
        if (grid == null || cellsInRow == 0) {
            return;
        }

        int columns = Math.max(1, grid.getActualColumnCount());
        double gap = Math.max(0.0, grid.getHgap());
        double cellHeight = grid.getCellHeight();
        double cellWidth;
        double startX;
        if (grid.isStretchCells()) {
            cellWidth = Math.max(0.0, (w - (columns - 1) * gap) / columns);
            startX = 0.0;
        } else {
            cellWidth = grid.getCellWidth();
            double used = columns * cellWidth + (columns - 1) * gap;
            double slack = Math.max(0.0, w - used);
            startX = switch (justifyOrDefault(grid.getItemsJustify())) {
                case CENTER -> slack / 2.0;
                case END -> slack;
                case START -> 0.0;
            };
        }

        // Place each cell at its fixed column position so columns line up across
        // rows; a short final row simply leaves the trailing columns empty.
        double cellX = x + startX;
        for (int column = 0; column < cellsInRow; column++) {
            children.get(column).resizeRelocate(cellX, y, cellWidth, cellHeight);
            cellX += cellWidth + gap;
        }
    }

    private static RXGridJustify justifyOrDefault(RXGridJustify justify) {
        return justify == null ? RXGridJustify.START : justify;
    }

    // The row always stretches to the viewport width and never reports a breadth
    // wider than it, so the grid never grows a horizontal scroll bar. Its height
    // is the fixed row slot (cell height plus the inter-row gap).

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return 0.0;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return 0.0;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        RXGridView<T> grid = gridView();
        if (grid == null) {
            return super.computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
        }
        return grid.getCellHeight() + Math.max(0.0, grid.getVgap());
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }
}
