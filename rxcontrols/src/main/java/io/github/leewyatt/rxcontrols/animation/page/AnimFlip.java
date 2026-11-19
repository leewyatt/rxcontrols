package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.effect.PerspectiveTransform;
import javafx.util.Duration;

/**
 * 3D flip transition drawn with a {@link PerspectiveTransform}, so the page turns
 * with perspective foreshortening without a {@code PerspectiveCamera} in the scene.
 * The current page turns 90 degrees about its centre line until it is seen edge on,
 * then the next page turns in from the other side.
 *
 * <p>{@link Orientation#HORIZONTAL} (the default) turns the pages about the
 * horizontal centre line, top over bottom; {@link Orientation#VERTICAL} turns them
 * about the vertical centre line, side over side, like a playing card.</p>
 *
 * <p>Each page turns about the centre of its own layout bounds, read on every frame,
 * so the animation is resize-safe and a page that does not fill the content pane
 * turns in place. When the near edge of a turning page would leave those bounds,
 * the whole page is scaled down just enough to stay inside them.</p>
 */
public class AnimFlip extends PageAnimationBase {

    private static final double DEFAULT_FOCAL_LENGTH_FACTOR = 2.0;
    // Below this the camera would sit inside the turning page.
    private static final double MIN_FOCAL_LENGTH_FACTOR = 0.5;

    private final Orientation orientation;
    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private double focalLengthFactor = DEFAULT_FOCAL_LENGTH_FACTOR;

    private final PerspectiveTransform ptCurrent = new PerspectiveTransform();
    private final PerspectiveTransform ptNext = new PerspectiveTransform();
    private final double[] corners = new double[8];

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    /**
     * Creates a flip animation about the horizontal centre line.
     */
    public AnimFlip() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a flip animation with the given orientation.
     *
     * @param orientation HORIZONTAL turns the pages about the horizontal centre
     *                    line (top over bottom), VERTICAL about the vertical one
     *                    (side over side)
     */
    public AnimFlip(Orientation orientation) {
        this.orientation = orientation;
    }

    /**
     * Returns the interpolator used for the flip animation.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator used for the flip animation. It runs over the whole
     * flip, so the default {@link Interpolator#EASE_BOTH} turns fastest when the
     * pages are edge on; {@code null} falls back to that default.
     *
     * @param interpolator the interpolator
     */
    public void setInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    /**
     * Returns the focal length factor: the distance of the eye from the page, in
     * page widths (VERTICAL) or heights (HORIZONTAL). Higher values give a flatter
     * turn. Default is 2.0.
     *
     * @return the focal length factor
     */
    public double getFocalLengthFactor() {
        return focalLengthFactor;
    }

    /**
     * Sets the focal length factor. A value that is not finite or not above 0.5
     * falls back to the default 2.0.
     *
     * @param focalLengthFactor typically 1.5 to 4.0
     */
    public void setFocalLengthFactor(double focalLengthFactor) {
        this.focalLengthFactor = focalLengthFactor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        double sign = context.getDirection() == TransitionDirection.FORWARD ? 1.0 : -1.0;

        Runnable finish = () -> {
            removeProgressListener();
            if (currentPage != null) {
                currentPage.setEffect(null);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setEffect(null);
                nextPage.setVisible(true);
            }
        };
        setFinishAction(finish);

        // Both pages start flat: a PerspectiveTransform left at its all-zero default
        // would draw the page as a single point on the first frame.
        project(ptCurrent, 0.0, currentPage);
        project(ptNext, 0.0, nextPage);
        if (currentPage != null) {
            currentPage.setEffect(ptCurrent);
            currentPage.setVisible(true);
        }
        if (nextPage != null) {
            nextPage.setEffect(ptNext);
            nextPage.setVisible(false);
        }

        removeProgressListener();
        progress.set(0);
        boolean[] swapped = {false};
        progressListener = (obs, oldValue, newValue) -> {
            double p = newValue.doubleValue();
            if (!swapped[0] && p < 0.5) {
                // The current page turns away until it is edge on.
                project(ptCurrent, sign * p * Math.PI, currentPage);
                return;
            }
            if (!swapped[0]) {
                swapped[0] = true;
                if (currentPage != null) {
                    currentPage.setVisible(false);
                }
                if (nextPage != null) {
                    nextPage.setVisible(true);
                }
            }
            // The next page turns in from the other side. Once it shows, an
            // interpolator that dips back below the middle cannot turn it away again.
            project(ptNext, -sign * (1.0 - Math.max(p, 0.5)) * Math.PI, nextPage);
        };
        progress.addListener(progressListener);

        // The same order as the other transitions: closing and opening now, closed and
        // opened when it ends. A host that interrupts the flip makes up the last two.
        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(duration, new KeyValue(progress, 1, interpolatorOrDefault())));
        timeline.setOnFinished(e -> {
            finish.run();
            context.fireClosed(context.getCurrentIndex());
            context.fireOpened(context.getNextIndex());
        });

