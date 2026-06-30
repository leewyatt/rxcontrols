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
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Honeycomb dissolve transition. The current page is overlaid with
 * hexagonal cells that shrink and fade out in a radial wave expanding
 * from the page center, revealing the next page beneath.
 *
 * <p>The hexagons are arranged in a flat-top honeycomb tessellation.
 * This animation is direction-independent: both FORWARD and BACKWARD
 * produce the same center-outward dissolve.</p>
 *
 * <p><b>Note:</b> This animation uses snapshots and is not resize-safe.
 * If the host is resized during the animation, it will jump to its
 * end state automatically.</p>
 */
public class AnimHoneycomb extends PageAnimationBase {

    private final double hexRadius;
    private Interpolator interpolator = Interpolator.EASE_IN;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    private ChangeListener<Number> progressListener;

    private ImageView[] cells;
    private double[] delays;
    private StackPane animContentPane;

    /**
     * Creates a honeycomb animation with a 50px hex radius.
     */
    public AnimHoneycomb() {
        this(50);
    }

    /**
     * Creates a honeycomb animation with the given hex radius.
     *
     * @param hexRadius center-to-vertex radius in pixels (at least 5, recommended 20 to 80)
     */
    public AnimHoneycomb(double hexRadius) {
        this.hexRadius = Math.max(5, hexRadius);
    }

    /**
     * Returns the interpolator used for each cell's disappearance.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator for each cell's disappearance.
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

        List<ImageView> cellList = new ArrayList<>();
        List<Double> delayList = new ArrayList<>();

        if (snap != null) {
            double colSpacing = hexRadius * 1.5;
            double rowSpacing = hexRadius * Math.sqrt(3);
            double halfRow = rowSpacing / 2.0;

            double pageCx = w / 2.0;
            double pageCy = h / 2.0;
            double maxDist = Math.sqrt(pageCx * pageCx + pageCy * pageCy);

            int colCount = (int) Math.ceil(w / colSpacing) + 2;
            int rowCount = (int) Math.ceil(h / rowSpacing) + 2;

            for (int col = -1; col <= colCount; col++) {
                double cx = col * colSpacing;
                double yOffset = (col % 2 != 0) ? halfRow : 0;

                for (int row = -1; row <= rowCount; row++) {
                    double cy = row * rowSpacing + yOffset;

                    if (cx + hexRadius < 0 || cx - hexRadius > w
                            || cy + hexRadius < 0 || cy - hexRadius > h) {
                        continue;
                    }

                    ImageView cell = new ImageView(snap);
                    cell.setManaged(false);
                    cell.setClip(createHexClip(cx, cy, hexRadius));

                    cellList.add(cell);

                    double dx = cx - pageCx;
                    double dy = cy - pageCy;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    double normalized = (maxDist > 0) ? dist / maxDist : 0;
                    delayList.add(normalized * 0.5);

                    animContentPane.getChildren().add(cell);
                }
            }
        }

        int count = cellList.size();
        cells = cellList.toArray(new ImageView[0]);
        delays = new double[count];
        for (int i = 0; i < count; i++) {
            delays[i] = delayList.get(i);
        }

        Runnable finish = () -> {
            removeResizeGuard();
            removeCells();
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
                updateCells(newVal.doubleValue());
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

    private static Polygon createHexClip(double cx, double cy, double r) {
        double[] points = new double[12];
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60.0 * i);
            points[i * 2] = cx + r * Math.cos(angle);
            points[i * 2 + 1] = cy + r * Math.sin(angle);
        }
        return new Polygon(points);
    }

    private void updateCells(double p) {
        double fadeDuration = 0.5;

        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == null) {
                continue;
            }

            double localP = RXMath.clamp((p - delays[i]) / fadeDuration, 0.0, 1.0);
            double easedP = interpolator.interpolate(0.0, 1.0, localP);

            double scale = 1.0 - easedP;
            cells[i].setScaleX(scale);
            cells[i].setScaleY(scale);
            cells[i].setOpacity(1.0 - easedP);
        }
    }

    private void removeCells() {
        if (cells != null && animContentPane != null) {
            for (ImageView cell : cells) {
                if (cell != null) {
                    cell.setClip(null);
                    cell.setImage(null);
                    cell.setScaleX(1);
                    cell.setScaleY(1);
                    cell.setOpacity(1);
                    animContentPane.getChildren().remove(cell);
                }
            }
        }
        cells = null;
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
        removeCells();
    }

    @Override
    public void dispose() {
        if (progressListener != null) {
            progress.removeListener(progressListener);
            progressListener = null;
        }
        removeCells();
        super.dispose();
    }
}
