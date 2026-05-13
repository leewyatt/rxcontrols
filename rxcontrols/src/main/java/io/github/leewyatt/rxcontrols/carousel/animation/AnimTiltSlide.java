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
import javafx.scene.effect.PerspectiveTransform;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Tilt-slide transition. Both pages slide simultaneously like
 * {@link AnimSlide}, but each page tilts with perspective
 * foreshortening as it moves — as if the pages are on a gently
 * curved surface.
 *
 * <p>The outgoing page's trailing edge shrinks (recedes), while
 * the incoming page's leading edge starts foreshortened and
 * gradually straightens as it arrives.</p>
 *
 * <p>This animation is resize-safe.</p>
 */
public class AnimTiltSlide extends CarouselAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private double tiltDepth = 0.15;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private final PerspectiveTransform ptCurrent = new PerspectiveTransform();
    private final PerspectiveTransform ptNext = new PerspectiveTransform();

    /**
     * Creates a tilt-slide animation.
     */
    public AnimTiltSlide() {
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
     * Returns the tilt depth as a ratio of the page height. This
     * controls how much the far edge contracts.
     *
     * @return the tilt depth ratio
     */
    public double getTiltDepth() {
        return tiltDepth;
    }

    /**
     * Sets the tilt depth ratio. Recommended range: 0.05–0.3.
     *
     * @param tiltDepth the tilt depth ratio (0.0–0.5)
     */
    public void setTiltDepth(double tiltDepth) {
        this.tiltDepth = Math.max(0, Math.min(0.5, tiltDepth));
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        Direction direction = context.getDirection();
        StackPane contentPane = context.getContentPane();

        boolean forward = direction == Direction.FORWARD;

        if (currentPage != null) {
            currentPage.setVisible(true);
        }
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
            updateTransforms(p, w, h, forward, currentPage, nextPage);
        };
        progress.addListener(progressListener);

        // Initialize PTs to p=0 state, then apply effects
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

    /**
     * Updates both PerspectiveTransforms.
     *
     * <p>FORWARD: current page slides left with trailing (right) edge
     * contracting; next page enters from the right with leading (left)
     * edge expanding.</p>
     *
     * <p>BACKWARD: mirrored.</p>
     */
    private void updateTransforms(double p, double w, double h,
                                  boolean forward, Node currentPage,
                                  Node nextPage) {
        double maxInset = h * tiltDepth;

        if (forward) {
            // --- Current page: slides left, right edge shrinks ---
            // tilt ramps up as page moves out
            double curInset = maxInset * p;
            double curLeft = -w * p;
            double curRight = curLeft + w;

            if (currentPage != null) {
                ptCurrent.setUlx(curLeft);
                ptCurrent.setUly(0);
                ptCurrent.setUrx(curRight);
                ptCurrent.setUry(curInset);
                ptCurrent.setLrx(curRight);
                ptCurrent.setLry(h - curInset);
                ptCurrent.setLlx(curLeft);
                ptCurrent.setLly(h);
            }

            // --- Next page: slides in from right, left edge expands ---
            double nextInset = maxInset * (1.0 - p);
            double nextLeft = w * (1.0 - p);
            double nextRight = nextLeft + w;

            if (nextPage != null) {
                ptNext.setUlx(nextLeft);
                ptNext.setUly(nextInset);
                ptNext.setUrx(nextRight);
                ptNext.setUry(0);
                ptNext.setLrx(nextRight);
                ptNext.setLry(h);
                ptNext.setLlx(nextLeft);
                ptNext.setLly(h - nextInset);
            }
        } else {
            // --- Current page: slides right, left edge shrinks ---
            double curInset = maxInset * p;
            double curLeft = w * p;
            double curRight = curLeft + w;

            if (currentPage != null) {
                ptCurrent.setUlx(curLeft);
                ptCurrent.setUly(curInset);
                ptCurrent.setUrx(curRight);
                ptCurrent.setUry(0);
                ptCurrent.setLrx(curRight);
                ptCurrent.setLry(h);
                ptCurrent.setLlx(curLeft);
                ptCurrent.setLly(h - curInset);
            }

            // --- Next page: slides in from left, right edge expands ---
            double nextInset = maxInset * (1.0 - p);
            double nextLeft = -w * (1.0 - p);
            double nextRight = nextLeft + w;

            if (nextPage != null) {
                ptNext.setUlx(nextLeft);
                ptNext.setUly(0);
                ptNext.setUrx(nextRight);
                ptNext.setUry(nextInset);
                ptNext.setLrx(nextRight);
                ptNext.setLry(h - nextInset);
                ptNext.setLlx(nextLeft);
                ptNext.setLly(h);
            }
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
