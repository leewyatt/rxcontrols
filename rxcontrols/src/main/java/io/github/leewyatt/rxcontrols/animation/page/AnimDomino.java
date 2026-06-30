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
 * Domino transition. The current page is split into strips that "fall"
 * one after another like dominoes, revealing the next page beneath.
 *
 * <p>Each strip flips (compresses via scaleX/scaleY) with a staggered
 * delay, creating a sequential wave effect. The direction of the wave
 * follows the {@link TransitionDirection}.</p>
 *
 * <p>Supports both {@link Orientation#HORIZONTAL} (default, horizontal
 * strips falling top-to-bottom) and {@link Orientation#VERTICAL} (vertical
 * strips falling left-to-right).</p>
 *
 * <p><b>Note:</b> This animation uses snapshots and is not resize-safe.
 * If the host is resized during the animation, it will jump to its
 * end state automatically.</p>
 */
public class AnimDomino extends PageAnimationBase {

    private final Orientation orientation;
    private final int stripCount;
    private Interpolator interpolator = Interpolator.EASE_IN;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    private ImageView[] strips;
    private StackPane animContentPane;

    /**
     * Creates a horizontal domino animation with 12 strips.
     */
    public AnimDomino() {
        this(Orientation.HORIZONTAL, 12);
    }

    /**
     * Creates a domino animation with the given orientation and 12 strips.
     *
     * @param orientation the strip orientation
     */
    public AnimDomino(Orientation orientation) {
        this(orientation, 12);
    }

    /**
     * Creates a domino animation with the given orientation and strip count.
     *
     * @param orientation the strip orientation
     * @param stripCount  the number of strips (at least 2, recommended 4 to 20)
     */
    public AnimDomino(Orientation orientation, int stripCount) {
        this.orientation = orientation;
        this.stripCount = Math.max(2, stripCount);
    }

    /**
     * Returns the interpolator used for each strip's fall.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator for each strip's fall. Default is EASE_IN
     * (accelerating, like gravity).
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
        boolean horizontal = orientation == Orientation.HORIZONTAL;
        double w = animContentPane.getWidth();
        double h = animContentPane.getHeight();

        installResizeGuard(animContentPane);

        // Next page visible as background
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
        }

        // Snapshot current page
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);

        WritableImage snap = currentPage != null ? currentPage.snapshot(params, null) : null;
        if (currentPage != null) {
            currentPage.setVisible(false);
        }

        // Create strips
        strips = new ImageView[stripCount];
        double stripSize = horizontal ? h / stripCount : w / stripCount;

        if (snap != null) {
            for (int i = 0; i < stripCount; i++) {
                ImageView strip = new ImageView(snap);
                strip.setManaged(false);
                if (horizontal) {
                    strip.setViewport(new Rectangle2D(0, i * stripSize, w, stripSize));
                    strip.setLayoutY(i * stripSize);
                } else {
                    strip.setViewport(new Rectangle2D(i * stripSize, 0, stripSize, h));
                    strip.setLayoutX(i * stripSize);
                }
                strips[i] = strip;
                animContentPane.getChildren().add(strip);
            }
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

        progressListener = (obs, oldVal, newVal) -> {
            double p = newVal.doubleValue();
            updateStrips(p, forward, horizontal);
        };
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
     * Each strip has a staggered start time. The first 60% of the total
     * duration is used for staggering; each strip's own fall takes 40%.
     * This ensures overlap — multiple strips are falling simultaneously.
     */
    private void updateStrips(double p, boolean forward, boolean horizontal) {
        double staggerWindow = 0.6;
        double fallDuration = 1.0 - staggerWindow;

        for (int i = 0; i < stripCount; i++) {
            if (strips[i] == null) {
                continue;
            }

            // Determine strip order based on direction
            int idx = forward ? i : (stripCount - 1 - i);
            double staggerDelay = (double) idx / (stripCount - 1) * staggerWindow;
            double localP = RXMath.clamp((p - staggerDelay) / fallDuration, 0.0, 1.0);

            // Apply easing to each strip's local progress
            double easedP = interpolator.interpolate(0.0, 1.0, localP);

            // Flip: compress along the strip's narrow axis
            if (horizontal) {
                strips[i].setScaleY(1.0 - easedP);
            } else {
                strips[i].setScaleX(1.0 - easedP);
            }

            // Fade out as it falls
            strips[i].setOpacity(1.0 - easedP * 0.6);
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
