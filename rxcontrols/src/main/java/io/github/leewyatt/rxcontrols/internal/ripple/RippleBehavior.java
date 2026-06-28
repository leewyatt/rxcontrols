package io.github.leewyatt.rxcontrols.internal.ripple;

import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Pointer ripple state machine shared by ripple hosts.
 */
public final class RippleBehavior {

    private static final String RIPPLE_STYLE_CLASS = "ripple";
    // Bounds how many fading ripples may overlap under rapid clicking: only one
    // ripple grows at a time (see press), the rest are fading out. A value >= 2
    // keeps the overlap smooth; it stays small so runaway input cannot
    // accumulate ripple nodes and timelines without bound. Not a Material rule.
    private static final int MAX_RIPPLE_COUNT = 5;
    // Press ripple timing favours a desktop M2 / JFoenix feel: a slow, clearly
    // visible grow rather than M3's near-instant subtle state layer.
    private static final Duration ENTER_DURATION = Duration.millis(500.0);
    private static final Duration EXIT_DURATION = Duration.millis(375.0);
    // Opacity reaches its peak well before the grow finishes and then holds, so
    // the still-small circle is already visible while it expands. Ramping
    // opacity together with scale (as one fast EASE_OUT) hides the small phase
    // and reads as "started big".
    private static final Duration OPACITY_RAMP_DURATION = Duration.millis(120.0);
    private static final double MINIMUM_VISIBLE_MILLIS = 150.0;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    // Decelerating grow (Material standard easing): the gentle start keeps the
    // small circle on screen long enough to read, unlike a front-loaded EASE_OUT.
    private static final Interpolator GROW_INTERPOLATOR = Interpolator.SPLINE(0.4, 0.0, 0.2, 1.0);

    private final RippleLayer layer;
    private final Supplier<Paint> fillSupplier;
    private final DoubleSupplier opacitySupplier;
    private final Deque<RippleHandle> ripples = new ArrayDeque<>();

    private RippleHandle activeRipple;

    /**
     * Creates a behavior for the given ripple layer.
     *
     * @param layer           the ripple layer
     * @param fillSupplier    supplies the fill for new ripples
     * @param opacitySupplier supplies the peak opacity for new ripples
     * @throws NullPointerException if any argument is {@code null}
     */
    public RippleBehavior(RippleLayer layer,
                          Supplier<Paint> fillSupplier,
                          DoubleSupplier opacitySupplier) {
        this.layer = Objects.requireNonNull(layer, "layer cannot be null");
        this.fillSupplier = Objects.requireNonNull(fillSupplier, "fillSupplier cannot be null");
        this.opacitySupplier = Objects.requireNonNull(opacitySupplier, "opacitySupplier cannot be null");
    }

