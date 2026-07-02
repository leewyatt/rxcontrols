package io.github.leewyatt.rxcontrols.internal.popup;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Lifecycle state-machine tests for {@link RXPopupSupport} that do not require a
 * shown window. The window-dependent paths (real show / flip / clamp / auto-hide)
 * are covered end-to-end through the cascader in {@code RXCascaderPopupTest}.
 */
public class RXPopupSupportTest {

    /**
     * Starts the JavaFX toolkit so {@link javafx.scene.control.PopupControl} can be
     * constructed.
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
    }

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
