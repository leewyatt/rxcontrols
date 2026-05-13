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
import javafx.scene.shape.Circle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

/**
 * Circle wave transition. A row of circles gathers toward the center,
 * spreads back out, then expands to fully reveal the next page.
 */
public class AnimCircleWave extends CarouselAnimationBase {

    private int circleCount = 5;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private Node clippedNode;

    /**
     * Creates a circle wave animation with default parameters.
     */
    public AnimCircleWave() {
    }

    /**
     * Creates a circle wave animation with the given circle count.
     *
     * @param circleCount number of circles in the wave
     */
    public AnimCircleWave(int circleCount) {
        this.circleCount = Math.max(2, circleCount);
    }

    /**
     * Returns the number of circles in the wave.
     *
     * @return the circle count
     */
    public int getCircleCount() {
        return circleCount;
    }

    /**
     * Sets the number of circles in the wave.
     *
     * @param circleCount the circle count (minimum 2)
     */
    public void setCircleCount(int circleCount) {
        this.circleCount = Math.max(2, circleCount);
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

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        StackPane contentPane = context.getContentPane();

        if (nextPage != null) {
            nextPage.setVisible(true);
        }

        clippedNode = nextPage;

        if (clippedNode != null) {
            clippedNode.toFront();
            clippedNode.setClip(createClipShape(0,
                    contentPane.getWidth(), contentPane.getHeight()));
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
            clippedNode = null;
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        if (progressListener != null) {
            progress.removeListener(progressListener);
        }
        progress.set(0);

        progressListener = (obs, oldVal, newVal) -> {
            if (clippedNode != null) {
                double w = contentPane.getWidth();
                double h = contentPane.getHeight();
                clippedNode.setClip(createClipShape(newVal.doubleValue(), w, h));
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

    private Shape createClipShape(double p, double w, double h) {
        double centerY = h / 2.0;
        double spacing = w / (circleCount + 1);
        double centerX = w / 2.0;
        double gatherEnd = 0.4;

        Shape shape = null;
        for (int i = 0; i < circleCount; i++) {
            double homeX = spacing * (i + 1);
            double cx, radius;

            if (p <= gatherEnd) {
                double pp = p / gatherEnd;
                cx = homeX + (centerX - homeX) * pp;
                radius = (h / circleCount) * pp;
            } else {
                double pp = (p - gatherEnd) / (1.0 - gatherEnd);
                cx = centerX;
                radius = (h / circleCount) + (Math.max(w, h) - h / circleCount) * pp;
            }

            Circle c = new Circle(cx, centerY, Math.max(0.1, radius));
            shape = (shape == null) ? c : Shape.union(shape, c);
        }
        return shape;
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
        clippedNode = null;
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        clippedNode = null;
        super.dispose();
    }
}
