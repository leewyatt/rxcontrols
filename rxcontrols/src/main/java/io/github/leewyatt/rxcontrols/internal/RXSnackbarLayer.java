package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.RXSnackbarHost;

import javafx.scene.Scene;

import java.util.Objects;

/**
 * Per-scene overlay layer carrying that scene's single {@link RXSnackbarHost}.
 * Unlike the dialog layer, the host is long-lived: it mounts on first use and
 * stays installed (a snackbar host repeatedly shows and hides bars, and
 * re-mounting per message would churn a WRAP-mode root). The layer renders
 * behind the dialog layer (higher viewOrder), so dialogs always cover snackbars.
 */
public final class RXSnackbarLayer extends RXSceneOverlayLayer {

    private static final Object LAYER_KEY = new Object();

    // Behind the dialog layer (-20), in front of default app content (0).
    private static final double VIEW_ORDER = -10.0;

    private RXSnackbarHost host;

    private RXSnackbarLayer() {
        super(LAYER_KEY, VIEW_ORDER);
    }

    /**
     * Returns the scene's snackbar host, creating and mounting it (with its
     * layer) on first use. A cached layer that lost its scene (for example its
     * mount point was removed, or another layer's WRAP root was restored around
     * it) is discarded and rebuilt, so the returned host is always live in the
     * given scene.
     *
     * @param scene the scene to host snackbars over
     * @return the scene's live snackbar host
     */
    public static RXSnackbarHost hostFor(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        RXSnackbarHost live = peekHost(scene);
        if (live != null) {
            return live;
        }
        RXSnackbarLayer stale = (RXSnackbarLayer) scene.getProperties().get(LAYER_KEY);
        if (stale != null) {
            // Orphaned off-scene; its host already settled its requests when it
            // left the scene. Unhook whatever is left and rebuild fresh.
            stale.uninstall();
        }
        RXSnackbarLayer fresh = new RXSnackbarLayer();
        RXSnackbarHost freshHost = new RXSnackbarHost();
        fresh.host = freshHost;
        fresh.installInto(scene, null);
        fresh.getChildren().add(freshHost);
        return freshHost;
    }

    /**
     * Returns the scene's snackbar host only if one is already installed and
     * still live in the scene; never creates one.
     *
     * @param scene the scene to look in
     * @return the live host, or {@code null} when none is installed
     */
    public static RXSnackbarHost peekHost(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        RXSnackbarLayer layer = (RXSnackbarLayer) scene.getProperties().get(LAYER_KEY);
        if (layer != null && layer.host != null && layer.host.getScene() == scene) {
            return layer.host;
        }
        return null;
    }
}
