package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
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
 * Tests {@link RXSegmentedControl} equal-width segment geometry against the device pixel
 * grid of its window.
 */
public class RXSegmentedControlPixelGridTest {

    private static final double EPSILON = 1.0e-9;
    private static final double[] SCALES = {1.0, 1.25, 1.5, 1.75, 2.0};
    private static final double[] FRACTIONS = {0.0, 0.25, 0.5, 0.75, 1.0 / 3.0, 2.0 / 3.0};
    private static final double[] SPACINGS = {0.0, 3.0, 2.5, 0.6};
    private static final int COUNT = 4;

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
     * Verifies equal segments fill the content area the skin is given, on the pixel grid,
     * with equal snapped gaps and the indicator on the selected segment, at any scale.
     */
    @Test
    public void equalSegmentsFillTheContentArea() throws Exception {
        runOnFx(() -> {
            for (boolean[] mode : new boolean[][] {{true, false}, {false, true}, {true, true}}) {
                for (double spacing : SPACINGS) {
                    RXSegmentedControl<String> control = control(mode[0], mode[1], spacing);
                    Pane root = new Pane(control);
                    Stage stage = stageFor(root);
                    for (double scale : SCALES) {
                        stage.setRenderScaleX(scale);
                        for (int base = 300; base < 420; base++) {
                            for (double fraction : FRACTIONS) {
                                double width = base + fraction;
                                layout(root, control, width);
                                assertSegments(control, spacing, scale, "block " + mode[0] + ", equal " + mode[1]
                                        + ", spacing " + spacing + ", scale " + scale + ", width " + width);
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies a control laid out at its own minimum width keeps its last segment inside
     * the content area, because measure and layout share the snapped spacing.
     */
    @Test
    public void minimumWidthHoldsTheSegments() throws Exception {
        runOnFx(() -> {
            for (double spacing : SPACINGS) {
                RXSegmentedControl<String> control = control(true, false, spacing);
                Pane root = new Pane(control);
                Stage stage = stageFor(root);
                for (double scale : SCALES) {
                    stage.setRenderScaleX(scale);
                    root.applyCss();
                    double width = control.minWidth(-1.0);
                    layout(root, control, width);
                    List<Region> segments = segments(control);
                    Region last = segments.get(segments.size() - 1);
                    double hi = snapCeil(control.getWidth(), scale) - control.snappedRightInset();
                    String at = "spacing " + spacing + ", scale " + scale;
                    assertTrue(last.getLayoutX() + last.getWidth() <= hi + EPSILON, at + ": last segment ends past " + hi);
                    // The minimum width counts the same snapped gaps the layout leaves.
                    double widestMin = 0.0;
                    for (Region segment : segments) {
                        widestMin = Math.max(widestMin, segment.minWidth(-1.0));
                    }
                    double gap = Math.round(spacing * scale) / scale;
                    assertEquals(COUNT * widestMin + (COUNT - 1) * gap,
                            width - control.snappedLeftInset() - control.snappedRightInset(), EPSILON,
                            at + ": minimum width");
                }
            }
        });
    }

    /**
     * Verifies content-width segments compressed between their minimum and preferred
     * widths keep the snapped spacing, stay on the pixel grid and end inside the content.
     */
    @Test
    public void compressedContentSegmentsKeepTheSnappedSpacing() throws Exception {
        runOnFx(() -> {
            for (double spacing : new double[] {2.5, 0.6, 3.0}) {
                for (double scale : SCALES) {
                    RXSegmentedControl<String> control = control(false, false, spacing);
                    control.getItems().get(1).setText("A much longer item");
                    Pane root = new Pane(control);
                    stageFor(root).setRenderScaleX(scale);
                    root.applyCss();
                    double min = control.minWidth(-1.0);
                    double pref = control.prefWidth(-1.0);
                    double gap = Math.round(spacing * scale) / scale;
                    for (double width = min; width <= pref; width += 0.25) {
                        layout(root, control, width);
                        String at = "spacing " + spacing + ", scale " + scale + ", width " + width;
                        double hi = snapCeil(control.getWidth(), scale) - control.snappedRightInset();
                        List<Region> segments = segments(control);
                        for (int i = 0; i < segments.size(); i++) {
                            Region segment = segments.get(i);
                            assertOnGrid(segment.getLayoutX(), scale, at + ": x of segment " + i);
                            assertOnGrid(segment.getLayoutX() + segment.getWidth(), scale, at + ": end of segment " + i);
                            if (i > 0) {
                                Region previous = segments.get(i - 1);
                                assertEquals(gap, segment.getLayoutX() - (previous.getLayoutX() + previous.getWidth()),
                                        EPSILON, at + ": gap before segment " + i);
                            }
                        }
                        Region last = segments.get(segments.size() - 1);
                        assertTrue(last.getLayoutX() + last.getWidth() <= hi + EPSILON,
                                at + ": last segment ends past " + hi);
                    }
                }
            }
        });
    }

    /**
     * Verifies equal segments with the theme padding and no spacing end exactly on the
     * content edge, where ceiling each cumulative edge used to overshoot it.
     */
    @Test
    public void themePaddingWithoutSpacingEndsOnTheContentEdge() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = new RXSegmentedControl<>();
            for (int i = 0; i < 3; i++) {
                control.getItems().add(new RXSegmentedItem<>("v" + i, "Item " + i));
            }
            control.setBlock(true);
            control.setAnimated(false);
            control.setManaged(false);
            Pane root = new Pane(control);
            stageFor(root).setRenderScaleX(1.5);
            for (double width = 300.0; width < 320.0; width += 0.25) {
                layout(root, control, width);
                double hi = snapCeil(control.getWidth(), 1.5) - control.snappedRightInset();
                List<Region> segments = segments(control);
                Region last = segments.get(segments.size() - 1);
                assertEquals(hi, last.getLayoutX() + last.getWidth(), EPSILON, "width " + width + ": trailing edge");
            }
        });
    }

    /**
     * Verifies a width below the total gaps collapses the segments to zero width while the
     * gaps keep their snapped size.
     */
    @Test
    public void tooNarrowAWidthCollapsesTheSegments() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = control(true, false, 10.0);
            Pane root = new Pane(control);
            stageFor(root).setRenderScaleX(1.5);
            control.setPadding(Insets.EMPTY);
            layout(root, control, 12.0);
            List<Region> segments = segments(control);
            double gap = Math.round(10.0 * 1.5) / 1.5;
            for (int i = 0; i < segments.size(); i++) {
                assertEquals(0.0, segments.get(i).getWidth(), EPSILON, "segment " + i + " width");
                if (i > 0) {
                    assertEquals(gap, segments.get(i).getLayoutX() - segments.get(i - 1).getLayoutX(), EPSILON,
                            "gap before segment " + i);
                }
            }
        });
    }

    /**
     * Verifies a control that does not snap to pixels keeps the continuous geometry,
     * ending exactly at the content edge.
     */
    @Test
    public void unsnappedSegmentsEndAtTheContentEdge() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = control(true, false, 2.5);
            control.setSnapToPixel(false);
            Pane root = new Pane(control);
            stageFor(root).setRenderScaleX(1.5);
            layout(root, control, 300.3);
            List<Region> segments = segments(control);
            Region last = segments.get(segments.size() - 1);
            assertEquals(300.3 - control.snappedRightInset(), last.getLayoutX() + last.getWidth(), EPSILON,
                    "trailing edge");
        });
    }

    // ==================== Assertions ====================

    // Control.layoutChildren hands the skin snapSizeX(getWidth()) minus the snapped insets;
    // that content area is the range the segments must fill.
    private static void assertSegments(RXSegmentedControl<String> control, double spacing, double scale,
                                       String at) {
        double lo = control.snappedLeftInset();
        double hi = snapCeil(control.getWidth(), scale) - control.snappedRightInset();
        double gap = Math.round(spacing * scale) / scale;
        double unit = 1.0 / scale;
        List<Region> segments = segments(control);
        assertEquals(COUNT, segments.size(), at + ": segment count");
        double minWidth = Double.MAX_VALUE;
        double maxWidth = -Double.MAX_VALUE;
        for (int i = 0; i < segments.size(); i++) {
            Region segment = segments.get(i);
            assertOnGrid(segment.getLayoutX(), scale, at + ": x of segment " + i);
            assertOnGrid(segment.getLayoutX() + segment.getWidth(), scale, at + ": end of segment " + i);
            minWidth = Math.min(minWidth, segment.getWidth());
            maxWidth = Math.max(maxWidth, segment.getWidth());
            if (i > 0) {
                Region previous = segments.get(i - 1);
                assertEquals(gap, segment.getLayoutX() - (previous.getLayoutX() + previous.getWidth()), EPSILON,
                        at + ": gap before segment " + i);
            }
        }
        Region last = segments.get(segments.size() - 1);
        double end = last.getLayoutX() + last.getWidth();
        assertEquals(lo, segments.get(0).getLayoutX(), EPSILON, at + ": leading edge");
        assertEquals(hi, end, EPSILON, at + ": trailing edge");
        assertTrue(maxWidth - minWidth <= unit + EPSILON, at + ": segment widths differ by more than a pixel");

        // Node bounds are kept in float precision.
        Region selected = segments.get(2);
        Bounds indicator = indicator(control).getBoundsInParent();
        assertEquals(selected.getLayoutX(), indicator.getMinX(), 1.0e-4, at + ": indicator x");
        assertEquals(selected.getWidth(), indicator.getWidth(), 1.0e-4, at + ": indicator width");
    }

    private static void assertOnGrid(double value, double scale, String message) {
        double scaled = value * scale;
        assertEquals(Math.rint(scaled), scaled, 1.0e-6, message + " " + value + " is off the " + scale + "x grid");
    }

    private static double snapCeil(double value, double scale) {
        return Math.ceil(value * scale - 1.0e-14) / scale;
    }

    // ==================== Fixtures ====================

    private static RXSegmentedControl<String> control(boolean block, boolean equal, double spacing) {
        RXSegmentedControl<String> control = new RXSegmentedControl<>();
        for (int i = 0; i < COUNT; i++) {
            control.getItems().add(new RXSegmentedItem<>("v" + i, "Item " + i));
        }
        control.setBlock(block);
        control.setEqualSegmentWidth(equal);
        control.setSegmentSpacing(spacing);
        control.setAnimated(false);
        control.setPadding(new Insets(3.0, 7.5, 3.5, 4.25));
        control.selectIndex(2);
        control.setManaged(false);
        return control;
    }

    private static List<Region> segments(RXSegmentedControl<String> control) {
        List<Region> segments = new ArrayList<>();
        for (Node node : control.getChildrenUnmodifiable()) {
            if (node.getStyleClass().contains("segment")) {
                segments.add((Region) node);
            }
        }
        segments.sort(Comparator.comparingDouble(Region::getLayoutX));
        return segments;
    }

    private static Node indicator(RXSegmentedControl<String> control) {
        for (Node node : control.getChildrenUnmodifiable()) {
            if (node.getStyleClass().contains("indicator")) {
                return node;
            }
        }
        throw new AssertionError("no indicator");
    }

    // The stage is never shown: it only supplies the render scale the control snaps to.
    private static Stage stageFor(Pane root) {
        Stage stage = new Stage();
        stage.setScene(new Scene(root, 800.0, 200.0));
        return stage;
    }

    private static void layout(Pane root, Region control, double width) {
        control.resize(width, 40.0);
        root.applyCss();
        control.layout();
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
        if (!latch.await(60, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task timed out");
        }
        Throwable thrown = error.get();
        if (thrown instanceof AssertionError assertionError) {
            throw assertionError;
        }
        if (thrown != null) {
            throw new RuntimeException(thrown);
        }
    }
}
