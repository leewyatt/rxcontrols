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
 * A mark carries the order of the install that added it rather than the order the
 * scope was first seen on the node, so reinstalling a theme already mirrored there
 * makes it the newest, and swapping the scene root re-marks the new root without
 * changing which install that is.
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
    /** Node property key for the scopes marked on that node. */
    private static final String SCOPE_MARKS_KEY = "rx-theme-scope-marks";
    /** Scene / parent property key for the order of the install still in force. */
    private static final String SCOPE_ORDER_KEY = "rx-theme-scope-order:";

    /** Counter behind the install order every mark carries. */
    private static long sequence;

    private ThemeScope() {
    }

    static void install(Scene scene, String scopeClass, String stylesheet) {
        RXStyles.addSheets(scene, stylesheet);
        long order = ++sequence;
        scene.getProperties().put(SCOPE_ORDER_KEY + scopeClass, order);
        markTree(scene.getProperties(), scene.getRoot(), scopeClass, order);
        trackRoot(scene, scopeClass);
    }

    static void uninstall(Scene scene, String scopeClass, String stylesheet) {
        RXStyles.removeSheets(scene, stylesheet);
        untrackRoot(scene, scopeClass);
        unmarkTree(scene.getProperties(), scopeClass);
        scene.getProperties().remove(SCOPE_ORDER_KEY + scopeClass);
    }

    static void install(Parent parent, String scopeClass, String stylesheet) {
        RXStyles.addSheets(parent, stylesheet);
        markTree(parent.getProperties(), parent, scopeClass, ++sequence);
    }

    static void uninstall(Parent parent, String scopeClass, String stylesheet) {
        RXStyles.removeSheets(parent, stylesheet);
        unmarkTree(parent.getProperties(), scopeClass);
    }

    // ==================== Marking ====================

    /** Marks the root and every nested sub-scene root, remembering what was marked. */
    private static void markTree(ObservableMap<Object, Object> owner, Parent root, String scopeClass,
                                 long order) {
        unmarkTree(owner, scopeClass);
        List<Parent> roots = new ArrayList<>();
        collectRoots(root, roots);
        List<MarkedRoot> marked = new ArrayList<>();
        for (int index = 0; index < roots.size(); index++) {
            boolean direct = index == 0;
            mark(roots.get(index), scopeClass, direct, order);
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

    private static void mark(Parent node, String scopeClass, boolean direct, long order) {
        ScopeMark mark = scopeMark(node, scopeClass, true);
        mark.count++;
        mark.order = order;
        if (direct) {
            mark.directCount++;
            mark.directOrder = order;
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
            if (mark.directCount > 0 && (winner == null || mark.directOrder > winner.directOrder)) {
                winner = mark;
            }
        }
        if (winner == null) {
            for (ScopeMark mark : marks) {
                if (winner == null || mark.order > winner.order) {
                    winner = mark;
                }
            }
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
        ChangeListener<Parent> listener = (observable, oldRoot, newRoot) ->
                markTree(scene.getProperties(), newRoot, scopeClass, installOrder(scene, scopeClass));
        scene.rootProperty().addListener(listener);
        scene.getProperties().put(key, listener);
    }

    /** The order of the install still in force, so a root swap keeps its place among scopes. */
    private static long installOrder(Scene scene, String scopeClass) {
        Object order = scene.getProperties().get(SCOPE_ORDER_KEY + scopeClass);
        return order instanceof Long ? (Long) order : ++sequence;
    }

    @SuppressWarnings("unchecked")
    private static void untrackRoot(Scene scene, String scopeClass) {
        Object listener = scene.getProperties().remove(ROOT_LISTENER_KEY + scopeClass);
        if (listener != null) {
            scene.rootProperty().removeListener((ChangeListener<Parent>) listener);
        }
    }

    // ==================== Records ====================

    /** One scope on one node: how many installs marked it, how many did so directly, and when. */
    private static final class ScopeMark {

        private final String scopeClass;
        private int count;
        private int directCount;
        private long order;
        private long directOrder;
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
