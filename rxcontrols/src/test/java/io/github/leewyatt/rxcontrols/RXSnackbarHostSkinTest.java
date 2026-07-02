package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXSnackbarEvent;
import io.github.leewyatt.rxcontrols.skins.RXSnackbarHostSkin;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for {@link RXSnackbarHostSkin}: the host-skin handshake through
 * real skin transitions — synchronous snap paths (animated off / degenerate
 * duration), asynchronous animated paths, mid-flight reversal settling exactly
 * once, the auto-hide timer staying gated while the tree is not showing, scene
 * detach cleanup, and settling a transition orphaned by a skin swap. Animation
 * look, slide direction, hover / focus / minimize pause feel, and timer expiry in
 * a focused window are real-device checks.
 */
public class RXSnackbarHostSkinTest {

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

    // ==================== Snap paths (no animation) ====================

    @Test
    public void snapPathRunsHandshakeSynchronouslyThroughSkin() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            host.setAnimated(false);
            List<String> events = recordEvents(host);
            host.show(RXSnackbarRequest.builder("a").build());
            assertEquals(List.of("RX_SNACKBAR_SHOWING:a", "RX_SNACKBAR_SHOWN:a"), events);
            events.clear();
            host.dismiss();
            assertEquals(List.of("RX_SNACKBAR_DISMISSED:a"), events);
            assertFalse(host.isShowing());
        });
    }

    @Test
    public void degenerateAnimationDurationSnaps() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            host.setAnimationDuration(null);
            List<String> events = recordEvents(host);
            host.show(RXSnackbarRequest.builder("a").build());
            assertEquals(List.of("RX_SNACKBAR_SHOWING:a", "RX_SNACKBAR_SHOWN:a"), events);

            host.setAnimationDuration(Duration.ZERO);
            events.clear();
            host.dismiss();
            assertEquals(List.of("RX_SNACKBAR_DISMISSED:a"), events);
        });
    }

    @Test
    public void queueAdvancesThroughSkinWithoutAnimation() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            host.setAnimated(false);
            RXSnackbarRequest a = RXSnackbarRequest.builder("a").build();
            RXSnackbarRequest b = RXSnackbarRequest.builder("b").build();
            host.show(a);
            host.show(b);
            assertSame(a, host.getCurrentRequest());
            host.dismiss();
            assertSame(b, host.getCurrentRequest());
            assertTrue(host.isShowing());
        });
    }

    // ==================== Animated paths ====================

    @Test
    public void animatedShowCompletesAsynchronously() throws Exception {
        RXSnackbarHost host = onFx(RXSnackbarHostSkinTest::skinnedHost);
        CountDownLatch shown = new CountDownLatch(1);
        AtomicReference<Boolean> shownSynchronously = new AtomicReference<>();
        runOnFx(() -> {
            host.setAnimationDuration(Duration.millis(40.0));
            host.addEventHandler(RXSnackbarEvent.SHOWN, event -> shown.countDown());
            host.show(RXSnackbarRequest.builder("a").build());
            shownSynchronously.set(shown.getCount() == 0);
        });
        assertFalse(shownSynchronously.get(), "an animated enter must not complete synchronously");
        assertTrue(shown.await(3, TimeUnit.SECONDS), "the enter transition completes");
    }

    @Test
    public void midFlightReversalSettlesExactlyOnce() throws Exception {
        RXSnackbarHost host = onFx(RXSnackbarHostSkinTest::skinnedHost);
        AtomicInteger dismissals = new AtomicInteger();
        CountDownLatch dismissed = new CountDownLatch(1);
        runOnFx(() -> {
            host.setAnimationDuration(Duration.millis(60.0));
            host.addEventHandler(RXSnackbarEvent.DISMISSED, event -> {
                dismissals.incrementAndGet();
                dismissed.countDown();
            });
            host.show(RXSnackbarRequest.builder("a").build());
            // Reverse while the enter is still animating: the open timeline is
            // superseded, the exit plays, and settlement happens exactly once.
            host.dismiss();
        });
        assertTrue(dismissed.await(3, TimeUnit.SECONDS), "the exit transition completes");
        // Give a stray duplicate settlement a chance to surface before asserting.
        Thread.sleep(150L);
        assertEquals(1, dismissals.get());
        runOnFx(() -> assertFalse(host.isShowing()));
    }

    // ==================== Auto-hide gating ====================

    @Test
    public void autoHideStaysGatedWhileTreeNotShowing() throws Exception {
        RXSnackbarHost host = onFx(() -> {
            RXSnackbarHost fresh = skinnedHost();
            fresh.setAnimated(false);
            fresh.setDefaultDuration(Duration.millis(50.0));
            fresh.show(RXSnackbarRequest.builder("a").build());
            return fresh;
        });
        // The scene has no window, so the tree is not showing and the window-focus
        // gate is closed: the 50ms timer must not run in the background.
        Thread.sleep(300L);
        runOnFx(() -> {
            assertTrue(host.isShowing(), "a background host never times its bar out");
            assertNotNull(host.getCurrentRequest());
        });
    }

    // ==================== Scene detach & skin swap ====================

    @Test
    public void sceneDetachWithSkinSettlesAndCleansUp() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            StackPane root = new StackPane(host);
            new Scene(root, 400.0, 300.0);
            host.applyCss();
            assertNotNull(host.getSkin());
            List<String> log = new ArrayList<>();
            host.show(RXSnackbarRequest.builder("a")
                    .onDismissed((request, reason) -> log.add("a:" + reason)).build());
            host.show(RXSnackbarRequest.builder("b")
                    .onDismissed((request, reason) -> log.add("b:" + reason)).build());
            root.getChildren().remove(host);
            assertEquals(List.of("b:DISCARDED", "a:PROGRAMMATIC"), log);
            assertFalse(host.isShowing());
            assertNull(host.getCurrentRequest());
        });
    }

    @Test
    public void skinSwapMidCloseSettlesOrphanedExit() throws Exception {
        RXSnackbarHost host = onFx(RXSnackbarHostSkinTest::skinnedHost);
        AtomicInteger dismissals = new AtomicInteger();
        runOnFx(() -> {
            host.setAnimated(false);
            host.show(RXSnackbarRequest.builder("a")
                    .onDismissed((request, reason) -> dismissals.incrementAndGet()).build());
            host.setAnimated(true);
            host.setAnimationDuration(Duration.minutes(10.0));
            // Start an exit that will never finish, then kill its skin mid-flight.
            host.dismiss();
            assertEquals(0, dismissals.get(), "the exit is still animating");
            RXSnackbarHostSkin old = (RXSnackbarHostSkin) host.getSkin();
            old.dispose();
            // A replacement skin settles the orphaned exit on construction.
            host.setSkin(new RXSnackbarHostSkin(host));
            assertEquals(1, dismissals.get());
            assertFalse(host.isShowing());
        });
    }

    // ==================== Helpers ====================

    private static RXSnackbarHost skinnedHost() {
        RXSnackbarHost host = new RXSnackbarHost();
        StackPane root = new StackPane(host);
        new Scene(root, 400.0, 300.0);
        host.applyCss();
        if (host.getSkin() == null) {
            throw new AssertionError("skin was not created");
        }
        return host;
    }

    private static List<String> recordEvents(RXSnackbarHost host) {
        List<String> events = new ArrayList<>();
        host.addEventHandler(RXSnackbarEvent.ANY, event -> events.add(
                event.getEventType().getName() + ":" + event.getRequest().getMessage()));
        return events;
    }

    private static <T> T onFx(Supplier<T> supplier) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        runOnFx(() -> result.set(supplier.get()));
        return result.get();
    }

    private static void runOnFx(Runnable action) throws Exception {
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
