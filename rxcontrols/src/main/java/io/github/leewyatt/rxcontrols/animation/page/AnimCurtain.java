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
 * Curtain transition. The next page drops in from one edge like a
 * theater curtain falling, covering the stationary current page.
 * A slight overshoot bounce at the end gives a natural "landing" feel.
 *
 * <p>Supports both horizontal and vertical orientations.
 * Vertical (default): FORWARD drops from the top, BACKWARD rises from the bottom.
 * Horizontal: FORWARD sweeps from the left, BACKWARD sweeps from the right.</p>
 *
 * <p>This animation is resize-safe.</p>
 */
public class AnimCurtain extends PageAnimationBase {

    private final Orientation orientation;
    private Interpolator interpolator = new OvershootInterpolator(1.4);

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private ChangeListener<Number> sizeListener;
    private StackPane animatingContentPane;
    private Rectangle clipRect;

    /**
     * Creates a vertical curtain animation.
     */
    public AnimCurtain() {
        this(Orientation.VERTICAL);
    }

    /**
     * Creates a curtain animation with the given orientation.
     *
     * @param orientation the curtain orientation
     */
    public AnimCurtain(Orientation orientation) {
        this.orientation = orientation;
    }

    /**
     * Returns the orientation.
     *
     * @return the orientation
     */
    public Orientation getOrientation() {
        return orientation;
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
     * Sets the interpolator. The default is a custom overshoot
     * interpolator that gives a slight bounce at the end.
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

        // Current page stationary behind
        if (currentPage != null) {
            currentPage.setVisible(true);
            currentPage.toBack();
        }

        boolean horizontal = orientation == Orientation.HORIZONTAL;

        // Next page on top, starts off-screen
        if (nextPage != null) {
            if (horizontal) {
                double w = contentPane.getWidth();
                nextPage.setTranslateX(forward ? -w : w);
            } else {
                double h = contentPane.getHeight();
                nextPage.setTranslateY(forward ? -h : h);
            }
            nextPage.setVisible(true);
            nextPage.toFront();

            // Clip to prevent overflow during overshoot bounce
            clipRect = new Rectangle(contentPane.getWidth(), contentPane.getHeight());
            nextPage.setClip(clipRect);
        }

        DoubleProperty prog = this.progress;

        Runnable updatePosition = () -> {
            double p = prog.get();
            if (nextPage != null) {
                if (horizontal) {
                    double w = contentPane.getWidth();
                    nextPage.setTranslateX(forward ? -w * (1.0 - p) : w * (1.0 - p));
                } else {
                    double h = contentPane.getHeight();
                    nextPage.setTranslateY(forward ? -h * (1.0 - p) : h * (1.0 - p));
                }
                if (clipRect != null) {
                    clipRect.setWidth(contentPane.getWidth());
                    clipRect.setHeight(contentPane.getHeight());
                }
            }
        };

        Runnable finish = () -> {
            removeSizeListener();
            if (currentPage != null) {
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setTranslateX(0);
                nextPage.setTranslateY(0);
                nextPage.setClip(null);
                nextPage.setVisible(true);
            }
            clipRect = null;
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        if (progressListener != null) {
            progress.removeListener(progressListener);
        }
        progress.set(0);

        progressListener = (obs, oldVal, newVal) -> updatePosition.run();
        progress.addListener(progressListener);

        removeSizeListener();
        animatingContentPane = contentPane;
        sizeListener = (obs, oldVal, newVal) -> updatePosition.run();
        contentPane.heightProperty().addListener(sizeListener);
        contentPane.widthProperty().addListener(sizeListener);

        Timeline timeline = new Timeline(
                new KeyFrame(duration, new KeyValue(progress, 1.0, interpolator))
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
        removeSizeListener();
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        for (Node child : context.getContentPane().getChildren()) {
            child.setTranslateX(0);
            child.setTranslateY(0);
            child.setClip(null);
        }
        clipRect = null;
    }

    @Override
    public void dispose() {
        removeSizeListener();
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        clipRect = null;
        super.dispose();
    }

    private void removeSizeListener() {
        if (sizeListener != null && animatingContentPane != null) {
            animatingContentPane.widthProperty().removeListener(sizeListener);
            animatingContentPane.heightProperty().removeListener(sizeListener);
            sizeListener = null;
            animatingContentPane = null;
        }
    }

    /**
     * Interpolator that overshoots the target and settles back,
     * producing a slight bounce at the end of the motion.
     */
    private static class OvershootInterpolator extends Interpolator {

        private final double tension;

        OvershootInterpolator(double tension) {
            this.tension = tension;
        }

        @Override
        protected double curve(double t) {
            double t1 = t - 1.0;
            return t1 * t1 * ((tension + 1.0) * t1 + tension) + 1.0;
        }
    }
}
