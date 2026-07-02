package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.RXDialog;

import javafx.scene.Scene;
import javafx.scene.layout.Pane;

/**
 * Per-scene overlay host for {@link RXDialog} instances. One layer per scene
 * (cached in the scene's property map) carries the stack of open dialogs; each
 * dialog fills the layer (so its skin can paint a full-bleed scrim and a centered
 * card), and stacking, focus, and ESC are scoped to the top-most dialog by the
 * dialogs' own skins.
 *
 * <p>Mounting, fill tracking, and empty-uninstall come from
 * {@link RXSceneOverlayLayer}. The dialog layer renders in front of every other
 * overlay layer type (its viewOrder is the lowest), so dialogs always cover, for
 * example, snackbars — regardless of which layer was installed first.</p>
 *
 * <p>This class is driven by {@link RXDialog} (show attaches, hide-completed
 * detaches), not by the skin — keeping scene-graph mounting out of the skin.</p>
 */
public final class RXDialogLayer extends RXSceneOverlayLayer {

    private static final Object LAYER_KEY = new Object();

    // Dialogs stack above every other overlay layer (lower viewOrder = in front).
    private static final double VIEW_ORDER = -20.0;

    private RXDialogLayer() {
        super(LAYER_KEY, VIEW_ORDER);
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
            current.detachChild(dialog);
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
            layer.installInto(targetScene, container);
        } else if (container != null && container != layer.getFillParent()) {
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
            layer.detachChild(dialog);
        }
    }
}
