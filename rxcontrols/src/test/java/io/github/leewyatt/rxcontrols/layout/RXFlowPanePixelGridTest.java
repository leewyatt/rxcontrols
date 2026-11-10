package io.github.leewyatt.rxcontrols.layout;

import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link RXFlowPane} block alignment against the device pixel grid of its window.
 */
public class RXFlowPanePixelGridTest {

    private static final double EPSILON = 1.0e-9;
    private static final double[][] SCALES = {{1.0, 1.0}, {1.25, 1.25}, {1.5, 1.5}, {1.75, 1.75}, {2.0, 2.0},
            {1.5, 1.25}, {1.25, 1.5}};
    private static final double[] FRACTIONS = {0.0, 0.25, 0.5, 0.75, 1.0 / 3.0, 2.0 / 3.0};

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
     * Verifies the content block stays inside the content area and on the pixel grid for
     * every block alignment along the width, in both orientations, at any render scale.
     */
    @Test
    public void blockStaysInsideAlongTheWidth() throws Exception {
        runOnFx(() -> {
            for (Orientation orientation : Orientation.values()) {
                for (Pos pos : new Pos[] {Pos.TOP_LEFT, Pos.TOP_CENTER, Pos.TOP_RIGHT}) {
                    RXFlowPane pane = paddedPane(orientation, pos);
                    pane.setRowHalignment(HPos.RIGHT);
                    Stage stage = stageFor(pane);
                    for (double[] scale : SCALES) {
                        stage.setRenderScaleX(scale[0]);
                        stage.setRenderScaleY(scale[1]);
                        for (int base = 300; base < 420; base++) {
                            for (double fraction : FRACTIONS) {
                                double width = base + fraction;
                                layout(pane, width, 400.0);
                                assertBlock(pane, true, pos.getHpos().ordinal(), scale[0],
                                        orientation + " " + pos + ", scale " + scale[0] + "/" + scale[1]
                                                + ", width " + width);
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies the content block stays inside the content area and on the pixel grid for
     * every block alignment along the height, in both orientations, at any render scale.
     */
    @Test
    public void blockStaysInsideAlongTheHeight() throws Exception {
        runOnFx(() -> {
            for (Orientation orientation : Orientation.values()) {
                for (Pos pos : new Pos[] {Pos.TOP_LEFT, Pos.CENTER_LEFT, Pos.BOTTOM_LEFT}) {
                    RXFlowPane pane = paddedPane(orientation, pos);
                    pane.setColumnValignment(VPos.BOTTOM);
                    Stage stage = stageFor(pane);
                    int edge = pos.getVpos() == VPos.TOP ? 0 : pos.getVpos() == VPos.CENTER ? 1 : 2;
                    for (double[] scale : SCALES) {
                        stage.setRenderScaleX(scale[0]);
                        stage.setRenderScaleY(scale[1]);
                        for (int base = 300; base < 420; base++) {
                            for (double fraction : FRACTIONS) {
                                double height = base + fraction;
                                layout(pane, 400.0, height);
                                assertBlock(pane, false, edge, scale[1],
                                        orientation + " " + pos + ", scale " + scale[0] + "/" + scale[1]
                                                + ", height " + height);
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies a centred block keeps its offset from the unfloored inside width.
     */
    @Test
    public void centredBlockKeepsTheUnflooredOffset() throws Exception {
        runOnFx(() -> {
            RXFlowPane pane = new RXFlowPane(Orientation.HORIZONTAL, 10.0, 6.0);
            pane.setPadding(new Insets(3.0, 7.5, 3.5, 4.25));
            pane.setAlignment(Pos.TOP_CENTER);
            pane.setAnimated(false);
            for (int i = 0; i < 3; i++) {
                pane.getChildren().add(box(60.0, 40.0));
            }
            stageFor(pane).setRenderScaleX(1.5);
            // The 200 px block in the 303 - 4 - 22/3 inside width sits 74.75 device pixels
            // from the origin and rounds to 75. The floored width would place it at exactly
            // 74.5, which still rounds to 75 in exact arithmetic; in doubles it lands just
            // below and rounds to 74. The sample tells the two apart only through that tie.
            layout(pane, 303.0, 400.0);
            assertEquals(50.0, pane.getChildren().get(0).getLayoutX(), EPSILON, "block x");
        });
    }

    /**
     * Verifies runs wrap at the unfloored inside width, even where the summed run length
     * carries floating-point noise above a grid value the floored width would hold.
     */
    @Test
    public void runsWrapAtTheUnflooredInsideWidth() throws Exception {
        runOnFx(() -> {
            RXFlowPane pane = new RXFlowPane(Orientation.HORIZONTAL, 10.0, 6.0);
            pane.setPadding(new Insets(3.0, 7.5, 3.5, 4.25));
            pane.setAnimated(false);
            Region first = box(37.3, 20.0);
            Region second = box(52.6, 20.0);
            Region third = box(44.4, 20.0);
            Region fourth = box(71.05, 20.0);
            RXFlowPane.setMargin(second, new Insets(0.0, 0.3, 0.0, 1.25));
            RXFlowPane.setMargin(fourth, new Insets(0.0, 2.6, 0.0, 0.7));
            pane.getChildren().addAll(first, second, third, fourth);
            stageFor(pane).setRenderScaleX(1.5);
            // At 1.5x the four areas and gaps sum to 361 device pixels, one ulp above the
            // double the floored inside width of this pane holds; the unfloored width fits them.
            layout(pane, 252.25, 400.0);
            for (Node child : pane.getChildren()) {
                assertEquals(first.getLayoutY(), child.getLayoutY(), EPSILON, "every box stays in the first run");
            }
        });
    }

    /**
     * Verifies a pane that does not snap to pixels keeps the continuous geometry, ending
     * exactly at the inside edge.
     */
    @Test
    public void unsnappedTrailingBlockEndsAtTheInsideEdge() throws Exception {
        runOnFx(() -> {
            RXFlowPane pane = paddedPane(Orientation.HORIZONTAL, Pos.BOTTOM_RIGHT);
            pane.setSnapToPixel(false);
            stageFor(pane).setRenderScaleX(1.5);
            layout(pane, 400.3, 300.3);
            double right = -Double.MAX_VALUE;
            double bottom = -Double.MAX_VALUE;
            for (Node child : pane.getChildren()) {
                Region region = (Region) child;
                right = Math.max(right, region.getLayoutX() + region.getWidth());
                bottom = Math.max(bottom, region.getLayoutY() + region.getHeight());
            }
            assertEquals(400.3 - 7.5, right, EPSILON, "right edge");
            assertEquals(300.3 - 3.5, bottom, EPSILON, "bottom edge");
        });
    }

    // ==================== Assertions ====================

    // edge: 0 leading, 1 centre, 2 trailing.
    private static void assertBlock(RXFlowPane pane, boolean alongX, int edge, double scale, String at) {
        double lo = alongX ? pane.snappedLeftInset() : pane.snappedTopInset();
        double hi = alongX
                ? pane.getWidth() - pane.snappedRightInset()
                : pane.getHeight() - pane.snappedBottomInset();
        double first = Double.MAX_VALUE;
        double last = -Double.MAX_VALUE;
        for (Node child : pane.getChildren()) {
            Region region = (Region) child;
            double start = alongX ? region.getLayoutX() : region.getLayoutY();
            double end = start + (alongX ? region.getWidth() : region.getHeight());
            assertOnGrid(start, scale, at + ": start");
            assertOnGrid(end, scale, at + ": end");
            first = Math.min(first, start);
            last = Math.max(last, end);
        }
        double unit = 1.0 / scale;
        assertTrue(first >= lo - EPSILON, at + ": first " + first + " < " + lo);
        assertTrue(last <= hi + EPSILON, at + ": last " + last + " > " + hi);
        switch (edge) {
            case 0 -> assertEquals(lo, first, EPSILON, at + ": leading edge");
            case 2 -> assertTrue(last > hi - unit - EPSILON, at + ": trailing edge " + last + " stops short of " + hi);
            default -> assertTrue(Math.abs((first - lo) - (hi - last)) <= unit + EPSILON, at + ": centred within a pixel");
        }
    }

    private static void assertOnGrid(double value, double scale, String message) {
        double scaled = value * scale;
        assertEquals(Math.rint(scaled), scaled, 1.0e-6, message + " " + value + " is off the " + scale + "x grid");
    }

    // ==================== Fixtures ====================

    // Five 60x40 boxes: one run or two along the main axis, depending on the extent.
    private static RXFlowPane paddedPane(Orientation orientation, Pos pos) {
        RXFlowPane pane = new RXFlowPane(orientation, 10.0, 6.0);
        pane.setPadding(new Insets(3.0, 7.5, 3.5, 4.25));
        pane.setAlignment(pos);
        pane.setAnimated(false);
        for (int i = 0; i < 5; i++) {
            pane.getChildren().add(box(60.0, 40.0));
        }
        return pane;
    }

    private static Region box(double width, double height) {
        Region region = new Region();
        region.setMinSize(width, height);
        region.setPrefSize(width, height);
        region.setMaxSize(width, height);
        return region;
    }

    // The stage is never shown: it only supplies the render scale the pane snaps to.
    private static Stage stageFor(Region root) {
        Stage stage = new Stage();
        stage.setScene(new Scene(root));
        return stage;
    }

    private static void layout(Region region, double width, double height) {
        region.resize(width, height);
        region.layout();
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
