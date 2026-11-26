package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXSnackbarHost.DismissReason;

import javafx.scene.Node;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Convenience entry point for snackbars — the counterpart of {@code RXDialogs}.
 * Every method shows in the snackbar host of the nearest {@link RXOverlayPane}
 * above the owner node, so with the scene root wrapped in an overlay pane,
 * showing a message needs nothing else:
 *
 * <pre>{@code
 * RXSnackbars.show(anyNode, "File saved");
 * RXSnackbars.error(anyNode, "Upload failed");
 * RXSnackbars.show(anyNode, RXSnackbarRequest.builder("File deleted")
 *         .action("Undo", () -> restore(file))
 *         .build());
 * }</pre>
 *
 * <p><b>Where the bar appears.</b> The owner selects the overlay pane, not
 * just the window: an owner inside a nested overlay pane shows in that pane.
 * To show further out, pass a node outside the inner pane, or the pane
 * returned by {@link RXOverlayPane#findOutermost(Node)}.</p>
 *
 * <p><b>Owner without an overlay pane.</b> An owner that is in a scene with no
 * overlay pane above it is a setup mistake, and the {@code show} methods throw
 * an {@link IllegalStateException} that says how to fix it. An owner that is
 * not in a scene and has no overlay pane above it is a passing state (a
 * callback returning after its page was closed): the request is never
 * accepted, the void convenience methods are silent no-ops, and a request
 * carrying an {@code onDismissed} callback is settled immediately with
 * {@link DismissReason#DISCARDED}, so the caller's callback never hangs.</p>
 *
 * <p>Position, margin, animation and queue policy live on the pane's
 * {@link RXSnackbarHost}; get it from the pane to configure it once,
 * typically at startup:
 *
 * <pre>{@code
 * overlayPane.getOrCreateSnackbarHost().setPosition(Pos.TOP_CENTER);
 * }</pre>
 */
public final class RXSnackbars {

    private RXSnackbars() {
    }

    /**
     * Shows the request in the snackbar host of the nearest overlay pane above
     * the owner. A no-op when the owner has no overlay pane above it and is not
     * in a scene (an {@code onDismissed} callback is then settled immediately
     * with {@link DismissReason#DISCARDED}).
     *
     * @param owner   the node whose overlay pane receives the request
     * @param request the request to show
     * @throws NullPointerException  if {@code owner} or {@code request} is {@code null}
     * @throws IllegalStateException if the owner is in a scene and has no overlay pane
     *                               above it
     */
    public static void show(Node owner, RXSnackbarRequest request) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(request, "request");
        RXOverlayPane pane = RXOverlayPane.nearest(owner, true);
        if (pane != null) {
            pane.getOrCreateSnackbarHost().show(request);
            return;
        }
        if (owner.getScene() != null) {
            throw new IllegalStateException(RXOverlayPane.missingPaneMessage("RXSnackbars.show", "owner"));
        }
        // Never accepted by a host; settle the callback so it cannot hang.
        BiConsumer<RXSnackbarRequest, DismissReason> callback = request.getOnDismissed();
        if (callback != null) {
            callback.accept(request, DismissReason.DISCARDED);
        }
    }

    /**
     * Shows a plain text snackbar (see {@link #show(Node, RXSnackbarRequest)}).
     *
     * @param owner   the node whose overlay pane receives the request
     * @param message the message text
     * @throws NullPointerException  if {@code owner} is {@code null}
     * @throws IllegalStateException if the owner is in a scene and has no overlay pane
     *                               above it
     */
    public static void show(Node owner, String message) {
        show(owner, RXSnackbarRequest.builder(message).build());
    }

    /**
     * Shows a success-tinted snackbar (see {@link #show(Node, RXSnackbarRequest)}).
     *
     * @param owner   the node whose overlay pane receives the request
     * @param message the message text
     * @throws NullPointerException  if {@code owner} is {@code null}
     * @throws IllegalStateException if the owner is in a scene and has no overlay pane
     *                               above it
     */
    public static void success(Node owner, String message) {
        show(owner, RXSnackbarRequest.builder(message).severity(RXSnackbarSeverity.SUCCESS).build());
    }

    /**
     * Shows an info-tinted snackbar (see {@link #show(Node, RXSnackbarRequest)}).
     *
     * @param owner   the node whose overlay pane receives the request
     * @param message the message text
     * @throws NullPointerException  if {@code owner} is {@code null}
     * @throws IllegalStateException if the owner is in a scene and has no overlay pane
     *                               above it
     */
    public static void info(Node owner, String message) {
        show(owner, RXSnackbarRequest.builder(message).severity(RXSnackbarSeverity.INFO).build());
    }

    /**
     * Shows a warning-tinted snackbar (see {@link #show(Node, RXSnackbarRequest)}).
     *
     * @param owner   the node whose overlay pane receives the request
     * @param message the message text
     * @throws NullPointerException  if {@code owner} is {@code null}
     * @throws IllegalStateException if the owner is in a scene and has no overlay pane
     *                               above it
     */
    public static void warning(Node owner, String message) {
        show(owner, RXSnackbarRequest.builder(message).severity(RXSnackbarSeverity.WARNING).build());
    }

    /**
     * Shows an error-tinted snackbar (see {@link #show(Node, RXSnackbarRequest)}).
     *
     * @param owner   the node whose overlay pane receives the request
     * @param message the message text
     * @throws NullPointerException  if {@code owner} is {@code null}
     * @throws IllegalStateException if the owner is in a scene and has no overlay pane
     *                               above it
     */
    public static void error(Node owner, String message) {
        show(owner, RXSnackbarRequest.builder(message).severity(RXSnackbarSeverity.ERROR).build());
    }

    /**
     * Dismisses the request carrying the given key in the snackbar host of the
     * nearest overlay pane above the owner.
     *
     * @param owner the node whose overlay pane is searched
     * @param key   the request key to match
     * @return {@code true} if that pane has a snackbar host and a displayed or pending
     *         request matched
     * @throws NullPointerException if {@code owner} is {@code null}
     */
    public static boolean dismiss(Node owner, String key) {
        Objects.requireNonNull(owner, "owner");
        RXSnackbarHost host = createdHostFor(owner);
        return host != null && host.dismiss(key);
    }

    /**
     * Returns the snackbar host a {@code show} with this owner goes to, if it
     * exists: the host of the nearest overlay pane above the owner. Never
     * creates one — the result is empty when there is no overlay pane above the
     * owner, or when that pane has not created its host yet (nothing was shown
     * in it and {@link RXOverlayPane#getOrCreateSnackbarHost()} was not called).
     *
     * @param owner the node whose overlay pane is searched
     * @return the snackbar host, or empty when there is none yet
     * @throws NullPointerException if {@code owner} is {@code null}
     */
    public static Optional<RXSnackbarHost> hostFor(Node owner) {
        Objects.requireNonNull(owner, "owner");
        return Optional.ofNullable(createdHostFor(owner));
    }

    private static RXSnackbarHost createdHostFor(Node owner) {
        RXOverlayPane pane = RXOverlayPane.nearest(owner, true);
        return pane == null ? null : pane.createdSnackbarHost();
    }
}
