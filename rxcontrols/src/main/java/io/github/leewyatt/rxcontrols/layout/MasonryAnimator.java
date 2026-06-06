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
 * Drives FLIP relayout and exit animations for {@link RXMasonryPane}.
 *
 * <p>Layout writes the final {@code layoutX} / {@code layoutY}; this animator only
 * tweens {@code translateX} / {@code translateY} and {@code opacity}, leaving the
 * layout state authoritative and never re-triggering a layout pass. A single
 * shared {@link Timeline} runs per relayout pass and supersedes the previous one
 * without snapping; exit animations run on their own timelines so surviving
 * children can reflow at the same time.</p>
 *
 * <p>All termination paths converge on the same cleanup: {@code translateX} /
 * {@code translateY} return to {@code 0} and {@code opacity} to {@code 1} (or the
 * node is removed for exits). A node owned by an in-flight exit is never clobbered
 * by a relayout finish.</p>
 */
final class MasonryAnimator {

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

    private static final double MOVE_EPSILON = 0.5;

    private final Set<Node> activeMoves = new HashSet<>();
    private final Map<Node, ExitState> exits = new HashMap<>();
    private Timeline relayoutTimeline;

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
        for (Node node : new ArrayList<>(activeMoves)) {
            if (!passNodes.contains(node)) {
                finalizeMove(node);
            }
        }
        if (relayoutTimeline != null) {
            relayoutTimeline.stop();
            relayoutTimeline = null;
        }
        if (!animate) {
            for (Move move : moves) {
                finalizeMove(move.node());
            }
            return;
        }

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
            activeMoves.add(node);
            animated.add(node);
        }
        if (keyValues.isEmpty()) {
            return;
        }

        Timeline timeline = new Timeline(new KeyFrame(duration, keyValues.toArray(new KeyValue[0])));
        timeline.setOnFinished(event -> {
            for (Node node : animated) {
                finalizeMove(node);
            }
            if (relayoutTimeline == timeline) {
                relayoutTimeline = null;
            }
        });
        relayoutTimeline = timeline;
        timeline.play();
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
        activeMoves.remove(node);
        ExitState existing = exits.remove(node);
        if (existing != null) {
            existing.timeline().stop();
        }
        if (!animate) {
            onRemoved.run();
            return;
        }

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
        activeMoves.remove(node);
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
        for (Node node : new ArrayList<>(activeMoves)) {
            finalizeMove(node);
        }
        activeMoves.clear();
        for (Node node : new ArrayList<>(exits.keySet())) {
            finishExit(node);
        }
    }

    private void finishExit(Node node) {
        ExitState state = exits.remove(node);
        if (state == null) {
            return;
        }
        state.timeline().stop();
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
