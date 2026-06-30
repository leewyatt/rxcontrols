package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.DrawerEvent;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the PR2 surface of {@link RXDrawerPane}: the {@link DrawerEvent}
 * lifecycle (OPENING/OPENED/CLOSING/CLOSED), the vetoable {@code CLOSE_REQUEST}
 * across all close paths, and the {@code onXxx} handler properties.
 */
public class DrawerEventTest {

    private static final double WIDTH = 400.0;
    private static final double HEIGHT = 300.0;
    private static final double THICKNESS = 200.0;

    /**
     * Starts the JavaFX toolkit so the skin and {@code Timeline.play()} can run.
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

    // ==================== Lifecycle order ====================

    @Test
    public void snapOpenCloseFiresFullLifecycleInOrder() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            List<String> log = recordEvents(pane);
            attach(pane);

            pane.open();
            pane.close();

            assertEquals(List.of(
                    "OPENING", "OPENED",
                    "CLOSE_REQUEST", "CLOSING", "CLOSED"), log);
        });
    }

    @Test
    public void onXxxConvenienceHandlersFireInOrder() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            attach(pane);

            List<String> calls = new ArrayList<>();
            pane.setOnOpening(e -> calls.add("opening"));
            pane.setOnOpened(e -> calls.add("opened"));
            pane.setOnCloseRequest(e -> calls.add("request"));
            pane.setOnClosing(e -> calls.add("closing"));
            pane.setOnClosed(e -> calls.add("closed"));

            pane.open();
            pane.close();

            assertEquals(List.of("opening", "opened", "request", "closing", "closed"), calls);
        });
    }

    @Test
    public void animatedOpenFiresOpeningImmediatelyThenOpened() throws Exception {
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch opened = new CountDownLatch(1);
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setPrefDrawerWidth(THICKNESS);
            pane.setAnimationDuration(Duration.millis(60.0));
            pane.addEventHandler(DrawerEvent.ANY, e -> log.add(e.getEventType().getName()));
            attach(pane);
            pane.addEventHandler(DrawerEvent.OPENED, e -> opened.countDown());

            pane.open();
            // OPENING fires the instant the slide starts; OPENED waits for the Timeline.
            assertEquals(List.of("OPENING"), List.copyOf(log));
        });
        assertTrue(opened.await(3, TimeUnit.SECONDS), "animated open reaches OPENED");
        runOnFx(() -> assertEquals(List.of("OPENING", "OPENED"), List.copyOf(log)));
    }

    // ==================== CLOSE_REQUEST veto ====================

    @Test
    public void closeRequestVetoKeepsOpenAndSuppressesClosing() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            List<String> log = recordEvents(pane);
            attach(pane);
            pane.open();
            pane.setOnCloseRequest(Event::consume);
            log.clear();

            pane.close();

            assertTrue(pane.isShowing(), "vetoed close stays open");
            assertEquals(List.of("CLOSE_REQUEST"), log, "no CLOSING/CLOSED after veto");
        });
    }

    @Test
    public void toggleCloseHonoursVeto() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            attach(pane);
            pane.open();
            pane.setOnCloseRequest(Event::consume);

            pane.toggle();
            assertTrue(pane.isShowing(), "toggle-close is vetoable too");
        });
    }

    @Test
    public void closeWhenClosedFiresNoCloseRequest() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            List<String> log = recordEvents(pane);

            pane.close();
            assertTrue(log.isEmpty(), "closing an already-closed drawer is a silent no-op");
        });
    }

    // ==================== No spurious events ====================

    @Test
    public void sceneRemovalWhileOpenFiresNothing() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            List<String> log = recordEvents(pane);
            Scene scene = new Scene(pane);
            pane.resize(WIDTH, HEIGHT);
            pane.applyCss();
            pane.layout();

            pane.open();
            log.clear();
            // Already OPEN (no transition in flight): settling on detach is silent.
            scene.setRoot(new Region());
            assertTrue(log.isEmpty(), "no lifecycle events on detach when already open");
        });
    }

    @Test
    public void sideChangeMidSlideSettlesWithoutLeavingStaleFlag() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setPrefDrawerWidth(THICKNESS);
            pane.setAnimationDuration(Duration.millis(500.0));
            List<String> log = recordEvents(pane);
            Scene scene = new Scene(pane);
            pane.resize(WIDTH, HEIGHT);
            pane.applyCss();
            pane.layout();

            pane.open();
            assertEquals(List.of("OPENING"), log, "OPENING while the slide is animating");
            // A side change mid-slide settles the open exactly once; it must not leave a
            // stale inFlight flag that a later detach would fire as a spurious OPENED.
            pane.setSide(Side.LEFT);
            assertEquals(List.of("OPENING", "OPENED"), log, "side change settles the open");
            log.clear();
            scene.setRoot(new Region());
            assertTrue(log.isEmpty(), "no spurious event on detach after a side change");
        });
    }

    // ==================== Helpers ====================

    private static List<String> recordEvents(RXDrawerPane pane) {
        List<String> log = new ArrayList<>();
        pane.addEventHandler(DrawerEvent.ANY,
                e -> log.add(e.getEventType().getName()));
        return log;
    }

    private static void attach(RXDrawerPane pane) {
        new Scene(pane);
        pane.resize(WIDTH, HEIGHT);
        pane.applyCss();
        pane.layout();
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
