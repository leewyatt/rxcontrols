package io.github.leewyatt.rxcontrols.animation.page;

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
import javafx.util.Duration;

/**
 * Circle reveal transition. A circular clip expands from the center to
 * reveal the next page, or contracts to hide the current page.
 */
public class AnimCircle extends PageAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private final Circle circleClip = new Circle();
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    /**
     * Creates a circle reveal animation.
     */
    public AnimCircle() {
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
        TransitionDirection direction = context.getDirection();
        StackPane contentPane = context.getContentPane();

        boolean forward = direction == TransitionDirection.FORWARD;

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
            clippedNode.setClip(circleClip);
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

        // Create new listener that recomputes from current dimensions each frame
        progressListener = (obs, oldVal, newVal) -> {
            double p = newVal.doubleValue();
            double w = contentPane.getWidth();
            double h = contentPane.getHeight();
            double r = Math.sqrt(w * w + h * h) / 2;
            circleClip.setCenterX(w / 2);
            circleClip.setCenterY(h / 2);
            if (forward) {
                circleClip.setRadius(r * p);
            } else {
                circleClip.setRadius(r * (1 - p));
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
