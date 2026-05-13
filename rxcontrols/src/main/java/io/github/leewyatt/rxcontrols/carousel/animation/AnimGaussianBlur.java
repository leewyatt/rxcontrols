package io.github.leewyatt.rxcontrols.carousel.animation;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.effect.GaussianBlur;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Gaussian blur cross-fade transition. The current page blurs and fades out
 * while the next page simultaneously sharpens and fades in.
 */
public class AnimGaussianBlur extends CarouselAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private final GaussianBlur blurCurrent = new GaussianBlur();
    private final GaussianBlur blurNext = new GaussianBlur();
    private double radius;

    /**
     * Creates a gaussian blur animation with default radius (30).
     */
    public AnimGaussianBlur() {
        this(30);
    }

    /**
     * Creates a gaussian blur animation with the given radius.
     *
     * @param radius the maximum blur radius
     */
    public AnimGaussianBlur(double radius) {
        this.radius = radius;
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
     * Returns the maximum blur radius.
     *
     * @return the maximum blur radius
     */
    public double getRadius() {
        return radius;
    }

    /**
     * Sets the maximum blur radius.
     *
     * @param radius the maximum blur radius
     */
    public void setRadius(double radius) {
        this.radius = radius;
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();

        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.setEffect(blurNext);
        }
        if (currentPage != null) {
            currentPage.toFront();
            currentPage.setEffect(blurCurrent);
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setEffect(null);
                currentPage.setOpacity(1.0);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setEffect(null);
                nextPage.setOpacity(1.0);
                nextPage.setVisible(true);
            }
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        List<KeyValue> startValues = new ArrayList<>();
        List<KeyValue> endValues = new ArrayList<>();

        if (currentPage != null) {
            startValues.add(new KeyValue(blurCurrent.radiusProperty(), 0.0));
            startValues.add(new KeyValue(currentPage.opacityProperty(), 1.0));
            endValues.add(new KeyValue(blurCurrent.radiusProperty(), radius, interpolator));
            endValues.add(new KeyValue(currentPage.opacityProperty(), 0.0, interpolator));
        }
        if (nextPage != null) {
            startValues.add(new KeyValue(blurNext.radiusProperty(), radius));
            startValues.add(new KeyValue(nextPage.opacityProperty(), 0.0));
            endValues.add(new KeyValue(blurNext.radiusProperty(), 0, interpolator));
            endValues.add(new KeyValue(nextPage.opacityProperty(), 1.0, interpolator));
        }

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, startValues.toArray(new KeyValue[0])),
                new KeyFrame(duration, endValues.toArray(new KeyValue[0]))
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
        for (Node child : context.getContentPane().getChildren()) {
            child.setEffect(null);
            child.setOpacity(1.0);
        }
    }
}
