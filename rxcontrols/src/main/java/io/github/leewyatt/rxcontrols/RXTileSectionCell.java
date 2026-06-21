package io.github.leewyatt.rxcontrols;

import javafx.scene.control.Cell;
import javafx.scene.control.Skin;
import javafx.scene.control.skin.CellSkinBase;

/**
 * Section-header cell for an {@link RXTileView}. The tile view recycles a small
 * pool of these across the visible section rows, calling
 * {@link #updateSection(RXTileSection)} to bind each one to the section it
 * currently represents.
 *
 * <p>Override {@link #updateItem(RXTileSection, boolean)} to render the header
 * and install the cell through
 * {@link RXTileView#sectionHeaderFactoryProperty() sectionHeaderFactory}. The
 * base implementation carries no rendering opinion — when no factory is set the
 * tile view installs a default factory that shows the section key as text. To
 * build a rich header from the section's items, slice the live list with
 * {@code getItems().subList(section.firstItemIndex(), section.endItemIndex())}.
 */
public class RXTileSectionCell extends Cell<RXTileSection> {

    /**
     * Creates an empty section-header cell.
     */
    public RXTileSectionCell() {
        getStyleClass().add("rx-tile-section-header");
    }

    /**
     * Binds this cell to the given section. Intended for the skin / viewport that
     * hosts this cell; a {@code null} section renders the cell empty.
     *
     * @param section the section to display, or {@code null} for empty
     */
    public final void updateSection(RXTileSection section) {
        updateItem(section, section == null);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The base implementation clears text and graphic when the cell is empty
     * and otherwise renders nothing — rendering of a non-empty section is the job
     * of the section-header factory (or the tile view's default key-text
     * factory).
     */
    @Override
    protected void updateItem(RXTileSection item, boolean empty) {
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
