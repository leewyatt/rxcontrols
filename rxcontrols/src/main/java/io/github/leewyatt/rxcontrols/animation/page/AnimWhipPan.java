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
import javafx.scene.effect.MotionBlur;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Whip-pan transition. Both pages slide simultaneously (like
 * {@link AnimSlide}), but a {@link MotionBlur} effect ramps up to a
 * peak at mid-transition and ramps back down, simulating the look of
 * a fast camera whip.
 *
 * <p>The blur intensity follows a bell curve (sine): zero at the
 * start and end, maximum at the halfway point where the two pages
 * cross.</p>
 *
 * <p>Supports both horizontal and vertical orientations.</p>
 *
 * <p>This animation is resize-safe.</p>
 */
public class AnimWhipPan extends PageAnimationBase {

    private final Orientation orientation;
    private Interpolator interpolator = Interpolator.LINEAR;
    private double maxBlurRadius = 50;

    private ChangeListener<Number> sizeListener;
    private StackPane animatingContentPane;
    private MotionBlur blurCurrent;
    private MotionBlur blurNext;

    /**
     * Creates a horizontal whip-pan animation.
     */
    public AnimWhipPan() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a whip-pan animation with the given orientation.
     *
     * @param orientation the slide orientation
     */
    public AnimWhipPan(Orientation orientation) {
        this.orientation = orientation;
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

    /**
     * Returns the maximum blur radius at the midpoint.
     *
     * @return the max blur radius
     */
    public double getMaxBlurRadius() {
        return maxBlurRadius;
    }

    /**
     * Sets the maximum blur radius at the midpoint.
     * Recommended range: 20–80.
     *
     * @param maxBlurRadius the max blur radius (minimum 0)
     */
    public void setMaxBlurRadius(double maxBlurRadius) {
        this.maxBlurRadius = Math.max(0, maxBlurRadius);
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
        double blurAngle = horizontal ? 0 : 90;

        // Set up MotionBlur effects
        blurCurrent = new MotionBlur(blurAngle, 0);
        blurNext = new MotionBlur(blurAngle, 0);

        if (currentPage != null) {
            currentPage.setEffect(blurCurrent);
        }

        if (nextPage != null) {
            double initialOffset = sign * (horizontal ? contentPane.getWidth() : contentPane.getHeight());
            if (horizontal) {
                nextPage.setTranslateX(initialOffset);
            } else {
                nextPage.setTranslateY(initialOffset);
            }
            nextPage.setVisible(true);
            nextPage.setEffect(blurNext);
        }

        DoubleProperty progress = new SimpleDoubleProperty(0);

        Runnable updatePositions = () -> {
            double p = progress.get();
            double size = horizontal ? contentPane.getWidth() : contentPane.getHeight();

            // Slide both pages
            if (currentPage != null) {
                if (horizontal) {
                    currentPage.setTranslateX(-sign * size * p);
                } else {
                    currentPage.setTranslateY(-sign * size * p);
                }
            }
            if (nextPage != null) {
                if (horizontal) {
                    nextPage.setTranslateX(sign * size * (1.0 - p));
                } else {
                    nextPage.setTranslateY(sign * size * (1.0 - p));
                }
            }

            // Bell curve blur: sin(p * PI) peaks at 0.5
            double blur = maxBlurRadius * Math.sin(p * Math.PI);
            if (blurCurrent != null) {
                blurCurrent.setRadius(blur);
            }
            if (blurNext != null) {
                blurNext.setRadius(blur);
            }
        };

        Runnable finish = () -> {
            removeSizeListener();
            if (currentPage != null) {
                currentPage.setTranslateX(0);
                currentPage.setTranslateY(0);
                currentPage.setEffect(null);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setTranslateX(0);
                nextPage.setTranslateY(0);
                nextPage.setEffect(null);
                nextPage.setVisible(true);
            }
            blurCurrent = null;
            blurNext = null;
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        progress.addListener((obs, oldVal, newVal) -> updatePositions.run());
        // Apply p=0 state before first render frame to prevent nextPage flicker
        updatePositions.run();

        removeSizeListener();
        animatingContentPane = contentPane;
        sizeListener = (obs, oldVal, newVal) -> updatePositions.run();
        if (horizontal) {
            contentPane.widthProperty().addListener(sizeListener);
        } else {
            contentPane.heightProperty().addListener(sizeListener);
        }

        Timeline timeline = new Timeline(
                new KeyFrame(duration, new KeyValue(progress, 1.0, interpolator))
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
        removeSizeListener();
        for (Node child : context.getContentPane().getChildren()) {
            child.setTranslateX(0);
            child.setTranslateY(0);
            child.setEffect(null);
        }
        blurCurrent = null;
        blurNext = null;
    }

    @Override
    public void dispose() {
        removeSizeListener();
        blurCurrent = null;
        blurNext = null;
        super.dispose();
    }

    private void removeSizeListener() {
        if (sizeListener != null && animatingContentPane != null) {
            animatingContentPane.widthProperty().removeListener(sizeListener);
            animatingContentPane.heightProperty().removeListener(sizeListener);
            sizeListener = null;
            animatingContentPane = null;
        }
    }
}
