package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P7 content behaviour for {@link RXTabPane}: the detach vs {@code preserveContent}
 * attach model and its pref-size consequences, {@code dynamicHeight}, the
 * {@code contentAnimation} gate (animate vs direct cut), {@code wheelScrollEnabled},
 * and {@code tabAlignment} placement.
 */
public class RXTabPaneContentTest {

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

    // ==================== Detach (default) attach model ====================

    @Test
    public void defaultDetachAttachesOnlySelectedContent() throws Exception {
        runOnFx(() -> {
            RXTab t0 = tab("A", 120, 40);
            RXTab t1 = tab("B", 300, 160);
            RXTabPane pane = new RXTabPane(t0, t1);
            laidOut(pane);
            StackPane region = contentRegionOf(pane);
            assertEquals(1, region.getChildrenUnmodifiable().size());
            assertSame(t0.getContent(), region.getChildrenUnmodifiable().get(0));
        });
    }

    @Test
    public void detachSwapsChildOnSelection() throws Exception {
        runOnFx(() -> {
            RXTab t0 = tab("A", 120, 40);
            RXTab t1 = tab("B", 300, 160);
            RXTabPane pane = new RXTabPane(t0, t1);
            laidOut(pane);
            pane.getSelectionModel().select(1);
            StackPane region = contentRegionOf(pane);
            assertEquals(1, region.getChildrenUnmodifiable().size());
            assertSame(t1.getContent(), region.getChildrenUnmodifiable().get(0));
        });
    }

    // ==================== Preserve attach model ====================

    @Test
    public void preserveContentAttachesAllContent() throws Exception {
        runOnFx(() -> {
            RXTab t0 = tab("A", 120, 40);
            RXTab t1 = tab("B", 300, 160);
            RXTab t2 = tab("C", 200, 90);
            RXTabPane pane = new RXTabPane(t0, t1, t2);
            pane.setPreserveContent(true);
            laidOut(pane);
            StackPane region = contentRegionOf(pane);
            assertEquals(3, region.getChildrenUnmodifiable().size());
            assertTrue(region.getChildrenUnmodifiable().contains(t0.getContent()));
            assertTrue(region.getChildrenUnmodifiable().contains(t1.getContent()));
            assertTrue(region.getChildrenUnmodifiable().contains(t2.getContent()));
        });
    }

    @Test
    public void preserveContentHidesNonSelected() throws Exception {
        runOnFx(() -> {
            RXTab t0 = tab("A", 120, 40);
            RXTab t1 = tab("B", 300, 160);
            RXTabPane pane = new RXTabPane(t0, t1);
            pane.setPreserveContent(true);
            laidOut(pane);
            // Selected page shows; the other stays attached but unmanaged + hidden.
            assertTrue(t0.getContent().isVisible());
            assertFalse(t1.getContent().isVisible());
            assertFalse(t1.getContent().isManaged());
        });
    }

    @Test
    public void togglingPreserveDetachesNonSelected() throws Exception {
        runOnFx(() -> {
            RXTab t0 = tab("A", 120, 40);
            RXTab t1 = tab("B", 300, 160);
            RXTabPane pane = new RXTabPane(t0, t1);
            pane.setPreserveContent(true);
            laidOut(pane);
            assertEquals(2, contentRegionOf(pane).getChildrenUnmodifiable().size());

            pane.setPreserveContent(false);
            pane.getParent().layout();
            StackPane region = contentRegionOf(pane);
            assertEquals(1, region.getChildrenUnmodifiable().size());
            assertSame(t0.getContent(), region.getChildrenUnmodifiable().get(0));
        });
    }

    // ==================== Pref sizing ====================

    @Test
    public void detachPrefFollowsSelectedContent() throws Exception {
        runOnFx(() -> {
            RXTab t0 = tab("A", 120, 40);
            RXTab t1 = tab("B", 300, 200);
            RXTabPane pane = new RXTabPane(t0, t1);
            laidOut(pane);
            pane.getSelectionModel().select(0);
            double small = pane.prefHeight(-1);
            pane.getSelectionModel().select(1);
            double large = pane.prefHeight(-1);
            // The pane tracks the selected page: switching to the taller tab grows it.
            assertTrue(large > small + EPSILON,
                    "expected taller selection " + large + " > " + small);
        });
    }

