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
 * removed node is not retained by a running timeline. All termination paths
 * converge on the same cleanup: {@code translateX} / {@code translateY} return to
 * {@code 0} and {@code opacity} to {@code 1} (or the node is removed for exits).</p>
 */
final class PaneRelayoutAnimator {

    /**
     * One node move within a relayout pass.
     *
     * @param node           the moving node
     * @param fromTranslateX the starting {@code translateX} (relative to the new layout position)
     * @param fromTranslateY the starting {@code translateY}
     * @param fade           whether the node fades in from {@code opacity 0}
     */
    record Move(Node node, double fromTranslateX, double fromTranslateY, boolean fade) {
    }

    static final double MOVE_EPSILON = 0.5;

    // node -> whether its relayout animation also fades opacity in
    private final Map<Node, Boolean> activeMoves = new HashMap<>();
    private final Map<Node, ExitState> exits = new HashMap<>();
    private Timeline relayoutTimeline;
    private Duration lastDuration = Duration.ZERO;
    private Interpolator lastInterpolator = Interpolator.LINEAR;

    private record ExitState(Timeline timeline, Runnable onRemoved) {
    }

    /**
     * Runs (or snaps) a relayout pass.
     *
     * @param moves        the per-node moves
     * @param animate      whether to animate or snap to the final state
     * @param duration     the animation duration
     * @param interpolator the animation interpolator
     */
    void runRelayout(List<Move> moves, boolean animate, Duration duration, Interpolator interpolator) {
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
            boolean hasTranslation = Math.abs(move.fromTranslateX()) >= MOVE_EPSILON
                    || Math.abs(move.fromTranslateY()) >= MOVE_EPSILON;
            if (!move.fade() && !hasTranslation) {
                finalizeMove(node);
                continue;
            }
            node.setTranslateX(move.fromTranslateX());
            node.setTranslateY(move.fromTranslateY());
            if (move.fade()) {
                node.setOpacity(0.0);
                keyValues.add(new KeyValue(node.opacityProperty(), 1.0, interpolator));
            }
            keyValues.add(new KeyValue(node.translateXProperty(), 0.0, interpolator));
            keyValues.add(new KeyValue(node.translateYProperty(), 0.0, interpolator));
            activeMoves.put(node, move.fade());
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
     * Runs (or skips) an exit animation, removing the node when it finishes.
     *
     * @param node           the leaving node
     * @param animate        whether to animate or remove immediately
     * @param duration       the animation duration
     * @param interpolator   the animation interpolator
     * @param exitTranslateY the {@code translateY} the node drifts to while fading out
     * @param onRemoved      the action that detaches the node once the exit completes
     */
    void runExit(Node node, boolean animate, Duration duration, Interpolator interpolator,
                 double exitTranslateY, Runnable onRemoved) {
        // The exit takes ownership of this node's transforms away from any relayout.
        releaseFromRelayout(node);
        ExitState existing = exits.remove(node);
        if (existing != null) {
            existing.timeline().stop();
        }
        if (!animate) {
            onRemoved.run();
            return;
        }

        node.setTranslateX(0.0);
        node.setTranslateY(0.0);
        node.setOpacity(1.0);
        Timeline timeline = new Timeline(new KeyFrame(duration,
                new KeyValue(node.opacityProperty(), 0.0, interpolator),
                new KeyValue(node.translateYProperty(), exitTranslateY, interpolator)));
        timeline.setOnFinished(event -> finishExit(node));
        exits.put(node, new ExitState(timeline, onRemoved));
        timeline.play();
    }

    /**
     * Drops a node from all tracking and restores it to a neutral state. Called
     * when a node is removed from the pane's children outside an exit animation.
     *
     * @param node the node to forget
     */
    void forget(Node node) {
        releaseFromRelayout(node);
        ExitState exit = exits.remove(node);
        if (exit != null) {
            exit.timeline().stop();
        }
        node.setTranslateX(0.0);
        node.setTranslateY(0.0);
        node.setOpacity(1.0);
    }

    /**
     * Stops every animation, leaving moved nodes at their final layout state and
     * completing pending exits so no detached-but-unmanaged ghosts remain.
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
            keyValues.add(new KeyValue(remaining.translateXProperty(), 0.0, lastInterpolator));
            keyValues.add(new KeyValue(remaining.translateYProperty(), 0.0, lastInterpolator));
            if (Boolean.TRUE.equals(activeMoves.get(remaining))) {
                keyValues.add(new KeyValue(remaining.opacityProperty(), 1.0, lastInterpolator));
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
        node.setTranslateX(0.0);
        node.setTranslateY(0.0);
        node.setOpacity(1.0);
        state.onRemoved().run();
    }

    private void finalizeMove(Node node) {
        activeMoves.remove(node);
        if (exits.containsKey(node)) {
            return;
        }
        node.setTranslateX(0.0);
        node.setTranslateY(0.0);
        node.setOpacity(1.0);
    }
}
