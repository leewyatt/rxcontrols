package io.github.leewyatt.rxcontrols.internal;

import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TouchEvent;

/**
 * Tracks, per {@link Scene}, whether the latest input was a focus-traversal key
 * press. Skins consult it the moment focus arrives to tell keyboard focus apart
 * from focus gained by a pointer press, by the window opening, or programmatically,
 * so a focus indicator shows only for keyboard users.
 *
 * <p>This stands in for the {@code focusVisible} flag JavaFX added in version 19.
 * A traversal key arms the flag and only a pointer press clears it, so activation
 * keys pressed on an already focused control leave it untouched. The flag starts
 * cleared, which is what makes the focus a window hands out when it opens count as
 * neither keyboard nor pointer focus.</p>
 */
public final class KeyboardTraversalMonitor {

    private static final Object SCENE_KEY = new Object();

    private boolean traversalKeyPressed;

    private KeyboardTraversalMonitor() {
    }

    /**
     * Starts monitoring the given scene, once per scene. Call this as soon as a node
     * that will ask about traversal joins a scene: the flag is armed by the key press
     * that moves focus, which is delivered before the focus change itself, so a
     * monitor installed only when focus first arrives would miss that key press.
     *
     * @param scene the scene to monitor, may be {@code null}
     */
    public static void install(Scene scene) {
        if (scene == null || scene.getProperties().get(SCENE_KEY) instanceof KeyboardTraversalMonitor) {
            return;
        }
        KeyboardTraversalMonitor monitor = new KeyboardTraversalMonitor();
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (isTraversalKey(event)) {
                monitor.traversalKeyPressed = true;
            }
        });
        EventHandler<InputEvent> pointerPressed = event -> monitor.traversalKeyPressed = false;
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, pointerPressed);
        scene.addEventFilter(TouchEvent.TOUCH_PRESSED, pointerPressed);
        scene.getProperties().put(SCENE_KEY, monitor);
    }

    /**
     * Returns whether focus arriving in the given scene right now was brought in by a
     * traversal key. A scene that is not monitored reports no traversal.
     *
     * @param scene the scene to inspect, may be {@code null}
     * @return {@code true} if the latest input in the scene was a traversal key press
     */
    public static boolean isKeyboardTraversal(Scene scene) {
        if (scene == null) {
            return false;
        }
        Object installed = scene.getProperties().get(SCENE_KEY);
        return installed instanceof KeyboardTraversalMonitor
                && ((KeyboardTraversalMonitor) installed).traversalKeyPressed;
    }

    private static boolean isTraversalKey(KeyEvent event) {
        KeyCode code = event.getCode();
        return code == KeyCode.TAB
                || code == KeyCode.UP
                || code == KeyCode.DOWN
                || code == KeyCode.LEFT
                || code == KeyCode.RIGHT;
    }
}
