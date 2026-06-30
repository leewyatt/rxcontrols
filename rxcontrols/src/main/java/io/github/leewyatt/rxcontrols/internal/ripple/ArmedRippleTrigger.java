package io.github.leewyatt.rxcontrols.internal.ripple;

import io.github.leewyatt.rxcontrols.event.AnimationEvent;
import io.github.leewyatt.rxcontrols.skins.SkinDisposer;
import javafx.geometry.Point2D;
import javafx.scene.control.ButtonBase;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Shared armed-driven ripple trigger for {@link ButtonBase} ripple hosts whose
 * skins extend different base classes ({@code ButtonSkin}, {@code ToggleButtonSkin},
 * {@code RXSkinBase}) and therefore cannot inherit it. It owns the pointer-press
 * bookkeeping and the {@code armed} reaction that were otherwise copied verbatim
 * across {@code RXButtonSkin}, {@code RXToggleButtonSkin} and
 * {@code RXTransitionButtonSkin}.
 *
 * <p>The host's {@code armed} property already covers every start/stop path of the
 * standard button behavior (valid primary press, SPACE/ENTER activation, drag-exit
 * disarm, focus-loss disarm), so it is the trigger; a mouse-press event filter only
 * records the pointer location, and re-arming while still pressed (dragging back in)
 * does not start a new ripple. The skin keeps ownership of the {@code armed}
 * listener registration so a skin that reacts to {@code armed} for more than the
 * ripple (the transition button also flips its face) can call
 * {@link #handleArmedChanged()} from its own combined listener.</p>
 */
public final class ArmedRippleTrigger {

    private final ButtonBase control;
    private final RippleDecoration ripple;
    private final BooleanSupplier rippleEnabled;
    private final BooleanSupplier rippleCentered;

    private double pressX;
    private double pressY;
    private boolean pointerCoordsFresh;

    /**
     * Creates a trigger for the given host.
     *
     * @param control        the ripple host (a {@code ButtonBase})
     * @param ripple         the decoration to drive
     * @param rippleEnabled  supplies whether press ripple is enabled
     * @param rippleCentered supplies whether pointer presses start from the center
     * @throws NullPointerException if any argument is {@code null}
     */
    public ArmedRippleTrigger(ButtonBase control,
                              RippleDecoration ripple,
                              BooleanSupplier rippleEnabled,
                              BooleanSupplier rippleCentered) {
        this.control = Objects.requireNonNull(control, "control cannot be null");
        this.ripple = Objects.requireNonNull(ripple, "ripple cannot be null");
        this.rippleEnabled = Objects.requireNonNull(rippleEnabled, "rippleEnabled cannot be null");
        this.rippleCentered = Objects.requireNonNull(rippleCentered, "rippleCentered cannot be null");
    }

    /**
     * Registers the pointer-press tracking filters so the next arm ripples at the
     * press location; the host must still register an {@code armed} listener that
     * calls {@link #handleArmedChanged()}.
     *
     * @param disposer the skin disposer that owns the registrations
     */
    public void installPointerTracking(SkinDisposer disposer) {
        disposer.registerEventFilter(control, MouseEvent.MOUSE_PRESSED, this::recordPointerPress);
        disposer.registerEventFilter(control, MouseEvent.MOUSE_RELEASED,
                event -> pointerCoordsFresh = false);
    }

    /**
     * Reacts to an {@code armed} change: a disarm releases the ripple; an arm
     * starts one at the recorded pointer location (or the center for keyboard
     * activation), gated by ripple-enabled and the disabled state. Re-arming while
     * still pressed (dragged back in) starts no new ripple.
     */
    public void handleArmedChanged() {
        if (!control.isArmed()) {
            ripple.release();
            return;
        }
        if (!rippleEnabled.getAsBoolean() || control.isDisabled()) {
            return;
        }
        if (pointerCoordsFresh) {
            pointerCoordsFresh = false;
            ripple.press(pressX, pressY, rippleCentered.getAsBoolean());
        } else if (!control.isPressed()) {
            // Keyboard activation has no pointer location.
            ripple.press(0.0, 0.0, true);
        }
    }

    /**
     * Registers the {@link AnimationEvent#PLAY_RIPPLE} programmatic trigger: a
     * centered press and immediate release, gated by ripple-enabled and the
     * disabled state. Events bubbling up from a nested ripple host are passed
     * through (not consumed).
     *
     * @param disposer the skin disposer that owns the registration
     */
    public void installPlayRipple(SkinDisposer disposer) {
        disposer.registerEventHandler(control, AnimationEvent.PLAY_RIPPLE, event -> {
            // Reject events bubbling up from a nested ripple host.
            if (event.getTarget() != control) {
                return;
            }
            if (rippleEnabled.getAsBoolean() && !control.isDisabled()) {
                ripple.press(0.0, 0.0, true);
                ripple.release();
            }
            event.consume();
        });
    }

    private void recordPointerPress(MouseEvent event) {
        // Mirrors the "valid" arming condition of ButtonBehavior.mousePressed, so
        // stale coordinates are never left behind by presses that never arm.
        if (event.getButton() != MouseButton.PRIMARY
                || event.isMiddleButtonDown() || event.isSecondaryButtonDown()
                || event.isShiftDown() || event.isControlDown()
                || event.isAltDown() || event.isMetaDown()) {
            return;
        }
        Point2D local = control.sceneToLocal(event.getSceneX(), event.getSceneY());
        pressX = local.getX();
        pressY = local.getY();
        pointerCoordsFresh = true;
    }
}
