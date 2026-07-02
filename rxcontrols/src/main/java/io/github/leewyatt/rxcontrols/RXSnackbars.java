package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXSnackbarLayer;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Convenience entry point for snackbars — the counterpart of {@code RXDialogs}.
 * Every method resolves the owner node's scene and lazily installs (or reuses)
 * that scene's single {@link RXSnackbarHost} through an overlay layer, so showing
 * a message needs no setup:
 *
 * <pre>{@code
 * RXSnackbars.show(anyNode, "File saved");
 * RXSnackbars.error(anyNode, "Upload failed");
 * RXSnackbars.show(anyNode, RXSnackbarRequest.builder("File deleted")
 *         .action("Undo", () -> restore(file))
 *         .build());
 * }</pre>
 *
 * <p><b>Owner without a live scene.</b> When the owner is not attached to a
 * scene, the request is never accepted by any host: the void convenience methods
 * are silent no-ops, and a request carrying an {@code onDismissed} callback is
 * settled immediately with {@link DismissReason#DISCARDED} so the caller's
 * callback never hangs.</p>
 *
 * <p>{@link #installInto(Pane)} is the escape hatch for hosting snackbars inside
 * a specific pane instead of over the whole scene. Such a host is not entered
 * into the scene cache and this facade never routes to it — show messages on the
 * returned host directly. It is idempotent per container, and the caller owns
 * the host's lifecycle (it is a plain child of the container).</p>
 */
public final class RXSnackbars {

    // Per-container cache key for installInto (kept in Pane.getProperties()).
    private static final Object INSTALL_KEY = new Object();

    private RXSnackbars() {
    }

    /**
     * Shows the request over the owner's scene. A no-op when the owner has no
     * live scene (an {@code onDismissed} callback is then settled immediately
     * with {@link DismissReason#DISCARDED}).
     *
     * @param owner   a node in the target scene
     * @param request the request to show
     */
    public static void show(Node owner, RXSnackbarRequest request) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(request, "request");
        Scene scene = owner.getScene();
        if (scene == null) {
            // Never accepted by a host; settle the callback so it cannot hang.
            BiConsumer<RXSnackbarRequest, DismissReason> callback = request.getOnDismissed();
            if (callback != null) {
                callback.accept(request, DismissReason.DISCARDED);
            }
            return;
        }
        RXSnackbarLayer.hostFor(scene).show(request);
    }

    /**
     * Shows a plain text snackbar over the owner's scene. A no-op when the owner
     * has no live scene.
     *
     * @param owner   a node in the target scene
     * @param message the message text
     */
    public static void show(Node owner, String message) {
        show(owner, RXSnackbarRequest.builder(message).build());
    }

    /**
     * Shows a success-tinted snackbar. A no-op when the owner has no live scene.
     *
     * @param owner   a node in the target scene
     * @param message the message text
     */
    public static void success(Node owner, String message) {
        show(owner, RXSnackbarRequest.builder(message).severity(RXSnackbarSeverity.SUCCESS).build());
    }

    /**
     * Shows an info-tinted snackbar. A no-op when the owner has no live scene.
     *
     * @param owner   a node in the target scene
     * @param message the message text
     */
    public static void info(Node owner, String message) {
        show(owner, RXSnackbarRequest.builder(message).severity(RXSnackbarSeverity.INFO).build());
    }

    /**
     * Shows a warning-tinted snackbar. A no-op when the owner has no live scene.
     *
     * @param owner   a node in the target scene
     * @param message the message text
     */
    public static void warning(Node owner, String message) {
        show(owner, RXSnackbarRequest.builder(message).severity(RXSnackbarSeverity.WARNING).build());
    }

    /**
     * Shows an error-tinted snackbar. A no-op when the owner has no live scene.
     *
     * @param owner   a node in the target scene
     * @param message the message text
     */
    public static void error(Node owner, String message) {
        show(owner, RXSnackbarRequest.builder(message).severity(RXSnackbarSeverity.ERROR).build());
    }

    /**
     * Dismisses the request carrying the given key on the owner's scene's host.
     *
     * @param owner a node in the target scene
     * @param key   the request key to match
     * @return {@code true} if a host exists and a displayed or pending request matched
     */
    public static boolean dismiss(Node owner, String key) {
        Objects.requireNonNull(owner, "owner");
        Scene scene = owner.getScene();
        if (scene == null) {
            return false;
        }
        RXSnackbarHost host = RXSnackbarLayer.peekHost(scene);
        return host != null && host.dismiss(key);
    }

    /**
     * Returns the snackbar host already installed over the owner's scene, if any.
     * Never installs one — use the {@code show} methods for that.
     *
     * @param owner a node in the target scene
     * @return the scene's host, or empty when the owner has no scene or no host exists
     */
    public static Optional<RXSnackbarHost> hostFor(Node owner) {
        Objects.requireNonNull(owner, "owner");
        Scene scene = owner.getScene();
        if (scene == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(RXSnackbarLayer.peekHost(scene));
    }

    /**
     * Installs (or returns the previously installed) snackbar host as a child of
     * the given container, for snackbars scoped to a sub-area instead of the
     * whole scene. Idempotent per container. The host is not entered into the
     * scene cache and this facade never routes to it — call
     * {@link RXSnackbarHost#show(RXSnackbarRequest) show} on the returned host
     * directly. The container does not need to be in a scene yet; the caller owns
     * the host's lifecycle and may remove it like any child.
     *
     * @param container the pane to host snackbars in
     * @return the container's snackbar host
     */
    public static RXSnackbarHost installInto(Pane container) {
        Objects.requireNonNull(container, "container");
        Object cached = container.getProperties().get(INSTALL_KEY);
        if (cached instanceof RXSnackbarHost host && host.getParent() == container) {
            return host;
        }
        RXSnackbarHost host = new RXSnackbarHost();
        // A plain Pane autosizes children to their pref size; follow the container
        // so the bar can position against the container's full area. (Bindings use
        // weak listeners internally, so a removed host stays collectable.)
        host.prefWidthProperty().bind(container.widthProperty());
        host.prefHeightProperty().bind(container.heightProperty());
        container.getProperties().put(INSTALL_KEY, host);
        container.getChildren().add(host);
        return host;
    }
}
