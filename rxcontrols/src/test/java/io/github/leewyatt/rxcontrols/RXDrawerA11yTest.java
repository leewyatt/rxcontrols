package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the PR5 accessibility behavior of {@link RXDrawerPane}: the DIALOG
 * role, focus moving into a modal drawer on open and restoring on close, a
 * non-modal drawer not stealing focus, and the modal focus trap. Focus
 * <em>ownership</em> ({@code Scene.getFocusOwner()}) is tracked by
 * {@code requestFocus()} without a shown window, so these run against a plain
 * {@code Scene}.
 */
public class RXDrawerA11yTest {

    private static final double WIDTH = 400.0;
    private static final double HEIGHT = 300.0;

    /**
     * Starts the JavaFX toolkit.
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
    public void accessibleRoleIsDialog() throws Exception {
        runOnFx(() -> assertEquals(AccessibleRole.DIALOG, new RXDrawerPane().getAccessibleRole()));
    }

    @Test
    public void modalOpenMovesFocusToFirstFocusable() throws Exception {
        onScene(pane -> {
            TextField field = new TextField();
            pane.setDrawerContent(field);
            relayout(pane);

            pane.open();
            assertSame(field, pane.getScene().getFocusOwner(),
                    "focus moved to the first focusable in the drawer");
        });
    }

    @Test
    public void modalCloseRestoresFocus() throws Exception {
        onScene(pane -> {
            Button trigger = new Button("open");
            pane.setContent(trigger);
            pane.setDrawerContent(new TextField());
            relayout(pane);
            trigger.requestFocus();
            assertSame(trigger, pane.getScene().getFocusOwner(), "focus starts on the content button");

            pane.open();
            assertSame(pane.getDrawerContent(), pane.getScene().getFocusOwner(), "focus entered the drawer");

            pane.close();
            assertSame(trigger, pane.getScene().getFocusOwner(), "focus restored to the content button");
        });
    }

    @Test
    public void closedDrawerSubtreeIsHiddenAndCannotTakeFocus() throws Exception {
        onScene(pane -> {
            Button trigger = new Button("open");
            TextField field = new TextField();
            pane.setContent(trigger);
            pane.setDrawerContent(field);
            relayout(pane);
            Region drawer = (Region) pane.lookup(".drawer");
            assertNotNull(drawer);
            assertFalse(drawer.isVisible(), "closed drawer subtree is hidden");
            assertTrue(drawer.isMouseTransparent(), "closed drawer subtree does not catch mouse input");

            trigger.requestFocus();
            field.requestFocus();
            assertSame(trigger, pane.getScene().getFocusOwner(),
                    "closed drawer content cannot become focus owner");

            Node closeButton = pane.lookup(".close-button");
            assertNotNull(closeButton);
            closeButton.requestFocus();
            assertSame(trigger, pane.getScene().getFocusOwner(),
                    "closed close button cannot become focus owner");
        });
    }

    @Test
    public void nonModalOpenDoesNotStealFocus() throws Exception {
        onScene(pane -> {
            pane.setOverlayPaneVisible(false);
            Button trigger = new Button("open");
            pane.setContent(trigger);
            pane.setDrawerContent(new TextField());
            relayout(pane);
            trigger.requestFocus();

            pane.open();
            assertSame(trigger, pane.getScene().getFocusOwner(), "non-modal drawer does not steal focus");
        });
    }

    @Test
    public void modalTabTrapKeepsFocusInsideDrawer() throws Exception {
        onScene(pane -> {
            pane.setDrawerContent(new VBox(new TextField(), new TextField()));
            relayout(pane);
            pane.open();
            Region drawer = (Region) pane.lookup(".drawer");
            assertNotNull(drawer);

            // Tab repeatedly; focus must cycle and never escape the drawer subtree.
            for (int i = 0; i < 6; i++) {
                pane.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB,
                        false, false, false, false));
                assertTrue(isDescendant(pane.getScene().getFocusOwner(), drawer),
                        "focus stayed within the drawer after Tab #" + i);
            }
        });
    }

    @Test
    public void modalTabTrapWrapsForwardAndBackwardInOrder() throws Exception {
        onScene(pane -> {
            TextField first = new TextField();
            TextField second = new TextField();
            pane.setTitle("");
            pane.setShowCloseButton(false);
            pane.setScrollable(false);
            pane.setDrawerContent(new VBox(first, second));
            relayout(pane);

            pane.open();
            assertSame(first, pane.getScene().getFocusOwner(), "open focuses the first field");

            fireTab(pane, false);
            assertSame(second, pane.getScene().getFocusOwner(), "Tab advances");
            fireTab(pane, false);
            assertSame(first, pane.getScene().getFocusOwner(), "Tab wraps forward");
            fireTab(pane, true);
            assertSame(second, pane.getScene().getFocusOwner(), "Shift+Tab wraps backward");
            fireTab(pane, true);
            assertSame(first, pane.getScene().getFocusOwner(), "Shift+Tab moves backward");
        });
    }

    @Test
    public void modalOpenFallsBackToDrawerPaneWhenNoFocusableChild() throws Exception {
        onScene(pane -> {
            pane.setTitle("");
            pane.setShowCloseButton(false);
            pane.setDrawerContent(new Region());
            relayout(pane);
            Region drawer = (Region) pane.lookup(".drawer");
            assertNotNull(drawer);

            pane.open();
            assertSame(drawer, pane.getScene().getFocusOwner(),
                    "drawer panel is the last-resort focus target");
            fireTab(pane, false);
            assertSame(drawer, pane.getScene().getFocusOwner(),
                    "empty focus cycle keeps focus on the fallback target");
        });
    }

    // ==================== Helpers ====================

    private static void onScene(Consumer<RXDrawerPane> body) throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            new Scene(pane, WIDTH, HEIGHT);
            body.accept(pane);
        });
    }

    private static void relayout(RXDrawerPane pane) {
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

    private static void fireTab(RXDrawerPane pane, boolean shiftDown) {
        pane.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB,
                shiftDown, false, false, false));
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
