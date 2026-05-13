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
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.util.Duration;

/**
 * Sector (pie/arc) reveal transition. A circular arc clip sweeps open
 * to reveal the next page, or sweeps closed to hide the current page.
 */
public class AnimSector extends CarouselAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private final Arc arcClip = new Arc();
    private double startAngle;
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    /**
     * Creates a sector reveal starting at 90 degrees.
     */
    public AnimSector() {
        this(90);
    }

    /**
     * Creates a sector reveal with the given start angle.
     *
     * @param startAngle the arc start angle in degrees
     */
    public AnimSector(double startAngle) {
        this.startAngle = startAngle;
        arcClip.setType(ArcType.ROUND);
        arcClip.setStartAngle(startAngle);
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
     * Returns the arc start angle in degrees.
     *
     * @return the start angle
     */
    public double getStartAngle() {
        return startAngle;
    }

    /**
     * Sets the arc start angle in degrees.
     *
     * @param startAngle the start angle
     */
    public void setStartAngle(double startAngle) {
        this.startAngle = startAngle;
        arcClip.setStartAngle(startAngle);
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        Direction direction = context.getDirection();
        StackPane contentPane = context.getContentPane();

        boolean forward = direction == Direction.FORWARD;

        if (nextPage != null) {
            nextPage.setVisible(true);
        }

        // FORWARD: clip currentPage (arc shrinks to reveal next behind)
        // BACKWARD: clip nextPage (arc grows to reveal next in front)
        Node clippedNode;
        if (forward) {
            clippedNode = currentPage;
            if (clippedNode != null) {
                clippedNode.toFront();
            }
        } else {
            clippedNode = nextPage;
            if (clippedNode != null) {
                clippedNode.toFront();
            }
        }
        if (clippedNode != null) {
            clippedNode.setClip(arcClip);
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setClip(null);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setClip(null);
                nextPage.setVisible(true);
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

        // Create new listener that recomputes from current dimensions each frame
        progressListener = (obs, oldVal, newVal) -> {
            double p = newVal.doubleValue();
            double w = contentPane.getWidth();
            double h = contentPane.getHeight();
            double r = Math.sqrt(w * w + h * h) / 2.0;
            arcClip.setCenterX(w / 2);
            arcClip.setCenterY(h / 2);
            arcClip.setRadiusX(r);
            arcClip.setRadiusY(r);
            if (forward) {
                arcClip.setLength(360 * (1 - p));
            } else {
                arcClip.setLength(360 * p);
            }
        };
        progress.addListener(progressListener);
        // Apply p=0 state before first render frame to prevent nextPage flicker
        progressListener.changed(progress, 0.0, 0.0);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(progress, 0)),
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

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        for (Node child : context.getContentPane().getChildren()) {
            child.setClip(null);
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
