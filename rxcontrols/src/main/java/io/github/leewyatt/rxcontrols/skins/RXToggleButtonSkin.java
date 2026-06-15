package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXToggleButton;
import io.github.leewyatt.rxcontrols.event.RXAnimationEvent;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import javafx.geometry.Point2D;
import javafx.scene.control.skin.ToggleButtonSkin;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Skin for {@link RXToggleButton}: the standard {@link ToggleButtonSkin} plus a
 * {@link RippleDecoration} placed above the button background and below the
 * label. The decoration owns the ripple layer, hover overlay and shared
 * lifecycle; this skin only supplies the armed-driven trigger.
 *
 * <p>The ripple lifecycle is driven by {@code armedProperty}, which already
 * covers every start/stop path of {@code ToggleButtonBehavior} (valid primary
 * press, SPACE/ENTER activation, drag-exit disarm, focus-loss disarm). A
 * mouse-press event filter only records the pointer location; re-arming while
 * still pressed (dragging back in) does not start a new ripple.</p>
 */
public class RXToggleButtonSkin extends ToggleButtonSkin {

    private final SkinDisposer disposer = new SkinDisposer();
    private final RippleDecoration ripple;

    private double pressX;
    private double pressY;
    private boolean pointerCoordsFresh;

    /**
     * Creates the skin and wires the ripple triggers.
     *
     * @param toggle the toggle button this skin is attached to
     */
    public RXToggleButtonSkin(RXToggleButton toggle) {
        super(toggle);
        ripple = new RippleDecoration(toggle, toggle.rippleEnabledProperty(),
                toggle.rippleFillProperty(), toggle::getRippleOpacity,
                null, toggle.rippleCornerRadiusProperty());

        disposer.registerEventFilter(toggle, MouseEvent.MOUSE_PRESSED, this::recordPointerPress);
        disposer.registerEventFilter(toggle, MouseEvent.MOUSE_RELEASED,
                event -> pointerCoordsFresh = false);
        disposer.registerListener(toggle.armedProperty(), this::handleArmedChanged);
        disposer.registerEventHandler(toggle, RXAnimationEvent.PLAY_RIPPLE, event -> {
            // Reject events bubbling up from a nested ripple host.
            if (event.getTarget() != toggle) {
                return;
            }
            if (toggle.isRippleEnabled() && !toggle.isDisabled()) {
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
     * ripple listeners before the standard {@link ToggleButtonSkin} cleanup
     * runs.
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
        // Mirrors the "valid" arming condition of ToggleButtonBehavior's mouse
        // press, so stale coordinates are never left behind by presses that never arm.
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
        RXToggleButton toggle = (RXToggleButton) getSkinnable();
        if (!toggle.isArmed()) {
            ripple.release();
            return;
        }
        if (!toggle.isRippleEnabled() || toggle.isDisabled()) {
            return;
        }
        if (pointerCoordsFresh) {
            pointerCoordsFresh = false;
            ripple.press(pressX, pressY, toggle.isRippleCentered());
        } else if (!toggle.isPressed()) {
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
