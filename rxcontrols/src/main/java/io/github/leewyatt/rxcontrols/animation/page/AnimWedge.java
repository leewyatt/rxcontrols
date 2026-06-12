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
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

/**
 * Wedge transition with two complementary modes, both originating
 * from the top center of the page.
 *
 * <p>FORWARD (scissors open): a V-shaped cut expands downward from
 * the top center, slicing through the current page to reveal the
 * next page beneath.</p>
 *
 * <p>BACKWARD (curtains close): two triangular curtains of the next
 * page grow from the top-left and top-right corners, gradually
 * covering the current page — like two stage curtains dropping from
 * the sides.</p>
 *
 * <p>This animation is resize-safe: it reads the content pane
 * dimensions on every frame to compute the wedge geometry.</p>
 */
public class AnimWedge extends PageAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private StackPane animContentPane;
    private boolean forward;

    /**
     * Creates a wedge animation.
     */
    public AnimWedge() {
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
        animContentPane = context.getContentPane();

        forward = direction == TransitionDirection.FORWARD;

        if (forward) {
            if (nextPage != null) {
                nextPage.setVisible(true);
                nextPage.toBack();
            }
            if (currentPage != null) {
                currentPage.setVisible(true);
                currentPage.toFront();
            }
        } else {
            if (currentPage != null) {
                currentPage.setVisible(true);
                currentPage.toBack();
            }
            if (nextPage != null) {
                nextPage.setVisible(true);
                nextPage.toFront();
                nextPage.setClip(new Rectangle(0, 0, 0, 0));
            }
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

        if (progressListener != null) {
            progress.removeListener(progressListener);
        }
        progress.set(0);

        progressListener = (obs, oldVal, newVal) -> {
            double w = animContentPane.getWidth();
            double h = animContentPane.getHeight();
            updateWedge(newVal.doubleValue(), w, h,
                    currentPage, nextPage);
        };
        progress.addListener(progressListener);

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

    private void updateWedge(double p, double w, double h,
                              Node currentPage, Node nextPage) {
        if (p >= 0.99) {
            if (currentPage != null) {
                currentPage.setClip(null);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setClip(null);
            }
            return;
        }

        if (p <= 0.01) {
            if (forward) {
                if (currentPage != null) {
                    currentPage.setClip(null);
                }
            } else {
                if (nextPage != null) {
                    nextPage.setClip(new Rectangle(0, 0, 0, 0));
                }
            }
            return;
        }

        double cx = w / 2.0;
        double radius = Math.sqrt(cx * cx + h * h) * 1.1;

        if (forward) {
            // Scissors open: wedge 0→360°, cuts through currentPage
            double halfAngle = 180.0 * p;
            Arc wedge = new Arc(cx, 0, radius, radius,
                    270.0 - halfAngle, 2.0 * halfAngle);
            wedge.setType(ArcType.ROUND);
            if (currentPage != null) {
                currentPage.setClip(
                        Shape.subtract(new Rectangle(w, h), wedge));
            }
        } else {
            // Curtains close: the arc gap shrinks from 180° to 0°.
            // Curtains = rect - arc = page regions not covered by arc.
            //
            // The arc center is at (cx, 0) = top edge. The gap points
            // upward (centered on 90°). When halfAngle > 90, the gap
            // boundaries never enter the page (y > 0), creating a dead
            // zone where curtains are invisible. Cap at 90° to eliminate
            // this dead zone entirely.
            double halfAngle = 90.0 * (1.0 - p);
            Arc wedge = new Arc(cx, 0, radius, radius,
                    270.0 - halfAngle, 2.0 * halfAngle);
            wedge.setType(ArcType.ROUND);
            if (nextPage != null) {
                nextPage.setClip(
                        Shape.subtract(new Rectangle(w, h), wedge));
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
        for (Node child : context.getContentPane().getChildren()) {
            child.setClip(null);
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