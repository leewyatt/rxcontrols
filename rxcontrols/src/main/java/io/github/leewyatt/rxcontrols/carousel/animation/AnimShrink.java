package io.github.leewyatt.rxcontrols.carousel.animation;

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
 * Shrink transition. The current page shrinks toward the center like
 * a window being minimized, then fades out to reveal the next page
 * beneath.
 *
 * <p>The shrink and fade overlap: the page begins fading once it has
 * shrunk past the {@link #setFadeStart(double) fadeStart} threshold,
 * so the transition feels continuous rather than two-phased.</p>
 *
 * <p>This animation is not direction-sensitive.</p>
 *
 * <p>This animation is resize-safe.</p>
 */
public class AnimShrink extends CarouselAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private double minScale = 0.15;
    private double fadeStart = 0.4;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private StackPane animContentPane;

    /**
     * Creates a shrink animation.
     */
    public AnimShrink() {
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
     * Returns the minimum scale the page shrinks to before fading out.
     *
     * @return the minimum scale (0.0–1.0)
     */
    public double getMinScale() {
        return minScale;
    }

    /**
     * Sets the minimum scale. Recommended range: 0.05–0.3.
     *
     * @param minScale the minimum scale (0.0–1.0)
     */
    public void setMinScale(double minScale) {
        this.minScale = Math.max(0.01, Math.min(0.8, minScale));
    }

    /**
     * Returns the progress value at which the fade begins.
     *
     * @return the fade start threshold (0.0–1.0)
     */
    public double getFadeStart() {
        return fadeStart;
    }

    /**
     * Sets the progress value at which the fade begins.
     * Recommended range: 0.3–0.6.
     *
     * @param fadeStart the fade start threshold (0.0–1.0)
     */
    public void setFadeStart(double fadeStart) {
        this.fadeStart = Math.max(0.0, Math.min(0.95, fadeStart));
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        animContentPane = context.getContentPane();

        // Next page behind, stationary
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
        }
        // Current page on top, will shrink and fade
        if (currentPage != null) {
            currentPage.setVisible(true);
            currentPage.toFront();
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setScaleX(1);
                currentPage.setScaleY(1);
                currentPage.setOpacity(1);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setVisible(true);
            }
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

            if (currentPage != null) {
                // Scale: 1 → minScale
                double scale = 1.0 - (1.0 - minScale) * p;
                currentPage.setScaleX(scale);
                currentPage.setScaleY(scale);

                // Opacity: hold at 1 until fadeStart, then fade to 0
                if (p <= fadeStart) {
                    currentPage.setOpacity(1.0);
                } else {
                    double fadeP = (p - fadeStart) / (1.0 - fadeStart);
                    currentPage.setOpacity(1.0 - fadeP);
                }
            }
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

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        for (Node child : context.getContentPane().getChildren()) {
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
