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
 * Shred transition. The current page is sliced into strips that fly
 * out in alternating perpendicular directions, like paper being torn
 * apart.
 *
 * <p>{@link Orientation#HORIZONTAL} produces horizontal strips (full
 * width) that fly up and down alternately. {@link Orientation#VERTICAL}
 * produces vertical strips (full height) that fly left and right
 * alternately.</p>
 *
 * <p>All strips launch simultaneously for an explosive tearing feel.
 * FORWARD and BACKWARD swap which strips fly in which direction.</p>
 *
 * <p><b>Note:</b> This animation uses snapshots and is not resize-safe.
 * If the carousel is resized during the animation, it will jump to its
 * end state automatically.</p>
 */
public class AnimShred extends CarouselAnimationBase {

    private final Orientation orientation;
    private final int stripCount;
    private Interpolator interpolator = Interpolator.EASE_IN;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    private ImageView[] strips;
    private double[] flyDistances;
    private boolean horizontal;
    private StackPane animContentPane;

    /**
     * Creates a horizontal shred animation with 12 strips.
     */
    public AnimShred() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a shred animation with the given orientation and 12 strips.
     *
     * @param orientation the strip direction
     */
    public AnimShred(Orientation orientation) {
        this(orientation, 12);
    }

    /**
     * Creates a shred animation with the given orientation and strip count.
     *
     * @param orientation the strip direction
     * @param stripCount  the number of strips (at least 2, recommended 8 to 20)
     */
    public AnimShred(Orientation orientation, int stripCount) {
        this.orientation = orientation;
        this.stripCount = Math.max(2, stripCount);
    }

    /**
     * Returns the interpolator used for each strip's flight.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator for each strip's flight.
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

        double w = animContentPane.getWidth();
        double h = animContentPane.getHeight();

        installResizeGuard(animContentPane);

        horizontal = orientation == Orientation.HORIZONTAL;
        boolean forward = direction == Direction.FORWARD;

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
        flyDistances = new double[stripCount];

        // Fly distance perpendicular to the strip's long axis
        double flyDist = (horizontal ? h : w) * 1.2;

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

                // Odd/even alternate direction; BACKWARD swaps
                boolean odd = i % 2 != 0;
                boolean negative = forward ? odd : !odd;
                flyDistances[i] = negative ? -flyDist : flyDist;

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
        double easedP = interpolator.interpolate(0.0, 1.0, p);

        for (int i = 0; i < strips.length; i++) {
            if (strips[i] == null) {
                continue;
            }

            double offset = flyDistances[i] * easedP;
            if (horizontal) {
                strips[i].setTranslateY(offset);
            } else {
                strips[i].setTranslateX(offset);
            }
            strips[i].setOpacity(1.0 - easedP);
        }
    }

    private void removeStrips() {
        if (strips != null && animContentPane != null) {
            for (ImageView strip : strips) {
                if (strip != null) {
                    strip.setImage(null);
                    strip.setTranslateX(0);
                    strip.setTranslateY(0);
                    strip.setOpacity(1);
                    animContentPane.getChildren().remove(strip);
                }
            }
        }
        strips = null;
        flyDistances = null;
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