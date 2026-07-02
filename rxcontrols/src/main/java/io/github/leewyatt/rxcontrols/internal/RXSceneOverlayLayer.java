package io.github.leewyatt.rxcontrols.internal;

import javafx.beans.InvalidationListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

/**
 * Base class for per-scene, self-installing overlay layers. Each concrete layer
 * type keeps one instance per scene (cached in the scene's property map under its
 * own key), mounts itself adaptively over the scene, and stays click-through
 * outside its own children ({@code pickOnBounds = false}).
 *
 * <p>Installation adapts to the root: if the scene root (or an explicit container)
 * is a {@link Pane}, the layer is added as a {@code managed = false} child sized to
 * fill it (so layout panes such as {@code BorderPane} do not reserve space for it);
 * if the root is not an addable {@code Pane}, the original root is wrapped in a
 * {@link StackPane} as a fallback. {@link #detachChild(Node)} uninstalls the layer
 * when its last child leaves.</p>
 *
 * <p>Stacking between different overlay layer types over the same scene is decided
 * by a fixed per-type {@link Node#viewOrderProperty() viewOrder} (lower renders in
 * front), not by attach order — so, for example, a dialog layer stays above a
 * snackbar layer no matter which was installed first.</p>
 */
public abstract class RXSceneOverlayLayer extends StackPane {

    private enum InstallMode {
        /** Added as a managed=false overlay child of a container / root pane. */
        PANE_CHILD,
        /** The original root was wrapped in a StackPane holding it and this layer. */
        WRAP
    }

    // The subclass's per-scene cache key in Scene.getProperties().
    private final Object sceneKey;

    private Scene scene;
    private InstallMode mode;
    private Pane fillParent;
    private InvalidationListener fillListener;
    private Parent originalRoot;
    // Guards uninstall against re-entry: removing this layer from its parent
    // nulls the layer's scene DURING the removal (Parent pre-processes outgoing
    // children before the index-based removal commits), so the scene listener
    // below fires mid-removal — an unguarded nested uninstall would remove the
    // layer first and the outer removal would then throw IndexOutOfBounds.
    private boolean uninstalling;

    /**
     * Creates the layer with its per-scene cache key and fixed stacking priority.
     *
     * @param sceneKey  the key this layer is cached under in {@code Scene.getProperties()}
     * @param viewOrder the fixed viewOrder among sibling overlay layers (lower = in front)
     */
    protected RXSceneOverlayLayer(Object sceneKey, double viewOrder) {
        this.sceneKey = sceneKey;
        setViewOrder(viewOrder);
        // Empty areas of the layer stay click-through, so the rest of the scene
        // remains interactive; only the layer's own children can be picked.
        setPickOnBounds(false);
        // Losing the scene means the mount point is gone (the app swapped the
        // scene root, or an ancestor was removed): uninstall immediately so the
        // scene's property map does not pin this layer — and through fillParent /
        // originalRoot the entire old root tree — until some later attach happens
        // to notice. Idempotent with the normal detach path: uninstall() clears
        // its fields, so the second run is a no-op.
        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                uninstall();
            }
        });
    }

    /**
     * Returns the pane this layer fills in {@code PANE_CHILD} mode, or {@code null}
     * when uninstalled or mounted by wrapping.
     *
     * @return the fill parent, or {@code null}
     */
    protected final Pane getFillParent() {
        return fillParent;
    }

    /**
     * Mounts this layer over the scene and caches it in the scene's property map
     * under this layer's key. With an explicit container the layer fills that pane;
     * otherwise it fills a {@code Pane} scene root, or wraps a non-{@code Pane} root
     * in a {@code StackPane} as a fallback.
     *
     * @param targetScene the scene to mount over
     * @param container   the explicit pane to fill, or {@code null} to mount at the scene root
     */
    protected final void installInto(Scene targetScene, Pane container) {
        this.scene = targetScene;
        if (container != null) {
            installAsChildOf(container);
        } else {
            Parent root = targetScene.getRoot();
            if (root instanceof Pane pane) {
                installAsChildOf(pane);
            } else {
                installByWrapping(targetScene, root);
            }
        }
        targetScene.getProperties().put(sceneKey, this);
    }

    /**
     * Removes a child from this layer, uninstalling the layer when it becomes empty.
     *
     * @param child the child to remove
     */
    protected final void detachChild(Node child) {
        getChildren().remove(child);
        if (getChildren().isEmpty()) {
            uninstall();
        }
    }

    private void installAsChildOf(Pane parent) {
        this.mode = InstallMode.PANE_CHILD;
        this.fillParent = parent;
        setManaged(false);
        // Track the parent's size manually: a managed=false child is ignored by the
        // parent's layout, so it must fill the parent itself and follow resizes.
        fillListener = observable -> fillToParent();
        parent.widthProperty().addListener(fillListener);
        parent.heightProperty().addListener(fillListener);
        parent.getChildren().add(this);
        fillToParent();
    }

    private void fillToParent() {
        resizeRelocate(0.0, 0.0, fillParent.getWidth(), fillParent.getHeight());
    }

    private void installByWrapping(Scene targetScene, Parent root) {
        this.mode = InstallMode.WRAP;
        this.originalRoot = root;
        // Inside the wrapping StackPane this layer is a normal, filled child.
        setManaged(true);
        StackPane wrapper = new StackPane(root, this);
        targetScene.setRoot(wrapper);
    }

    /**
     * Unmounts this layer, restores a wrapped root, and evicts the layer from the
     * scene's property map. Safe to call on an already-uninstalled layer.
     */
    protected final void uninstall() {
        if (uninstalling) {
            return;
        }
        uninstalling = true;
        try {
            if (scene != null) {
                scene.getProperties().remove(sceneKey);
            }
            if (mode == InstallMode.PANE_CHILD && fillParent != null) {
                fillParent.widthProperty().removeListener(fillListener);
                fillParent.heightProperty().removeListener(fillListener);
                fillParent.getChildren().remove(this);
            } else if (mode == InstallMode.WRAP && scene != null && originalRoot != null) {
                // Only restore if our wrapper is still the root: if the app swapped the
                // scene root while this layer was mounted, leave its new root alone.
                if (scene.getRoot() instanceof Pane wrapper && wrapper.getChildren().contains(originalRoot)) {
                    wrapper.getChildren().remove(originalRoot);
                    scene.setRoot(originalRoot);
                }
            }
            scene = null;
            fillParent = null;
            fillListener = null;
            originalRoot = null;
            mode = null;
        } finally {
            uninstalling = false;
        }
    }
}