    @Test
    public void preservePrefIsMaxOfAllAndStable() throws Exception {
        runOnFx(() -> {
            RXTab t0 = tab("A", 120, 40);
            RXTab t1 = tab("B", 300, 200);
            RXTabPane pane = new RXTabPane(t0, t1);
            pane.setPreserveContent(true);
            laidOut(pane);
            pane.getSelectionModel().select(0);
            double onSmall = pane.prefHeight(-1);
            pane.getSelectionModel().select(1);
            double onLarge = pane.prefHeight(-1);
            // Locked to max-of-all: the pref height does not jump between tabs.
            assertEquals(onSmall, onLarge, EPSILON);
        });
    }

    @Test
    public void dynamicHeightTrueFollowsSelectedFalseLocksToMax() throws Exception {
        runOnFx(() -> {
            RXTab t0 = tab("A", 120, 40);
            RXTab t1 = tab("B", 300, 200);
            RXTabPane pane = new RXTabPane(t0, t1);
            pane.setPreserveContent(true);
            laidOut(pane);
            pane.getSelectionModel().select(0);

            pane.setDynamicHeight(false);
            double locked = pane.prefHeight(-1);
            pane.setDynamicHeight(true);
            double dynamic = pane.prefHeight(-1);
            // false locks to the tallest tab; true shrinks to the (short) selected one.
            assertTrue(locked > dynamic + EPSILON,
                    "expected locked " + locked + " > dynamic " + dynamic);
        });
    }

    // ==================== Content animation gate ====================

    @Test
    public void nullContentAnimationSwapsImmediately() throws Exception {
        runOnFx(() -> {
            RXTab t0 = tab("A", 120, 40);
            RXTab t1 = tab("B", 300, 160);
            RXTabPane pane = new RXTabPane(t0, t1);
            laidOut(pane);
            pane.getSelectionModel().select(1);
            // No animation configured: the old page is gone at once.
            assertEquals(1, contentRegionOf(pane).getChildrenUnmodifiable().size());
        });
    }

    @Test
    public void contentAnimationKeepsBothPagesDuringTransition() throws Exception {
        runOnFx(() -> {
            RXTab t0 = tab("A", 120, 40);
            RXTab t1 = tab("B", 300, 160);
            RXTabPane pane = new RXTabPane(t0, t1);
            pane.setContentAnimation(new AnimFade());
            laidOut(pane);
            pane.getSelectionModel().select(1);
            // The tween needs both pages on stage simultaneously.
            StackPane region = contentRegionOf(pane);
            assertEquals(2, region.getChildrenUnmodifiable().size());
            assertTrue(region.getChildrenUnmodifiable().contains(t0.getContent()));
            assertTrue(region.getChildrenUnmodifiable().contains(t1.getContent()));
        });
    }

    @Test
    public void zeroDurationContentAnimationDirectCuts() throws Exception {
        runOnFx(() -> {
            RXTab t0 = tab("A", 120, 40);
            RXTab t1 = tab("B", 300, 160);
            RXTabPane pane = new RXTabPane(t0, t1);
            pane.setContentAnimation(new AnimFade());
            pane.setAnimationDuration(Duration.ZERO);
            laidOut(pane);
            pane.getSelectionModel().select(1);
            // A non-positive duration gates the animation off: direct cut.
            assertEquals(1, contentRegionOf(pane).getChildrenUnmodifiable().size());
        });
    }

    @Test
    public void disabledAnimationDirectCuts() throws Exception {
        runOnFx(() -> {
            RXTab t0 = tab("A", 120, 40);
            RXTab t1 = tab("B", 300, 160);
            RXTabPane pane = new RXTabPane(t0, t1);
            pane.setContentAnimation(new AnimFade());
            pane.setAnimated(false);
            laidOut(pane);
            pane.getSelectionModel().select(1);
            assertEquals(1, contentRegionOf(pane).getChildrenUnmodifiable().size());
        });
    }

    // ==================== Wheel scroll enablement ====================

