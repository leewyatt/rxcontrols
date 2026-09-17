package io.github.leewyatt.rxcontrols.theme;

import io.github.leewyatt.rxcontrols.utils.RXStyles;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableMap;
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
 *
 * <p>Each install remembers the roots it marked, so uninstalling reaches a
 * sub-scene that has since left the scene graph. Marks are reference-counted per
 * node, so uninstalling one scope does not strip a mark another install owns.
 */
final class ThemeScope {

    /** Scene property key for the root-change listener of one scope class. */
    private static final String ROOT_LISTENER_KEY = "rx-theme-scope-root-listener:";
    /** Scene / parent property key for the roots one install marked. */
    private static final String MARKED_ROOTS_KEY = "rx-theme-scope-roots:";
    /** Node property key for how many installs marked that node with one scope class. */
    private static final String SCOPE_COUNT_KEY = "rx-theme-scope-count:";

    private ThemeScope() {
    }

    static void install(Scene scene, String scopeClass, String stylesheet) {
        RXStyles.addSheets(scene, stylesheet);
        markTree(scene.getProperties(), scene.getRoot(), scopeClass);
        trackRoot(scene, scopeClass);
    }

    static void uninstall(Scene scene, String scopeClass, String stylesheet) {
        RXStyles.removeSheets(scene, stylesheet);
        untrackRoot(scene, scopeClass);
        unmarkTree(scene.getProperties(), scopeClass);
    }

    static void install(Parent parent, String scopeClass, String stylesheet) {
        RXStyles.addSheets(parent, stylesheet);
        markTree(parent.getProperties(), parent, scopeClass);
    }

    static void uninstall(Parent parent, String scopeClass, String stylesheet) {
        RXStyles.removeSheets(parent, stylesheet);
        unmarkTree(parent.getProperties(), scopeClass);
    }

    // ==================== Marking ====================

    /** Marks the root and every nested sub-scene root, remembering what was marked. */
    private static void markTree(ObservableMap<Object, Object> owner, Parent root, String scopeClass) {
        unmarkTree(owner, scopeClass);
        List<Parent> marked = new ArrayList<>();
        collectRoots(root, marked);
        for (Parent node : marked) {
            mark(node, scopeClass);
        }
        owner.put(MARKED_ROOTS_KEY + scopeClass, marked);
    }

    @SuppressWarnings("unchecked")
    private static void unmarkTree(ObservableMap<Object, Object> owner, String scopeClass) {
        Object marked = owner.remove(MARKED_ROOTS_KEY + scopeClass);
        if (marked instanceof List) {
            for (Parent node : (List<Parent>) marked) {
                unmark(node, scopeClass);
            }
        }
    }

    private static void collectRoots(Parent root, List<Parent> found) {
        if (root == null) {
            return;
        }
        found.add(root);
        for (SubScene subScene : subScenes(root)) {
            collectRoots(subScene.getRoot(), found);
        }
    }

    private static void mark(Parent node, String scopeClass) {
        int count = scopeCount(node, scopeClass);
        node.getProperties().put(SCOPE_COUNT_KEY + scopeClass, count + 1);
        if (count == 0) {
            RXStyles.addClass(node, scopeClass);
        }
    }

    private static void unmark(Parent node, String scopeClass) {
        int count = scopeCount(node, scopeClass);
        if (count > 1) {
            node.getProperties().put(SCOPE_COUNT_KEY + scopeClass, count - 1);
            return;
        }
        node.getProperties().remove(SCOPE_COUNT_KEY + scopeClass);
        RXStyles.removeClass(node, scopeClass);
    }

    private static int scopeCount(Parent node, String scopeClass) {
        Object count = node.getProperties().get(SCOPE_COUNT_KEY + scopeClass);
        return count instanceof Integer ? (Integer) count : 0;
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

    // ==================== Root tracking ====================

    /** Keeps the scope on the scene when the application swaps the root node. */
    private static void trackRoot(Scene scene, String scopeClass) {
        String key = ROOT_LISTENER_KEY + scopeClass;
        if (scene.getProperties().containsKey(key)) {
            return;
        }
        ChangeListener<Parent> listener =
                (observable, oldRoot, newRoot) -> markTree(scene.getProperties(), newRoot, scopeClass);
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
