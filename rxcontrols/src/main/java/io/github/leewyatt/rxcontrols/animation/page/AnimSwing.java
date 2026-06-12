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
 * A swing-door transition using {@link PerspectiveTransform}.
 *
 * <p>The current page swings open like a door, rotating around one edge
 * with perspective foreshortening, revealing the next page beneath it.</p>
 *
 * <p>Supports both {@link Orientation#HORIZONTAL} (default, swings left/right)
 * and {@link Orientation#VERTICAL} (swings up/down).</p>
 *
 * <p>This animation is resize-safe: it uses a normalized progress value
 * and reads the content pane dimensions on every frame.</p>
 */
public class AnimSwing extends PageAnimationBase {

    private final Orientation orientation;
    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private final PerspectiveTransform pt = new PerspectiveTransform();

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    /**
     * Creates a horizontal swing animation (default).
     */
    public AnimSwing() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a swing animation with the specified orientation.
     *
     * @param orientation the swing orientation
     */
    public AnimSwing(Orientation orientation) {
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

        // Next page is behind, fully visible
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setEffect(null);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
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
                updateSwingVertical(p, w, h, forward);
            } else {
                updateSwingHorizontal(p, w, h, forward);
            }
        };
        progress.addListener(progressListener);

        // Initialize PT to p=0 (identity) before applying, to avoid
        // first-frame flicker from stale/zero PT coordinates.
        progressListener.changed(progress, 0.0, 0.0);

        // Current page swings open on top
        if (currentPage != null) {
            currentPage.setVisible(true);
            currentPage.setEffect(pt);
            currentPage.toFront();
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

    /**
     * Horizontal swing: current page rotates around its hinge edge (left or right).
     * The free edge narrows and shifts inward, creating a door-opening effect.
     */
    private void updateSwingHorizontal(double p, double w, double h, boolean forward) {
        // Perspective depth: how much the free edge shrinks vertically
        double maxDepth = h * 0.35;
        double depth = maxDepth * p;
        // The free edge moves toward the hinge as the "door" opens
        double freeEdgeX = w * (1.0 - p);

        if (forward) {
            // Hinge on left edge, free edge swings right-to-left
            pt.setUlx(0);
            pt.setUly(0);
            pt.setUrx(freeEdgeX);
            pt.setUry(depth);
            pt.setLrx(freeEdgeX);
            pt.setLry(h - depth);
            pt.setLlx(0);
            pt.setLly(h);
        } else {
            // Hinge on right edge, free edge swings left-to-right
            pt.setUlx(w - freeEdgeX);
            pt.setUly(depth);
            pt.setUrx(w);
            pt.setUry(0);
            pt.setLrx(w);
            pt.setLry(h);
            pt.setLlx(w - freeEdgeX);
            pt.setLly(h - depth);
        }
    }

    /**
     * Vertical swing: current page rotates around its hinge edge (top or bottom).
     * The free edge narrows and shifts inward, creating a flap effect.
     */
    private void updateSwingVertical(double p, double w, double h, boolean forward) {
        double maxDepth = w * 0.35;
        double depth = maxDepth * p;
        double freeEdgeY = h * (1.0 - p);

        if (forward) {
            // Hinge on top edge, free edge swings down-to-up
            pt.setUlx(0);
            pt.setUly(0);
            pt.setUrx(w);
            pt.setUry(0);
            pt.setLrx(w - depth);
            pt.setLry(freeEdgeY);
            pt.setLlx(depth);
            pt.setLly(freeEdgeY);
        } else {
            // Hinge on bottom edge, free edge swings up-to-down
            pt.setUlx(depth);
            pt.setUly(h - freeEdgeY);
            pt.setUrx(w - depth);
            pt.setUry(h - freeEdgeY);
            pt.setLrx(w);
            pt.setLry(h);
            pt.setLlx(0);
            pt.setLly(h);
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
