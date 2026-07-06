package io.github.leewyatt.rxcontrols.utils;

import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXMouse#isPlainPrimaryPress(MouseEvent)}.
 */
public class RXMouseTest {

    /**
     * Verifies a plain primary-button press (no chording, no modifiers) is accepted.
     */
    @Test
    public void acceptsPlainPrimaryPress() {
        assertTrue(RXMouse.isPlainPrimaryPress(
                press(MouseButton.PRIMARY, false, false, false, false, false, false)));
    }

    /**
     * Verifies a non-primary button press is rejected.
     */
    @Test
    public void rejectsNonPrimaryButton() {
        assertFalse(RXMouse.isPlainPrimaryPress(
                press(MouseButton.SECONDARY, false, false, false, false, false, false)));
        assertFalse(RXMouse.isPlainPrimaryPress(
                press(MouseButton.MIDDLE, false, false, false, false, false, false)));
    }

    /**
     * Verifies a primary press with any keyboard modifier or another mouse button held
     * is rejected (a macOS Control-click and a chorded press both fail).
     */
    @Test
    public void rejectsChordedOrModifiedPrimaryPress() {
        assertFalse(RXMouse.isPlainPrimaryPress(
                press(MouseButton.PRIMARY, true, false, false, false, false, false)), "shift");
        assertFalse(RXMouse.isPlainPrimaryPress(
                press(MouseButton.PRIMARY, false, true, false, false, false, false)), "control");
        assertFalse(RXMouse.isPlainPrimaryPress(
                press(MouseButton.PRIMARY, false, false, true, false, false, false)), "alt");
        assertFalse(RXMouse.isPlainPrimaryPress(
                press(MouseButton.PRIMARY, false, false, false, true, false, false)), "meta");
        assertFalse(RXMouse.isPlainPrimaryPress(
                press(MouseButton.PRIMARY, false, false, false, false, true, false)), "middle chord");
        assertFalse(RXMouse.isPlainPrimaryPress(
                press(MouseButton.PRIMARY, false, false, false, false, false, true)), "secondary chord");
    }

    private static MouseEvent press(MouseButton button, boolean shift, boolean control,
                                    boolean alt, boolean meta, boolean middleDown,
                                    boolean secondaryDown) {
        return new MouseEvent(MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, button, 1,
                shift, control, alt, meta,
                button == MouseButton.PRIMARY, middleDown, secondaryDown,
                false, false, true, new PickResult(null, 0, 0));
    }
}
