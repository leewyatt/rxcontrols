package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXTabPane.TabClosingPolicy;
import javafx.application.Platform;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.StackPane;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closable-tab behaviour for {@link RXTabPane}: {@link TabClosingPolicy}
 * button visibility, the {@code TAB_CLOSE_REQUEST}/{@code TAB_CLOSED} event
 * pipeline (tab- and pane-level veto, the fresh-{@code TAB_CLOSED}-per-listener
 * trap, fixed tab&rarr;pane order), and the mouse / keyboard close affordances.
 */
public class RXTabPaneClosingTest {

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

    // ==================== Close-button visibility ====================

    @Test
    public void unavailablePolicyHidesAllCloseButtons() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), tab("B")));
            // UNAVAILABLE is the default.
            assertFalse(closeButton(pane, 0).isVisible());
            assertFalse(closeButton(pane, 1).isVisible());
        });
    }

    @Test
    public void allTabsPolicyShowsButtonsForClosableTabsOnly() throws Exception {
        runOnFx(() -> {
            RXTab pinned = tab("Pinned");
            pinned.setClosable(false);
            RXTabPane pane = new RXTabPane(tab("A"), pinned, tab("C"));
            pane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
            laidOut(pane);
            assertTrue(closeButton(pane, 0).isVisible());
            assertFalse(closeButton(pane, 1).isVisible());
            assertTrue(closeButton(pane, 2).isVisible());
        });
    }

    @Test
    public void selectedTabPolicyShowsButtonOnlyOnSelection() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"), tab("C"));
            pane.setTabClosingPolicy(TabClosingPolicy.SELECTED_TAB);
            laidOut(pane);
            assertTrue(closeButton(pane, 0).isVisible());
            assertFalse(closeButton(pane, 1).isVisible());

            pane.getSelectionModel().select(1);
            pane.layout();
            assertFalse(closeButton(pane, 0).isVisible());
            assertTrue(closeButton(pane, 1).isVisible());
        });
    }

    @Test
    public void policyChangeAfterLayoutUpdatesButtons() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), tab("B")));
            assertFalse(closeButton(pane, 0).isVisible());

            pane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
            pane.layout();
            assertTrue(closeButton(pane, 0).isVisible());
            assertTrue(closeButton(pane, 1).isVisible());
        });
    }

    @Test
    public void closeButtonIsNotAFocusStop() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"));
            pane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
            laidOut(pane);
            assertFalse(closeButton(pane, 0).isFocusTraversable());
        });
    }

    // ==================== Close pipeline: happy path ====================

    @Test
    public void closeButtonFireRemovesTab() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            RXTab b = tab("B");
            RXTabPane pane = new RXTabPane(a, b);
            pane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
            laidOut(pane);

            closeButton(pane, 0).fire();
            assertEquals(1, pane.getTabs().size());
            assertSame(b, pane.getTabs().get(0));
        });
    }

    @Test
    public void closingSelectedTabRecoversSelection() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            RXTab b = tab("B");
            RXTab c = tab("C");
            RXTabPane pane = new RXTabPane(a, b, c);
            pane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
            laidOut(pane);
            // A is selected; closing it recovers forward to B.
            closeButton(pane, 0).fire();
            assertSame(b, pane.getSelectedItem());
            assertFalse(a.isSelected());
        });
    }

    @Test
    public void closeRequestEventIsWellFormed() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            RXTabPane pane = new RXTabPane(a, tab("B"));
            pane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
            laidOut(pane);

            AtomicReference<EventTarget> target = new AtomicReference<>();
            AtomicReference<RXTab> eventTab = new AtomicReference<>();
            pane.addEventHandler(RXTabEvent.TAB_CLOSE_REQUEST, e -> {
                target.set(e.getTarget());
                eventTab.set(e.getTab());
            });

            closeButton(pane, 0).fire();
            // Target is the pane; the tab is carried on the dedicated getTab() field
            // (getSource() is rewritten to the pane as the event bubbles).
            assertSame(pane, target.get());
            assertSame(a, eventTab.get());
        });
    }

    // ==================== Close pipeline: veto ====================

    @Test
    public void tabLevelHandlerCanVetoClose() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            a.setOnCloseRequest(e -> e.consume());
            RXTabPane pane = new RXTabPane(a, tab("B"));
            pane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
            laidOut(pane);

            closeButton(pane, 0).fire();
            assertEquals(2, pane.getTabs().size());
            assertSame(a, pane.getTabs().get(0));
        });
    }

    @Test
    public void paneLevelHandlerCanVetoClose() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            RXTabPane pane = new RXTabPane(a, tab("B"));
            pane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
            pane.addEventHandler(RXTabEvent.TAB_CLOSE_REQUEST, e -> e.consume());
            laidOut(pane);

            closeButton(pane, 0).fire();
            assertEquals(2, pane.getTabs().size());
        });
    }

    @Test
    public void vetoedCloseFiresNoClosedEvent() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            a.setOnCloseRequest(e -> e.consume());
            boolean[] closedRan = {false};
            a.setOnClosed(e -> closedRan[0] = true);
            RXTabPane pane = new RXTabPane(a, tab("B"));
            pane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
            pane.addEventHandler(RXTabEvent.TAB_CLOSED, e -> closedRan[0] = true);
            laidOut(pane);

            closeButton(pane, 0).fire();
            assertFalse(closedRan[0]);
        });
    }

    // ==================== Close pipeline: TAB_CLOSED fresh-event trap ====================

    @Test
    public void consumingTabClosedDoesNotGagPaneHandler() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            boolean[] tabClosedRan = {false};
            boolean[] paneClosedRan = {false};
            // A consumed tab-level TAB_CLOSED must not suppress the pane handler:
            // the pipeline fires two FRESH events, one per listener.
            a.setOnClosed(e -> {
                tabClosedRan[0] = true;
                e.consume();
            });
            RXTabPane pane = new RXTabPane(a, tab("B"));
            pane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
            pane.addEventHandler(RXTabEvent.TAB_CLOSED, e -> paneClosedRan[0] = true);
            laidOut(pane);

            closeButton(pane, 0).fire();
            assertTrue(tabClosedRan[0]);
            assertTrue(paneClosedRan[0]);
        });
    }

    @Test
    public void closeRequestHandlerRemovingTabSuppressesSpuriousClosed() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            int[] closedCount = {0};
            RXTabPane pane = new RXTabPane(a, tab("B"));
            pane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
            // The request handler removes the tab itself without consuming; the
            // pipeline must not then fire a TAB_CLOSED for a tab it did not remove.
            a.setOnCloseRequest(e -> pane.getTabs().remove(a));
            pane.addEventHandler(RXTabEvent.TAB_CLOSED, e -> closedCount[0]++);
            laidOut(pane);

            closeButton(pane, 0).fire();
            assertFalse(pane.getTabs().contains(a));
            assertEquals(0, closedCount[0]);
        });
    }

    @Test
    public void closedEventsFireInTabThenPaneOrder() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            List<String> order = new ArrayList<>();
            a.setOnClosed(e -> order.add("tab"));
            RXTabPane pane = new RXTabPane(a, tab("B"));
            pane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
            pane.addEventHandler(RXTabEvent.TAB_CLOSED, e -> order.add("pane"));
            laidOut(pane);

            closeButton(pane, 0).fire();
            assertEquals(List.of("tab", "pane"), order);
        });
    }

    // ==================== Mouse / keyboard affordances ====================

    @Test
    public void middleClickClosesClosableTab() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            RXTabPane pane = new RXTabPane(a, tab("B"));
            pane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
            laidOut(pane);

            middleClick(cellAt(pane, 0));
            assertEquals(1, pane.getTabs().size());
            assertFalse(pane.getTabs().contains(a));
        });
    }

    @Test
    public void middleClickIgnoredWhenClosingUnavailable() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), tab("B")));
            // Default UNAVAILABLE policy: middle-click must not close.
            middleClick(cellAt(pane, 0));
            assertEquals(2, pane.getTabs().size());
        });
    }

    @Test
    public void deleteKeyClosesFocusedClosableTab() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            RXTabPane pane = new RXTabPane(a, tab("B"));
            pane.setTabClosingPolicy(TabClosingPolicy.SELECTED_TAB);
            laidOut(pane);
            // A is focused + selected; SELECTED_TAB allows its close.
            press(pane, KeyCode.DELETE);
            assertFalse(pane.getTabs().contains(a));
        });
    }

    @Test
    public void deleteAdvancesSelectionToNextTab() throws Exception {
        runOnFx(() -> {
            RXTab b = tab("B");
            RXTab c = tab("C");
            RXTabPane pane = new RXTabPane(tab("A"), b, c);
            pane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
            laidOut(pane);
            pane.getSelectionModel().select(1);   // B focused + selected
            press(pane, KeyCode.DELETE);
            // Forward-first recovery after closing the selected tab: C takes selection.
            assertFalse(pane.getTabs().contains(b));
            assertSame(c, pane.getSelectionModel().getSelectedItem());
        });
    }

    @Test
    public void deleteKeyIgnoredWhenClosingUnavailable() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), tab("B")));
            press(pane, KeyCode.DELETE);
            assertEquals(2, pane.getTabs().size());
        });
    }

    @Test
    public void deleteKeyDoesNotCloseDisabledTab() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            a.setDisable(true);
            RXTabPane pane = new RXTabPane(a, tab("B"));
            pane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
            laidOut(pane);
            // Select (and thus focus) the disabled tab, then press Delete: a disabled
            // tab is inert and must not be closed by the keyboard.
            pane.getSelectionModel().select(0);
            press(pane, KeyCode.DELETE);
            assertTrue(pane.getTabs().contains(a));
        });
    }

    @Test
    public void deleteFromFocusedContentIsIgnored() throws Exception {
        runOnFx(() -> {
            RXTab b = RXTab.of("B", new StackPane());
            RXTabPane pane = new RXTabPane(tab("A"), b, tab("C"));
            pane.setTabClosingPolicy(TabClosingPolicy.ALL_TABS);
            laidOut(pane);
            pane.getSelectionModel().select(1);
            // A Delete targeted at a node inside the tab content (bubbles up to the
            // pane with target == content) must not be hijacked to close the tab.
            Node content = b.getContent();
            content.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DELETE,
                    false, false, false, false));
            assertTrue(pane.getTabs().contains(b));
        });
    }

    @Test
    public void primaryClickSelectsTab() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), tab("B"), tab("C")));
            primaryClick(cellAt(pane, 2));
            assertEquals(2, pane.getSelectedIndex());
        });
    }

    // ==================== Helpers ====================

    private static Button closeButton(RXTabPane pane, int index) {
        Node cell = cellAt(pane, index);
        Node button = cell.lookup(".close-button");
        assertNotNull(button, "close button not found in cell " + index);
        return (Button) button;
    }

    private static Node cellAt(RXTabPane pane, int index) {
        return (Node) pane.queryAccessibleAttribute(
                javafx.scene.AccessibleAttribute.ITEM_AT_INDEX, index);
    }

    private static void primaryClick(Node cell) {
        cell.fireEvent(mousePress(cell, MouseButton.PRIMARY));
    }

    private static void middleClick(Node cell) {
        cell.fireEvent(mousePress(cell, MouseButton.MIDDLE));
    }

    private static MouseEvent mousePress(Node cell, MouseButton button) {
        boolean middle = button == MouseButton.MIDDLE;
        boolean primary = button == MouseButton.PRIMARY;
        return new MouseEvent(MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, button, 1,
                false, false, false, false,
                primary, middle, false,
                false, false, false,
                new PickResult(cell, 0, 0));
    }

    private static void press(RXTabPane pane, KeyCode code) {
        pane.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false));
    }

    private static RXTabPane laidOut(RXTabPane pane) {
        StackPane root = new StackPane(pane);
        new Scene(root, 640, 400);
        root.applyCss();
        root.layout();
        return pane;
    }

    private static RXTab tab(String text) {
        return new RXTab(text);
    }

    private static void runOnFx(FxAction action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable error = failure.get();
        if (error instanceof Exception) {
            throw (Exception) error;
        }
        if (error != null) {
            throw new AssertionError(error);
        }
    }

    @FunctionalInterface
    private interface FxAction {
        void run() throws Exception;
    }
}
