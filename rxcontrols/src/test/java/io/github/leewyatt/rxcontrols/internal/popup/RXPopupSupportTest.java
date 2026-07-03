package io.github.leewyatt.rxcontrols.internal.popup;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.PopupControl;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lifecycle and sizing tests for {@link RXPopupSupport}. The pure state-machine
 * cases run without a shown window; the window-level cases (actual popup window
 * size per width mode, max-height cap, owner-node migration, owner-window
 * listener release) show a real {@link Stage} and are tagged {@code "ui"} so a
 * headless CI can exclude them ({@code -DexcludedGroups=ui}). The cascader
 * end-to-end popup paths are covered in {@code RXCascaderPopupTest}.
 */
public class RXPopupSupportTest {

    private static final String TEST_POPUP_CLASS = "rx-popup-support-test";

    private Stage stage;

    /**
     * Starts the JavaFX toolkit so {@link javafx.scene.control.PopupControl} can be
     * constructed, and keeps it alive across window hides.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
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
        // The window-level tests hide real windows; with the default implicit
        // exit, hiding the last one would shut the toolkit down for the fork.
        Platform.setImplicitExit(false);
    }

    /**
     * Hides any leaked test popup and the test stage after each test.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @AfterEach
    public void cleanup() throws InterruptedException {
        runOnFx(() -> {
            for (Window window : List.copyOf(Window.getWindows())) {
                if (window instanceof PopupControl
                        && ((PopupControl) window).getStyleClass().contains(TEST_POPUP_CLASS)) {
                    window.hide();
                }
            }
            if (stage != null) {
                stage.hide();
                stage = null;
            }
        });
    }

    // ==================== State machine (no shown window) ====================

    @Test
    public void initiallyNotShowing() throws InterruptedException {
        runOnFx(() -> {
            RXPopupSupport support = new RXPopupSupport(new Region());
            assertNotNull(support.showingProperty(), "showing property should exist");
            assertFalse(support.isShowing(), "a fresh support is not showing");
            assertFalse(support.showingProperty().get(), "showing property is false initially");
        });
    }

    @Test
    public void showWithDetachedAnchorRollsBackAndNotifiesHidden() throws InterruptedException {
        AtomicBoolean hiddenNotified = new AtomicBoolean(false);
        runOnFx(() -> {
            RXPopupSupport support = new RXPopupSupport(new Region());
            support.setOnHidden(() -> hiddenNotified.set(true));
            // A node not attached to a scene cannot host a popup: show must roll the
            // logical showing state back and notify the host via onHidden.
            support.show(new Button("anchor"));
            assertFalse(support.isShowing(), "cannot show against a detached anchor");
        });
        // hiddenNotified is asserted after the FX task drains.
        if (!hiddenNotified.get()) {
            throw new AssertionError("onHidden must fire when show rolls back");
        }
    }

    @Test
    public void showWithUnshownOwnerWindowRollsBackAndNotifiesHidden() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = new Region();
            anchor.setPrefSize(100, 30);
            stage = new Stage();
            stage.setScene(new Scene(new StackPane(anchor), 200, 90));
            // The stage is intentionally never shown: PopupWindow.showImpl silently
            // refuses to show against a non-showing root window.
            RXPopupSupport support = newTestSupport(sizedContent(120, 60));
            AtomicInteger hidden = new AtomicInteger();
            support.setOnHidden(hidden::incrementAndGet);
            support.show(anchor);
            assertFalse(support.isShowing(), "show against an un-shown owner window must roll back");
            assertEquals(1, hidden.get(), "the failed show must notify onHidden");
        });
    }

    @Test
    public void nullAnchorShowIsIgnored() throws InterruptedException {
        runOnFx(() -> {
            RXPopupSupport support = new RXPopupSupport(new Region());
            support.show(null);
            assertFalse(support.isShowing(), "null anchor is ignored, stays hidden");
        });
    }

    @Test
    public void hideWhenNotShowingIsNoOp() throws InterruptedException {
        runOnFx(() -> {
            RXPopupSupport support = new RXPopupSupport(new Region());
            support.hide();
            assertFalse(support.isShowing());
        });
    }

    @Test
    public void configSettersAreSafeWhenHidden() throws InterruptedException {
        runOnFx(() -> {
            RXPopupSupport support = new RXPopupSupport(new Region());
            support.setPlacement(RXPlacement.TOP_END);
            support.setPlacement(null);
            support.setOffset(4, 6);
            support.setWidthMode(RXPopupWidthMode.MATCH_ANCHOR_WIDTH);
            support.setWidthMode(null);
            support.setAutoHide(false);
            support.setHideOnEscape(false);
            support.setConsumeAutoHidingEvents(true);
            support.setPopupStyleClass("rx-suggestion-popup");
            support.setPopupStyleClass(null);
            support.setOnHidden(null);
            support.requestReposition();
            assertFalse(support.isShowing(), "configuration while hidden never shows");
        });
    }

    @Test
    public void setAnchorRebindWhileHiddenIsSafe() throws InterruptedException {
        runOnFx(() -> {
            RXPopupSupport support = new RXPopupSupport(new Region());
            support.setAnchor(new Button("first"));
            support.setAnchor(new Button("second"));
            assertFalse(support.isShowing(), "rebinding while hidden never shows");
        });
    }

    @Test
    public void setAnchorSameNodeIsNoOp() throws InterruptedException {
        runOnFx(() -> {
            RXPopupSupport support = new RXPopupSupport(new Region());
            Button anchor = new Button("anchor");
            support.setAnchor(anchor);
            support.setAnchor(anchor);
            assertFalse(support.isShowing());
        });
    }

    @Test
    public void disposeIsIdempotent() throws InterruptedException {
        runOnFx(() -> {
            RXPopupSupport support = new RXPopupSupport(new Region());
            support.dispose();
            support.dispose();
            support.show(new Button("anchor"));
            assertFalse(support.isShowing(), "a disposed support ignores show");
        });
    }

    // ==================== Window-level sizing ====================

    @Test
    @Tag("ui")
    public void preferAnchorWidthWidensPopupWindowToAnchor() throws InterruptedException {
        AtomicReference<PopupControl> popupRef = new AtomicReference<>();
        runOnFx(() -> {
            Region anchor = newShownAnchor(300);
            RXPopupSupport support = newTestSupport(sizedContent(180, 100));
            // PREFER_ANCHOR_WIDTH is the default width mode.
            support.show(anchor);
            assertTrue(support.isShowing(), "precondition: popup is showing");
            popupRef.set(findTestPopup());
            assertNotNull(popupRef.get(), "popup window should be present");
        });
        waitForFxCondition(() -> popupRef.get().getWidth() >= 300 - 0.5,
                () -> "PREFER_ANCHOR_WIDTH must widen the popup window to at least the anchor"
                        + " width, got " + popupRef.get().getWidth());
    }

    @Test
    @Tag("ui")
    public void matchAnchorWidthForcesPopupWindowToAnchorWidth() throws InterruptedException {
        AtomicReference<PopupControl> popupRef = new AtomicReference<>();
        runOnFx(() -> {
            Region anchor = newShownAnchor(300);
            RXPopupSupport support = newTestSupport(sizedContent(420, 100));
            support.setWidthMode(RXPopupWidthMode.MATCH_ANCHOR_WIDTH);
            support.show(anchor);
            popupRef.set(findTestPopup());
            assertNotNull(popupRef.get(), "popup window should be present");
        });
        waitForFxCondition(() -> Math.abs(popupRef.get().getWidth() - 300) <= 1.0,
                () -> "MATCH_ANCHOR_WIDTH must force the popup window to the anchor width, got "
                        + popupRef.get().getWidth());
    }

    @Test
    @Tag("ui")
    public void prefContentKeepsPopupWindowAtContentWidth() throws InterruptedException {
        AtomicReference<PopupControl> popupRef = new AtomicReference<>();
        runOnFx(() -> {
            Region anchor = newShownAnchor(300);
            RXPopupSupport support = newTestSupport(sizedContent(180, 100));
            support.setWidthMode(RXPopupWidthMode.PREF_CONTENT);
            support.show(anchor);
            popupRef.set(findTestPopup());
            assertNotNull(popupRef.get(), "popup window should be present");
        });
        waitForFxCondition(() -> Math.abs(popupRef.get().getWidth() - 180) <= 1.0,
                () -> "PREF_CONTENT keeps the popup window at the content width, got "
                        + popupRef.get().getWidth());
    }

    @Test
    @Tag("ui")
    public void maxHeightCapKeepsPopupWindowOnScreen() throws InterruptedException {
        AtomicReference<PopupControl> popupRef = new AtomicReference<>();
        runOnFx(() -> {
            Rectangle2D screen = Screen.getPrimary().getVisualBounds();
            Region anchor = newShownAnchor(200);
            // Anchor near the middle of the screen with content taller than the
            // whole screen: neither side fits, so the resolved geometry caps.
            stage.setY(screen.getMinY() + screen.getHeight() / 2 - 45);
            RXPopupSupport support = newTestSupport(sizedContent(200, screen.getHeight() * 2));
            support.setWidthMode(RXPopupWidthMode.PREF_CONTENT);
            support.show(anchor);
            popupRef.set(findTestPopup());
            assertNotNull(popupRef.get(), "popup window should be present");
        });
        waitForFxCondition(() -> {
            PopupControl popup = popupRef.get();
            Rectangle2D screen = Screen.getPrimary().getVisualBounds();
            return popup.getHeight() < screen.getHeight()
                    && popup.getAnchorY() >= screen.getMinY() - 0.5
                    && popup.getAnchorY() + popup.getHeight() <= screen.getMaxY() + 2.0;
        }, () -> "the max-height cap must shrink the popup window to fit the screen, got height "
                + popupRef.get().getHeight());
    }

    // ==================== Window-level lifecycle ====================

    @Test
    @Tag("ui")
    public void setAnchorNullWhileShowingRollsBackAndNotifiesHidden() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = newShownAnchor(150);
            RXPopupSupport support = newTestSupport(sizedContent(120, 60));
            AtomicInteger hidden = new AtomicInteger();
            support.setOnHidden(hidden::incrementAndGet);
            support.show(anchor);
            assertTrue(support.isShowing(), "precondition: popup is showing");
            support.setAnchor(null);
            assertFalse(support.isShowing(), "clearing the anchor while showing must report hidden");
            assertEquals(1, hidden.get(), "exactly one onHidden for the anchor-cleared hide");
        });
    }

    @Test
    @Tag("ui")
    public void showOnDifferentAnchorMigratesOwnerNode() throws InterruptedException {
        runOnFx(() -> {
            Region first = sizedContent(100, 30);
            Region second = sizedContent(100, 30);
            VBox root = new VBox(8, first, second);
            stage = new Stage();
            stage.setScene(new Scene(root, 220, 120));
            stage.show();
            root.applyCss();
            root.layout();
            RXPopupSupport support = newTestSupport(sizedContent(120, 60));
            support.show(first);
            PopupControl popup = findTestPopup();
            assertNotNull(popup, "popup window should be present");
            assertSame(first, popup.getOwnerNode(), "precondition: owner is the first anchor");
            support.show(second);
            assertTrue(support.isShowing(), "still logically showing across the migration");
            assertSame(second, popup.getOwnerNode(),
                    "show on another anchor must migrate the framework ownerNode");
        });
    }

    @Test
    @Tag("ui")
    public void hiddenPopupIsReclaimableWhileOwnerWindowLives() throws InterruptedException {
        AtomicReference<WeakReference<RXPopupSupport>> probe = new AtomicReference<>();
        runOnFx(() -> {
            Region anchor = newShownAnchor(120);
            StackPane root = (StackPane) anchor.getParent();
            RXPopupSupport support = newTestSupport(sizedContent(150, 80));
            support.show(anchor);
            assertTrue(support.isShowing(), "precondition: popup is showing");
            support.hide();
            assertFalse(support.isShowing());
            // Drop the anchor too: its listeners legitimately live until a rebind
            // or dispose. What is under test is the owner-window listener set,
            // which must not pin the support island after a hide.
            root.getChildren().remove(anchor);
            probe.set(new WeakReference<>(support));
        });
        // The stage stays showing during the GC probe, so a leaked owner-window
        // listener would keep the support strongly reachable.
        assertReclaimable(probe.get(),
                "a hidden support must not be pinned by its owner window's listeners");
    }

    @Test
    @Tag("ui")
    public void repositionFollowsWindowMoveAfterReshow() throws InterruptedException {
        AtomicReference<RXPopupSupport> supportRef = new AtomicReference<>();
        AtomicReference<PopupControl> popupRef = new AtomicReference<>();
        AtomicReference<Double> beforeX = new AtomicReference<>();
        runOnFx(() -> {
            Region anchor = newShownAnchor(200);
            RXPopupSupport support = newTestSupport(sizedContent(150, 80));
            supportRef.set(support);
            support.show(anchor);
            support.hide();
            support.show(anchor);
            assertTrue(support.isShowing(), "precondition: popup re-shown after a hide");
            popupRef.set(findTestPopup());
            assertNotNull(popupRef.get(), "popup window should be present");
            beforeX.set(popupRef.get().getAnchorX());
            stage.setX(stage.getX() + 40);
        });
        runOnFx(() -> assertEquals(beforeX.get() + 40, popupRef.get().getAnchorX(), 2.0,
                "a window move must reposition the popup after a hide + re-show"));
    }

    // ==================== Helpers ====================

    private RXPopupSupport newTestSupport(Region content) {
        RXPopupSupport support = new RXPopupSupport(content);
        support.setPopupStyleClass(TEST_POPUP_CLASS);
        return support;
    }

    private static Region sizedContent(double width, double height) {
        Region region = new Region();
        region.setPrefSize(width, height);
        return region;
    }

    private Region newShownAnchor(double width) {
        Region anchor = sizedContent(width, 30);
        anchor.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane root = new StackPane(anchor);
        stage = new Stage();
        stage.setScene(new Scene(root, width + 60, 90));
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        stage.setX(screen.getMinX() + 60);
        stage.setY(screen.getMinY() + 60);
        stage.show();
        root.applyCss();
        root.layout();
        return anchor;
    }

    private static PopupControl findTestPopup() {
        for (Window window : Window.getWindows()) {
            if (window instanceof PopupControl) {
                PopupControl popup = (PopupControl) window;
                if (popup.getStyleClass().contains(TEST_POPUP_CLASS)) {
                    return popup;
                }
            }
        }
        return null;
    }

    /**
     * Polls the condition on the FX thread until it holds or a timeout elapses.
     * Window sizes settle on a layout pulse, not synchronously with reconfigure,
     * so window-size assertions must wait for the pulse.
     */
    private static void waitForFxCondition(BooleanSupplier condition, Supplier<String> message)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        AtomicBoolean satisfied = new AtomicBoolean(false);
        while (System.currentTimeMillis() < deadline) {
            runOnFx(() -> satisfied.set(condition.getAsBoolean()));
            if (satisfied.get()) {
                return;
            }
            Thread.sleep(25L);
        }
        throw new AssertionError(message.get());
    }

    private static void assertReclaimable(WeakReference<?> reference, String message)
            throws InterruptedException {
        for (int i = 0; i < 50 && reference.get() != null; i++) {
            System.gc();
            Thread.sleep(10L);
        }
        assertNull(reference.get(), message);
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
