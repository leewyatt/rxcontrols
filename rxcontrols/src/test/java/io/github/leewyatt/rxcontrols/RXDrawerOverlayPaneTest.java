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
 * Tests for the overlay pane (modal backdrop) and ESC behavior of
 * {@link RXDrawerPane}: overlay-pane visibility/pickability across open/close,
 * {@code overlayPaneVisible} / {@code closeOnOverlayPaneClick} / {@code closeOnEsc},
 * overlay-pane click → OVERLAY_PANE_CLICK close, and ESC → ESC close, both vetoable.
 * The dim level itself is CSS (the {@code .overlay-pane} background), so the node's
 * opacity is just animated between 0 and 1.
 */
public class RXDrawerOverlayPaneTest {

    private static final double EPSILON = 1.0e-6;
    private static final double WIDTH = 400.0;
    private static final double HEIGHT = 300.0;

    /**
     * Starts the JavaFX toolkit so the skin can build the overlay pane.
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

    // ==================== Overlay pane visibility ====================

    @Test
    public void overlayPaneCatchesClicksWhenOpen() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            attach(pane);
            Region overlay = overlayPane(pane);

            pane.open();
            assertTrue(overlay.isVisible(), "overlay pane visible when open");
            assertFalse(overlay.isMouseTransparent(), "overlay pane catches clicks when open");
            assertEquals(1.0, overlay.getOpacity(), EPSILON, "node opacity is 1; dim comes from CSS");

            pane.close();
            assertFalse(overlay.isVisible(), "overlay pane hidden when closed");
            assertTrue(overlay.isMouseTransparent(), "overlay pane is click-through when closed");
            assertEquals(0.0, overlay.getOpacity(), EPSILON);
        });
    }

    @Test
    public void overlayPaneHiddenStaysHiddenWhenOpen() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            pane.setOverlayPaneVisible(false);
            attach(pane);
            Region overlay = overlayPane(pane);

            pane.open();
            assertFalse(overlay.isVisible(), "non-modal: overlay pane stays hidden");
            assertTrue(overlay.isMouseTransparent(), "non-modal: clicks pass through");
            assertEquals(0.0, overlay.getOpacity(), EPSILON);
        });
    }

    // ==================== Overlay pane click ====================

    @Test
    public void overlayPaneClickRequestsOverlayPaneClickClose() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            List<String> log = recordEvents(pane);
            attach(pane);
            pane.open();
            log.clear();

            fireClick(overlayPane(pane));

            assertFalse(pane.isShowing(), "overlay-pane click closes the drawer");
            assertEquals(List.of(
                    "CLOSE_REQUEST:OVERLAY_PANE_CLICK", "CLOSING:OVERLAY_PANE_CLICK",
                    "CLOSED:OVERLAY_PANE_CLICK"), log);
        });
    }

    @Test
    public void overlayPaneClickIsVetoable() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            attach(pane);
            pane.open();
            pane.setOnCloseRequest(Event::consume);

            fireClick(overlayPane(pane));
            assertTrue(pane.isShowing(), "a consumed overlay-pane CLOSE_REQUEST keeps it open");
        });
    }

    @Test
    public void closeOnOverlayPaneClickFalseDoesNotClose() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            pane.setCloseOnOverlayPaneClick(false);
            List<String> log = recordEvents(pane);
            attach(pane);
            pane.open();
            log.clear();

            fireClick(overlayPane(pane));
            assertTrue(pane.isShowing(), "overlay-pane click ignored");
            assertTrue(log.isEmpty(), "no CLOSE_REQUEST when closeOnOverlayPaneClick is false");
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

    private static Region overlayPane(RXDrawerPane pane) {
        Region overlay = (Region) pane.lookup(".overlay-pane");
        assertNotNull(overlay, "overlay pane exists");
        return overlay;
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
