package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import javafx.scene.Node;
import javafx.scene.Scene;

/**
 * Keeps the keyboard focus in place when a sample's button disables itself. A focused
 * node that becomes disabled hands the focus on by traversal, and the page's scroll pane
 * scrolls to wherever it lands; moving the focus first keeps the page still.
 */
final class FocusHandOff {

    private FocusHandOff() {
    }

    /**
     * Moves the focus to {@code next} whenever {@code button} becomes disabled while it
     * is the scene's focus owner.
     *
     * @param button the node that may disable itself
     * @param next   where the focus goes instead; any node in the scene can hold it
     */
    static void onDisable(Node button, Node next) {
        // The focus owner, not isFocused: that one is false while the window is in the
        // background, yet the scene still hands the focus on.
        button.disabledProperty().addListener((obs, wasDisabled, disabled) -> {
            Scene scene = button.getScene();
            if (disabled && scene != null && scene.getFocusOwner() == button) {
                next.requestFocus();
            }
        });
    }
}
