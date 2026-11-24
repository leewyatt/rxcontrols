package io.github.leewyatt.rxcontrols.skins;

import javafx.animation.Interpolator;
import javafx.animation.Transition;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reorder glide animator shared by the virtualized viewports and the kanban board
 * ({@link RXTileViewport}, {@code RXMasonryViewport}, {@code KanbanColumnViewport}
 * and {@code RXKanbanViewSkin}'s column boxes). Each node gets its own one-shot
 * glide that tweens {@code translateX/translateY} from a captured FLIP delta back
 * to zero, while layout owns the authoritative {@code layoutX/layoutY}.
 * Independent per-node glides (rather than one shared timeline) let a virtualized
 * cell be re-aimed mid-glide without disturbing the others, and let a cell that
 * finishes be recycled on its own.
 *
 * <p>A glide writes to its node only while it is the node's current glide. JavaFX
 * still pulses an animation that another animation's handler stopped earlier in
 * the same frame (a handler that lays the viewport out, such as a drag's
 * auto-scroll tick, cancels glides that way), and that late pulse would put a
 * stale translate back on a node already reset, recycled or re-aimed.
 *
 * <p>This class only manages the tween + transform cleanup; the viewport owns the
 * recycler pin-set (a gliding cell must not be parked or rebound without first
 * cancelling its glide) and removes the node from that set in the
 * {@code onFinished} callback, or itself after {@link #cancel} / {@link #snapAll}.
 */
final class ViewportReorderAnimator {

    // Sub-pixel moves are snapped rather than animated (no 0-frame glide churn).
    private static final double MOVE_EPSILON = 0.5;

    private final Map<Node, Glide> running = new HashMap<>();

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
        Glide prior = running.remove(node);
        if (prior != null) {
            prior.stop();
        }
        if (Math.abs(fromDx) < MOVE_EPSILON && Math.abs(fromDy) < MOVE_EPSILON) {
            node.setTranslateX(0);
            node.setTranslateY(0);
            onFinished.accept(node);
            return;
        }
        // The layout already moved the node to its new slot; show the old position
        // in this same frame, before the glide's first pulse.
        node.setTranslateX(fromDx);
        node.setTranslateY(fromDy);
        Glide glide = new Glide(node, fromDx, fromDy, duration, interpolator, onFinished);
        running.put(node, glide);
        glide.play();
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
        Glide prior = running.remove(node);
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
        // Released before stopping, so none of them still owns its node if it pulses late.
        Map<Node, Glide> stopped = new HashMap<>(running);
        running.clear();
        for (Map.Entry<Node, Glide> entry : stopped.entrySet()) {
            entry.getValue().stop();
            entry.getKey().setTranslateX(0);
            entry.getKey().setTranslateY(0);
        }
    }

    // ==================== Glide ====================

    private final class Glide extends Transition {

        private final Node node;
        private final double fromDx;
        private final double fromDy;
        private final Consumer<Node> finishCallback;

        Glide(Node node, double fromDx, double fromDy, Duration duration, Interpolator interpolator,
              Consumer<Node> finishCallback) {
            this.node = node;
            this.fromDx = fromDx;
            this.fromDy = fromDy;
            this.finishCallback = finishCallback;
            setCycleDuration(duration);
            setInterpolator(interpolator);
            setOnFinished(event -> finish());
        }

        // frac has already been through the interpolator.
        @Override
        protected void interpolate(double frac) {
            if (running.get(node) != this) {
                return;
            }
            node.setTranslateX(fromDx * (1.0 - frac));
            node.setTranslateY(fromDy * (1.0 - frac));
        }

        private void finish() {
            if (running.get(node) != this) {
                return;
            }
            running.remove(node);
            node.setTranslateX(0);
            node.setTranslateY(0);
            finishCallback.accept(node);
        }
    }
}
