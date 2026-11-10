package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link RXSegmentedProgressBar} segment geometry against the device pixel grid of
 * its window.
 */
public class RXSegmentedProgressBarPixelGridTest {

    private static final double EPSILON = 1.0e-9;
    private static final double[] SCALES = {1.0, 1.5, 2.0};

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    /**
     * Verifies segment edges sit on the pixel grid with equal snapped gaps, widths within one
     * device pixel of each other, and the last segment inside the content edge.
     */
    @Test
    public void segmentEdgesSitOnThePixelGrid() throws Exception {
        runOnFx(() -> {
            for (double scale : SCALES) {
                // 300 wide, 5 segments, gap 4: 56.8 per segment unsnapped.
                RXSegmentedProgressBar bar = new RXSegmentedProgressBar();
                Pane root = new Pane(bar);
                stageFor(root, scale);
                layout(root, bar, 300.0);
                List<Region> tracks = sorted(bar, ".track");
                assertEquals(5, tracks.size());
                double unit = 1.0 / scale;
                double gap = Math.round(4.0 * scale) / scale;
                double min = Double.MAX_VALUE;
                double max = 0.0;
                for (int i = 0; i < tracks.size(); i++) {
                    Region track = tracks.get(i);
                    String at = "scale " + scale + ", segment " + i;
                    assertOnGrid(track.getLayoutX(), scale, at + " x");
                    assertOnGrid(track.getWidth(), scale, at + " width");
                    if (i > 0) {
                        Region previous = tracks.get(i - 1);
                        assertEquals(gap, track.getLayoutX() - (previous.getLayoutX() + previous.getWidth()), EPSILON,
                                at + " gap");
                    }
                    min = Math.min(min, track.getWidth());
                    max = Math.max(max, track.getWidth());
                }
                assertTrue(max - min <= unit + EPSILON, "scale " + scale + ": widths " + min + ".." + max);
                Region last = tracks.get(tracks.size() - 1);
                double contentRight = bar.getWidth() - bar.snappedRightInset();
                assertTrue(last.getLayoutX() + last.getWidth() <= contentRight + EPSILON,
                        "scale " + scale + ": last segment ends past the content edge");
                assertTrue(last.getLayoutX() + last.getWidth() > contentRight - unit,
                        "scale " + scale + ": last segment ends a whole device pixel short");
            }
        });
    }

    /**
     * Verifies each fill covers its own snapped segment width for a determinate progress.
     */
    @Test
    public void fillsFollowTheSnappedSegmentWidths() throws Exception {
        runOnFx(() -> {
            RXSegmentedProgressBar bar = new RXSegmentedProgressBar();
            bar.setProgress(0.5);
            Pane root = new Pane(bar);
            stageFor(root, 1.5);
            layout(root, bar, 300.0);
            List<Region> tracks = sorted(bar, ".track");
            List<Region> fills = sorted(bar, ".segment-fill");
            double[] ratios = {1.0, 1.0, 0.5, 0.0, 0.0};
            for (int i = 0; i < fills.size(); i++) {
                Rectangle clip = (Rectangle) fills.get(i).getClip();
                assertEquals(tracks.get(i).getWidth(), fills.get(i).getWidth(), EPSILON, "fill " + i + " width");
                assertEquals(tracks.get(i).getWidth() * ratios[i], clip.getWidth(), EPSILON, "fill " + i + " clip");
            }
        });
    }

    private static void assertOnGrid(double value, double scale, String message) {
        double device = value * scale;
        assertEquals(Math.rint(device), device, 1.0e-6, message + " " + value + " is off the grid");
    }

    private static List<Region> sorted(RXSegmentedProgressBar bar, String selector) {
        List<Region> regions = new ArrayList<>();
        for (Node node : bar.lookupAll(selector)) {
            regions.add((Region) node);
        }
        regions.sort(Comparator.comparingDouble(Region::getLayoutX));
        return regions;
    }

    private static void stageFor(Pane root, double scale) {
        Stage stage = new Stage();
        stage.setScene(new Scene(root));
        stage.setRenderScaleX(scale);
        stage.setRenderScaleY(scale);
    }

    private static void layout(Pane root, RXSegmentedProgressBar bar, double width) {
        root.applyCss();
        bar.resize(width, bar.prefHeight(width));
        bar.layout();
    }

    private static void runOnFx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
    }
}
