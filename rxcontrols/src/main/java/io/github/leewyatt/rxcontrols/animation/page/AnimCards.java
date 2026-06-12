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
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Card-swipe transition inspired by Tinder-style swiping. The current page
 * flies off to one side with a slight rotation, while the next page scales
 * up from beneath.
 *
 * <p>This animation is resize-safe: it reads the content pane width on
 * every frame to compute the translate offset.</p>
 */
public class AnimCards extends PageAnimationBase {

    private double maxRotation = 8;
    private double backgroundScale = 0.92;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private StackPane animContentPane;

    /**
     * Creates a card-swipe animation with default parameters.
     */
    public AnimCards() {
    }

    /**
     * Returns the maximum rotation angle (degrees) of the flying card.
     *
     * @return the max rotation
     */
    public double getMaxRotation() {
        return maxRotation;
    }

    /**
     * Sets the maximum rotation angle. Default is 8 degrees.
     *
     * @param maxRotation the max rotation
     */
    public void setMaxRotation(double maxRotation) {
        this.maxRotation = maxRotation;
    }

    /**
     * Returns the initial scale of the background (next) page.
     *
     * @return the background scale
     */
    public double getBackgroundScale() {
        return backgroundScale;
    }

    /**
     * Sets the initial scale of the background page. Default is 0.92.
     *
     * @param backgroundScale value between 0.0 and 1.0 (recommended 0.8 to 0.95)
     */
    public void setBackgroundScale(double backgroundScale) {
        this.backgroundScale = Math.max(0.0, Math.min(1.0, backgroundScale));
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
        animContentPane = context.getContentPane();

        boolean forward = direction == TransitionDirection.FORWARD;

        // Next page starts behind, slightly scaled down
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.setScaleX(backgroundScale);
            nextPage.setScaleY(backgroundScale);
            nextPage.setOpacity(0.6);
            nextPage.toBack();
        }

        // Current page on top
        if (currentPage != null) {
            currentPage.setVisible(true);
            currentPage.setTranslateX(0);
            currentPage.setRotate(0);
            currentPage.setOpacity(1);
            currentPage.toFront();
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setTranslateX(0);
                currentPage.setRotate(0);
                currentPage.setOpacity(1);
                currentPage.setScaleX(1);
                currentPage.setScaleY(1);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setScaleX(1);
                nextPage.setScaleY(1);
                nextPage.setOpacity(1);
                nextPage.setTranslateX(0);
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
            double w = animContentPane.getWidth();
            updateTransforms(p, w, currentPage, nextPage, forward);
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

    private void updateTransforms(double p, double w,
                                   Node currentPage, Node nextPage, boolean forward) {
        double sign = forward ? -1.0 : 1.0;

        // Current page: fly off to the side with rotation
        if (currentPage != null) {
            currentPage.setTranslateX(sign * w * 1.2 * p);
            currentPage.setRotate(sign * -maxRotation * p);
            currentPage.setOpacity(1.0 - p * 0.5);
        }

        // Next page: scale up from background
        if (nextPage != null) {
            double scale = backgroundScale + (1.0 - backgroundScale) * p;
            nextPage.setScaleX(scale);
            nextPage.setScaleY(scale);
            nextPage.setOpacity(0.6 + 0.4 * p);
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
            child.setTranslateX(0);
            child.setRotate(0);
            child.setScaleX(1);
            child.setScaleY(1);
            child.setOpacity(1);
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
