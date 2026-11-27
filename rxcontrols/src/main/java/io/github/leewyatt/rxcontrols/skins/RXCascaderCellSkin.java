package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXCascaderCell;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import javafx.geometry.Point2D;
import javafx.scene.control.skin.ListCellSkin;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Default skin of {@link RXCascaderCell}: the native {@link ListCellSkin}, which keeps
 * the column's fixed row height, the measurement and the cell behavior, plus a press
 * ripple placed below the row content.
 *
 * <p>A primary press anywhere on a filled, enabled row ripples from the pointer,
 * the multiple-mode check box included. A recycled cell drops its ripple, so it does
 * not carry over to the next item.
 *
 * @param <T> application value type
 */
public class RXCascaderCellSkin<T> extends ListCellSkin<RXCascaderItem<T>> {

    private final RXCascaderCell<T> cell;
    private final SkinDisposer disposer = new SkinDisposer();
    private final RippleDecoration ripple;

    /**
     * Creates the skin and wires the ripple.
     *
     * @param cell the cell this skin is attached to
     */
    public RXCascaderCellSkin(RXCascaderCell<T> cell) {
        super(cell);
        this.cell = cell;
        ripple = new RippleDecoration(cell, cell.rippleEnabledProperty(), cell.stateOverlayEnabledProperty(),
                cell.rippleFillProperty(), cell::getRippleOpacity, null, null);
        ripple.getLayer().setManaged(false);

        // Filters: the multiple-mode check box consumes its own press and release.
        disposer.registerEventFilter(cell, MouseEvent.MOUSE_PRESSED, this::onPressed);
        disposer.registerEventFilter(cell, MouseEvent.MOUSE_RELEASED, event -> ripple.release());
        // Every recycle changes the index; the item listener covers an in-place replace.
        // cancelInteraction also drops a held press, which belongs to the old item.
        disposer.registerListener(cell.indexProperty(), ripple::cancelInteraction);
        disposer.registerListener(cell.itemProperty(), ripple::cancelInteraction);

        updateChildren();
    }

    // ==================== Children ====================

    @Override
    protected void updateChildren() {
        super.updateChildren();
        // The first call comes from the superclass constructor, before the ripple
        // exists; every call resets the children to the text and graphic.
        if (ripple != null) {
            getChildren().add(0, ripple.getLayer());
        }
    }

    // ==================== Ripple ====================

    private void onPressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || !cell.isRippleEnabled()
                || cell.isDisabled() || cell.isEmpty()) {
            return;
        }
        Point2D local = cell.sceneToLocal(event.getSceneX(), event.getSceneY());
        ripple.press(local.getX(), local.getY(), false);
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        ripple.layout(getSkinnable().getWidth(), getSkinnable().getHeight());
    }

    // ==================== Dispose ====================

    /** {@inheritDoc} */
    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(this::disposeRipple, disposer::dispose, super::dispose);
    }

    private void disposeRipple() {
        ripple.dispose();
        getChildren().remove(ripple.getLayer());
    }
}
