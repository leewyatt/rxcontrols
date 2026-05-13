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
import javafx.geometry.Orientation;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Cascade transition. The current page is sliced into strips that
 * fly out one after another in sequential order, like a waterfall curtain.
 *
 * <p>Supports both {@link Orientation#HORIZONTAL} (default, vertical strips
 * dropping downward, cascading left-to-right or right-to-left) and
 * {@link Orientation#VERTICAL} (horizontal strips sliding sideways,
 * cascading top-to-bottom or bottom-to-top).</p>
 *
 * <p>Each strip accelerates with gravity-like easing, producing a
 * rhythmic, sequential "domino-waterfall" effect that is distinct from
 * {@link AnimDomino} (which flips strips flat) and {@link AnimMelt}
 * (which spreads from the center outward).</p>
 *
 * <p><b>Note:</b> This animation uses snapshots and is not resize-safe.</p>
 */
public class AnimCascade extends CarouselAnimationBase {

    private final Orientation orientation;
    private int stripCount = 14;
    private double staggerFactor = 0.65;
    private Interpolator interpolator = Interpolator.EASE_IN;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private ImageView[] strips;
    private StackPane animContentPane;
    private double capturedSize;

    /**
     * Creates a horizontal cascade animation with 14 strips.
     */
    public AnimCascade() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a cascade animation with the given orientation and 14 strips.
     *
     * @param orientation the cascade orientation
     */
    public AnimCascade(Orientation orientation) {
        this(orientation, 14);
    }

    /**
     * Creates a cascade animation with the given orientation and strip count.
     *
     * @param orientation the cascade orientation
     * @param stripCount  the number of strips (minimum 2)
     */
    public AnimCascade(Orientation orientation, int stripCount) {
        this.orientation = orientation;
        this.stripCount = Math.max(2, stripCount);
    }

    /**
     * Returns the orientation.
     *
     * @return the orientation
     */
    public Orientation getOrientation() {
        return orientation;
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
     * Sets the number of strips. Recommended range: 8–24.
     *
     * @param stripCount the strip count (minimum 2)
     */
    public void setStripCount(int stripCount) {
        this.stripCount = Math.max(2, stripCount);
    }

    /**
     * Returns the stagger factor.
     *
     * @return the stagger factor
     */
    public double getStaggerFactor() {
        return staggerFactor;
    }

    /**
     * Sets the stagger factor controlling how much the strip start
     * times are spread out. Recommended range: 0.4–0.8.
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
     * Sets the interpolator for each strip's fall. Default is
     * EASE_IN (accelerating, like gravity).
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
        Direction direction = context.getDirection();
        animContentPane = context.getContentPane();

        boolean forward = direction == Direction.FORWARD;
        boolean horizontal = orientation == Orientation.HORIZONTAL;
        double w = animContentPane.getWidth();
        double h = animContentPane.getHeight();
        capturedSize = horizontal ? h : w;

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

            if (horizontal) {
                double stripW = w / stripCount;
                for (int i = 0; i < stripCount; i++) {
                    ImageView strip = new ImageView(snap);
                    strip.setManaged(false);
                    strip.setViewport(new Rectangle2D(i * stripW, 0, stripW, h));
                    strip.setLayoutX(i * stripW);
                    strip.setLayoutY(0);
                    strips[i] = strip;
                }
            } else {
                double stripH = h / stripCount;
                for (int i = 0; i < stripCount; i++) {
                    ImageView strip = new ImageView(snap);
                    strip.setManaged(false);
                    strip.setViewport(new Rectangle2D(0, i * stripH, w, stripH));
                    strip.setLayoutX(0);
                    strip.setLayoutY(i * stripH);
                    strips[i] = strip;
                }
            }

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

        progressListener = (obs, oldVal, newVal) ->
                updateStrips(newVal.doubleValue(), forward, horizontal);
        progress.addListener(progressListener);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(progress, 0)),
                new KeyFrame(duration, new KeyValue(progress, 1, Interpolator.LINEAR))
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
     * Updates each strip's position. HORIZONTAL: strips drop downward,
     * FORWARD left-to-right, BACKWARD right-to-left. VERTICAL: strips
     * slide sideways, FORWARD top-to-bottom, BACKWARD bottom-to-top.
     */
    private void updateStrips(double p, boolean forward, boolean horizontal) {
        if (strips == null) {
            return;
        }

        double fallDuration = 1.0 - staggerFactor;

        for (int i = 0; i < strips.length; i++) {
            ImageView strip = strips[i];
            if (strip == null) {
                continue;
            }

            int orderIdx = forward ? i : (strips.length - 1 - i);
            double delay = (strips.length > 1)
                    ? (double) orderIdx / (strips.length - 1) * staggerFactor
                    : 0;
            double localP = Math.max(0, Math.min(1, (p - delay) / fallDuration));

            double easedP = interpolator.interpolate(0.0, 1.0, localP);

            if (horizontal) {
                strip.setTranslateY(capturedSize * 1.2 * easedP);
            } else {
                strip.setTranslateX(capturedSize * 1.2 * easedP);
            }

            strip.setOpacity(1.0 - easedP * 0.7);
        }
    }

    private void removeStrips() {
        if (strips != null && animContentPane != null) {
            for (ImageView strip : strips) {
                if (strip != null) {
                    strip.setImage(null);
                    animContentPane.getChildren().remove(strip);
                }
            }
        }
        strips = null;
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
