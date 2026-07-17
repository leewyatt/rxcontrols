package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXSidebar.SidebarMode;
import javafx.application.Platform;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the sidebar's keyboard roving and accessibility wiring: Up/Down/
 * Home/End roving with wrap across the three lists, disabled items excluded from
 * the ring, the single roving Tab stop (the container is never it; within the
 * rail it follows focus, and it falls back to the selected item once focus is
 * elsewhere), and that assistive technology can name every item in both modes.
 *
 * <p>Focus is asserted through the scene's focus owner rather than
 * {@code Node.isFocused}, which is false whenever the window is not focused and
 * therefore always false in these headless runs.</p>
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
     * Regression (the white-row bug): a selection change must never strand the
     * focused item — focused yet not the Tab stop, so it holds scene focus while
     * being Tab-unreachable, and stays stuck showing the {@code :focused}
     * background because a later click elsewhere could not take focus from it.
     *
     * <p>The invariant is "never strand the focused item", not "focus follows
     * selection". Both halves are asserted here: the Tab stop follows focus (so
     * the focused item stays reachable, and the user's roving position is not
     * yanked by a programmatic selection), and a click does take focus away.</p>
     */
    @Test
    public void selectionNeverStrandsTheFocusedItem() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            sidebar.getItems().addAll(a, b);
            Scene scene = hostFor(sidebar).getScene();

            sidebar.selectItem(a);   // a is the sole Tab stop
            a.requestFocus();
            assertSame(a, scene.getFocusOwner());

            sidebar.selectItem(b);   // selection moves to b
            assertSame(a, scene.getFocusOwner(),
                    "a programmatic selection must not yank the user's roving position");
            assertSoleTabStop(a, a, b);  // ...and a stays reachable, i.e. not stranded

            // The other half: a click hands focus over even though the clicked
            // item is not the current Tab stop.
            pressMouse(b);
            assertSame(b, scene.getFocusOwner(),
                    "a click must take focus from the previously focused item");
            assertSoleTabStop(b, a, b);
        });
    }

    /**
     * An item may carry focusable content of its own (an actionable badge, say).
     * Focus sitting on that content is still focus in the rail: arrows keep roving
     * from the owning item rather than falling silent, and the Tab stop moves to
     * the owning item.
     *
     * <p>The content stays Tab-reachable in its own right — that is the caller's
     * node and their call. An item whose graphic should not take focus makes it
     * {@code focusTraversable = false}; the rail does not reach into an item and
     * decide that for them.</p>
     */
    @Test
    public void focusInsideAnItemStillRovesFromThatItem() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false);
            Button badge = new Button("3");
            RXSidebarNavItem a = new RXSidebarNavItem("Inbox", badge);
            RXSidebarNavItem b = new RXSidebarNavItem("Files");
            sidebar.getItems().addAll(a, b);
            Scene scene = hostFor(sidebar).getScene();

            badge.requestFocus();
            assertSame(badge, scene.getFocusOwner());
            assertSoleTabStop(a, a, b);   // the owning item, not the content

            press(scene, KeyCode.DOWN);
            assertSame(b, scene.getFocusOwner(), "arrows must rove on from the owning item");
        });
    }

    /**
     * The Tab stop follows focus however focus arrived — including a plain
     * {@code requestFocus()} from the application, which no roving or click path
     * observes. Without it the item would be focused yet Tab-unreachable, with a
     * second traversable item left in the traversal path.
     */
    @Test
    public void tabStopFollowsADirectRequestFocus() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            sidebar.getItems().addAll(a, b);
            Scene scene = hostFor(sidebar).getScene();

            sidebar.selectItem(a);
            assertSoleTabStop(a, a, b);

            b.requestFocus();   // neither roving nor a click
            assertSame(b, scene.getFocusOwner());
            assertSoleTabStop(b, a, b);
        });
    }

    /**
     * Once focus leaves the rail the Tab stop reverts to the selected item, so
     * Tabbing back in lands on the current destination rather than wherever the
     * user happened to rove last.
     */
    @Test
    public void tabStopRevertsToSelectionWhenFocusLeavesTheRail() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            sidebar.getItems().addAll(a, b);
            Button outside = new Button("Out");
            Scene scene = new Scene(new VBox(sidebar, outside), 400, 600);
            ((Pane) scene.getRoot()).applyCss();
            ((Pane) scene.getRoot()).layout();

            sidebar.selectItem(a);
            b.requestFocus();
            assertSoleTabStop(b, a, b);

            outside.requestFocus();
            assertSoleTabStop(a, a, b);  // back to the selected item
        });
    }

    /**
     * A membership or visibility change re-runs the Tab-stop rule. It must not
     * pull the stop back to the selected item while the user has roved elsewhere:
     * that would leave the focused item Tab-unreachable and put a second, higher
     * item in the traversal path, so Shift+Tab would re-enter the rail instead of
     * leaving it.
     */
    @Test
    public void tabStopStaysWithFocusAcrossRingChanges() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            RXSidebarNavItem c = new RXSidebarNavItem("C");
            sidebar.getItems().addAll(a, b, c);
            Scene scene = hostFor(sidebar).getScene();

            sidebar.selectItem(a);
            a.requestFocus();
            press(scene, KeyCode.DOWN);          // rove off the selected item
            assertSame(b, scene.getFocusOwner());
            assertSoleTabStop(b, a, b, c);

            RXSidebarNavItem d = new RXSidebarNavItem("D");
            sidebar.getItems().add(d);           // membership change
            assertSoleTabStop(b, a, b, c, d);

            c.setVisible(false);                 // visibility change
            assertSoleTabStop(b, a, b, d);
        });
    }

    /**
     * An action item must be click-focusable like any button, even though the
     * roving tab stop leaves it non-traversable: it never changes the selection,
     * so nothing else would ever hand it focus.
     */
    @Test
    public void clickingAnActionItemFocusesItAndMovesTheRovingPoint() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarActionItem settings = new RXSidebarActionItem("Settings");
            sidebar.getItems().add(a);
            sidebar.getBottomItems().add(settings);
            Scene scene = hostFor(sidebar).getScene();

            sidebar.selectItem(a);
            a.requestFocus();
            assertFalse(settings.isFocusTraversable(), "the action item starts non-traversable");

            pressMouse(settings);
            assertSame(settings, scene.getFocusOwner(), "clicking an action item must focus it");
            assertSoleTabStop(settings, a, settings);

            // Roving now continues from the clicked item, not from where focus used to be.
            press(scene, KeyCode.DOWN);
            assertSame(a, scene.getFocusOwner(), "roving resumes from the clicked item (wraps)");
        });
    }

    /**
     * Selection changes while the rail holds no focus must not steal focus into
     * the rail.
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
     * The requirement is that assistive technology can name every item, in both
     * modes — MINI hides the label, but the name must still get through. Labeled
     * already answers the TEXT query from its text regardless of content display,
     * so the skin owes nothing here; asserting the query (not some mirror the skin
     * might install) is what keeps that honest.
     */
    @Test
    public void assistiveTechnologyCanNameItemsInBothModes() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false);
            RXSidebarNavItem a = new RXSidebarNavItem("Inbox");
            RXSidebarActionItem s = new RXSidebarActionItem("Settings");
            sidebar.getItems().add(a);
            sidebar.getBottomItems().add(s);
            hostFor(sidebar);

            assertEquals("Inbox", a.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            assertEquals("Settings", s.queryAccessibleAttribute(AccessibleAttribute.TEXT));

            sidebar.setMode(SidebarMode.MINI); // label hidden
            assertEquals("Inbox", a.queryAccessibleAttribute(AccessibleAttribute.TEXT),
                    "the name must still reach assistive technology with the label hidden");

            a.setText("Archive");
            assertEquals("Archive", a.queryAccessibleAttribute(AccessibleAttribute.TEXT));
        });
    }

    /**
     * accessibleText stays the caller's: a nav rail item is exactly where an app
     * wants to say more than the label ("Inbox, 3 unread"), so the skin must not
     * own that property.
     */
    @Test
    public void accessibleTextRemainsTheCallersToSet() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("Inbox");
            sidebar.getItems().add(a);
            hostFor(sidebar);

            a.setAccessibleText("Inbox, 3 unread");
            assertEquals("Inbox, 3 unread", a.queryAccessibleAttribute(AccessibleAttribute.TEXT));

            // ...and it is not left behind on an item that leaves the rail.
            sidebar.getItems().remove(a);
            a.setAccessibleText(null);
            a.setText("Archive");
            assertEquals("Archive", a.queryAccessibleAttribute(AccessibleAttribute.TEXT));
        });
    }

    /**
     * Removing an item hands it back as it arrived: the caller's own
     * focus-traversability is restored, not merely reset to the type default.
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
        });
    }

    /**
     * Disposing the skin hands back the items it still had wired: their
     * focus-traversability is restored to what the caller had.
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
     * Re-adding a previously removed item re-wires it.
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

            // Re-wired: back in the roving ring and answering the name query.
            b.setText("Bee");
            assertEquals("Bee", b.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            b.requestFocus();
            assertSoleTabStop(b, a, b);
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
     * MINI hides the label, so the skin lends the item a tooltip carrying it, and
     * takes it back on the way out. {@code setAnimated(false)} is essential: with
     * the transition running, the mode is only committed to the items when the
     * timeline finishes, so an un-awaited test would assert nothing at all.
     */
    @Test
    public void miniModeLendsTheItemALabelTooltip() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false);
            RXSidebarNavItem a = new RXSidebarNavItem("Inbox");
            sidebar.getItems().add(a);
            hostFor(sidebar);

            assertNull(a.getTooltip(), "EXPANDED shows the label; no tooltip needed");

            sidebar.setMode(SidebarMode.MINI);
            assertNotNull(a.getTooltip(), "MINI must offer the label as a tooltip");
            assertEquals("Inbox", a.getTooltip().getText());
            // Via the tooltip property, not the static install: that is the only
            // thing assistive technology reads for HELP.
            assertEquals("Inbox", a.queryAccessibleAttribute(AccessibleAttribute.HELP));

            a.setText("Archive");
            assertEquals("Archive", a.getTooltip().getText(), "the lent tooltip tracks the text");

            sidebar.setMode(SidebarMode.EXPANDED);
            assertNull(a.getTooltip(), "the lent tooltip is taken back when the label returns");
        });
    }

    /**
     * An item that brought its own tooltip keeps it — in both modes, and after it
     * leaves the rail. A tooltip the caller set says more than a repeat of the
     * label, and replacing it would be silent theft.
     */
    @Test
    public void anItemsOwnTooltipIsNeverReplaced() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false);
            RXSidebarNavItem a = new RXSidebarNavItem("Inbox");
            Tooltip own = new Tooltip("Go to the inbox");
            a.setTooltip(own);
            sidebar.getItems().add(a);
            hostFor(sidebar);

            assertSame(own, a.getTooltip());
            sidebar.setMode(SidebarMode.MINI);
            assertSame(own, a.getTooltip(), "MINI must not replace the item's own tooltip");
            sidebar.setMode(SidebarMode.EXPANDED);
            assertSame(own, a.getTooltip());

            sidebar.getItems().remove(a);
            assertSame(own, a.getTooltip(), "the item keeps its tooltip on the way out");
        });
    }

    /**
     * The tooltip slot is read live, never replayed from a snapshot taken on the
     * way in. A caller who clears their own tooltip while the item is in the rail
     * has freed the slot; taking the lent tooltip back must leave it free, not
     * resurrect the one they deleted.
     */
    @Test
    public void clearingAnOwnTooltipInTheRailIsNotUndoneOnTheWayOut() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false);
            RXSidebarNavItem a = new RXSidebarNavItem("Home");
            a.setTooltip(new Tooltip("Go to the inbox"));
            sidebar.getItems().add(a);
            hostFor(sidebar);

            a.setTooltip(null);            // the caller changes their mind
            sidebar.setMode(SidebarMode.MINI);
            assertEquals("Home", a.getTooltip().getText(), "the freed slot may now be lent to");

            sidebar.getItems().remove(a);
            assertNull(a.getTooltip(), "a tooltip the caller deleted must stay deleted");
        });
    }

    /**
     * The slot is watched, not merely sampled when a mode is applied. MINI states
     * that hidden labels are exposed via tooltip, so a caller who frees the slot
     * while already in MINI must get the label tooltip back at once — otherwise the
     * item sits there unidentifiable until some unrelated mode change.
     */
    @Test
    public void freeingTheTooltipSlotWhileInMiniLendsTheLabelBackImmediately() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false);
            RXSidebarNavItem a = new RXSidebarNavItem("Home");
            Tooltip own = new Tooltip("Go home");
            a.setTooltip(own);
            sidebar.getItems().add(a);
            hostFor(sidebar);

            sidebar.setMode(SidebarMode.MINI);
            assertSame(own, a.getTooltip(), "the caller's tooltip still wins");

            a.setTooltip(null);   // the caller frees the slot, still in MINI
            assertNotNull(a.getTooltip(), "MINI must not leave the item without a tooltip");
            assertEquals("Home", a.getTooltip().getText());
        });
    }

    /**
     * A caller who takes the slot over while the rail's tooltip is lent keeps it:
     * from that moment the tooltip is theirs, on mode changes and on the way out.
     */
    @Test
    public void takingTheTooltipSlotOverMidLendWins() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setAnimated(false);
            RXSidebarNavItem a = new RXSidebarNavItem("Home");
            sidebar.getItems().add(a);
            hostFor(sidebar);

            sidebar.setMode(SidebarMode.MINI);   // rail lends its label tooltip
            Tooltip late = new Tooltip("Mine now");
            a.setTooltip(late);

            sidebar.setMode(SidebarMode.EXPANDED);
            assertSame(late, a.getTooltip(), "the rail must not clear a tooltip it no longer owns");
            sidebar.getItems().remove(a);
            assertSame(late, a.getTooltip());
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

    /** Fires MOUSE_PRESSED at a node — enough to exercise the roving hand-off. */
    private static void pressMouse(Node target) {
        target.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 5, 5, 5, 5,
                MouseButton.PRIMARY, 1, false, false, false, false,
                true, false, false, true, false, true, null));
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
