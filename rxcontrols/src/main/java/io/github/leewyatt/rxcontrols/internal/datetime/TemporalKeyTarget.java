package io.github.leewyatt.rxcontrols.internal.datetime;

import javafx.scene.input.KeyEvent;

/**
 * Keyboard entry point of a date / time view skin. A view handles its own keys while
 * it is focused; a picker routes keys to the same entry point from its editor, since
 * its popup never takes focus.
 */
public interface TemporalKeyTarget {

    /**
     * Handles a key pressed on (or routed to) the view.
     *
     * @param event the key event
     * @return {@code true} if the key was handled and should be consumed
     */
    boolean handleKey(KeyEvent event);

    /**
     * Returns whether a keyboard cursor is currently shown, i.e. whether further
     * arrow / Home / End / Enter keys belong to the view rather than to a text editor.
     *
     * @return {@code true} if the keyboard cursor is shown
     */
    boolean isKeyboardCursorVisible();

    /**
     * Hides the keyboard cursor; the next navigation key re-seeds it.
     */
    void clearKeyboardCursor();
}
