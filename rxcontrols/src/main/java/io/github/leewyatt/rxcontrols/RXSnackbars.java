package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXSnackbarLayer;
import io.github.leewyatt.rxcontrols.RXSnackbarHost.DismissReason;

import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Convenience entry point for snackbars — the counterpart of {@code RXDialogs}.
 * Every method resolves the owner node's snackbar scope: the nearest host
 * installed on the owner or one of its ancestors via {@link #installInto(Pane)},
 * or otherwise the scene-wide host, lazily installed (and reused) through an
 * overlay layer. Showing a message therefore needs no setup:
 *
 * <pre>{@code
 * RXSnackbars.show(anyNode, "File saved");
 * RXSnackbars.error(anyNode, "Upload failed");
 * RXSnackbars.show(anyNode, RXSnackbarRequest.builder("File deleted")
 *         .action("Undo", () -> restore(file))
 *         .build());
 * }</pre>
 *
 * <p><b>Scoped hosts.</b> {@link #installInto(Pane)} mounts a host inside a
 * specific container. Facade calls whose owner is that container or one of its
 * descendants route to it — the nearest enclosing host wins — while owners
 * elsewhere keep using the scene-wide host. The owner argument thus selects
 * where the bar appears, not just which scene it belongs to. To force a
 * scene-wide bar from inside a scoped container, show it on
 * {@code installInto(owner.getScene())} directly.</p>
 *
 * <p><b>Owner without a scope.</b> When the owner has no enclosing installed
 * host and no live scene, the request is never accepted by any host: the void
 * convenience methods are silent no-ops, and a request carrying an
 * {@code onDismissed} callback is settled immediately with
 * {@link DismissReason#DISCARDED} so the caller's callback never hangs.</p>
 *
 * <p>Scene-wide behavior — position, margin, animation, queue policy — lives on
 * the per-scene {@link RXSnackbarHost}. {@link #installInto(Scene)} resolves that
 * host (installing it if needed) so it can be configured once, typically at
 * startup:
 *
 * <pre>{@code
 * RXSnackbars.installInto(scene).setPosition(Pos.TOP_CENTER);
 * }</pre>
 */
public final class RXSnackbars {

    // Per-container cache key for installInto (kept in Pane.getProperties()).
    private static final Object INSTALL_KEY = new Object();

    private RXSnackbars() {
    }

    /**
     * Shows the request in the owner's snackbar scope: the nearest host
     * installed on the owner or one of its ancestors via
     * {@link #installInto(Pane)}, or the owner's scene-wide host otherwise
     * (installed lazily). A no-op when the owner has neither an enclosing
     * installed host nor a live scene (an {@code onDismissed} callback is then
     * settled immediately with {@link DismissReason#DISCARDED}).
     *
     * @param owner   the node whose snackbar scope receives the request
     * @param request the request to show
     * @throws NullPointerException if {@code owner} or {@code request} is {@code null}
     */
    public static void show(Node owner, RXSnackbarRequest request) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(request, "request");
        RXSnackbarHost scoped = scopedHostFor(owner);
        if (scoped != null) {
            scoped.show(request);
            return;
        }
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
     * Shows a plain text snackbar in the owner's snackbar scope
     * (see {@link #show(Node, RXSnackbarRequest)}). A no-op when the owner has
     * neither an enclosing installed host nor a live scene.
     *
     * @param owner   the node whose snackbar scope receives the request
     * @param message the message text
     * @throws NullPointerException if {@code owner} is {@code null}
     */
    public static void show(Node owner, String message) {
        show(owner, RXSnackbarRequest.builder(message).build());
    }

    /**
     * Shows a success-tinted snackbar in the owner's snackbar scope
     * (see {@link #show(Node, RXSnackbarRequest)}). A no-op when the owner has
     * neither an enclosing installed host nor a live scene.
     *
     * @param owner   the node whose snackbar scope receives the request
     * @param message the message text
     * @throws NullPointerException if {@code owner} is {@code null}
     */
    public static void success(Node owner, String message) {
        show(owner, RXSnackbarRequest.builder(message).severity(RXSnackbarSeverity.SUCCESS).build());
    }

    /**
     * Shows an info-tinted snackbar in the owner's snackbar scope
     * (see {@link #show(Node, RXSnackbarRequest)}). A no-op when the owner has
     * neither an enclosing installed host nor a live scene.
     *
     * @param owner   the node whose snackbar scope receives the request
     * @param message the message text
     * @throws NullPointerException if {@code owner} is {@code null}
     */
    public static void info(Node owner, String message) {
        show(owner, RXSnackbarRequest.builder(message).severity(RXSnackbarSeverity.INFO).build());
    }

    /**
     * Shows a warning-tinted snackbar in the owner's snackbar scope
     * (see {@link #show(Node, RXSnackbarRequest)}). A no-op when the owner has
     * neither an enclosing installed host nor a live scene.
     *
     * @param owner   the node whose snackbar scope receives the request
     * @param message the message text
     * @throws NullPointerException if {@code owner} is {@code null}
     */
    public static void warning(Node owner, String message) {
        show(owner, RXSnackbarRequest.builder(message).severity(RXSnackbarSeverity.WARNING).build());
    }

    /**
     * Shows an error-tinted snackbar in the owner's snackbar scope
     * (see {@link #show(Node, RXSnackbarRequest)}). A no-op when the owner has
     * neither an enclosing installed host nor a live scene.
     *
     * @param owner   the node whose snackbar scope receives the request
     * @param message the message text
     * @throws NullPointerException if {@code owner} is {@code null}
     */
    public static void error(Node owner, String message) {
        show(owner, RXSnackbarRequest.builder(message).severity(RXSnackbarSeverity.ERROR).build());
    }

    /**
     * Dismisses the request carrying the given key on the host of the owner's
     * snackbar scope (the nearest enclosing installed host, or the scene's host
     * otherwise).
     *
     * @param owner the node whose snackbar scope is searched
     * @param key   the request key to match
     * @return {@code true} if a host exists and a displayed or pending request matched
     * @throws NullPointerException if {@code owner} is {@code null}
     */
    public static boolean dismiss(Node owner, String key) {
        Objects.requireNonNull(owner, "owner");
        RXSnackbarHost scoped = scopedHostFor(owner);
        if (scoped != null) {
            return scoped.dismiss(key);
        }
        Scene scene = owner.getScene();
        if (scene == null) {
            return false;
        }
        RXSnackbarHost host = RXSnackbarLayer.peekHost(scene);
        return host != null && host.dismiss(key);
    }

    /**
     * Returns the host currently resolvable for the owner's snackbar scope: the
     * nearest host installed on the owner or one of its ancestors via
     * {@link #installInto(Pane)}, or else the host already installed over the
     * owner's scene. Never installs one — an empty result means a {@code show}
     * with this owner would lazily install the scene-wide host.
     *
     * @param owner the node whose snackbar scope is resolved
     * @return the scope's host, or empty when none is installed yet
     * @throws NullPointerException if {@code owner} is {@code null}
     */
    public static Optional<RXSnackbarHost> hostFor(Node owner) {
        Objects.requireNonNull(owner, "owner");
        RXSnackbarHost scoped = scopedHostFor(owner);
        if (scoped != null) {
            return Optional.of(scoped);
        }
        Scene scene = owner.getScene();
        if (scene == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(RXSnackbarLayer.peekHost(scene));
    }

    /**
     * Installs (or returns the previously installed) snackbar host over the given
     * scene — the same host the {@code show} methods resolve lazily, so every
     * subsequent facade call on nodes of that scene (outside any pane-scoped
     * host) routes to it. Idempotent per scene. Use it to configure scene-wide
     * behavior (position, margin, queue policy, animation) once, before or after
     * the first message is shown.
     *
     * @param scene the scene to host snackbars over
     * @return the scene's snackbar host
     * @throws NullPointerException if {@code scene} is {@code null}
     */
    public static RXSnackbarHost installInto(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        return RXSnackbarLayer.hostFor(scene);
    }

    /**
     * Installs (or returns the previously installed) snackbar host as a child of
     * the given container, scoping snackbars to that sub-area: facade calls
     * whose owner is the container or one of its descendants route to this host
     * instead of the scene-wide one (the nearest enclosing host wins). The host
     * is an unmanaged overlay covering the container's full bounds — it neither
     * joins the container's layout flow nor affects its preferred size, so any
     * {@code Pane} subclass works, including flow layouts like {@code VBox}.
     * Idempotent per container. The container does not need to be in a scene
     * yet; the caller owns the host's lifecycle and may remove it like any
     * child.
     *
     * @param container the pane to host snackbars in
     * @return the container's snackbar host
     * @throws NullPointerException if {@code container} is {@code null}
     */
    public static RXSnackbarHost installInto(Pane container) {
        Objects.requireNonNull(container, "container");
        Object cached = container.getProperties().get(INSTALL_KEY);
        if (cached instanceof RXSnackbarHost host && host.getParent() == container) {
            return host;
        }
        RXSnackbarHost host = new RXSnackbarHost();
        // Unmanaged overlay: the container's own layout must not place the host
        // (a flow layout like VBox would slot it into the flow), and the host
        // must not inflate the container's preferred size. Geometry is synced
        // explicitly instead; an unmanaged control is its own layout root, so
        // the bar still lays out inside on pulses.
        host.setManaged(false);
        InvalidationListener geometrySync = observable ->
                host.resize(container.getWidth(), container.getHeight());
        container.widthProperty().addListener(geometrySync);
        container.heightProperty().addListener(geometrySync);
        host.resize(container.getWidth(), container.getHeight());
        container.getProperties().put(INSTALL_KEY, host);
        // The caller owns the host's lifecycle: when it leaves the container (a
        // removal or reparent), evict the idempotence cache and drop the geometry
        // listeners so the container does not pin the removed host. One-shot: the
        // listener removes itself after cleaning up, so a reparented (still live)
        // host does not keep pinning the old container tree through it.
        host.parentProperty().addListener(new ChangeListener<Parent>() {
            @Override
            public void changed(ObservableValue<? extends Parent> observable, Parent oldParent, Parent newParent) {
                if (newParent != container) {
                    container.getProperties().remove(INSTALL_KEY, host);
                    container.widthProperty().removeListener(geometrySync);
                    container.heightProperty().removeListener(geometrySync);
                    observable.removeListener(this);
                }
            }
        });
        container.getChildren().add(host);
        return host;
    }

    // Walks up from the owner (inclusive — the owner may be the installed
    // container itself) to the nearest live installInto host. hasProperties()
    // first: getProperties() would lazily create a map on every ancestor.
    private static RXSnackbarHost scopedHostFor(Node owner) {
        for (Node node = owner; node != null; node = node.getParent()) {
            if (node.hasProperties()
                    && node.getProperties().get(INSTALL_KEY) instanceof RXSnackbarHost host
                    && host.getParent() == node) {
                return host;
            }
        }
        return null;
    }
}
