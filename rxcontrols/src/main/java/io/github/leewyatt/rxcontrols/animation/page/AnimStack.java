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
import javafx.util.Duration;

/**
 * Stacking transition. The current page slides away to reveal the next page
 * peeking from behind, then the next page slides into the center.
 *
 * <p>Works in both horizontal and vertical orientations.</p>
 */
public class AnimStack extends PageAnimationBase {

    private final Orientation orientation;
    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private double offsetRatio = 0.2;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    /**
     * Creates a horizontal stack animation.
     */
    public AnimStack() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a stack animation with the given orientation.
     *
     * @param orientation the slide orientation
     */
    public AnimStack(Orientation orientation) {
        this.orientation = orientation;
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
     * Returns the offset ratio for the peek distance (0.0 to 1.0).
     *
     * @return the offset ratio
     */
    public double getOffsetRatio() {
        return offsetRatio;
    }

    /**
     * Sets the offset ratio for the peek distance.
     *
     * @param offsetRatio value between 0.0 and 1.0 (clamped; fraction of pane size for peek distance)
     */
    public void setOffsetRatio(double offsetRatio) {
        this.offsetRatio = Math.max(0.0, Math.min(1.0, offsetRatio));
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        TransitionDirection direction = context.getDirection();
        StackPane contentPane = context.getContentPane();

        boolean horizontal = orientation == Orientation.HORIZONTAL;
        double sign = direction == TransitionDirection.FORWARD ? 1.0 : -1.0;

        if (nextPage != null) {
            nextPage.setVisible(true);
        }
        if (currentPage != null) {
            currentPage.toFront();
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setVisible(false);
                currentPage.setTranslateX(0);
                currentPage.setTranslateY(0);
            }
            if (nextPage != null) {
                nextPage.setVisible(true);
                nextPage.setTranslateX(0);
                nextPage.setTranslateY(0);
                nextPage.setScaleX(1.0);
                nextPage.setScaleY(1.0);
            }
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        // Remove old listener if present
        if (progressListener != null) {
            progress.removeListener(progressListener);
        }
        progress.set(0);

        // Set initial scale for nextPage
        if (nextPage != null) {
            nextPage.setScaleX(0.9);
            nextPage.setScaleY(0.9);
        }

        // Track whether z-order has been swapped
        final boolean[] swapped = {false};

        progressListener = (obs, oldVal, newVal) -> {
            double p = newVal.doubleValue();
            double paneWidth = contentPane.getWidth();
            double paneHeight = contentPane.getHeight();
            double paneSize = horizontal ? paneWidth : paneHeight;
            double peekOffset = paneSize * offsetRatio;

            // Z-order swap at midpoint
            if (!swapped[0] && p >= 0.5) {
                swapped[0] = true;
                if (nextPage != null) {
                    nextPage.toFront();
                }
            }

            // Scale: nextPage from 0.9 to 1.0 across the full animation
            if (nextPage != null) {
                double scale = 0.9 + 0.1 * p;
                nextPage.setScaleX(scale);
                nextPage.setScaleY(scale);
            }

            if (p <= 0.5) {
                // Phase 1: current slides out, next peeks from behind
                double pp = p * 2.0;
                if (horizontal) {
                    if (currentPage != null) {
                        currentPage.setTranslateX(sign * paneWidth * pp);
                    }
                    if (nextPage != null) {
                        nextPage.setTranslateX(-sign * peekOffset * pp);
                    }
                } else {
                    if (currentPage != null) {
                        currentPage.setTranslateY(sign * paneHeight * pp);
                    }
                    if (nextPage != null) {
                        nextPage.setTranslateY(-sign * peekOffset * pp);
                    }
                }
            } else {
                // Phase 2: mirror - both slide back toward center
                double pp = (p - 0.5) * 2.0;
                if (horizontal) {
                    if (currentPage != null) {
                        currentPage.setTranslateX(sign * paneWidth * (1.0 - pp));
                    }
                    if (nextPage != null) {
                        nextPage.setTranslateX(-sign * peekOffset * (1.0 - pp));
                    }
                } else {
                    if (currentPage != null) {
                        currentPage.setTranslateY(sign * paneHeight * (1.0 - pp));
                    }
                    if (nextPage != null) {
                        nextPage.setTranslateY(-sign * peekOffset * (1.0 - pp));
                    }
                }
            }
        };

        progress.addListener(progressListener);
        // Apply p=0 state before first render frame to prevent nextPage flicker
        progressListener.changed(progress, 0.0, 0.0);

        // Create timeline animating progress from 0 to 1
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

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        for (Node child : context.getContentPane().getChildren()) {
            child.setTranslateX(0);
            child.setTranslateY(0);
            child.setScaleX(1.0);
            child.setScaleY(1.0);
        }
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        super.dispose();
    }
}
