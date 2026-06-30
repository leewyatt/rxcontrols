package io.github.leewyatt.rxcontrols.animation.page;

import io.github.leewyatt.rxcontrols.utils.RXMath;

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
 * Wind strip transition. The current page is sliced into strips that
 * blow away with staggered timing, like pages caught in a gust of wind.
 *
 * <p>{@link Orientation#HORIZONTAL} produces horizontal strips (full
 * width, each a fraction of the height) that slide left or right.
 * {@link Orientation#VERTICAL} produces vertical strips (full height,
 * each a fraction of the width) that slide up or down.</p>
 *
 * <p>FORWARD sweeps strips from first to last and slides them in the
 * negative direction (left / up). BACKWARD reverses both the sweep
 * order and the slide direction.</p>
 *
 * <p><b>Note:</b> This animation uses snapshots and is not resize-safe.
 * If the host is resized during the animation, it will jump to its
 * end state automatically.</p>
 */
public class AnimWind extends PageAnimationBase {

    private final Orientation orientation;
    private final int stripCount;
    private Interpolator interpolator = Interpolator.EASE_IN;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    private ImageView[] strips;
    private double[] delays;
    private double slideDistance;
    private boolean horizontal;
    private boolean forward;
    private StackPane animContentPane;

    /**
     * Creates a horizontal wind animation with 10 strips.
     */
    public AnimWind() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a wind animation with the given orientation and 10 strips.
     *
     * @param orientation the strip and slide direction
     */
    public AnimWind(Orientation orientation) {
        this(orientation, 10);
    }

    /**
     * Creates a wind animation with the given orientation and strip count.
     *
     * @param orientation the strip and slide direction
     * @param stripCount  the number of strips (at least 2, recommended 4 to 16)
     */
    public AnimWind(Orientation orientation, int stripCount) {
        this.orientation = orientation;
        this.stripCount = Math.max(2, stripCount);
    }

    /**
     * Returns the interpolator used for each strip's slide-out.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator for each strip's slide-out.
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

        installResizeGuard(animContentPane);

        horizontal = orientation == Orientation.HORIZONTAL;
        forward = direction == TransitionDirection.FORWARD;
        slideDistance = (horizontal ? w : h) * 1.2;
        if (forward) {
            slideDistance = -slideDistance;
        }

        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
        }

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);

        WritableImage snap = currentPage != null
                ? currentPage.snapshot(params, null) : null;
        if (currentPage != null) {
            currentPage.setVisible(false);
        }

        strips = new ImageView[stripCount];
        delays = new double[stripCount];

        double staggerRange = 0.5;

        if (snap != null) {
            for (int i = 0; i < stripCount; i++) {
                ImageView strip;
                if (horizontal) {
                    double stripH = h / stripCount;
                    strip = new ImageView(snap);
                    strip.setManaged(false);
                    strip.setViewport(new Rectangle2D(
                            0, i * stripH, w, stripH));
                    strip.setLayoutX(0);
                    strip.setLayoutY(i * stripH);
                } else {
                    double stripW = w / stripCount;
                    strip = new ImageView(snap);
                    strip.setManaged(false);
                    strip.setViewport(new Rectangle2D(
                            i * stripW, 0, stripW, h));
                    strip.setLayoutX(i * stripW);
                    strip.setLayoutY(0);
                }

                strips[i] = strip;

                int step = forward ? i : (stripCount - 1 - i);
                delays[i] = (stripCount > 1)
                        ? (double) step / (stripCount - 1) * staggerRange
                        : 0;

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

        progressListener = (obs, oldVal, newVal) ->
                updateStrips(newVal.doubleValue());
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

    private void updateStrips(double p) {
        double slideDuration = 0.5;

        for (int i = 0; i < strips.length; i++) {
            if (strips[i] == null) {
                continue;
            }

            double localP = RXMath.clamp((p - delays[i]) / slideDuration, 0.0, 1.0);
            double easedP = interpolator.interpolate(0.0, 1.0, localP);

            double offset = slideDistance * easedP;
            if (horizontal) {
                strips[i].setTranslateX(offset);
            } else {
                strips[i].setTranslateY(offset);
            }
            strips[i].setOpacity(1.0 - easedP);
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
        delays = null;
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