        setCurrentAnimation(timeline);
        return timeline;
    }

    // ==================== Projection ====================

    /**
     * Turns a page by theta about its own centre line and projects its corners for an
     * eye in front of it, then shrinks the result about the centre if the near edge
     * would leave the page's own bounds. The page's layout bounds are its local
     * rectangle, so a page that does not fill the content pane turns in place.
     */
    private void project(PerspectiveTransform pt, double theta, Node page) {
        if (page == null) {
            return;
        }
        Bounds bounds = page.getLayoutBounds();
        double halfW = bounds.getWidth() / 2.0;
        double halfH = bounds.getHeight() / 2.0;
        double centerX = bounds.getMinX() + halfW;
        double centerY = bounds.getMinY() + halfH;
        if (halfW <= 0.0 || halfH <= 0.0) {
            setPT(pt, centerX, centerY, centerX, centerY, centerX, centerY, centerX, centerY);
            return;
        }
        boolean aboutVertical = orientation == Orientation.VERTICAL;
        double f = 2.0 * (aboutVertical ? halfW : halfH) * focalLengthOrDefault();
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double reachX = 0.0;
        double reachY = 0.0;
        // Upper left, upper right, lower right, lower left.
        for (int i = 0; i < 4; i++) {
            double x = i == 1 || i == 2 ? halfW : -halfW;
            double y = i >= 2 ? halfH : -halfH;
            double z;
            if (aboutVertical) {
                z = x * sin;
                x = x * cos;
            } else {
                z = y * sin;
                y = y * cos;
            }
            double scale = f / (f + z);
            corners[2 * i] = x * scale;
            corners[2 * i + 1] = y * scale;
            reachX = Math.max(reachX, Math.abs(corners[2 * i]));
            reachY = Math.max(reachY, Math.abs(corners[2 * i + 1]));
        }
        double fit = 1.0;
        if (reachX > halfW) {
            fit = halfW / reachX;
        }
        if (reachY > halfH) {
            fit = Math.min(fit, halfH / reachY);
        }
        setPT(pt,
                corners[0] * fit + centerX, corners[1] * fit + centerY,
                corners[2] * fit + centerX, corners[3] * fit + centerY,
                corners[4] * fit + centerX, corners[5] * fit + centerY,
                corners[6] * fit + centerX, corners[7] * fit + centerY);
    }

    private Interpolator interpolatorOrDefault() {
        return interpolator == null ? Interpolator.EASE_BOTH : interpolator;
    }

    private void removeProgressListener() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
    }

    private double focalLengthOrDefault() {
        return Double.isFinite(focalLengthFactor) && focalLengthFactor > MIN_FOCAL_LENGTH_FACTOR
                ? focalLengthFactor : DEFAULT_FOCAL_LENGTH_FACTOR;
    }

    private void setPT(PerspectiveTransform pt,
                       double ulx, double uly, double urx, double ury,
                       double lrx, double lry, double llx, double lly) {
        pt.setUlx(ulx);
        pt.setUly(uly);
        pt.setUrx(urx);
        pt.setUry(ury);
        pt.setLrx(lrx);
        pt.setLry(lry);
        pt.setLlx(llx);
        pt.setLly(lly);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        removeProgressListener();
        for (Node child : context.getContentPane().getChildren()) {
            child.setEffect(null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        removeProgressListener();
        super.dispose();
    }
}
