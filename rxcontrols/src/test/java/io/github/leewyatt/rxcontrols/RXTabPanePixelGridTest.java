package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.geometry.Side;
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

/**
 * Tests {@link RXTabPane} FULL_WIDTH tab geometry against the device pixel grid of its
 * window.
 */
public class RXTabPanePixelGridTest {

    private static final double EPSILON = 1.0e-9;
    // X and Y scales differ in the last two pairs so a vertical strip snapped with the X
    // scale would be caught.
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
     * Verifies FULL_WIDTH tabs meet edge to edge on the pixel grid and fill the header
     * strip floored to the grid, on every side, for several tab counts, at any scale.
     */
    @Test
    public void fullWidthTabsFillTheStripOnTheGrid() throws Exception {
        runOnFx(() -> {
            for (Side side : Side.values()) {
                boolean vertical = side.isVertical();
                for (int count = 2; count <= 7; count++) {
                    for (double[] scales : SCALES) {
                        double scale = vertical ? scales[1] : scales[0];
                        RXTabPane tabPane = tabPane(side, count);
                        Pane root = new Pane(tabPane);
                        Stage stage = stageFor(root);
                        stage.setRenderScaleX(scales[0]);
                        stage.setRenderScaleY(scales[1]);
                        for (int base = 150; base < 260; base++) {
                            for (double fraction : FRACTIONS) {
                                double extent = base + fraction;
                                if (vertical) {
                                    tabPane.resize(300.0, extent);
                                } else {
                                    tabPane.resize(extent, 300.0);
                                }
                                root.applyCss();
                                tabPane.layout();
                                assertStrip(tabPane, vertical, scale, side + ", " + count + " tabs, scale " + scales[0] + "/" + scales[1]
                                        + ", extent " + extent);
                            }
                        }
                    }
                }
            }
        });
    }

    private static void assertStrip(RXTabPane tabPane, boolean vertical, double scale, String at) {
        Region header = (Region) tabPane.lookup(".header");
        header.layout();
        double start = vertical ? header.snappedTopInset() : header.snappedLeftInset();
        double extent = vertical
                ? header.getHeight() - header.snappedTopInset() - header.snappedBottomInset()
                : header.getWidth() - header.snappedLeftInset() - header.snappedRightInset();
        double end = start + Math.floor(extent * scale + 1.0e-9) / scale;
        List<Region> tabs = new ArrayList<>();
        for (Node node : header.getChildrenUnmodifiable()) {
            if (node.getStyleClass().contains("tab")) {
                tabs.add((Region) node);
            }
        }
        tabs.sort(Comparator.comparingDouble(tab -> vertical ? tab.getLayoutY() : tab.getLayoutX()));
        double cursor = start;
        for (int i = 0; i < tabs.size(); i++) {
            Region tab = tabs.get(i);
            double position = vertical ? tab.getLayoutY() : tab.getLayoutX();
            double size = vertical ? tab.getHeight() : tab.getWidth();
            assertEquals(cursor, position, EPSILON, at + ": tab " + i + " start");
            assertEquals(Math.rint((position + size) * scale), (position + size) * scale, 1.0e-6,
                    at + ": tab " + i + " ends off the grid");
            cursor = position + size;
        }
        assertEquals(end, cursor, EPSILON, at + ": last tab end");
    }

    private static RXTabPane tabPane(Side side, int count) {
        RXTabPane tabPane = new RXTabPane();
        for (int i = 0; i < count; i++) {
            tabPane.getTabs().add(new RXTab("Tab " + i));
        }
        tabPane.setVariant(RXTabPane.Variant.FULL_WIDTH);
        tabPane.setSide(side);
        tabPane.setManaged(false);
        return tabPane;
    }

    // The stage is never shown: it only supplies the render scale the tab pane snaps to.
    private static Stage stageFor(Pane root) {
        Stage stage = new Stage();
        stage.setScene(new Scene(root, 800.0, 800.0));
        return stage;
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
        if (!latch.await(120, TimeUnit.SECONDS)) {
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
