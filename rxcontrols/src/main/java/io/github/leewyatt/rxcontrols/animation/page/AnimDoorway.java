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
import javafx.util.Duration;

/**
 * Doorway / zoom-through transition. The current page scales up and
 * fades out as if the camera is flying forward through it, revealing
 * the next page that emerges from underneath with a subtle scale-up —
 * a cinematic "push-in" effect common in film and Keynote presentations.
 *
 * <p>The {@link #setMaxScale(double) maxScale} parameter controls how
 * far the exiting page zooms in:</p>
 * <ul>
 *   <li><b>1.5 ~ 2.0:</b> gentle zoom, subtle doorway feel.</li>
 *   <li><b>2.0 ~ 3.0:</b> moderate zoom, clear "fly through".
 *       <em>(default: 2.5)</em></li>
 *   <li><b>3.0 ~ 5.0:</b> dramatic zoom, strong immersive push-in.</li>
 * </ul>
 *
 * <p>This animation is not direction-sensitive — FORWARD and BACKWARD
 * produce the same visual effect.</p>
 *
 * <p>This animation does not use snapshots and is fully resize-safe.</p>
 */
public class AnimDoorway extends PageAnimationBase {

    private double maxScale;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private StackPane animContentPane;

    /**
     * Creates a doorway animation with a max scale of 2.5.
     */
    public AnimDoorway() {
        this(2.5);
    }

    /**
     * Creates a doorway animation with the given max scale.
     *
     * @param maxScale the scale the exiting page reaches at full zoom
     *                 (at least 1.2; see class javadoc for effect of
     *                 different ranges)
     */
    public AnimDoorway(double maxScale) {
        this.maxScale = Math.max(1.2, maxScale);
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
     * Returns the max scale for the exiting page.
     *
     * @return the max scale
     */
    public double getMaxScale() {
        return maxScale;
    }

    /**
     * Sets how far the exiting page zooms in.
     *
     * @param maxScale at least 1.2; see class javadoc for effect of
     *                 different ranges
     */
    public void setMaxScale(double maxScale) {
        this.maxScale = Math.max(1.2, maxScale);
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        animContentPane = context.getContentPane();

        // New page behind, starts slightly scaled down
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

        progressListener = (obs, oldVal, newVal) ->
                updateDoorway(newVal.doubleValue(), currentPage, nextPage);
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

    private void updateDoorway(double p, Node currentPage, Node nextPage) {
        // Current page: scale up + fade out (flying through it)
        if (currentPage != null) {
            double scale = 1.0 + (maxScale - 1.0) * p;
            currentPage.setScaleX(scale);
            currentPage.setScaleY(scale);
            currentPage.setOpacity(1.0 - p);
        }

        // Next page: subtle scale-up from 0.9 to 1.0 (emerging from depth)
        if (nextPage != null) {
            double nextScale = 0.9 + 0.1 * p;
            nextPage.setScaleX(nextScale);
            nextPage.setScaleY(nextScale);
            nextPage.setOpacity(0.3 + 0.7 * p);
        }
    }

    private void resetNode(Node node) {
        if (node != null) {
            node.setScaleX(1);
            node.setScaleY(1);
            node.setOpacity(1);
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
