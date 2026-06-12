package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Newspaper-throw transition. The current page spins and shrinks to the
 * center, then the next page spins in from the center and grows to full size.
 *
 * <p>The rotation direction follows the {@link TransitionDirection}:
 * FORWARD spins clockwise out / counter-clockwise in, BACKWARD reverses.</p>
 *
 * <p>This animation is resize-safe: it only uses scale and rotate transforms
 * which are independent of absolute pixel dimensions.</p>
 */
public class AnimNewspaper extends PageAnimationBase {

    private double maxRotation = 720;
    private double minScale = 0.05;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    /**
     * Creates a newspaper animation with default parameters.
     */
    public AnimNewspaper() {
    }

    /**
     * Returns the maximum rotation angle (in degrees) for the spin effect.
     *
     * @return the max rotation angle
     */
    public double getMaxRotation() {
        return maxRotation;
    }

    /**
     * Sets the maximum rotation angle (in degrees). Higher values produce
     * more spins. Default is 720 (two full rotations).
     *
     * @param maxRotation the max rotation angle
     */
    public void setMaxRotation(double maxRotation) {
        this.maxRotation = maxRotation;
    }

    /**
     * Returns the minimum scale at the midpoint of the transition.
     *
     * @return the min scale
     */
    public double getMinScale() {
        return minScale;
    }

    /**
     * Sets the minimum scale at the midpoint. Default is 0.05 (nearly invisible).
     *
     * @param minScale value between 0.0 and 1.0 (clamped; the scale factor at the transition midpoint)
     */
    public void setMinScale(double minScale) {
        this.minScale = Math.max(0.0, Math.min(1.0, minScale));
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

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        TransitionDirection direction = context.getDirection();

        boolean forward = direction == TransitionDirection.FORWARD;
        double spinSign = forward ? 1.0 : -1.0;

        // Next page starts hidden, scaled down, rotated
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.setScaleX(minScale);
            nextPage.setScaleY(minScale);
            nextPage.setRotate(-spinSign * maxRotation);
            nextPage.setOpacity(0);
        }
        // Current page starts normal
        if (currentPage != null) {
            currentPage.setVisible(true);
            currentPage.setScaleX(1.0);
            currentPage.setScaleY(1.0);
            currentPage.setRotate(0);
            currentPage.setOpacity(1);
            currentPage.toFront();
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setScaleX(1.0);
                currentPage.setScaleY(1.0);
                currentPage.setRotate(0);
                currentPage.setOpacity(1);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setScaleX(1.0);
                nextPage.setScaleY(1.0);
                nextPage.setRotate(0);
                nextPage.setOpacity(1);
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
            double p = newVal.doubleValue();
            updateTransforms(p, currentPage, nextPage, spinSign);
        };
        progress.addListener(progressListener);
        // Apply p=0 state before first render frame to prevent nextPage flicker
        progressListener.changed(progress, 0.0, 0.0);

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
     * Updates transforms for both pages based on progress (0→1).
     * First half (0→0.5): current page spins out and shrinks.
     * Second half (0.5→1): next page spins in and grows.
     */
    private void updateTransforms(double p, Node currentPage, Node nextPage, double spinSign) {
        if (p <= 0.5) {
            // Phase 1: current page spins out (0→0.5 mapped to 0→1)
            double phase = p * 2.0;
            double scale = 1.0 - (1.0 - minScale) * phase;
            double rotation = spinSign * maxRotation * phase;
            double opacity = 1.0 - phase;

            if (currentPage != null) {
                currentPage.setScaleX(scale);
                currentPage.setScaleY(scale);
                currentPage.setRotate(rotation);
                currentPage.setOpacity(opacity);
            }
            if (nextPage != null) {
                nextPage.setOpacity(0);
            }
        } else {
            // Phase 2: next page spins in (0.5→1 mapped to 0→1)
            double phase = (p - 0.5) * 2.0;
            double scale = minScale + (1.0 - minScale) * phase;
            double rotation = -spinSign * maxRotation * (1.0 - phase);
            double opacity = phase;

            if (currentPage != null) {
                currentPage.setOpacity(0);
            }
            if (nextPage != null) {
                nextPage.setScaleX(scale);
                nextPage.setScaleY(scale);
                nextPage.setRotate(rotation);
                nextPage.setOpacity(opacity);
                nextPage.toFront();
            }
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
            child.setScaleX(1.0);
            child.setScaleY(1.0);
            child.setRotate(0);
            child.setOpacity(1);
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
