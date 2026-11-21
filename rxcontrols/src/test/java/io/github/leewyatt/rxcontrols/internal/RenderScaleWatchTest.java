package io.github.leewyatt.rxcontrols.internal;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link RenderScaleWatch}: the action runs when the window's render scale changes
 * or the node moves to another window, and no longer once the watch is disposed.
 */
public class RenderScaleWatchTest {

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
     * Verifies the action follows the node's window: it runs on a render scale change on
     * either axis and when the node moves to another window, and stops after dispose.
     */
    @Test
    public void actionFollowsTheWindowUntilDisposed() throws Exception {
        runOnFx(() -> {
            Region node = new Region();
            StackPane first = new StackPane(node);
            Stage firstStage = new Stage();
            firstStage.setScene(new Scene(first));
            Stage secondStage = new Stage();
            StackPane second = new StackPane();
            secondStage.setScene(new Scene(second));

            AtomicInteger runs = new AtomicInteger();
            RenderScaleWatch watch = new RenderScaleWatch(node, runs::incrementAndGet);
            assertEquals(1, runs.get(), "runs once for the window it starts in");
            firstStage.setRenderScaleX(1.25);
            assertEquals(2, runs.get(), "runs on a horizontal scale change");
            firstStage.setRenderScaleY(1.25);
            assertEquals(3, runs.get(), "runs on a vertical scale change");
            // Moving the node leaves its scene for none and then joins the other one.
            second.getChildren().add(node);
            assertEquals(5, runs.get(), "runs as the node leaves its window and joins another");
            firstStage.setRenderScaleX(1.5);
            assertEquals(5, runs.get(), "no longer follows the window it left");
            secondStage.setRenderScaleX(1.5);
            assertEquals(6, runs.get(), "follows the window it moved to");

            watch.dispose();
            secondStage.setRenderScaleX(1.75);
            first.getChildren().add(node);
            firstStage.setRenderScaleX(2.0);
            assertEquals(6, runs.get(), "a disposed watch runs nothing");
        });
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
        if (!latch.await(5, TimeUnit.SECONDS)) {
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
