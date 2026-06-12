package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Spiral tiles transition. The page is divided into a grid and cells
 * are revealed one by one along a spiral path — from the outer edge
 * inward (or the reverse), creating a "vortex focus" visual effect.
 *
 * <p>Compared to {@link AnimSerpentine} (row-by-row zigzag), the
 * spiral path draws the viewer's eye toward (or away from) the center,
 * producing a more dynamic reveal pattern.</p>
 *
 * <p>FORWARD reveals from outside inward (converging spiral).
 * BACKWARD reveals from center outward (expanding spiral).</p>
 *
 * <p>This animation does not use snapshots and is fully resize-safe.</p>
 */
public class AnimSpiralTiles extends PageAnimationBase {

    private int rows = 5;
    private int cols = 7;
    private Interpolator interpolator = Interpolator.LINEAR;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private Node clippedNode;

    /**
     * Creates a spiral tiles animation with default 5 rows and 7 columns.
     */
    public AnimSpiralTiles() {
    }

    /**
     * Creates a spiral tiles animation with the given grid size.
     *
     * @param rows number of rows (at least 2, recommended 4 to 10)
     * @param cols number of columns (at least 2, recommended 5 to 12)
     */
    public AnimSpiralTiles(int rows, int cols) {
        this.rows = Math.max(2, rows);
        this.cols = Math.max(2, cols);
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
     * @param rows at least 2, recommended 4 to 10
     */
    public void setRows(int rows) {
        this.rows = Math.max(2, rows);
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
     * @param cols at least 2, recommended 5 to 12
     */
    public void setCols(int cols) {
        this.cols = Math.max(2, cols);
    }

    /**
     * Returns the interpolator used for the reveal timing.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator used for the reveal timing.
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
        StackPane contentPane = context.getContentPane();
        boolean forward = context.getDirection() == io.github.leewyatt.rxcontrols.animation.page.TransitionDirection.FORWARD;

        if (nextPage != null) {
            nextPage.setVisible(true);
        }

        clippedNode = nextPage;
        final int[][] order = forward ? computeSpiralOrder() : computeReverseSpiralOrder();

        if (clippedNode != null) {
            clippedNode.toFront();
            clippedNode.setClip(createClipNode(0,
                    contentPane.getWidth(), contentPane.getHeight(), order));
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setClip(null);
                currentPage.setVisible(false);
            }
            if (nextPage != null) {
                nextPage.setClip(null);
                nextPage.setVisible(true);
            }
            clippedNode = null;
        };
        setFinishAction(finish);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        if (progressListener != null) {
            progress.removeListener(progressListener);
        }
        progress.set(0);

        progressListener = (obs, oldVal, newVal) -> {
            if (clippedNode != null) {
                double w = contentPane.getWidth();
                double h = contentPane.getHeight();
                int total = rows * cols;
                double p = newVal.doubleValue();
                int revealed = (p >= 1.0) ? total : (int) (p * total);
                clippedNode.setClip(createClipNode(revealed, w, h, order));
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
     * Computes spiral order from outside inward (clockwise).
     *
     * <pre>
     *  0  1  2  3  4
     * 15 16 17 18  5
     * 14 23 24 19  6
     * 13 22 21 20  7
     * 12 11 10  9  8
     * </pre>
     */
    private int[][] computeSpiralOrder() {
        int[][] order = new int[rows][cols];
        int seq = 0;
        int top = 0, bottom = rows - 1, left = 0, right = cols - 1;

        while (top <= bottom && left <= right) {
            // Top edge: left to right
            for (int c = left; c <= right; c++) {
                order[top][c] = seq++;
            }
            top++;

            // Right edge: top to bottom
            for (int r = top; r <= bottom; r++) {
                order[r][right] = seq++;
            }
            right--;

            // Bottom edge: right to left
            if (top <= bottom) {
                for (int c = right; c >= left; c--) {
                    order[bottom][c] = seq++;
                }
                bottom--;
            }

            // Left edge: bottom to top
            if (left <= right) {
                for (int r = bottom; r >= top; r--) {
                    order[r][left] = seq++;
                }
                left++;
            }
        }

        return order;
    }

    /**
     * Reverses the spiral order: center outward.
     */
    private int[][] computeReverseSpiralOrder() {
        int[][] order = computeSpiralOrder();
        int total = rows * cols;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                order[r][c] = total - 1 - order[r][c];
            }
        }
        return order;
    }

    private Node createClipNode(int revealed, double w, double h, int[][] order) {
        double cellW = w / cols;
        double cellH = h / rows;
        Group group = new Group();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (order[r][c] < revealed) {
                    group.getChildren().add(
                            new Rectangle(c * cellW, r * cellH, cellW, cellH));
                }
            }
        }
        return group;
    }

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        for (Node child : context.getContentPane().getChildren()) {
            child.setClip(null);
        }
        clippedNode = null;
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        clippedNode = null;
        super.dispose();
    }
}
