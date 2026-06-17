package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.event.RXAnimationEvent;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import javafx.geometry.Point2D;
import javafx.scene.control.skin.ButtonSkin;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Skin for {@link RXButton}: the standard {@link ButtonSkin} plus a
 * {@link RippleDecoration} placed above the button background and below the
 * label. The decoration owns the ripple layer, hover overlay and shared
 * lifecycle; this skin only supplies the armed-driven trigger.
 *
 * <p>The ripple lifecycle is driven by {@code armedProperty}, which already
 * covers every start/stop path of {@code ButtonBehavior} (valid primary
 * press, SPACE/ENTER activation, drag-exit disarm, focus-loss disarm). A
 * mouse-press event filter only records the pointer location; re-arming while
 * still pressed (dragging back in) does not start a new ripple.</p>
 */
public class RXButtonSkin extends ButtonSkin {

    private final SkinDisposer disposer = new SkinDisposer();
    private final RippleDecoration ripple;

    private double pressX;
    private double pressY;
    private boolean pointerCoordsFresh;

    /**
     * Creates the skin and wires the ripple triggers.
     *
     * @param button the button this skin is attached to
     */
    public RXButtonSkin(RXButton button) {
        super(button);
        ripple = new RippleDecoration(button, button.rippleEnabledProperty(),
                button.hoverOverlayEnabledProperty(), button.rippleFillProperty(),
                button::getRippleOpacity, null, button.rippleCornerRadiusProperty());

        disposer.registerEventFilter(button, MouseEvent.MOUSE_PRESSED, this::recordPointerPress);
        disposer.registerEventFilter(button, MouseEvent.MOUSE_RELEASED,
                event -> pointerCoordsFresh = false);
        disposer.registerListener(button.armedProperty(), this::handleArmedChanged);
        disposer.registerEventHandler(button, RXAnimationEvent.PLAY_RIPPLE, event -> {
            // Reject events bubbling up from a nested ripple host.
            if (event.getTarget() != button) {
                return;
            }
            if (button.isRippleEnabled() && !button.isDisabled()) {
                ripple.press(0.0, 0.0, true);
                ripple.release();
            }
            event.consume();
        });

        updateChildren();
    }

    @Override
    protected void updateChildren() {
        super.updateChildren();
        // The first call comes from the LabeledSkinBase constructor, before
        // this skin's fields are initialized.
        if (ripple != null) {
            getChildren().add(0, ripple.getLayer());
        }
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        ripple.layout(getSkinnable().getWidth(), getSkinnable().getHeight());
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

    // ==================== Ripple Trigger ====================

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
            ripple.release();
            return;
        }
        if (!button.isRippleEnabled() || button.isDisabled()) {
            return;
        }
        if (pointerCoordsFresh) {
            pointerCoordsFresh = false;
            ripple.press(pressX, pressY, button.isRippleCentered());
        } else if (!button.isPressed()) {
            // Keyboard activation has no pointer location.
            ripple.press(0.0, 0.0, true);
        }
        // Re-armed while still pressed (dragged back in): no new ripple.
    }

    private void disposeRipple() {
        ripple.dispose();
        getChildren().remove(ripple.getLayer());
    }
}
