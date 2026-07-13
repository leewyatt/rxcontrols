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
 * Cell for an {@link RXMasonryView}. One cell renders one item; the masonry view
 * recycles a small pool of cells across the visible region, so a cell's item and
 * position change as the view scrolls or relays out.
 *
 * <p>A masonry cell knows its grid coordinate: it exposes the read-only
 * {@link #columnIndexProperty() columnIndex} of the (leftmost) column it occupies
 * and its {@link #columnSpanProperty() columnSpan}, alongside the owning
 * {@link #masonryViewProperty() masonryView}. There is no row index — masonry
 * columns advance independently, so vertical position is geometric, not a row.
 *
 * <p>A {@code null} item is a legal value, not an empty cell: emptiness is decided
 * solely by whether the linear index falls inside the items list (see
 * {@link #updateIndex(int)}). Override {@link #updateItem(Object, boolean)} to
 * render content and install the cell through
 * {@link RXMasonryView#cellFactoryProperty() cellFactory}. The base implementation
 * carries no rendering opinion — when no factory is set the masonry view installs a
 * default factory that shows {@code item.toString()}.
 *
 * @param <T> the item type
 */
public class RXMasonryCell<T> extends IndexedCell<T> {

    // The skin drives the keyboard focus ring through this pseudo-class. It is the
    // same "focused" pseudo-class Cell exposes; because masonry cells are never
    // focus-traversable (see the constructor) Cell's own Node-focus listener never
    // fires, so this manual toggle is the sole writer and is not overridden.
    private static final PseudoClass FOCUSED_PSEUDO_CLASS = PseudoClass.getPseudoClass("focused");

    /**
     * Creates an empty masonry cell.
     */
    public RXMasonryCell() {
        getStyleClass().add("rx-masonry-cell");
        setAccessibleRole(AccessibleRole.LIST_ITEM);
        // Cells are not Tab stops — the masonry view is the single focus owner — so
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

    // ==================== Masonry View ====================

    private final ReadOnlyObjectWrapper<RXMasonryView<T>> masonryView =
            new ReadOnlyObjectWrapper<>(this, "masonryView");

    /**
     * The masonry view that owns this cell.
     *
     * @return the read-only masonry-view property
     */
    public final ReadOnlyObjectProperty<RXMasonryView<T>> masonryViewProperty() {
        return masonryView.getReadOnlyProperty();
    }

    /**
     * Returns the masonry view that owns this cell.
     *
     * @return the owning masonry view, or {@code null} if not attached
     */
    public final RXMasonryView<T> getMasonryView() {
        return masonryView.get();
    }

    /**
     * Updates the owning masonry view. Intended for the skin / viewport that hosts
     * this cell; not for application code. Called once when the cell is created.
     *
     * @param masonryView the owning masonry view
     */
    public final void updateMasonryView(RXMasonryView<T> masonryView) {
        this.masonryView.set(masonryView);
    }

    // ==================== Position ====================

    private final ReadOnlyIntegerWrapper columnIndex = new ReadOnlyIntegerWrapper(this, "columnIndex", -1);

    /**
     * Zero-based leftmost column of the slot this cell occupies, or {@code -1} when
     * empty.
     *
     * @return the read-only column-index property
     */
    public final ReadOnlyIntegerProperty columnIndexProperty() {
        return columnIndex.getReadOnlyProperty();
    }

    /**
     * Returns the zero-based leftmost column of the slot this cell occupies.
     *
     * @return the column index, or {@code -1} when empty
     */
    public final int getColumnIndex() {
        return columnIndex.get();
    }

    private final ReadOnlyIntegerWrapper columnSpan = new ReadOnlyIntegerWrapper(this, "columnSpan", 1);

    /**
     * Number of columns the slot this cell occupies spans. {@code 1} for a normal
     * cell.
     *
     * @return the read-only column-span property
     */
    public final ReadOnlyIntegerProperty columnSpanProperty() {
        return columnSpan.getReadOnlyProperty();
    }

    /**
     * Returns the number of columns this cell spans.
     *
     * @return the column span, at least {@code 1}
     */
    public final int getColumnSpan() {
        return columnSpan.get();
    }

    /**
     * Updates the grid coordinate of this cell. Intended for the skin / viewport
     * that hosts this cell; call it before {@link #updateIndex(int)} so the position
     * is current when {@link #updateItem(Object, boolean)} runs.
     *
     * @param column the leftmost column index
     * @param span   the number of columns spanned
     */
    public final void updateMasonryPosition(int column, int span) {
        columnIndex.set(column);
        columnSpan.set(span);
    }

    /**
     * Sets the keyboard focus ring on this cell. Intended for the skin / viewport;
     * the cell carries the focus ring for the item it currently renders, re-applied
     * whenever the cell is recycled to another index.
     *
     * @param focused whether this cell holds the keyboard focus
     */
    public final void updateMasonryFocus(boolean focused) {
        pseudoClassStateChanged(FOCUSED_PSEUDO_CLASS, focused);
    }

    // ==================== Item resolution ====================

    /**
     * {@inheritDoc}
     *
     * <p>Resolves the cell's item from the owning masonry view's items list at index
     * {@code i} and pushes it through {@link #updateItem(Object, boolean)}.
     * Emptiness is decided by the index alone — {@code i < 0} or out of the list's
     * bounds — so a {@code null} stored at a valid index is delivered as a non-empty
     * {@code null} item.
     */
    @Override
    public void updateIndex(int i) {
        super.updateIndex(i);
        RXMasonryView<T> view = getMasonryView();
        ObservableList<T> list = (view == null) ? null : view.getItems();
        boolean empty = (list == null) || i < 0 || i >= list.size();
        T item = empty ? null : list.get(i);
        updateItem(item, empty);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The base implementation clears text and graphic when the cell is empty and
     * otherwise renders nothing — rendering of non-empty items is the job of the
     * cell factory (or the masonry view's default {@code toString()} factory).
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
            updateMasonryFocus(false);
        }
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new CellSkinBase<>(this);
    }
}
