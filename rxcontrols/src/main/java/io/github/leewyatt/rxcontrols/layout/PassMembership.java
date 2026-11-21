package io.github.leewyatt.rxcontrols.layout;

import javafx.scene.Node;
import javafx.scene.Scene;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Records which children a pane laid out on its previous layout pass and which joined
 * it since, so the pane can tell a child it showed (which may glide from where it
 * showed) from one that is new (which enters) and from one that is neither.
 *
 * <p>A glide needs a position the child was last shown at. Only a pass in the scene the
 * pane was last laid out in animates, and only a child that pass laid out glides. A
 * child added to the list since enters; any other child (newly managed, back after
 * missing a pass) snaps into its slot instead of gliding in from a stale position or the
 * pane origin. A child removed and put back before the next pass goes on as it stood
 * when it was removed: one the last pass laid out glides on from where it showed, one
 * added since still enters, and one that was in the list but not laid out snaps.</p>
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
    // Children added since the last pass; all are in the list, so they are held anyway.
    private final Set<Node> addedSinceLastPass = new HashSet<>();
    // Children removed since the last pass, with how each stood. Weak keys: a removed
    // child must not stay reachable from the pane when no further pass comes; the
    // values hold no node.
    private final Map<Node, Departure> departures = new WeakHashMap<>();

    /**
     * How a removed child of the last pass showed: layout plus translate, and opacity,
     * read before the animator gives them back.
     *
     * @param x       the x it showed at
     * @param y       the y it showed at
     * @param opacity the opacity it showed at
     */
    record Shown(double x, double y, double opacity) {
    }

    // How a child stood when it was removed: laid out by the last pass (shown is where it
    // showed), added since (entering), or neither (shown and entering both unset).
    private record Departure(Shown shown, boolean entering) {
    }

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
     * Returns whether the child joined the list since the last pass, and so enters.
     *
     * @param child the child
     * @return whether it enters
     */
    boolean isEntering(Node child) {
        return addedSinceLastPass.contains(child);
    }

    /**
     * Returns where a child of the last pass showed when it was removed since, or
     * {@code null} when it was not, or the last pass did not lay it out.
     *
     * @param child the child
     * @return where it showed when removed, or {@code null}
     */
    Shown shownWhenRemoved(Node child) {
        Departure departure = departures.get(child);
        return departure == null ? null : departure.shown();
    }

    /**
     * Notes that a child really left the children list; called before the animator gives
     * its translate back.
     *
     * @param child the child removed
     */
    void removed(Node child) {
        boolean laidOut = laidOutLastPass.remove(child);
        boolean entering = addedSinceLastPass.remove(child);
        // Taken out again after being put back: how it stood is recorded already.
        if (departures.containsKey(child)) {
            return;
        }
        Shown shown = laidOut
                ? new Shown(child.getLayoutX() + child.getTranslateX(), child.getLayoutY() + child.getTranslateY(),
                        child.getOpacity())
                : null;
        departures.put(child, new Departure(shown, entering));
    }

    /**
     * Notes that a child really joined the children list: a new child enters, and one
     * put back goes on as it stood when it was removed.
     *
     * @param child the child added
     */
    void added(Node child) {
        Departure departure = departures.get(child);
        if (departure == null || departure.entering()) {
            addedSinceLastPass.add(child);
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
        addedSinceLastPass.clear();
        departures.clear();
        layoutScene = scene;
    }

    /**
     * Forgets every record, so the next pass in a scene starts afresh.
     */
    void forget() {
        layoutScene = null;
        laidOutLastPass.clear();
        laidOutThisPass.clear();
        addedSinceLastPass.clear();
        departures.clear();
    }
}
