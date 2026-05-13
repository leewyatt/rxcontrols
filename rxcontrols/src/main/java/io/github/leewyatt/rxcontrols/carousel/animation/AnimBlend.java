package io.github.leewyatt.rxcontrols.carousel.animation;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.util.Duration;

/**
 * Blend transition. The current page is instantly hidden, then a blend effect
 * on the next page fades from full intensity to zero, creating a color-burn
 * or other blend mode reveal.
 */
public class AnimBlend extends CarouselAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private final Blend blend = new Blend();
    private BlendMode blendMode;

    /**
     * Creates a blend animation with {@link BlendMode#COLOR_BURN}.
     */
    public AnimBlend() {
        this(BlendMode.COLOR_BURN);
    }

    /**
     * Creates a blend animation with the given blend mode.
     *
     * @param blendMode the blend mode to use
     */
    public AnimBlend(BlendMode blendMode) {
        this.blendMode = blendMode;
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
     * Returns the blend mode.
     *
     * @return the blend mode
     */
    public BlendMode getBlendMode() {
        return blendMode;
    }

    /**
     * Sets the blend mode.
     *
     * @param blendMode the blend mode
     */
    public void setBlendMode(BlendMode blendMode) {
        this.blendMode = blendMode;
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();

        blend.setMode(blendMode);

        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.setEffect(blend);
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setVisible(false);
                currentPage.setOpacity(1.0);
            }
            if (nextPage != null) {
                nextPage.setVisible(true);
                nextPage.setEffect(null);
                nextPage.setOpacity(1.0);
            }
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        // Instantly hide current page
        if (currentPage != null) {
            currentPage.setVisible(false);
        }
        context.fireClosed(context.getCurrentIndex());

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(blend.opacityProperty(), 1)),
                new KeyFrame(duration, new KeyValue(blend.opacityProperty(), 0, interpolator))
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
            child.setOpacity(1.0);
        }
    }
}
