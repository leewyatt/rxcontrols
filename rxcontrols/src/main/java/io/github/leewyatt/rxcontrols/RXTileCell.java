package io.github.leewyatt.rxcontrols;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Skin;
import javafx.scene.control.skin.CellSkinBase;

/**
 * Cell for an {@link RXTileView}. One cell renders one item; the tile view
 * recycles a small pool of cells across the visible rows, so a cell's item and
 * position change as the view scrolls or relays out.
 *
 * <p>Like a grid cell, a tile cell knows its two-dimensional position: it
 * exposes the read-only {@link #rowIndexProperty() rowIndex} and
 * {@link #columnIndexProperty() columnIndex} of the slot it currently occupies,
 * alongside the owning {@link #tileViewProperty() tileView}.
 *
 * <p>A {@code null} item is a legal value, not an empty cell: emptiness is
 * decided solely by whether the linear index falls inside the items list (see
 * {@link #updateIndex(int)}). Override {@link #updateItem(Object, boolean)} to
 * render content and install the cell through
 * {@link RXTileView#cellFactoryProperty() cellFactory}. The base implementation
 * carries no rendering opinion — when no factory is set the tile view installs a
 * default factory that shows {@code item.toString()}.
 *
 * @param <T> the item type
 */
public class RXTileCell<T> extends IndexedCell<T> {

    // The skin drives the keyboard focus ring through this pseudo-class. It is the
    // same "focused" pseudo-class Cell exposes; because tile cells are never
    // focus-traversable (see the constructor) Cell's own Node-focus listener never
    // fires, so this manual toggle is the sole writer and is not overridden.
    private static final PseudoClass FOCUSED_PSEUDO_CLASS = PseudoClass.getPseudoClass("focused");

    /**
     * Creates an empty tile cell.
     */
    public RXTileCell() {
        getStyleClass().add("rx-tile-cell");
        setAccessibleRole(AccessibleRole.LIST_ITEM);
        // Cells are not Tab stops — the tile view is the single focus owner — so
        // they never receive Node focus and the focus ring above stays under skin
        // control.
        setFocusTraversable(false);
    }

    // ==================== Accessibility ====================

    /**
     * Reports this cell's index and selection state to assistive technologies
     * (mirroring {@code ListCell}); other attributes defer to the superclass. Only the
     * realized (visible) cells are exposed — the self-built viewport keeps no off-screen
     * accessibility peers, so screen readers see the visible window rather than the full
     * item list.
     *
     * @param attribute  the requested accessible attribute
     * @param parameters optional attribute parameters
     * @return the attribute value
     */
    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        return switch (attribute) {
            case INDEX -> getIndex();
            case SELECTED -> isSelected();
            default -> super.queryAccessibleAttribute(attribute, parameters);
        };
    }

    // ==================== Tile View ====================

    private final ReadOnlyObjectWrapper<RXTileView<T>> tileView =
            new ReadOnlyObjectWrapper<>(this, "tileView");

    /**
     * The tile view that owns this cell.
     *
     * @return the read-only tile-view property
     */
    public final ReadOnlyObjectProperty<RXTileView<T>> tileViewProperty() {
        return tileView.getReadOnlyProperty();
    }

    /**
     * Returns the tile view that owns this cell.
     *
     * @return the owning tile view, or {@code null} if not attached
     */
    public final RXTileView<T> getTileView() {
        return tileView.get();
    }

    /**
     * Updates the owning tile view. Intended for the skin / viewport that hosts
     * this cell; not for application code. Called once when the cell is created.
     *
     * @param tileView the owning tile view
     */
    public final void updateTileView(RXTileView<T> tileView) {
        this.tileView.set(tileView);
    }

    // ==================== Position ====================

    private final ReadOnlyIntegerWrapper rowIndex = new ReadOnlyIntegerWrapper(this, "rowIndex", -1);

    /**
     * Zero-based data-row index of the slot this cell occupies — section-header
     * rows are not counted, so in a grouped view this is the running data-row
     * index across sections, not the visual row. {@code -1} when empty.
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
     * viewport that hosts this cell; call it before {@link #updateIndex(int)} so
     * the position is current when {@link #updateItem(Object, boolean)} runs.
     *
     * @param row    the row index
     * @param column the column index
     */
    public final void updateGridPosition(int row, int column) {
        rowIndex.set(row);
        columnIndex.set(column);
    }

    /**
     * Sets the keyboard focus ring on this cell. Intended for the skin / viewport;
     * the cell carries the focus ring for the item it currently renders, re-applied
     * whenever the cell is recycled to another index.
     *
     * @param focused whether this cell holds the keyboard focus
     */
    public final void updateTileFocus(boolean focused) {
        pseudoClassStateChanged(FOCUSED_PSEUDO_CLASS, focused);
    }

    // ==================== Item resolution ====================

    /**
     * {@inheritDoc}
     *
     * <p>Resolves the cell's item from the owning tile view's items list at index
     * {@code i} and pushes it through {@link #updateItem(Object, boolean)}.
     * Emptiness is decided by the index alone — {@code i < 0} or out of the
     * list's bounds — so a {@code null} stored at a valid index is delivered as a
     * non-empty {@code null} item.
     */
    @Override
    public void updateIndex(int i) {
        super.updateIndex(i);
        RXTileView<T> view = getTileView();
        ObservableList<T> list = (view == null) ? null : view.getItems();
        boolean empty = (list == null) || i < 0 || i >= list.size();
        T item = empty ? null : list.get(i);
        updateItem(item, empty);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The base implementation clears text and graphic when the cell is empty
     * and otherwise renders nothing — rendering of non-empty items is the job of
     * the cell factory (or the tile view's default {@code toString()} factory).
     */
    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            setText(null);
            setGraphic(null);
            // Reset a recycled / parked slot's framework state (cell-reuse
            // discipline): the viewport re-applies real :selected / focus to a
            // re-bound visible cell right after updateIndex.
            updateSelected(false);
            updateTileFocus(false);
        }
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new CellSkinBase<>(this);
    }
}
