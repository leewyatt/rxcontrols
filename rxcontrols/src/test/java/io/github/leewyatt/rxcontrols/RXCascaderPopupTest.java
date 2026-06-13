package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.PopupControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Window-dependent popup behavior tests for {@link RXCascader} and its skin: the
 * display-click toggle and the suppressReopen auto-hide race, keyboard toggling,
 * and the scene-leave guard.
 *
 * <p>These need a real shown {@link Stage}: without a window the skin bounces
 * {@code show()} straight back to hidden, so the popup logic cannot be exercised
 * otherwise. The class is tagged {@code "ui"} so a headless CI without Monocle
 * can exclude it ({@code -DexcludedGroups=ui}); it runs by default locally.
 */
@Tag("ui")
public class RXCascaderPopupTest {

    private Stage stage;

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
        // These tests are the only ones in the module that show and hide real
        // windows. With the default implicit exit, hiding the last window would
        // shut the toolkit down for good, breaking every test class that runs
        // afterward in the same JVM fork.
        Platform.setImplicitExit(false);
    }

    /**
     * Hides any popup window and the test stage so windows do not leak across tests.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @AfterEach
    public void cleanup() throws InterruptedException {
        runOnFx(() -> {
            PopupControl popup = findCascaderPopup();
            if (popup != null) {
                popup.hide();
            }
            if (stage != null) {
                stage.hide();
                stage = null;
            }
        });
    }

    /**
     * Verifies clicking the display toggles the popup open and closed.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void displayClickTogglesPopup() throws InterruptedException {
        runOnFx(() -> {
            RXCascader<String> cascader = newShownCascader();
            Node display = cascader.lookup(".display");
            assertNotNull(display, "display should exist");

            fireClick(display);
            assertTrue(cascader.isShowing(), "first display click opens the popup");

            fireClick(display);
            assertFalse(cascader.isShowing(), "second display click closes the popup");
        });
    }

    /**
     * Verifies the suppressReopen guard: a click that arrives in the same pulse
     * as a popup auto-hide must not immediately reopen the popup, while a click
     * after the guard resets (next pulse) reopens it normally.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void popupAutoHideDoesNotInstantlyReopen() throws InterruptedException {
        AtomicReference<RXCascader<String>> ref = new AtomicReference<>();
        runOnFx(() -> {
            RXCascader<String> cascader = newShownCascader();
            ref.set(cascader);
            cascader.show();
            assertTrue(cascader.isShowing(), "precondition: popup is open");
            PopupControl popup = findCascaderPopup();
            assertNotNull(popup, "popup window should be present");

            // Simulate the auto-hide (a press outside hides the popup window),
            // which arms suppressReopen for the current pulse.
            popup.hide();
            assertFalse(cascader.isShowing(), "auto-hide closes the control");

            // The same press lands on the display: it must NOT reopen the popup.
            fireClick(cascader.lookup(".display"));
            assertFalse(cascader.isShowing(), "a click in the auto-hide pulse must not reopen the popup");
        });
        // The guard resets on the next pulse: a later display click reopens.
        runOnFx(() -> {
            RXCascader<String> cascader = ref.get();
            fireClick(cascader.lookup(".display"));
            assertTrue(cascader.isShowing(), "after the guard resets, a display click reopens the popup");
        });
    }

    /**
     * Verifies ENTER and SPACE toggle the popup and ESCAPE closes it.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void keyboardEnterSpaceEscapeTogglePopup() throws InterruptedException {
        runOnFx(() -> {
            RXCascader<String> cascader = newShownCascader();

            fireKey(cascader, KeyCode.ENTER);
            assertTrue(cascader.isShowing(), "ENTER opens the popup");

            fireKey(cascader, KeyCode.ENTER);
            assertFalse(cascader.isShowing(), "ENTER again closes the popup");

            fireKey(cascader, KeyCode.SPACE);
            assertTrue(cascader.isShowing(), "SPACE opens the popup");

            fireKey(cascader, KeyCode.ESCAPE);
            assertFalse(cascader.isShowing(), "ESCAPE closes the popup");
        });
    }

    /**
     * Verifies the popup hides when its owning control leaves the scene. This is
     * provided by the JavaFX framework rather than the skin:
     * {@code PopupWindow.show(ownerNode, ...)} tracks the owner node's
     * tree-showing and hides the popup when it leaves the scene, which then syncs
     * the control back to not-showing via {@code WINDOW_HIDDEN}. Locking the
     * behavior in guards against a Phase 5 hardening regression (for example,
     * switching the popup to a window owner would silently break it).
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void popupHidesWhenControlLeavesScene() throws InterruptedException {
        AtomicReference<PopupControl> popupRef = new AtomicReference<>();
        runOnFx(() -> {
            RXCascader<String> cascader = newShownCascader();
            StackPane rootPane = (StackPane) cascader.getParent();
            cascader.show();
            assertTrue(cascader.isShowing(), "precondition: popup is open");
            PopupControl popup = findCascaderPopup();
            assertNotNull(popup, "popup window should be present");
            assertTrue(popup.isShowing(), "precondition: popup window is showing");
            popupRef.set(popup);

            rootPane.getChildren().remove(cascader);
            cascader.applyCss();
        });
        // Assert on a fresh pulse so any deferred owner tree-showing hide has drained.
        runOnFx(() -> assertFalse(popupRef.get().isShowing(),
                "popup must hide when its owning control leaves the scene"));
    }

    /**
     * Verifies the popup flips above the control when there is no room below it,
     * using only public {@link Screen} API.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void popupFlipsAboveWhenNoRoomBelow() throws InterruptedException {
        boolean[] flippedAbove = new boolean[1];
        runOnFx(() -> {
            Rectangle2D screen = Screen.getPrimary().getVisualBounds();
            RXCascader<String> cascader = new RXCascader<>();
            cascader.getRootItems().addAll(List.of(
                    new RXCascaderItem<>("a"), new RXCascaderItem<>("b"), new RXCascaderItem<>("c")));
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(cascader), 300, 60));
            stage.setX(screen.getMinX() + 40);
            stage.setY(screen.getMaxY() - 80); // control near the bottom edge
            stage.show();
            cascader.applyCss();
            cascader.layout();
            cascader.show();
            PopupControl popup = findCascaderPopup();
            assertNotNull(popup, "popup window should be present");
            layoutPopupContent(popup);
            Bounds control = cascader.localToScreen(cascader.getBoundsInLocal());
            flippedAbove[0] = popup.getAnchorY() <= control.getMinY();
            assertTrue(popup.getAnchorY() >= screen.getMinY(), "popup top stays on screen");
        });
        assertTrue(flippedAbove[0], "popup flips above the control when there is no room below");
    }

    /**
     * Verifies the popup is clamped within the screen's right edge when the
     * control sits near it, using only public {@link Screen} API.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void popupClampsToScreen() throws InterruptedException {
        boolean[] clamped = new boolean[1];
        runOnFx(() -> {
            Rectangle2D screen = Screen.getPrimary().getVisualBounds();
            RXCascader<String> cascader = new RXCascader<>();
            cascader.getRootItems().add(new RXCascaderItem<>("a"));
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(cascader), 300, 60));
            stage.setX(screen.getMaxX() - 90); // control near the right edge
            stage.setY(screen.getMinY() + 40);
            stage.show();
            cascader.applyCss();
            cascader.layout();
            cascader.show();
            PopupControl popup = findCascaderPopup();
            assertNotNull(popup, "popup window should be present");
            layoutPopupContent(popup);
            clamped[0] = popup.getAnchorX() + popup.getWidth() <= screen.getMaxX() + 1.0;
        });
        assertTrue(clamped[0], "popup is clamped within the screen's right edge");
    }

    // ==================== Helpers ====================

    private RXCascader<String> newShownCascader() {
        RXCascader<String> cascader = new RXCascader<>();
        cascader.getRootItems().add(new RXCascaderItem<>("root"));
        stage = new Stage();
        stage.setScene(new Scene(new StackPane(cascader), 320, 200));
        stage.show();
        cascader.applyCss();
        cascader.layout();
        return cascader;
    }

    private static void layoutPopupContent(PopupControl popup) {
        if (popup.getScene() != null && popup.getScene().getRoot() != null) {
            popup.getScene().getRoot().applyCss();
            popup.getScene().getRoot().layout();
        }
    }

    private static PopupControl findCascaderPopup() {
        for (Window window : Window.getWindows()) {
            if (window instanceof PopupControl) {
                PopupControl popup = (PopupControl) window;
                if (popup.getStyleClass().contains("rx-cascader-popup")) {
                    return popup;
                }
            }
        }
        return null;
    }

    private static void fireClick(Node node) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false,
                false, false, true, null));
    }

    private static void fireKey(Node node, KeyCode code) {
        node.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, "", code,
                false, false, false, false));
    }

    private static void runOnFx(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task did not complete");
        }
        Throwable t = error.get();
        if (t instanceof AssertionError) {
            throw (AssertionError) t;
        }
        if (t != null) {
            throw new AssertionError("FX task failed", t);
        }
    }
}
