package io.github.leewyatt.rxcontrols.carousel.animation;

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
 * Serpentine reveal transition. The page is divided into a grid and cells
 * appear one by one in serpentine order (left-to-right on even rows,
 * right-to-left on odd rows) until the entire page is revealed.
 */
public class AnimSerpentine extends CarouselAnimationBase {

    private int rows = 4;
    private int cols = 6;
    private Interpolator interpolator = Interpolator.LINEAR;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private Node clippedNode;

    /**
     * Creates a serpentine reveal animation with default 4 rows and 6 columns.
     */
    public AnimSerpentine() {
    }

    /**
     * Creates a serpentine reveal animation with the given grid size.
     *
     * @param rows number of rows
     * @param cols number of columns
     */
    public AnimSerpentine(int rows, int cols) {
        this.rows = Math.max(1, rows);
        this.cols = Math.max(1, cols);
    }

    /**
     * Returns the number of rows in the grid.
     *
     * @return the number of rows
     */
    public int getRows() {
        return rows;
    }

    /**
     * Sets the number of rows in the grid.
     *
     * @param rows the number of rows (minimum 1)
     */
    public void setRows(int rows) {
        this.rows = Math.max(1, rows);
    }

    /**
     * Returns the number of columns in the grid.
     *
     * @return the number of columns
     */
    public int getCols() {
        return cols;
    }

    /**
     * Sets the number of columns in the grid.
     *
     * @param cols the number of columns (minimum 1)
     */
    public void setCols(int cols) {
        this.cols = Math.max(1, cols);
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
        StackPane contentPane = context.getContentPane();
        boolean forward = context.getDirection() == io.github.leewyatt.rxcontrols.carousel.Direction.FORWARD;

        if (nextPage != null) {
            nextPage.setVisible(true);
        }

        clippedNode = nextPage;
        final int[][] order = forward ? computeOrder() : computeReverseOrder();

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

    private int[][] computeOrder() {
        int[][] order = new int[rows][cols];
        int seq = 0;
        for (int r = 0; r < rows; r++) {
            if (r % 2 == 0) {
                for (int c = 0; c < cols; c++) {
                    order[r][c] = seq++;
                }
            } else {
                for (int c = cols - 1; c >= 0; c--) {
                    order[r][c] = seq++;
                }
            }
        }
        return order;
    }

    private int[][] computeReverseOrder() {
        int[][] order = computeOrder();
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
