package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Four-way {@link Side} geometry for {@link RXTabPane} (P6): header placement per
 * side, vertical cell stacking, the indicator orientation flip (thin vertical bar
 * on LEFT/RIGHT), vertical keyboard remapping (Up/Down), and vertical scrolling.
 */
public class RXTabPaneVerticalTest {

    private static final double EPSILON = 1.0;

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    // ==================== Side pseudo-classes ====================

    @Test
    public void sideDrivesPseudoClass() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"));
            pane.setSide(Side.LEFT);
            laidOut(pane);
            assertTrue(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("left")));

            pane.setSide(Side.RIGHT);
            pane.getParent().layout();
            assertTrue(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("right")));
        });
    }

    // ==================== Header placement ====================

    @Test
    public void headerSitsOnTheChosenSide() throws Exception {
        runOnFx(() -> {
            assertTrue(sceneMinY(cellOf(Side.TOP)) < 200.0);
            assertTrue(sceneMinY(cellOf(Side.BOTTOM)) > 200.0);
            assertTrue(sceneMinX(cellOf(Side.LEFT)) < 320.0);
            assertTrue(sceneMinX(cellOf(Side.RIGHT)) > 320.0);
        });
    }

    @Test
    public void verticalSideStacksCellsInAColumn() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"), tab("C"));
            pane.setSide(Side.LEFT);
            laidOut(pane);
            Node c0 = cellAt(pane, 0);
            Node c1 = cellAt(pane, 1);
            // Same column (equal X), stacked downward (increasing Y).
            assertEquals(c0.getLayoutX(), c1.getLayoutX(), EPSILON);
            assertTrue(c1.getLayoutY() > c0.getLayoutY() + EPSILON);
        });
    }

    // ==================== Indicator orientation ====================

    @Test
    public void topSideIndicatorIsHorizontalBar() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"), tab("C"));
            pane.setAnimated(false);
            laidOut(pane);
            Region indicator = indicatorOf(pane);
            // Horizontal underline: wide and thin.
            assertTrue(indicator.getWidth() > indicator.getHeight());
        });
    }

    @Test
    public void leftSideIndicatorIsVerticalBar() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"), tab("C"));
            pane.setSide(Side.LEFT);
            pane.setAnimated(false);
            laidOut(pane);
            Region indicator = indicatorOf(pane);
            // Vertical bar on the inner edge: tall and thin.
            assertTrue(indicator.getHeight() > indicator.getWidth());
        });
    }

    // ==================== Vertical keyboard ====================

    @Test
    public void verticalDownArrowSelectsNext() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"), tab("C"));
            pane.setSide(Side.LEFT);
            laidOut(pane);
            pane.getSelectionModel().select(0);
            press(pane, KeyCode.DOWN);
            assertEquals(1, pane.getSelectedIndex());
        });
    }

    @Test
    public void verticalUpArrowSelectsPrevious() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"), tab("C"));
            pane.setSide(Side.RIGHT);
            laidOut(pane);
            pane.getSelectionModel().select(2);
            press(pane, KeyCode.UP);
            assertEquals(1, pane.getSelectedIndex());
        });
    }

    @Test
    public void verticalIgnoresHorizontalArrows() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"), tab("C"));
            pane.setSide(Side.LEFT);
            laidOut(pane);
            pane.getSelectionModel().select(1);
            press(pane, KeyCode.RIGHT);
            press(pane, KeyCode.LEFT);
            assertEquals(1, pane.getSelectedIndex());
        });
    }

    // ==================== Wrapped label ====================

    @Test
    public void longLabelWrapsToTallerHeaderWhenWidthCapped() throws Exception {
        runOnFx(() -> {
            RXTabPane wide = new RXTabPane(new RXTab("A very long tab label that must wrap"));
            laidOut(wide);
            double singleLine = cellAt(wide, 0).getLayoutBounds().getHeight();

            RXTabPane capped = new RXTabPane(new RXTab("A very long tab label that must wrap"));
            capped.setTabMaxWidth(90.0);
            laidOut(capped);
            double wrapped = cellAt(capped, 0).getLayoutBounds().getHeight();

            // The width cap forces the label onto multiple lines, so the cell (and the
            // header it sizes) grows taller than the single-line case.
            assertTrue(wrapped > singleLine + EPSILON,
                    "expected wrapped height " + wrapped + " > single-line " + singleLine);
        });
    }

    // ==================== Vertical scrolling ====================

    @Test
    public void verticalWheelScrollsStrip() throws Exception {
        runOnFx(() -> {
            RXTab[] tabs = new RXTab[12];
            for (int i = 0; i < tabs.length; i++) {
                tabs[i] = new RXTab("Tab " + (i + 1));
            }
            RXTabPane pane = new RXTabPane(tabs);
            pane.setSide(Side.LEFT);
            pane.setVariant(RXTabPane.Variant.SCROLLABLE);
            pane.setPrefHeight(200.0);
            pane.setMaxHeight(200.0);
            StackPane root = new StackPane(pane);
            new Scene(root, 640, 400);
            root.applyCss();
            root.layout();

            double before = cellAt(pane, 0).getLayoutY();
            wheel(cellAt(pane, 0), -120);
            root.layout();
            // Strip shifts up (first cell moves toward negative Y).
            assertTrue(cellAt(pane, 0).getLayoutY() < before - EPSILON);
        });
    }

    // ==================== Helpers ====================

    private static Node cellOf(Side side) {
        RXTabPane pane = new RXTabPane(tab("A"), tab("B"));
        pane.setSide(side);
        laidOut(pane);
        return cellAt(pane, 0);
    }

    private static Region indicatorOf(RXTabPane pane) {
        Region indicator = (Region) pane.lookup(".indicator");
        assertNotNull(indicator, "indicator not found");
        return indicator;
    }

    private static Node cellAt(RXTabPane pane, int index) {
        return (Node) pane.queryAccessibleAttribute(
                javafx.scene.AccessibleAttribute.ITEM_AT_INDEX, index);
    }

    private static double sceneMinX(Node node) {
        return node.localToScene(node.getBoundsInLocal()).getMinX();
    }

    private static double sceneMinY(Node node) {
        return node.localToScene(node.getBoundsInLocal()).getMinY();
    }

    private static void press(RXTabPane pane, KeyCode code) {
        pane.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false));
        pane.layout();
    }

    private static void wheel(Node target, double deltaY) {
        target.fireEvent(new ScrollEvent(ScrollEvent.SCROLL,
                0, 0, 0, 0,
                false, false, false, false,
                false, false,
                0, deltaY, 0, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                0, new PickResult(target, 0, 0)));
    }

    private static RXTabPane laidOut(RXTabPane pane) {
        StackPane root = new StackPane(pane);
        new Scene(root, 640, 400);
        root.applyCss();
        root.layout();
        return pane;
    }

    private static RXTab tab(String text) {
        return new RXTab(text);
    }

    private static void runOnFx(FxAction action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable error = failure.get();
        if (error instanceof Exception) {
            throw (Exception) error;
        }
        if (error != null) {
            throw new AssertionError(error);
        }
    }

    @FunctionalInterface
    private interface FxAction {
        void run() throws Exception;
    }
}
