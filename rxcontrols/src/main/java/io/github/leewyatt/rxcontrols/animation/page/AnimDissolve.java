package io.github.leewyatt.rxcontrols.animation.page;

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
 * Dissolve transition. The current page is divided into a grid of small
 * blocks that fade out at randomized times, progressively revealing the
 * next page underneath — a classic "film dissolve" effect.
 *
 * <p>Unlike {@link AnimCheckerboard} (regular alternating pattern),
 * dissolve blocks disappear in a random, gradual fashion, creating an
 * organic "melting away" look.</p>
 *
 * <p>The {@link #setCols(int)} and {@link #setRows(int)} parameters
 * control the grid density:</p>
 * <ul>
 *   <li><b>Low (8 ~ 12):</b> large, clearly visible blocks — chunky
 *       retro feel.</li>
 *   <li><b>Medium (16 ~ 24):</b> balanced look.
 *       <em>(default: 20 × 12)</em></li>
 *   <li><b>High (30 ~ 50):</b> very fine blocks — smoother, more
 *       film-like dissolve.</li>
 * </ul>
 *
 * <p>FORWARD biases earlier dissolution toward the leading edge;
 * BACKWARD reverses this bias.</p>
 *
 * <p><b>Note:</b> This animation uses snapshots and is not resize-safe.
 * If the host is resized during the animation, it will jump to its
 * end state automatically.</p>
 */
public class AnimDissolve extends PageAnimationBase {

    private int cols;
    private int rows;
    private Interpolator interpolator = Interpolator.EASE_IN;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    private ImageView[] blocks;
    private double[] thresholds;
    private StackPane animContentPane;

    /**
     * Creates a dissolve animation with a 20 × 12 grid.
     */
    public AnimDissolve() {
        this(20, 12);
    }

    /**
     * Creates a dissolve animation with the given grid size.
     *
     * @param cols number of columns (at least 4, recommended 10 to 50)
     * @param rows number of rows (at least 4, recommended 8 to 30)
     */
    public AnimDissolve(int cols, int rows) {
        this.cols = Math.max(4, cols);
        this.rows = Math.max(4, rows);
    }

    /**
     * Returns the interpolator used for each block's fade.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator for each block's fade.
     *
     * @param interpolator the interpolator
     */
    public void setInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    /**
     * Returns the number of columns.
     *
     * @return the column count
     */
    public int getCols() {
        return cols;
    }

    /**
     * Sets the number of columns.
     *
     * @param cols at least 4, recommended 10 to 50
     */
    public void setCols(int cols) {
        this.cols = Math.max(4, cols);
    }

    /**
     * Returns the number of rows.
     *
     * @return the row count
     */
    public int getRows() {
        return rows;
    }

    /**
     * Sets the number of rows.
     *
     * @param rows at least 4, recommended 8 to 30
     */
    public void setRows(int rows) {
        this.rows = Math.max(4, rows);
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

        boolean forward = direction == TransitionDirection.FORWARD;

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

        // Create grid blocks
        int total = cols * rows;
        blocks = new ImageView[total];
        thresholds = new double[total];
        double cellW = w / cols;
        double cellH = h / rows;

        Random random = new Random();

        // The stagger window: blocks start fading within [0, staggerEnd].
        // Each block's individual fade takes (1 - staggerEnd) of the
        // total duration, ensuring all blocks finish by progress=1.
        double staggerEnd = 0.7;

        if (snap != null) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int idx = r * cols + c;

                    ImageView block = new ImageView(snap);
                    block.setManaged(false);
                    block.setViewport(new Rectangle2D(
                            c * cellW, r * cellH, cellW, cellH));
                    block.setLayoutX(c * cellW);
                    block.setLayoutY(r * cellH);
                    blocks[idx] = block;
                    animContentPane.getChildren().add(block);

                    // Random base + directional spatial bias (up to 20%)
                    double bias;
                    if (forward) {
                        bias = (double) c / (cols - 1) * 0.2;
                    } else {
                        bias = (1.0 - (double) c / (cols - 1)) * 0.2;
                    }
                    thresholds[idx] = Math.min(staggerEnd,
                            random.nextDouble() * staggerEnd * 0.8 + bias);
                }
            }
        }

        Runnable finish = () -> {
            removeResizeGuard();
            removeBlocks();
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

        double fadeDuration = 1.0 - staggerEnd;

        progressListener = (obs, oldVal, newVal) ->
                updateBlocks(newVal.doubleValue(), fadeDuration);
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

    private void updateBlocks(double p, double fadeDuration) {
        if (blocks == null) {
            return;
        }

        for (int i = 0; i < blocks.length; i++) {
            if (blocks[i] == null) {
                continue;
            }

            double threshold = thresholds[i];
            if (p <= threshold) {
                blocks[i].setOpacity(1.0);
            } else {
                double localP = Math.min(1.0, (p - threshold) / fadeDuration);
                double eased = interpolator.interpolate(0.0, 1.0, localP);
                blocks[i].setOpacity(1.0 - eased);
            }
        }
    }

    private void removeBlocks() {
        if (blocks != null && animContentPane != null) {
            for (ImageView block : blocks) {
                if (block != null) {
                    block.setImage(null);
                    block.setOpacity(1);
                    animContentPane.getChildren().remove(block);
                }
            }
        }
        blocks = null;
        thresholds = null;
        animContentPane = null;
    }

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removeBlocks();
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removeBlocks();
        super.dispose();
    }
}
