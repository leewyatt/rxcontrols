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
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Single-line wipe transition. A rectangular clip slides across the page
 * to reveal or hide content, like a curtain being drawn.
 *
 * <p>Supports both horizontal and vertical orientations.</p>
 */
public class AnimWipe extends PageAnimationBase {

    private final Orientation orientation;
    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private final Rectangle rectClip = new Rectangle();
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    /**
     * Creates a horizontal wipe animation.
     */
    public AnimWipe() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a wipe animation with the given orientation.
     *
     * @param orientation the wipe orientation
     */
    public AnimWipe(Orientation orientation) {
        this.orientation = orientation;
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
        boolean horizontal = orientation == Orientation.HORIZONTAL;

        if (nextPage != null) {
            nextPage.setVisible(true);
        }

        // FORWARD: clip nextPage (wipe reveals next), BACKWARD: clip currentPage (wipe hides current)
        Node clippedNode = forward ? nextPage : currentPage;
        if (clippedNode != null) {
            clippedNode.toFront();
            clippedNode.setClip(rectClip);
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

        // Create new listener that recomputes positions from current dimensions each frame
        progressListener = (obs, oldVal, newVal) -> {
            double p = newVal.doubleValue();
            double w = contentPane.getWidth();
            double h = contentPane.getHeight();

            rectClip.setWidth(w);
            rectClip.setHeight(h);

            if (horizontal) {
                rectClip.setTranslateX(forward ? w * (1 - p) : w * p);
            } else {
                rectClip.setTranslateY(forward ? h * (1 - p) : h * p);
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
