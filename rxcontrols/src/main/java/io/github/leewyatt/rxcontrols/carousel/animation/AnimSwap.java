package io.github.leewyatt.rxcontrols.carousel.animation;

import io.github.leewyatt.rxcontrols.carousel.Direction;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Swap transition. The current page and the next page exchange positions
 * along arc-shaped paths that dip downward at the midpoint, like two
 * cards being swapped by hand — inspired by the Keynote "Swap" effect.
 *
 * <p>Both pages scale down as they pass through the center crossing
 * point, then scale back up as they settle into their final positions.
 * The front/back layer order flips at the midpoint, reinforcing the
 * sense that the pages orbit around each other.</p>
 *
 * <p>The {@link #setArcHeight(double) arcHeight} parameter controls
 * how far the pages dip during the crossing:</p>
 * <ul>
 *   <li><b>0.05 ~ 0.10:</b> subtle, nearly flat arc.</li>
 *   <li><b>0.10 ~ 0.25:</b> moderate arc, clear swapping motion.
 *       <em>(default: 0.15)</em></li>
 *   <li><b>0.25 ~ 0.50:</b> dramatic deep arc.</li>
 * </ul>
 *
 * <p>FORWARD: current page exits left, next enters from right.
 * BACKWARD: reversed.</p>
 *
 * <p>This animation does not use snapshots and is fully resize-safe.</p>
 */
public class AnimSwap extends CarouselAnimationBase {

    private double arcHeight;
    private double midScale;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private StackPane animContentPane;

    /**
     * Creates a swap animation with default arc height (0.15) and
     * mid-scale (0.7).
     */
    public AnimSwap() {
        this(0.15, 0.7);
    }

    /**
     * Creates a swap animation with the given parameters.
     *
     * @param arcHeight the vertical dip as a fraction of viewport height
     *                  (see class javadoc for effect of different ranges)
     * @param midScale  the scale factor at the crossing point
     *                  (at least 0.2, recommended 0.5 to 0.85)
     */
    public AnimSwap(double arcHeight, double midScale) {
        this.arcHeight = Math.max(0.01, arcHeight);
        this.midScale = Math.max(0.2, Math.min(1.0, midScale));
    }

    /**
     * Returns the interpolator used for the animation.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator used for the animation.
     *
     * @param interpolator the interpolator
     */
    public void setInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    /**
     * Returns the arc height as a fraction of viewport height.
     *
     * @return the arc height
     */
    public double getArcHeight() {
        return arcHeight;
    }

    /**
     * Sets how far the pages dip during the crossing, as a fraction
     * of viewport height.
     *
     * @param arcHeight see class javadoc for effect of different ranges
     */
    public void setArcHeight(double arcHeight) {
        this.arcHeight = Math.max(0.01, arcHeight);
    }

    /**
     * Returns the scale factor at the crossing midpoint.
     *
     * @return the mid-scale
     */
    public double getMidScale() {
        return midScale;
    }

    /**
     * Sets the scale factor at the crossing midpoint. Smaller values
     * create a stronger sense of depth as pages pass behind each other.
     *
     * @param midScale at least 0.2, recommended 0.5 to 0.85
     */
    public void setMidScale(double midScale) {
        this.midScale = Math.max(0.2, Math.min(1.0, midScale));
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        Direction direction = context.getDirection();
        animContentPane = context.getContentPane();

        boolean forward = direction == Direction.FORWARD;

        if (nextPage != null) {
            nextPage.setVisible(true);
        }
        if (currentPage != null) {
            currentPage.setVisible(true);
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                resetNode(currentPage);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                resetNode(nextPage);
                nextPage.setVisible(true);
            }
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        if (progressListener != null) {
            progress.removeListener(progressListener);
        }
        progress.set(0);

        progressListener = (obs, oldVal, newVal) -> {
            double w = animContentPane.getWidth();
            double h = animContentPane.getHeight();
            updateSwap(newVal.doubleValue(), w, h,
                    currentPage, nextPage, forward);
        };
        progress.addListener(progressListener);
        // Apply p=0 state before first render frame to prevent nextPage flicker
        progressListener.changed(progress, 0.0, 0.0);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(duration,
                        new KeyValue(progress, 1, interpolator))
        );

        timeline.setOnFinished(e -> {
            finish.run();
            context.fireClosed(context.getCurrentIndex());
            context.fireOpened(context.getNextIndex());
        });

        setCurrentAnimation(timeline);
        return timeline;
    }

    private void updateSwap(double p, double w, double h,
                            Node currentPage, Node nextPage,
                            boolean forward) {
        // Convention: FORWARD = current exits left, next enters from right
        double sign = forward ? -1 : 1;
        double travel = w * 0.5;

        // Parabolic arc: peaks at p=0.5, value = arcHeight * h
        double dip = 4.0 * arcHeight * h * p * (1.0 - p);

        // Scale: dips to midScale at p=0.5, returns to 1.0 at endpoints
        // Using parabola: scale = 1 - (1-midScale) * 4*p*(1-p)
        double scaleDip = 1.0 - (1.0 - midScale) * 4.0 * p * (1.0 - p);

        // Current page: arcs from center to exit side
        if (currentPage != null) {
            currentPage.setTranslateX(sign * travel * p);
            currentPage.setTranslateY(dip);
            currentPage.setScaleX(scaleDip);
            currentPage.setScaleY(scaleDip);
            // Behind after midpoint
            currentPage.setViewOrder(p < 0.5 ? -1 : 0);
        }

        // Next page: arcs from enter side to center
        if (nextPage != null) {
            nextPage.setTranslateX(-sign * travel * (1.0 - p));
            nextPage.setTranslateY(dip);
            nextPage.setScaleX(scaleDip);
            nextPage.setScaleY(scaleDip);
            // In front after midpoint
            nextPage.setViewOrder(p < 0.5 ? 0 : -1);
        }
    }

    private void resetNode(Node node) {
        if (node != null) {
            node.setTranslateX(0);
            node.setTranslateY(0);
            node.setScaleX(1);
            node.setScaleY(1);
            node.setViewOrder(0);
        }
    }

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        for (Node child : context.getContentPane().getChildren()) {
            resetNode(child);
        }
        animContentPane = null;
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        animContentPane = null;
        super.dispose();
    }
}
