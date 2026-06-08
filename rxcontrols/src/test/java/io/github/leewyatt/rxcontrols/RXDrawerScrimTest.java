package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXDrawerEvent;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the PR4 scrim and ESC behavior of {@link RXDrawerPane}: scrim
 * visibility/opacity across open/close, {@code scrim}/{@code scrimOpacity}/
 * {@code dismissOnScrimClick}/{@code closeOnEsc}, scrim-click → SCRIM_CLICK close,
 * and ESC → ESC close, both vetoable.
 */
public class RXDrawerScrimTest {

    private static final double EPSILON = 1.0e-6;
    private static final double WIDTH = 400.0;
    private static final double HEIGHT = 300.0;

    /**
     * Starts the JavaFX toolkit so the skin can build the scrim.
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

    // ==================== Scrim visibility ====================

    @Test
    public void scrimDimsAndCatchesClicksWhenOpen() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            attach(pane);
            Region scrim = scrim(pane);

            pane.open();
            assertTrue(scrim.isVisible(), "scrim visible when open");
            assertFalse(scrim.isMouseTransparent(), "scrim catches clicks when open");
            assertEquals(RXDrawerPane.DEFAULT_SCRIM_OPACITY, scrim.getOpacity(), EPSILON);

            pane.close();
            assertFalse(scrim.isVisible(), "scrim hidden when closed");
            assertTrue(scrim.isMouseTransparent(), "scrim is click-through when closed");
            assertEquals(0.0, scrim.getOpacity(), EPSILON);
        });
    }

    @Test
    public void scrimDisabledStaysHiddenWhenOpen() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            pane.setScrim(false);
            attach(pane);
            Region scrim = scrim(pane);

            pane.open();
            assertFalse(scrim.isVisible(), "non-modal: scrim stays hidden");
            assertTrue(scrim.isMouseTransparent(), "non-modal: clicks pass through");
            assertEquals(0.0, scrim.getOpacity(), EPSILON);
        });
    }

    @Test
    public void scrimOpacityIsClampedAndApplied() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setScrimOpacity(1.5);
            assertEquals(1.0, pane.getScrimOpacity(), EPSILON, "clamped above 1");
            pane.setScrimOpacity(-0.5);
            assertEquals(0.0, pane.getScrimOpacity(), EPSILON, "clamped below 0");

            pane.setScrimOpacity(0.5);
            pane.setAnimated(false);
            attach(pane);
            pane.open();
            assertEquals(0.5, scrim(pane).getOpacity(), EPSILON, "open scrim uses scrimOpacity");
        });
    }

    // ==================== Scrim click ====================

    @Test
    public void scrimClickRequestsScrimClickClose() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            List<String> log = recordEvents(pane);
            attach(pane);
            pane.open();
            log.clear();

            fireClick(scrim(pane));

            assertFalse(pane.isShowing(), "scrim click closes the drawer");
            assertEquals(List.of(
                    "CLOSE_REQUEST:SCRIM_CLICK", "CLOSING:SCRIM_CLICK", "CLOSED:SCRIM_CLICK"), log);
        });
    }

    @Test
    public void scrimClickIsVetoable() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            attach(pane);
            pane.open();
            pane.setOnCloseRequest(Event::consume);

            fireClick(scrim(pane));
            assertTrue(pane.isShowing(), "a consumed scrim-click CLOSE_REQUEST keeps it open");
        });
    }

    @Test
    public void dismissOnScrimClickFalseDoesNotClose() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            pane.setDismissOnScrimClick(false);
            List<String> log = recordEvents(pane);
            attach(pane);
            pane.open();
            log.clear();

            fireClick(scrim(pane));
            assertTrue(pane.isShowing(), "scrim click ignored");
            assertTrue(log.isEmpty(), "no CLOSE_REQUEST when dismissOnScrimClick is false");
        });
    }

    // ==================== ESC ====================

    @Test
    public void escRequestsEscClose() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            List<String> log = recordEvents(pane);
            attach(pane);
            pane.open();
            log.clear();

            fireKey(pane, KeyCode.ESCAPE);

            assertFalse(pane.isShowing(), "ESC closes the drawer");
            assertEquals(List.of(
                    "CLOSE_REQUEST:ESC", "CLOSING:ESC", "CLOSED:ESC"), log);
        });
    }

    @Test
    public void escIsVetoable() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            attach(pane);
            pane.open();
            pane.setOnCloseRequest(Event::consume);

            fireKey(pane, KeyCode.ESCAPE);
            assertTrue(pane.isShowing(), "a consumed ESC CLOSE_REQUEST keeps it open");
        });
    }

    @Test
    public void closeOnEscFalseDoesNotClose() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            pane.setCloseOnEsc(false);
            attach(pane);
            pane.open();

            fireKey(pane, KeyCode.ESCAPE);
            assertTrue(pane.isShowing(), "ESC ignored when closeOnEsc is false");
        });
    }

    @Test
    public void escWhenClosedIsIgnored() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            List<String> log = recordEvents(pane);
            attach(pane);

            fireKey(pane, KeyCode.ESCAPE);
            assertTrue(log.isEmpty(), "ESC on a closed drawer does nothing");
        });
    }

    // ==================== Helpers ====================

    private static Region scrim(RXDrawerPane pane) {
        Region scrim = (Region) pane.lookup(".scrim");
        assertNotNull(scrim, "scrim layer exists");
        return scrim;
    }

    private static void fireClick(Node node) {
        assertNotNull(node, "click target exists");
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                MouseButton.PRIMARY, 1, false, false, false, false,
                true, false, false, true, false, false, null));
    }

    private static void fireKey(RXDrawerPane pane, KeyCode code) {
        pane.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    private static List<String> recordEvents(RXDrawerPane pane) {
        List<String> log = new ArrayList<>();
        pane.addEventHandler(RXDrawerEvent.ANY,
                e -> log.add(e.getEventType().getName() + ":" + e.getReason()));
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
