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
 * Gallery transition. The current page shrinks, slides to one side and
 * fades out, while the next page enters from the opposite side at a
 * reduced scale, then grows to fill the viewport — like browsing through
 * a row of framed pictures on a gallery wall.
 *
 * <p>Compared to {@link AnimConveyor}, the gallery effect features:</p>
 * <ul>
 *   <li>A deeper scale reduction ({@link #setSideScale(double) sideScale},
 *       default 0.55) for a stronger depth impression.</li>
 *   <li>An opacity fade on the exiting page, simulating distance.</li>
 *   <li>Overlapping travel paths — the two pages cross in the center
 *       region, creating a parallax-like layered feel.</li>
 * </ul>
 *
 * <p>FORWARD: current page exits left, next enters from right.
 * BACKWARD: reversed.</p>
 *
 * <p>This animation is resize-safe: it reads the content pane
 * dimensions on every frame.</p>
 */
public class AnimGallery extends PageAnimationBase {

    private double sideScale;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private StackPane animContentPane;

    /**
     * Creates a gallery animation with a side scale of 0.55.
     */
    public AnimGallery() {
        this(0.55);
    }

    /**
     * Creates a gallery animation with the given side scale.
     *
     * @param sideScale the scale factor at the side position
     *                  (at least 0.1, recommended 0.4 to 0.7)
     */
    public AnimGallery(double sideScale) {
        this.sideScale = Math.max(0.1, sideScale);
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
     * Returns the scale factor at the side position.
     *
     * @return the side scale
     */
    public double getSideScale() {
        return sideScale;
    }

    /**
     * Sets the scale factor pages shrink to when at the side position.
     * Smaller values create a more dramatic depth effect.
     *
     * @param sideScale at least 0.1, recommended 0.4 to 0.7
     */
    public void setSideScale(double sideScale) {
        this.sideScale = Math.max(0.1, sideScale);
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        TransitionDirection direction = context.getDirection();
        animContentPane = context.getContentPane();

        boolean forward = direction == TransitionDirection.FORWARD;

        if (nextPage != null) {
            nextPage.setVisible(true);
        }
        if (currentPage != null) {
            currentPage.setVisible(true);
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
            updateGallery(newVal.doubleValue(), w,
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

    private void updateGallery(double p, double w,
                               Node currentPage, Node nextPage,
                               boolean forward) {
        double exitSign = forward ? -1 : 1;

        // Travel distance: 60% of width — pages overlap in the center
        double travel = w * 0.6;

        // Current page: scale 1→sideScale, slide out, fade out
        if (currentPage != null) {
            double currScale = 1.0 - (1.0 - sideScale) * p;
            currentPage.setScaleX(currScale);
            currentPage.setScaleY(currScale);
            currentPage.setTranslateX(exitSign * travel * p);
            currentPage.setOpacity(1.0 - p * 0.8);
            currentPage.setViewOrder(p < 0.5 ? -1 : 0);
        }

        // Next page: scale sideScale→1, slide in, fade in
        if (nextPage != null) {
            double nextScale = sideScale + (1.0 - sideScale) * p;
            nextPage.setScaleX(nextScale);
            nextPage.setScaleY(nextScale);
            nextPage.setTranslateX(-exitSign * travel * (1.0 - p));
            nextPage.setOpacity(0.2 + p * 0.8);
            nextPage.setViewOrder(p < 0.5 ? 0 : -1);
        }
    }

    private void resetNode(Node node) {
        if (node != null) {
            node.setScaleX(1);
            node.setScaleY(1);
            node.setTranslateX(0);
            node.setOpacity(1);
            node.setViewOrder(0);
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
