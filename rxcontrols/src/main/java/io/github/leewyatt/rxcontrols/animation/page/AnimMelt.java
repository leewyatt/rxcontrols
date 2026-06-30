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
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Melt transition. The current page is sliced into vertical strips
 * that drip downward at staggered speeds, like wax melting off a
 * surface or icicles breaking free, revealing the next page beneath.
 *
 * <p>FORWARD melts from the center outward; BACKWARD melts from
 * the edges inward.</p>
 *
 * <p>Each strip accelerates downward (EASE_IN by default) and
 * stretches slightly along the vertical axis to enhance the fluid,
 * dripping look.</p>
 *
 * <p><b>Note:</b> This animation uses snapshots and is not resize-safe.</p>
 */
public class AnimMelt extends PageAnimationBase {

    private int stripCount = 16;
    private double staggerFactor = 0.5;
    private Interpolator interpolator = Interpolator.EASE_IN;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private ImageView[] strips;
    private StackPane animContentPane;
    private double capturedH;

    /**
     * Creates a melt animation with 16 strips.
     */
    public AnimMelt() {
    }

    /**
     * Creates a melt animation with the given strip count.
     *
     * @param stripCount the number of vertical strips (minimum 2)
     */
    public AnimMelt(int stripCount) {
        this.stripCount = Math.max(2, stripCount);
    }

    /**
     * Returns the number of strips.
     *
     * @return the strip count
     */
    public int getStripCount() {
        return stripCount;
    }

    /**
     * Sets the number of strips. Recommended range: 8–30.
     *
     * @param stripCount the strip count (minimum 2)
     */
    public void setStripCount(int stripCount) {
        this.stripCount = Math.max(2, stripCount);
    }

    /**
     * Returns the stagger factor.
     *
     * @return the stagger factor
     */
    public double getStaggerFactor() {
        return staggerFactor;
    }

    /**
     * Sets the stagger factor controlling how much the strip start
     * times are spread out. Recommended range: 0.3–0.7.
     *
     * @param staggerFactor the stagger factor (0.0–0.95)
     */
    public void setStaggerFactor(double staggerFactor) {
        this.staggerFactor = RXMath.clamp(staggerFactor, 0.0, 0.95);
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
     * Sets the interpolator for each strip's fall. Default is
     * EASE_IN (accelerating, like gravity).
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

        boolean forward = direction == TransitionDirection.FORWARD;
        double w = animContentPane.getWidth();
        double h = animContentPane.getHeight();
        capturedH = h;

        installResizeGuard(animContentPane);

        // Next page as background
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
        }

        // Snapshot current page
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        WritableImage snap = currentPage != null
                ? currentPage.snapshot(params, null) : null;
        if (currentPage != null) {
            currentPage.setVisible(false);
        }

        if (snap != null) {
            strips = new ImageView[stripCount];
            double stripW = w / stripCount;

            for (int i = 0; i < stripCount; i++) {
                ImageView strip = new ImageView(snap);
                strip.setManaged(false);
                strip.setViewport(new Rectangle2D(i * stripW, 0, stripW, h));
                strip.setLayoutX(i * stripW);
                strip.setLayoutY(0);
                strips[i] = strip;
            }

            animContentPane.getChildren().addAll(strips);
        }

        Runnable finish = () -> {
            removeResizeGuard();
            removeStrips();
            if (currentPage != null) {
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

        progressListener = (obs, oldVal, newVal) ->
                updateStrips(newVal.doubleValue(), forward);
        progress.addListener(progressListener);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(duration, new KeyValue(progress, 1, Interpolator.LINEAR))
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
     * Updates each strip's fall position.
     *
     * <p>FORWARD: center strips melt first, edges last.
     * BACKWARD: edge strips melt first, center last.</p>
     *
     * <p>Delay is based on distance from center (forward) or from
     * the nearest edge (backward).</p>
     */
    private void updateStrips(double p, boolean forward) {
        if (strips == null) {
            return;
        }

        double fallDuration = 1.0 - staggerFactor;
        double center = (stripCount - 1) / 2.0;

        for (int i = 0; i < strips.length; i++) {
            ImageView strip = strips[i];
            if (strip == null) {
                continue;
            }

            // Distance from center, normalized to 0–1
            double distFromCenter = Math.abs(i - center) / center;

            // Forward: center first (small delay), edges last (large delay)
            // Backward: edges first (small delay), center last (large delay)
            double normalizedOrder = forward ? distFromCenter : (1.0 - distFromCenter);
            double delay = normalizedOrder * staggerFactor;
            double localP = RXMath.clamp((p - delay) / fallDuration, 0.0, 1.0);

            // Apply easing
            double easedP = interpolator.interpolate(0.0, 1.0, localP);

            // Fall downward
            strip.setTranslateY(capturedH * 1.2 * easedP);

            // Slight vertical stretch (dripping effect)
            strip.setScaleY(1.0 + 0.3 * easedP);

            // Fade out
            strip.setOpacity(1.0 - easedP * 0.8);
        }
    }

    private void removeStrips() {
        if (strips != null && animContentPane != null) {
            for (ImageView strip : strips) {
                if (strip != null) {
                    strip.setImage(null);
                    animContentPane.getChildren().remove(strip);
                }
            }
        }
        strips = null;
        animContentPane = null;
    }

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removeStrips();
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removeStrips();
        super.dispose();
    }
}
