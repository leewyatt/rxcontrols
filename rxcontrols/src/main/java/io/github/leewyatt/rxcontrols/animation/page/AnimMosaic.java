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
 * Mosaic dissolve transition. The current page is split into a grid of
 * tiles that shrink and fade out in a wave pattern, revealing the next
 * page beneath.
 *
 * <p>The wave propagation direction is controlled by an {@link Orientation}:
 * horizontal waves sweep column by column, vertical waves sweep row by
 * row. The {@link TransitionDirection} of the transition determines which side the
 * wave starts from: FORWARD sweeps left-to-right (or top-to-bottom),
 * BACKWARD reverses the sweep.</p>
 *
 * <p><b>Note:</b> This animation uses snapshots and is not resize-safe.
 * If the host is resized during the animation, it will jump to its
 * end state automatically.</p>
 */
public class AnimMosaic extends PageAnimationBase {

    private final Orientation orientation;
    private final int cols;
    private final int rows;
    private Interpolator interpolator = Interpolator.EASE_IN;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    private ImageView[] tiles;
    private double[] delays;
    private StackPane animContentPane;

    /**
     * Creates a horizontal mosaic animation with an 8x6 grid.
     */
    public AnimMosaic() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a mosaic animation with the given orientation and an 8x6 grid.
     *
     * @param orientation the wave propagation direction
     */
    public AnimMosaic(Orientation orientation) {
        this(orientation, 8, 6);
    }

    /**
     * Creates a mosaic animation with the given orientation and grid size.
     *
     * @param orientation the wave propagation direction
     * @param cols        number of columns (at least 1, recommended 2 to 10)
     * @param rows        number of rows (at least 1, recommended 2 to 10)
     */
    public AnimMosaic(Orientation orientation, int cols, int rows) {
        this.orientation = orientation;
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);
    }

    /**
     * Returns the interpolator used for each tile's disappearance.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator for each tile's disappearance.
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

        int total = cols * rows;
        tiles = new ImageView[total];
        delays = new double[total];

        double tileW = w / cols;
        double tileH = h / rows;

        boolean horizontal = orientation == Orientation.HORIZONTAL;
        boolean forward = direction == TransitionDirection.FORWARD;
        int steps = horizontal ? cols : rows;

        double staggerRange = 0.6;

        if (snap != null) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int idx = r * cols + c;

                    ImageView tile = new ImageView(snap);
                    tile.setManaged(false);
                    tile.setViewport(new Rectangle2D(
                            c * tileW, r * tileH, tileW, tileH));
                    tile.setLayoutX(c * tileW);
                    tile.setLayoutY(r * tileH);

                    tiles[idx] = tile;

                    int step = horizontal ? c : r;
                    if (!forward) {
                        step = (steps - 1) - step;
                    }
                    delays[idx] = (steps > 1)
                            ? (double) step / (steps - 1) * staggerRange
                            : 0;

                    animContentPane.getChildren().add(tile);
                }
            }
        }

        Runnable finish = () -> {
            removeResizeGuard();
            removeTiles();
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
                updateTiles(newVal.doubleValue());
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

    private void updateTiles(double p) {
        double fadeDuration = 0.4;

        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] == null) {
                continue;
            }

            double localP = RXMath.clamp((p - delays[i]) / fadeDuration, 0.0, 1.0);
            double easedP = interpolator.interpolate(0.0, 1.0, localP);

            double scale = 1.0 - easedP;
            tiles[i].setScaleX(scale);
            tiles[i].setScaleY(scale);
            tiles[i].setOpacity(1.0 - easedP);
        }
    }

    private void removeTiles() {
        if (tiles != null && animContentPane != null) {
            for (ImageView tile : tiles) {
                if (tile != null) {
                    tile.setImage(null);
                    animContentPane.getChildren().remove(tile);
                }
            }
        }
        tiles = null;
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
        removeTiles();
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removeTiles();
        super.dispose();
    }
}
