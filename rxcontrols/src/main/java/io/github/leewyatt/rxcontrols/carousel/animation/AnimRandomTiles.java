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
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.Random;

/**
 * Tile shatter transition. The current page is split into an N×M grid of
 * tiles that disappear in random order (each with a random delay), revealing
 * the next page beneath.
 *
 * <p>Each tile shrinks, rotates slightly, and fades out, creating a
 * shattering / dissolving effect.</p>
 *
 * <p><b>Note:</b> This animation uses snapshots and is not resize-safe.
 * If the carousel is resized during the animation, it will jump to its
 * end state automatically.</p>
 */
public class AnimRandomTiles extends CarouselAnimationBase {

    private final int cols;
    private final int rows;
    private Interpolator interpolator = Interpolator.EASE_IN;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    private ImageView[] tiles;
    private double[] delays;
    private double[] rotations;
    private StackPane animContentPane;

    /**
     * Creates a tile animation with a 6×4 grid.
     */
    public AnimRandomTiles() {
        this(6, 4);
    }

    /**
     * Creates a tile animation with the specified grid dimensions.
     *
     * @param cols number of columns (at least 1, recommended 2 to 10)
     * @param rows number of rows (at least 1, recommended 2 to 10)
     */
    public AnimRandomTiles(int cols, int rows) {
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
        animContentPane = context.getContentPane();

        double w = animContentPane.getWidth();
        double h = animContentPane.getHeight();

        installResizeGuard(animContentPane);

        // Next page visible as background
        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
        }

        // Snapshot current page
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);

        WritableImage snap = currentPage != null ? currentPage.snapshot(params, null) : null;
        if (currentPage != null) {
            currentPage.setVisible(false);
        }

        // Create tiles
        int total = cols * rows;
        tiles = new ImageView[total];
        delays = new double[total];
        rotations = new double[total];

        double tileW = w / cols;
        double tileH = h / rows;

        Random rng = new Random();

        if (snap != null) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int idx = r * cols + c;

                    ImageView tile = new ImageView(snap);
                    tile.setManaged(false);
                    tile.setViewport(new Rectangle2D(c * tileW, r * tileH, tileW, tileH));
                    tile.setLayoutX(c * tileW);
                    tile.setLayoutY(r * tileH);

                    tiles[idx] = tile;
                    delays[idx] = rng.nextDouble() * 0.6;
                    rotations[idx] = (rng.nextDouble() - 0.5) * 30;

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

        progressListener = (obs, oldVal, newVal) -> {
            double p = newVal.doubleValue();
            updateTiles(p);
        };
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
     * Each tile has a random delay in [0, 0.6]. Its own disappearance
     * takes the remaining 0.4 of the total duration. Multiple tiles
     * are disappearing simultaneously.
     */
    private void updateTiles(double p) {
        double fallDuration = 0.4;

        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] == null) {
                continue;
            }

            double localP = Math.max(0, Math.min(1, (p - delays[i]) / fallDuration));
            double easedP = interpolator.interpolate(0.0, 1.0, localP);

            double scale = 1.0 - easedP;
            tiles[i].setScaleX(scale);
            tiles[i].setScaleY(scale);
            tiles[i].setOpacity(1.0 - easedP);
            tiles[i].setRotate(rotations[i] * easedP);
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
        rotations = null;
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
