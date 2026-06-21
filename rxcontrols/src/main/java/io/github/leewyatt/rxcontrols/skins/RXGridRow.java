package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXGridView;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Skin;

/**
 * A single row of a {@link RXGridView}, and the unit of virtualization: the
 * grid's {@code VirtualFlow} realizes one {@code RXGridRow} per visible row and
 * recycles it as the grid scrolls. Each row owns and reuses the cells for its
 * columns; the real item data lives on those cells, not on the row, so this
 * cell's own item is always {@code null} and its {@code T} only satisfies the
 * {@code VirtualFlow<I extends IndexedCell>} bound. The row itself never carries
 * text or a graphic — only its child cells render — which is what lets its skin
 * drop the default {@code LabeledText} and lay out cells directly.
 *
 * @param <T> the item type
 */
class RXGridRow<T> extends IndexedCell<T> {

    private RXGridView<T> gridView;

    RXGridRow() {
        getStyleClass().add("grid-row");
    }

    /**
     * Returns the grid view this row belongs to.
     *
     * @return the owning grid view, or {@code null} before it is attached
     */
    RXGridView<T> getGridView() {
        return gridView;
    }

    /**
     * Sets the grid view this row belongs to. Called by the grid skin when the
     * row is created.
     *
     * @param gridView the owning grid view
     */
    void updateGridView(RXGridView<T> gridView) {
        this.gridView = gridView;
    }

    @Override
    public void updateIndex(int i) {
        super.updateIndex(i);
        // VirtualFlow calls updateIndex on recycle even when the value is the
        // same; the row must (re)configure its cells for the (possibly new) row.
        if (getSkin() instanceof RXGridRowSkin) {
            ((RXGridRowSkin<?>) getSkin()).updateCells();
        }
        // A row must report non-empty while it maps a valid index, otherwise the
        // flow discards it (and wheel scrolling breaks). Its item stays null.
        updateItem(null, getIndex() == -1);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXGridRowSkin<>(this);
    }
}
