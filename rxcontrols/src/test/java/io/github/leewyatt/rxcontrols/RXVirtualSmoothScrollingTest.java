package io.github.leewyatt.rxcontrols;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smooth wheel scrolling integration tests for the Rx virtual viewport controls.
 */
public class RXVirtualSmoothScrollingTest {

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
        Platform.runLater(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    @Test
    public void virtualControlsDefaultSmoothScrollingToTrue() throws Exception {
        onFx(() -> {
            assertTrue(new RXListView<>().isSmoothScrolling());
            assertTrue(new RXTileView<>().isSmoothScrolling());
            assertTrue(new RXMasonryView<>().isSmoothScrolling());
            assertEquals(SmoothScrollMode.MOMENTUM, new RXListView<>().getSmoothScrollMode());
            assertEquals(SmoothScrollMode.MOMENTUM, new RXTileView<>().getSmoothScrollMode());
            assertEquals(SmoothScrollMode.MOMENTUM, new RXMasonryView<>().getSmoothScrollMode());
        });
    }

    @Test
    public void listSmoothWheelAdvancesAfterPulse() throws Exception {
        AtomicReference<RXListView<String>> viewRef = new AtomicReference<>();
        AtomicReference<StackPane> rootRef = new AtomicReference<>();
        onFx(() -> {
            RXListView<String> view = list(200);
            view.setFixedCellSize(20.0);
            StackPane root = host(view, 300.0, 200.0);
            pump(root);
            Node viewport = view.lookup(".viewport");
            fireWheel(viewport, -120.0);
            assertEquals(0, view.getVisibleRange().firstIndex(),
                    "smooth scrolling does not jump in the same event turn");
            viewRef.set(view);
            rootRef.set(root);
        });

        waitForFx(220.0);

        onFx(() -> {
            pump(rootRef.get());
            assertTrue(viewRef.get().getVisibleRange().firstIndex() > 0,
                    "smooth animation advances the visible range on later pulses");
        });
    }

    @Test
    public void tileSmoothWheelAdvancesAfterPulse() throws Exception {
        AtomicReference<RXTileView<String>> viewRef = new AtomicReference<>();
        AtomicReference<StackPane> rootRef = new AtomicReference<>();
        onFx(() -> {
            RXTileView<String> view = tile(200);
            view.setMaxColumns(2);
            StackPane root = host(view, 300.0, 200.0);
            pump(root);
            fireWheel(view.lookup(".viewport"), -180.0);
            assertEquals(0, view.getVisibleRange().firstIndex(),
                    "smooth scrolling does not jump in the same event turn");
            viewRef.set(view);
            rootRef.set(root);
        });

        waitForFx(260.0);

        onFx(() -> {
            pump(rootRef.get());
            assertTrue(viewRef.get().getVisibleRange().firstIndex() > 0,
                    "tile smooth animation advances the visible range on later pulses");
        });
    }

    @Test
    public void masonrySmoothWheelAdvancesAfterPulse() throws Exception {
        AtomicReference<RXMasonryView<Integer>> viewRef = new AtomicReference<>();
        AtomicReference<StackPane> rootRef = new AtomicReference<>();
        onFx(() -> {
            RXMasonryView<Integer> view = masonry(200);
            view.setColumnCount(2);
            StackPane root = host(view, 360.0, 220.0);
            pump(root);
            fireWheel(view.lookup(".viewport"), -180.0);
            assertEquals(0, view.getFirstVisibleIndex(),
                    "smooth scrolling does not jump in the same event turn");
            viewRef.set(view);
            rootRef.set(root);
        });

        waitForFx(260.0);

        onFx(() -> {
            pump(rootRef.get());
            assertTrue(viewRef.get().getFirstVisibleIndex() > 0,
                    "masonry smooth animation is not cancelled by anchor correction");
        });
    }

    @Test
    public void virtualViewportPicksBlankAreasForWheelInput() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = masonry(200);
            StackPane root = host(view, 360.0, 220.0);
            pump(root);
            Node viewport = view.lookup(".viewport");
            Node content = view.lookup(".content");

            assertTrue(viewport.isPickOnBounds(), "viewport receives wheel events in blank areas");
            assertTrue(content.isPickOnBounds(), "content layer receives wheel events in item gaps");
        });
    }

    @Test
    public void listImmediatePathChainsAtBoundary() throws Exception {
        onFx(() -> {
            RXListView<String> view = list(200);
            view.setFixedCellSize(20.0);
            view.setSmoothScrolling(false);
            StackPane root = host(view, 300.0, 200.0);
            pump(root);
            AtomicInteger bubbled = new AtomicInteger();
            root.addEventHandler(ScrollEvent.SCROLL, event -> bubbled.incrementAndGet());
            Node viewport = view.lookup(".viewport");

            ScrollEvent boundary = fireWheel(viewport, 80.0);
            assertFalse(boundary.isConsumed(), "top boundary chains to parent");
            assertEquals(1, bubbled.get());

            fireWheel(viewport, -120.0);
            pump(root);
            assertEquals(1, bubbled.get(), "usable immediate wheel input is consumed");
            assertTrue(view.getVisibleRange().firstIndex() > 0);
        });
    }

    @Test
    public void listLineTextDeltaUsesRowHeight() throws Exception {
        onFx(() -> {
            RXListView<String> view = list(200);
            view.setFixedCellSize(20.0);
            view.setSmoothScrolling(false);
            StackPane root = host(view, 300.0, 200.0);
            pump(root);

            fireLineWheel(view.lookup(".viewport"), -3.0);
            pump(root);

            assertEquals(3, view.getVisibleRange().firstIndex(),
                    "three line units map to three fixed-height rows");
        });
    }

    @Test
    public void listDirectTouchUsesImmediateBoundaryPolicy() throws Exception {
        onFx(() -> {
            RXListView<String> view = list(200);
            view.setFixedCellSize(20.0);
            StackPane root = host(view, 300.0, 200.0);
            pump(root);
            AtomicInteger bubbled = new AtomicInteger();
            root.addEventHandler(ScrollEvent.SCROLL, event -> bubbled.incrementAndGet());
            Node viewport = view.lookup(".viewport");

            fireWheel(viewport, 80.0, true);
            assertEquals(1, bubbled.get(), "direct touch at the top boundary chains");

            fireWheel(viewport, -120.0, true);
            pump(root);

            assertEquals(1, bubbled.get(), "direct touch that can scroll is consumed");
            assertTrue(view.getVisibleRange().firstIndex() > 0);
        });
    }

    @Test
    public void tileImmediatePathChainsAtBoundary() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tile(200);
            view.setMaxColumns(2);
            view.setSmoothScrolling(false);
            StackPane root = host(view, 300.0, 200.0);
            pump(root);
            AtomicInteger bubbled = new AtomicInteger();
            root.addEventHandler(ScrollEvent.SCROLL, event -> bubbled.incrementAndGet());

            ScrollEvent boundary = fireWheel(view.lookup(".viewport"), 80.0);

            assertFalse(boundary.isConsumed(), "top boundary chains to parent");
            assertEquals(1, bubbled.get());
        });
    }

    @Test
    public void masonryImmediatePathChainsAtBoundary() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = masonry(200);
            view.setColumnCount(2);
            view.setSmoothScrolling(false);
            StackPane root = host(view, 360.0, 220.0);
            pump(root);
            AtomicInteger bubbled = new AtomicInteger();
            root.addEventHandler(ScrollEvent.SCROLL, event -> bubbled.incrementAndGet());

            ScrollEvent boundary = fireWheel(view.lookup(".viewport"), 80.0);

            assertFalse(boundary.isConsumed(), "top boundary chains to parent");
            assertEquals(1, bubbled.get());
        });
    }

    private static RXListView<String> list(int count) {
        ObservableList<String> items = FXCollections.observableArrayList();
        for (int i = 0; i < count; i++) {
            items.add("Item " + i);
        }
        return new RXListView<>(items);
    }

    private static RXTileView<String> tile(int count) {
        ObservableList<String> items = FXCollections.observableArrayList();
        for (int i = 0; i < count; i++) {
            items.add("Item " + i);
        }
        return new RXTileView<>(items);
    }

    private static RXMasonryView<Integer> masonry(int count) {
        ObservableList<Integer> items = FXCollections.observableArrayList();
        for (int i = 0; i < count; i++) {
            items.add(i);
        }
        RXMasonryView<Integer> view = new RXMasonryView<>(items);
        view.setCellHeightProvider(context -> 120.0);
        return view;
    }

    private static StackPane host(Node node, double w, double h) {
        StackPane root = new StackPane(node);
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

    private static ScrollEvent fireWheel(Node target, double deltaY) {
        return fireWheel(target, deltaY, false);
    }

    private static ScrollEvent fireWheel(Node target, double deltaY, boolean direct) {
        ScrollEvent event = new ScrollEvent(ScrollEvent.SCROLL,
                0.0, 0.0, 0.0, 0.0,
                false, false, false, false,
                direct, false,
                0.0, deltaY, 0.0, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0.0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0.0,
                0,
                null);
        target.fireEvent(event);
        return event;
    }

    private static ScrollEvent fireLineWheel(Node target, double textDeltaY) {
        ScrollEvent event = new ScrollEvent(ScrollEvent.SCROLL,
                0.0, 0.0, 0.0, 0.0,
                false, false, false, false,
                false, false,
                0.0, 0.0, 0.0, 0.0,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0.0,
                ScrollEvent.VerticalTextScrollUnits.LINES, textDeltaY,
                0,
                null);
        target.fireEvent(event);
        return event;
    }

    private static void waitForFx(double millis) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            PauseTransition pause = new PauseTransition(Duration.millis(millis));
            pause.setOnFinished(event -> latch.countDown());
            pause.play();
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting for JavaFX pulse");
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
