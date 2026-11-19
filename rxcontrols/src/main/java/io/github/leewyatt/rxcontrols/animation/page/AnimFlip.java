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
import javafx.scene.effect.PerspectiveTransform;
import javafx.scene.layout.StackPane;
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
 * <p>This animation is resize-safe: it reads the content pane dimensions on every
 * frame. When the near edge of a turning page would leave the content pane, the
 * whole page is scaled down just enough to stay inside it, so the pane's clip
 * never cuts the page.</p>
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
     * pages are edge on.
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

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        StackPane contentPane = context.getContentPane();
        double sign = context.getDirection() == TransitionDirection.FORWARD ? 1.0 : -1.0;

        Runnable finish = () -> {
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
        project(ptCurrent, 0.0, contentPane.getWidth(), contentPane.getHeight());
        project(ptNext, 0.0, contentPane.getWidth(), contentPane.getHeight());
        if (currentPage != null) {
            currentPage.setEffect(ptCurrent);
            currentPage.setVisible(true);
        }
        if (nextPage != null) {
            nextPage.setEffect(ptNext);
            nextPage.setVisible(false);
        }

        if (progressListener != null) {
            progress.removeListener(progressListener);
        }
        progress.set(0);
        boolean[] swapped = {false};
        progressListener = (obs, oldValue, newValue) -> {
            double p = newValue.doubleValue();
            double w = contentPane.getWidth();
            double h = contentPane.getHeight();
            if (p < 0.5) {
                // The current page turns away until it is edge on.
                project(ptCurrent, sign * p * Math.PI, w, h);
            } else {
                if (!swapped[0]) {
                    swapped[0] = true;
                    if (currentPage != null) {
                        currentPage.setVisible(false);
                    }
                    context.fireClosed(context.getCurrentIndex());
                    if (nextPage != null) {
                        nextPage.setVisible(true);
                    }
                    context.fireOpening(context.getNextIndex());
                }
                // The next page turns in from the other side, flat again at the end.
                project(ptNext, p >= 0.999 ? 0.0 : -sign * (1.0 - p) * Math.PI, w, h);
            }
        };
        progress.addListener(progressListener);

        context.fireClosing(context.getCurrentIndex());

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(duration, new KeyValue(progress, 1, interpolator)));
        timeline.setOnFinished(e -> {
            if (!swapped[0]) {
                swapped[0] = true;
                context.fireClosed(context.getCurrentIndex());
                context.fireOpening(context.getNextIndex());
            }
            finish.run();
            context.fireOpened(context.getNextIndex());
        });

        setCurrentAnimation(timeline);
        return timeline;
    }

    // ==================== Projection ====================

    /**
     * Turns a w x h page by theta about its centre line and projects its corners
     * for an eye in front of the page, then shrinks the result about the centre if
     * the near edge would leave the w x h area.
     */
    private void project(PerspectiveTransform pt, double theta, double w, double h) {
        double halfW = w / 2.0;
        double halfH = h / 2.0;
        if (halfW <= 0.0 || halfH <= 0.0) {
            setPT(pt, 0, 0, 0, 0, 0, 0, 0, 0);
            return;
        }
        boolean aboutVertical = orientation == Orientation.VERTICAL;
        double f = (aboutVertical ? w : h) * focalLengthOrDefault();
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
                corners[0] * fit + halfW, corners[1] * fit + halfH,
                corners[2] * fit + halfW, corners[3] * fit + halfH,
                corners[4] * fit + halfW, corners[5] * fit + halfH,
                corners[6] * fit + halfW, corners[7] * fit + halfH);
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
