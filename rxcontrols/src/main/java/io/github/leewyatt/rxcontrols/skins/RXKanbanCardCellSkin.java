package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXKanbanCardCell;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.skin.CellSkinBase;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Skin for {@link RXKanbanCardCell}: the standard {@link CellSkinBase} plus an
 * embedded {@link RippleDecoration} placed below the card content. The cell owns
 * its content holder (set as the cell graphic); this skin places the ripple layer
 * behind it, stretches the holder to fill the card, supplies the pointer-press
 * ripple trigger and clears the ripple when the cell is recycled.
 *
 * @param <T> the card type
 */
public class RXKanbanCardCellSkin<T> extends CellSkinBase<RXKanbanCardCell<T>> {

    private final SkinDisposer disposer = new SkinDisposer();
    private final RippleDecoration ripple;

    /**
     * Creates the skin and wires the ripple.
     *
     * @param cell the cell this skin is attached to
     */
    public RXKanbanCardCellSkin(RXKanbanCardCell<T> cell) {
        super(cell);
        ripple = new RippleDecoration(cell, cell.rippleEnabledProperty(),
                cell.stateOverlayEnabledProperty(), cell.rippleFillProperty(),
                cell::getRippleOpacity, null, null);

        disposer.registerEventHandler(cell, MouseEvent.MOUSE_PRESSED, this::onPressed);
        disposer.registerEventHandler(cell, MouseEvent.MOUSE_RELEASED, event -> ripple.release());
        // Drop any in-flight ripple AND cancel a held press when the cell is recycled
        // so neither bleeds onto the next card: the index changes on every recycle and
        // on parking (index -> -1); the item listener additionally covers an in-place
        // replace at the same index. cancelInteraction (not clear) also resets the
        // pressed flag, so a cell recycled mid-press does not re-derive a pressed
        // overlay on the new card.
        disposer.registerListener(cell.indexProperty(), ripple::cancelInteraction);
        disposer.registerListener(cell.itemProperty(), ripple::cancelInteraction);

        updateChildren();
    }

    @Override
    protected void updateChildren() {
        super.updateChildren();
        // The first call comes from the LabeledSkinBase constructor, before this
        // skin's fields are initialized.
        if (ripple != null) {
            getChildren().add(0, ripple.getLayer());
        }
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        // Force the content holder (the cell's graphic) to fill the content box so
        // both the content and the ripple span the full card.
        Node graphic = getSkinnable().getGraphic();
        if (graphic != null) {
            graphic.resizeRelocate(x, y, w, h);
        }
        ripple.layout(getSkinnable().getWidth(), getSkinnable().getHeight());
    }

    /**
     * Stops ripple animations, removes the ripple layer and unregisters all ripple
     * listeners before the standard {@link CellSkinBase} cleanup runs.
     */
    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(this::disposeRipple, disposer::dispose, super::dispose);
    }

    // ==================== Ripple trigger ====================

    private void onPressed(MouseEvent event) {
        RXKanbanCardCell<T> cell = getSkinnable();
        if (event.getButton() != MouseButton.PRIMARY
                || !cell.isRippleEnabled() || cell.isDisabled() || cell.isEmpty()) {
            return;
        }
        Point2D local = cell.sceneToLocal(event.getSceneX(), event.getSceneY());
        ripple.press(local.getX(), local.getY(), false);
    }

    private void disposeRipple() {
        ripple.dispose();
        getChildren().remove(ripple.getLayer());
    }
}
