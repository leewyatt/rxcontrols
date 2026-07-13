package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.SingleSelectionModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Selection-semantics matrix for {@link RXTabPane} / {@link RXTabPaneSelectionModel}:
 * empty panes, initial selection, disabled handling, forward-first removal
 * recovery (APG), permutation resync, duplicate selection, and the all-disabled
 * boundary that leaves the pane unselected.
 */
public class RXTabPaneSelectionModelTest {

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

    // ==================== Initial selection ====================

    @Test
    public void emptyPaneHasNoSelection() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane();
            assertEquals(-1, pane.getSelectedIndex());
            assertNull(pane.getSelectedItem());
        });
    }

    @Test
    public void firstEnabledTabAutoSelected() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"), tab("C"));
            assertEquals(0, pane.getSelectedIndex());
            assertEquals("A", pane.getSelectedItem().getText());
        });
    }

    @Test
    public void initialSelectionSkipsDisabledFirst() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(disabledTab("A"), tab("B"), tab("C"));
            assertEquals(1, pane.getSelectedIndex());
        });
    }

    // ==================== Disabled boundary (catch-all availability) ====================

    @Test
    public void addingOnlyDisabledTabStaysUnselected() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane();
            pane.getTabs().add(disabledTab("A"));
            assertEquals(-1, pane.getSelectedIndex());
        });
    }

    @Test
    public void allDisabledStaysUnselected() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(disabledTab("A"), disabledTab("B"));
            assertEquals(-1, pane.getSelectedIndex());
            assertNull(pane.getSelectedItem());
        });
    }

    @Test
    public void addingEnabledTabAfterAllDisabledAutoSelectsIt() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(disabledTab("A"), disabledTab("B"));
            assertEquals(-1, pane.getSelectedIndex());
            RXTab c = tab("C");
            pane.getTabs().add(c);
            assertEquals(2, pane.getSelectedIndex());
            assertSame(c, pane.getSelectedItem());
        });
    }

    @Test
    public void disabledTabCanBeSelectedProgrammatically() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), disabledTab("B"), tab("C"));
            pane.getSelectionModel().select(1);
            assertEquals(1, pane.getSelectedIndex());
            assertTrue(pane.getSelectedItem().isDisable());
        });
    }

    // ==================== Removal recovery (APG: forward-first) ====================

    @Test
    public void removingSelectedRecoversForward() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            RXTab b = tab("B");
            RXTab c = tab("C");
            RXTabPane pane = new RXTabPane(a, b, c);
            pane.getSelectionModel().select(0);
            pane.getTabs().remove(a);
            // Prefer the tab that followed the removed one: b (now at index 0).
            assertSame(b, pane.getSelectedItem());
            assertEquals(0, pane.getSelectedIndex());
        });
    }

    @Test
    public void removalRecoverySkipsDisabledForward() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            RXTab b = disabledTab("B");
            RXTab c = tab("C");
            RXTabPane pane = new RXTabPane(a, b, c);
            pane.getSelectionModel().select(0);
            pane.getTabs().remove(a);
            // Forward from index 0: b disabled -> skip -> c.
            assertSame(c, pane.getSelectedItem());
        });
    }

    @Test
    public void removingLastSelectedRecoversBackward() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            RXTab b = tab("B");
            RXTab c = tab("C");
            RXTabPane pane = new RXTabPane(a, b, c);
            pane.getSelectionModel().select(2);
            pane.getTabs().remove(c);
            // Nothing forward -> fall back to the previous tab b.
            assertSame(b, pane.getSelectedItem());
            assertEquals(1, pane.getSelectedIndex());
        });
    }

    @Test
    public void removingSelectedWithOnlyDisabledRemainingStaysUnselected() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            RXTab b = disabledTab("B");
            RXTabPane pane = new RXTabPane(a, b);
            pane.getSelectionModel().select(0);
            pane.getTabs().remove(a);
            assertEquals(-1, pane.getSelectedIndex());
            assertNull(pane.getSelectedItem());
            assertFalse(a.isSelected());
        });
    }

    @Test
    public void removingUnselectedTabKeepsSelection() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            RXTab b = tab("B");
            RXTab c = tab("C");
            RXTabPane pane = new RXTabPane(a, b, c);
            pane.getSelectionModel().select(2);
            pane.getTabs().remove(a);
            // Removing an unselected tab before the selection: selection unchanged,
            // index re-synced.
            assertSame(c, pane.getSelectedItem());
            assertEquals(1, pane.getSelectedIndex());
        });
    }

    // ==================== Permutation ====================

    @Test
    public void permutationResyncsIndexKeepsSelectedItem() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            RXTab b = tab("B");
            RXTab c = tab("C");
            RXTabPane pane = new RXTabPane(a, b, c);
            pane.getSelectionModel().select(1);
            assertSame(b, pane.getSelectedItem());

            // Reverse-sort by text (C, B, A): a real permutation change.
            FXCollections.sort(pane.getTabs(), Comparator.comparing(RXTab::getText).reversed());
            assertSame(b, pane.getSelectedItem());
            assertEquals(1, pane.getSelectedIndex());
        });
    }

    // ==================== Duplicate ====================

    @Test
    public void selectPicksFirstMatchingTab() throws Exception {
        runOnFx(() -> {
            RXTab shared = tab("Shared");
            RXTab other = tab("Other");
            RXTabPane pane = new RXTabPane();
            pane.getTabs().addAll(shared, other, shared);
            pane.getSelectionModel().select(other);
            pane.getSelectionModel().select(shared);
            assertEquals(0, pane.getSelectedIndex());
        });
    }

    // ==================== clearSelection ====================

    @Test
    public void clearSelectionReachesMinusOneWithoutStructuralChange() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            RXTab b = tab("B");
            RXTabPane pane = new RXTabPane(a, b);
            pane.getSelectionModel().select(1);
            pane.getSelectionModel().clearSelection();
            assertEquals(-1, pane.getSelectedIndex());
            assertNull(pane.getSelectedItem());
            assertFalse(b.isSelected());
        });
    }

    @Test
    public void clearingAllTabsClearsSelection() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            RXTab b = tab("B");
            RXTabPane pane = new RXTabPane(a, b);
            pane.getSelectionModel().select(0);
            assertTrue(a.isSelected());

            pane.getTabs().clear();
            assertEquals(-1, pane.getSelectedIndex());
            assertNull(pane.getSelectedItem());
            assertFalse(a.isSelected());
            assertNull(a.getTabPane());
        });
    }

    @Test
    public void reentrantSelectionStaysConsistent() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            RXTab b = tab("B");
            RXTab c = tab("C");
            RXTabPane pane = new RXTabPane(a, b, c);
            SingleSelectionModel<RXTab> model = pane.getSelectionModel();
            // A listener that re-entrantly redirects the first move to index 2.
            boolean[] redirected = {false};
            model.selectedIndexProperty().addListener((obs, ov, nv) -> {
                if (nv.intValue() == 1 && !redirected[0]) {
                    redirected[0] = true;
                    model.select(2);
                }
            });
            model.select(1);
            // After the re-entrant redirect, index and item must both point at c
            // (the select() re-reads the index before deriving the item).
            assertEquals(2, model.getSelectedIndex());
            assertSame(c, model.getSelectedItem());
            assertEquals(2, pane.getSelectedIndex());
            assertSame(c, pane.getSelectedItem());
        });
    }

    @Test
    public void nullTabDoesNotPoisonCatchAll() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane();
            // A stray null tab must not drive a phantom selection via indexOf(null)==0;
            // the resync guard leaves selection empty so the catch-all still fires.
            pane.getTabs().add(null);
            assertEquals(-1, pane.getSelectedIndex());
            assertNull(pane.getSelectedItem());

            RXTab real = tab("Real");
            pane.getTabs().add(real);
            // The enabled tab is auto-selected (catch-all not poisoned by the null).
            assertEquals(1, pane.getSelectedIndex());
            assertSame(real, pane.getSelectedItem());
        });
    }

    @Test
    public void outOfRangeSelectIgnored() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"));
            pane.getSelectionModel().select(0);
            pane.getSelectionModel().select(5);
            assertEquals(0, pane.getSelectedIndex());
        });
    }

    @Test
    public void projectionsTrackModel() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"), tab("C"));
            SingleSelectionModel<RXTab> model = pane.getSelectionModel();
            model.select(2);
            assertEquals(model.getSelectedIndex(), pane.getSelectedIndex());
            assertSame(model.getSelectedItem(), pane.getSelectedItem());
        });
    }

    // ==================== Helpers ====================

    private static RXTab tab(String text) {
        return new RXTab(text);
    }

    private static RXTab disabledTab(String text) {
        RXTab tab = new RXTab(text);
        tab.setDisable(true);
        return tab;
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
