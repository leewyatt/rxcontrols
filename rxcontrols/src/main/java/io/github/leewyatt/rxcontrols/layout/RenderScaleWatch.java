package io.github.leewyatt.rxcontrols.layout;

import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Window;

/**
 * Runs an action whenever the render scale a node is drawn at may have changed: when
 * the render scale of its window changes on either axis, or when the node moves to
 * another window or out of one.
 *
 * <p>Region snaps sizes and positions to that scale but lays nothing out again when it
 * changes, so a pane that caches snapped sizes (its own, or the preferred size Region
 * caches) uses this to request its layout. The listeners sit only on the node's current
 * scene and window and move with them, so neither keeps a node that has left it
 * reachable.</p>
 */
final class RenderScaleWatch {

    private final Runnable action;
    private final InvalidationListener scaleListener;
    private final ChangeListener<Window> windowListener = (obs, oldWindow, newWindow) -> watchWindow(newWindow);
    private Scene scene;
    private Window window;

    /**
     * Starts watching the node.
     *
     * @param node   the node whose window is watched
     * @param action the action to run when the render scale may have changed
     */
    RenderScaleWatch(Node node, Runnable action) {
        this.action = action;
        this.scaleListener = obs -> action.run();
        node.sceneProperty().addListener((obs, oldScene, newScene) -> watchScene(newScene));
        watchScene(node.getScene());
    }

    private void watchScene(Scene newScene) {
        if (scene != null) {
            scene.windowProperty().removeListener(windowListener);
        }
        scene = newScene;
        if (scene != null) {
            scene.windowProperty().addListener(windowListener);
        }
        watchWindow(scene == null ? null : scene.getWindow());
    }

    private void watchWindow(Window newWindow) {
        if (newWindow == window) {
            return;
        }
        if (window != null) {
            window.renderScaleXProperty().removeListener(scaleListener);
            window.renderScaleYProperty().removeListener(scaleListener);
        }
        window = newWindow;
        if (window != null) {
            window.renderScaleXProperty().addListener(scaleListener);
            window.renderScaleYProperty().addListener(scaleListener);
        }
        // Sizes measured outside this window, or in another one, were snapped to its scale.
        action.run();
    }
}