    @Test
    public void wheelScrollDisabledIgnoresScroll() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = scrollablePane();
            pane.setWheelScrollEnabled(false);
            layoutConstrained(pane);
            double before = cellAt(pane, 0).getLayoutX();
            wheel(cellAt(pane, 0), -120);
            pane.getParent().layout();
            assertEquals(before, cellAt(pane, 0).getLayoutX(), EPSILON);
        });
    }

    @Test
    public void wheelScrollEnabledScrollsStrip() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = scrollablePane();
            layoutConstrained(pane);
            double before = cellAt(pane, 0).getLayoutX();
            wheel(cellAt(pane, 0), -120);
            pane.getParent().layout();
            // Default wheelScrollEnabled=true: the strip shifts left.
            assertTrue(cellAt(pane, 0).getLayoutX() < before - EPSILON);
        });
    }

    // ==================== Tab alignment ====================

    @Test
    public void alignmentShiftsStandardStrip() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"));
            laidOut(pane);
            pane.setTabAlignment(RXTabPane.TabAlignment.START);
            pane.getParent().layout();
            double startX = cellAt(pane, 0).getLayoutX();
            pane.setTabAlignment(RXTabPane.TabAlignment.CENTER);
            pane.getParent().layout();
            double centerX = cellAt(pane, 0).getLayoutX();
            pane.setTabAlignment(RXTabPane.TabAlignment.END);
            pane.getParent().layout();
            double endX = cellAt(pane, 0).getLayoutX();
            assertTrue(startX < centerX - EPSILON, "center should sit right of start");
            assertTrue(centerX < endX - EPSILON, "end should sit right of center");
        });
    }

    @Test
    public void alignmentIgnoredWhenScrollable() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"));
            pane.setVariant(RXTabPane.Variant.SCROLLABLE);
            laidOut(pane);
            pane.setTabAlignment(RXTabPane.TabAlignment.START);
            pane.getParent().layout();
            double startX = cellAt(pane, 0).getLayoutX();
            pane.setTabAlignment(RXTabPane.TabAlignment.CENTER);
            pane.getParent().layout();
            double centerX = cellAt(pane, 0).getLayoutX();
            // SCROLLABLE anchors the strip at the viewport start regardless of alignment.
            assertEquals(startX, centerX, EPSILON);
        });
    }

    // ==================== Review-regression guards ====================

    @Test
    public void tabMaxWidthCapsHeaderPrefWidth() throws Exception {
        runOnFx(() -> {
            RXTabPane natural = new RXTabPane(
                    tab("A very long tab label number one"),
                    tab("A very long tab label number two"),
                    tab("A very long tab label number three"));
            laidOut(natural);
            double naturalWidth = natural.prefWidth(-1);

            RXTabPane capped = new RXTabPane(
                    tab("A very long tab label number one"),
                    tab("A very long tab label number two"),
                    tab("A very long tab label number three"));
            capped.setTabMaxWidth(80.0);
            laidOut(capped);
            double cappedWidth = capped.prefWidth(-1);

            // computeHeaderPrimary must clamp to tabMaxWidth like the layout does, so
            // the pref width tracks the capped strip (3x80 + insets), not the natural sum.
            assertTrue(cappedWidth < naturalWidth - EPSILON,
                    "capped pref " + cappedWidth + " should be < natural " + naturalWidth);
            assertTrue(cappedWidth <= 3 * 80.0 + 60.0,
                    "capped pref " + cappedWidth + " should be bounded by the cap");
        });
    }

    @Test
    public void preserveContentRestoresRemovedTabContentToNeutral() throws Exception {
        runOnFx(() -> {
            RXTab t0 = tab("A", 120, 40);
            RXTab t1 = tab("B", 300, 160);
            RXTabPane pane = new RXTabPane(t0, t1);
            pane.setPreserveContent(true);
            laidOut(pane);
            Node c1 = t1.getContent();
            // Preserved-but-not-selected: hidden and unmanaged while attached.
            assertFalse(c1.isVisible());
            assertFalse(c1.isManaged());

            pane.getTabs().remove(t1);
            // Detached and handed back neutral, not stuck hidden/unmanaged.
            assertFalse(contentRegionOf(pane).getChildrenUnmodifiable().contains(c1));
            assertTrue(c1.isVisible(), "removed preserved content should be restored visible");
            assertTrue(c1.isManaged(), "removed preserved content should be restored managed");
        });
    }

    @Test
    public void clearingSelectionMidTransitionDoesNotResurrectPage() throws Exception {
        RXTab t0 = tab("A", 120, 40);
        RXTab t1 = tab("B", 300, 160);
        AtomicReference<RXTabPane> ref = new AtomicReference<>();
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(t0, t1);
            pane.setContentAnimation(new AnimSlide());
            pane.setAnimationDuration(Duration.millis(40.0));
            laidOut(pane);
            pane.getSelectionModel().select(1);
            assertEquals(2, contentRegionOf(pane).getChildrenUnmodifiable().size());
            // Clearing selection mid-transition must cancel the tween, not just detach
            // the pages — otherwise its deferred settle re-shows a page under no selection.
            pane.getSelectionModel().clearSelection();
            assertEquals(0, contentRegionOf(pane).getChildrenUnmodifiable().size());
            ref.set(pane);
        });
        // Wait well past the 40ms animation so any un-cancelled settle would have fired.
        Thread.sleep(220);
        runOnFx(() -> {
            RXTabPane pane = ref.get();
            assertEquals(-1, pane.getSelectedIndex());
            assertEquals(0, contentRegionOf(pane).getChildrenUnmodifiable().size(),
                    "a cancelled transition must not re-attach a page after clearSelection");
        });
    }

    @Test
    public void animateThenAnimateHandsBackStalePageNeutral() throws Exception {
        runOnFx(() -> {
            RXTab t0 = tab("A", 120, 40);
            RXTab t1 = tab("B", 300, 160);
            RXTab t2 = tab("C", 200, 90);
            RXTabPane pane = new RXTabPane(t0, t1, t2);
            pane.setContentAnimation(new AnimSlide());
            laidOut(pane);
            pane.getSelectionModel().select(1);   // A->B animating: [A, B] attached
            pane.getSelectionModel().select(2);   // B->C: A is interrupted + detached
            StackPane region = contentRegionOf(pane);
            // The two tween pages survive; the stale first page is detached...
            assertTrue(region.getChildrenUnmodifiable().contains(t1.getContent()));
            assertTrue(region.getChildrenUnmodifiable().contains(t2.getContent()));
            assertFalse(region.getChildrenUnmodifiable().contains(t0.getContent()));
            // ...and handed back neutral, not left invisible by the interrupted tween.
            assertTrue(t0.getContent().isVisible());
        });
    }

    @Test
    public void detachingContentClearsResidualScaleAndRotate() throws Exception {
        runOnFx(() -> {
            RXTab t0 = tab("A", 120, 40);
            RXTab t1 = tab("B", 300, 160);
            RXTabPane pane = new RXTabPane(t0, t1);
            pane.setPreserveContent(true);
            laidOut(pane);
            Node c1 = t1.getContent();
            // Simulate a transform left behind by an interrupted Zoom/Flip transition.
            c1.setScaleX(2.0);
            c1.setScaleY(0.5);
            c1.setRotate(45.0);
            pane.getTabs().remove(t1);
            // resetPageState must clear scale/rotate, not only translate/opacity/visibility.
            assertEquals(1.0, c1.getScaleX(), 0.001);
            assertEquals(1.0, c1.getScaleY(), 0.001);
            assertEquals(0.0, c1.getRotate(), 0.001);
        });
    }

    // ==================== Helpers ====================

    private static StackPane contentRegionOf(RXTabPane pane) {
        StackPane region = (StackPane) pane.lookup(".content");
        assertNotNull(region, "content region not found");
        return region;
    }

    private static Node cellAt(RXTabPane pane, int index) {
        return (Node) pane.queryAccessibleAttribute(
                javafx.scene.AccessibleAttribute.ITEM_AT_INDEX, index);
    }

    private static RXTabPane scrollablePane() {
        RXTab[] tabs = new RXTab[12];
        for (int i = 0; i < tabs.length; i++) {
            tabs[i] = new RXTab("Tab " + (i + 1));
        }
        RXTabPane pane = new RXTabPane(tabs);
        pane.setVariant(RXTabPane.Variant.SCROLLABLE);
        return pane;
    }

    private static void layoutConstrained(RXTabPane pane) {
        pane.setPrefWidth(200.0);
        pane.setMaxWidth(200.0);
        StackPane root = new StackPane(pane);
        new Scene(root, 640, 400);
        root.applyCss();
        root.layout();
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

    private static RXTab tab(String text, double prefW, double prefH) {
        Region content = new Region();
        content.setPrefSize(prefW, prefH);
        RXTab tab = new RXTab(text);
        tab.setContent(content);
        return tab;
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
