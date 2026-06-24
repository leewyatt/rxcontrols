package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXMasonryView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
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
 * Viewport-internal tests for {@link RXMasonryViewport} that need package-visible
 * access (the scroll offset): the anchor pin must not corrupt the scroll position
 * when the items list mutates while scrolled.
 */
public class RXMasonryViewportTest {

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
    public void frontInsertPreservesScrollOffset() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = gallery(300);
            view.setColumnCount(3);
            StackPane root = host(view, 340, 300);
            pump(root);

            view.scrollTo(180);
            pump(root);
            RXMasonryViewport<?> viewport = viewport(view);
            double before = viewport.scrollOffset();
            assertTrue(before > 0.0, "scrolled away from the top");

            // A front insert shifts every index. Without the anchor reset the pin would
            // re-target a now-different index and corrupt the scroll offset (jump).
            view.getItems().add(0, 9999);
            pump(root);
            assertEquals(before, viewport.scrollOffset(), 1.0,
                    "a front insert preserves the scroll offset");
        });
    }

    @Test
    public void frontRemovePreservesScrollOffset() throws Exception {
        onFx(() -> {
            RXMasonryView<Integer> view = gallery(300);
            view.setColumnCount(3);
            StackPane root = host(view, 340, 300);
            pump(root);

            view.scrollTo(180);
            pump(root);
            RXMasonryViewport<?> viewport = viewport(view);
            double before = viewport.scrollOffset();
            assertTrue(before > 0.0, "scrolled away from the top");

            view.getItems().remove(0);
            pump(root);
            assertEquals(before, viewport.scrollOffset(), 1.0,
                    "a front remove preserves the scroll offset");
        });
    }

    // ==================== Helpers ====================

    private static RXMasonryView<Integer> gallery(int count) {
        ObservableList<Integer> items = FXCollections.observableArrayList();
        for (int i = 0; i < count; i++) {
            items.add(i);
        }
        RXMasonryView<Integer> view = new RXMasonryView<>(items);
        view.setCellHeightProvider(context -> 100.0 + (context.index() % 4) * 30.0);
        return view;
    }

    private static RXMasonryViewport<?> viewport(RXMasonryView<?> view) {
        Node node = view.lookup(".viewport");
        assertNotNull(node, "the viewport is in the scene graph");
        return (RXMasonryViewport<?>) node;
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
