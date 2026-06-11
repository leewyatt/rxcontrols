package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleBehavior;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import javafx.geometry.Point2D;
import javafx.scene.control.skin.ButtonSkin;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Skin for {@link RXButton}: the standard {@link ButtonSkin} plus an internal
 * ripple layer placed above the button background and below the label.
 *
 * <p>The ripple lifecycle is driven by {@code armedProperty}, which already
 * covers every start/stop path of {@code ButtonBehavior} (valid primary
 * press, SPACE/ENTER activation, drag-exit disarm, focus-loss disarm). A
 * mouse-press event filter only records the pointer location; re-arming while
 * still pressed (dragging back in) does not start a new ripple.</p>
 */
public class RXButtonSkin extends ButtonSkin {

    private final SkinDisposer disposer = new SkinDisposer();
    private final RippleLayer rippleLayer = new RippleLayer();
    private final RippleBehavior rippleBehavior;

    private double pressX;
    private double pressY;
    private boolean pointerCoordsFresh;
    private boolean pointerInside;

    /**
     * Creates the skin and wires the ripple triggers.
     *
     * @param button the button this skin is attached to
     */
    public RXButtonSkin(RXButton button) {
        super(button);
        rippleBehavior = new RippleBehavior(rippleLayer,
                button::getRippleFill, button::getRippleOpacity);

        disposer.registerEventFilter(button, MouseEvent.MOUSE_PRESSED, this::recordPointerPress);
        disposer.registerEventFilter(button, MouseEvent.MOUSE_RELEASED,
                event -> pointerCoordsFresh = false);
        disposer.registerListener(button.armedProperty(), this::handleArmedChanged);
        disposer.registerListener(button.rippleEnabledProperty(), () -> {
            if (!button.isRippleEnabled()) {
                clearRipples();
                button.requestLayout();
            }
            updateStateOverlay();
        });
        disposer.registerListener(button.disabledProperty(), () -> {
            if (button.isDisabled()) {
                rippleBehavior.release();
            }
            updateStateOverlay();
        });
        disposer.registerListener(button.sceneProperty(), () -> {
            if (button.getScene() == null) {
                pointerInside = false;
                clearRipples();
            }
        });
        disposer.registerListener(button.backgroundProperty(), button::requestLayout);
        disposer.registerListener(button.shapeProperty(), button::requestLayout);
        disposer.registerListener(button.scaleShapeProperty(), button::requestLayout);
        disposer.registerListener(button.centerShapeProperty(), button::requestLayout);

        // Hover state overlay: a low-opacity tint while the pointer is inside.
        disposer.registerEventHandler(button, MouseEvent.MOUSE_ENTERED, event -> {
            pointerInside = true;
            updateStateOverlay();
        });
        disposer.registerEventHandler(button, MouseEvent.MOUSE_EXITED, event -> {
            pointerInside = false;
            updateStateOverlay();
        });
        disposer.registerListener(button.rippleFillProperty(),
                () -> rippleLayer.setOverlayFill(button.getRippleFill()));
        rippleLayer.setOverlayFill(button.getRippleFill());

        updateChildren();
    }

    @Override
    protected void updateChildren() {
        super.updateChildren();
        // The first call comes from the LabeledSkinBase constructor, before
        // this skin's fields are initialized.
        if (rippleLayer != null) {
            getChildren().add(0, rippleLayer);
        }
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        double width = getSkinnable().getWidth();
        double height = getSkinnable().getHeight();
        rippleLayer.resizeRelocate(0.0, 0.0, width, height);
        rippleLayer.updateClipFor(getSkinnable(), width, height);
    }

    /**
     * Stops ripple animations, removes the ripple layer and unregisters all
     * ripple listeners before the standard {@link ButtonSkin} cleanup runs.
     */
    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(this::disposeRipple, disposer::dispose, super::dispose);
    }

    // ==================== Ripple Triggers ====================

    private void recordPointerPress(MouseEvent event) {
        // Mirrors the "valid" arming condition of ButtonBehavior.mousePressed,
        // so stale coordinates are never left behind by presses that never arm.
        if (event.getButton() != MouseButton.PRIMARY
                || event.isMiddleButtonDown() || event.isSecondaryButtonDown()
                || event.isShiftDown() || event.isControlDown()
                || event.isAltDown() || event.isMetaDown()) {
            return;
        }
        Point2D local = getSkinnable().sceneToLocal(event.getSceneX(), event.getSceneY());
        pressX = local.getX();
        pressY = local.getY();
        pointerCoordsFresh = true;
    }

    private void handleArmedChanged() {
        RXButton button = (RXButton) getSkinnable();
        if (!button.isArmed()) {
            rippleBehavior.release();
            return;
        }
        if (!button.isRippleEnabled() || button.isDisabled()) {
            return;
        }
        if (pointerCoordsFresh) {
            pointerCoordsFresh = false;
            rippleBehavior.press(pressX, pressY, button.isRippleCentered());
        } else if (!button.isPressed()) {
            // Keyboard activation has no pointer location.
            rippleBehavior.press(0.0, 0.0, true);
        }
        // Re-armed while still pressed (dragged back in): no new ripple.
    }

    private void updateStateOverlay() {
        RXButton button = (RXButton) getSkinnable();
        boolean active = pointerInside && button.isRippleEnabled() && !button.isDisabled();
        rippleLayer.setOverlayState(active);
    }

    private void clearRipples() {
        rippleBehavior.clear();
        rippleLayer.clearClip();
    }

    private void disposeRipple() {
        clearRipples();
        getChildren().remove(rippleLayer);
    }
}
