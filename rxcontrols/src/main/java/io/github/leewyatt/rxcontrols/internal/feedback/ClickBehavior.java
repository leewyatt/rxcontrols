package io.github.leewyatt.rxcontrols.internal.feedback;

import io.github.leewyatt.rxcontrols.skins.SkinDisposer;
import io.github.leewyatt.rxcontrols.utils.RXMouse;
import io.github.leewyatt.rxcontrols.utils.RXOS;
import io.github.leewyatt.rxcontrols.utils.RXTreeShowingProperty;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.util.function.BooleanSupplier;

/**
 * Standard button behavior for a skin that does not extend
 * {@code ButtonSkin}, wired through the public
 * {@code arm()}/{@code disarm()}/{@code fire()} API: mouse arming, SPACE
 * (and ENTER on non-Mac platforms) activation, disarm on focus loss, and the
 * default and cancel button accelerators, which follow the button from scene
 * to scene. Mnemonics are not supported.
 *
 * <p>All handlers consume their event, matching the auto-consume default of
 * the standard button input mappings. For activation keys this is
 * load-bearing: scene accelerators only run for unconsumed events, so the
 * focused button must consume ENTER or a default button would also fire.
 * Other keys are left alone, so the scene still handles focus traversal.</p>
 */
public final class ClickBehavior {

    private static final boolean MAC = RXOS.isMacOS();
    private static final KeyCombination DEFAULT_BUTTON_COMBO = new KeyCodeCombination(KeyCode.ENTER);
    private static final KeyCombination CANCEL_BUTTON_COMBO = new KeyCodeCombination(KeyCode.ESCAPE);

    private final Button button;
    private final BooleanSupplier canArm;

    private boolean keyDown;
    // A key stroke that began while the button could not be armed stays
    // without effect until the key is released: the repeats of a held key
    // must not arm the button once it can be armed again.
    private boolean keyRefused;
    // The same for a mouse press: dragging out and back in must not arm it.
    private boolean mouseRefused;

    // Two objects: an accelerator is only removed while it is still the one
    // this behavior installed, and the two combinations are told apart.
    private final Runnable defaultButtonRunnable = () -> fireFromAccelerator();
    private final Runnable cancelButtonRunnable = () -> fireFromAccelerator();

    /**
     * Creates the behavior for the given button.
     *
     * @param button the button to drive
     * @param canArm asked before every arm; while it answers {@code false}
     *               a press does not arm the button, so nothing fires on
     *               release and no pressed feedback shows
     */
    public ClickBehavior(Button button, BooleanSupplier canArm) {
        this.button = button;
        this.canArm = canArm;
    }

    /**
     * Installs the handlers, listeners and accelerators. Everything is
     * removed again when the disposer is disposed.
     *
     * @param disposer the disposer of the skin
     */
    public void install(SkinDisposer disposer) {
        // Registered first, so it runs last, against the scene the button is
        // in at that moment.
        disposer.registerDisposeTask(() -> {
            // The release of a key that is still down goes to the next
            // behavior, which knows nothing of this stroke.
            if (keyDown) {
                keyDown = false;
                button.disarm();
            }
            Scene scene = button.getScene();
            setAccelerator(scene, DEFAULT_BUTTON_COMBO, defaultButtonRunnable, false);
            setAccelerator(scene, CANCEL_BUTTON_COMBO, cancelButtonRunnable, false);
        });

        disposer.registerEventHandler(button, MouseEvent.MOUSE_PRESSED, this::mousePressed);
        disposer.registerEventHandler(button, MouseEvent.MOUSE_RELEASED, this::mouseReleased);
        disposer.registerEventHandler(button, MouseEvent.MOUSE_ENTERED, this::mouseEntered);
        disposer.registerEventHandler(button, MouseEvent.MOUSE_EXITED, this::mouseExited);
        disposer.registerEventHandler(button, KeyEvent.KEY_PRESSED, this::keyPressed);
        disposer.registerEventHandler(button, KeyEvent.KEY_RELEASED, this::keyReleased);
        // If the key went down but focus left, the button must disarm or it
        // would stay armed forever.
        disposer.registerListener(button.focusedProperty(), () -> {
            if (button.isFocused()) {
                return;
            }
            // The release of the key goes to whatever has the focus now.
            keyRefused = false;
            if (keyDown) {
                keyDown = false;
                button.disarm();
            }
        });

        disposer.registerListener(button.defaultButtonProperty(),
                () -> setAccelerator(button.getScene(), DEFAULT_BUTTON_COMBO,
                        defaultButtonRunnable, button.isDefaultButton()));
        disposer.registerListener(button.cancelButtonProperty(),
                () -> setAccelerator(button.getScene(), CANCEL_BUTTON_COMBO,
                        cancelButtonRunnable, button.isCancelButton()));
        disposer.registerListener(button.sceneProperty(), (observable, oldScene, newScene) -> {
            if (oldScene != null) {
                setAccelerator(oldScene, DEFAULT_BUTTON_COMBO, defaultButtonRunnable, false);
                setAccelerator(oldScene, CANCEL_BUTTON_COMBO, cancelButtonRunnable, false);
            }
            if (newScene != null) {
                setAccelerator(newScene, DEFAULT_BUTTON_COMBO, defaultButtonRunnable,
                        button.isDefaultButton());
                setAccelerator(newScene, CANCEL_BUTTON_COMBO, cancelButtonRunnable,
                        button.isCancelButton());
            }
        });
        setAccelerator(button.getScene(), DEFAULT_BUTTON_COMBO, defaultButtonRunnable,
                button.isDefaultButton());
        setAccelerator(button.getScene(), CANCEL_BUTTON_COMBO, cancelButtonRunnable,
                button.isCancelButton());
    }

