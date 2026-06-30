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
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Flash transition. A bright overlay flares up to full opacity, the
 * page switches at the peak of the flash, and the overlay fades back
 * to transparent — like a camera flash or a strobe cut.
 *
 * <p>The flash color is configurable (default white). The peak point
 * is configurable via {@link #setFlashPeak(double)}.</p>
 *
 * <p>This animation is not direction-sensitive.</p>
 *
 * <p>This animation is resize-safe.</p>
 */
public class AnimFlash extends PageAnimationBase {

    private Color flashColor;
    private double flashPeak = 0.35;
    private Interpolator interpolator = Interpolator.LINEAR;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private Rectangle overlay;
    private StackPane animContentPane;

    /**
     * Creates a white flash animation.
     */
    public AnimFlash() {
        this(Color.WHITE);
    }

    /**
     * Creates a flash animation with the given color.
     *
     * @param flashColor the flash overlay color
     */
    public AnimFlash(Color flashColor) {
        this.flashColor = flashColor;
    }

    /**
     * Returns the flash color.
     *
     * @return the flash color
     */
    public Color getFlashColor() {
        return flashColor;
    }

    /**
     * Sets the flash color.
     *
     * @param flashColor the flash overlay color
     */
    public void setFlashColor(Color flashColor) {
        this.flashColor = flashColor;
    }

    /**
     * Returns the flash peak point (the progress value at which the
     * flash is brightest and the page switches).
     *
     * @return the peak point (0.0–1.0)
     */
    public double getFlashPeak() {
        return flashPeak;
    }

    /**
     * Sets the flash peak point. A lower value means the flash peaks
     * earlier and the fade-out is longer. Recommended range: 0.2–0.5.
     *
     * @param flashPeak the peak point (0.0–1.0)
     */
    public void setFlashPeak(double flashPeak) {
        this.flashPeak = RXMath.clamp(flashPeak, 0.05, 0.95);
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
        animContentPane = context.getContentPane();

        // Current page visible, next page hidden until peak
        if (currentPage != null) {
            currentPage.setVisible(true);
        }
        if (nextPage != null) {
            nextPage.setVisible(false);
            nextPage.toBack();
        }

        // Flash overlay
        overlay = new Rectangle();
        overlay.setFill(flashColor);
        overlay.setOpacity(0);
        overlay.setManaged(false);
        overlay.setMouseTransparent(true);
        overlay.setWidth(animContentPane.getWidth());
        overlay.setHeight(animContentPane.getHeight());
        animContentPane.getChildren().add(overlay);

        Runnable finish = () -> {
            removeOverlay();
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

        boolean[] switched = {false};

        progressListener = (obs, oldVal, newVal) -> {
            double p = newVal.doubleValue();
            double w = animContentPane.getWidth();
            double h = animContentPane.getHeight();

            // Keep overlay sized to pane
            if (overlay != null) {
                overlay.setWidth(w);
                overlay.setHeight(h);
                overlay.toFront();
            }

            double opacity;
            if (p <= flashPeak) {
                // Ramp up: 0 → 1
                opacity = p / flashPeak;
            } else {
                // Ramp down: 1 → 0
                opacity = 1.0 - (p - flashPeak) / (1.0 - flashPeak);
            }
            if (overlay != null) {
                overlay.setOpacity(opacity);
            }

            // Switch pages at peak
            if (!switched[0] && p >= flashPeak) {
                switched[0] = true;
                if (currentPage != null) {
                    currentPage.setVisible(false);
                }
                if (nextPage != null) {
                    nextPage.setVisible(true);
                }
            }
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

    private void removeOverlay() {
        if (overlay != null && animContentPane != null) {
            animContentPane.getChildren().remove(overlay);
        }
        overlay = null;
        animContentPane = null;
    }

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removeOverlay();
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removeOverlay();
        super.dispose();
    }
}
