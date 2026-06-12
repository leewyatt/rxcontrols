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
 * Motion blur transition. The current page blurs and fades out to reveal
 * the next page behind it.
 */
public class AnimMotionBlur extends PageAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private final MotionBlur motionBlur = new MotionBlur();
    private double radius;

    /**
     * Creates a motion blur animation with default radius (20).
     */
    public AnimMotionBlur() {
        this(20);
    }

    /**
     * Creates a motion blur animation with the given radius.
     *
     * @param radius the maximum blur radius (clamped to 0–63, the JavaFX MotionBlur API limit)
     */
    public AnimMotionBlur(double radius) {
        setRadius(radius);
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
     * Returns the blur radius.
     *
     * @return the blur radius
     */
    public double getRadius() {
        return radius;
    }

    /**
     * Sets the blur radius.
     *
     * @param radius the blur radius
     */
    public void setRadius(double radius) {
        this.radius = Math.max(0, Math.min(63, radius));
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setEffect(null);
                currentPage.setOpacity(1.0);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setVisible(true);
            }
        };
        setFinishAction(finish);

        if (currentPage == null) {
            context.fireClosing(context.getCurrentIndex());
            context.fireOpening(context.getNextIndex());
            finish.run();
            context.fireClosed(context.getCurrentIndex());
            context.fireOpened(context.getNextIndex());
            return new Timeline();
        }

        if (nextPage != null) {
            nextPage.setVisible(true);
        }
        currentPage.toFront();
        currentPage.setEffect(motionBlur);
        motionBlur.setRadius(0);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO),
                new KeyFrame(duration,
                        new KeyValue(motionBlur.radiusProperty(), radius, interpolator),
                        new KeyValue(currentPage.opacityProperty(), 0.0, interpolator))
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
