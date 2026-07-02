package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXSnackbarEvent;
import io.github.leewyatt.rxcontrols.RXSnackbarHost.DismissReason;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless model tests for {@link RXSnackbarHost}: property defaults and
 * contracts, the FIFO queue with its bound, the single dismiss gate
 * (exactly-once settlement, command-to-reason mapping, re-entrancy and stray-hook
 * guards), effective-value helpers including the persistent close-icon guard, and
 * scene-detach cleanup. No skin exists in these tests, so transitions settle
 * synchronously; animation and interaction are real-device checks.
 */
public class RXSnackbarHostTest {

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

    // ==================== Defaults & property contracts ====================

    @Test
    public void defaultsAreSensible() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            assertFalse(host.isShowing());
            assertNull(host.getCurrentRequest());
            assertEquals(RXSnackbarStrategy.QUEUE, host.getStrategy());
            assertEquals(Pos.BOTTOM_LEFT, host.getPosition());
            assertEquals(new Insets(24.0), host.getMargin());
            assertEquals(568.0, host.getSnackbarMaxWidth());
            assertEquals(Duration.seconds(4.0), host.getDefaultDuration());
            assertEquals(5, host.getMaxQueueSize());
            assertFalse(host.isPreventDuplicate());
            assertTrue(host.isAnimated());
            assertEquals(Duration.millis(250.0), host.getAnimationDuration());
            assertFalse(host.isFocusTraversable());
            assertTrue(host.getStyleClass().contains("rx-snackbar-host"));
        });
    }

    @Test
    public void showRejectsNullRequest() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            assertThrows(NullPointerException.class, () -> host.show((RXSnackbarRequest) null));
        });
    }

    @Test
    public void requestBuilderSnapshotsAndNormalizes() throws Exception {
        runOnFx(() -> {
            Runnable handler = () -> {
            };
            RXSnackbarRequest request = RXSnackbarRequest.builder("hello")
                    .severity(null)
                    .action("Undo", handler)
                    .key("k1")
                    .build();
            assertEquals("hello", request.getMessage());
            assertEquals(RXSnackbarSeverity.NONE, request.getSeverity(), "null severity normalizes to NONE");
            assertTrue(request.hasAction());
            assertEquals("Undo", request.getActionLabel());
            assertSame(handler, request.getActionHandler());
            assertNull(request.getDuration());
            assertFalse(request.isShowCloseIcon());
            assertEquals("k1", request.getKey());
        });
    }

    @Test
    public void styleablePropertiesAreExposedAndCssSettable() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            List<String> names = new ArrayList<>();
            for (CssMetaData<?, ?> metaData : RXSnackbarHost.getClassCssMetaData()) {
                names.add(metaData.getProperty());
            }
            assertTrue(names.contains("-rx-animated"));
            assertTrue(names.contains("-rx-animation-duration"));
            assertTrue(names.contains("-rx-snackbar-margin"));
            assertTrue(names.contains("-rx-snackbar-max-width"));

            StackPane root = new StackPane(host);
            new Scene(root, 400.0, 300.0);
            host.setStyle("-rx-snackbar-max-width: 300; -rx-animated: false; "
                    + "-rx-snackbar-margin: 8; -rx-animation-duration: 100ms;");
            host.applyCss();
            assertEquals(300.0, host.getSnackbarMaxWidth());
            assertFalse(host.isAnimated());
            assertEquals(new Insets(8.0), host.getMargin());
            assertEquals(Duration.millis(100.0), host.getAnimationDuration());
        });
    }

    // ==================== FIFO queue ====================

    @Test
    public void queueIsFifoAndAdvancesOnDismiss() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            RXSnackbarRequest a = RXSnackbarRequest.builder("a").build();
            RXSnackbarRequest b = RXSnackbarRequest.builder("b").build();
            RXSnackbarRequest c = RXSnackbarRequest.builder("c").build();
            host.show(a);
            host.show(b);
            host.show(c);
            assertSame(a, host.getCurrentRequest());
            assertTrue(host.isShowing());
            host.dismiss();
            assertSame(b, host.getCurrentRequest());
            assertTrue(host.isShowing());
            host.dismiss();
            assertSame(c, host.getCurrentRequest());
            host.dismiss();
            assertNull(host.getCurrentRequest());
            assertFalse(host.isShowing());
        });
    }

    @Test
    public void overflowDropsOldestQueuedWithDiscarded() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            host.setMaxQueueSize(2);
            List<String> discarded = new ArrayList<>();
            RXSnackbarRequest a = RXSnackbarRequest.builder("a").build();
            RXSnackbarRequest b = RXSnackbarRequest.builder("b")
                    .onDismissed((request, reason) -> discarded.add("b:" + reason)).build();
            RXSnackbarRequest c = RXSnackbarRequest.builder("c").build();
            RXSnackbarRequest d = RXSnackbarRequest.builder("d").build();
            host.show(a);
            host.show(b);
            host.show(c);
            host.show(d);
            assertEquals(List.of("b:DISCARDED"), discarded, "oldest queued item is dropped");
            assertSame(a, host.getCurrentRequest(), "the displayed bar is never dropped by overflow");
            host.dismiss();
            assertSame(c, host.getCurrentRequest());
            host.dismiss();
            assertSame(d, host.getCurrentRequest());
        });
    }

    @Test
    public void nonPositiveMaxQueueSizeFallsBackToDefaultAtUseSite() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            host.setMaxQueueSize(0);
            assertEquals(0, host.getMaxQueueSize(), "the property itself is never rewritten");
            AtomicInteger discardedCount = new AtomicInteger();
            host.show(RXSnackbarRequest.builder("current").build());
            for (int i = 0; i < 7; i++) {
                host.show(RXSnackbarRequest.builder("q" + i)
                        .onDismissed((request, reason) -> {
                            if (reason == DismissReason.DISCARDED) {
                                discardedCount.incrementAndGet();
                            }
                        }).build());
            }
            // Default bound 5: seven enqueues overflow twice.
            assertEquals(2, discardedCount.get());
        });
    }

    // ==================== Exactly-once settlement & reason mapping ====================

    @Test
    public void everyRemovalPathSettlesExactlyOnceWithPairedEvent() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            host.setMaxQueueSize(1);
            List<String> log = new ArrayList<>();
            host.addEventHandler(RXSnackbarEvent.DISMISSED,
                    event -> log.add("event:" + event.getRequest().getMessage() + ":" + event.getReason()));

            RXSnackbarRequest a = request("a", log);
            RXSnackbarRequest b = request("b", log);
            RXSnackbarRequest c = request("c", log);
            host.show(a);       // displayed
            host.show(b);       // queued
            host.show(c);       // overflows the bound of 1 -> b DISCARDED
            assertEquals(List.of("cb:b:DISCARDED", "event:b:DISCARDED"), log,
                    "callback first, then host event");
            log.clear();

            host.dismiss();     // a -> PROGRAMMATIC, c promoted
            assertEquals(List.of("cb:a:PROGRAMMATIC", "event:a:PROGRAMMATIC"), log);
            log.clear();

            host.clear();       // c (displayed) -> PROGRAMMATIC
            assertEquals(List.of("cb:c:PROGRAMMATIC", "event:c:PROGRAMMATIC"), log);
            assertFalse(host.isShowing());
        });
    }

    @Test
    public void dismissByKeyMapsCurrentVsQueuedReasons() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            List<String> log = new ArrayList<>();
            RXSnackbarRequest a = RXSnackbarRequest.builder("a").key("ka")
                    .onDismissed((request, reason) -> log.add("a:" + reason)).build();
            RXSnackbarRequest b = RXSnackbarRequest.builder("b").key("kb")
                    .onDismissed((request, reason) -> log.add("b:" + reason)).build();
            host.show(a);
            host.show(b);
            assertTrue(host.dismiss("kb"), "queued match");
            assertEquals(List.of("b:DISCARDED"), log);
            log.clear();
            assertTrue(host.dismiss("ka"), "displayed match");
            assertEquals(List.of("a:PROGRAMMATIC"), log);
            assertFalse(host.dismiss("missing"));
            assertFalse(host.dismiss((String) null));
        });
    }

    @Test
    public void clearDiscardsQueueThenClosesCurrent() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            List<String> log = new ArrayList<>();
            host.show(request("a", log));
            host.show(request("b", log));
            host.show(request("c", log));
            host.clear();
            assertEquals(List.of("cb:b:DISCARDED", "cb:c:DISCARDED", "cb:a:PROGRAMMATIC"), log,
                    "queued settle as DISCARDED before the displayed bar closes as PROGRAMMATIC");
            assertFalse(host.isShowing());
            assertNull(host.getCurrentRequest());
        });
    }

    // ==================== Gate guards ====================

    @Test
    public void reentrantAndDoubleDismissTriggersSettleOnce() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            AtomicInteger settlements = new AtomicInteger();
            RXSnackbarRequest a = RXSnackbarRequest.builder("a")
                    .onDismissed((request, reason) -> {
                        settlements.incrementAndGet();
                        // Re-entrant dismissal from the callback must be a no-op.
                        host.dismiss();
                        host.requestDismiss(DismissReason.TIMEOUT);
                    }).build();
            host.show(a);
            // Same-frame double trigger: only the first wins.
            host.requestDismiss(DismissReason.TIMEOUT);
            host.requestDismiss(DismissReason.ACTION);
            assertEquals(1, settlements.get());
        });
    }

    @Test
    public void showFromDismissedCallbackQueuesInOrder() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            RXSnackbarRequest x = RXSnackbarRequest.builder("x").build();
            RXSnackbarRequest a = RXSnackbarRequest.builder("a")
                    .onDismissed((request, reason) -> host.show(x)).build();
            RXSnackbarRequest b = RXSnackbarRequest.builder("b").build();
            host.show(a);
            host.show(b);
            host.dismiss();
            // b was queued before the callback showed x, so b displays first.
            assertSame(b, host.getCurrentRequest());
            host.dismiss();
            assertSame(x, host.getCurrentRequest());
        });
    }

    @Test
    public void strayHooksAreNoOps() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            List<String> events = new ArrayList<>();
            host.addEventHandler(RXSnackbarEvent.ANY, event -> events.add(event.getEventType().getName()));
            host.notifyShown();
            host.notifyDismissed();
            host.requestDismiss(DismissReason.TIMEOUT);
            assertEquals(List.of(), events, "no in-flight transition, so no event is fabricated");

            host.show(RXSnackbarRequest.builder("a").build());
            events.clear();
            host.notifyShown();      // enter already completed (skinless -> synchronous)
            host.notifyDismissed();  // no exit in flight
            assertEquals(List.of(), events);
        });
    }

    // ==================== Lifecycle events & showing truth ====================

    @Test
    public void lifecycleEventOrderAcrossQueueAdvancement() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            List<String> events = new ArrayList<>();
            host.addEventHandler(RXSnackbarEvent.ANY, event -> events.add(
                    event.getEventType().getName() + ":" + event.getRequest().getMessage()));
            RXSnackbarRequest a = RXSnackbarRequest.builder("a").build();
            RXSnackbarRequest b = RXSnackbarRequest.builder("b").build();
            host.show(a);
            host.show(b);
            host.dismiss();
            assertEquals(List.of(
                    "RX_SNACKBAR_SHOWING:a", "RX_SNACKBAR_SHOWN:a",
                    "RX_SNACKBAR_DISMISSED:a",
                    "RX_SNACKBAR_SHOWING:b", "RX_SNACKBAR_SHOWN:b"), events);
        });
    }

    @Test
    public void showingStaysTrueAcrossAdvancementAndFlipsPseudoClass() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            List<Boolean> transitions = new ArrayList<>();
            host.showingProperty().addListener((observable, was, is) -> transitions.add(is));
            host.show(RXSnackbarRequest.builder("a").build());
            host.show(RXSnackbarRequest.builder("b").build());
            host.dismiss();  // a leaves, b promotes: showing never flickers false
            host.dismiss();  // b leaves, queue empty
            assertEquals(List.of(true, false), transitions);
            assertFalse(host.getPseudoClassStates().stream()
                    .anyMatch(pseudoClass -> "showing".equals(pseudoClass.getPseudoClassName())));
        });
    }

    // ==================== Effective-value helpers (persistent guard) ====================

    @Test
    public void effectiveDurationInheritsHostDefaultOnlyForNull() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            RXSnackbarRequest inherit = RXSnackbarRequest.builder("a").build();
            RXSnackbarRequest explicit = RXSnackbarRequest.builder("b").duration(Duration.seconds(10.0)).build();
            RXSnackbarRequest persistent = RXSnackbarRequest.builder("c").duration(Duration.INDEFINITE).build();
            assertEquals(Duration.seconds(4.0), host.effectiveDuration(inherit));
            assertEquals(Duration.seconds(10.0), host.effectiveDuration(explicit));
            assertEquals(Duration.INDEFINITE, host.effectiveDuration(persistent));
            host.setDefaultDuration(null);
            assertNull(host.effectiveDuration(inherit), "null default -> inherited requests are persistent");
        });
    }

    @Test
    public void persistentGuardForcesCloseIconWithoutRewritingRequest() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();

            RXSnackbarRequest plain = RXSnackbarRequest.builder("plain").build();
            assertFalse(host.effectiveShowCloseIcon(plain),
                    "inheriting the 4s default is not persistent; no forced icon");

            RXSnackbarRequest persistentBare = RXSnackbarRequest.builder("p").duration(Duration.INDEFINITE).build();
            assertTrue(host.effectiveShowCloseIcon(persistentBare), "persistent without affordance forces the icon");
            assertFalse(persistentBare.isShowCloseIcon(), "the request value is never rewritten");

            RXSnackbarRequest persistentWithAction = RXSnackbarRequest.builder("p").duration(Duration.INDEFINITE)
                    .action("Undo", () -> {
                    }).build();
            assertFalse(host.effectiveShowCloseIcon(persistentWithAction), "an action is a way out");

            RXSnackbarRequest zero = RXSnackbarRequest.builder("z").duration(Duration.ZERO).build();
            assertTrue(host.effectiveShowCloseIcon(zero), "non-positive duration is persistent");

            RXSnackbarRequest unknown = RXSnackbarRequest.builder("u").duration(Duration.UNKNOWN).build();
            assertTrue(host.effectiveShowCloseIcon(unknown), "unknown duration is persistent");

            host.setDefaultDuration(null);
            assertTrue(host.effectiveShowCloseIcon(plain),
                    "persistent host default makes inheriting requests persistent");

            RXSnackbarRequest asked = RXSnackbarRequest.builder("asked").showCloseIcon(true).build();
            host.setDefaultDuration(Duration.seconds(4.0));
            assertTrue(host.effectiveShowCloseIcon(asked), "an explicitly requested icon always shows");
        });
    }

    // ==================== Scene detach & reference release ====================

    @Test
    public void sceneDetachSettlesEverything() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            StackPane root = new StackPane(host);
            new Scene(root, 400.0, 300.0);
            List<String> log = new ArrayList<>();
            host.show(request("a", log));
            host.show(request("b", log));
            assertTrue(host.isShowing());
            root.getChildren().remove(host);
            assertEquals(List.of("cb:b:DISCARDED", "cb:a:PROGRAMMATIC"), log);
            assertFalse(host.isShowing());
            assertNull(host.getCurrentRequest());
        });
    }

    @Test
    public void clearReleasesRequestReferences() throws Exception {
        AtomicReference<WeakReference<RXSnackbarRequest>> probe = new AtomicReference<>();
        RXSnackbarHost host = new RXSnackbarHost();
        runOnFx(() -> {
            host.show(RXSnackbarRequest.builder("current").build());
            RXSnackbarRequest queued = RXSnackbarRequest.builder("queued").build();
            host.show(queued);
            probe.set(new WeakReference<>(queued));
            host.clear();
        });
        assertReclaimable(probe.get());
    }

    // ==================== Helpers ====================

    private static RXSnackbarRequest request(String message, List<String> log) {
        return RXSnackbarRequest.builder(message)
                .onDismissed((request, reason) -> log.add("cb:" + message + ":" + reason))
                .build();
    }

    private static void assertReclaimable(WeakReference<?> reference) throws InterruptedException {
        for (int i = 0; i < 50 && reference.get() != null; i++) {
            System.gc();
            Thread.sleep(10L);
        }
        assertNull(reference.get(), "the request must be unreachable after clear()");
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
