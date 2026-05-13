package io.github.leewyatt.rxcontrols.carousel.animation;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Cross-fade transition. The current page fades out while the next page
 * fades in simultaneously.
 */
public class AnimFade extends CarouselAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_BOTH;

    /**
     * Returns the interpolator used for the fade animation.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator used for the fade animation.
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

        if (nextPage != null) {
            nextPage.setOpacity(0);
            nextPage.setVisible(true);
            nextPage.toFront();
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setOpacity(1.0);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setOpacity(1.0);
                nextPage.setVisible(true);
            }
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        Timeline timeline = new Timeline();
        if (currentPage != null && nextPage != null) {
            timeline.getKeyFrames().add(new KeyFrame(duration,
                    new KeyValue(currentPage.opacityProperty(), 0, interpolator),
                    new KeyValue(nextPage.opacityProperty(), 1.0, interpolator)));
        } else if (nextPage != null) {
            timeline.getKeyFrames().add(new KeyFrame(duration,
                    new KeyValue(nextPage.opacityProperty(), 1.0, interpolator)));
        }

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
            child.setOpacity(1.0);
        }
    }
}
