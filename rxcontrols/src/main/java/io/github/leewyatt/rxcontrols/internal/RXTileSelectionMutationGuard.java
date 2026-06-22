package io.github.leewyatt.rxcontrols.internal;

import javafx.scene.control.MultipleSelectionModel;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Thread-local guard used while {@code RXTileSelectionModel} updates selection
 * in response to an items-list mutation.
 */
public final class RXTileSelectionMutationGuard {

    private static final ThreadLocal<Map<MultipleSelectionModel<?>, Integer>> ACTIVE =
            ThreadLocal.withInitial(IdentityHashMap::new);

    private RXTileSelectionMutationGuard() {
    }

    /**
     * Marks the given selection model as updating selection from an items-list
     * mutation on the current thread.
     *
     * @param model the selection model
     */
    public static void enter(MultipleSelectionModel<?> model) {
        Map<MultipleSelectionModel<?>, Integer> active = ACTIVE.get();
        active.merge(model, 1, Integer::sum);
    }

    /**
     * Clears one active mutation mark for the given selection model.
     *
     * @param model the selection model
     */
    public static void exit(MultipleSelectionModel<?> model) {
        Map<MultipleSelectionModel<?>, Integer> active = ACTIVE.get();
        Integer depth = active.get(model);
        if (depth == null || depth <= 1) {
            active.remove(model);
        } else {
            active.put(model, depth - 1);
        }
        if (active.isEmpty()) {
            ACTIVE.remove();
        }
    }

    /**
     * Returns whether the given selection model is currently updating selection
     * from an items-list mutation on the current thread.
     *
     * @param model the selection model
     * @return {@code true} while an items-list mutation update is active
     */
    public static boolean isActive(MultipleSelectionModel<?> model) {
        Map<MultipleSelectionModel<?>, Integer> active = ACTIVE.get();
        boolean result = active.containsKey(model);
        if (active.isEmpty()) {
            ACTIVE.remove();
        }
        return result;
    }
}
