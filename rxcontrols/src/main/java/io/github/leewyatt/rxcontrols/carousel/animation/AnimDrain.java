package io.github.leewyatt.rxcontrols.carousel.animation;

import io.github.leewyatt.rxcontrols.carousel.Direction;
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
import javafx.util.Duration;

/**
 * Drain transition. The current page shrinks, spins, and drifts
 * downward as if being sucked into a drain — inspired by the macOS
 * "Genie" minimize effect and vortex/whirlpool imagery.
 *
 * <p>The animation combines four simultaneous transforms on the
 * current page:</p>
 * <ol>
 *   <li>Scale 1.0 → 0.0 (shrinks to nothing)</li>
 *   <li>Rotate 0° → {@link #setTotalRotation(double) totalRotation}
 *       (spins, accelerating as it shrinks)</li>
 *   <li>Translate downward toward the bottom of the viewport</li>
 *   <li>Opacity 1.0 → 0.0 (fades out)</li>
 * </ol>
 *
 * <p>The {@link #setTotalRotation(double) totalRotation} parameter
 * controls the spin intensity:</p>
 * <ul>
 *   <li><b>180 ~ 360:</b> gentle swirl.
 *       <em>(default: 360)</em></li>
 *   <li><b>360 ~ 720:</b> dramatic vortex spin.</li>
 *   <li><b>720+:</b> very fast tornado-style rotation.</li>
 * </ul>
 *
 * <p>FORWARD spins clockwise; BACKWARD spins counter-clockwise.</p>
 *
 * <p>This animation does not use snapshots and is fully resize-safe.</p>
 */
public class AnimDrain extends CarouselAnimationBase {

    private double totalRotation;
    private Interpolator interpolator = Interpolator.EASE_IN;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private StackPane animContentPane;

    /**
     * Creates a drain animation with 360° total rotation.
     */
    public AnimDrain() {
        this(360);
    }

    /**
     * Creates a drain animation with the given total rotation.
     *
     * @param totalRotation total degrees of spin (at least 90;
     *                      see class javadoc for effect of different ranges)
     */
    public AnimDrain(double totalRotation) {
        this.totalRotation = Math.max(90, totalRotation);
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

    /**
     * Returns the total rotation in degrees.
     *
     * @return the total rotation
     */
    public double getTotalRotation() {
        return totalRotation;
    }

    /**
     * Sets the total degrees of spin during the drain.
     *
     * @param totalRotation at least 90; see class javadoc for effect
     *                      of different ranges
     */
    public void setTotalRotation(double totalRotation) {
        this.totalRotation = Math.max(90, totalRotation);
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        Direction direction = context.getDirection();
        animContentPane = context.getContentPane();

        boolean forward = direction == Direction.FORWARD;

        // New page as background
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
        }
        if (currentPage != null) {
            currentPage.setVisible(true);
            currentPage.toFront();
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                resetNode(currentPage);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                resetNode(nextPage);
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

        double rotSign = forward ? 1 : -1;

        progressListener = (obs, oldVal, newVal) -> {
            double h = animContentPane.getHeight();
            updateDrain(newVal.doubleValue(), h, currentPage, rotSign);
        };
        progress.addListener(progressListener);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(duration,
                        new KeyValue(progress, 1, Interpolator.LINEAR))
        );

        timeline.setOnFinished(e -> {
            finish.run();
            context.fireClosed(context.getCurrentIndex());
            context.fireOpened(context.getNextIndex());
        });

        setCurrentAnimation(timeline);
        return timeline;
    }

    private void updateDrain(double p, double h,
                             Node currentPage, double rotSign) {
        if (currentPage == null) {
            return;
        }

        // Apply easing: EASE_IN makes motion accelerate (like gravity
        // pulling into the drain — slow start, fast finish).
        double eased = interpolator.interpolate(0.0, 1.0, p);

        // Scale: 1 → 0
        double scale = 1.0 - eased;
        currentPage.setScaleX(scale);
        currentPage.setScaleY(scale);

        // Translate: drift toward bottom (up to 40% of height)
        currentPage.setTranslateY(eased * h * 0.4);

        // Rotate: accelerating spin (use eased² for spin acceleration
        // — the page spins faster as it gets smaller, like water near
        // a drain)
        currentPage.setRotate(rotSign * totalRotation * eased * eased);

        // Opacity: fade out in sync with scaling
        currentPage.setOpacity(1.0 - eased);
    }

    private void resetNode(Node node) {
        if (node != null) {
            node.setScaleX(1);
            node.setScaleY(1);
            node.setTranslateY(0);
            node.setRotate(0);
            node.setOpacity(1);
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
            resetNode(child);
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
