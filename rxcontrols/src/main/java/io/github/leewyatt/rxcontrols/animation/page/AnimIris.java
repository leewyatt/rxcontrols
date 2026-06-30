package io.github.leewyatt.rxcontrols.animation.page;

import io.github.leewyatt.rxcontrols.utils.RXMath;

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
import javafx.scene.shape.Shape;
import javafx.util.Duration;

/**
 * Iris transition. The current page is clipped by an iris shape (N arc
 * blades) that closes from the edges toward the center, revealing the
 * next page beneath. No colored overlay — the next page is directly
 * visible through the gaps between the shrinking blades.
 *
 * <p>The number of blades is configurable. Default is 8.</p>
 *
 * <p>This animation is resize-safe: it reads the content pane dimensions
 * on every frame to compute the iris geometry.</p>
 */
public class AnimIris extends PageAnimationBase {

    private final int bladeCount;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private StackPane animContentPane;
    private boolean forward;

    /**
     * Creates an iris animation with 8 blades.
     */
    public AnimIris() {
        this(8);
    }

    /**
     * Creates an iris animation with the specified number of blades.
     *
     * @param bladeCount the number of blades (clamped to 1–64;
     *                   very high values approach a circular reveal)
     */
    public AnimIris(int bladeCount) {
        this.bladeCount = RXMath.clamp(bladeCount, 1, 64);
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

        // Next page behind, fully visible
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
        }
        // Current page on top, will be clipped by iris
        if (currentPage != null) {
            currentPage.setVisible(true);
            currentPage.toFront();
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setClip(null);
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
            double w = animContentPane.getWidth();
            double h = animContentPane.getHeight();
            updateIris(p, w, h, currentPage);
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

    /**
     * Creates an iris clip on the current page.
     * <ul>
     *   <li>FORWARD: blades shrink from full sector inward — iris closes,
     *       revealing next page through widening gaps.</li>
     *   <li>BACKWARD: blades grow from 0 outward — iris opens from center,
     *       cutting away the current page from the inside out.</li>
     * </ul>
     */
    private void updateIris(double p, double w, double h, Node currentPage) {
        if (currentPage == null) {
            return;
        }

        double cx = w / 2;
        double cy = h / 2;
        double radius = Math.sqrt(cx * cx + cy * cy) + 10;
        double sectorAngle = 360.0 / bladeCount;

        if (forward) {
            // Blades shrink: current page visible only through blades
            double bladeAngle = sectorAngle * (1.0 - p);

            if (bladeAngle <= 0.01) {
                currentPage.setClip(null);
                currentPage.setVisible(false);
                return;
            }

            Shape clip = null;
            for (int i = 0; i < bladeCount; i++) {
                Arc blade = new Arc(cx, cy, radius, radius,
                        i * sectorAngle, bladeAngle);
                blade.setType(ArcType.ROUND);
                if (clip == null) {
                    clip = blade;
                } else {
                    clip = Shape.union(clip, blade);
                }
            }
            currentPage.setClip(clip);
        } else {
            // Blades grow from center: current page visible through the GAPS
            // (full rectangle minus growing blades)
            double bladeAngle = sectorAngle * p;

            if (bladeAngle >= sectorAngle - 0.01) {
                currentPage.setClip(null);
                currentPage.setVisible(false);
                return;
            }

            // Build blade union
            Shape blades = null;
            for (int i = 0; i < bladeCount; i++) {
                Arc blade = new Arc(cx, cy, radius, radius,
                        i * sectorAngle, bladeAngle);
                blade.setType(ArcType.ROUND);
                if (blades == null) {
                    blades = blade;
                } else {
                    blades = Shape.union(blades, blade);
                }
            }

            // Clip = full area minus blades (show only where blades are NOT)
            javafx.scene.shape.Rectangle fullRect = new javafx.scene.shape.Rectangle(w, h);
            Shape clip = Shape.subtract(fullRect, blades);
            currentPage.setClip(clip);
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
