package io.github.leewyatt.rxcontrols.carousel.animation;

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
 * Barn door transition. The current page splits down the middle and
 * the two halves swing open like barn doors with 3D perspective,
 * revealing the next page underneath.
 *
 * <p>Unlike {@link AnimSplitWipe} (flat mask movement), the barn
 * door halves exhibit perspective foreshortening — the far edge of
 * each door appears to recede into the screen, giving a convincing
 * sense of physical depth.</p>
 *
 * <p>Supports both {@link Orientation#HORIZONTAL} (default, vertical
 * split — doors open left/right) and {@link Orientation#VERTICAL}
 * (horizontal split — doors open up/down).</p>
 *
 * <p>This animation is not direction-sensitive.</p>
 *
 * <p><b>Note:</b> This animation uses snapshots and is not resize-safe.
 * If the carousel is resized during the animation, it will jump to its
 * end state automatically.</p>
 */
public class AnimBarn extends CarouselAnimationBase {

    private final Orientation orientation;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    private ImageView doorA;
    private ImageView doorB;
    private PerspectiveTransform ptA;
    private PerspectiveTransform ptB;
    private StackPane animContentPane;

    /**
     * Creates a horizontal barn door animation (vertical split,
     * doors open left/right).
     */
    public AnimBarn() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a barn door animation with the given orientation.
     *
     * @param orientation HORIZONTAL for left/right doors,
     *                    VERTICAL for up/down doors
     */
    public AnimBarn(Orientation orientation) {
        this.orientation = orientation;
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
        animContentPane = context.getContentPane();

        double w = animContentPane.getWidth();
        double h = animContentPane.getHeight();
        boolean horizontal = orientation == Orientation.HORIZONTAL;

        installResizeGuard(animContentPane);

        // New page as background
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
        }

        // Snapshot current page and split into two doors
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);

        WritableImage snap = currentPage != null
                ? currentPage.snapshot(params, null) : null;
        if (currentPage != null) {
            currentPage.setVisible(false);
        }

        if (snap != null) {
            ptA = new PerspectiveTransform();
            ptB = new PerspectiveTransform();

            if (horizontal) {
                double halfW = w / 2.0;

                // Left door
                doorA = new ImageView(snap);
                doorA.setManaged(false);
                doorA.setViewport(new Rectangle2D(0, 0, halfW, h));
                doorA.setLayoutX(0);
                doorA.setLayoutY(0);
                doorA.setEffect(ptA);

                // Right door
                doorB = new ImageView(snap);
                doorB.setManaged(false);
                doorB.setViewport(new Rectangle2D(halfW, 0, halfW, h));
                doorB.setLayoutX(halfW);
                doorB.setLayoutY(0);
                doorB.setEffect(ptB);
            } else {
                double halfH = h / 2.0;

                // Top door
                doorA = new ImageView(snap);
                doorA.setManaged(false);
                doorA.setViewport(new Rectangle2D(0, 0, w, halfH));
                doorA.setLayoutX(0);
                doorA.setLayoutY(0);
                doorA.setEffect(ptA);

                // Bottom door
                doorB = new ImageView(snap);
                doorB.setManaged(false);
                doorB.setViewport(new Rectangle2D(0, halfH, w, halfH));
                doorB.setLayoutX(0);
                doorB.setLayoutY(halfH);
                doorB.setEffect(ptB);
            }

            // Initialize PT to p=0 before adding to scene graph
            updateDoors(0, w, h, horizontal);
            animContentPane.getChildren().addAll(doorA, doorB);
        }

        Runnable finish = () -> {
            removeResizeGuard();
            removeDoors();
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
                updateDoors(newVal.doubleValue(), capturedW, capturedH, horizontal);
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

    /**
     * Updates the PerspectiveTransform on each door half.
     *
     * <p>The hinge is on the outer edge of each door (left edge for
     * the left door, right edge for the right door). The center seam
     * edges swing toward their respective hinges with perspective
     * foreshortening, revealing the next page from the middle.</p>
     */
    private void updateDoors(double p, double w, double h, boolean horizontal) {
        if (ptA == null || ptB == null) {
            return;
        }

        // Hide doors before PT degenerates to zero-width quadrilateral
        if (p >= 0.98) {
            if (doorA != null) {
                doorA.setVisible(false);
            }
            if (doorB != null) {
                doorB.setVisible(false);
            }
            return;
        }

        if (horizontal) {
            double halfW = w / 2.0;
            // Perspective depth: moving edge contracts vertically
            double depth = h * 0.35 * p;

            // Left door: hinge on LEFT edge (x = 0), center seam swings left
            ptA.setUlx(0);
            ptA.setUly(0);
            ptA.setUrx(halfW * (1.0 - p));
            ptA.setUry(depth);
            ptA.setLrx(halfW * (1.0 - p));
            ptA.setLry(h - depth);
            ptA.setLlx(0);
            ptA.setLly(h);

            // Right door: hinge on RIGHT edge (x = halfW), center seam swings right
            ptB.setUlx(halfW * p);
            ptB.setUly(depth);
            ptB.setUrx(halfW);
            ptB.setUry(0);
            ptB.setLrx(halfW);
            ptB.setLry(h);
            ptB.setLlx(halfW * p);
            ptB.setLly(h - depth);
        } else {
            double halfH = h / 2.0;
            // Perspective depth: moving edge contracts horizontally
            double depth = w * 0.35 * p;

            // Top door: hinge on TOP edge (y = 0), center seam swings up
            ptA.setUlx(0);
            ptA.setUly(0);
            ptA.setUrx(w);
            ptA.setUry(0);
            ptA.setLrx(w - depth);
            ptA.setLry(halfH * (1.0 - p));
            ptA.setLlx(depth);
            ptA.setLly(halfH * (1.0 - p));

            // Bottom door: hinge on BOTTOM edge (y = halfH), center seam swings down
            ptB.setUlx(depth);
            ptB.setUly(halfH * p);
            ptB.setUrx(w - depth);
            ptB.setUry(halfH * p);
            ptB.setLrx(w);
            ptB.setLry(halfH);
            ptB.setLlx(0);
            ptB.setLly(halfH);
        }
    }

    private void removeDoors() {
        if (animContentPane != null) {
            if (doorA != null) {
                doorA.setEffect(null);
                doorA.setImage(null);
                animContentPane.getChildren().remove(doorA);
            }
            if (doorB != null) {
                doorB.setEffect(null);
                doorB.setImage(null);
                animContentPane.getChildren().remove(doorB);
            }
        }
        doorA = null;
        doorB = null;
        ptA = null;
        ptB = null;
        animContentPane = null;
    }

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removeDoors();
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removeDoors();
        super.dispose();
    }
}