    /**
     * Returns whether the key is one that activates a button: SPACE always,
     * ENTER on non-Mac platforms only, matching the standard platform split.
     *
     * @param event the key event
     * @return {@code true} for an activation key
     */
    public static boolean isActivationKey(KeyEvent event) {
        return event.getCode() == KeyCode.SPACE
                || (event.getCode() == KeyCode.ENTER && !MAC);
    }

    /**
     * Returns whether a modifier key is down in the event.
     *
     * @param event the key event
     * @return {@code true} when shift, control, alt or meta is down
     */
    public static boolean hasModifier(KeyEvent event) {
        return event.isShiftDown() || event.isControlDown() || event.isAltDown() || event.isMetaDown();
    }

    // ==================== Mouse ====================

    private void mousePressed(MouseEvent event) {
        if (button.isFocusTraversable()) {
            button.requestFocus();
        }
        if (!button.isArmed() && RXMouse.isPlainPrimaryPress(event)) {
            // Judged afresh on every press: the release of a refused press
            // may never have arrived.
            mouseRefused = !canArm.getAsBoolean();
            if (!mouseRefused) {
                button.arm();
            }
        }
        event.consume();
    }

    private void mouseReleased(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            mouseRefused = false;
        }
        if (!keyDown && button.isArmed()) {
            // An action handler that throws propagates out of fire(); the
            // button must not stay armed behind it.
            try {
                button.fire();
            } finally {
                button.disarm();
            }
        }
        event.consume();
    }

    private void mouseEntered(MouseEvent event) {
        if (!keyDown && !mouseRefused && button.isPressed() && canArm.getAsBoolean()) {
            button.arm();
        }
        event.consume();
    }

    private void mouseExited(MouseEvent event) {
        if (!keyDown && button.isArmed()) {
            button.disarm();
        }
        event.consume();
    }

    // ==================== Keys ====================

    private void keyPressed(KeyEvent event) {
        // A modified press (e.g. Ctrl+ENTER) is not an activation: it stays
        // unconsumed for application shortcuts.
        if (!isActivationKey(event) || hasModifier(event)) {
            return;
        }
        if (!keyRefused && !button.isPressed() && !button.isArmed()) {
            if (canArm.getAsBoolean()) {
                keyDown = true;
                button.arm();
            } else {
                keyRefused = true;
            }
        }
        event.consume();
    }

    private void keyReleased(KeyEvent event) {
        if (!isActivationKey(event)) {
            return;
        }
        keyRefused = false;
        if (keyDown) {
            keyDown = false;
            if (button.isArmed()) {
                button.disarm();
                button.fire();
            }
            event.consume();
            return;
        }
        // Mirrors keyPressed: a modified release (e.g. Ctrl+ENTER) stays
        // unconsumed, so a shortcut sees both halves of its key stroke.
        if (!hasModifier(event)) {
            event.consume();
        }
    }

    // ==================== Accelerators ====================

    private void fireFromAccelerator() {
        // A button inside a hidden ancestor (an unselected tab, a collapsed
        // pane) is still in the scene but must not answer ENTER or ESCAPE.
        if (RXTreeShowingProperty.isTreeShowing(button) && !button.isDisabled()) {
            button.fire();
        }
    }

    private void setAccelerator(Scene scene, KeyCombination combo, Runnable runnable, boolean set) {
        if (scene == null) {
            return;
        }
        if (set) {
            scene.getAccelerators().put(combo, runnable);
        } else if (scene.getAccelerators().get(combo) == runnable) {
            scene.getAccelerators().remove(combo);
        }
    }
}
