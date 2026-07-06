package io.github.leewyatt.rxcontrols.utils;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * Helpers for interpreting {@link MouseEvent}s.
 */
public final class RXMouse {

    private RXMouse() {
    }

    /**
     * Returns whether the event is a "plain" primary-button press: the primary button
     * triggered it and no other mouse button or keyboard modifier is held (no middle /
     * secondary button, no Shift / Control / Alt / Meta).
     *
     * <p>Mirrors the arming condition of the JavaFX
     * {@code ButtonBehavior.mousePressed}, so a chorded press or a modifier-click
     * (for example a macOS Control-click, which is a popup trigger) never arms or
     * activates a control. Skins that install their own press handling — rather than
     * relying on {@code ButtonBehavior} — use this to gate arming.</p>
     *
     * @param event the mouse event, typically a {@code MOUSE_PRESSED}
     * @return {@code true} for an unmodified, unchorded primary-button press
     */
    public static boolean isPlainPrimaryPress(MouseEvent event) {
        return event.getButton() == MouseButton.PRIMARY
                && !(event.isMiddleButtonDown() || event.isSecondaryButtonDown()
                || event.isShiftDown() || event.isControlDown()
                || event.isAltDown() || event.isMetaDown());
    }
}
