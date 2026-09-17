package io.github.leewyatt.rxcontrols.theme;

import io.github.leewyatt.rxcontrols.utils.RXStyles;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SubScene;

import java.lang.ref.WeakReference;
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
 * <p>A node ends up carrying two scope classes when a sub-scene root has a theme
 * of its own and a scene theme is mirrored onto it. The scoped blocks read as
 * descendant selectors, so two classes on one node would leave the file order to
 * decide. Only the winning scope is kept on the node instead: a theme installed
 * on that node beats one mirrored onto it, regardless of install order, and among
 * equals the last install wins. Removing the winner restores the suppressed one.
 *
 * <p>Each install remembers the roots it marked, weakly, so uninstalling reaches a
 * sub-scene that has since left the scene graph without keeping a detached one
 * alive. Marks are counted per node, so uninstalling one scope does not strip a
 * mark another install owns, and a scope class the application added itself is
 * never removed.
 */
final class ThemeScope {

    /** Scene property key for the root-change listener of one scope class. */
    private static final String ROOT_LISTENER_KEY = "rx-theme-scope-root-listener:";
    /** Scene / parent property key for the roots one install marked. */
    private static final String MARKED_ROOTS_KEY = "rx-theme-scope-roots:";
    /** Node property key for the scopes marked on that node, in install order. */
    private static final String SCOPE_MARKS_KEY = "rx-theme-scope-marks";

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
        List<Parent> roots = new ArrayList<>();
        collectRoots(root, roots);
        List<MarkedRoot> marked = new ArrayList<>();
        for (int index = 0; index < roots.size(); index++) {
            boolean direct = index == 0;
            mark(roots.get(index), scopeClass, direct);
            marked.add(new MarkedRoot(roots.get(index), direct));
        }
        owner.put(MARKED_ROOTS_KEY + scopeClass, marked);
    }

    @SuppressWarnings("unchecked")
    private static void unmarkTree(ObservableMap<Object, Object> owner, String scopeClass) {
        Object marked = owner.remove(MARKED_ROOTS_KEY + scopeClass);
        if (!(marked instanceof List)) {
            return;
        }
        for (MarkedRoot root : (List<MarkedRoot>) marked) {
            Parent node = root.node.get();
            if (node != null) {
                unmark(node, scopeClass, root.direct);
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

    private static void mark(Parent node, String scopeClass, boolean direct) {
        ScopeMark mark = scopeMark(node, scopeClass, true);
        mark.count++;
        if (direct) {
            mark.directCount++;
        }
        applyWinner(node);
    }

    private static void unmark(Parent node, String scopeClass, boolean direct) {
        List<ScopeMark> marks = scopeMarks(node);
        ScopeMark mark = scopeMark(node, scopeClass, false);
        if (mark == null) {
            return;
        }
        mark.count--;
        if (direct) {
            mark.directCount--;
        }
        if (mark.count <= 0) {
            marks.remove(mark);
            if (mark.owned) {
                RXStyles.removeClass(node, scopeClass);
            }
            if (marks.isEmpty()) {
                node.getProperties().remove(SCOPE_MARKS_KEY);
            }
        }
        applyWinner(node);
    }

    /** Keeps only the winning scope class on the node, leaving classes it did not add. */
    private static void applyWinner(Parent node) {
        List<ScopeMark> marks = scopeMarks(node);
        ScopeMark winner = null;
        for (ScopeMark mark : marks) {
            if (mark.directCount > 0) {
                winner = mark;
            }
        }
        if (winner == null && !marks.isEmpty()) {
            winner = marks.get(marks.size() - 1);
        }
        for (ScopeMark mark : marks) {
            if (mark == winner) {
                if (!node.getStyleClass().contains(mark.scopeClass)) {
                    RXStyles.addClass(node, mark.scopeClass);
                    mark.owned = true;
                }
            } else if (mark.owned) {
                RXStyles.removeClass(node, mark.scopeClass);
                mark.owned = false;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ScopeMark> scopeMarks(Parent node) {
        Object marks = node.getProperties().get(SCOPE_MARKS_KEY);
        return marks instanceof List ? (List<ScopeMark>) marks : new ArrayList<>();
    }

    private static ScopeMark scopeMark(Parent node, String scopeClass, boolean create) {
        List<ScopeMark> marks = scopeMarks(node);
        for (ScopeMark mark : marks) {
            if (mark.scopeClass.equals(scopeClass)) {
                return mark;
            }
        }
        if (!create) {
            return null;
        }
        ScopeMark mark = new ScopeMark(scopeClass);
        marks.add(mark);
        node.getProperties().put(SCOPE_MARKS_KEY, marks);
        return mark;
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

    // ==================== Records ====================

    /** One scope on one node: how many installs marked it, and how many did so directly. */
    private static final class ScopeMark {

        private final String scopeClass;
        private int count;
        private int directCount;
        private boolean owned;

        private ScopeMark(String scopeClass) {
            this.scopeClass = scopeClass;
        }
    }

    /** One root an install marked, held weakly so a detached sub-scene stays collectable. */
    private static final class MarkedRoot {

        private final WeakReference<Parent> node;
        private final boolean direct;

        private MarkedRoot(Parent node, boolean direct) {
            this.node = new WeakReference<>(node);
            this.direct = direct;
        }
    }
}
