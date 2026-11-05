package io.github.leewyatt.rxcontrols.theme;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.utils.RXStyles;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SubScene;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Makes RxControls follow an <a href="https://github.com/mkpaz/atlantafx">AtlantaFX</a>
 * theme. By default RxControls derives its colors from the Modena palette; installing
 * the bridge on a scene re-points its color role tokens at AtlantaFX's {@code -color-*}
 * functional tokens, so the controls follow whichever AtlantaFX theme is the
 * Application user-agent stylesheet.
 *
 * <h2>Switching</h2>
 * The application owns the Application user-agent stylesheet; the bridge never sets
 * it. Switching is the user-agent stylesheet plus the bridge:
 * <pre>{@code
 * // Modena -> any AtlantaFX theme
 * Application.setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());
 * RXAtlantaFXThemeBridge.install(scene);
 *
 * // between AtlantaFX themes: the bridge stays as it is
 * Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
 *
 * // AtlantaFX -> Modena
 * Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
 * RXAtlantaFXThemeBridge.uninstall(scene);
 * }</pre>
 * Set the AtlantaFX user-agent stylesheet before a bridged scene is first shown or
 * styled, otherwise its first CSS pass cannot resolve {@code -color-*}. Remove the
 * bridge when going back to Modena: the bridge references {@code -color-*}, which
 * Modena does not define.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>Call on the JavaFX Application Thread, like
 *       {@code Application.setUserAgentStylesheet}. Nothing is deferred: the scene is
 *       bridged when the call returns.</li>
 *   <li>{@link #install} and {@link #uninstall} are idempotent.</li>
 *   <li>The bridge covers one whole scene. Install it on every scene that shows
 *       RxControls (each window, dialog and stage); it is not applied to scenes
 *       created later.</li>
 *   <li>Swapping the scene root keeps the bridge. Sub-scenes found at install time
 *       are bridged too; for a sub-scene added afterwards, or one given a new root,
 *       call {@link #install} again.</li>
 *   <li>A popup copies its owner scene's stylesheets when it shows, so popups opened
 *       after the change follow it; a popup already open follows once reopened.</li>
 *   <li>A scope style class the application put on a root before the install is
 *       left in place on uninstall. The scope class and the bridge stylesheet are
 *       otherwise the bridge's to manage while it is installed.</li>
 * </ul>
 *
 * <h2>Scope</h2>
 * Besides the role tokens, the bridge supplies the Modena base colors that RxControls
 * rules still reference and AtlantaFX does not define ({@code -fx-accent},
 * {@code -fx-control-inner-background}, …). Non-color values (radius, spacing, sizes)
 * are not themed.
 *
 * <p>This class adds no compile-time dependency on AtlantaFX.
 */
public final class RXAtlantaFXThemeBridge {

    /** Scope style class carried by every bridged root. */
    private static final String SCOPE_CLASS = "rx-theme-atlantafx";

    /** Scene property key for the roots the bridge marked, held weakly. */
    private static final String MARKED_ROOTS_KEY = "rx-atlantafx-bridge-roots";
    /** Scene property key for the listener that re-marks a swapped root. */
    private static final String ROOT_LISTENER_KEY = "rx-atlantafx-bridge-root-listener";
    /** Node property key for how many bridged scenes currently mark the node. */
    private static final String MARK_COUNT_KEY = "rx-atlantafx-bridge-marks";
    /** Node property key set when the bridge, not the application, added the scope class. */
    private static final String CLASS_ADDED_KEY = "rx-atlantafx-bridge-class-added";

    private RXAtlantaFXThemeBridge() {
    }

    /**
     * Returns the external-form URL of the bridge's author stylesheet, for callers that
     * manage stylesheet lists themselves. Such callers must also put
     * {@link #getScopeClass()} on the roots they bridge. A scope built this way is
     * managed by the caller; the library only guarantees a complete look for a scene
     * bridged with {@link #install(Scene)}.
     *
     * @return the bridge stylesheet URL
     */
    public static String getStylesheet() {
        return RXResources.ATLANTAFX_BRIDGE_STYLESHEET;
    }

    /**
     * Returns the scope style class the bridge puts on every bridged root, for callers
     * that manage stylesheet lists themselves (see {@link #getStylesheet()}).
     *
     * @return the bridge scope style class
     */
    public static String getScopeClass() {
        return SCOPE_CLASS;
    }

    /**
     * Bridges the scene: adds the bridge stylesheet, marks the root and every sub-scene
     * root under it, and keeps the bridge when the root is swapped. Installing again
     * re-scans the scene for sub-scenes.
     *
     * @param scene the scene to bridge
     * @throws NullPointerException if {@code scene} is null
     */
    public static void install(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        RXStyles.addSheets(scene, getStylesheet());
        markTree(scene, scene.getRoot());
        if (!scene.getProperties().containsKey(ROOT_LISTENER_KEY)) {
            ChangeListener<Parent> listener = (observable, oldRoot, newRoot) -> markTree(scene, newRoot);
            scene.rootProperty().addListener(listener);
            scene.getProperties().put(ROOT_LISTENER_KEY, listener);
        }
    }

    /**
     * Removes the bridge from the scene, including the roots it marked that have
     * since left the scene. Does nothing if the scene is not bridged. It also removes the
     * bridge stylesheet when the caller added it through {@link #getStylesheet()}, so do
     * not mix the two on one scene.
     *
     * @param scene the scene to unbridge
     * @throws NullPointerException if {@code scene} is null
     */
    @SuppressWarnings("unchecked")
    public static void uninstall(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        RXStyles.removeSheets(scene, getStylesheet());
        Object listener = scene.getProperties().remove(ROOT_LISTENER_KEY);
        if (listener != null) {
            scene.rootProperty().removeListener((ChangeListener<Parent>) listener);
        }
        unmarkTree(scene);
    }

    // ==================== Marking ====================

    /**
     * Marks the root and every nested sub-scene root. A sub-scene root is not a child
     * of the sub-scene node, so the scope class on the scene root does not reach it.
     */
    private static void markTree(Scene scene, Parent root) {
        unmarkTree(scene);
        List<Parent> roots = new ArrayList<>();
        collectRoots(root, roots);
        List<WeakReference<Parent>> marked = new ArrayList<>();
        for (Parent node : roots) {
            mark(node);
            marked.add(new WeakReference<>(node));
        }
        scene.getProperties().put(MARKED_ROOTS_KEY, marked);
    }

    @SuppressWarnings("unchecked")
    private static void unmarkTree(Scene scene) {
        Object marked = scene.getProperties().remove(MARKED_ROOTS_KEY);
        if (!(marked instanceof List)) {
            return;
        }
        for (WeakReference<Parent> reference : (List<WeakReference<Parent>>) marked) {
            Parent node = reference.get();
            if (node != null) {
                unmark(node);
            }
        }
    }

    private static void mark(Parent node) {
        ObservableMap<Object, Object> properties = node.getProperties();
        int count = markCount(properties);
        if (count == 0 && !node.getStyleClass().contains(SCOPE_CLASS)) {
            RXStyles.addClass(node, SCOPE_CLASS);
            properties.put(CLASS_ADDED_KEY, Boolean.TRUE);
        }
        properties.put(MARK_COUNT_KEY, count + 1);
    }

    private static void unmark(Parent node) {
        ObservableMap<Object, Object> properties = node.getProperties();
        int count = markCount(properties);
        if (count > 1) {
            properties.put(MARK_COUNT_KEY, count - 1);
            return;
        }
        properties.remove(MARK_COUNT_KEY);
        if (properties.remove(CLASS_ADDED_KEY) != null) {
            RXStyles.removeClass(node, SCOPE_CLASS);
        }
    }

    private static int markCount(ObservableMap<Object, Object> properties) {
        Object count = properties.get(MARK_COUNT_KEY);
        return count instanceof Integer ? (Integer) count : 0;
    }

    private static void collectRoots(Parent root, List<Parent> found) {
        if (root == null) {
            return;
        }
        found.add(root);
        List<SubScene> subScenes = new ArrayList<>();
        collectSubScenes(root, subScenes);
        for (SubScene subScene : subScenes) {
            collectRoots(subScene.getRoot(), found);
        }
    }

    private static void collectSubScenes(Node node, List<SubScene> found) {
        if (node instanceof SubScene) {
            found.add((SubScene) node);
            return;
        }
        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                collectSubScenes(child, found);
            }
        }
    }
}
