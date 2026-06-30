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
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

/**
 * Checkerboard transition. The current page disappears in a two-wave
 * checkerboard pattern, revealing the next page beneath.
 *
 * <p>The page area is divided into a grid. Cells at odd positions
 * (row+col is odd) shrink and vanish in the first half of the
 * animation, then cells at even positions follow in the second half.
 * Each cell contracts toward its center as it disappears.</p>
 *
 * <p>FORWARD: odd cells first, then even cells.
 * BACKWARD: even cells first, then odd cells.</p>
 *
 * <p>This animation is resize-safe: it reads the content pane
 * dimensions on every frame to compute cell positions and sizes.</p>
 */
public class AnimCheckerboard extends PageAnimationBase {

    private final int cols;
    private final int rows;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;
    private StackPane animContentPane;

    /**
     * Creates a checkerboard animation with an 8x6 grid.
     */
    public AnimCheckerboard() {
        this(8, 6);
    }

    /**
     * Creates a checkerboard animation with the given grid size.
     *
     * @param cols number of columns (at least 1, recommended 4 to 12)
     * @param rows number of rows (at least 1, recommended 4 to 12)
     */
    public AnimCheckerboard(int cols, int rows) {
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);
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

        if (nextPage != null) {
            nextPage.setVisible(true);
            nextPage.toBack();
        }
        if (currentPage != null) {
            currentPage.setVisible(true);
            currentPage.toFront();
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setClip(null);
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
            double w = animContentPane.getWidth();
            double h = animContentPane.getHeight();
            updateCheckerboard(newVal.doubleValue(), w, h,
                    currentPage, forward);
        };
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

    private void updateCheckerboard(double p, double w, double h,
                                     Node currentPage, boolean forward) {
        if (currentPage == null) {
            return;
        }

        if (p >= 0.99) {
            currentPage.setClip(null);
            currentPage.setVisible(false);
            return;
        }

        if (p <= 0.01) {
            currentPage.setClip(null);
            return;
        }

        double cellW = w / cols;
        double cellH = h / rows;

        // Wave 1: p 0→0.5, Wave 2: p 0.5→1
        // FORWARD: wave1 = odd cells, wave2 = even cells
        // BACKWARD: wave1 = even cells, wave2 = odd cells

        double wave1P = RXMath.clamp(p / 0.5, 0.0, 1.0);
        double wave2P = RXMath.clamp((p - 0.5) / 0.5, 0.0, 1.0);

        Shape clip = null;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                boolean odd = (r + c) % 2 != 0;
                boolean isWave1 = forward ? odd : !odd;

                double localP = isWave1 ? wave1P : wave2P;
                double shrink = 1.0 - localP;

                if (shrink <= 0.001) {
                    continue;
                }

                double cw = cellW * shrink;
                double ch = cellH * shrink;
                double cx = c * cellW + (cellW - cw) / 2.0;
                double cy = r * cellH + (cellH - ch) / 2.0;

                Rectangle rect = new Rectangle(cx, cy, cw, ch);
                clip = (clip == null) ? rect : Shape.union(clip, rect);
            }
        }

        if (clip == null) {
            currentPage.setClip(null);
            currentPage.setVisible(false);
        } else {
            currentPage.setClip(clip);
        }
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
        animContentPane = null;
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        animContentPane = null;
        super.dispose();
    }
}
