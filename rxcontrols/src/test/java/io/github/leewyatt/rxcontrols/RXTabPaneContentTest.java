package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimZoom;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
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
            List<Node> contents = attachedContents(pane);
            assertEquals(1, contents.size());
            assertSame(t0.getContent(), contents.get(0));
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
            List<Node> contents = attachedContents(pane);
            assertEquals(1, contents.size());
            assertSame(t1.getContent(), contents.get(0));
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
            List<Node> contents = attachedContents(pane);
            assertEquals(3, contents.size());
            assertTrue(contents.contains(t0.getContent()));
            assertTrue(contents.contains(t1.getContent()));
            assertTrue(contents.contains(t2.getContent()));
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
            // The selected page wrapper shows; the other stays attached but unmanaged +
            // hidden. Visibility is toggled on the wrapper, never on the user content.
            assertTrue(pageOf(pane, t0.getContent()).isVisible());
            assertFalse(pageOf(pane, t1.getContent()).isVisible());
            assertFalse(pageOf(pane, t1.getContent()).isManaged());
            // The user content nodes themselves are left untouched inside their wrappers.
            assertTrue(t0.getContent().isVisible());
            assertTrue(t1.getContent().isVisible());
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
            assertEquals(2, attachedContents(pane).size());

            pane.setPreserveContent(false);
            pane.getParent().layout();
            List<Node> contents = attachedContents(pane);
            assertEquals(1, contents.size());
            assertSame(t0.getContent(), contents.get(0));
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
            assertEquals(1, attachedContents(pane).size());
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
            List<Node> contents = attachedContents(pane);
            assertEquals(2, contents.size());
            assertTrue(contents.contains(t0.getContent()));
            assertTrue(contents.contains(t1.getContent()));
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
            assertEquals(1, attachedContents(pane).size());
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
            assertEquals(1, attachedContents(pane).size());
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
            // Animation off: assert the wheel-to-scroll wiring synchronously (the animated
            // momentum path is covered in RXTabPaneScrollTest).
            pane.setAnimated(false);
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
            // Preserved-but-not-selected: the page wrapper is hidden + unmanaged while
            // attached, but the user content inside it is never touched.
            assertFalse(pageOf(pane, c1).isVisible());
            assertFalse(pageOf(pane, c1).isManaged());
            assertTrue(c1.isVisible());
            assertTrue(c1.isManaged());

            pane.getTabs().remove(t1);
            // Detached and handed back untouched, not trapped inside a hidden wrapper.
            assertFalse(attachedContents(pane).contains(c1));
            assertTrue(c1.isVisible(), "removed preserved content should be visible");
            assertTrue(c1.isManaged(), "removed preserved content should be managed");
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
            assertEquals(2, attachedContents(pane).size());
            // Clearing selection mid-transition must cancel the tween, not just detach
            // the pages — otherwise its deferred settle re-shows a page under no selection.
            pane.getSelectionModel().clearSelection();
            assertEquals(0, attachedContents(pane).size());
            ref.set(pane);
        });
        // Wait well past the 40ms animation so any un-cancelled settle would have fired.
        Thread.sleep(220);
        runOnFx(() -> {
            RXTabPane pane = ref.get();
            assertEquals(-1, pane.getSelectedIndex());
            assertEquals(0, attachedContents(pane).size(),
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
            List<Node> contents = attachedContents(pane);
            // The two tween pages survive; the stale first page is detached...
            assertTrue(contents.contains(t1.getContent()));
            assertTrue(contents.contains(t2.getContent()));
            assertFalse(contents.contains(t0.getContent()));
            // ...and handed back untouched, not left invisible by the interrupted tween.
            assertTrue(t0.getContent().isVisible());
        });
    }

    @Test
    public void showingContentPreservesUserSetTransform() throws Exception {
        runOnFx(() -> {
            RXTab t0 = tab("A", 120, 40);
            RXTab t1 = tab("B", 300, 160);
            Node c0 = t0.getContent();
            // A caller-set transform on the content root (no content animation here).
            c0.setScaleX(1.5);
            c0.setRotate(15.0);
            RXTabPane pane = new RXTabPane(t0, t1);
            laidOut(pane);
            // Showing the page must not clobber the user's transform (the skin owns only
            // visibility/managed; transforms belong to the user or the animation).
            assertEquals(1.5, c0.getScaleX(), 0.001);
            assertEquals(15.0, c0.getRotate(), 0.001);
            // Still true after a plain (non-animated) switch away and back.
            pane.getSelectionModel().select(1);
            pane.getSelectionModel().select(0);
            assertEquals(1.5, c0.getScaleX(), 0.001);
            assertEquals(15.0, c0.getRotate(), 0.001);
        });
    }

    @Test
    public void animatedSwitchPreservesUserSetContentTransform() throws Exception {
        RXTab t0 = tab("A", 120, 40);
        RXTab t1 = tab("B", 300, 160);
        Node c0 = t0.getContent();
        AtomicReference<RXTabPane> ref = new AtomicReference<>();
        runOnFx(() -> {
            // Caller-set visual state on the content root.
            c0.setScaleX(1.5);
            c0.setOpacity(0.7);
            RXTabPane pane = new RXTabPane(t0, t1);
            // AnimZoom tweens scale + opacity + rotate and resets them to identity when it
            // finishes. It runs on the disposable page wrapper, so those resets must not
            // reach the user content node.
            pane.setContentAnimation(new AnimZoom());
            pane.setAnimationDuration(Duration.millis(40.0));
            laidOut(pane);
            pane.getSelectionModel().select(1);   // animate c0 out
            ref.set(pane);
        });
        // Let the out-animation settle, then animate c0 back in and let that settle too.
        Thread.sleep(200);
        runOnFx(() -> ref.get().getSelectionModel().select(0));
        Thread.sleep(200);
        runOnFx(() -> {
            // After a full animated round-trip the user's transform + opacity survive
            // intact: the animation only ever mutated the wrapper, never c0.
            assertEquals(1.5, c0.getScaleX(), 0.001,
                    "content animation must not clobber the user-set scale");
            assertEquals(0.7, c0.getOpacity(), 0.001,
                    "content animation must not clobber the user-set opacity");
        });
    }

    @Test
    public void contentRegionIsClippedToItsBounds() throws Exception {
        runOnFx(() -> {
            RXTab t0 = tab("A", 120, 40);
            RXTab t1 = tab("B", 300, 160);
            RXTabPane pane = new RXTabPane(t0, t1);
            laidOut(pane);
            StackPane region = contentRegionOf(pane);
            Node clip = region.getClip();
            assertNotNull(clip, "content region must be clipped so a transition cannot paint outside the pane");
            assertTrue(clip instanceof Rectangle, "content clip should be a Rectangle");
            Rectangle rect = (Rectangle) clip;
            assertTrue(rect.getWidth() > 0.0 && rect.getHeight() > 0.0, "clip must have a positive size");
            // The clip tracks the content region's laid-out size, masking any page that a
            // transition moves or scales beyond the viewport.
            assertEquals(region.getWidth(), rect.getWidth(), EPSILON);
            assertEquals(region.getHeight(), rect.getHeight(), EPSILON);
        });
    }

    // ==================== Helpers ====================

    private static StackPane contentRegionOf(RXTabPane pane) {
        StackPane region = (StackPane) pane.lookup(".content");
        assertNotNull(region, "content region not found");
        return region;
    }

    /**
     * The user content nodes currently attached, unwrapped from the skin-owned page
     * wrappers that host them (a page animation transforms the wrapper, never the
     * content). Each attached wrapper hosts exactly one content node.
     */
    private static List<Node> attachedContents(RXTabPane pane) {
        List<Node> contents = new ArrayList<>();
        for (Node wrapper : contentRegionOf(pane).getChildrenUnmodifiable()) {
            contents.addAll(((StackPane) wrapper).getChildrenUnmodifiable());
        }
        return contents;
    }

    /** The page wrapper hosting {@code content}, or {@code null} if it is not attached. */
    private static StackPane pageOf(RXTabPane pane, Node content) {
        for (Node wrapper : contentRegionOf(pane).getChildrenUnmodifiable()) {
            StackPane page = (StackPane) wrapper;
            if (page.getChildrenUnmodifiable().contains(content)) {
                return page;
            }
        }
        return null;
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
