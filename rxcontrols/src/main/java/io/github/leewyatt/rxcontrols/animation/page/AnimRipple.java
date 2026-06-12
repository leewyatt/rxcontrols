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
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

/**
 * Ripple reveal transition. Concentric circular wavefronts expand from
 * the page center, each one flipping the visible layer between current
 * and next page. The result is an animated pattern of alternating rings
 * that expands outward until the next page is fully revealed.
 *
 * <p>This animation is direction-independent: both FORWARD and BACKWARD
 * produce the same center-outward ripple. It is implemented as a pure
 * clip animation (no snapshots) and is fully resize-safe.</p>
 *
 * <p>The ring count must be odd so that the innermost zone (where all
 * wavefronts have passed) shows the next page. An even value passed to
 * the constructor is rounded up to the next odd number.</p>
 */
public class AnimRipple extends PageAnimationBase {

    private final int ringCount;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    private StackPane animContentPane;

    /**
     * Creates a ripple animation with 5 rings.
     */
    public AnimRipple() {
        this(5);
    }

    /**
     * Creates a ripple animation with the given number of rings.
     *
     * @param ringCount number of concentric wavefronts (clamped to 3–9, forced odd;
     *                  higher values degrade performance due to Shape boolean operations)
     */
    public AnimRipple(int ringCount) {
        int count = Math.max(3, Math.min(9, ringCount));
        this.ringCount = (count % 2 == 0) ? count + 1 : count;
    }

    /**
     * Returns the interpolator used for the overall animation timeline.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator for the overall animation timeline.
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
            double w = animContentPane.getWidth();
            double h = animContentPane.getHeight();
            updateRipple(newVal.doubleValue(), w, h, currentPage);
        };
        progress.addListener(progressListener);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(duration,
                        new KeyValue(progress, 1, interpolator))
        );

        timeline.setOnFinished(e -> {
            finish.run();
            context.fireClosed(context.getCurrentIndex());
            context.fireOpened(context.getNextIndex());
        });

        setCurrentAnimation(timeline);
        return timeline;
    }

    private void updateRipple(double p, double w, double h, Node currentPage) {
        if (currentPage == null) {
            return;
        }

        if (p >= 0.99) {
            currentPage.setClip(null);
            currentPage.setVisible(false);
            return;
        }

        if (p <= 0.01) {
            currentPage.setClip(null);
            return;
        }

        double cx = w / 2.0;
        double cy = h / 2.0;
        double maxR = Math.sqrt(cx * cx + cy * cy) * 1.1;

        // --- Compute wavefront radii ---
        // Wave 0 starts first (largest radius), wave N-1 starts last (smallest).

        double staggerRange = 0.4;
        double waveDuration = 1.0 - staggerRange;

        double[] radii = new double[ringCount];
        for (int i = 0; i < ringCount; i++) {
            double delay = (ringCount > 1)
                    ? (double) i / (ringCount - 1) * staggerRange : 0;
            double localP = Math.max(0, Math.min(1,
                    (p - delay) / waveDuration));
            double eased = 1.0 - (1.0 - localP) * (1.0 - localP);
            radii[i] = maxR * eased;
        }

        // Sort ascending: sorted[0] = smallest (last wave), sorted[N-1] = largest (first wave)
        double[] sorted = new double[ringCount];
        for (int i = 0; i < ringCount; i++) {
            sorted[i] = radii[ringCount - 1 - i];
        }

        if (sorted[0] >= maxR) {
            currentPage.setClip(null);
            currentPage.setVisible(false);
            return;
        }

        // --- Build revealed shape ---
        //
        // At radius r, the number of wavefronts that have passed = count of sorted[i] < r.
        // Odd count → next page, even count → current page.
        //
        // With odd ringCount, the center zone (all N waves passed) = next page.
        //
        // Revealed zones (next page visible):
        //   [0, sorted[0]],  [sorted[1], sorted[2]],  [sorted[3], sorted[4]], ...
        //
        // Current page zones:
        //   [sorted[0], sorted[1]],  [sorted[2], sorted[3]],  ...,  [sorted[N-1], edge]

        Shape revealed = null;

        if (sorted[0] > 0.5) {
            revealed = new Circle(cx, cy, sorted[0]);
        }

        for (int i = 1; i + 1 < ringCount; i += 2) {
            double inner = sorted[i];
            double outer = sorted[i + 1];
            if (outer - inner > 0.5) {
                Shape annulus = Shape.subtract(
                        new Circle(cx, cy, outer),
                        new Circle(cx, cy, inner));
                revealed = (revealed == null)
                        ? annulus : Shape.union(revealed, annulus);
            }
        }

        if (revealed == null) {
            currentPage.setClip(null);
            return;
        }

        currentPage.setClip(
                Shape.subtract(new Rectangle(w, h), revealed));
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