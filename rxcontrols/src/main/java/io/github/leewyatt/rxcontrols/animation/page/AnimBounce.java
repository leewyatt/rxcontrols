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
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Bounce slide transition. Pages slide in and out just like
 * {@link AnimSlide}, but the motion curve overshoots the target
 * position and springs back, giving a lively elastic feel — common
 * in iOS animations and motion design.
 *
 * <p>The {@link #setBounceStrength(double) bounceStrength} parameter
 * controls how far the pages overshoot:</p>
 * <ul>
 *   <li><b>0.5 ~ 1.0:</b> subtle overshoot, barely noticeable
 *       spring.</li>
 *   <li><b>1.0 ~ 2.0:</b> moderate bounce, clearly elastic.
 *       <em>(default: 1.5)</em></li>
 *   <li><b>2.0 ~ 4.0:</b> dramatic bounce, playful and energetic.</li>
 * </ul>
 *
 * <p>Supports both {@link Orientation#HORIZONTAL} (default) and
 * {@link Orientation#VERTICAL}.</p>
 *
 * <p>FORWARD: current page exits left (or up), next enters from right
 * (or below). BACKWARD: reversed.</p>
 *
 * <p>This animation is resize-safe: it reads the content pane
 * dimensions on every frame.</p>
 */
public class AnimBounce extends PageAnimationBase {

    private final Orientation orientation;
    private double bounceStrength;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private StackPane animContentPane;

    /**
     * Creates a horizontal bounce animation with strength 1.5.
     */
    public AnimBounce() {
        this(Orientation.HORIZONTAL, 1.5);
    }

    /**
     * Creates a bounce animation with the given orientation and strength 1.5.
     *
     * @param orientation the slide direction
     */
    public AnimBounce(Orientation orientation) {
        this(orientation, 1.5);
    }

    /**
     * Creates a bounce animation with the given orientation and strength.
     *
     * @param orientation    the slide direction
     * @param bounceStrength the overshoot intensity (at least 0.1;
     *                       see class javadoc for effect of different ranges)
     */
    public AnimBounce(Orientation orientation, double bounceStrength) {
        this.orientation = orientation;
        this.bounceStrength = Math.max(0.1, bounceStrength);
    }

    /**
     * Returns the overshoot intensity.
     *
     * @return the bounce strength
     */
    public double getBounceStrength() {
        return bounceStrength;
    }

    /**
     * Sets the overshoot intensity.
     *
     * @param bounceStrength at least 0.1; see class javadoc for effect
     *                       of different ranges
     */
    public void setBounceStrength(double bounceStrength) {
        this.bounceStrength = Math.max(0.1, bounceStrength);
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        TransitionDirection direction = context.getDirection();
        animContentPane = context.getContentPane();

        boolean forward = direction == TransitionDirection.FORWARD;
        boolean horizontal = orientation == Orientation.HORIZONTAL;

        if (nextPage != null) {
            nextPage.setVisible(true);
        }
        if (currentPage != null) {
            currentPage.setVisible(true);
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                resetNode(currentPage, horizontal);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                resetNode(nextPage, horizontal);
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
            double h = animContentPane.getHeight();
            double size = horizontal ? w : h;
            updateBounce(newVal.doubleValue(), size,
                    currentPage, nextPage, forward, horizontal);
        };
        progress.addListener(progressListener);
        // Apply p=0 state before first render frame to prevent nextPage flicker
        progressListener.changed(progress, 0.0, 0.0);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(duration,
                        new KeyValue(progress, 1, Interpolator.LINEAR))
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
     * Applies a "back-ease-out" curve that overshoots the target then
     * springs back. The formula is the standard back-ease-out used in
     * CSS and motion design toolkits, scaled by {@code bounceStrength}.
     *
     * @param t linear progress 0 to 1
     * @return eased value that may exceed 1.0 before settling back
     */
    private double backEaseOut(double t) {
        if (t <= 0) {
            return 0;
        }
        if (t >= 1) {
            return 1;
        }
        double s = 1.70158 * bounceStrength;
        double t1 = t - 1.0;
        return t1 * t1 * ((s + 1) * t1 + s) + 1.0;
    }

    private void updateBounce(double p, double size,
                              Node currentPage, Node nextPage,
                              boolean forward, boolean horizontal) {
        double eased = backEaseOut(p);
        double sign = forward ? -1 : 1;

        // Both pages move together, like a slide, but with bounce curve
        if (currentPage != null) {
            double offset = sign * size * eased;
            if (horizontal) {
                currentPage.setTranslateX(offset);
            } else {
                currentPage.setTranslateY(offset);
            }
        }

        if (nextPage != null) {
            double offset = sign * size * (eased - 1.0);
            if (horizontal) {
                nextPage.setTranslateX(offset);
            } else {
                nextPage.setTranslateY(offset);
            }
        }
    }

    private void resetNode(Node node, boolean horizontal) {
        if (node != null) {
            if (horizontal) {
                node.setTranslateX(0);
            } else {
                node.setTranslateY(0);
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
        boolean horizontal = orientation == Orientation.HORIZONTAL;
        for (Node child : context.getContentPane().getChildren()) {
            resetNode(child, horizontal);
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
