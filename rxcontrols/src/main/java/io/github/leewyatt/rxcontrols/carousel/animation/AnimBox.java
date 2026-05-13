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
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.effect.PerspectiveTransform;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * 3D box flip transition using {@link PerspectiveTransform} to simulate
 * a cube-like rotation between pages.
 *
 * <p>Supports both horizontal (rotate around Y axis) and vertical
 * (rotate around X axis) orientations.</p>
 */
public class AnimBox extends CarouselAnimationBase {

    private final Orientation orientation;
    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private final PerspectiveTransform ptCurrent = new PerspectiveTransform();
    private final PerspectiveTransform ptNext = new PerspectiveTransform();

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    /**
     * Creates a horizontal box flip animation.
     */
    public AnimBox() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a box flip animation with the given orientation.
     *
     * @param orientation the flip orientation
     */
    public AnimBox(Orientation orientation) {
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
        Direction direction = context.getDirection();
        StackPane contentPane = context.getContentPane();

        boolean forward = direction == Direction.FORWARD;
        boolean horizontal = orientation == Orientation.HORIZONTAL;

        if (nextPage != null) {
            nextPage.setVisible(true);
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setEffect(null);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                // Set PT to identity before removing to avoid sub-pixel jump
                double fw = contentPane.getWidth();
                double fh = contentPane.getHeight();
                ptNext.setUlx(0);  ptNext.setUly(0);
                ptNext.setUrx(fw); ptNext.setUry(0);
                ptNext.setLrx(fw); ptNext.setLry(fh);
                ptNext.setLlx(0);  ptNext.setLly(fh);
                nextPage.setEffect(null);
                nextPage.setVisible(true);
            }
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        // Remove old listener if present
        if (progressListener != null) {
            progress.removeListener(progressListener);
        }
        progress.set(0);

        // Create new progress listener that recomputes all transforms from current dimensions
        progressListener = (obs, oldVal, newVal) -> {
            double p = newVal.doubleValue();
            double w = contentPane.getWidth();
            double h = contentPane.getHeight();

            if (horizontal) {
                updateHorizontal(p, w, h, forward, currentPage, nextPage);
            } else {
                updateVertical(p, w, h, forward, currentPage, nextPage);
            }
        };
        progress.addListener(progressListener);

        // Initialize PTs to p=0 state before applying, to avoid first-frame
        // flicker from stale/zero PT coordinates.
        progressListener.changed(progress, 0.0, 0.0);

        // Apply effects after initialization
        if (currentPage != null) {
            currentPage.setEffect(ptCurrent);
        }
        if (nextPage != null) {
            nextPage.setEffect(ptNext);
        }

        // Create timeline animating progress from 0 to 1
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

    private void updateHorizontal(double p, double w, double h, boolean forward,
                                   Node currentPage, Node nextPage) {
        double depth = w / 4.0;

        if (forward) {
            // Cube rotates left. Ridge moves from right (p=0) to left (p=1).
            double edgeX = w * (1.0 - p);

            // Current page: right edge at ridge (full height), left edge receding (foreshortened)
            ptCurrent.setUlx(0);
            ptCurrent.setUly(depth * p);
            ptCurrent.setUrx(edgeX);
            ptCurrent.setUry(0);
            ptCurrent.setLrx(edgeX);
            ptCurrent.setLry(h);
            ptCurrent.setLlx(0);
            ptCurrent.setLly(h - depth * p);

            // Next page: left edge at ridge (full height), right edge emerging (foreshortened)
            ptNext.setUlx(edgeX);
            ptNext.setUly(0);
            ptNext.setUrx(w);
            ptNext.setUry(depth * (1.0 - p));
            ptNext.setLrx(w);
            ptNext.setLry(h - depth * (1.0 - p));
            ptNext.setLlx(edgeX);
            ptNext.setLly(h);
        } else {
            // Cube rotates right. Ridge moves from left (p=0) to right (p=1).
            double edgeX = w * p;

            // Current page: left edge at ridge (full height), right edge receding (foreshortened)
            ptCurrent.setUlx(edgeX);
            ptCurrent.setUly(0);
            ptCurrent.setUrx(w);
            ptCurrent.setUry(depth * p);
            ptCurrent.setLrx(w);
            ptCurrent.setLry(h - depth * p);
            ptCurrent.setLlx(edgeX);
            ptCurrent.setLly(h);

            // Next page: right edge at ridge (full height), left edge emerging (foreshortened)
            ptNext.setUlx(0);
            ptNext.setUly(depth * (1.0 - p));
            ptNext.setUrx(edgeX);
            ptNext.setUry(0);
            ptNext.setLrx(edgeX);
            ptNext.setLry(h);
            ptNext.setLlx(0);
            ptNext.setLly(h - depth * (1.0 - p));
        }
    }

    private void updateVertical(double p, double w, double h, boolean forward,
                                 Node currentPage, Node nextPage) {
        double depth = h / 4.0;

        if (forward) {
            // Cube rotates upward. Ridge moves from bottom (p=0) to top (p=1).
            double edgeY = h * (1.0 - p);

            // Current page: bottom edge at ridge (full width), top edge receding (foreshortened)
            ptCurrent.setUlx(depth * p);
            ptCurrent.setUly(0);
            ptCurrent.setUrx(w - depth * p);
            ptCurrent.setUry(0);
            ptCurrent.setLrx(w);
            ptCurrent.setLry(edgeY);
            ptCurrent.setLlx(0);
            ptCurrent.setLly(edgeY);

            // Next page: top edge at ridge (full width), bottom edge emerging (foreshortened)
            ptNext.setUlx(0);
            ptNext.setUly(edgeY);
            ptNext.setUrx(w);
            ptNext.setUry(edgeY);
            ptNext.setLrx(w - depth * (1.0 - p));
            ptNext.setLry(h);
            ptNext.setLlx(depth * (1.0 - p));
            ptNext.setLly(h);
        } else {
            // Cube rotates downward. Ridge moves from top (p=0) to bottom (p=1).
            double edgeY = h * p;

            // Current page: top edge at ridge (full width), bottom edge receding (foreshortened)
            ptCurrent.setUlx(0);
            ptCurrent.setUly(edgeY);
            ptCurrent.setUrx(w);
            ptCurrent.setUry(edgeY);
            ptCurrent.setLrx(w - depth * p);
            ptCurrent.setLry(h);
            ptCurrent.setLlx(depth * p);
            ptCurrent.setLly(h);

            // Next page: bottom edge at ridge (full width), top edge emerging (foreshortened)
            ptNext.setUlx(depth * (1.0 - p));
            ptNext.setUly(0);
            ptNext.setUrx(w - depth * (1.0 - p));
            ptNext.setUry(0);
            ptNext.setLrx(w);
            ptNext.setLry(edgeY);
            ptNext.setLlx(0);
            ptNext.setLly(edgeY);
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
            child.setEffect(null);
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
