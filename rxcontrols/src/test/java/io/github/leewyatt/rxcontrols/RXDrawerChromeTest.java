package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXDrawerEvent;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the PR3 chrome of {@link RXDrawerPane}: the header (title + close
 * button), the body (optional {@code ScrollPane} via {@code scrollable}), the
 * footer, and the close button producing a {@code CLOSE_BUTTON} close.
 */
public class RXDrawerChromeTest {

    private static final double WIDTH = 400.0;
    private static final double HEIGHT = 300.0;

    /**
     * Starts the JavaFX toolkit so the skin can build its chrome.
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

    // ==================== Header / title ====================

    @Test
    public void titleRendersInHeaderLabel() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setTitle("Edit task");
            attach(pane);

            Label label = (Label) pane.lookup(".header > .label");
            assertNotNull(label, "title label exists in the header");
            assertEquals("Edit task", label.getText());
        });
    }

    @Test
    public void nullTitleRendersEmptyWithoutError() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setTitle(null);
            assertNull(pane.getTitle(), "title getter is pure pass-through (may return null)");
            attach(pane);
            // Header still exists because of the default close button; the label is empty.
            Label label = (Label) pane.lookup(".header > .label");
            assertNotNull(label);
            assertEquals("", label.getText(), "null title normalizes to empty at render");
        });
    }

    @Test
    public void headerIsAbsentWithoutTitleOrCloseButton() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setTitle("");
            pane.setShowCloseButton(false);
            attach(pane);
            assertNull(pane.lookup(".header"), "empty title + no close button means no header");
        });
    }

    // ==================== Close button ====================

    @Test
    public void closeButtonShownByDefault() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            attach(pane);
            assertNotNull(pane.lookup(".close-button"), "close button shown by default");
        });
    }

    @Test
    public void showCloseButtonFalseRemovesIt() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setShowCloseButton(false);
            pane.setTitle("Title keeps the header");
            attach(pane);
            assertNull(pane.lookup(".close-button"), "no close button when disabled");
        });
    }

    @Test
    public void closeButtonClickRequestsCloseWithCloseButtonReason() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            List<String> log = recordEvents(pane);
            attach(pane);
            pane.open();
            log.clear();

            fireClick(pane.lookup(".close-button"));

            assertFalse(pane.isShowing(), "close button closes the drawer");
            assertEquals(List.of(
                    "CLOSE_REQUEST:CLOSE_BUTTON", "CLOSING:CLOSE_BUTTON", "CLOSED:CLOSE_BUTTON"), log);
        });
    }

    @Test
    public void closeButtonClickIsVetoable() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            attach(pane);
            pane.open();
            pane.setOnCloseRequest(Event::consume);

            fireClick(pane.lookup(".close-button"));
            assertTrue(pane.isShowing(), "a consumed CLOSE_REQUEST keeps the drawer open");
        });
    }

    // ==================== Body / scrollable ====================

    @Test
    public void scrollableWrapsContentInScrollPane() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            Label content = new Label("body");
            pane.setDrawerContent(content);
            attach(pane);

            Node scrollPane = pane.lookup(".body > .scroll-pane");
            assertNotNull(scrollPane, "scrollable body wraps a ScrollPane");
            assertTrue(isDescendant(content, (Parent) scrollPane), "content lives inside the ScrollPane");
        });
    }

    @Test
    public void notScrollableUsesBareBody() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setScrollable(false);
            Label content = new Label("body");
            pane.setDrawerContent(content);
            attach(pane);

            assertNull(pane.lookup(".scroll-pane"), "bare body has no ScrollPane");
            Region body = (Region) pane.lookup(".body");
            assertTrue(body.getChildrenUnmodifiable().contains(content), "content sits directly in the body");
        });
    }

    // ==================== Footer ====================

    @Test
    public void footerAbsentByDefault() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            attach(pane);
            assertNull(pane.lookup(".footer"), "no footer node by default");
        });
    }

    @Test
    public void footerRendersTheFooterNode() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            HBox footer = new HBox(new Button("Save"));
            pane.setFooter(footer);
            attach(pane);

            Region footerRegion = (Region) pane.lookup(".footer");
            assertNotNull(footerRegion, "footer area exists when a footer is set");
            assertTrue(footerRegion.getChildrenUnmodifiable().contains(footer));
        });
    }

    // ==================== Helpers ====================

    private static void fireClick(Node node) {
        assertNotNull(node, "click target exists");
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                MouseButton.PRIMARY, 1, false, false, false, false,
                true, false, false, true, false, false, null));
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

    private static boolean isDescendant(Node node, Parent ancestor) {
        Node current = node;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
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
