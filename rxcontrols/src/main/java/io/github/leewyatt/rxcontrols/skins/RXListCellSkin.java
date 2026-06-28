package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.skin.CellSkinBase;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Skin for {@link RXListCell}: the standard {@link CellSkinBase} plus an embedded
 * {@link RippleDecoration} placed below the cell content. The cell owns its
 * three-segment content container (set as the cell graphic); this skin places the
 * ripple layer behind it, stretches the container to fill the row, supplies the
 * pointer-press ripple trigger and clears the ripple when the cell is recycled.
 *
 * <p>Selection toggling is handled centrally by {@code RXListViewSkin}; the
 * checkbox / checkmark are mouse-transparent display indicators, so a press
 * anywhere on the row — including over them — is one row click that ripples.
 *
 * @param <T> the item type
 */
public class RXListCellSkin<T> extends CellSkinBase<RXListCell<T>> {

    private final SkinDisposer disposer = new SkinDisposer();
    private final RippleDecoration ripple;

    /**
     * Creates the skin and wires the ripple.
     *
     * @param cell the cell this skin is attached to
     */
    public RXListCellSkin(RXListCell<T> cell) {
        super(cell);
        ripple = new RippleDecoration(cell, cell.rippleEnabledProperty(),
                cell.stateOverlayEnabledProperty(), cell.rippleFillProperty(),
                cell::getRippleOpacity, null, null);

        disposer.registerEventHandler(cell, MouseEvent.MOUSE_PRESSED, this::onPressed);
        disposer.registerEventHandler(cell, MouseEvent.MOUSE_RELEASED, event -> ripple.release());
        // Drop any in-flight ripple AND cancel a held press when the cell is recycled
        // so neither bleeds onto the next item: the index changes on every recycle
        // (covering a new item even at a duplicate object reference) and on parking
        // (index -> -1); the item listener additionally covers an in-place replace at
        // the same index. cancelInteraction (not clear) also resets the pressed flag,
        // so a cell recycled mid-press does not re-derive a pressed overlay on the new
        // item at the next layout pass; pointer-inside is kept so hover still follows.
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
        // Force the content container (the cell's graphic) to fill the content box so
        // the selection slot, content and ripple span the full row width.
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
        RXListCell<T> cell = getSkinnable();
        if (event.getButton() != MouseButton.PRIMARY
                || !cell.isRippleEnabled() || cell.isDisabled() || cell.isEmpty()) {
            return;
        }
        // A press anywhere on the row — including over the display-only checkbox /
        // checkmark — is one row click, so it always ripples.
        Point2D local = cell.sceneToLocal(event.getSceneX(), event.getSceneY());
        ripple.press(local.getX(), local.getY(), false);
    }

    private void disposeRipple() {
        ripple.dispose();
        getChildren().remove(ripple.getLayer());
    }
}
