package io.github.leewyatt.rxcontrols.skins;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reorder glide animator shared by the virtualized viewports and the kanban board
 * ({@link RXTileViewport}, {@code RXMasonryViewport}, {@code KanbanColumnViewport}
 * and {@code RXKanbanViewSkin}'s column boxes). Each node gets its own one-shot
 * {@link Timeline} that tweens {@code translateX/translateY} from a captured FLIP
 * delta back to zero, while layout owns the authoritative {@code layoutX/layoutY}.
 * Independent per-node timelines (rather than one shared timeline) let a
 * virtualized cell be re-aimed mid-glide without disturbing the others, and let a
 * cell that finishes be recycled on its own.
 *
 * <p>This class only manages the tween + transform cleanup; the viewport owns the
 * recycler pin-set (a gliding cell must not be parked or rebound without first
 * cancelling its glide) and removes the node from that set in the
 * {@code onFinished} callback, or itself after {@link #cancel} / {@link #snapAll}.
 */
final class ViewportReorderAnimator {

    // Sub-pixel moves are snapped rather than animated (no 0-frame Timeline churn).
    private static final double MOVE_EPSILON = 0.5;

    private final Map<Node, Timeline> running = new HashMap<>();

    /**
     * Glides {@code node} from the given translate delta back to zero over
     * {@code duration} using {@code interpolator}. If a tween is already running
     * for the node it is stopped and replaced (re-aim, not restart-from-scratch).
     * Sub-pixel deltas snap immediately. {@code onFinished} runs after the
     * transforms are reset, on every terminal path (natural finish or snap), so the
     * caller can un-pin / recycle.
     */
    void animate(Node node, double fromDx, double fromDy, Duration duration, Interpolator interpolator,
                 Consumer<Node> onFinished) {
        Timeline prior = running.remove(node);
        if (prior != null) {
            prior.stop();
        }
        if (Math.abs(fromDx) < MOVE_EPSILON && Math.abs(fromDy) < MOVE_EPSILON) {
            node.setTranslateX(0);
            node.setTranslateY(0);
            onFinished.accept(node);
            return;
        }
        node.setTranslateX(fromDx);
        node.setTranslateY(fromDy);
        Timeline timeline = new Timeline(new KeyFrame(duration,
                new KeyValue(node.translateXProperty(), 0.0, interpolator),
                new KeyValue(node.translateYProperty(), 0.0, interpolator)));
        timeline.setOnFinished(e -> {
            node.setTranslateX(0);
            node.setTranslateY(0);
            running.remove(node);
            onFinished.accept(node);
        });
        running.put(node, timeline);
        timeline.play();
    }

    /**
     * Stops the running glide for {@code node}, if any, and resets its transforms.
     * Unlike a natural finish, {@code onFinished} is not invoked — like
     * {@link #snapAll}, the caller removes the node from its own pin-set. Used
     * when a pass rebinds a gliding cell to a different item or parks a gliding
     * cell that the pass no longer shows.
     *
     * @param node the node whose glide to cancel
     */
    void cancel(Node node) {
        Timeline prior = running.remove(node);
        if (prior == null) {
            return;
        }
        prior.stop();
        node.setTranslateX(0);
        node.setTranslateY(0);
    }

    /**
     * Stops every running glide and snaps all nodes to their final state. Used on
     * animation disable, cell-pool rebuild and disposal. {@code onFinished} is not
     * invoked; the caller must clear its own pin-set.
     */
    void snapAll() {
        for (Map.Entry<Node, Timeline> entry : new ArrayList<>(running.entrySet())) {
            entry.getValue().stop();
            entry.getKey().setTranslateX(0);
            entry.getKey().setTranslateY(0);
        }
        running.clear();
    }
}