    /**
     * Starts a ripple at the given layer-local coordinates.
     *
     * @param x        the layer-local x coordinate
     * @param y        the layer-local y coordinate
     * @param centered whether to ignore the coordinates and use the layer center
     */
    public void press(double x, double y, boolean centered) {
        if (activeRipple != null && !activeRipple.released) {
            return;
        }

        double width = layer.getWidth();
        double height = layer.getHeight();
        if (width <= 0.0 || height <= 0.0
                || !Double.isFinite(width) || !Double.isFinite(height)) {
            clear();
            return;
        }

        double centerX = centered ? width / 2.0 : clamp(x, 0.0, width);
        double centerY = centered ? height / 2.0 : clamp(y, 0.0, height);
        // The layer carries the radius knob: AUTO falls back to the hypot
        // center-to-bled-corner radius; an explicit value (small thumb controls)
        // is used directly. The (layer, fill, opacity) ctor stays unchanged.
        double explicitRadius = layer.getRippleRadius();
        double radius = explicitRadius >= 0.0 && Double.isFinite(explicitRadius)
                ? explicitRadius
                : computeAutoRadius(width, height, centerX, centerY, layer.getRippleBleed());
        if (radius <= 0.0 || !Double.isFinite(radius)) {
            clear();
            return;
        }

        Circle circle = new Circle(centerX, centerY, radius);
        circle.getStyleClass().add(RIPPLE_STYLE_CLASS);
        circle.setManaged(false);
        circle.setFill(fillSupplier.get());
        circle.setOpacity(0.0);
        circle.setScaleX(0.0);
        circle.setScaleY(0.0);

        RippleHandle handle = new RippleHandle(circle, System.nanoTime());
        ripples.addLast(handle);
        layer.addRipple(circle);
        trimToLimit();
        activeRipple = handle;

        double targetOpacity = RXMath.clamp0To1(opacitySupplier.getAsDouble());
        handle.enterTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(circle.scaleXProperty(), 0.0),
                        new KeyValue(circle.scaleYProperty(), 0.0),
                        new KeyValue(circle.opacityProperty(), 0.0)),
                new KeyFrame(OPACITY_RAMP_DURATION,
                        new KeyValue(circle.opacityProperty(), targetOpacity, Interpolator.EASE_OUT)),
                new KeyFrame(ENTER_DURATION,
                        new KeyValue(circle.scaleXProperty(), 1.0, GROW_INTERPOLATOR),
                        new KeyValue(circle.scaleYProperty(), 1.0, GROW_INTERPOLATOR),
                        new KeyValue(circle.opacityProperty(), targetOpacity)));
        handle.enterTimeline.play();
    }

    /**
     * Releases the active ripple, fading it out after the minimum visible
     * interval if needed.
     */
    public void release() {
        RippleHandle handle = activeRipple;
        if (handle == null || handle.released) {
            activeRipple = null;
            return;
        }
        activeRipple = null;
        handle.released = true;

        double visibleMillis = (System.nanoTime() - handle.pressNanos) / NANOS_PER_MILLI;
        double delayMillis = Math.max(0.0, MINIMUM_VISIBLE_MILLIS - visibleMillis);
        if (delayMillis <= 0.0) {
            startExit(handle);
            return;
        }

        handle.releaseDelay = new PauseTransition(Duration.millis(delayMillis));
        handle.releaseDelay.setOnFinished(event -> startExit(handle));
        handle.releaseDelay.play();
    }

    /**
     * Stops all live animations and removes all ripple nodes.
     */
    public void clear() {
        activeRipple = null;
        for (RippleHandle handle : ripples) {
            stop(handle);
        }
        ripples.clear();
        layer.clearRipples();
    }

    private void startExit(RippleHandle handle) {
        if (!ripples.contains(handle)) {
            return;
        }
        stop(handle.enterTimeline);
        handle.enterTimeline = null;

        Circle circle = handle.circle;
        handle.exitTimeline = new Timeline(
                new KeyFrame(EXIT_DURATION,
                        new KeyValue(circle.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(circle.scaleYProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(circle.opacityProperty(), 0.0, Interpolator.EASE_OUT)));
        handle.exitTimeline.setOnFinished(event -> remove(handle));
        handle.exitTimeline.play();
    }

    private void remove(RippleHandle handle) {
        stop(handle);
        ripples.remove(handle);
        layer.removeRipple(handle.circle);
        if (activeRipple == handle) {
            activeRipple = null;
        }
    }

    private void trimToLimit() {
        while (ripples.size() > MAX_RIPPLE_COUNT) {
            RippleHandle oldest = ripples.removeFirst();
            stop(oldest);
            layer.removeRipple(oldest.circle);
            if (activeRipple == oldest) {
                activeRipple = null;
            }
        }
    }

    private static double computeAutoRadius(double width, double height,
                                            double centerX, double centerY, Insets bleed) {
        double maxX = Math.max(centerX + bleed.getLeft(), width - centerX + bleed.getRight());
        double maxY = Math.max(centerY + bleed.getTop(), height - centerY + bleed.getBottom());
        return Math.hypot(maxX, maxY);
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static void stop(RippleHandle handle) {
        stop(handle.enterTimeline);
        stop(handle.releaseDelay);
        stop(handle.exitTimeline);
        handle.enterTimeline = null;
        handle.releaseDelay = null;
        handle.exitTimeline = null;
    }

    private static void stop(Animation animation) {
        if (animation != null) {
            animation.stop();
        }
    }

    private static final class RippleHandle {

        private final Circle circle;
        private final long pressNanos;

        private Timeline enterTimeline;
        private PauseTransition releaseDelay;
        private Timeline exitTimeline;
        private boolean released;

        private RippleHandle(Circle circle, long pressNanos) {
            this.circle = circle;
            this.pressNanos = pressNanos;
        }
    }
}
