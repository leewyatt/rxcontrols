package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXSidebar.SidebarMode;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P4 gate tests for the sidebar's keyboard roving and accessibility wiring:
 * Up/Down/Home/End roving with wrap across the three lists, disabled items
 * excluded from the ring, the single roving Tab stop (container not traversable,
 * one item traversable tracking the selection, migrating on roving), and
 * {@code accessibleText} mirroring {@code text}. Focus is driven via the scene
 * focus owner (Node.isFocused is always false headless — javafx-notes §6.7).
 */
public class RXSidebarKeyboardA11yTest {

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

    /**
     * Down/Up rove between items and wrap at the ends.
     */
    @Test
    public void arrowRovingWraps() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            RXSidebarNavItem c = new RXSidebarNavItem("C");
            sidebar.getItems().addAll(a, b, c);
            Scene scene = hostFor(sidebar).getScene();

            a.requestFocus();
            assertSame(a, scene.getFocusOwner());

            press(scene, KeyCode.DOWN);
            assertSame(b, scene.getFocusOwner());
            press(scene, KeyCode.DOWN);
            assertSame(c, scene.getFocusOwner());
            press(scene, KeyCode.DOWN); // wrap to first
            assertSame(a, scene.getFocusOwner());
            press(scene, KeyCode.UP);   // wrap to last
            assertSame(c, scene.getFocusOwner());
        });
    }

    /**
     * Roving spans top, main, and bottom lists as one ring.
     */
    @Test
    public void rovingSpansAllThreeLists() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem top = new RXSidebarNavItem("T");
            RXSidebarNavItem main = new RXSidebarNavItem("M");
            RXSidebarActionItem bottom = new RXSidebarActionItem("B");
            sidebar.getTopItems().add(top);
            sidebar.getItems().add(main);
            sidebar.getBottomItems().add(bottom);
            Scene scene = hostFor(sidebar).getScene();

            top.requestFocus();
            press(scene, KeyCode.DOWN);
            assertSame(main, scene.getFocusOwner());
            press(scene, KeyCode.DOWN);
            assertSame(bottom, scene.getFocusOwner());
            press(scene, KeyCode.DOWN); // wrap
            assertSame(top, scene.getFocusOwner());
        });
    }

    /**
     * Disabled items are excluded from the roving ring.
     */
    @Test
    public void rovingSkipsDisabled() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            RXSidebarNavItem c = new RXSidebarNavItem("C");
            b.setDisable(true);
            sidebar.getItems().addAll(a, b, c);
            Scene scene = hostFor(sidebar).getScene();

            a.requestFocus();
            press(scene, KeyCode.DOWN);
            assertSame(c, scene.getFocusOwner(), "disabled b must be skipped");
        });
    }

    /**
     * Home / End jump to the first / last ring members.
     */
    @Test
    public void homeEndJumpToEdges() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            RXSidebarNavItem c = new RXSidebarNavItem("C");
            sidebar.getItems().addAll(a, b, c);
            Scene scene = hostFor(sidebar).getScene();

            b.requestFocus();
            press(scene, KeyCode.HOME);
            assertSame(a, scene.getFocusOwner());
            press(scene, KeyCode.END);
            assertSame(c, scene.getFocusOwner());
        });
    }

    /**
     * The container is not a Tab stop; exactly one item is, defaulting to the
     * first and tracking the selection.
     */
    @Test
    public void singleTabStopTracksSelection() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            RXSidebarNavItem c = new RXSidebarNavItem("C");
            sidebar.getItems().addAll(a, b, c);
            hostFor(sidebar);

            assertFalse(sidebar.isFocusTraversable(), "rail container is not a Tab stop");
            assertSoleTabStop(a, a, b, c); // no selection -> first

            sidebar.selectItem(b);
            assertSoleTabStop(b, a, b, c); // selection -> selected item
        });
    }

    /**
     * The single Tab stop migrates to the focused item while roving.
     */
    @Test
    public void tabStopMigratesOnRoving() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            sidebar.getItems().addAll(a, b);
            Scene scene = hostFor(sidebar).getScene();

            assertSoleTabStop(a, a, b);
            a.requestFocus();
            press(scene, KeyCode.DOWN);
            assertSame(b, scene.getFocusOwner());
            assertSoleTabStop(b, a, b); // migrated to the focused item
        });
    }

    /**
     * Regression: when the focused (and selected) item is the sole Tab stop,
     * selecting another item migrates focus to the new selection instead of
     * stranding it on the old item. A stranded item stays focus-traversable=false
     * yet keeps scene focus, so it never relinquishes it on a later click of
     * another non-traversable item — leaving it stuck showing the {@code :focused}
     * background (the white-row bug).
     */
    @Test
    public void selectionMigratesFocusOffStrandedItem() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            sidebar.getItems().addAll(a, b);
            Scene scene = hostFor(sidebar).getScene();

            sidebar.selectItem(a);   // a is the sole Tab stop
            a.requestFocus();             // re-clicking the selected Tab stop focuses it
            assertSame(a, scene.getFocusOwner());

            sidebar.selectItem(b);   // selecting another item
            assertSame(b, scene.getFocusOwner(),
                    "focus migrates to the newly selected item, not stranded on a");
            assertSoleTabStop(b, a, b);
        });
    }

    /**
     * Selection changes while the rail holds no focus must not steal focus into
     * the rail (the migration is guarded on the rail already owning focus).
     */
    @Test
    public void selectionWithoutRailFocusDoesNotStealFocus() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            sidebar.getItems().addAll(a, b);
            Button outside = new Button("Out");
            Scene scene = new Scene(new VBox(sidebar, outside), 400, 600);
            ((Pane) scene.getRoot()).applyCss();
            ((Pane) scene.getRoot()).layout();

            outside.requestFocus();
            assertSame(outside, scene.getFocusOwner());

            sidebar.selectItem(a);
            sidebar.selectItem(b);
            assertSame(outside, scene.getFocusOwner(),
                    "programmatic selection must not pull focus into the rail");
        });
    }

    /**
     * accessibleText mirrors text live (skin-applied binding).
     */
    @Test
    public void accessibleTextMirrorsText() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("Inbox");
            RXSidebarActionItem s = new RXSidebarActionItem("Settings");
            sidebar.getItems().add(a);
            sidebar.getBottomItems().add(s);
            hostFor(sidebar);

            assertEquals("Inbox", a.getAccessibleText());
            assertEquals("Settings", s.getAccessibleText());
            a.setText("Archive");
            assertEquals("Archive", a.getAccessibleText());
        });
    }

    /**
     * Removing an item unwires it: accessibleText stops mirroring and its
     * focus-traversability is restored to the default.
     */
    @Test
    public void removingItemUnwiresIt() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            sidebar.getItems().addAll(a, b);
            sidebar.selectItem(a); // a is the Tab stop, b is demoted
            hostFor(sidebar);
            assertFalse(b.isFocusTraversable());

            sidebar.getItems().remove(b);
            assertTrue(b.isFocusTraversable(), "removed item's focus-traversability restored");
            String before = b.getAccessibleText();
            b.setText("Changed");
            assertEquals(before, b.getAccessibleText(), "removed item's accessibleText unbound");
        });
    }

    /**
     * Disposing the skin unbinds accessibleText and restores focus-traversability
     * for items left wired.
     */
    @Test
    public void disposeUnwiresRemainingItems() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            sidebar.getItems().addAll(a, b);
            sidebar.selectItem(a); // b demoted to non-traversable
            hostFor(sidebar);
            assertFalse(b.isFocusTraversable());

            sidebar.setSkin(null); // dispose

            assertTrue(b.isFocusTraversable());
            String before = b.getAccessibleText();
            b.setText("Changed");
            assertEquals(before, b.getAccessibleText());
        });
    }

    /**
     * The capturing filter on root consumes arrow keys before they bubble to the
     * ScrollPane (which would otherwise scroll), pinning the rationale for D2.
     */
    @Test
    public void captureFilterPreemptsScrollPane() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            sidebar.getItems().addAll(a, b);
            Scene scene = hostFor(sidebar).getScene();
            ScrollPane mainScroll = mainScrollOf(sidebar);

            AtomicInteger bubbledToScroll = new AtomicInteger();
            mainScroll.addEventHandler(KeyEvent.KEY_PRESSED, e -> bubbledToScroll.incrementAndGet());

            a.requestFocus();
            press(scene, KeyCode.DOWN);

            assertSame(b, scene.getFocusOwner());
            assertEquals(0, bubbledToScroll.get(),
                    "arrow must be consumed by the root capture filter before reaching the ScrollPane");
        });
    }

    /**
     * The rail's capture filter does NOT consume arrow keys aimed at header/footer
     * content (the onKeyPressed guard returns when focus is not a ring item), so
     * such content still receives them. (JavaFX's own directional traversal may
     * then move focus — that is the platform default, not the rail acting.)
     */
    @Test
    public void headerContentKeepsItsArrowKeys() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.getItems().add(new RXSidebarNavItem("A"));
            Button headerButton = new Button("H");
            AtomicInteger received = new AtomicInteger();
            headerButton.addEventHandler(KeyEvent.KEY_PRESSED, e -> received.incrementAndGet());
            sidebar.setHeader(headerButton);
            Scene scene = hostFor(sidebar).getScene();

            headerButton.requestFocus();
            press(scene, KeyCode.DOWN);
            assertEquals(1, received.get(),
                    "rail filter must not consume arrows aimed at header content");
        });
    }

    /**
     * An empty rail with a focusable header handles key presses without throwing
     * and without moving focus.
     */
    @Test
    public void emptyRailHandlesKeysGracefully() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            Button headerButton = new Button("H");
            sidebar.setHeader(headerButton);
            Scene scene = hostFor(sidebar).getScene();

            headerButton.requestFocus();
            assertDoesNotThrow(() -> {
                press(scene, KeyCode.DOWN);
                press(scene, KeyCode.UP);
                press(scene, KeyCode.HOME);
                press(scene, KeyCode.END);
            });
            assertSame(headerButton, scene.getFocusOwner());
        });
    }

    /**
     * An all-disabled rail (empty roving ring) is handled without IndexOOB:
     * construction, lenient selection of a disabled item, and a mode toggle.
     */
    @Test
    public void allDisabledRailHandledGracefully() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            a.setDisable(true);
            b.setDisable(true);
            sidebar.getItems().addAll(a, b);
            Pane host = hostFor(sidebar);

            assertDoesNotThrow(() -> {
                sidebar.selectItem(a); // disabled + not in ring -> preferredTabStop null
                sidebar.setMode(SidebarMode.MINI);
                host.applyCss();
                host.layout();
            });
        });
    }

    /**
     * Removing the item that is currently the sole Tab stop re-establishes the
     * Tab stop on the surviving ring and restores the removed item.
     */
    @Test
    public void removingActiveTabStopReestablishesIt() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            sidebar.getItems().addAll(a, b);
            sidebar.selectItem(a);
            hostFor(sidebar);
            assertSoleTabStop(a, a, b);

            sidebar.getItems().remove(a); // remove the active Tab stop
            assertTrue(a.isFocusTraversable(), "removed Tab stop restored to default");
            assertSoleTabStop(b, b);
        });
    }

    /**
     * Re-adding a previously removed item re-wires it (its accessibleText mirrors
     * text again).
     */
    @Test
    public void reAddingItemReWiresIt() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            sidebar.getItems().addAll(a, b);
            hostFor(sidebar);

            sidebar.getItems().remove(b);
            sidebar.getItems().add(b);
            b.setText("Bee");
            assertEquals("Bee", b.getAccessibleText());
        });
    }

    /**
     * When the current Tab stop item becomes invisible it leaves the ring, and a
     * visible sibling becomes the new Tab stop (focusability listener fires).
     */
    @Test
    public void visibilityChangeReestablishesTabStop() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            sidebar.getItems().addAll(a, b);
            sidebar.selectItem(a);
            hostFor(sidebar);
            assertSoleTabStop(a, a, b);

            a.setVisible(false); // a leaves the ring; the listener re-establishes the Tab stop
            assertTrue(b.isFocusTraversable(), "visible sibling becomes the Tab stop");
        });
    }

    /**
     * MINI mode exercises the tooltip install path without throwing, and the
     * name source (accessibleText, sharing the tooltip's text binding) holds.
     */
    @Test
    public void miniModeInstallsTooltipPathSafely() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("Inbox");
            sidebar.getItems().add(a);
            Pane host = hostFor(sidebar);

            assertDoesNotThrow(() -> {
                sidebar.setMode(SidebarMode.MINI); // installs the auto tooltip
                host.applyCss();
                host.layout();
                sidebar.setMode(SidebarMode.EXPANDED); // uninstalls it
                host.applyCss();
                host.layout();
            });
            assertEquals("Inbox", a.getAccessibleText());
        });
    }

    // ==================== Helpers ====================

    private static ScrollPane mainScrollOf(RXSidebar sidebar) {
        VBox root = (VBox) sidebar.getChildrenUnmodifiable().get(0);
        return (ScrollPane) root.getChildren().get(2);
    }

    private static void assertSoleTabStop(Node expected, Node... all) {
        for (Node node : all) {
            assertEquals(node == expected, node.isFocusTraversable(),
                    "focus-traversable expectation for " + node);
        }
    }

    private static void press(Scene scene, KeyCode code) {
        Node target = scene.getFocusOwner();
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false));
    }

    private static Pane hostFor(RXSidebar sidebar) {
        Pane host = new Pane(sidebar);
        new Scene(host, 400, 600);
        host.applyCss();
        host.layout();
        return host;
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
            } catch (Throwable ex) {
                failure.set(ex);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable ex = failure.get();
        if (ex instanceof Exception) {
            throw (Exception) ex;
        }
        if (ex != null) {
            throw new AssertionError(ex);
        }
    }
}
