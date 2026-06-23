package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.RXDialog;

import javafx.beans.InvalidationListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

/**
 * Per-scene overlay host for {@link RXDialog} instances. One layer per scene
 * (cached in the scene's property map) carries the stack of open dialogs; each
 * dialog fills the layer (so its skin can paint a full-bleed scrim and a centered
 * card), and stacking, focus, and ESC are scoped to the top-most dialog by the
 * dialogs' own skins.
 *
 * <p>Installation adapts to the root: if the scene root (or an explicit container) is
 * a {@link Pane}, the layer is added as a {@code managed = false} child sized to fill
 * it (so layout panes such as {@code BorderPane} do not reserve space for it); if
 * the root is not an addable {@code Pane}, the original root is wrapped in a
 * {@link StackPane} as a fallback. The layer uninstalls itself when its last dialog
 * detaches.</p>
 *
 * <p>This class is driven by {@link RXDialog} (show attaches, hide-completed
 * detaches), not by the skin — keeping scene-graph mounting out of the skin.</p>
 */
public final class RXDialogLayer extends StackPane {

    private enum InstallMode {
        /** Added as a managed=false overlay child of a container / root pane. */
        PANE_CHILD,
        /** The original root was wrapped in a StackPane holding it and this layer. */
        WRAP
    }

    private static final Object LAYER_KEY = new Object();

    private Scene scene;
    private InstallMode mode;
    private Pane fillParent;
    private InvalidationListener fillListener;
    private Parent originalRoot;

    private RXDialogLayer() {
        // Empty areas (no scrim / card under the cursor) stay click-through, so a
        // non-modal dialog leaves the rest of the scene interactive.
        setPickOnBounds(false);
    }

    /**
     * Attaches the dialog to its scene's overlay layer (creating and installing the
     * layer on first use) and pushes it to the top of the stack.
     *
     * @param dialog    the dialog to show
     * @param container the explicit container to mount the layer into, or {@code null} to
     *                  resolve the scene from the dialog's owner and mount at the scene root
     * @throws IllegalStateException if neither the container nor the dialog's owner is in a
     *                               scene, or an explicit container is given but the scene
     *                               already has an overlay layer mounted elsewhere
     */
    public static void attach(RXDialog<?> dialog, Pane container) {
        // If a previous show left the dialog parented in a layer (e.g. re-show during
        // a still-running close transition, possibly onto a different scene), remove it
        // from there first — so it is never added twice and the old layer never leaks.
        if (dialog.getParent() instanceof RXDialogLayer current) {
            current.getChildren().remove(dialog);
            if (current.getChildren().isEmpty()) {
                current.uninstall();
            }
        }
        Scene targetScene = container != null
                ? container.getScene()
                : (dialog.getOwner() != null ? dialog.getOwner().getScene() : null);
        if (targetScene == null) {
            throw new IllegalStateException(
                    "RXDialog.show requires an owner or container already attached to a Scene");
        }
        RXDialogLayer layer = (RXDialogLayer) targetScene.getProperties().get(LAYER_KEY);
        if (layer == null) {
            layer = new RXDialogLayer();
            layer.install(targetScene, container);
            targetScene.getProperties().put(LAYER_KEY, layer);
        } else if (container != null && container != layer.fillParent) {
            // One overlay layer per scene carries all that scene's dialogs (so they stack,
            // share a single scrim, and trap focus together), so an explicit container is honored
            // only by the dialog that first installs the layer. Rather than silently mounting a
            // later, differently-mounted dialog into the existing layer, fail loudly. (A WRAP-mode
            // layer has a null fillParent, so any explicit container conflicts with it.)
            throw new IllegalStateException(
                    "RXDialog: this scene already has a dialog overlay mounted elsewhere; an "
                            + "explicit container is honored only by the first dialog shown over a "
                            + "scene (stacked dialogs share one overlay). Show this dialog after "
                            + "the others close, or give it the same container / no container.");
        }
        layer.getChildren().add(dialog);
    }

    /**
     * Detaches the dialog from its overlay layer, uninstalling the layer when it
     * becomes empty. A no-op if the dialog is not currently hosted by a layer.
     *
     * @param dialog the dialog to detach
     */
    public static void detach(RXDialog<?> dialog) {
        if (dialog.getParent() instanceof RXDialogLayer layer) {
            layer.getChildren().remove(dialog);
            if (layer.getChildren().isEmpty()) {
                layer.uninstall();
            }
        }
    }

    private void install(Scene targetScene, Pane container) {
        this.scene = targetScene;
        if (container != null) {
            installAsChildOf(container);
            return;
        }
        Parent root = targetScene.getRoot();
        if (root instanceof Pane pane) {
            installAsChildOf(pane);
        } else {
            installByWrapping(targetScene, root);
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

    private void uninstall() {
        if (scene != null) {
            scene.getProperties().remove(LAYER_KEY);
        }
        if (mode == InstallMode.PANE_CHILD && fillParent != null) {
            fillParent.widthProperty().removeListener(fillListener);
            fillParent.heightProperty().removeListener(fillListener);
            fillParent.getChildren().remove(this);
        } else if (mode == InstallMode.WRAP && scene != null && originalRoot != null) {
            // Only restore if our wrapper is still the root: if the app swapped the
            // scene root while a wrapped dialog was open, leave its new root alone.
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
    }
}
