package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXKanbanCardCell;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.skin.CellSkinBase;

/**
 * Skin for {@link RXKanbanCardCell}: the standard {@link CellSkinBase} (Labeled
 * text + graphic rendering) plus an embedded {@link RippleDecoration} placed
 * below the card content; it supplies the pointer-press ripple trigger and
 * clears the ripple when the cell is recycled.
 *
 * <p>One deliberate deviation from plain {@code LabeledSkinBase} layout: a
 * <em>resizable</em> graphic under {@link ContentDisplay#GRAPHIC_ONLY} is
 * resized to fill the card's content area (min/max respected, label padding
 * honored in both layout and measurement), so rich full-card content needs no
 * manual width binding; pref-locked icons and non-resizable shapes are
 * unaffected.
 *
 * @param <T> the card type
 */
public class RXKanbanCardCellSkin<T> extends RippleCellSkinBase<RXKanbanCardCell<T>> {

    /**
     * Creates the skin and wires the ripple.
     *
     * @param cell the cell this skin is attached to
     */
    public RXKanbanCardCellSkin(RXKanbanCardCell<T> cell) {
        super(cell, cell.rippleEnabledProperty(), cell.stateOverlayEnabledProperty(),
                cell.rippleFillProperty(), cell::getRippleOpacity);
    }
}
