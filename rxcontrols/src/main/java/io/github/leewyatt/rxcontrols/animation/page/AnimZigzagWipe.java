package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;

/**
 * Zigzag wipe transition. A sawtooth-edged boundary sweeps across
 * the page, revealing the next page behind it.
 *
 * <p>Unlike {@link AnimWipe} (straight-line boundary), the zigzag
 * edge gives a "torn paper" or serrated look.</p>
 *
 * <p>Supports both {@link Orientation#HORIZONTAL} (default, edge
 * sweeps left/right) and {@link Orientation#VERTICAL} (edge sweeps
 * up/down).</p>
 *
 * <p>This animation is resize-safe.</p>
 */
public class AnimZigzagWipe extends PageAnimationBase {

    private final Orientation orientation;
    private int toothCount = 12;
    private double toothDepthRatio = 0.045;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private StackPane animContentPane;
    private Polygon clipPolygon;

    /**
     * Creates a horizontal zigzag wipe animation.
     */
    public AnimZigzagWipe() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a zigzag wipe animation with the given orientation.
     *
     * @param orientation the wipe direction
     */
    public AnimZigzagWipe(Orientation orientation) {
        this.orientation = orientation;
    }

    /**
     * Returns the number of zigzag teeth.
     *
     * @return the tooth count
     */
    public int getToothCount() {
        return toothCount;
    }

    /**
     * Sets the number of zigzag teeth. Recommended range: 6–30.
     *
     * @param toothCount the tooth count (minimum 2)
     */
    public void setToothCount(int toothCount) {
        this.toothCount = Math.max(2, toothCount);
    }

    /**
     * Returns the tooth depth as a ratio of the wipe dimension.
     *
     * @return the tooth depth ratio
     */
    public double getToothDepthRatio() {
        return toothDepthRatio;
    }

    /**
     * Sets the tooth depth as a ratio of the wipe dimension.
     * Recommended range: 0.02–0.1.
     *
     * @param toothDepthRatio the ratio (0.0–1.0)
     */
    public void setToothDepthRatio(double toothDepthRatio) {
        this.toothDepthRatio = Math.max(0.005, Math.min(0.3, toothDepthRatio));
    }

    /**
     * Returns the interpolator.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator.
     *
     * @param interpolator the interpolator
     */
    public void setInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        TransitionDirection direction = context.getDirection();
        animContentPane = context.getContentPane();

        boolean horizontal = orientation == Orientation.HORIZONTAL;
        boolean forward = direction == TransitionDirection.FORWARD;

        // Next page on top, clipped by zigzag polygon
        if (currentPage != null) {
            currentPage.setVisible(true);
            currentPage.toBack();
        }
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toFront();
        }

        clipPolygon = new Polygon();
        if (nextPage != null) {
            nextPage.setClip(clipPolygon);
        }

        // Initialize clip to p=0 state
        rebuildPolygon(0, animContentPane.getWidth(), animContentPane.getHeight(),
                horizontal, forward);

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setClip(null);
                nextPage.setVisible(true);
            }
            clipPolygon = null;
            animContentPane = null;
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        if (progressListener != null) {
            progress.removeListener(progressListener);
        }
        progress.set(0);

        progressListener = (obs, oldVal, newVal) -> {
            double p = newVal.doubleValue();
            if (animContentPane == null || clipPolygon == null) {
                return;
            }
            double w = animContentPane.getWidth();
            double h = animContentPane.getHeight();

            if (p >= 1.0 && nextPage != null) {
                nextPage.setClip(null);
                return;
            }

            rebuildPolygon(p, w, h, horizontal, forward);
        };
        progress.addListener(progressListener);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(duration, new KeyValue(progress, 1, interpolator))
        );

        timeline.setOnFinished(e -> {
            finish.run();
            context.fireClosed(context.getCurrentIndex());
            context.fireOpened(context.getNextIndex());
        });

        setCurrentAnimation(timeline);
        return timeline;
    }

    /**
     * Rebuilds the clip polygon's point list for the current progress.
     *
     * <p>The polygon consists of a straight fill edge on one side and
     * a zigzag leading edge on the other. For HORIZONTAL orientation
     * the zigzag runs vertically; for VERTICAL it runs horizontally.</p>
     */
    private void rebuildPolygon(double p, double w, double h,
                                boolean horizontal, boolean forward) {
        if (clipPolygon == null) {
            return;
        }

        // (toothCount+1) zigzag points + 2 corner points = (toothCount+3) points
        // each point has x,y = (toothCount+3)*2 doubles
        int pointCount = toothCount + 3;
        Double[] coords = new Double[pointCount * 2];
        int idx = 0;

        if (horizontal) {
            double toothDepth = w * toothDepthRatio;
            double centerX = forward ? p * w : w * (1.0 - p);
            double sign = forward ? 1.0 : -1.0;
            double fillX = forward ? 0 : w;
            double segH = h / toothCount;

            // Start corner
            coords[idx++] = fillX;
            coords[idx++] = 0.0;

            // Zigzag edge (top to bottom)
            for (int i = 0; i <= toothCount; i++) {
                double y = i * segH;
                double x = (i % 2 == 0)
                        ? centerX + sign * toothDepth
                        : centerX - sign * toothDepth;
                coords[idx++] = x;
                coords[idx++] = y;
            }

            // End corner
            coords[idx++] = fillX;
            coords[idx++] = h;
        } else {
            double toothDepth = h * toothDepthRatio;
            double centerY = forward ? p * h : h * (1.0 - p);
            double sign = forward ? 1.0 : -1.0;
            double fillY = forward ? 0 : h;
            double segW = w / toothCount;

            // Start corner
            coords[idx++] = 0.0;
            coords[idx++] = fillY;

            // Zigzag edge (left to right)
            for (int i = 0; i <= toothCount; i++) {
                double x = i * segW;
                double y = (i % 2 == 0)
                        ? centerY + sign * toothDepth
                        : centerY - sign * toothDepth;
                coords[idx++] = x;
                coords[idx++] = y;
            }

            // End corner
            coords[idx++] = w;
            coords[idx++] = fillY;
        }

        clipPolygon.getPoints().setAll(coords);
    }

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        for (Node child : context.getContentPane().getChildren()) {
            child.setClip(null);
        }
        clipPolygon = null;
        animContentPane = null;
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        clipPolygon = null;
        animContentPane = null;
        super.dispose();
    }
}
