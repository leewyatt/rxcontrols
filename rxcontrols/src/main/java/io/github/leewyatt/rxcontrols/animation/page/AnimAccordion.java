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
 * Accordion fold transition. The current page folds into multiple segments
 * and collapses to one side, revealing the next page beneath it.
 *
 * <p>Each segment uses a {@link PerspectiveTransform} to create a 3D folding
 * appearance with alternating perspective depth. The number of folds is
 * configurable via the constructor.</p>
 *
 * <p>Supports both {@link Orientation#HORIZONTAL} (default, vertical folds)
 * and {@link Orientation#VERTICAL} (horizontal folds).</p>
 *
 * <p><b>Note:</b> This animation uses snapshots and is not resize-safe.
 * If the host is resized during the animation, it will jump to its
 * end state automatically.</p>
 */
public class AnimAccordion extends PageAnimationBase {

    private final Orientation orientation;
    private final int foldCount;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    private ImageView[] currentStrips;
    private PerspectiveTransform[] currentPTs;
    private StackPane animContentPane;

    /**
     * Creates a horizontal accordion animation with 5 folds.
     */
    public AnimAccordion() {
        this(Orientation.HORIZONTAL, 5);
    }

    /**
     * Creates an accordion animation with the given orientation and 5 folds.
     *
     * @param orientation the fold orientation
     */
    public AnimAccordion(Orientation orientation) {
        this(orientation, 5);
    }

    /**
     * Creates an accordion animation with the given orientation and fold count.
     *
     * @param orientation the fold orientation
     * @param foldCount   the number of folds (at least 2, recommended 2 to 10)
     */
    public AnimAccordion(Orientation orientation, int foldCount) {
        this.orientation = orientation;
        this.foldCount = Math.max(2, foldCount);
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
        animContentPane = context.getContentPane();

        boolean forward = direction == TransitionDirection.FORWARD;
        boolean horizontal = orientation == Orientation.HORIZONTAL;
        double w = animContentPane.getWidth();
        double h = animContentPane.getHeight();

        installResizeGuard(animContentPane);

        // Next page stays visible as background — no snapshot needed
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
        }

        // Snapshot current page, then hide it
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);

        WritableImage currentSnap = currentPage != null ? currentPage.snapshot(params, null) : null;
        if (currentPage != null) {
            currentPage.setVisible(false);
        }

        // Create fold strips from current page snapshot
        currentStrips = new ImageView[foldCount];
        currentPTs = new PerspectiveTransform[foldCount];
        double stripSize = horizontal ? w / foldCount : h / foldCount;

        if (currentSnap != null) {
            for (int i = 0; i < foldCount; i++) {
                ImageView strip = new ImageView(currentSnap);
                strip.setManaged(false);
                if (horizontal) {
                    strip.setViewport(new Rectangle2D(i * stripSize, 0, stripSize, h));
                    strip.setLayoutX(i * stripSize);
                } else {
                    strip.setViewport(new Rectangle2D(0, i * stripSize, w, stripSize));
                    strip.setLayoutY(i * stripSize);
                }
                PerspectiveTransform pt = new PerspectiveTransform();
                if (horizontal) {
                    setPT(pt, 0, 0, stripSize, 0, stripSize, h, 0, h);
                } else {
                    setPT(pt, 0, 0, w, 0, w, stripSize, 0, stripSize);
                }
                strip.setEffect(pt);
                currentStrips[i] = strip;
                currentPTs[i] = pt;
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
            if (horizontal) {
                updateHorizontal(p, w, h, stripSize, forward);
            } else {
                updateVertical(p, w, h, stripSize, forward);
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

    /**
     * Horizontal accordion: strips compress toward the hinge side with
     * alternating perspective depth, revealing the next page beneath.
     */
    private void updateHorizontal(double p, double w, double h, double stripW, boolean forward) {
        // Hide strips when fully compressed to avoid degenerate PT
        if (p >= 0.98) {
            for (ImageView strip : currentStrips) {
                if (strip != null) {
                    strip.setVisible(false);
                }
            }
            return;
        }

        double depth = h * 0.12;
        double curRegion = w * (1.0 - p);
        double curOrigin = forward ? 0 : w - curRegion;
        double curStripW = curRegion / foldCount;

        for (int i = 0; i < foldCount; i++) {
            if (currentPTs[i] == null) {
                continue;
            }
            currentStrips[i].setVisible(true);
            boolean even = (i % 2 == 0);
            double d = even ? depth * p : 0;
            double di = even ? 0 : depth * p;

            setPT(currentPTs[i], 0, d, curStripW, di, curStripW, h - di, 0, h - d);
            // Reposition with a transform, not setLayoutX: the strips are unmanaged
            // but live in the managed contentPane, so a per-frame layoutX change would
            // bubble requestParentLayout to the transition host every pulse. The base
            // layoutX (i * stripW) is set once in setup; translate only the delta.
            currentStrips[i].setTranslateX(curOrigin + i * curStripW - i * stripW);
        }
    }

    /**
     * Vertical accordion: same logic rotated 90 degrees.
     */
    private void updateVertical(double p, double w, double h, double stripH, boolean forward) {
        if (p >= 0.98) {
            for (ImageView strip : currentStrips) {
                if (strip != null) {
                    strip.setVisible(false);
                }
            }
            return;
        }

        double depth = w * 0.12;
        double curRegion = h * (1.0 - p);
        double curOrigin = forward ? 0 : h - curRegion;
        double curStripH = curRegion / foldCount;

        for (int i = 0; i < foldCount; i++) {
            if (currentPTs[i] == null) {
                continue;
            }
            currentStrips[i].setVisible(true);
            boolean even = (i % 2 == 0);
            double d = even ? depth * p : 0;
            double di = even ? 0 : depth * p;

            setPT(currentPTs[i], d, 0, w - d, 0, w - di, curStripH, di, curStripH);
            // Transform, not setLayoutY — see updateHorizontal. Base layoutY (i * stripH)
            // is set once in setup; translate only the per-frame delta.
            currentStrips[i].setTranslateY(curOrigin + i * curStripH - i * stripH);
        }
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

    private void removeStrips() {
        if (currentStrips != null && animContentPane != null) {
            for (ImageView strip : currentStrips) {
                if (strip != null) {
                    strip.setEffect(null);
                    strip.setImage(null);
                    animContentPane.getChildren().remove(strip);
                }
            }
        }
        currentStrips = null;
        currentPTs = null;
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
