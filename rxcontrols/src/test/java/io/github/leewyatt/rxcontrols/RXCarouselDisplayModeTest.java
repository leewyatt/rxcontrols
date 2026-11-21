package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the display modes of the arrows and the navigator in a showing
 * window: a mode set while a hover fade plays outlasts the fade. A test ends
 * by hiding the window's root rather than the window.
 */
public class RXCarouselDisplayModeTest {

    /**
     * Starts the JavaFX toolkit before loading Control subclasses.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
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

    @Test
    public void showModeSetWhileTheFadeOutPlaysKeepsEverythingVisible() throws Exception {
        RXCarousel[] carousel = new RXCarousel[1];
        onFx(() -> {
            carousel[0] = showingCarousel(3);
            carousel[0].setArrowDisplayMode(DisplayMode.AUTO);
            carousel[0].setNavigatorDisplayMode(DisplayMode.AUTO);
            Event.fireEvent(carousel[0], mouse(MouseEvent.MOUSE_ENTERED));
        });
        Thread.sleep(400L);
        try {
            onFx(() -> {
                for (Node node : arrowsAndNavigator(carousel[0])) {
                    assertTrue(node.isVisible());
                }
                Event.fireEvent(carousel[0], mouse(MouseEvent.MOUSE_EXITED));
                carousel[0].setArrowDisplayMode(DisplayMode.SHOW);
                carousel[0].setNavigatorDisplayMode(DisplayMode.SHOW);
            });
            Thread.sleep(400L);
            onFx(() -> assertEquals(List.of(1.0, 1.0, 1.0), shownOpacities(carousel[0])));
        } finally {
            onFx(() -> carousel[0].getScene().getRoot().setVisible(false));
        }
    }

    // ==================== Helpers ====================

    private static List<Node> arrowsAndNavigator(RXCarousel carousel) {
        List<Node> nodes = new ArrayList<>();
        for (String selector : List.of(".prev-button", ".next-button", ".carousel-navigator")) {
            nodes.add(carousel.lookup(selector));
        }
        return nodes;
    }

    // The opacity of the previous arrow, the next arrow and the navigator;
    // 0 for one that is not visible.
    private static List<Double> shownOpacities(RXCarousel carousel) {
        List<Double> opacities = new ArrayList<>();
        for (Node node : arrowsAndNavigator(carousel)) {
            opacities.add(node.isVisible() ? node.getOpacity() : 0.0);
        }
        return opacities;
    }

    private static MouseEvent mouse(EventType<MouseEvent> type) {
        return new MouseEvent(type, 1.0, 1.0, 1.0, 1.0, MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, false, null);
    }

    private static RXCarousel showingCarousel(int count) {
        List<Node> pages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pages.add(new Label("P" + i));
        }
        RXCarousel carousel = new RXCarousel();
        carousel.setPages(pages.toArray(new Node[0]));
        carousel.setHoverPause(false);
        Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane(carousel), 320.0, 200.0));
        stage.show();
        return carousel;
    }

    private static void onFx(Runnable body) throws Exception {
        FutureTask<Void> task = new FutureTask<>(body, null);
        Platform.runLater(task);
        try {
            task.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}
