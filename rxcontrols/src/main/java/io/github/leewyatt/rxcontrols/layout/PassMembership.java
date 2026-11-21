package io.github.leewyatt.rxcontrols.layout;

import javafx.scene.Node;
import javafx.scene.Scene;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Records which children a pane laid out on its previous layout pass, so the pane can
 * tell a child it showed (which may glide from where it showed) from one it did not.
 *
 * <p>A glide needs a position the child was last shown at. Only a pass in the scene the
 * pane was last laid out in animates, and only a child that pass laid out glides; any
 * other child (newly added, newly managed, back after missing a pass) snaps into its
 * slot instead of gliding in from a stale position or the pane origin. A child removed
 * and put back before the next pass glides on from where it showed when it was
 * removed.</p>
 *
 * <p>A listener reached from a layout pass can take the pane out of its scene while the
 * pass is still running. The pane samples {@link #sceneChanges()} when the pass starts;
 * when the count has moved by the end, the pass starts no glide and forgets what it
 * recorded.</p>
 */
final class PassMembership {

    private Scene layoutScene;
    private int sceneChanges;
    private Set<Node> laidOutLastPass = new HashSet<>();
    private Set<Node> laidOutThisPass = new HashSet<>();
    // Children of the last pass removed since, with where each showed when it was
    // removed (layout plus translate, read before the animator gives the translate back).
    // Weak keys: a removed child must not stay reachable from the pane when no further
    // pass comes; the values hold no node.
    private final Map<Node, double[]> removedSinceLastPass = new WeakHashMap<>();

    /**
     * Notes that the pane moved to another scene or out of one; leaving a scene forgets
     * every record.
     *
     * @param newScene the pane's new scene, or {@code null}
     */
    void sceneChanged(Scene newScene) {
        sceneChanges++;
        if (newScene == null) {
            forget();
        }
    }

    /**
     * Returns how often the pane's scene has changed, for a pass to sample when it starts.
     *
     * @return the scene change count
     */
    int sceneChanges() {
        return sceneChanges;
    }

    /**
     * Starts a pass. A pass that threw midway may have left members behind.
     */
    void beginPass() {
        laidOutThisPass.clear();
    }

    /**
     * Returns whether a pass in the given scene continues from the last one, so its
     * children may glide.
     *
     * @param scene the pane's scene now
     * @return whether the pane was last laid out in this scene
     */
    boolean continuesIn(Scene scene) {
        return scene != null && scene == layoutScene;
    }

    /**
     * Records that the pass laid the child out. Called for every child the pass lays out,
     * before any skip, the first and resize-only passes included.
     *
     * @param child the child laid out
     */
    void laidOut(Node child) {
        laidOutThisPass.add(child);
    }

    /**
     * Returns whether the previous pass laid the child out.
     *
     * @param child the child
     * @return whether the previous pass laid it out
     */
    boolean wasLaidOut(Node child) {
        return laidOutLastPass.contains(child);
    }

    /**
     * Returns where a child of the last pass showed when it was removed since, as
     * {@code {x, y}} in layout plus translate, or {@code null} when it was not removed.
     *
     * @param child the child
     * @return the position it showed at when removed, or {@code null}
     */
    double[] shownWhenRemoved(Node child) {
        return removedSinceLastPass.get(child);
    }

    /**
     * Notes that a child left the children list; called before the animator gives its
     * translate back.
     *
     * @param child the child removed
     */
    void removed(Node child) {
        if (laidOutLastPass.remove(child)) {
            removedSinceLastPass.put(child, new double[] {
                    child.getLayoutX() + child.getTranslateX(),
                    child.getLayoutY() + child.getTranslateY()});
        }
    }

    /**
     * Ends a pass that ran to its end in the given scene. A pass outside any scene shows
     * nothing, so the next pass in a scene starts afresh.
     *
     * @param scene the pane's scene during the pass, or {@code null}
     */
    void endPass(Scene scene) {
        if (scene == null) {
            forget();
            return;
        }
        Set<Node> previous = laidOutLastPass;
        laidOutLastPass = laidOutThisPass;
        laidOutThisPass = previous;
        laidOutThisPass.clear();
        removedSinceLastPass.clear();
        layoutScene = scene;
    }

    /**
     * Forgets every record, so the next pass in a scene starts afresh.
     */
    void forget() {
        layoutScene = null;
        laidOutLastPass.clear();
        laidOutThisPass.clear();
        removedSinceLastPass.clear();
    }
}
