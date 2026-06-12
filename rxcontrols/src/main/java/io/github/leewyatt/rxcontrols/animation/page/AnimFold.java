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
import javafx.scene.effect.PerspectiveTransform;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * A folding transition using {@link PerspectiveTransform}.
 *
 * <p>The current page folds away while the next page unfolds into view.
 * Both pages meet at a ridge line that sweeps across the viewport, with
 * perspective foreshortening applied at the ridge.</p>
 *
 * <p>Supports both {@link Orientation#HORIZONTAL} (default, vertical ridge)
 * and {@link Orientation#VERTICAL} (horizontal ridge).</p>
 */
public class AnimFold extends PageAnimationBase {

    private final Orientation orientation;
    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private final PerspectiveTransform ptCurrent = new PerspectiveTransform();
    private final PerspectiveTransform ptNext = new PerspectiveTransform();

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    /**
     * Creates a horizontal door animation (default).
     */
    public AnimFold() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a door animation with the specified orientation.
     *
     * @param orientation the orientation of the door fold
     */
    public AnimFold(Orientation orientation) {
        this.orientation = orientation;
    }

    /**
     * Returns the orientation of this animation.
     *
     * @return the orientation
     */
    public Orientation getOrientation() {
        return orientation;
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

        if (progressListener != null) {
            progress.removeListener(progressListener);
        }
        progress.set(0);

        progressListener = (obs, oldVal, newVal) -> {
            double p = newVal.doubleValue();
            double w = contentPane.getWidth();
            double h = contentPane.getHeight();
            if (orientation == Orientation.VERTICAL) {
                updateDoorVertical(p, w, h, forward);
            } else {
                updateDoorHorizontal(p, w, h, forward);
            }
        };
        progress.addListener(progressListener);

        // Initialize PTs to p=0 state before applying, to avoid first-frame
        // flicker from stale/zero PT coordinates.
        progressListener.changed(progress, 0.0, 0.0);

        if (currentPage != null) {
            currentPage.setEffect(ptCurrent);
        }
        if (nextPage != null) {
            nextPage.setEffect(ptNext);
        }

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

    private void updateDoorHorizontal(double p, double w, double h, boolean forward) {
        double depth = w / 4.0;

        if (forward) {
            double edgeX = w * (1.0 - p);

            ptCurrent.setUlx(0);
            ptCurrent.setUly(0);
            ptCurrent.setUrx(edgeX);
            ptCurrent.setUry(depth * p);
            ptCurrent.setLrx(edgeX);
            ptCurrent.setLry(h - depth * p);
            ptCurrent.setLlx(0);
            ptCurrent.setLly(h);

            ptNext.setUlx(edgeX);
            ptNext.setUly(depth * (1.0 - p));
            ptNext.setUrx(w);
            ptNext.setUry(0);
            ptNext.setLrx(w);
            ptNext.setLry(h);
            ptNext.setLlx(edgeX);
            ptNext.setLly(h - depth * (1.0 - p));
        } else {
            double edgeX = w * p;

            ptCurrent.setUlx(edgeX);
            ptCurrent.setUly(depth * p);
            ptCurrent.setUrx(w);
            ptCurrent.setUry(0);
            ptCurrent.setLrx(w);
            ptCurrent.setLry(h);
            ptCurrent.setLlx(edgeX);
            ptCurrent.setLly(h - depth * p);

            ptNext.setUlx(0);
            ptNext.setUly(0);
            ptNext.setUrx(edgeX);
            ptNext.setUry(depth * (1.0 - p));
            ptNext.setLrx(edgeX);
            ptNext.setLry(h - depth * (1.0 - p));
            ptNext.setLlx(0);
            ptNext.setLly(h);
        }
    }

    private void updateDoorVertical(double p, double w, double h, boolean forward) {
        double depth = h / 4.0;

        if (forward) {
            double edgeY = h * (1.0 - p);

            ptCurrent.setUlx(0);
            ptCurrent.setUly(0);
            ptCurrent.setUrx(w);
            ptCurrent.setUry(0);
            ptCurrent.setLrx(w - depth * p);
            ptCurrent.setLry(edgeY);
            ptCurrent.setLlx(depth * p);
            ptCurrent.setLly(edgeY);

            ptNext.setUlx(depth * (1.0 - p));
            ptNext.setUly(edgeY);
            ptNext.setUrx(w - depth * (1.0 - p));
            ptNext.setUry(edgeY);
            ptNext.setLrx(w);
            ptNext.setLry(h);
            ptNext.setLlx(0);
            ptNext.setLly(h);
        } else {
            double edgeY = h * p;

            ptCurrent.setUlx(depth * p);
            ptCurrent.setUly(edgeY);
            ptCurrent.setUrx(w - depth * p);
            ptCurrent.setUry(edgeY);
            ptCurrent.setLrx(w);
            ptCurrent.setLry(h);
            ptCurrent.setLlx(0);
            ptCurrent.setLly(h);

            ptNext.setUlx(0);
            ptNext.setUly(0);
            ptNext.setUrx(w);
            ptNext.setUry(0);
            ptNext.setLrx(w - depth * (1.0 - p));
            ptNext.setLry(edgeY);
            ptNext.setLlx(depth * (1.0 - p));
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
