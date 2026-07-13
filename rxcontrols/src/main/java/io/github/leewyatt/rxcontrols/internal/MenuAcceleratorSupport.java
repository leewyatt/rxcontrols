package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.RXMenuItem;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCombination;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Registers command-menu item accelerators as real scene shortcuts for as long as
 * an owner node is in a shown scene, mirroring the platform
 * {@code ControlAcceleratorSupport} without depending on its {@code com.sun}
 * internals.
 *
 * <p>Each item that carries a {@link RXMenuItem#getAccelerator() accelerator} is
 * put into the owner's {@code scene.getAccelerators()} while the owner is in a
 * scene; when the owner leaves the scene (or {@link #dispose()} is called) every
 * registration is removed. Removing on detach is essential — a leaked entry would
 * leave a dead global shortcut wired to a disposed menu. Item-list changes and
 * per-item accelerator changes re-sync automatically.
 *
 * <p>When a registered accelerator fires, the supplied consumer is invoked with
 * the item; the consumer owns the activation semantics (disabled guard, checkbox /
 * radio toggle, firing, and closing an open popup with the accelerator reason).
 *
 * <p>Not thread-safe; use on the JavaFX Application Thread.
 */
public final class MenuAcceleratorSupport {

    private final Node owner;
    private final ObservableList<RXMenuItem> items;
    private final Consumer<RXMenuItem> onAccelerator;

    private final ChangeListener<Scene> sceneListener = (obs, oldScene, newScene) -> setScene(newScene);
    private final ListChangeListener<RXMenuItem> itemsListener = change -> resync();

    private final Map<RXMenuItem, KeyCombination> registeredCombos = new IdentityHashMap<>();
    private final Map<RXMenuItem, Runnable> registeredRunnables = new IdentityHashMap<>();
    private final Map<RXMenuItem, InvalidationListener> acceleratorListeners = new IdentityHashMap<>();

    private Scene scene;
    private boolean disposed;

    /**
     * Creates the support and immediately registers against the owner's current
     * scene (if any).
     *
     * @param owner         the node whose scene hosts the accelerators and receives
     *                      lifecycle tracking
     * @param items         the menu items whose accelerators are registered
     * @param onAccelerator invoked with an item when its accelerator fires
     * @throws NullPointerException if any argument is {@code null}
     */
    public MenuAcceleratorSupport(Node owner, ObservableList<RXMenuItem> items,
                                  Consumer<RXMenuItem> onAccelerator) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.items = Objects.requireNonNull(items, "items");
        this.onAccelerator = Objects.requireNonNull(onAccelerator, "onAccelerator");
        owner.sceneProperty().addListener(sceneListener);
        items.addListener(itemsListener);
        setScene(owner.getScene());
    }

    private void setScene(Scene newScene) {
        if (scene == newScene) {
            return;
        }
        unregisterAll();
        scene = newScene;
        registerAll();
    }

    private void resync() {
        unregisterAll();
        registerAll();
    }

    private void registerAll() {
        if (scene == null) {
            return;
        }
        for (RXMenuItem item : items) {
            // A value-object item may legally appear more than once; register a
            // single listener per instance so unregisterAll() stays symmetric.
            if (acceleratorListeners.containsKey(item)) {
                continue;
            }
            InvalidationListener listener = observable -> reregister(item);
            item.acceleratorProperty().addListener(listener);
            acceleratorListeners.put(item, listener);
            register(item);
        }
    }

    private void register(RXMenuItem item) {
        KeyCombination combo = item.getAccelerator();
        if (scene == null || combo == null) {
            return;
        }
        Runnable runnable = () -> onAccelerator.accept(item);
        scene.getAccelerators().put(combo, runnable);
        registeredCombos.put(item, combo);
        registeredRunnables.put(item, runnable);
    }

    private void reregister(RXMenuItem item) {
        unregister(item);
        register(item);
    }

    private void unregister(RXMenuItem item) {
        KeyCombination combo = registeredCombos.remove(item);
        Runnable runnable = registeredRunnables.remove(item);
        if (scene != null && combo != null && runnable != null) {
            // Remove only our own mapping so a colliding combo owned elsewhere stays.
            scene.getAccelerators().remove(combo, runnable);
        }
    }

    private void unregisterAll() {
        for (Map.Entry<RXMenuItem, InvalidationListener> entry : acceleratorListeners.entrySet()) {
            entry.getKey().acceleratorProperty().removeListener(entry.getValue());
        }
        acceleratorListeners.clear();
        for (RXMenuItem item : new ArrayList<>(registeredCombos.keySet())) {
            unregister(item);
        }
        registeredCombos.clear();
        registeredRunnables.clear();
    }

    /**
     * Unwires all scene and item listeners and removes every accelerator
     * registration. Idempotent.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        owner.sceneProperty().removeListener(sceneListener);
        items.removeListener(itemsListener);
        unregisterAll();
        scene = null;
    }
}
