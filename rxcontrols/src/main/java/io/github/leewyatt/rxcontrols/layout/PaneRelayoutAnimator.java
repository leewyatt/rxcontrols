package io.github.leewyatt.rxcontrols.layout;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Drives FLIP relayout and exit animations for layout panes.
 *
 * <p>Layout writes the final {@code layoutX} / {@code layoutY}; this animator only
 * tweens {@code translateX} / {@code translateY} and {@code opacity}, leaving the
 * layout state authoritative and never re-triggering a layout pass. A single
 * shared {@link Timeline} runs per relayout pass and supersedes the previous one
 * without snapping; exit animations run on their own timelines so surviving
 * children can reflow at the same time.</p>
 *
 * <p>When a node leaves relayout ownership (an exit starts, or the node is removed
 * externally) the shared relayout timeline is rebuilt for the remaining nodes from
 * their current transforms, so no two timelines ever write the same node and a
 * removed node is not retained by a running timeline.</p>
 *
 * <p>Both channels are only borrowed. When the animator first takes a node over it
 * records the node's translate as its resting translate; every glide ends there, and
 * every way out of tracking (finishing, settling, dropping, forgetting, an exit ending)
 * writes it back, as an exit starts by re-homing the node to it before fading out. A
 * faded node likewise returns to the opacity it had before the fade, and nodes the
 * animator never tracked are never written, so caller-set values survive layout
 * passes. While the animator holds a node, a caller write to the same channel (one
 * assignment or a running animation of its own) is not supported: the animator may
 * overwrite it, and settling restores the value recorded before. Callers submit moves
 * through {@link #addRelayoutMove} and {@link #addEnterMove}, which measure motion
 * against the resting translate rather than against zero.</p>
 *
 * <p>A {@code null}, zero, negative, unknown or indefinite duration — or a
 * {@code null} interpolator — degrades an animated call to the snap path instead
 * of constructing a timeline that would throw inside a layout pass.</p>
 *
 * <p>Bound channels are never written, since setting a bound property throws: a
 * relayout move whose node has a bound {@code translateX} or {@code translateY} is
 * skipped (the node simply snaps), a fade whose node has a bound {@code opacity}
 * becomes a pure glide, and an exit whose node has either bound completes at once
 * without animating. Binding these properties while the node is animating is not
 * supported: the running timeline keeps writing the node until the next pass drops
 * it, though settling never writes a bound channel.</p>
 */
final class PaneRelayoutAnimator {

    /**
     * One node move within a relayout pass.
     *
     * @param node           the moving node
     * @param fromTranslateX the {@code translateX} the node starts from at its new layout
     *                       position; the glide ends at its resting translate
     * @param fromTranslateY the {@code translateY} the node starts from
     * @param fade           whether the node fades in from {@code 0} toward its current opacity
     * @param fromOpacity    the opacity a fade resumes from toward the node's current
     *                       opacity, or {@code NaN} for none; a move with one fades even
     *                       when it does not move
     */
    record Move(Node node, double fromTranslateX, double fromTranslateY, boolean fade, double fromOpacity) {

        /**
         * Creates a move that resumes no fade.
         *
         * @param node           the moving node
         * @param fromTranslateX the {@code translateX} the node starts from
         * @param fromTranslateY the {@code translateY} the node starts from
         * @param fade           whether the node fades in from {@code 0}
         */
        Move(Node node, double fromTranslateX, double fromTranslateY, boolean fade) {
            this(node, fromTranslateX, fromTranslateY, fade, Double.NaN);
        }

        private boolean resumesFade() {
            return !Double.isNaN(fromOpacity);
        }
    }

    static final double MOVE_EPSILON = 0.5;

    // The rise of an entrance, in pixels: the pane's gap clamped to this range.
    private static final double ENTER_OFFSET_MIN = 4.0;
    private static final double ENTER_OFFSET_MAX = 12.0;

    /**
     * Returns how far below its slot an entering child starts: the given gap, clamped
     * to a few pixels so the rise reads as a settle rather than a slide.
     *
     * @param gap the pane's gap between rows
     * @return the entrance offset
     */
    static double enterOffset(double gap) {
        return Math.max(ENTER_OFFSET_MIN, Math.min(ENTER_OFFSET_MAX, gap));
    }

    // targetOpacity is the opacity the fade tween ends at (the value the node had
    // before the animator wrote 0); NaN for pure-translate moves, whose opacity is
    // never touched. restX / restY is the translate the node had when first taken over.
    private record MoveState(boolean fade, double targetOpacity, double restX, double restY) {
    }

    // baseOpacity is the opacity restored when the exit ends, so a node pulled out
    // mid-exit keeps its caller-set value; restX / restY likewise for translate.
    private record ExitState(Timeline timeline, Runnable onFinished, double baseOpacity,
                             double restX, double restY) {
    }

    private final Map<Node, MoveState> activeMoves = new HashMap<>();
    private final Map<Node, ExitState> exits = new HashMap<>();
    private Timeline relayoutTimeline;
    private Duration lastDuration = Duration.ZERO;
    private Interpolator lastInterpolator = Interpolator.LINEAR;

    /**
     * Runs (or snaps) a relayout pass. Invalid animation inputs (see the class
     * documentation) degrade to the snap path.
     *
     * @param moves        the per-node moves
     * @param animate      whether to animate or snap to the final state
     * @param duration     the animation duration
     * @param interpolator the animation interpolator
     */
    void runRelayout(List<Move> moves, boolean animate, Duration duration, Interpolator interpolator) {
        animate = animate && isAnimatable(duration, interpolator);
        moves = writableMoves(moves);
        Set<Node> passNodes = new HashSet<>();
        for (Move move : moves) {
            passNodes.add(move.node());
        }
        boolean removedActiveMove = false;
        for (Node node : new ArrayList<>(activeMoves.keySet())) {
            if (!passNodes.contains(node)) {
                finalizeMove(node);
                removedActiveMove = true;
            }
        }
        if (!animate) {
            if (relayoutTimeline != null) {
                relayoutTimeline.stop();
                relayoutTimeline = null;
            }
            for (Move move : moves) {
                finalizeMove(move.node());
            }
            return;
        }

        // A pass whose nodes are all already animating toward an unchanged target
        // needs no new timeline: the in-flight tween already heads to the right
        // place. Rebuilding it would reset the tween clock, so a pane relaid on
        // every pulse (e.g. by an animated child forcing a parent relayout) would
        // restart the tween each frame and stretch a sub-second animation into a
        // multi-second crawl. Only (re)arm when a target changes or a node fades in.
        // Never take this shortcut after dropping an active node above: the running
        // timeline still holds it, so it must be rebuilt to stop writing that node.
        if (!removedActiveMove && relayoutTimeline != null && !needsRearm(moves)) {
            return;
        }
        if (relayoutTimeline != null) {
            relayoutTimeline.stop();
            relayoutTimeline = null;
        }

        lastDuration = duration;
        lastInterpolator = interpolator;
        List<KeyValue> keyValues = new ArrayList<>();
        List<Node> animated = new ArrayList<>();
        for (Move move : moves) {
            Node node = move.node();
            // The exit timeline owns a leaving node's transforms; arming it here
            // would have two timelines writing the same node.
            if (exits.containsKey(node)) {
                continue;
            }
            MoveState previous = activeMoves.get(node);
            // A node whose fade-in is still in flight keeps fading across timeline
            // rebuilds; its new move only carries fade on the pass it entered.
            boolean continuedFade = previous != null && previous.fade();
            boolean fade = move.fade() || continuedFade || move.resumesFade();
            // Read before the first write: a node taken over now rests where it is.
            double restX = previous != null ? previous.restX() : node.getTranslateX();
            double restY = previous != null ? previous.restY() : node.getTranslateY();
            boolean hasTranslation = Math.abs(move.fromTranslateX() - restX) >= MOVE_EPSILON
                    || Math.abs(move.fromTranslateY() - restY) >= MOVE_EPSILON;
            if (!fade && !hasTranslation) {
                finalizeMove(node);
                continue;
            }
            node.setTranslateX(move.fromTranslateX());
            node.setTranslateY(move.fromTranslateY());
            double targetOpacity = Double.NaN;
            if (fade) {
                if (continuedFade) {
                    // Resume from the live mid-fade value: no opacity write, the
                    // timeline interpolates from the current value at play time.
                    targetOpacity = previous.targetOpacity();
                } else {
                    targetOpacity = node.getOpacity();
                    node.setOpacity(move.resumesFade() ? move.fromOpacity() : 0.0);
                }
                keyValues.add(new KeyValue(node.opacityProperty(), targetOpacity, interpolator));
            }
            keyValues.add(new KeyValue(node.translateXProperty(), restX, interpolator));
            keyValues.add(new KeyValue(node.translateYProperty(), restY, interpolator));
            activeMoves.put(node, new MoveState(fade, targetOpacity, restX, restY));
            animated.add(node);
        }
        if (keyValues.isEmpty()) {
            return;
        }
        relayoutTimeline = playRelayout(keyValues, animated, duration);
    }

    // True when this pass introduces a move the running timeline does not already
    // cover: a fading-in node, a static node that starts to move, or a tracked node
    // whose layout target shifted since its tween was armed. A tracked node still
    // tweening toward the same target — its from-translate (offset from the freshly
    // written layout position) equals its live translate — is left to its existing
    // timeline, so a per-pulse relayout does not reset the tween clock. Any other
    // from-translate of a tracked node is a new target, even one with no offset left:
    // the node then sits at its new layout position while the old timeline would keep
    // pushing it away.
    private boolean needsRearm(List<Move> moves) {
        for (Move move : moves) {
            Node node = move.node();
            if (exits.containsKey(node)) {
                continue;
            }
            if (move.fade() || move.resumesFade()) {
                return true;
            }
            if (activeMoves.containsKey(node)) {
                if (Math.abs(move.fromTranslateX() - node.getTranslateX()) >= MOVE_EPSILON
                        || Math.abs(move.fromTranslateY() - node.getTranslateY()) >= MOVE_EPSILON) {
                    return true;
                }
                continue;
            }
            // An untracked node rests at its current translate.
            if (Math.abs(move.fromTranslateX() - node.getTranslateX()) >= MOVE_EPSILON
                    || Math.abs(move.fromTranslateY() - node.getTranslateY()) >= MOVE_EPSILON) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs (or skips) an exit animation, running the exit's completion action when
     * it finishes (for example detaching or hiding the node). Invalid animation
     * inputs (see the class documentation) run the completion action immediately.
     *
     * @param node           the leaving node
     * @param animate        whether to animate or finish immediately
     * @param duration       the animation duration
     * @param interpolator   the animation interpolator
     * @param exitTranslateY the {@code translateY} the node drifts to while fading out
     * @param onFinished     the completion action, run once the node's transforms are restored
     */
    void runExit(Node node, boolean animate, Duration duration, Interpolator interpolator,
                 double exitTranslateY, Runnable onFinished) {
        animate = animate && isAnimatable(duration, interpolator)
                && !isTranslateBound(node) && !node.opacityProperty().isBound();
        // The exit takes ownership of this node's transforms away from any relayout.
        MoveState tracked = activeMoves.get(node);
        releaseFromRelayout(node);
        ExitState existing = exits.remove(node);
        if (existing != null) {
            existing.timeline().stop();
        }
        double restX;
        double restY;
        if (existing != null) {
            restX = existing.restX();
            restY = existing.restY();
        } else if (tracked != null) {
            restX = tracked.restX();
            restY = tracked.restY();
        } else {
            restX = node.getTranslateX();
            restY = node.getTranslateY();
        }
        if (!animate) {
            // Settle anything this animator wrote before detaching, since forget
            // will no longer see the node as tracked.
            if (tracked != null || existing != null) {
                restoreTranslate(node, restX, restY);
                if (existing != null) {
                    restoreOpacity(node, existing.baseOpacity());
                } else if (tracked.fade()) {
                    restoreOpacity(node, tracked.targetOpacity());
                }
            }
            onFinished.run();
            return;
        }

        double baseOpacity;
        if (existing != null) {
            baseOpacity = existing.baseOpacity();
        } else if (tracked != null && tracked.fade()) {
            baseOpacity = tracked.targetOpacity();
        } else {
            baseOpacity = node.getOpacity();
        }
        // Any glide offset left is dropped: the exit fades out from the resting position.
        node.setTranslateX(restX);
        node.setTranslateY(restY);
        Timeline timeline = new Timeline(new KeyFrame(duration,
                new KeyValue(node.opacityProperty(), 0.0, interpolator),
                new KeyValue(node.translateYProperty(), restY + exitTranslateY, interpolator)));
        timeline.setOnFinished(event -> finishExit(node));
        exits.put(node, new ExitState(timeline, onFinished, baseOpacity, restX, restY));
        timeline.play();
    }

    /**
     * Turns an in-flight exit back into a fade-in: the exit stops without running
     * its completion action and without writing opacity, and the node is tracked as
     * fading toward the exit's base opacity and resting translate. The caller must
     * submit the node in the next {@link #runRelayout} with {@code fade=true}; the fade
     * then resumes from the live mid-exit opacity. A reclaimed node left out of that pass
     * is settled at its base opacity and resting translate like any dropped move.
     *
     * @param node the node whose exit to reclaim
     * @return whether the node had an exit in flight
     */
    boolean reclaimExit(Node node) {
        ExitState exit = exits.remove(node);
        if (exit == null) {
            return false;
        }
        exit.timeline().stop();
        activeMoves.put(node, new MoveState(true, exit.baseOpacity(), exit.restX(), exit.restY()));
        return true;
    }

    /**
     * Drops a node from all tracking and, if this animator ever wrote to it,
     * restores what was written. A node that was never tracked is left untouched
     * so caller-set transforms survive. Called when a node is removed from the
     * pane's children outside an exit animation.
     *
     * @param node the node to forget
     */
    void forget(Node node) {
        MoveState tracked = activeMoves.get(node);
        releaseFromRelayout(node);
        ExitState exit = exits.remove(node);
        if (exit != null) {
            exit.timeline().stop();
        }
        if (tracked == null && exit == null) {
            return;
        }
        if (tracked != null) {
            restoreTranslate(node, tracked.restX(), tracked.restY());
        } else {
            restoreTranslate(node, exit.restX(), exit.restY());
        }
        if (tracked != null && tracked.fade()) {
            restoreOpacity(node, tracked.targetOpacity());
        } else if (exit != null) {
            restoreOpacity(node, exit.baseOpacity());
        }
    }

    /**
     * Returns whether this animator currently owns the node's transforms via an
     * in-flight relayout move. Callers that pre-filter static moves must still
     * submit tracked nodes — the drop detection would otherwise finalize them
     * mid-tween.
     *
     * @param node the node to query
     * @return whether the node has an in-flight relayout move
     */
    boolean isTracked(Node node) {
        return activeMoves.containsKey(node);
    }

    /**
     * Resubmits a tracked node at its live offset, so its in-flight tween keeps its
     * clock while its end point follows the node's freshly written layout position.
     * Does nothing for an untracked node. Used on passes that must not start motion.
     *
     * @param moves the pass's moves
     * @param node  the node just laid out
     */
    void keepInFlight(List<Move> moves, Node node) {
        if (isTracked(node)) {
            moves.add(new Move(node, node.getTranslateX(), node.getTranslateY(), false));
        }
    }

    /**
     * Returns the {@code translateX} a node rests at: the value recorded when the
     * animator took the node over while it glides or exits, its current
     * {@code translateX} otherwise.
     *
     * @param node the node to query
     * @return the resting {@code translateX}
     */
    double restingTranslateX(Node node) {
        MoveState tracked = activeMoves.get(node);
        if (tracked != null) {
            return tracked.restX();
        }
        ExitState exit = exits.get(node);
        return exit != null ? exit.restX() : node.getTranslateX();
    }

    /**
     * Returns the {@code translateY} a node rests at; see {@link #restingTranslateX}.
     *
     * @param node the node to query
     * @return the resting {@code translateY}
     */
    double restingTranslateY(Node node) {
        MoveState tracked = activeMoves.get(node);
        if (tracked != null) {
            return tracked.restY();
        }
        ExitState exit = exits.get(node);
        return exit != null ? exit.restY() : node.getTranslateY();
    }

    /**
     * Adds the move of a node the pass has just laid out, when it moved or is still in
     * flight. The start is the node's old on-screen position seen from its new layout
     * position, and the node moved when that start is off its resting translate: a move
     * whose start happens to be zero still glides back to a non-zero resting translate,
     * and a static node with a translate of its own is left alone.
     *
     * @param moves      the pass's moves
     * @param node       the node, already at its new layout position
     * @param oldVisualX the node's {@code layoutX + translateX} before the pass laid it out
     * @param oldVisualY the node's {@code layoutY + translateY} before the pass laid it out
     */
    void addRelayoutMove(List<Move> moves, Node node, double oldVisualX, double oldVisualY) {
        double fromX = oldVisualX - node.getLayoutX();
        double fromY = oldVisualY - node.getLayoutY();
        if (isTracked(node)
                || Math.abs(fromX - restingTranslateX(node)) >= MOVE_EPSILON
                || Math.abs(fromY - restingTranslateY(node)) >= MOVE_EPSILON) {
            moves.add(new Move(node, fromX, fromY, false));
        }
    }

    /**
     * Adds the move of a node put back after it was removed mid-fade: it glides on from
     * where it showed, like {@link #addRelayoutMove}, and its fade goes on from the
     * opacity it showed at toward the opacity it has now, which the removal gave back.
     *
     * @param moves       the pass's moves
     * @param node        the node, already at its new layout position
     * @param oldVisualX  where the node showed, {@code layoutX + translateX}, when removed
     * @param oldVisualY  where the node showed, {@code layoutY + translateY}, when removed
     * @param fromOpacity the opacity the node showed at when removed
     */
    void addResumedMove(List<Move> moves, Node node, double oldVisualX, double oldVisualY, double fromOpacity) {
        moves.add(new Move(node, oldVisualX - node.getLayoutX(), oldVisualY - node.getLayoutY(), false, fromOpacity));
    }

    /**
     * Adds the entrance of a node with no earlier position to glide from: it starts at
     * the given offset from its resting translate and, when fading, at opacity 0.
     *
     * @param moves   the pass's moves
     * @param node    the entering node, already at its layout position
     * @param offsetX the horizontal offset the entrance starts at
     * @param offsetY the vertical offset the entrance starts at
     * @param fade    whether the node fades in
     */
    void addEnterMove(List<Move> moves, Node node, double offsetX, double offsetY, boolean fade) {
        moves.add(new Move(node, restingTranslateX(node) + offsetX, restingTranslateY(node) + offsetY, fade));
    }

    /**
     * Returns whether any relayout move or exit is in flight. When nothing is
     * tracked, a non-animated pass may skip move bookkeeping entirely — there is
     * no state left for the snap path to settle.
     *
     * @return whether any animation state is live
     */
    boolean hasActiveState() {
        return !activeMoves.isEmpty() || !exits.isEmpty() || relayoutTimeline != null;
    }

    /**
     * Stops every animation, leaving moved nodes at their final layout state and
     * completing pending exits (running their completion actions) so no
     * detached-but-unmanaged ghosts remain.
     */
    void stopAll() {
        if (relayoutTimeline != null) {
            relayoutTimeline.stop();
            relayoutTimeline = null;
        }
        for (Node node : new ArrayList<>(activeMoves.keySet())) {
            finalizeMove(node);
        }
        activeMoves.clear();
        for (Node node : new ArrayList<>(exits.keySet())) {
            finishExit(node);
        }
    }

    // Removes a node from the shared relayout timeline by stopping it and rebuilding
    // it for the remaining nodes from their current transforms, so the released node
    // is no longer written or retained while the others keep animating smoothly.
    private void releaseFromRelayout(Node node) {
        if (activeMoves.remove(node) == null) {
            return;
        }
        if (relayoutTimeline == null) {
            return;
        }
        relayoutTimeline.stop();
        relayoutTimeline = null;
        if (activeMoves.isEmpty()) {
            return;
        }
        List<KeyValue> keyValues = new ArrayList<>();
        List<Node> animated = new ArrayList<>(activeMoves.keySet());
        for (Node remaining : animated) {
            MoveState state = activeMoves.get(remaining);
            keyValues.add(new KeyValue(remaining.translateXProperty(), state.restX(), lastInterpolator));
            keyValues.add(new KeyValue(remaining.translateYProperty(), state.restY(), lastInterpolator));
            if (state.fade()) {
                keyValues.add(new KeyValue(remaining.opacityProperty(), state.targetOpacity(), lastInterpolator));
            }
        }
        relayoutTimeline = playRelayout(keyValues, animated, lastDuration);
    }

    private Timeline playRelayout(List<KeyValue> keyValues, List<Node> animated, Duration duration) {
        Timeline timeline = new Timeline(new KeyFrame(duration, keyValues.toArray(new KeyValue[0])));
        timeline.setOnFinished(event -> {
            for (Node node : animated) {
                finalizeMove(node);
            }
            if (relayoutTimeline == timeline) {
                relayoutTimeline = null;
            }
        });
        timeline.play();
        return timeline;
    }

    private void finishExit(Node node) {
        ExitState state = exits.remove(node);
        if (state == null) {
            return;
        }
        state.timeline().stop();
        restoreTranslate(node, state.restX(), state.restY());
        restoreOpacity(node, state.baseOpacity());
        state.onFinished().run();
    }

    // Settles a tracked node: translate returns to its resting value, and opacity
    // returns to its fade target only when this animator wrote opacity. A node that
    // was never tracked is not ours to touch.
    private void finalizeMove(Node node) {
        MoveState state = activeMoves.remove(node);
        if (state == null) {
            return;
        }
        if (exits.containsKey(node)) {
            return;
        }
        restoreTranslate(node, state.restX(), state.restY());
        if (state.fade()) {
            restoreOpacity(node, state.targetOpacity());
        }
    }

    // Settling skips a channel bound since the node was armed. Binding mid-animation
    // is unsupported, but it must not make the settle throw inside a layout pass.
    private static void restoreTranslate(Node node, double x, double y) {
        if (!node.translateXProperty().isBound()) {
            node.setTranslateX(x);
        }
        if (!node.translateYProperty().isBound()) {
            node.setTranslateY(y);
        }
    }

    private static void restoreOpacity(Node node, double value) {
        if (!node.opacityProperty().isBound()) {
            node.setOpacity(value);
        }
    }

    // Allocates only when a move has to be dropped or downgraded.
    private static List<Move> writableMoves(List<Move> moves) {
        List<Move> result = null;
        for (int i = 0; i < moves.size(); i++) {
            Move move = moves.get(i);
            Node node = move.node();
            boolean translateBound = isTranslateBound(node);
            boolean opacityBound = (move.fade() || move.resumesFade()) && node.opacityProperty().isBound();
            if (!translateBound && !opacityBound) {
                if (result != null) {
                    result.add(move);
                }
                continue;
            }
            if (result == null) {
                result = new ArrayList<>(moves.subList(0, i));
            }
            if (!translateBound) {
                result.add(new Move(node, move.fromTranslateX(), move.fromTranslateY(), false));
            }
        }
        return result == null ? moves : result;
    }

    private static boolean isTranslateBound(Node node) {
        return node.translateXProperty().isBound() || node.translateYProperty().isBound();
    }

    private static boolean isAnimatable(Duration duration, Interpolator interpolator) {
        return interpolator != null && duration != null && !duration.isUnknown()
                && !duration.isIndefinite() && duration.greaterThan(Duration.ZERO);
    }
}
