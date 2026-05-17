package io.github.leewyatt.rxcontrols.utils;

import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanPropertyBase;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only boolean property that is {@code true} when the target {@link Node}
 * is in a visible parent chain attached to a showing window. The value is true
 * only when all of the following hold:
 * <ul>
 *     <li>the node itself is {@link Node#isVisible() visible};</li>
 *     <li>every ancestor of the node up to the scene root is visible;</li>
 *     <li>the node is attached to a {@link Scene};</li>
 *     <li>the scene has a {@link Window};</li>
 *     <li>the window {@link Window#isShowing() is showing}.</li>
 * </ul>
 *
 * <p>Use this to pause long-running visual work, such as animations or timers,
 * when the target is detached, hidden, or hosted by a hidden window.</p>
 *
 * <h2>Lifecycle — two modes</h2>
 * <ol>
 *   <li><strong>Explicit ownership (recommended for {@code Skin}s):</strong>
 *       construct with {@code new TreeShowingProperty(node)} and call
 *       {@link #dispose()} when finished. After disposal the property
 *       reports {@code false} permanently and no further events are fired.</li>
 *   <li><strong>Shared cache (recommended for ad-hoc consumers):</strong>
 *       call {@link #of(Node)}. Subsequent calls for the same node return
 *       the same instance via {@link Node#getProperties()}, so multiple
 *       consumers share a single listener chain. Do not dispose the shared
 *       instance.</li>
 * </ol>
 *
 * <p>If you only need a single read and do not want to register listeners, use
 * {@link #isTreeShowing(Node)}.</p>
 */
public final class TreeShowingProperty extends ReadOnlyBooleanPropertyBase {

    private static final Object CACHE_KEY = new Object();

    private final Node target;

    private boolean valid;
    private boolean treeShowing;
    private boolean disposed;

    private final List<Node> chain = new ArrayList<>();

    private Scene currentScene;
    private Window currentWindow;

    private final InvalidationListener visibleChangeListener = obs -> invalidate();
    private final InvalidationListener showingChangeListener = obs -> invalidate();
    private final ChangeListener<Parent> parentChangeListener = (obs, oldP, newP) -> rebuildChain();
    private final ChangeListener<Scene> sceneChangeListener = (obs, oldScene, newScene) -> attachScene(newScene);
    private final ChangeListener<Window> windowChangeListener = (obs, oldWin, newWin) -> attachWindow(newWin);

    /**
     * Creates a tree-showing property that tracks the given node.
     *
     * @param target the node whose effective showing state will be observed
     * @throws NullPointerException if {@code target} is {@code null}
     */
    public TreeShowingProperty(Node target) {
        this.target = Objects.requireNonNull(target, "target must not be null");
        target.sceneProperty().addListener(sceneChangeListener);
        rebuildChain();
        attachScene(target.getScene());
    }

    @Override
    public Object getBean() {
        return target;
    }

    @Override
    public String getName() {
        return "treeShowing";
    }

    @Override
    public boolean get() {
        if (!valid) {
            treeShowing = compute();
            valid = true;
        }
        return treeShowing;
    }

    /**
     * Detaches all listeners installed on the node, scene, window and ancestor
     * chain. After disposal {@link #get()} returns {@code false} permanently
     * and the property no longer fires events. Safe to call multiple times.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;

        target.sceneProperty().removeListener(sceneChangeListener);
        detachScene();
        detachChain();

        treeShowing = false;
        valid = true;
    }

    /**
     * Returns a tree-showing property cached on the node's
     * {@link Node#getProperties() properties map}. Subsequent calls for the
     * same node return the same instance, so multiple consumers share a single
     * listener chain. Do not dispose the shared instance.
     *
     * @param node the node to obtain a tree-showing property for
     * @return the shared, read-only tree-showing property
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public static ReadOnlyBooleanProperty of(Node node) {
        Objects.requireNonNull(node, "node must not be null");
        Object cached = node.getProperties().get(CACHE_KEY);
        if (cached instanceof TreeShowingProperty existing && !existing.disposed) {
            return existing;
        }
        TreeShowingProperty fresh = new TreeShowingProperty(node);
        node.getProperties().put(CACHE_KEY, fresh);
        return fresh;
    }

    /**
     * Computes the tree-showing state once, without registering any listener.
     * Equivalent to constructing a {@link TreeShowingProperty}, reading
     * {@link #get()} and immediately calling {@link #dispose()} — but without
     * the allocation and listener overhead.
     *
     * @param node the node to inspect; {@code null} returns {@code false}
     * @return whether the node is in a visible parent chain attached to a showing window
     */
    public static boolean isTreeShowing(Node node) {
        if (node == null) {
            return false;
        }
        Node walk = node;
        while (walk != null) {
            if (!walk.isVisible()) {
                return false;
            }
            walk = walk.getParent();
        }
        Scene scene = node.getScene();
        if (scene == null) {
            return false;
        }
        Window window = scene.getWindow();
        return window != null && window.isShowing();
    }

    // ==================== Internal ====================

    private void invalidate() {
        if (disposed) {
            return;
        }
        if (valid) {
            valid = false;
            fireValueChangedEvent();
        }
    }

    private boolean compute() {
        if (currentWindow == null || !currentWindow.isShowing()) {
            return false;
        }
        if (currentScene == null) {
            return false;
        }
        for (Node n : chain) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private void rebuildChain() {
        detachChain();

        Node walk = target;
        while (walk != null) {
            walk.visibleProperty().addListener(visibleChangeListener);
            walk.parentProperty().addListener(parentChangeListener);
            chain.add(walk);
            walk = walk.getParent();
        }
        invalidate();
    }

    private void detachChain() {
        for (Node n : chain) {
            n.visibleProperty().removeListener(visibleChangeListener);
            n.parentProperty().removeListener(parentChangeListener);
        }
        chain.clear();
    }

    private void attachScene(Scene newScene) {
        if (currentScene != null) {
            currentScene.windowProperty().removeListener(windowChangeListener);
        }
        currentScene = newScene;
        if (currentScene != null) {
            currentScene.windowProperty().addListener(windowChangeListener);
            attachWindow(currentScene.getWindow());
        } else {
            attachWindow(null);
        }
    }

    private void detachScene() {
        if (currentScene != null) {
            currentScene.windowProperty().removeListener(windowChangeListener);
            currentScene = null;
        }
        if (currentWindow != null) {
            currentWindow.showingProperty().removeListener(showingChangeListener);
            currentWindow = null;
        }
    }

    private void attachWindow(Window newWindow) {
        if (currentWindow != null) {
            currentWindow.showingProperty().removeListener(showingChangeListener);
        }
        currentWindow = newWindow;
        if (currentWindow != null) {
            currentWindow.showingProperty().addListener(showingChangeListener);
        }
        invalidate();
    }
}
