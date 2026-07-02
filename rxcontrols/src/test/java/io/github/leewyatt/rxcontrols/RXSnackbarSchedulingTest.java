package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXSnackbarEvent;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for snackbar scheduling beyond plain FIFO — the REPLACE
 * strategy with its action protection, same-key in-place updates (displayed and
 * queued) with the fixed event order, duplicate prevention by key and by
 * message, severity style classes on the bar, and the {@code RXSnackbars}
 * facade (owner-scene resolution, no-scene settlement, host caching, and the
 * {@code installInto} escape hatch).
 */
public class RXSnackbarSchedulingTest {

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

    // ==================== REPLACE strategy ====================

    @Test
    public void replacePreemptsPlainDisplayedBar() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            host.setStrategy(RXSnackbarStrategy.REPLACE);
            List<String> log = new ArrayList<>();
            RXSnackbarRequest a = request("a", log);
            RXSnackbarRequest b = request("b", log);
            host.show(a);
            host.show(b);
            assertEquals(List.of("cb:a:REPLACED"), log, "the displayed bar settles as REPLACED");
            assertSame(b, host.getCurrentRequest(), "the replacement displays immediately");
        });
    }

    @Test
    public void replaceJumpsAheadOfQueuedRequests() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            RXSnackbarRequest a = RXSnackbarRequest.builder("a").build();
            RXSnackbarRequest queued = RXSnackbarRequest.builder("queued").build();
            RXSnackbarRequest urgent = RXSnackbarRequest.builder("urgent")
                    .strategy(RXSnackbarStrategy.REPLACE).build();
            host.show(a);
            host.show(queued);
            host.show(urgent);
            assertSame(urgent, host.getCurrentRequest(), "the replacement outranks the queue");
            host.dismiss();
            assertSame(queued, host.getCurrentRequest());
        });
    }

    @Test
    public void replaceDoesNotPreemptActionableAutoHidingBar() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            host.setStrategy(RXSnackbarStrategy.REPLACE);
            RXSnackbarRequest actionable = RXSnackbarRequest.builder("undoable")
                    .action("Undo", () -> {
                    }).build();
            RXSnackbarRequest late = RXSnackbarRequest.builder("late").build();
            host.show(actionable);
            host.show(late);
            assertSame(actionable, host.getCurrentRequest(),
                    "an actionable, auto-hiding bar keeps its reading time");
            host.dismiss();
            assertSame(late, host.getCurrentRequest(), "the new request fell back to the queue");
        });
    }

    @Test
    public void replacePreemptsPersistentActionableBar() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            host.setStrategy(RXSnackbarStrategy.REPLACE);
            List<String> log = new ArrayList<>();
            RXSnackbarRequest persistent = RXSnackbarRequest.builder("stuck")
                    .duration(Duration.INDEFINITE)
                    .action("Retry", () -> {
                    })
                    .onDismissed((request, reason) -> log.add("stuck:" + reason))
                    .build();
            RXSnackbarRequest late = RXSnackbarRequest.builder("late").build();
            host.show(persistent);
            host.show(late);
            assertEquals(List.of("stuck:REPLACED"), log,
                    "a persistent bar never blocks the queue, action or not");
            assertSame(late, host.getCurrentRequest());
        });
    }

    // ==================== Same-key in-place update ====================

    @Test
    public void sameKeyUpdatesDisplayedBarWithFixedEventOrder() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            List<String> events = new ArrayList<>();
            host.addEventHandler(RXSnackbarEvent.ANY, event -> events.add(
                    event.getEventType().getName() + ":" + event.getRequest().getMessage()
                            + (event.getReason() == null ? "" : ":" + event.getReason())));
            RXSnackbarRequest loading = RXSnackbarRequest.builder("loading").key("job").build();
            RXSnackbarRequest done = RXSnackbarRequest.builder("done").key("job").build();
            host.show(loading);
            events.clear();
            host.show(done);
            assertEquals(List.of(
                    "RX_SNACKBAR_DISMISSED:loading:REPLACED",
                    "RX_SNACKBAR_SHOWING:done",
                    "RX_SNACKBAR_SHOWN:done"), events, "old settles, then the new announces, in order");
            assertSame(done, host.getCurrentRequest());
            Label message = (Label) host.lookup(".message");
            assertEquals("done", message.getText(), "the bar content swapped in place");
            assertTrue(host.isShowing());
        });
    }

    @Test
    public void sameKeyUpdatesQueuedRequestInPlace() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            List<String> log = new ArrayList<>();
            RXSnackbarRequest a = RXSnackbarRequest.builder("a").build();
            RXSnackbarRequest oldQueued = RXSnackbarRequest.builder("old").key("job")
                    .onDismissed((request, reason) -> log.add("old:" + reason)).build();
            RXSnackbarRequest newQueued = RXSnackbarRequest.builder("new").key("job").build();
            host.show(a);
            host.show(oldQueued);
            host.show(newQueued);
            assertEquals(List.of("old:REPLACED"), log, "the queued request settles as REPLACED");
            host.dismiss();
            assertSame(newQueued, host.getCurrentRequest(), "the update kept the queue position");
        });
    }

    @Test
    public void reentrantSameKeyShowFromReplacedCallbackSettlesExactlyOnce() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            List<String> events = new ArrayList<>();
            host.addEventHandler(RXSnackbarEvent.DISMISSED, event ->
                    events.add("dismissed:" + event.getRequest().getMessage()));
            AtomicInteger callbackRuns = new AtomicInteger();
            RXSnackbarRequest b2 = RXSnackbarRequest.builder("b2").key("job").build();
            RXSnackbarRequest a = RXSnackbarRequest.builder("a").key("job")
                    .onDismissed((request, reason) -> {
                        callbackRuns.incrementAndGet();
                        // Re-enter with the same key while A is being settled: it
                        // must queue up, never match A again.
                        host.show(b2);
                    }).build();
            RXSnackbarRequest b = RXSnackbarRequest.builder("b").key("job").build();
            host.show(a);
            host.show(b);
            assertEquals(1, callbackRuns.get(), "A settles exactly once");
            assertEquals(List.of("dismissed:a"), events, "one DISMISSED for A, none fabricated");
            assertSame(b, host.getCurrentRequest(), "the outer update wins the slot");
            host.dismiss();
            assertSame(b2, host.getCurrentRequest(), "the re-entrant request queued up, not lost");
        });
    }

    @Test
    public void reentrantReplaceFromReplacedCallbackDoesNotWedgeTheGate() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            RXSnackbarRequest b2 = RXSnackbarRequest.builder("b2")
                    .strategy(RXSnackbarStrategy.REPLACE).build();
            RXSnackbarRequest a = RXSnackbarRequest.builder("a").key("job")
                    .onDismissed((request, reason) -> host.show(b2)).build();
            RXSnackbarRequest b = RXSnackbarRequest.builder("b").key("job").build();
            host.show(a);
            host.show(b);
            assertSame(b, host.getCurrentRequest());
            host.dismiss();
            assertSame(b2, host.getCurrentRequest(), "the re-entrant REPLACE queued instead of preempting");
            host.dismiss();
            assertFalse(host.isShowing(), "the gate never wedged; dismissals still work");
        });
    }

    // ==================== Duplicate prevention ====================

    @Test
    public void preventDuplicateRejectsMatchingMessage() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            host.setPreventDuplicate(true);
            List<String> log = new ArrayList<>();
            RXSnackbarRequest first = RXSnackbarRequest.builder("saved").build();
            RXSnackbarRequest dup = RXSnackbarRequest.builder("saved")
                    .onDismissed((request, reason) -> log.add("dup:" + reason)).build();
            host.show(first);
            host.show(dup);
            assertEquals(List.of("dup:DUPLICATE"), log, "the newcomer is rejected, never displayed");
            assertSame(first, host.getCurrentRequest());

            RXSnackbarRequest queued = RXSnackbarRequest.builder("pending").build();
            RXSnackbarRequest queuedDup = RXSnackbarRequest.builder("pending")
                    .onDismissed((request, reason) -> log.add("qdup:" + reason)).build();
            host.show(queued);
            host.show(queuedDup);
            assertEquals(List.of("dup:DUPLICATE", "qdup:DUPLICATE"), log,
                    "queued requests count for matching too");
        });
    }

    @Test
    public void keyUpdateWinsOverDuplicatePrevention() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            host.setPreventDuplicate(true);
            RXSnackbarRequest loading = RXSnackbarRequest.builder("working").key("job").build();
            RXSnackbarRequest update = RXSnackbarRequest.builder("working").key("job").build();
            host.show(loading);
            host.show(update);
            assertSame(update, host.getCurrentRequest(),
                    "a key hit updates in place instead of being swallowed as a duplicate");
        });
    }

    @Test
    public void distinctKeysAreNeverDuplicates() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = new RXSnackbarHost();
            host.setPreventDuplicate(true);
            RXSnackbarRequest first = RXSnackbarRequest.builder("same text").key("k1").build();
            RXSnackbarRequest second = RXSnackbarRequest.builder("same text").key("k2").build();
            host.show(first);
            host.show(second);
            host.dismiss();
            assertSame(second, host.getCurrentRequest(),
                    "a keyed request carries its own identity; message text does not matter");
        });
    }

    // ==================== Severity style hook ====================

    @Test
    public void severityAddsStyleClassAndPseudoClassOnlyWhenNotNone() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            host.show(RXSnackbarRequest.builder("plain").build());
            javafx.scene.Node bar = host.lookup(".snackbar");
            assertNotNull(bar);
            assertTrue(bar.getStyleClass().stream().noneMatch(c -> c.startsWith("rx-snackbar-")),
                    "NONE adds no severity class");

            host.clear();
            host.show(RXSnackbarRequest.builder("err").severity(RXSnackbarSeverity.ERROR).build());
            assertTrue(bar.getStyleClass().contains("rx-snackbar-error"));
            assertTrue(bar.getPseudoClassStates().stream()
                    .anyMatch(p -> "error".equals(p.getPseudoClassName())));

            host.clear();
            host.show(RXSnackbarRequest.builder("ok").severity(RXSnackbarSeverity.SUCCESS).build());
            assertTrue(bar.getStyleClass().contains("rx-snackbar-success"));
            assertFalse(bar.getStyleClass().contains("rx-snackbar-error"), "severity classes swap cleanly");
        });
    }

    // ==================== RXSnackbars facade ====================

    @Test
    public void facadeResolvesAndCachesPerSceneHost() throws Exception {
        runOnFx(() -> {
            StackPane root = new StackPane();
            Scene scene = new Scene(root, 400.0, 300.0);
            Label owner = new Label("owner");
            root.getChildren().add(owner);

            assertTrue(RXSnackbars.hostFor(owner).isEmpty(), "no host before first use");
            RXSnackbars.show(owner, "first");
            RXSnackbarHost host = RXSnackbars.hostFor(owner).orElseThrow();
            assertTrue(host.isShowing());
            assertSame(scene, host.getScene(), "the host lives in the owner's scene");

            RXSnackbars.show(owner, "second");
            assertSame(host, RXSnackbars.hostFor(owner).orElseThrow(), "one host per scene");
        });
    }

    @Test
    public void facadeWithoutSceneIsNoOpAndSettlesCallback() throws Exception {
        runOnFx(() -> {
            Label detached = new Label("nowhere");
            RXSnackbars.show(detached, "quiet");
            RXSnackbars.success(detached, "quiet");

            List<String> log = new ArrayList<>();
            RXSnackbars.show(detached, RXSnackbarRequest.builder("observed")
                    .onDismissed((request, reason) -> log.add("cb:" + reason)).build());
            assertEquals(List.of("cb:DISCARDED"), log,
                    "a never-accepted request settles its callback immediately");
            assertFalse(RXSnackbars.dismiss(detached, "any"));
        });
    }

    @Test
    public void facadeDismissByKeyRoutesToTheSceneHost() throws Exception {
        runOnFx(() -> {
            StackPane root = new StackPane();
            new Scene(root, 400.0, 300.0);
            Label owner = new Label("owner");
            root.getChildren().add(owner);
            assertFalse(RXSnackbars.dismiss(owner, "job"), "no host installed yet");
            RXSnackbars.show(owner, RXSnackbarRequest.builder("working").key("job").build());
            assertTrue(RXSnackbars.dismiss(owner, "job"));
            assertFalse(RXSnackbars.hostFor(owner).orElseThrow().isShowing());
        });
    }

    @Test
    public void installIntoIsIdempotentPerContainerAndOutsideTheSceneCache() throws Exception {
        runOnFx(() -> {
            Pane container = new Pane();
            RXSnackbarHost installed = RXSnackbars.installInto(container);
            assertSame(installed, RXSnackbars.installInto(container), "idempotent per container");
            assertSame(container, installed.getParent());

            StackPane root = new StackPane(container);
            new Scene(root, 400.0, 300.0);
            Label owner = new Label("owner");
            root.getChildren().add(owner);
            RXSnackbars.show(owner, "scene level");
            RXSnackbarHost sceneHost = RXSnackbars.hostFor(owner).orElseThrow();
            assertNotSame(installed, sceneHost, "the facade never routes to an installInto host");

            container.getChildren().remove(installed);
            RXSnackbarHost reinstalled = RXSnackbars.installInto(container);
            assertNotSame(installed, reinstalled, "a caller-removed host is not resurrected");
            assertSame(container, reinstalled.getParent());
        });
    }

    // ==================== Helpers ====================

    private static RXSnackbarHost skinnedHost() {
        RXSnackbarHost host = new RXSnackbarHost();
        host.setAnimated(false);
        StackPane root = new StackPane(host);
        new Scene(root, 400.0, 300.0);
        host.applyCss();
        if (host.getSkin() == null) {
            throw new AssertionError("skin was not created");
        }
        return host;
    }

    private static RXSnackbarRequest request(String message, List<String> log) {
        return RXSnackbarRequest.builder(message)
                .onDismissed((request, reason) -> log.add("cb:" + message + ":" + reason))
                .build();
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
