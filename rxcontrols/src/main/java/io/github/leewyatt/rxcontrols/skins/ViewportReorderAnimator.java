package io.github.leewyatt.rxcontrols.skins;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.WritableDoubleValue;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
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
 * <p>Two guarantees hold whatever runs in between, listeners on the glided
 * translate included:
 * <ul>
 *   <li>A glide that was cancelled or replaced, even while its start position was
 *   being written, never writes to its node again. JavaFX still pulses an
 *   animation that another animation's handler stopped earlier in the same frame
 *   (a handler that lays the viewport out, such as a drag's auto-scroll tick,
 *   cancels glides that way), so ownership is checked on every write of each
 *   axis, not only when the glide is stopped.</li>
 *   <li>Each glide starts only if the owner still animates at that moment. Settings
 *   can change in the middle of a layout pass that already decided to glide, and
 *   a disabled owner's duration may be one no {@link KeyFrame} accepts.</li>
 * </ul>
 *
 * <p>The tween itself is a plain {@link KeyValue} that starts from the node's actual
 * translate, so every interpolator, tangent ones included, shapes it as it would
 * shape the node's own property.
 *
 * <p>This class only manages the tween + transform cleanup; the viewport owns the
 * recycler pin-set (a gliding cell must not be parked or rebound without first
 * cancelling its glide) and removes the node from that set in the
 * {@code onFinished} callback, or itself after {@link #cancel} / {@link #snapAll}.
 */
final class ViewportReorderAnimator {

    // Sub-pixel moves are snapped rather than animated (no 0-frame Timeline churn).
    private static final double MOVE_EPSILON = 0.5;

    private final BooleanSupplier animationEnabled;
    private final Map<Node, Glide> running = new HashMap<>();

    /**
     * Creates an animator for an owner whose animation settings can change at any time.
     *
     * @param animationEnabled whether the owner animates right now; read each time a
     *                         glide is about to start
     */
    ViewportReorderAnimator(BooleanSupplier animationEnabled) {
        this.animationEnabled = animationEnabled;
    }

    /**
     * Glides {@code node} from the given translate delta back to zero over
     * {@code duration} using {@code interpolator}. If a tween is already running
     * for the node it is stopped and replaced (re-aim, not restart-from-scratch).
     * Sub-pixel deltas, and any delta while the owner no longer animates, snap
     * immediately. {@code onFinished} runs after the transforms are reset, on a
     * natural finish or a snap, so the caller can un-pin / recycle.
     */
    void animate(Node node, double fromDx, double fromDy, Duration duration, Interpolator interpolator,
                 Consumer<Node> onFinished) {
        Glide prior = running.remove(node);
        if (prior != null) {
            prior.stop();
        }
        boolean subPixel = Math.abs(fromDx) < MOVE_EPSILON && Math.abs(fromDy) < MOVE_EPSILON;
        if (subPixel || !animationEnabled.getAsBoolean()) {
            node.setTranslateX(0);
            node.setTranslateY(0);
            onFinished.accept(node);
            return;
        }
        // Registered before the start position is written, so a listener on those
        // writes that cancels the glide finds it. The layout already moved the node
        // to its new slot; the start position shows the old one in this same frame.
        Glide glide = new Glide(node, duration, interpolator, onFinished);
        running.put(node, glide);
        boolean playing = false;
        try {
            node.setTranslateX(fromDx);
            if (!glide.ownsNode()) {
                return;
            }
            node.setTranslateY(fromDy);
            if (!glide.ownsNode()) {
                return;
            }
            glide.play();
            playing = true;
        } finally {
            if (!playing) {
                // A start that never played (cancelled, replaced, or a write that threw,
                // as on a bound translate) must not stay registered: snapAll would
                // write to the node again.
                running.remove(node, glide);
            }
        }
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
     * invoked; the caller must clear its own pin-set. A reset that throws (a bound
     * translate) does not stop the others; the first failure is rethrown once every
     * node has been reset.
     */
    void snapAll() {
        // Released and stopped before any reset: none of them owns its node if it
        // pulses late, and none is left frozen mid-glide when a reset throws.
        Map<Node, Glide> stopped = new HashMap<>(running);
        running.clear();
        List<Runnable> resets = new ArrayList<>();
        for (Map.Entry<Node, Glide> entry : stopped.entrySet()) {
            entry.getValue().stop();
            Node node = entry.getKey();
            resets.add(() -> node.setTranslateX(0));
            resets.add(() -> node.setTranslateY(0));
        }
        SkinDisposer.disposeInOrder(resets.toArray(new Runnable[0]));
    }

    // ==================== Glide ====================

    private final class Glide {

        private final Node node;
        private final Timeline timeline;
        private final Consumer<Node> finishCallback;

        Glide(Node node, Duration duration, Interpolator interpolator, Consumer<Node> finishCallback) {
            this.node = node;
            this.finishCallback = finishCallback;
            timeline = new Timeline(new KeyFrame(duration,
                    new KeyValue(new Axis(node.translateXProperty()), 0.0, interpolator),
                    new KeyValue(new Axis(node.translateYProperty()), 0.0, interpolator)));
            timeline.setOnFinished(event -> finish());
        }

        void play() {
            timeline.play();
        }

        void stop() {
            timeline.stop();
        }

        boolean ownsNode() {
            return running.get(node) == this;
        }

        private void finish() {
            if (!ownsNode()) {
                return;
            }
            running.remove(node);
            node.setTranslateX(0);
            node.setTranslateY(0);
            finishCallback.accept(node);
        }

        // The tween's target for one translate axis: it reads the node's property, so
        // the tween starts wherever the node actually is, and writes to it only while
        // this glide still owns the node.
        private final class Axis implements WritableDoubleValue {

            private final DoubleProperty translate;

            Axis(DoubleProperty translate) {
                this.translate = translate;
            }

            @Override
            public double get() {
                return translate.get();
            }

            @Override
            public void set(double newValue) {
                if (ownsNode()) {
                    translate.set(newValue);
                }
            }

            @Override
            public Number getValue() {
                return translate.get();
            }

            @Override
            public void setValue(Number newValue) {
                set(newValue == null ? 0.0 : newValue.doubleValue());
            }
        }
    }
}
