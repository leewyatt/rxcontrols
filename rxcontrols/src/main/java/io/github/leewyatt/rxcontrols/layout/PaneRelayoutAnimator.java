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
 * removed node is not retained by a running timeline. Opacity is only ever
 * borrowed: a faded node returns to the opacity it had before the fade, and the
 * opacity of nodes the animator never tracked is never written, so caller-set
 * opacity survives layout passes. The translate channel, by contrast, belongs to
 * the FLIP mechanism: armed nodes return to translate {@code 0}, and an exit
 * re-homes the node to its layout position before fading out.</p>
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
     * @param fromTranslateX the starting {@code translateX} (relative to the new layout position)
     * @param fromTranslateY the starting {@code translateY}
     * @param fade           whether the node fades in from {@code 0} toward its current opacity
     */
    record Move(Node node, double fromTranslateX, double fromTranslateY, boolean fade) {
    }

    static final double MOVE_EPSILON = 0.5;

    // targetOpacity is the opacity the fade tween ends at (the value the node had
    // before the animator wrote 0); NaN for pure-translate moves, whose opacity is
    // never touched.
    private record MoveState(boolean fade, double targetOpacity) {
    }

    // baseOpacity is the opacity restored when the exit ends, so a node pulled out
    // mid-exit keeps its caller-set value.
    private record ExitState(Timeline timeline, Runnable onFinished, double baseOpacity) {
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
            boolean fade = move.fade() || continuedFade;
            boolean hasTranslation = Math.abs(move.fromTranslateX()) >= MOVE_EPSILON
                    || Math.abs(move.fromTranslateY()) >= MOVE_EPSILON;
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
                    node.setOpacity(0.0);
                }
                keyValues.add(new KeyValue(node.opacityProperty(), targetOpacity, interpolator));
            }
            keyValues.add(new KeyValue(node.translateXProperty(), 0.0, interpolator));
            keyValues.add(new KeyValue(node.translateYProperty(), 0.0, interpolator));
            activeMoves.put(node, new MoveState(fade, targetOpacity));
            animated.add(node);
        }
        if (keyValues.isEmpty()) {
            return;
        }
        relayoutTimeline = playRelayout(keyValues, animated, duration);
    }

    // True when this pass introduces a move the running timeline does not already
    // cover: a fading-in node, or a node whose layout target shifted since its tween
    // was armed. A node still tweening toward the same target — its from-translate
    // (offset from the freshly written layout position) equals its live translate —
    // is left to its existing timeline, so a per-pulse relayout does not reset the
    // tween clock.
    private boolean needsRearm(List<Move> moves) {
        for (Move move : moves) {
            Node node = move.node();
            if (exits.containsKey(node)) {
                continue;
            }
            if (move.fade()) {
                return true;
            }
            boolean hasTranslation = Math.abs(move.fromTranslateX()) >= MOVE_EPSILON
                    || Math.abs(move.fromTranslateY()) >= MOVE_EPSILON;
            if (!hasTranslation) {
                continue;
            }
            boolean tweeningToSameTarget = activeMoves.containsKey(node)
                    && Math.abs(move.fromTranslateX() - node.getTranslateX()) < MOVE_EPSILON
                    && Math.abs(move.fromTranslateY() - node.getTranslateY()) < MOVE_EPSILON;
            if (!tweeningToSameTarget) {
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
        if (!animate) {
            // Settle anything this animator wrote before detaching, since forget
            // will no longer see the node as tracked.
            if (tracked != null || existing != null) {
                resetTranslate(node);
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
        node.setTranslateX(0.0);
        node.setTranslateY(0.0);
        Timeline timeline = new Timeline(new KeyFrame(duration,
                new KeyValue(node.opacityProperty(), 0.0, interpolator),
                new KeyValue(node.translateYProperty(), exitTranslateY, interpolator)));
        timeline.setOnFinished(event -> finishExit(node));
        exits.put(node, new ExitState(timeline, onFinished, baseOpacity));
        timeline.play();
    }

    /**
     * Turns an in-flight exit back into a fade-in: the exit stops without running
     * its completion action and without writing opacity, and the node is tracked as
     * fading toward the exit's base opacity. The caller must submit the node in the
     * next {@link #runRelayout} with {@code fade=true}; the fade then resumes from the
     * live mid-exit opacity. A reclaimed node left out of that pass is settled at its
     * base opacity like any dropped move.
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
        activeMoves.put(node, new MoveState(true, exit.baseOpacity()));
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
        resetTranslate(node);
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
            keyValues.add(new KeyValue(remaining.translateXProperty(), 0.0, lastInterpolator));
            keyValues.add(new KeyValue(remaining.translateYProperty(), 0.0, lastInterpolator));
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
        resetTranslate(node);
        restoreOpacity(node, state.baseOpacity());
        state.onFinished().run();
    }

    // Settles a tracked node: translate returns to 0, and opacity returns to its
    // fade target only when this animator wrote opacity. A node that was never
    // tracked is not ours to touch.
    private void finalizeMove(Node node) {
        MoveState state = activeMoves.remove(node);
        if (state == null) {
            return;
        }
        if (exits.containsKey(node)) {
            return;
        }
        resetTranslate(node);
        if (state.fade()) {
            restoreOpacity(node, state.targetOpacity());
        }
    }

    // Settling skips a channel bound since the node was armed. Binding mid-animation
    // is unsupported, but it must not make the settle throw inside a layout pass.
    private static void resetTranslate(Node node) {
        if (!node.translateXProperty().isBound()) {
            node.setTranslateX(0.0);
        }
        if (!node.translateYProperty().isBound()) {
            node.setTranslateY(0.0);
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
            boolean opacityBound = move.fade() && node.opacityProperty().isBound();
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
