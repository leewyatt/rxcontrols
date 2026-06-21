package io.github.leewyatt.rxcontrols;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ObservableList;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Skin;
import javafx.scene.control.skin.CellSkinBase;

/**
 * Cell for a {@link RXGridView}. One cell renders one item; the grid recycles a
 * small pool of cells across the visible rows, so a cell's item and position
 * change as the grid scrolls or relays out.
 *
 * <p>Unlike a list cell, a grid cell knows its two-dimensional position: it
 * exposes the read-only {@link #rowIndexProperty() rowIndex} and
 * {@link #columnIndexProperty() columnIndex} of the slot it currently occupies,
 * alongside the owning {@link #gridViewProperty() gridView}.
 *
 * <p>A {@code null} item is a legal value, not an empty cell: emptiness is
 * decided solely by whether the linear index falls inside the items list (see
 * {@link #updateIndex(int)}). Override {@link #updateItem(Object, boolean)} to
 * render content and install the cell through
 * {@link RXGridView#cellFactoryProperty() cellFactory}. The base implementation
 * carries no rendering opinion — when no factory is set the grid installs a
 * default factory that shows {@code item.toString()}.
 *
 * @param <T> the item type
 */
public class RXGridCell<T> extends IndexedCell<T> {

    /**
     * Creates an empty grid cell.
     */
    public RXGridCell() {
        getStyleClass().add("rx-grid-cell");
    }

    // ==================== Grid View ====================

    private final ReadOnlyObjectWrapper<RXGridView<T>> gridView =
            new ReadOnlyObjectWrapper<>(this, "gridView");

    /**
     * The grid view that owns this cell.
     *
     * @return the read-only grid-view property
     */
    public final ReadOnlyObjectProperty<RXGridView<T>> gridViewProperty() {
        return gridView.getReadOnlyProperty();
    }

    /**
     * Returns the grid view that owns this cell.
     *
     * @return the owning grid view, or {@code null} if not attached
     */
    public final RXGridView<T> getGridView() {
        return gridView.get();
    }

    /**
     * Updates the owning grid view. Intended for the skin / row that hosts this
     * cell; not for application code.
     *
     * @param gridView the owning grid view
     */
    public final void updateGridView(RXGridView<T> gridView) {
        this.gridView.set(gridView);
    }

    // ==================== Position ====================

    private final ReadOnlyIntegerWrapper rowIndex = new ReadOnlyIntegerWrapper(this, "rowIndex", -1);

    /**
     * Zero-based row of the slot this cell occupies, or {@code -1} when empty.
     *
     * @return the read-only row-index property
     */
    public final ReadOnlyIntegerProperty rowIndexProperty() {
        return rowIndex.getReadOnlyProperty();
    }

    /**
     * Returns the zero-based row of the slot this cell occupies.
     *
     * @return the row index, or {@code -1} when empty
     */
    public final int getRowIndex() {
        return rowIndex.get();
    }

    private final ReadOnlyIntegerWrapper columnIndex = new ReadOnlyIntegerWrapper(this, "columnIndex", -1);

    /**
     * Zero-based column of the slot this cell occupies, or {@code -1} when empty.
     *
     * @return the read-only column-index property
     */
    public final ReadOnlyIntegerProperty columnIndexProperty() {
        return columnIndex.getReadOnlyProperty();
    }

    /**
     * Returns the zero-based column of the slot this cell occupies.
     *
     * @return the column index, or {@code -1} when empty
     */
    public final int getColumnIndex() {
        return columnIndex.get();
    }

    /**
     * Updates the two-dimensional position of this cell. Intended for the skin /
     * row that hosts this cell; call it before {@link #updateIndex(int)} so the
     * position is current when {@link #updateItem(Object, boolean)} runs.
     *
     * @param row    the row index
     * @param column the column index
     */
    public final void updateGridPosition(int row, int column) {
        rowIndex.set(row);
        columnIndex.set(column);
    }

    // ==================== Item resolution ====================

    /**
     * {@inheritDoc}
     *
     * <p>Resolves the cell's item from the owning grid's items list at index
     * {@code i} and pushes it through {@link #updateItem(Object, boolean)}.
     * Emptiness is decided by the index alone — {@code i < 0} or out of the
     * list's bounds — so a {@code null} stored at a valid index is delivered as a
     * non-empty {@code null} item.
     */
    @Override
    public void updateIndex(int i) {
        super.updateIndex(i);
        RXGridView<T> gv = getGridView();
        ObservableList<T> list = (gv == null) ? null : gv.getItems();
        boolean empty = (list == null) || i < 0 || i >= list.size();
        T item = empty ? null : list.get(i);
        updateItem(item, empty);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The base implementation clears text and graphic when the cell is empty
     * and otherwise renders nothing — rendering of non-empty items is the job of
     * the cell factory (or the grid's default {@code toString()} factory).
     */
    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setText(null);
            setGraphic(null);
        }
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new CellSkinBase<>(this);
    }
}
