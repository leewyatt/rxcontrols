package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXTabPane.ScrollButtonPolicy;
import io.github.leewyatt.rxcontrols.RXTabPane.Variant;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SCROLLABLE-variant behaviour for {@link RXTabPane}: scroll-button visibility per
 * {@link ScrollButtonPolicy}, bounded wheel/button scrolling, and
 * {@code ensureSelectedVisible} bringing an off-screen selection into the viewport.
 */
public class RXTabPaneScrollTest {

    private static final double EPSILON = 1.0;
    private static final PseudoClass LEFT = PseudoClass.getPseudoClass("left");
    private static final PseudoClass RIGHT = PseudoClass.getPseudoClass("right");

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

    // ==================== Scroll-button visibility ====================

    @Test
    public void autoPolicyShowsButtonsWhenOverflowing() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = scrollable(10, 220);
            assertTrue(leftButton(pane).isVisible());
            assertTrue(rightButton(pane).isVisible());
        });
    }

    @Test
    public void autoPolicyHidesButtonsWhenFitting() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = scrollable(2, 600);
            assertFalse(leftButton(pane).isVisible());
            assertFalse(rightButton(pane).isVisible());
        });
    }

    @Test
    public void alwaysPolicyShowsButtonsWithoutOverflow() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = scrollable(2, 600);
            pane.setScrollButtonPolicy(ScrollButtonPolicy.ALWAYS);
            layout(pane);
            assertTrue(leftButton(pane).isVisible());
            assertTrue(rightButton(pane).isVisible());
        });
    }

    @Test
    public void neverPolicyHidesButtonsDespiteOverflow() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = scrollable(10, 220);
            pane.setScrollButtonPolicy(ScrollButtonPolicy.NEVER);
            layout(pane);
            assertFalse(leftButton(pane).isVisible());
            assertFalse(rightButton(pane).isVisible());
        });
    }

    @Test
    public void standardVariantNeverShowsButtons() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = scrollable(10, 220);
            pane.setVariant(Variant.STANDARD);
            layout(pane);
            assertFalse(leftButton(pane).isVisible());
            assertFalse(rightButton(pane).isVisible());
        });
    }

    // ==================== Bounded scrolling ====================

    @Test
    public void wheelScrollsStripForward() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = scrollable(10, 220);
            // Animation off isolates the wheel-to-scroll wiring from the momentum timing
            // (the animated path is covered by wheelMomentumAdvancesStripAfterPulses).
            pane.setAnimated(false);
            double before = cellLayoutX(pane, 0);
            wheel(pane, -80);
            layout(pane);
            // Strip shifts left (first cell moves toward negative x).
            assertTrue(cellLayoutX(pane, 0) < before - EPSILON);
        });
    }

    @Test
    public void wheelCannotScrollBeforeStart() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = scrollable(10, 220);
            double before = cellLayoutX(pane, 0);
            // Wheel up at the start: already clamped at offset 0, no movement.
            wheel(pane, 120);
            layout(pane);
            assertEquals(before, cellLayoutX(pane, 0));
        });
    }

    @Test
    public void leftButtonDisabledAtStartRightEnabled() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = scrollable(10, 220);
            assertTrue(leftButton(pane).isDisable());
            assertFalse(rightButton(pane).isDisable());
        });
    }

    @Test
    public void scrollingEnablesLeftButton() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = scrollable(10, 220);
            pane.setAnimated(false);
            wheel(pane, -120);
            layout(pane);
            assertFalse(leftButton(pane).isDisable());
        });
    }

    @Test
    public void rightButtonClickAdvancesStrip() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = scrollable(10, 220);
            // Animation off isolates the button-to-scroll wiring from the glide timing
            // (the animated path is covered by rightButtonGlideAdvancesStripAfterPulses).
            pane.setAnimated(false);
            double before = cellLayoutX(pane, 0);
            rightButton(pane).fire();
            layout(pane);
            assertTrue(cellLayoutX(pane, 0) < before - EPSILON);
        });
    }

    // ==================== Animated scrolling (momentum wheel + button glide) ====================

    @Test
    public void wheelMomentumAdvancesStripAfterPulses() throws Exception {
        AtomicReference<RXTabPane> ref = new AtomicReference<>();
        AtomicReference<Double> beforeRef = new AtomicReference<>();
        runOnFx(() -> {
            RXTabPane pane = scrollable(10, 220);   // animated by default: momentum wheel
            double before = cellLayoutX(pane, 0);
            wheel(pane, -120);
            layout(pane);
            // Momentum is frame-driven, so nothing moves within the same event turn.
            assertEquals(before, cellLayoutX(pane, 0));
            beforeRef.set(before);
            ref.set(pane);
        });
        // Let the pulse clock advance the momentum animation.
        waitForFx(280.0);
        runOnFx(() -> {
            layout(ref.get());
            assertTrue(cellLayoutX(ref.get(), 0) < beforeRef.get() - EPSILON,
                    "momentum wheel advances the strip on later pulses");
        });
    }

    @Test
    public void rightButtonGlideAdvancesStripAfterPulses() throws Exception {
        AtomicReference<RXTabPane> ref = new AtomicReference<>();
        AtomicReference<Double> beforeRef = new AtomicReference<>();
        runOnFx(() -> {
            RXTabPane pane = scrollable(10, 220);   // animated by default: glide on click
            double before = cellLayoutX(pane, 0);
            rightButton(pane).fire();
            layout(pane);
            // The page glide is frame-driven, so nothing moves within the same event turn.
            assertEquals(before, cellLayoutX(pane, 0));
            beforeRef.set(before);
            ref.set(pane);
        });
        // Let the pulse clock run past the glide duration.
        waitForFx(320.0);
        runOnFx(() -> {
            layout(ref.get());
            assertTrue(cellLayoutX(ref.get(), 0) < beforeRef.get() - EPSILON,
                    "button glide advances the strip on later pulses");
        });
    }

    @Test
    public void disablingAnimationMidGlideHaltsStrip() throws Exception {
        AtomicReference<RXTabPane> ref = new AtomicReference<>();
        AtomicReference<Double> beforeRef = new AtomicReference<>();
        AtomicReference<Double> frozenRef = new AtomicReference<>();
        runOnFx(() -> {
            RXTabPane pane = scrollable(10, 220);   // animated by default: glide on click
            beforeRef.set(cellLayoutX(pane, 0));
            rightButton(pane).fire();               // start the glide
            ref.set(pane);
        });
        // Let the glide advance partway (default 250ms glide).
        waitForFx(100.0);
        runOnFx(() -> {
            RXTabPane pane = ref.get();
            layout(pane);
            assertTrue(cellLayoutX(pane, 0) < beforeRef.get() - EPSILON,
                    "glide should be in flight before animation is disabled");
            // Disabling animation mid-glide must halt the engine, not let it coast.
            pane.setAnimated(false);
            layout(pane);
            frozenRef.set(cellLayoutX(pane, 0));
        });
        // Wait well past the remaining glide duration.
        waitForFx(320.0);
        runOnFx(() -> {
            layout(ref.get());
            // Frozen: later pulses do not move the strip once animation was switched off.
            assertEquals(frozenRef.get(), cellLayoutX(ref.get(), 0));
        });
    }

    // ==================== ensureSelectedVisible ====================

    @Test
    public void selectingLastTabScrollsItIntoView() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = scrollable(10, 220);
            int last = pane.getTabs().size() - 1;
            double rightEdgeBefore = cellRightEdge(pane, last);
            // Off-screen before selection.
            assertTrue(rightEdgeBefore > pane.getWidth() + EPSILON);

            pane.getSelectionModel().select(last);
            layout(pane);
            // Now within the header (viewport right sits just inside the pane width).
            assertTrue(cellRightEdge(pane, last) <= pane.getWidth() + EPSILON);
            // Fully scrolled: the right button is at its travel limit.
            assertTrue(rightButton(pane).isDisable());
        });
    }

    @Test
    public void switchingToScrollableRevealsSelection() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = scrollable(10, 220);
            pane.setVariant(Variant.STANDARD);
            layout(pane);
            int last = pane.getTabs().size() - 1;
            pane.getSelectionModel().select(last);
            layout(pane);

            // Switching (back) to SCROLLABLE must scroll the selected tab into view.
            pane.setVariant(Variant.SCROLLABLE);
            layout(pane);
            assertTrue(cellRightEdge(pane, last) <= pane.getWidth() + EPSILON);
        });
    }

    @Test
    public void leavingScrollableResetsOffset() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = scrollable(10, 220);
            // Animation off so the wheel shifts the strip synchronously for this assertion.
            pane.setAnimated(false);
            wheel(pane, -160);
            layout(pane);
            assertTrue(cellLayoutX(pane, 0) < 0);

            pane.setVariant(Variant.STANDARD);
            layout(pane);
            // STANDARD re-anchors the strip at the start (offset dropped).
            assertTrue(cellLayoutX(pane, 0) >= -EPSILON);
        });
    }

    // ==================== Helpers ====================

    private static void assertEquals(double expected, double actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, EPSILON);
    }

    private static RXTabPane scrollable(int tabCount, double width) {
        List<RXTab> tabs = new ArrayList<>();
        for (int i = 0; i < tabCount; i++) {
            tabs.add(new RXTab("Tab " + (i + 1)));
        }
        RXTabPane pane = new RXTabPane(tabs.toArray(new RXTab[0]));
        pane.setVariant(Variant.SCROLLABLE);
        pane.setPrefWidth(width);
        pane.setMaxWidth(width);
        StackPane root = new StackPane(pane);
        new Scene(root, Math.max(width, 640), 400);
        root.applyCss();
        root.layout();
        return pane;
    }

    private static void layout(RXTabPane pane) {
        pane.getParent().applyCss();
        pane.getParent().layout();
    }

    private static Node cell(RXTabPane pane, int index) {
        return (Node) pane.queryAccessibleAttribute(
                javafx.scene.AccessibleAttribute.ITEM_AT_INDEX, index);
    }

    private static double cellLayoutX(RXTabPane pane, int index) {
        return cell(pane, index).getLayoutX();
    }

    private static double cellRightEdge(RXTabPane pane, int index) {
        Node c = cell(pane, index);
        return c.getLayoutX() + c.getLayoutBounds().getWidth();
    }

    private static RXButton leftButton(RXTabPane pane) {
        return scrollButton(pane, LEFT);
    }

    private static RXButton rightButton(RXTabPane pane) {
        return scrollButton(pane, RIGHT);
    }

    private static RXButton scrollButton(RXTabPane pane, PseudoClass side) {
        for (Node n : pane.lookupAll(".scroll-button")) {
            if (n instanceof RXButton && n.getPseudoClassStates().contains(side)) {
                return (RXButton) n;
            }
        }
        assertNotNull(null, "scroll button not found: " + side.getPseudoClassName());
        return null;
    }

    private static void wheel(RXTabPane pane, double deltaY) {
        // Fire on a cell so the event bubbles up to the header's SCROLL handler,
        // mirroring a real wheel gesture landing on a tab.
        Node target = cell(pane, 0);
        target.fireEvent(new ScrollEvent(ScrollEvent.SCROLL,
                0, 0, 0, 0,
                false, false, false, false,
                false, false,
                0, deltaY, 0, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                0, new PickResult(target, 0, 0)));
    }

    /** Runs the JavaFX pulse clock for {@code millis} so frame-driven animations advance. */
    private static void waitForFx(double millis) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            PauseTransition pause = new PauseTransition(Duration.millis(millis));
            pause.setOnFinished(event -> latch.countDown());
            pause.play();
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting for JavaFX pulses");
        }
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
