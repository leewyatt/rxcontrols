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
 * 3D cube rotation transition using true perspective projection.
 *
 * <p>The current page and next page are treated as adjacent faces of a cube.
 * The cube rotates 90° around the Y axis (horizontal) or X axis (vertical),
 * with proper perspective foreshortening computed via trigonometry.</p>
 *
 * <p>This animation is resize-safe: it reads the content pane dimensions
 * on every frame to compute the projection.</p>
 */
public class AnimCube extends PageAnimationBase {

    private final Orientation orientation;
    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private double focalLengthFactor = 2.0;

    private final PerspectiveTransform ptCurrent = new PerspectiveTransform();
    private final PerspectiveTransform ptNext = new PerspectiveTransform();

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    /**
     * Creates a horizontal cube rotation animation.
     */
    public AnimCube() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a cube rotation animation with the given orientation.
     *
     * @param orientation the rotation orientation
     */
    public AnimCube(Orientation orientation) {
        this.orientation = orientation;
    }

    /**
     * Returns the focal length factor. Higher values produce less perspective
     * distortion. Default is 2.0.
     *
     * @return the focal length factor
     */
    public double getFocalLengthFactor() {
        return focalLengthFactor;
    }

    /**
     * Sets the focal length factor.
     *
     * @param focalLengthFactor value &gt; 0.5, typically 1.5 to 4.0
     */
    public void setFocalLengthFactor(double focalLengthFactor) {
        this.focalLengthFactor = Math.max(0.5, focalLengthFactor);
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

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        TransitionDirection direction = context.getDirection();
        StackPane contentPane = context.getContentPane();

        boolean forward = direction == TransitionDirection.FORWARD;

        if (nextPage != null) {
            nextPage.setVisible(true);
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setEffect(null);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                // Set PT to exact full-screen before removing effect to avoid size jump
                double fw = contentPane.getWidth();
                double fh = contentPane.getHeight();
                setPT(ptNext, 0, 0, fw, 0, fw, fh, 0, fh);
                nextPage.setEffect(null);
                nextPage.setVisible(true);
            }
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        // Initialize PTs to p=0 state BEFORE applying, so the first rendered
        // frame shows the correct geometry (known pitfall: PT defaults to all
        // zeros, which renders the node as a zero-size point).
        double initW = contentPane.getWidth();
        double initH = contentPane.getHeight();
        setPT(ptCurrent, 0, 0, initW, 0, initW, initH, 0, initH);
        if (orientation == Orientation.HORIZONTAL) {
            setPT(ptNext, forward ? initW : 0, 0,
                    forward ? initW : 0, 0,
                    forward ? initW : 0, initH,
                    forward ? initW : 0, initH);
        } else {
            setPT(ptNext, 0, forward ? initH : 0,
                    initW, forward ? initH : 0,
                    initW, forward ? initH : 0,
                    0, forward ? initH : 0);
        }

        if (currentPage != null) {
            currentPage.setEffect(ptCurrent);
        }
        if (nextPage != null) {
            nextPage.setEffect(ptNext);
        }

        if (progressListener != null) {
            progress.removeListener(progressListener);
        }
        progress.set(0);

        progressListener = (obs, oldVal, newVal) -> {
            double p = newVal.doubleValue();
            double w = contentPane.getWidth();
            double h = contentPane.getHeight();
            if (orientation == Orientation.HORIZONTAL) {
                updateHorizontal(p, w, h, forward);
            } else {
                updateVertical(p, w, h, forward);
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

    // ==================== Horizontal (Y-axis rotation) ====================

    private void updateHorizontal(double p, double w, double h, boolean forward) {
        // At endpoints, snap to exact full-screen to avoid jump when effect is removed
        if (p <= 0.001) {
            setPT(ptCurrent, 0, 0, w, 0, w, h, 0, h);
            setPT(ptNext, w, 0, w, 0, w, h, w, h);
            return;
        }
        if (p >= 0.999) {
            setPT(ptCurrent, 0, 0, 0, 0, 0, h, 0, h);
            setPT(ptNext, 0, 0, w, 0, w, h, 0, h);
            return;
        }

        double halfW = w / 2.0;
        double halfH = h / 2.0;

        double f = w * focalLengthFactor;
        // Scale factor so the front face (at z = s) projects to exactly the
        // viewport size: f·s/(f+s) = halfW requires s = halfW·f/(f-halfW).
        // Both X and Y extents use the same factor so aspect ratio is preserved.
        double scale = f / (f - halfW);
        double s = halfW * scale;
        double sH = halfH * scale;

        double theta = p * Math.PI / 2.0;

        if (forward) {
            projectFaceH(ptCurrent, theta, s, sH, f, halfW, halfH,
                    -s, s, s, s);
            projectFaceH(ptNext, theta, s, sH, f, halfW, halfH,
                    s, s, s, -s);
        } else {
            projectFaceH(ptCurrent, -theta, s, sH, f, halfW, halfH,
                    -s, s, s, s);
            projectFaceH(ptNext, -theta, s, sH, f, halfW, halfH,
                    -s, -s, -s, s);
        }
    }

    /**
     * Projects a vertical face of the cube after Y-axis rotation by theta.
     * The face has two vertical edges:
     *   edge A at (ax, y, az) and edge B at (bx, y, bz), y ∈ [-sH, sH]
     *
     * @param sH the face Y half-extent (scaled so projection fills viewport height)
     */
    private void projectFaceH(PerspectiveTransform pt, double theta,
                               double s, double sH, double f,
                               double halfW, double halfH,
                               double ax, double az, double bx, double bz) {
        double cosT = Math.cos(theta);
        double sinT = Math.sin(theta);

        double ax2 = ax * cosT - az * sinT;
        double az2 = ax * sinT + az * cosT;
        double bx2 = bx * cosT - bz * sinT;
        double bz2 = bx * sinT + bz * cosT;

        double da = Math.max(f + az2, 1);
        double db = Math.max(f + bz2, 1);

        double aScreenX = f * ax2 / da + halfW;
        double aScreenTopY = f * (-sH) / da + halfH;
        double aScreenBotY = f * sH / da + halfH;

        double bScreenX = f * bx2 / db + halfW;
        double bScreenTopY = f * (-sH) / db + halfH;
        double bScreenBotY = f * sH / db + halfH;

        pt.setUlx(aScreenX);
        pt.setUly(aScreenTopY);
        pt.setUrx(bScreenX);
        pt.setUry(bScreenTopY);
        pt.setLrx(bScreenX);
        pt.setLry(bScreenBotY);
        pt.setLlx(aScreenX);
        pt.setLly(aScreenBotY);
    }

    // ==================== Vertical (X-axis rotation) ====================

    private void updateVertical(double p, double w, double h, boolean forward) {
        if (p <= 0.001) {
            setPT(ptCurrent, 0, 0, w, 0, w, h, 0, h);
            setPT(ptNext, 0, h, w, h, w, h, 0, h);
            return;
        }
        if (p >= 0.999) {
            setPT(ptCurrent, 0, 0, w, 0, w, 0, 0, 0);
            setPT(ptNext, 0, 0, w, 0, w, h, 0, h);
            return;
        }

        double halfW = w / 2.0;
        double halfH = h / 2.0;

        double f = h * focalLengthFactor;
        double scale = f / (f - halfH);
        double s = halfH * scale;
        double sW = halfW * scale;

        double theta = p * Math.PI / 2.0;

        if (forward) {
            projectFaceV(ptCurrent, theta, s, sW, f, halfW, halfH,
                    -s, s, s, s);
            projectFaceV(ptNext, theta, s, sW, f, halfW, halfH,
                    s, s, s, -s);
        } else {
            projectFaceV(ptCurrent, -theta, s, sW, f, halfW, halfH,
                    -s, s, s, s);
            projectFaceV(ptNext, -theta, s, sW, f, halfW, halfH,
                    -s, -s, -s, s);
        }
    }

    /**
     * Projects a horizontal face of the cube after X-axis rotation by theta.
     * The face has two horizontal edges:
     *   edge A at (x, ay, az) and edge B at (x, by, bz), x ∈ [-sW, sW]
     *
     * @param sW the face X half-extent (scaled so projection fills viewport width)
     */
    private void projectFaceV(PerspectiveTransform pt, double theta,
                               double s, double sW, double f,
                               double halfW, double halfH,
                               double ay, double az, double by, double bz) {
        double cosT = Math.cos(theta);
        double sinT = Math.sin(theta);

        double ay2 = ay * cosT - az * sinT;
        double az2 = ay * sinT + az * cosT;
        double by2 = by * cosT - bz * sinT;
        double bz2 = by * sinT + bz * cosT;

        double da = Math.max(f + az2, 1);
        double db = Math.max(f + bz2, 1);

        double aScreenY = f * ay2 / da + halfH;
        double aScreenLeftX = f * (-sW) / da + halfW;
        double aScreenRightX = f * sW / da + halfW;

        double bScreenY = f * by2 / db + halfH;
        double bScreenLeftX = f * (-sW) / db + halfW;
        double bScreenRightX = f * sW / db + halfW;

        pt.setUlx(aScreenLeftX);
        pt.setUly(aScreenY);
        pt.setUrx(aScreenRightX);
        pt.setUry(aScreenY);
        pt.setLrx(bScreenRightX);
        pt.setLry(bScreenY);
        pt.setLlx(bScreenLeftX);
        pt.setLly(bScreenY);
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
