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
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Rectangle reveal transition. A rounded rectangle clip expands from
 * the center to reveal the next page, or contracts to hide the current page.
 */
public class AnimRectangle extends CarouselAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private final Rectangle rect = new Rectangle();
    private double arcWidth;
    private double arcHeight;
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    /**
     * Creates a rectangle reveal with default arc sizes (12, 12).
     */
    public AnimRectangle() {
        this(12, 12);
    }

    /**
     * Creates a rectangle reveal with the given arc sizes.
     *
     * @param arcWidth  the arc width for rounded corners
     * @param arcHeight the arc height for rounded corners
     */
    public AnimRectangle(double arcWidth, double arcHeight) {
        this.arcWidth = arcWidth;
        this.arcHeight = arcHeight;
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
     * Returns the arc width for rounded corners.
     *
     * @return the arc width
     */
    public double getArcWidth() {
        return arcWidth;
    }

    /**
     * Sets the arc width for rounded corners.
     *
     * @param arcWidth the arc width
     */
    public void setArcWidth(double arcWidth) {
        this.arcWidth = arcWidth;
    }

    /**
     * Returns the arc height for rounded corners.
     *
     * @return the arc height
     */
    public double getArcHeight() {
        return arcHeight;
    }

    /**
     * Sets the arc height for rounded corners.
     *
     * @param arcHeight the arc height
     */
    public void setArcHeight(double arcHeight) {
        this.arcHeight = arcHeight;
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        Direction direction = context.getDirection();
        StackPane contentPane = context.getContentPane();

        boolean forward = direction == Direction.FORWARD;

        rect.setArcWidth(arcWidth);
        rect.setArcHeight(arcHeight);

        if (nextPage != null) {
            nextPage.setVisible(true);
        }

        Node clippedNode;
        if (forward) {
            clippedNode = nextPage;
            if (clippedNode != null) {
                clippedNode.toFront();
            }
        } else {
            clippedNode = currentPage;
            if (clippedNode != null) {
                clippedNode.toFront();
            }
        }
        if (clippedNode != null) {
            clippedNode.setClip(rect);
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setClip(null);
                currentPage.setVisible(false);
                currentPage.setOpacity(1.0);
            }
            if (nextPage != null) {
                nextPage.setClip(null);
                nextPage.setVisible(true);
                nextPage.setOpacity(1.0);
            }
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        // Remove old listener
        if (progressListener != null) {
            progress.removeListener(progressListener);
        }
        progress.set(0);

        // Create new listener that recomputes positions from current dimensions each frame
        progressListener = (obs, oldVal, newVal) -> {
            double p = newVal.doubleValue();
            double w = contentPane.getWidth();
            double h = contentPane.getHeight();

            if (forward) {
                rect.setX(w / 2 * (1 - p));
                rect.setY(h / 2 * (1 - p));
                rect.setWidth(w * p);
                rect.setHeight(h * p);
            } else {
                rect.setX(w / 2 * p);
                rect.setY(h / 2 * p);
                rect.setWidth(w * (1 - p));
                rect.setHeight(h * (1 - p));
            }
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

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        for (Node child : context.getContentPane().getChildren()) {
            child.setClip(null);
            child.setOpacity(1.0);
        }
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        super.dispose();
    }
}
