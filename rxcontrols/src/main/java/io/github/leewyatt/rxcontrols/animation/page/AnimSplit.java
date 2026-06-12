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
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Split transition. The current page is sliced into strips that
 * slide out in alternating directions — odd strips go one way,
 * even strips go the other — like a zipper opening.
 *
 * <p>{@link Orientation#HORIZONTAL} (default) creates horizontal
 * strips that slide left/right. {@link Orientation#VERTICAL} creates
 * vertical strips that slide up/down.</p>
 *
 * <p>FORWARD: odd strips slide in the forward direction, even strips
 * in the backward direction. BACKWARD reverses both.</p>
 *
 * <p><b>Note:</b> This animation uses snapshots and is not resize-safe.</p>
 */
public class AnimSplit extends PageAnimationBase {

    private final Orientation orientation;
    private int stripCount = 10;
    private double staggerFactor = 0.3;
    private Interpolator interpolator = Interpolator.EASE_IN;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private ImageView[] strips;
    private StackPane animContentPane;
    private double capturedW;
    private double capturedH;

    /**
     * Creates a horizontal split animation with 10 strips.
     */
    public AnimSplit() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a split animation with the given orientation.
     *
     * @param orientation HORIZONTAL for horizontal strips sliding
     *                    left/right, VERTICAL for vertical strips
     *                    sliding up/down
     */
    public AnimSplit(Orientation orientation) {
        this.orientation = orientation;
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
     * Sets the number of strips. Recommended range: 6–20.
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
     * times are spread out. 0 means all strips start together.
     * Recommended range: 0.0–0.5.
     *
     * @param staggerFactor the stagger factor (0.0–0.95)
     */
    public void setStaggerFactor(double staggerFactor) {
        this.staggerFactor = Math.max(0, Math.min(0.95, staggerFactor));
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
     * Sets the interpolator.
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

        boolean horizontal = orientation == Orientation.HORIZONTAL;
        boolean forward = direction == TransitionDirection.FORWARD;
        double w = animContentPane.getWidth();
        double h = animContentPane.getHeight();
        capturedW = w;
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

            if (horizontal) {
                double stripH = h / stripCount;
                for (int i = 0; i < stripCount; i++) {
                    ImageView strip = new ImageView(snap);
                    strip.setManaged(false);
                    strip.setViewport(new Rectangle2D(0, i * stripH, w, stripH));
                    strip.setLayoutX(0);
                    strip.setLayoutY(i * stripH);
                    strips[i] = strip;
                }
            } else {
                double stripW = w / stripCount;
                for (int i = 0; i < stripCount; i++) {
                    ImageView strip = new ImageView(snap);
                    strip.setManaged(false);
                    strip.setViewport(new Rectangle2D(i * stripW, 0, stripW, h));
                    strip.setLayoutX(i * stripW);
                    strip.setLayoutY(0);
                    strips[i] = strip;
                }
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
                updateStrips(newVal.doubleValue(), horizontal, forward);
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
     * Updates each strip's slide position. Odd and even strips
     * slide in opposite directions. Strips near the center start
     * slightly earlier than those at the edges.
     */
    private void updateStrips(double p, boolean horizontal, boolean forward) {
        if (strips == null) {
            return;
        }

        double slideDuration = 1.0 - staggerFactor;
        double center = (stripCount - 1) / 2.0;

        for (int i = 0; i < strips.length; i++) {
            ImageView strip = strips[i];
            if (strip == null) {
                continue;
            }

            // Stagger: center strips start first, edges last
            double distFromCenter = Math.abs(i - center) / center;
            double delay = distFromCenter * staggerFactor;
            double localP = Math.max(0, Math.min(1, (p - delay) / slideDuration));
            double easedP = interpolator.interpolate(0.0, 1.0, localP);

            // Alternating directions: even strips one way, odd the other
            double sign = (i % 2 == 0) ? 1.0 : -1.0;
            if (!forward) {
                sign = -sign;
            }

            if (horizontal) {
                // Horizontal strips slide left/right
                strip.setTranslateX(sign * capturedW * easedP);
            } else {
                // Vertical strips slide up/down
                strip.setTranslateY(sign * capturedH * easedP);
            }

            strip.setOpacity(1.0 - easedP * 0.6);
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
