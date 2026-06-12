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
import javafx.scene.effect.PerspectiveTransform;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Louver (split-flap) transition. The current page is sliced into strips
 * that flip open one after another like the slats of a louver blind,
 * progressively revealing the next page behind them.
 *
 * <p>Unlike {@link AnimDomino} (which compresses strips flat via scale),
 * the louver strips use {@link PerspectiveTransform} to simulate 3D
 * rotation around their hinge edge, producing visible foreshortening
 * and a convincing sense of physical depth.</p>
 *
 * <p>FORWARD flips strips from first to last; BACKWARD flips from last
 * to first.</p>
 *
 * <p>Supports both {@link Orientation#HORIZONTAL} (default, horizontal
 * strips hinged at their top edge) and {@link Orientation#VERTICAL}
 * (vertical strips hinged at their left edge).</p>
 *
 * <p><b>Note:</b> This animation uses snapshots and is not resize-safe.</p>
 */
public class AnimLouver extends PageAnimationBase {

    private final Orientation orientation;
    private int stripCount = 10;
    private double staggerFactor = 0.6;
    private Interpolator interpolator = Interpolator.EASE_IN;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private ImageView[] strips;
    private PerspectiveTransform[] transforms;
    private StackPane animContentPane;

    /**
     * Creates a horizontal louver animation with 10 strips.
     */
    public AnimLouver() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a louver animation with the given orientation.
     *
     * @param orientation HORIZONTAL for horizontal strips,
     *                    VERTICAL for vertical strips
     */
    public AnimLouver(Orientation orientation) {
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
     * Sets the number of strips. Recommended range: 5–20.
     *
     * @param stripCount the strip count (minimum 2)
     */
    public void setStripCount(int stripCount) {
        this.stripCount = Math.max(2, stripCount);
    }

    /**
     * Returns the stagger factor that controls how much the strip
     * flip times overlap. A value of 0 means all strips flip
     * simultaneously; a higher value means more sequential.
     *
     * @return the stagger factor
     */
    public double getStaggerFactor() {
        return staggerFactor;
    }

    /**
     * Sets the stagger factor. Recommended range: 0.3–0.8.
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

        double w = animContentPane.getWidth();
        double h = animContentPane.getHeight();
        boolean horizontal = orientation == Orientation.HORIZONTAL;
        boolean forward = direction == TransitionDirection.FORWARD;

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
            transforms = new PerspectiveTransform[stripCount];

            if (horizontal) {
                double stripH = h / stripCount;
                for (int i = 0; i < stripCount; i++) {
                    ImageView strip = new ImageView(snap);
                    strip.setManaged(false);
                    strip.setViewport(new Rectangle2D(0, i * stripH, w, stripH));
                    strip.setLayoutX(0);
                    strip.setLayoutY(i * stripH);
                    PerspectiveTransform pt = new PerspectiveTransform();
                    strip.setEffect(pt);
                    strips[i] = strip;
                    transforms[i] = pt;
                }
            } else {
                double stripW = w / stripCount;
                for (int i = 0; i < stripCount; i++) {
                    ImageView strip = new ImageView(snap);
                    strip.setManaged(false);
                    strip.setViewport(new Rectangle2D(i * stripW, 0, stripW, h));
                    strip.setLayoutX(i * stripW);
                    strip.setLayoutY(0);
                    PerspectiveTransform pt = new PerspectiveTransform();
                    strip.setEffect(pt);
                    strips[i] = strip;
                    transforms[i] = pt;
                }
            }

            // Initialize PT to p=0 state before adding to scene graph
            updateStrips(0, w, h, horizontal, forward);
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

        double capturedW = w;
        double capturedH = h;

        progressListener = (obs, oldVal, newVal) ->
                updateStrips(newVal.doubleValue(), capturedW, capturedH, horizontal, forward);
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

    /**
     * Updates the PerspectiveTransform for each strip.
     *
     * <p>HORIZONTAL: each strip is hinged at its top edge. As it
     * flips, the bottom edge rises toward the top and narrows
     * horizontally, simulating rotation around a horizontal axis.</p>
     *
     * <p>VERTICAL: each strip is hinged at its left edge. As it
     * flips, the right edge moves toward the left and narrows
     * vertically, simulating rotation around a vertical axis.</p>
     */
    private void updateStrips(double p, double w, double h,
                              boolean horizontal, boolean forward) {
        if (strips == null || transforms == null) {
            return;
        }

        double flipDuration = 1.0 - staggerFactor;

        for (int i = 0; i < strips.length; i++) {
            int orderIdx = forward ? i : (strips.length - 1 - i);
            double delay = (strips.length > 1)
                    ? (double) orderIdx / (strips.length - 1) * staggerFactor
                    : 0;
            double localP = Math.max(0, Math.min(1, (p - delay) / flipDuration));

            ImageView strip = strips[i];
            PerspectiveTransform pt = transforms[i];
            if (strip == null || pt == null) {
                continue;
            }

            // Hide strip when fully flipped to avoid degenerate PT
            if (localP >= 0.98) {
                strip.setVisible(false);
                continue;
            }
            strip.setVisible(true);

            if (horizontal) {
                double stripH = h / stripCount;
                // Simulate rotation: bottom edge rises + narrows
                double bottomY = stripH * (1.0 - localP);
                double inset = w * 0.15 * localP;

                // Top edge (hinge) — fixed
                pt.setUlx(0);
                pt.setUly(0);
                pt.setUrx(w);
                pt.setUry(0);
                // Bottom edge — rises and narrows
                pt.setLlx(inset);
                pt.setLly(bottomY);
                pt.setLrx(w - inset);
                pt.setLry(bottomY);
            } else {
                double stripW = w / stripCount;
                // Simulate rotation: right edge moves left + narrows
                double rightX = stripW * (1.0 - localP);
                double inset = h * 0.15 * localP;

                // Left edge (hinge) — fixed
                pt.setUlx(0);
                pt.setUly(0);
                pt.setLlx(0);
                pt.setLly(h);
                // Right edge — moves left and narrows
                pt.setUrx(rightX);
                pt.setUry(inset);
                pt.setLrx(rightX);
                pt.setLry(h - inset);
            }

            // Darken as strip rotates away
            strip.setOpacity(1.0 - localP * 0.7);
        }
    }

    private void removeStrips() {
        if (strips != null && animContentPane != null) {
            for (ImageView strip : strips) {
                if (strip != null) {
                    strip.setEffect(null);
                    strip.setImage(null);
                    animContentPane.getChildren().remove(strip);
                }
            }
        }
        strips = null;
        transforms = null;
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
