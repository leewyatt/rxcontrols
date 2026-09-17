package io.github.leewyatt.rxcontrols.theme;

import io.github.leewyatt.rxcontrols.utils.RXStyles;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SubScene;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared install / uninstall plumbing for the RxControls themes. A theme is a
 * scope style class carried by the themed root (matched by the scoped token
 * blocks in the user-agent stylesheet) plus an author-origin stylesheet holding
 * the per-control re-points that must stay above the platform theme.
 *
 * <p>The scope class is also mirrored onto the root of every {@link SubScene}
 * found when installing: a sub-scene root is not a child of the sub-scene node,
 * so ancestor matching stops there.
 */
final class ThemeScope {

    /** Scene property key prefix for the root-change listener of one scope class. */
    private static final String ROOT_LISTENER_KEY = "rx-theme-scope-root-listener:";

    private ThemeScope() {
    }

    static void install(Scene scene, String scopeClass, String stylesheet) {
        RXStyles.addSheets(scene, stylesheet);
        mark(scene.getRoot(), scopeClass, true);
        trackRoot(scene, scopeClass);
    }

    static void uninstall(Scene scene, String scopeClass, String stylesheet) {
        RXStyles.removeSheets(scene, stylesheet);
        untrackRoot(scene, scopeClass);
        mark(scene.getRoot(), scopeClass, false);
    }

    static void install(Parent parent, String scopeClass, String stylesheet) {
        RXStyles.addSheets(parent, stylesheet);
        mark(parent, scopeClass, true);
    }

    static void uninstall(Parent parent, String scopeClass, String stylesheet) {
        RXStyles.removeSheets(parent, stylesheet);
        mark(parent, scopeClass, false);
    }

    // ==================== Internal ====================

    /** Adds or removes the scope class on the root and on every nested sub-scene root. */
    private static void mark(Parent root, String scopeClass, boolean present) {
        if (root == null) {
            return;
        }
        if (present) {
            RXStyles.addClass(root, scopeClass);
        } else {
            RXStyles.removeClass(root, scopeClass);
        }
        for (SubScene subScene : subScenes(root)) {
            mark(subScene.getRoot(), scopeClass, present);
        }
    }

    private static List<SubScene> subScenes(Parent root) {
        List<SubScene> found = new ArrayList<>();
        collectSubScenes(root, found);
        return found;
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

    /** Keeps the scope on the scene when the application swaps the root node. */
    private static void trackRoot(Scene scene, String scopeClass) {
        String key = ROOT_LISTENER_KEY + scopeClass;
        if (scene.getProperties().containsKey(key)) {
            return;
        }
        ChangeListener<Parent> listener = (observable, oldRoot, newRoot) -> {
            mark(oldRoot, scopeClass, false);
            mark(newRoot, scopeClass, true);
        };
        scene.rootProperty().addListener(listener);
        scene.getProperties().put(key, listener);
    }

    @SuppressWarnings("unchecked")
    private static void untrackRoot(Scene scene, String scopeClass) {
        Object listener = scene.getProperties().remove(ROOT_LISTENER_KEY + scopeClass);
        if (listener != null) {
            scene.rootProperty().removeListener((ChangeListener<Parent>) listener);
        }
    }
}
