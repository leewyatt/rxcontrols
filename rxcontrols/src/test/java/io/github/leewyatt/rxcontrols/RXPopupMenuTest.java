package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXPopupMenu.CloseReason;
import io.github.leewyatt.rxcontrols.event.RXMenuEvent;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXPopupMenu}: the showing state machine, lifecycle callbacks,
 * the detached-owner / empty-menu guards, close-then-fire activation, the veto,
 * and the precise {@link CloseReason} paths. Pure state-machine cases run without
 * a shown window; cases that need a real popup window are tagged {@code "ui"}.
 */
public class RXPopupMenuTest {

    private Stage stage;

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
        Platform.setImplicitExit(false);
    }

    @AfterEach
    public void cleanup() throws InterruptedException {
        runOnFx(() -> {
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
            RXPopupMenu menu = new RXPopupMenu();
            assertNotNull(menu.showingProperty());
            assertFalse(menu.isShowing());
        });
    }

    @Test
    public void detachedAnchorIsNoOpAndFiresNothing() throws InterruptedException {
        runOnFx(() -> {
            RXPopupMenu menu = new RXPopupMenu();
            menu.getItems().add(RXMenuItem.of("A"));
            AtomicInteger showing = new AtomicInteger();
            menu.setOnShowing(e -> showing.incrementAndGet());
            // Guard (a): an anchor with no scene must be a no-op — not showing and no
            // onShowing fired (aligns with "owner has no scene -> no-op").
            menu.show(new Button("detached"));
            assertFalse(menu.isShowing());
            assertEquals(0, showing.get(), "onShowing must not fire for a detached anchor");
        });
    }

    @Test
    public void nullAnchorIsIgnored() throws InterruptedException {
        runOnFx(() -> {
            RXPopupMenu menu = new RXPopupMenu();
            menu.getItems().add(RXMenuItem.of("A"));
            menu.show(null);
            assertFalse(menu.isShowing());
        });
    }

    @Test
    public void hideWhenNotShowingIsNoOp() throws InterruptedException {
        runOnFx(() -> {
            RXPopupMenu menu = new RXPopupMenu();
            AtomicInteger hidden = new AtomicInteger();
            menu.setOnHidden(e -> hidden.incrementAndGet());
            menu.hide();
            assertFalse(menu.isShowing());
            assertEquals(0, hidden.get(), "hide while hidden fires nothing");
        });
    }

    @Test
    public void disposeIsIdempotentAndBlocksShow() throws InterruptedException {
        runOnFx(() -> {
            RXPopupMenu menu = new RXPopupMenu();
            menu.getItems().add(RXMenuItem.of("A"));
            menu.dispose();
            menu.dispose();
            menu.show(new Button("anchor"));
            assertFalse(menu.isShowing(), "a disposed menu ignores show");
        });
    }

    @Test
    public void disposeClearsItemsToReleaseCells() throws InterruptedException {
        runOnFx(() -> {
            RXPopupMenu menu = new RXPopupMenu();
            menu.getItems().addAll(RXMenuItem.of("A"), RXMenuItem.of("B"));
            menu.dispose();
            assertTrue(menu.getItems().isEmpty(),
                    "dispose clears the internal list so per-item cell listeners are torn down");
        });
    }

    @Test
    public void getItemsAndMenuListDelegate() throws InterruptedException {
        runOnFx(() -> {
            RXPopupMenu menu = new RXPopupMenu();
            RXMenuItem item = RXMenuItem.of("A");
            menu.getItems().add(item);
            assertSame(menu.getItems(), menu.getMenuList().getItems());
            assertTrue(menu.getMenuList().getItems().contains(item));
        });
    }

    // ==================== Window-level state machine ====================

    @Test
    @Tag("ui")
    public void showThenHideDrivesShowingAndLifecycle() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            RXPopupMenu menu = quietMenu("Run", "Save");
            AtomicInteger showing = new AtomicInteger();
            AtomicInteger shown = new AtomicInteger();
            AtomicReference<CloseReason> hiddenReason = new AtomicReference<>();
            menu.setOnShowing(e -> showing.incrementAndGet());
            menu.setOnShown(e -> shown.incrementAndGet());
            menu.setOnHidden(e -> hiddenReason.set(e.getReason()));

            menu.show(anchor);
            assertTrue(menu.isShowing(), "menu shows against a realized anchor");
            assertEquals(1, showing.get());
            assertEquals(1, shown.get());

            menu.hide();
            assertFalse(menu.isShowing());
            assertSame(CloseReason.PROGRAMMATIC, hiddenReason.get(), "no-arg hide is PROGRAMMATIC");
        });
    }

    @Test
    @Tag("ui")
    public void toggleShowsThenHides() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            RXPopupMenu menu = quietMenu("A", "B");
            menu.toggle(anchor);
            assertTrue(menu.isShowing(), "toggle from hidden shows");
            menu.toggle(anchor);
            assertFalse(menu.isShowing(), "toggle from showing hides");
        });
    }

    @Test
    @Tag("ui")
    public void emptyMenuDoesNotShow() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            RXPopupMenu menu = new RXPopupMenu();
            menu.getMenuList().setAnimated(false);
            menu.getItems().addAll(RXMenuSeparator.create(), RXMenuHeader.of("Group"));
            AtomicInteger showing = new AtomicInteger();
            menu.setOnShowing(e -> showing.incrementAndGet());
            menu.show(anchor);
            assertFalse(menu.isShowing(), "a menu with no focusable item does not show");
            assertEquals(0, showing.get(), "onShowing must not fire for an unfocusable menu");
        });
    }

    @Test
    @Tag("ui")
    public void allDisabledMenuOpensInApgMode() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            RXPopupMenu menu = new RXPopupMenu();
            menu.getMenuList().setAnimated(false);
            menu.getMenuList().setDisabledItemsFocusable(true);
            RXMenuItem a = RXMenuItem.of("A");
            RXMenuItem b = RXMenuItem.of("B");
            a.setDisable(true);
            b.setDisable(true);
            menu.getItems().addAll(a, b);
            menu.show(anchor);
            assertTrue(menu.isShowing(),
                    "an all-disabled menu still opens in APG (disabledItemsFocusable) mode");
        });
    }

    @Test
    @Tag("ui")
    public void allDisabledMenuDoesNotShowWithoutApg() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            RXPopupMenu menu = new RXPopupMenu();
            menu.getMenuList().setAnimated(false);
            RXMenuItem a = RXMenuItem.of("A");
            a.setDisable(true);
            menu.getItems().add(a);
            menu.show(anchor);
            assertFalse(menu.isShowing(),
                    "an all-disabled menu does not open when disabled items are not focusable");
        });
    }

    @Test
    @Tag("ui")
    public void disabledAnchorIsNoOpAndFiresNothing() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            anchor.setDisable(true);
            RXPopupMenu menu = quietMenu("A", "B");
            AtomicInteger showing = new AtomicInteger();
            menu.setOnShowing(e -> showing.incrementAndGet());
            // A realized but disabled anchor must not open the menu ("disabled owner").
            menu.show(anchor);
            assertFalse(menu.isShowing(), "a disabled anchor does not open the menu");
            assertEquals(0, showing.get(), "onShowing must not fire for a disabled anchor");
        });
    }

    @Test
    @Tag("ui")
    public void disablingOwnerWhileOpenClosesTheMenu() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            RXPopupMenu menu = quietMenu("A", "B");
            AtomicReference<CloseReason> reason = new AtomicReference<>();
            menu.setOnHidden(e -> reason.set(e.getReason()));
            menu.show(anchor);
            assertTrue(menu.isShowing(), "precondition: showing");
            // A standalone popup closes when its owner becomes (effectively) disabled,
            // not only when it is disabled before opening ("disabled owner -> no menu").
            anchor.setDisable(true);
            assertFalse(menu.isShowing(), "disabling the owner while open closes the menu");
            assertSame(CloseReason.PROGRAMMATIC, reason.get());
        });
    }

    @Test
    @Tag("ui")
    public void surfaceReportsVisibleAndParentMenuToAccessibility() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            RXPopupMenu menu = quietMenu("A", "B");
            RXMenuList surface = menu.getMenuList();
            // Before showing: no owner yet.
            assertNull(surface.queryAccessibleAttribute(AccessibleAttribute.PARENT_MENU),
                    "no parent menu before showing");

            menu.show(anchor);
            assertEquals(Boolean.TRUE, surface.queryAccessibleAttribute(AccessibleAttribute.VISIBLE),
                    "VISIBLE reflects the shown popup");
            assertSame(anchor, surface.queryAccessibleAttribute(AccessibleAttribute.PARENT_MENU),
                    "PARENT_MENU returns the invoking anchor");

            menu.hide();
            assertEquals(Boolean.FALSE, surface.queryAccessibleAttribute(AccessibleAttribute.VISIBLE),
                    "VISIBLE reflects the hidden popup");
        });
    }

    @Test
    @Tag("ui")
    public void escapeClosesWithEscapeReason() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            RXPopupMenu menu = quietMenu("A", "B");
            AtomicReference<CloseReason> reason = new AtomicReference<>();
            menu.setOnHidden(e -> reason.set(e.getReason()));
            menu.show(anchor);
            fireKey(menu.getMenuList(), KeyCode.ESCAPE);
            assertFalse(menu.isShowing());
            assertSame(CloseReason.ESCAPE, reason.get());
        });
    }

    @Test
    @Tag("ui")
    public void tabClosesWithTabReason() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            RXPopupMenu menu = quietMenu("A", "B");
            AtomicReference<CloseReason> reason = new AtomicReference<>();
            menu.setOnHidden(e -> reason.set(e.getReason()));
            menu.show(anchor);
            fireKey(menu.getMenuList(), KeyCode.TAB);
            assertFalse(menu.isShowing());
            assertSame(CloseReason.TAB, reason.get());
        });
    }

    @Test
    @Tag("ui")
    public void actionActivationClosesThenFires() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            RXPopupMenu menu = quietMenu();
            RXMenuItem item = RXMenuItem.of("Run");
            AtomicBoolean showingAtFire = new AtomicBoolean(true);
            AtomicInteger fired = new AtomicInteger();
            item.setOnAction(e -> {
                showingAtFire.set(menu.isShowing());
                fired.incrementAndGet();
            });
            menu.getItems().add(item);
            AtomicReference<CloseReason> reason = new AtomicReference<>();
            menu.setOnHidden(e -> reason.set(e.getReason()));

            menu.show(anchor);
            menu.getMenuList().activate(item);
            assertFalse(menu.isShowing(), "activation closes the menu");
            assertEquals(1, fired.get(), "the item fires");
            assertFalse(showingAtFire.get(), "close-then-fire: menu is already closed when the item fires");
            assertSame(CloseReason.ACTION, reason.get());
        });
    }

    @Test
    @Tag("ui")
    public void keepOpenItemFiresWithoutClosing() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            RXPopupMenu menu = quietMenu();
            RXMenuItem item = RXMenuItem.of("Toggle");
            item.setKeepOpen(true);
            AtomicInteger fired = new AtomicInteger();
            item.setOnAction(e -> fired.incrementAndGet());
            menu.getItems().add(item);

            menu.show(anchor);
            menu.getMenuList().activate(item);
            assertTrue(menu.isShowing(), "a keepOpen item does not close the menu");
            assertEquals(1, fired.get(), "a keepOpen item still fires");
        });
    }

    @Test
    @Tag("ui")
    public void actionHandlerExceptionPropagatesButCloseCompleted() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            RXPopupMenu menu = quietMenu();
            RXMenuItem item = RXMenuItem.of("Boom");
            item.setOnAction(e -> {
                throw new IllegalStateException("boom");
            });
            menu.getItems().add(item);

            menu.show(anchor);
            // close-then-fire: the close finishes before the throwing handler runs,
            // so the exception propagates but the menu is already closed.
            assertThrows(IllegalStateException.class, () -> menu.getMenuList().activate(item));
            assertFalse(menu.isShowing(), "the menu closed before the handler threw");
        });
    }

    @Test
    @Tag("ui")
    public void onHidingVetoKeepsMenuOpen() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            RXPopupMenu menu = quietMenu("A", "B");
            menu.setOnHiding(RXMenuEvent::consume);
            AtomicInteger hidden = new AtomicInteger();
            menu.setOnHidden(e -> hidden.incrementAndGet());

            menu.show(anchor);
            menu.hide();
            assertTrue(menu.isShowing(), "consuming onHiding vetoes the close");
            assertEquals(0, hidden.get(), "a vetoed close fires no onHidden");
        });
    }

    @Test
    @Tag("ui")
    public void ownerDetachClosesAsOwnerDetached() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            RXPopupMenu menu = quietMenu("A", "B");
            AtomicReference<CloseReason> reason = new AtomicReference<>();
            menu.setOnHidden(e -> reason.set(e.getReason()));
            menu.show(anchor);
            assertTrue(menu.isShowing(), "precondition: showing");
            // Owner leaves the scene: auto-close with the invoker no longer realized
            // is reported precisely as OWNER_DETACHED (distinct from an outside click).
            ((StackPane) anchor.getParent()).getChildren().remove(anchor);
            assertFalse(menu.isShowing());
            assertSame(CloseReason.OWNER_DETACHED, reason.get());
        });
    }

    @Test
    @Tag("ui")
    public void restorableCloseReasonsReturnFocusToInvoker() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            RXPopupMenu menu = quietMenu("A", "B");
            // PROGRAMMATIC / ESCAPE / TAB / ACTION all return focus to the invoker;
            // move focus off the anchor before each cycle so the restore is what puts
            // it back (not a leftover).
            anchor.getScene().getRoot().requestFocus();
            menu.show(anchor);
            menu.hide(CloseReason.PROGRAMMATIC);
            assertSame(anchor, anchor.getScene().getFocusOwner(),
                    "PROGRAMMATIC close restores focus to the invoker");

            anchor.getScene().getRoot().requestFocus();
            menu.show(anchor);
            fireKey(menu.getMenuList(), KeyCode.ESCAPE);
            assertSame(anchor, anchor.getScene().getFocusOwner(),
                    "ESCAPE close restores focus to the invoker");

            anchor.getScene().getRoot().requestFocus();
            menu.show(anchor);
            fireKey(menu.getMenuList(), KeyCode.TAB);
            assertSame(anchor, anchor.getScene().getFocusOwner(),
                    "TAB close restores focus to the invoker");

            anchor.getScene().getRoot().requestFocus();
            menu.show(anchor);
            menu.getMenuList().activate(menu.getItems().get(0));
            assertSame(anchor, anchor.getScene().getFocusOwner(),
                    "ACTION close (close-then-fire) restores focus to the invoker");
        });
    }

    @Test
    @Tag("ui")
    public void vetoDoesNotBlockOwnerDetach() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            RXPopupMenu menu = quietMenu("A", "B");
            // A veto guards only the explicit hide() path: auto-close from the owner
            // leaving the scene must still fire so a handler cannot trap the menu open
            // once its invoker is gone.
            menu.setOnHiding(RXMenuEvent::consume);
            AtomicReference<CloseReason> reason = new AtomicReference<>();
            menu.setOnHidden(e -> reason.set(e.getReason()));
            menu.show(anchor);
            ((StackPane) anchor.getParent()).getChildren().remove(anchor);
            assertFalse(menu.isShowing(), "a veto cannot block owner-detach auto-close");
            assertSame(CloseReason.OWNER_DETACHED, reason.get());
        });
    }

    @Test
    @Tag("ui")
    public void disposeWhileShowingReleasesAndHides() throws InterruptedException {
        runOnFx(() -> {
            Region anchor = shownAnchor();
            RXPopupMenu menu = quietMenu("A", "B");
            menu.show(anchor);
            assertTrue(menu.isShowing(), "precondition: showing");
            menu.dispose();
            assertFalse(menu.isShowing(), "dispose hides");
            menu.show(anchor);
            assertFalse(menu.isShowing(), "a disposed menu ignores show");
        });
    }

    // ==================== Helpers ====================

    private RXPopupMenu quietMenu(String... labels) {
        RXPopupMenu menu = new RXPopupMenu();
        menu.getMenuList().setAnimated(false);
        for (String label : labels) {
            menu.getItems().add(RXMenuItem.of(label));
        }
        return menu;
    }

    private Region shownAnchor() {
        Region anchor = new Region();
        anchor.setPrefSize(120, 30);
        anchor.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane root = new StackPane(anchor);
        stage = new Stage();
        stage.setScene(new Scene(root, 220, 120));
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        stage.setX(screen.getMinX() + 60);
        stage.setY(screen.getMinY() + 60);
        stage.show();
        root.applyCss();
        root.layout();
        return anchor;
    }

    private static void fireKey(Region target, KeyCode code) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
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
