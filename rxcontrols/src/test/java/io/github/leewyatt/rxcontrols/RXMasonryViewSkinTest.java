package io.github.leewyatt.rxcontrols;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
