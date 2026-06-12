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
import javafx.scene.shape.Polygon;
import javafx.util.Duration;

/**
 * Diamond reveal transition. A diamond (rhombus) shape expands from the
 * center of the page to reveal the next page, or contracts to hide the
 * current page.
 *
 * <p>FORWARD: the diamond clip is applied to the next page and expands
 * outward, progressively revealing it. BACKWARD: the diamond clip is
 * applied to the current page and contracts inward, progressively
 * hiding it.</p>
 *
 * <p>This animation is resize-safe: it reads the content pane dimensions
 * on every frame to compute the diamond geometry.</p>
 */
public class AnimDiamond extends PageAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private final Polygon diamondClip = new Polygon();
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    /**
     * Creates a diamond reveal animation.
     */
    public AnimDiamond() {
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
            clippedNode.setClip(diamondClip);
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
            double p = newVal.doubleValue();
            double w = contentPane.getWidth();
            double h = contentPane.getHeight();
            double cx = w / 2.0;
            double cy = h / 2.0;

            // Diamond radius: distance from center to each vertex.
            // At maxR the diamond covers the entire rectangle.
            // For a diamond with vertices at (cx, cy-r), (cx+r, cy),
            // (cx, cy+r), (cx-r, cy), a point (0,0) is inside when
            // |0-cx|/r + |0-cy|/r <= 1, i.e. r >= cx + cy.
            double maxR = (cx + cy) * 1.05;
            double r = forward ? maxR * p : maxR * (1.0 - p);

            diamondClip.getPoints().setAll(
                    cx, cy - r,   // top
                    cx + r, cy,   // right
                    cx, cy + r,   // bottom
                    cx - r, cy    // left
            );
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
