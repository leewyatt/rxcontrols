package io.github.leewyatt.rxcontrols.carousel.animation;

import io.github.leewyatt.rxcontrols.carousel.Direction;
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
 * Parallax slide transition. The current page slides out at full speed
 * while the next page drifts in from a closer starting position at a
 * slower pace, creating a layered depth illusion — the hallmark of
 * modern parallax scrolling seen on Apple-style websites.
 *
 * <p>The {@link #setParallaxFactor(double) parallaxFactor} controls how
 * much distance the incoming page travels relative to the viewport
 * width:</p>
 * <ul>
 *   <li><b>0.1 ~ 0.2:</b> subtle drift — the new page barely moves,
 *       strong "reveal" feeling.</li>
 *   <li><b>0.2 ~ 0.4:</b> moderate parallax.
 *       <em>(default: 0.3)</em></li>
 *   <li><b>0.5 ~ 0.8:</b> mild parallax — closer to a regular slide
 *       but still with noticeable speed difference.</li>
 *   <li><b>1.0:</b> no parallax — identical to {@link AnimSlide}.</li>
 * </ul>
 *
 * <p>FORWARD: current page exits left, next enters from right.
 * BACKWARD: reversed.</p>
 *
 * <p>This animation is resize-safe: it reads the content pane
 * dimensions on every frame.</p>
 */
public class AnimParallax extends CarouselAnimationBase {

    private double parallaxFactor;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private StackPane animContentPane;

    /**
     * Creates a parallax animation with a factor of 0.3.
     */
    public AnimParallax() {
        this(0.3);
    }

    /**
     * Creates a parallax animation with the given factor.
     *
     * @param parallaxFactor the travel ratio for the incoming page
     *                       (0.0 to 1.0; see class javadoc)
     */
    public AnimParallax(double parallaxFactor) {
        this.parallaxFactor = Math.max(0.0, Math.min(1.0, parallaxFactor));
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
     * Returns the parallax factor.
     *
     * @return the parallax factor
     */
    public double getParallaxFactor() {
        return parallaxFactor;
    }

    /**
     * Sets the travel ratio for the incoming page relative to the
     * viewport width.
     *
     * @param parallaxFactor 0.0 to 1.0; see class javadoc for effect
     *                       of different ranges
     */
    public void setParallaxFactor(double parallaxFactor) {
        this.parallaxFactor = Math.max(0.0, Math.min(1.0, parallaxFactor));
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        Direction direction = context.getDirection();
        animContentPane = context.getContentPane();

        boolean forward = direction == Direction.FORWARD;

        // New page behind, current page on top
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
        }
        if (currentPage != null) {
            currentPage.setVisible(true);
            currentPage.toFront();
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                resetNode(currentPage);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                resetNode(nextPage);
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
            double w = animContentPane.getWidth();
            updateParallax(newVal.doubleValue(), w,
                    currentPage, nextPage, forward);
        };
        progress.addListener(progressListener);
        // Apply p=0 state before first render frame to prevent nextPage flicker
        progressListener.changed(progress, 0.0, 0.0);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(duration,
                        new KeyValue(progress, 1, interpolator))
        );

        timeline.setOnFinished(e -> {
            finish.run();
            context.fireClosed(context.getCurrentIndex());
            context.fireOpened(context.getNextIndex());
        });

        setCurrentAnimation(timeline);
        return timeline;
    }

    private void updateParallax(double p, double w,
                                Node currentPage, Node nextPage,
                                boolean forward) {
        double sign = forward ? -1 : 1;

        // Current page: slides out at full speed
        if (currentPage != null) {
            currentPage.setTranslateX(sign * w * p);
        }

        // Next page: drifts in from a closer starting position
        if (nextPage != null) {
            double startOffset = -sign * w * parallaxFactor;
            nextPage.setTranslateX(startOffset * (1.0 - p));
        }
    }

    private void resetNode(Node node) {
        if (node != null) {
            node.setTranslateX(0);
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
            resetNode(child);
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
