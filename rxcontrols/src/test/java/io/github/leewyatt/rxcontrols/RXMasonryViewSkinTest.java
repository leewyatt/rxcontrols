package io.github.leewyatt.rxcontrols;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventType;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skin / virtualization tests for {@link RXMasonryView}: precise-height placement,
 * realizing only the cells intersecting the viewport, scroll-bar overflow and
 * scrolling, scroll-to, column resolution from width, and the resize anchor. Each
 * test drives a real (headless) layout pass.
 */
public class RXMasonryViewSkinTest {

    private static final double EPSILON = 0.5;

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
        // Pin modena so the inner ScrollBar gets a real measured breadth.
        Platform.runLater(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    @Test
    public void emptyViewReportsEmptyMetrics() throws Exception {
        onFx(() -> {
            RXMasonryView<String> view = new RXMasonryView<>();
            pump(host(view, 400, 300));
            assertTrue(view.getActualColumnCount() >= 1);
            assertEquals(-1, view.getFirstVisibleIndex());
            assertEquals(-1, view.getLastVisibleIndex());
            assertEquals(0, liveCellCount(view));
        });
    }

    @Test
    public void realizesOnlyTheVisibleCells() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = gallery(200);
            view.setColumnCount(3);
            pump(host(view, 340, 300));

            int realized = liveCellCount(view);
            assertTrue(realized > 0, "some cells are realized");
            assertTrue(realized < 40, "far fewer than 200 cells are realized, got " + realized);
            assertEquals(0, view.getFirstVisibleIndex(), "the top-left item is visible first");
            assertTrue(view.getLastVisibleIndex() < 60, "only the top of the list is realized");
        });
    }

    @Test
    public void placesFirstItemAtTopLeftWithProviderHeight() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = gallery(50);
            view.setColumnCount(3);
            view.setHgap(8);
            pump(host(view, 340, 300));

            RXMasonryCell<?> first = cellByIndex(view, 0);
            assertNotNull(first);
            assertEquals(0.0, first.getLayoutX(), EPSILON, "top-left x");
            assertEquals(0.0, first.getLayoutY(), EPSILON, "top y at scrollY 0");
            // item 0 height = 100 (index % 4 == 0).
            assertEquals(100.0, first.getHeight(), EPSILON);
            assertEquals(0, first.getColumnIndex());
            assertEquals(1, first.getColumnSpan());
        });
    }

    @Test
    public void scrollBarAppearsOnOverflowAndScrolls() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = gallery(200);
            view.setColumnCount(3);
            StackPane root = host(view, 340, 300);
            pump(root);

            ScrollBar bar = verticalBar(view);
            assertNotNull(bar, "a vertical scroll bar exists");
            assertTrue(bar.isVisible(), "the bar shows when content overflows");
            assertTrue(bar.getMax() > 0.0);

            int firstBefore = view.getFirstVisibleIndex();
            bar.setValue(bar.getMax() * 0.5);
            pump(root);
            assertTrue(view.getFirstVisibleIndex() > firstBefore, "scrolling reveals later items");
        });
    }

    @Test
    public void scrollToRevealsADeepItem() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = gallery(300);
            view.setColumnCount(3);
            StackPane root = host(view, 340, 300);
            pump(root);
            assertNull(cellByIndex(view, 180), "a deep item is not realized initially");

            view.scrollTo(180);
            pump(root);
            assertNotNull(cellByIndex(view, 180), "scrollTo realizes the target item");
            assertFalse(view.hasPendingScroll(), "the pending scroll was consumed");
        });
    }

    @Test
    public void columnCountResolvesFromWidth() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = gallery(60);
            view.setColumnWidth(100);
            view.setHgap(10);
            StackPane root = host(view, 1000, 300);
            pump(root);
            int wide = view.getActualColumnCount();
            assertTrue(wide >= 6, "a wide viewport resolves many columns, got " + wide);

            root.resize(360, 300);
            pump(root);
            int narrow = view.getActualColumnCount();
            assertTrue(narrow < wide, "narrowing drops the column count: " + narrow + " < " + wide);
            assertTrue(narrow >= 1);
        });
    }

    @Test
    public void resizeAnchorKeepsScrolledContentInView() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = gallery(300);
            view.setColumnCount(4);
            StackPane root = host(view, 480, 300);
            pump(root);

            view.scrollTo(120);
            pump(root);
            int anchored = view.getFirstVisibleIndex();
            assertTrue(anchored > 0, "scrolled away from the top");

            // A column-count change (forced) reflows everything; the anchor should keep
            // us near the same content rather than snapping back to the top.
            view.setColumnCount(3);
            pump(root);
            assertTrue(view.getFirstVisibleIndex() > 0,
                    "the anchor keeps scrolled content in view after a reflow");
        });
    }

    @Test
    public void breakpointOverrideRefillsViewportNotJustTheMetric() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = gallery(60);
            view.setColumnWidth(80);
            view.setHgap(0);
            StackPane root = host(view, 800, 300);
            pump(root);
            assertTrue(view.getActualColumnCount() > 2, "auto-resolves many columns at 800px");

            // 800px resolves to the md band; force 2 columns there.
            view.setMd(2);
            pump(root);
            assertEquals(2, view.getActualColumnCount(), "the metric reflects the override");
            // The realized cells must re-fill to 2 columns, not keep the stale wide layout.
            assertTrue(maxColumnIndex(view) <= 1,
                    "cells re-filled to 2 columns, max column was " + maxColumnIndex(view));
        });
    }

    @Test
    public void negativeVgapIsClampedToZero() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> zero = gallery(120);
            zero.setColumnCount(3);
            zero.setVgap(0);
            pump(host(zero, 340, 300));
            ScrollBar zeroBar = verticalBar(zero);
            double maxZero = zeroBar == null ? 0.0 : zeroBar.getMax();

            RXMasonryView<Integer> negative = gallery(120);
            negative.setColumnCount(3);
            negative.setVgap(-50);
            pump(host(negative, 340, 300));
            ScrollBar negBar = verticalBar(negative);
            double maxNegative = negBar == null ? 0.0 : negBar.getMax();

            assertTrue(maxZero > 0.0, "content overflows");
            // If the negative gap overlapped (not clamped) the content would be shorter.
            assertEquals(maxZero, maxNegative, 1.0,
                    "a negative vgap is clamped to zero, so the content height matches vgap 0");
            assertNotNull(cellByIndex(negative, 0), "the top cells still render");
        });
    }

    @Test
    public void changingProviderRebuildsHeights() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = gallery(50);
            view.setColumnCount(3);
            StackPane root = host(view, 340, 300);
            pump(root);
            assertEquals(100.0, cellByIndex(view, 0).getHeight(), EPSILON);

            view.setCellHeightProvider(ctx -> 250.0);
            pump(root);
            assertEquals(250.0, cellByIndex(view, 0).getHeight(), EPSILON,
                    "a new provider re-measures every cell");
        });
    }

    // ==================== Keyboard ====================

    @Test
    public void verticalArrowNavigatesGeometricallyWithinColumn() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = uniformGallery(60);
            view.setColumnCount(3);
            StackPane root = host(view, 340, 600);
            pump(root);
            MultipleSelectionModel<Integer> sm = view.getSelectionModel();

            fireKey(view, KeyCode.DOWN, false, false); // no focus -> first item
            assertEquals(0, sm.getSelectedIndex());
            fireKey(view, KeyCode.DOWN, false, false); // geometric down in column 0 -> item 3
            assertEquals(3, sm.getSelectedIndex());
            fireKey(view, KeyCode.DOWN, false, false); // -> item 6
            assertEquals(6, sm.getSelectedIndex());
            fireKey(view, KeyCode.UP, false, false);   // -> item 3
            assertEquals(3, sm.getSelectedIndex());
        });
    }

    @Test
    public void leftRightStepBySourceIndexAndHomeEndJump() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = uniformGallery(60);
            view.setColumnCount(3);
            StackPane root = host(view, 340, 400);
            pump(root);
            MultipleSelectionModel<Integer> sm = view.getSelectionModel();

            fireKey(view, KeyCode.DOWN, false, false); // focus 0
            fireKey(view, KeyCode.RIGHT, false, false);
            assertEquals(1, sm.getSelectedIndex());
            fireKey(view, KeyCode.RIGHT, false, false);
            assertEquals(2, sm.getSelectedIndex());
            fireKey(view, KeyCode.LEFT, false, false);
            assertEquals(1, sm.getSelectedIndex());

            fireKey(view, KeyCode.END, false, false);
            assertEquals(59, sm.getSelectedIndex());
            fireKey(view, KeyCode.HOME, false, false);
            assertEquals(0, sm.getSelectedIndex());
        });
    }

    @Test
    public void spaceTogglesShiftExtendsAndCtrlASelectsAll() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = uniformGallery(60);
            view.setColumnCount(3);
            StackPane root = host(view, 340, 600);
            pump(root);
            MultipleSelectionModel<Integer> sm = view.getSelectionModel();
            sm.setSelectionMode(SelectionMode.MULTIPLE);

            fireKey(view, KeyCode.DOWN, false, false); // focus + select 0, anchor 0
            fireKey(view, KeyCode.DOWN, true, false);  // shift + geometric down -> range 0..3 by index
            assertEquals(List.of(0, 1, 2, 3), List.copyOf(sm.getSelectedIndices()));

            fireKey(view, KeyCode.SPACE, false, false); // toggle the focused lead (3) off
            assertFalse(sm.isSelected(3));

            fireKey(view, KeyCode.A, false, true);       // Ctrl/Cmd + A
            assertEquals(60, sm.getSelectedIndices().size());
        });
    }

    @Test
    public void shortcutArrowMovesFocusOnlyWithoutChangingSelection() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = uniformGallery(60);
            view.setColumnCount(3);
            StackPane root = host(view, 340, 600);
            pump(root);
            MultipleSelectionModel<Integer> sm = view.getSelectionModel();

            fireKey(view, KeyCode.DOWN, false, false); // focus + select 0
            fireKey(view, KeyCode.DOWN, false, true);  // Shortcut + down: focus only
            assertEquals(0, sm.getSelectedIndex(), "selection unchanged by Shortcut+arrow");
            assertTrue(hasFocusRing(cellByIndex(view, 3)), "focus ring moved to item 3");
        });
    }

    @Test
    public void enterActivatesFocusedItem() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = uniformGallery(60);
            view.setColumnCount(3);
            AtomicInteger fired = new AtomicInteger(-1);
            view.setOnAction(e -> fired.set(e.getIndex()));
            StackPane root = host(view, 340, 400);
            pump(root);

            fireKey(view, KeyCode.DOWN, false, false); // focus 0
            fireKey(view, KeyCode.ENTER, false, false);
            assertEquals(0, fired.get());
        });
    }

    // ==================== Mouse ====================

    @Test
    public void plainClickSelectsAndFocuses() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = uniformGallery(60);
            view.setColumnCount(3);
            StackPane root = host(view, 340, 400);
            pump(root);
            MultipleSelectionModel<Integer> sm = view.getSelectionModel();

            fireCellPress(cellByIndex(view, 4), false, false);
            assertEquals(4, sm.getSelectedIndex());
            assertTrue(isSelected(cellByIndex(view, 4)));
            assertTrue(hasFocusRing(cellByIndex(view, 4)));
        });
    }

    @Test
    public void shortcutClickTogglesAndShiftClickExtendsRange() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = uniformGallery(60);
            view.setColumnCount(3);
            StackPane root = host(view, 340, 600);
            pump(root);
            MultipleSelectionModel<Integer> sm = view.getSelectionModel();
            sm.setSelectionMode(SelectionMode.MULTIPLE);

            fireCellPress(cellByIndex(view, 1), false, false);
            fireCellPress(cellByIndex(view, 4), false, true);  // shortcut adds 4
            fireCellPress(cellByIndex(view, 7), false, true);  // shortcut adds 7
            assertTrue(sm.isSelected(1) && sm.isSelected(4) && sm.isSelected(7));
            fireCellPress(cellByIndex(view, 4), false, true);  // shortcut toggles 4 off
            assertFalse(sm.isSelected(4));

            fireCellPress(cellByIndex(view, 2), false, false); // anchor 2
            fireCellPress(cellByIndex(view, 6), true, false);  // shift -> range 2..6
            assertEquals(List.of(2, 3, 4, 5, 6), List.copyOf(sm.getSelectedIndices()));
        });
    }

    @Test
    public void doubleClickFiresActionEvent() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = uniformGallery(60);
            view.setColumnCount(3);
            AtomicInteger fired = new AtomicInteger(-1);
            view.setOnAction(e -> fired.set(e.getIndex()));
            StackPane root = host(view, 340, 400);
            pump(root);

            fireDoubleClick(cellByIndex(view, 4));
            assertEquals(4, fired.get());
        });
    }

    @Test
    public void marqueeFromBlankSpaceSelectsIntersectingCells() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = uniformGallery(6);
            view.setColumnCount(3);
            view.setHgap(10);
            view.setVgap(10);
            StackPane root = host(view, 340, 400);
            view.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            pump(root);
            MultipleSelectionModel<Integer> sm = view.getSelectionModel();

            // Content is two ~100px tiers (< 400), so below ~210px is blank space.
            Node viewport = view.lookup(".viewport");
            assertFalse(selectionRectangle(view).isVisible(), "overlay hidden before drag");
            fireMouse(viewport, MouseEvent.MOUSE_PRESSED, 50, 380, true);
            fireMouse(viewport, MouseEvent.MOUSE_DRAGGED, 300, 5, true);
            assertTrue(selectionRectangle(view).isVisible(), "overlay visible during drag");
            fireMouse(viewport, MouseEvent.MOUSE_RELEASED, 300, 5, false);
            pump(root);

            // The rect [50,300] x [5,380] in content space covers all six cells (two
            // ~106px-wide tiers within x and y).
            assertEquals(List.of(0, 1, 2, 3, 4, 5), List.copyOf(sm.getSelectedIndices()));
            assertFalse(selectionRectangle(view).isVisible(), "overlay hidden after release");
        });
    }

    @Test
    public void escapeCancelsAnArmedMarquee() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = uniformGallery(6);
            view.setColumnCount(3);
            StackPane root = host(view, 340, 400);
            view.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            pump(root);

            Node viewport = view.lookup(".viewport");
            fireMouse(viewport, MouseEvent.MOUSE_PRESSED, 50, 380, true);
            fireMouse(viewport, MouseEvent.MOUSE_DRAGGED, 300, 5, true);
            assertTrue(selectionRectangle(view).isVisible());

            fireKey(view, KeyCode.ESCAPE, false, false);
            assertFalse(selectionRectangle(view).isVisible(), "Escape cancels the marquee");
        });
    }

    @Test
    public void pageDownAndUpWalkAndStopAtEdges() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = uniformGallery(120);
            view.setColumnCount(3);
            StackPane root = host(view, 340, 300);
            pump(root);
            MultipleSelectionModel<Integer> sm = view.getSelectionModel();

            fireKey(view, KeyCode.DOWN, false, false); // focus 0
            fireKey(view, KeyCode.PAGE_DOWN, false, false);
            int first = sm.getSelectedIndex();
            assertTrue(first > 3 && first % 3 == 0, "PageDown walks several rows in column 0, got " + first);
            fireKey(view, KeyCode.PAGE_DOWN, false, false);
            int second = sm.getSelectedIndex();
            assertTrue(second > first, "a second PageDown advances further");
            fireKey(view, KeyCode.PAGE_UP, false, false);
            assertTrue(sm.getSelectedIndex() < second, "PageUp walks back");

            fireKey(view, KeyCode.END, false, false);
            fireKey(view, KeyCode.PAGE_DOWN, false, false);
            assertEquals(119, sm.getSelectedIndex(), "PageDown at the bottom does not move");
        });
    }

    @Test
    public void horizontalNavResetsTheHeldVerticalColumn() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = uniformGallery(60);
            view.setColumnCount(3);
            StackPane root = host(view, 340, 600);
            pump(root);
            MultipleSelectionModel<Integer> sm = view.getSelectionModel();

            fireKey(view, KeyCode.DOWN, false, false); // focus 0 (column 0)
            fireKey(view, KeyCode.DOWN, false, false); // hold column 0 -> item 3
            assertEquals(3, sm.getSelectedIndex());
            fireKey(view, KeyCode.RIGHT, false, false); // -> item 4 (column 1), resets the held x
            assertEquals(4, sm.getSelectedIndex());
            fireKey(view, KeyCode.DOWN, false, false); // re-seeds from item 4's column -> item 7
            assertEquals(7, sm.getSelectedIndex(),
                    "after RIGHT the held column is re-seeded, so DOWN stays in column 1");
        });
    }

    // ==================== Reorder animation ====================

    @Test
    public void reorderGlideOnColumnChangePreservesIdentityAndAnimates() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = uniformGallery(30);
            view.setColumnCount(3);
            view.setAnimated(true);
            StackPane root = host(view, 340, 400);
            pump(root);
            RXMasonryCell<?> before = cellByIndex(view, 2);
            assertNotNull(before);

            // 3 -> 2 columns moves item 2 from column 2 to column 0 row 1: a real reflow.
            view.setColumnCount(2);
            layoutOnce(root);
            RXMasonryCell<?> after = cellByIndex(view, 2);
            assertSame(before, after, "the carry-over cell keeps its identity and glides");
            assertTrue(Math.abs(after.getTranslateX()) + Math.abs(after.getTranslateY()) > 0.5,
                    "the cell is mid-glide (the FLIP delta is set synchronously)");
        });
    }

    @Test
    public void animationDisabledSnapsInsteadOfGliding() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = uniformGallery(30);
            view.setColumnCount(3);
            view.setAnimated(false);
            StackPane root = host(view, 340, 400);
            pump(root);

            view.setColumnCount(2);
            layoutOnce(root);
            RXMasonryCell<?> cell = cellByIndex(view, 2);
            assertEquals(0.0, cell.getTranslateX(), 0.001);
            assertEquals(0.0, cell.getTranslateY(), 0.001);
        });
    }

    @Test
    public void disablingAnimationMidGlideSnapsToFinal() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = uniformGallery(30);
            view.setColumnCount(3);
            view.setAnimated(true);
            StackPane root = host(view, 340, 400);
            pump(root);

            view.setColumnCount(2);
            layoutOnce(root);
            RXMasonryCell<?> gliding = cellByIndex(view, 2);
            assertTrue(Math.abs(gliding.getTranslateX()) + Math.abs(gliding.getTranslateY()) > 0.5,
                    "mid-glide");

            view.setAnimated(false); // onAnimationSettingsChanged snaps every in-flight glide
            layoutOnce(root);
            RXMasonryCell<?> snapped = cellByIndex(view, 2);
            assertEquals(0.0, snapped.getTranslateX(), 0.001);
            assertEquals(0.0, snapped.getTranslateY(), 0.001);
        });
    }

    @Test
    public void disposeStopsInFlightGlide() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = uniformGallery(30);
            view.setColumnCount(3);
            view.setAnimated(true);
            StackPane root = host(view, 340, 400);
            pump(root);

            view.setColumnCount(2);
            layoutOnce(root);
            RXMasonryCell<?> gliding = cellByIndex(view, 2);
            assertTrue(Math.abs(gliding.getTranslateX()) + Math.abs(gliding.getTranslateY()) > 0.5,
                    "mid-glide");

            view.getSkin().dispose(); // disposeSkin -> viewport.dispose -> snapAllGlides
            assertEquals(0.0, gliding.getTranslateX(), 0.001, "the glide is snapped on dispose");
            assertEquals(0.0, gliding.getTranslateY(), 0.001);
        });
    }

    @Test
    public void estimatedColumnChangeSnapsGlideInsteadOfFightingTheRepack() throws Exception {
        onFx(() -> {
            // On the estimated path a column-count change re-measures and re-packs every
            // cell in the pass, so a reorder glide started by that change has an unstable
            // target and is snapped — not left mid-glide for the converge re-fill to fight.
            RXMasonryView<Integer> view = estimatedGallery(30);
            view.setColumnCount(3);
            view.setAnimated(true);
            StackPane root = host(view, 340, 420);
            pump(root);

            view.setColumnCount(2);
            layoutOnce(root);

            RXMasonryCell<?> cell = cellByIndex(view, 2);
            assertNotNull(cell);
            assertEquals(0.0, cell.getTranslateX(), 0.001, "estimated-path column change snaps the glide");
            assertEquals(0.0, cell.getTranslateY(), 0.001);
        });
    }

    // ==================== Placement cache ====================

    private static RXMasonryView<Integer> countingProviderView(AtomicInteger calls) {
        ObservableList<Integer> items = FXCollections.observableArrayList();
        for (int i = 0; i < 300; i++) {
            items.add(i);
        }
        RXMasonryView<Integer> view = new RXMasonryView<>(items);
        view.setColumnCount(3);
        view.setCellHeightProvider(ctx -> {
            calls.incrementAndGet();
            return 100.0 + (ctx.index() % 4) * 30.0;
        });
        return view;
    }

    @Test
    public void pureScrollReusesThePlacementWithoutRebuilding() throws Exception {
        onFx(() -> {
            AtomicInteger calls = new AtomicInteger();
            RXMasonryView<Integer> view = countingProviderView(calls);
            StackPane root = host(view, 340, 400);
            pumpUntilStable(root, 20);
            assertTrue(calls.get() > 0, "the initial layout builds the placement");

            calls.set(0);
            view.scrollTo(150); // pure scroll: geometry inputs unchanged
            pumpUntilStable(root, 20);
            assertEquals(0, calls.get(), "a pure scroll reuses the cached placement, never recomputing it");
        });
    }

    @Test
    public void geometryChangeRebuildsThePlacement() throws Exception {
        onFx(() -> {
            AtomicInteger calls = new AtomicInteger();
            RXMasonryView<Integer> view = countingProviderView(calls);
            StackPane root = host(view, 340, 400);
            pumpUntilStable(root, 20);

            calls.set(0);
            view.setColumnCount(2); // a geometry input change must invalidate the cache
            pumpUntilStable(root, 20);
            assertTrue(calls.get() > 0, "a column-count change rebuilds the placement");
        });
    }

    // ==================== Estimated path: measure-time re-pack ====================

    @Test
    public void estimatedPathKeepsColumnsBalancedDeepInList() throws Exception {
        onFx(() -> {
            ObservableList<Integer> items = FXCollections.observableArrayList();
            for (int i = 0; i < 5000; i++) {
                items.add(i);
            }
            RXMasonryView<Integer> view = new RXMasonryView<>(items);
            view.setColumnWidth(220);
            view.setHgap(12);
            view.setVgap(12);
            // Heights swing widely (98..314), so a fixed column assignment would random-walk
            // the column bottoms apart over thousands of items and blank out short columns.
            view.setCellFactory(v -> new RXMasonryCell<>() {
                @Override
                protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    setPrefHeight(empty || item == null ? USE_COMPUTED_SIZE : 44.0 + (1 + (item % 5)) * 54.0);
                }
            });
            StackPane root = host(view, 1100, 680);
            pumpUntilStable(root, 120);
            int columns = view.getActualColumnCount();
            for (int target : new int[]{1500, 3200, 4990, 4999}) {
                view.scrollTo(target);
                pumpUntilStable(root, 200);
            }

            // Near the very bottom, every column must still reach the visible window — no
            // column drifted hundreds of px above the others.
            Set<Integer> visibleColumns = new HashSet<>();
            for (Node n : view.lookupAll(".rx-masonry-cell")) {
                if (n instanceof RXMasonryCell<?> c && n.isVisible() && c.getIndex() >= 0) {
                    visibleColumns.add(c.getColumnIndex());
                }
            }
            assertEquals(columns, visibleColumns.size(),
                    "all columns reach the bottom window (no blank column from drift)");
        });
    }

    // Each item's real cell height, deterministic and different from the default estimate
    // (200) so a correction is forced.
    private static double measuredHeightOf(int item) {
        return 60.0 + (item % 5) * 20.0;
    }

    // An estimated-path view (no cellHeightProvider): cells carry a fixed pref height the
    // skin must measure and re-pack around.
    private static RXMasonryView<Integer> estimatedGallery(int count) {
        ObservableList<Integer> items = FXCollections.observableArrayList();
        for (int i = 0; i < count; i++) {
            items.add(i);
        }
        RXMasonryView<Integer> view = new RXMasonryView<>(items);
        view.setColumnCount(3);
        view.setHgap(8);
        view.setVgap(8);
        view.setCellFactory(v -> new RXMasonryCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setPrefHeight(empty || item == null ? USE_COMPUTED_SIZE : measuredHeightOf(item));
            }
        });
        return view;
    }

    @Test
    public void estimatedPathConvergesToMeasuredHeights() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = estimatedGallery(30);
            StackPane root = host(view, 340, 420);
            pump(root);

            // The top items are realized and measured on the first pass, so by now the
            // placement uses their real heights, not the 200 estimate.
            RXMasonryCell<?> cell0 = cellByIndex(view, 0);
            RXMasonryCell<?> cell1 = cellByIndex(view, 1);
            assertNotNull(cell0);
            assertNotNull(cell1);
            assertEquals(measuredHeightOf(0), cell0.getHeight(), 1.0, "item 0 converged to its measured height");
            assertEquals(measuredHeightOf(1), cell1.getHeight(), 1.0, "item 1 converged to its measured height");
        });
    }

    @Test
    public void estimatedPathMeasuresItemsRevealedByUpwardScroll() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = estimatedGallery(120);
            StackPane root = host(view, 340, 300);
            pump(root);

            // Jump down past never-realized items, then scroll back UP a little into items
            // that were never measured. They must be measured now (not stranded at the 200
            // estimate) — the bug the removed up-scroll skip caused.
            view.scrollTo(90);
            pumpUntilStable(root, 60);
            view.scrollTo(86);
            pumpUntilStable(root, 60);

            RXMasonryCell<?> revealed = cellByIndex(view, 87);
            assertNotNull(revealed, "item 87 is realized after scrolling up to it");
            assertEquals(measuredHeightOf(87), revealed.getHeight(), 1.0,
                    "an item first revealed by an upward scroll is measured, not stranded at the estimate");
        });
    }

    @Test
    public void estimatedPathSettlesAtScrollBarBoundary() throws Exception {
        onFx(() -> {
            // Content height sits right at the viewport bottom, so the scroll bar toggles
            // as cells converge. Before the column-count-keyed invalidation fix this
            // re-measured at alternating track widths and span layout forever.
            ObservableList<Integer> items = FXCollections.observableArrayList();
            for (int i = 0; i < 16; i++) {
                items.add(i);
            }
            RXMasonryView<Integer> view = new RXMasonryView<>(items);
            view.setColumnCount(3);
            view.setHgap(8);
            view.setVgap(8);
            // Fixed height 50, far below the 200 estimate, so content crosses the viewport
            // bottom as it converges — the boundary case.
            view.setCellFactory(v -> new RXMasonryCell<>() {
                @Override
                protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    setPrefHeight(empty || item == null ? USE_COMPUTED_SIZE : 50.0);
                }
            });
            StackPane root = host(view, 340, 420);
            assertTrue(pumpUntilStable(root, 40), "the estimated layout settles at the scroll-bar boundary");
        });
    }

    @Test
    public void estimatedPathConvergenceSettlesAndStopsDirtying() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = estimatedGallery(24);
            StackPane root = host(view, 340, 420);
            pump(root);

            // Once settled, another layout must not move the converged top cell — proves
            // the measure loop reaches a fixpoint rather than oscillating.
            double before = cellByIndex(view, 0).getHeight();
            layoutOnce(root);
            double after = cellByIndex(view, 0).getHeight();
            assertEquals(before, after, 0.001, "a settled estimated layout is stable");
            assertEquals(measuredHeightOf(0), after, 1.0);
        });
    }

    // ==================== Helpers ====================

    // An image-gallery-style view: each item's height comes from its index, so the
    // placement is exact (the precise provider path).
    private static RXMasonryView<Integer> gallery(int count) {
        ObservableList<Integer> items = FXCollections.observableArrayList();
        for (int i = 0; i < count; i++) {
            items.add(i);
        }
        RXMasonryView<Integer> view = new RXMasonryView<>(items);
        view.setCellHeightProvider(ctx -> 100.0 + (ctx.index() % 4) * 30.0);
        return view;
    }

    // Uniform cell heights so columns line up in predictable tiers for keyboard tests.
    private static RXMasonryView<Integer> uniformGallery(int count) {
        ObservableList<Integer> items = FXCollections.observableArrayList();
        for (int i = 0; i < count; i++) {
            items.add(i);
        }
        RXMasonryView<Integer> view = new RXMasonryView<>(items);
        view.setCellHeightProvider(context -> 100.0);
        return view;
    }

    private static void fireKey(Node target, KeyCode code, boolean shift, boolean shortcut) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, shortcut, false, shortcut));
    }

    private static void fireCellPress(Node cell, boolean shift, boolean shortcut) {
        cell.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                shift, shortcut, false, shortcut, true, false, false, false, false, true,
                new PickResult(cell, 0, 0)));
    }

    private static void fireDoubleClick(Node cell) {
        cell.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 2,
                false, false, false, false, false, false, false, false, false, true,
                new PickResult(cell, 0, 0)));
    }

    private static void fireMouse(Node target, EventType<MouseEvent> type, double x, double y,
                                  boolean primaryDown) {
        target.fireEvent(new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, primaryDown, false, false, false, false, false,
                new PickResult(target, x, y)));
    }

    private static boolean isSelected(RXMasonryCell<?> cell) {
        return cell != null && cell.getPseudoClassStates().stream()
                .anyMatch(pc -> pc.getPseudoClassName().equals("selected"));
    }

    private static boolean hasFocusRing(RXMasonryCell<?> cell) {
        return cell != null && cell.getPseudoClassStates().stream()
                .anyMatch(pc -> pc.getPseudoClassName().equals("focused"));
    }

    private static Node selectionRectangle(RXMasonryView<?> view) {
        Node rectangle = view.lookup(".selection-rectangle");
        assertNotNull(rectangle);
        return rectangle;
    }

    private static int liveCellCount(RXMasonryView<?> view) {
        int count = 0;
        for (Node node : view.lookupAll(".rx-masonry-cell")) {
            if (node instanceof RXMasonryCell<?> cell && node.isVisible() && cell.getIndex() >= 0) {
                count++;
            }
        }
        return count;
    }

    private static int maxColumnIndex(RXMasonryView<?> view) {
        int max = -1;
        for (Node node : view.lookupAll(".rx-masonry-cell")) {
            if (node instanceof RXMasonryCell<?> cell && node.isVisible() && cell.getIndex() >= 0) {
                max = Math.max(max, cell.getColumnIndex());
            }
        }
        return max;
    }

    private static RXMasonryCell<?> cellByIndex(RXMasonryView<?> view, int index) {
        for (Node node : view.lookupAll(".rx-masonry-cell")) {
            if (node instanceof RXMasonryCell<?> cell && node.isVisible() && cell.getIndex() == index
                    && !cell.isEmpty()) {
                return cell;
            }
        }
        return null;
    }

    private static ScrollBar verticalBar(RXMasonryView<?> view) {
        for (Node node : view.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar bar && bar.getOrientation() == Orientation.VERTICAL) {
                return bar;
            }
        }
        return null;
    }

    private static StackPane host(RXMasonryView<?> view, double w, double h) {
        StackPane root = new StackPane(view);
        new Scene(root, w, h);
        root.resize(w, h);
        return root;
    }

    private static void pump(Region root) {
        for (int i = 0; i < 4; i++) {
            root.applyCss();
            root.layout();
        }
    }

    // A single layout pass, so a reorder pass's just-set glide translate is observable
    // before any subsequent non-reorder fill re-acquires cells.
    private static void layoutOnce(Region root) {
        root.applyCss();
        root.layout();
    }

    // Lays out until the tree stops requesting layout (a settled measure loop) or the cap
    // is hit. Returns false when it never settles — the infinite-loop signature.
    private static boolean pumpUntilStable(Region root, int maxPasses) {
        for (int i = 0; i < maxPasses; i++) {
            root.applyCss();
            root.layout();
            if (!root.isNeedsLayout()) {
                return true;
            }
        }
        return false;
    }

    private static void onFx(Runnable body) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable error = failure.get();
        if (error instanceof Exception exception) {
            throw exception;
        }
        if (error != null) {
            throw new AssertionError(error);
        }
    }
}
