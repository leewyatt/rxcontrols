package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.effect.MotionBlur;
import javafx.util.Duration;

/**
 * SlideIn transition (QQ Music style). The current page is instantly hidden,
 * then the next page slides in slightly with a motion blur that clears.
 */
public class AnimSlideIn extends PageAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private final MotionBlur blur = new MotionBlur();
    private double offsetX;
    private double blurRadius;

    /**
     * Creates a slide-in animation with default offset (10) and blur radius (5).
     */
    public AnimSlideIn() {
        this(10, 5);
    }

    /**
     * Creates a slide-in animation with the given parameters.
     *
     * @param offsetX    the horizontal offset for the slide-in
     * @param blurRadius the initial motion blur radius
     */
    public AnimSlideIn(double offsetX, double blurRadius) {
        this.offsetX = offsetX;
        this.blurRadius = blurRadius;
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
     * Returns the horizontal offset for the slide-in.
     *
     * @return the horizontal offset
     */
    public double getOffsetX() {
        return offsetX;
    }

    /**
     * Sets the horizontal offset for the slide-in.
     *
     * @param offsetX the horizontal offset
     */
    public void setOffsetX(double offsetX) {
        this.offsetX = offsetX;
    }

    /**
     * Returns the blur radius for the motion blur effect.
     *
     * @return the blur radius
     */
    public double getBlurRadius() {
        return blurRadius;
    }

    /**
     * Sets the blur radius for the motion blur effect.
     *
     * @param blurRadius the blur radius
     */
    public void setBlurRadius(double blurRadius) {
        this.blurRadius = blurRadius;
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setVisible(true);
                nextPage.setEffect(null);
                nextPage.setTranslateX(0);
            }
        };
        setFinishAction(finish);

        if (nextPage == null) {
            finish.run();
            return new Timeline();
        }

        nextPage.setVisible(true);
        blur.setRadius(blurRadius);
        nextPage.setEffect(blur);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        // Instantly hide current page
        if (currentPage != null) {
            currentPage.setVisible(false);
        }
        context.fireClosed(context.getCurrentIndex());

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(nextPage.translateXProperty(), offsetX),
                        new KeyValue(blur.radiusProperty(), blurRadius)),
                new KeyFrame(duration,
                        new KeyValue(nextPage.translateXProperty(), 0, interpolator),
                        new KeyValue(blur.radiusProperty(), 0, interpolator))
        );

        timeline.setOnFinished(e -> {
            finish.run();
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
            child.setTranslateX(0);
        }
    }
}
