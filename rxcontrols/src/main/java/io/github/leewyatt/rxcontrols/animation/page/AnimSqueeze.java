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
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Squeeze transition. The current page compresses into a thin line
 * at the center (like a CRT television switching off), then the
 * next page expands from that line to full size.
 *
 * <p>During the squeeze phase the page bulges slightly along the
 * perpendicular axis, giving a tactile "squeeze" feel.</p>
 *
 * <p>{@link Orientation#HORIZONTAL} (default) compresses vertically
 * into a horizontal line; {@link Orientation#VERTICAL} compresses
 * horizontally into a vertical line.</p>
 *
 * <p>This animation is not direction-sensitive.</p>
 *
 * <p>This animation is resize-safe.</p>
 */
public class AnimSqueeze extends PageAnimationBase {

    private final Orientation orientation;
    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private double squeezeSplit = 0.55;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private StackPane animContentPane;

    /**
     * Creates a horizontal squeeze animation (compresses into a
     * horizontal line).
     */
    public AnimSqueeze() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a squeeze animation with the given orientation.
     *
     * @param orientation HORIZONTAL to squeeze into a horizontal line,
     *                    VERTICAL to squeeze into a vertical line
     */
    public AnimSqueeze(Orientation orientation) {
        this.orientation = orientation;
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

    /**
     * Returns the split point between squeeze and expand phases.
     *
     * @return the split point (0.0–1.0)
     */
    public double getSqueezeSplit() {
        return squeezeSplit;
    }

    /**
     * Sets the split point between squeeze and expand phases.
     * A value of 0.5 means equal time for both phases.
     * Recommended range: 0.4–0.7.
     *
     * @param squeezeSplit the split point (0.0–1.0)
     */
    public void setSqueezeSplit(double squeezeSplit) {
        this.squeezeSplit = Math.max(0.1, Math.min(0.9, squeezeSplit));
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        animContentPane = context.getContentPane();

        boolean horizontal = orientation == Orientation.HORIZONTAL;

        // Both pages visible, current on top
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
            // Next page starts fully squeezed
            if (horizontal) {
                nextPage.setScaleY(0);
            } else {
                nextPage.setScaleX(0);
            }
        }
        if (currentPage != null) {
            currentPage.setVisible(true);
            currentPage.toFront();
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setScaleX(1);
                currentPage.setScaleY(1);
                currentPage.setOpacity(1);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setScaleX(1);
                nextPage.setScaleY(1);
                nextPage.setOpacity(1);
                nextPage.setVisible(true);
            }
            animContentPane = null;
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

            if (p <= squeezeSplit) {
                // Phase 1: current page squeezes
                double phase1 = p / squeezeSplit;

                if (currentPage != null) {
                    if (horizontal) {
                        currentPage.setScaleY(1.0 - phase1);
                        // Slight bulge along X during squeeze
                        currentPage.setScaleX(1.0 + 0.08 * Math.sin(phase1 * Math.PI));
                    } else {
                        currentPage.setScaleX(1.0 - phase1);
                        currentPage.setScaleY(1.0 + 0.08 * Math.sin(phase1 * Math.PI));
                    }
                    currentPage.setOpacity(1.0 - phase1 * 0.5);
                }

                // Next page stays squeezed
                if (nextPage != null) {
                    if (horizontal) {
                        nextPage.setScaleY(0);
                    } else {
                        nextPage.setScaleX(0);
                    }
                }
            } else {
                // Phase 2: next page expands
                double phase2 = (p - squeezeSplit) / (1.0 - squeezeSplit);

                // Current page hidden
                if (currentPage != null) {
                    currentPage.setVisible(false);
                }

                if (nextPage != null) {
                    if (horizontal) {
                        nextPage.setScaleY(phase2);
                        nextPage.setScaleX(1.0 + 0.08 * Math.sin((1.0 - phase2) * Math.PI));
                    } else {
                        nextPage.setScaleX(phase2);
                        nextPage.setScaleY(1.0 + 0.08 * Math.sin((1.0 - phase2) * Math.PI));
                    }
                    nextPage.setOpacity(0.5 + phase2 * 0.5);
                }
            }
        };
        progress.addListener(progressListener);
        // Apply p=0 state before first render frame to prevent nextPage flicker
        progressListener.changed(progress, 0.0, 0.0);

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
            child.setScaleX(1);
            child.setScaleY(1);
            child.setOpacity(1);
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
