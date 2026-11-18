package io.github.leewyatt.rxcontrols.skins;

import javafx.css.PseudoClass;
import javafx.scene.control.Control;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

/**
 * Drives the {@code :focus-shown} pseudo-class on a control: set while the control is
 * focused, unless that focus came from a pointer press and no key has been pressed
 * since. A user who just clicked a button knows where the focus is; a user on the
 * keyboard needs to see it.
 *
 * <p>Not {@code :focus-visible}: from JavaFX 19 the platform writes that pseudo-class
 * on every node, and two writers would overwrite each other.</p>
 */
final class FocusShownTracker {

    static final PseudoClass FOCUS_SHOWN = PseudoClass.getPseudoClass("focus-shown");

    private final Control control;

    /** True when the focus the control holds arrived through a pointer press. */
    private boolean pointerFocus;

    private FocusShownTracker(Control control) {
        this.control = control;
    }

    /**
     * Starts tracking the control; everything is unregistered when the disposer runs.
     *
     * @param control  the control that carries the pseudo-class
     * @param disposer the skin's disposer
     */
    static void install(Control control, SkinDisposer disposer) {
        FocusShownTracker tracker = new FocusShownTracker(control);
        // A filter, so the flag is set before the button behavior requests focus.
        disposer.registerEventFilter(control, MouseEvent.MOUSE_PRESSED, event -> tracker.onPointerPressed());
        disposer.registerEventFilter(control, KeyEvent.KEY_PRESSED, tracker::onKeyPressed);
        disposer.registerListener(control.focusedProperty(), tracker::onFocusChanged);
        disposer.registerDisposeTask(() -> control.pseudoClassStateChanged(FOCUS_SHOWN, false));
        tracker.update();
    }

    private void onPointerPressed() {
        pointerFocus = true;
        update();
    }

    private void onKeyPressed(KeyEvent event) {
        // Modifiers and shortcut chords (Cmd+C, Cmd+Tab) do not mean the user moved
        // to the keyboard.
        if (pointerFocus && !event.getCode().isModifierKey()
                && !event.isMetaDown() && !event.isControlDown() && !event.isAltDown()) {
            pointerFocus = false;
            update();
        }
    }

    private void onFocusChanged() {
        if (!control.isFocused()) {
            // The next focus, if not from a press, shows the indicator again.
            pointerFocus = false;
        }
        update();
    }

    private void update() {
        control.pseudoClassStateChanged(FOCUS_SHOWN, control.isFocused() && !pointerFocus);
    }
}
