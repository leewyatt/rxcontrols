package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import io.github.leewyatt.rxcontrols.skins.RXChipSetSkin;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXChipSet}: defaults, chip-list mirroring into the composed
 * flow pane, the single / multiple / none / allow-empty selection model, the
 * derived {@code selectedChips} and selection event, wrap sizing (horizontal
 * content bias) and arrow-key roving focus.
 */
public class RXChipSetTest {

    /**
     * Starts the JavaFX toolkit so skins, CSS and events can run.
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

    // ==================== Defaults ====================

    /**
     * Verifies default state, style class, skin and horizontal content bias.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void defaultStateAndSkin() throws Exception {
        runOnFx(() -> {
            RXChipSet set = new RXChipSet();
            assertTrue(set.getStyleClass().contains("rx-chip-set"));
            assertSame(RXChipSet.SelectionMode.NONE, set.getSelectionMode());
            assertTrue(set.isAllowEmptySelection());
            assertEquals(RXChipSet.DEFAULT_GAP, set.getHgap());
            assertEquals(RXChipSet.DEFAULT_GAP, set.getVgap());
            assertSame(Pos.TOP_LEFT, set.getAlignment());
            assertTrue(set.getChips().isEmpty());
            assertTrue(set.getSelectedChips().isEmpty());
            assertSame(Orientation.HORIZONTAL, set.getContentBias());
            assertNotNull(set.getUserAgentStylesheet());
            assertTrue(set.createDefaultSkin() instanceof RXChipSetSkin);
        });
    }

    /**
     * Verifies the vararg constructor seeds the chip list.
     */
    @Test
    public void varargConstructorSeedsChips() {
        RXChip a = new RXChip("a");
        RXChip b = new RXChip("b");
        RXChipSet set = new RXChipSet(a, b);
        assertEquals(List.of(a, b), set.getChips());
    }

    /**
     * Verifies the chips are mirrored into the composed flow pane and that a later
     * mutation stays in sync.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void chipsAreMirroredIntoTheFlowPane() throws Exception {
        runOnFx(() -> {
            RXChip a = new RXChip("a");
            RXChip b = new RXChip("b");
            RXChipSet set = attach(new RXChipSet(a, b));
            RXFlowPane flow = (RXFlowPane) set.lookup(".rx-flow-pane");
            assertNotNull(flow);
            assertEquals(List.of(a, b), flow.getChildrenUnmodifiable());

            RXChip c = new RXChip("c");
            set.getChips().add(c);
            assertEquals(List.of(a, b, c), flow.getChildrenUnmodifiable());
            set.getChips().remove(a);
            assertEquals(List.of(b, c), flow.getChildrenUnmodifiable());
        });
    }

    /**
     * Verifies mutating the chips does not detach the chip nodes already in place: the skin
     * reconciles the flow pane's children in place rather than rebuilding them with a full
     * {@code setAll}, so an unchanged chip is never removed from the scene — which would
     * drop the bounded ripple clip its skin installed and reset its hover / press state.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void mutatingChipsDoesNotDetachExistingChipNodes() throws Exception {
        runOnFx(() -> {
            RXChip a = new RXChip("a");
            RXChip b = new RXChip("b");
            RXChipSet set = attach(new RXChipSet(a, b));
            int[] aDetach = {0};
            a.sceneProperty().addListener((obs, old, scene) -> {
                if (scene == null) {
                    aDetach[0]++;
                }
            });

            set.getChips().add(new RXChip("c"));
            set.getChips().remove(b);

            assertEquals(0, aDetach[0],
                    "an unchanged chip must not be detached when other chips are added or removed");
        });
    }

    // ==================== Selection model ====================

    /**
     * Verifies SINGLE mode keeps at most one chip selected and derives selectedChips.
     */
    @Test
    public void singleModeIsMutuallyExclusive() {
        RXChip a = new RXChip("a", RXChip.ChipType.FILTER);
        RXChip b = new RXChip("b", RXChip.ChipType.FILTER);
        RXChipSet set = new RXChipSet(a, b);
        set.setSelectionMode(RXChipSet.SelectionMode.SINGLE);
        AtomicInteger changes = new AtomicInteger();
        set.setOnSelectionChange(event -> changes.incrementAndGet());

        a.setSelected(true);
        assertTrue(a.isSelected());
        assertEquals(List.of(a), set.getSelectedChips());

        b.setSelected(true);
        assertFalse(a.isSelected(), "selecting b deselects a in SINGLE mode");
        assertTrue(b.isSelected());
        assertEquals(List.of(b), set.getSelectedChips());
        assertEquals(2, changes.get());
    }

    /**
     * Verifies MULTIPLE mode accumulates selection in chip order.
     */
    @Test
    public void multipleModeAccumulatesInChipOrder() {
        RXChip a = new RXChip("a", RXChip.ChipType.FILTER);
        RXChip b = new RXChip("b", RXChip.ChipType.FILTER);
        RXChip c = new RXChip("c", RXChip.ChipType.FILTER);
        RXChipSet set = new RXChipSet(a, b, c);
        set.setSelectionMode(RXChipSet.SelectionMode.MULTIPLE);

        c.setSelected(true);
        a.setSelected(true);
        assertEquals(List.of(a, c), set.getSelectedChips());
    }

    /**
     * Verifies NONE mode clears any selection and rejects new selection.
     */
    @Test
    public void noneModeClearsAndRejectsSelection() {
        RXChip a = new RXChip("a", RXChip.ChipType.FILTER);
        RXChip b = new RXChip("b", RXChip.ChipType.FILTER);
        RXChipSet set = new RXChipSet(a, b);
        set.setSelectionMode(RXChipSet.SelectionMode.MULTIPLE);
        a.setSelected(true);
        b.setSelected(true);
        assertEquals(List.of(a, b), set.getSelectedChips());

        set.setSelectionMode(RXChipSet.SelectionMode.NONE);
        assertFalse(a.isSelected());
        assertFalse(b.isSelected());
        assertTrue(set.getSelectedChips().isEmpty());

        // A new selection attempt in NONE mode is rejected.
        a.setSelected(true);
        assertFalse(a.isSelected());
        assertTrue(set.getSelectedChips().isEmpty());
    }

    /**
     * Verifies switching to SINGLE collapses an existing multiple selection.
     */
    @Test
    public void switchingToSingleCollapsesSelection() {
        RXChip a = new RXChip("a", RXChip.ChipType.FILTER);
        RXChip b = new RXChip("b", RXChip.ChipType.FILTER);
        RXChipSet set = new RXChipSet(a, b);
        set.setSelectionMode(RXChipSet.SelectionMode.MULTIPLE);
        a.setSelected(true);
        b.setSelected(true);

        set.setSelectionMode(RXChipSet.SelectionMode.SINGLE);
        assertEquals(1, set.getSelectedChips().size(), "SINGLE keeps at most one");
    }

    /**
     * Verifies a required selection (allowEmptySelection = false) prevents deselecting
     * the last selected chip.
     */
    @Test
    public void requiredSelectionPreventsDeselectingTheLastChip() {
        RXChip a = new RXChip("a", RXChip.ChipType.FILTER);
        RXChip b = new RXChip("b", RXChip.ChipType.FILTER);
        RXChipSet set = new RXChipSet(a, b);
        set.setSelectionMode(RXChipSet.SelectionMode.SINGLE);
        set.setAllowEmptySelection(false);

        a.setSelected(true);
        assertTrue(a.isSelected());

        a.setSelected(false);
        assertTrue(a.isSelected(), "a required selection reverts deselecting the last selected chip");
        assertEquals(List.of(a), set.getSelectedChips());
    }

    /**
     * Verifies removing a selected chip updates the derived selection.
     */
    @Test
    public void removingSelectedChipUpdatesSelection() {
        RXChip a = new RXChip("a", RXChip.ChipType.FILTER);
        RXChip b = new RXChip("b", RXChip.ChipType.FILTER);
        RXChipSet set = new RXChipSet(a, b);
        set.setSelectionMode(RXChipSet.SelectionMode.MULTIPLE);
        a.setSelected(true);
        b.setSelected(true);
        assertEquals(List.of(a, b), set.getSelectedChips());

        set.getChips().remove(b);
        assertEquals(List.of(a), set.getSelectedChips());
    }

    // ==================== Keyboard roving ====================

    /**
     * Verifies Left / Right / Home / End move focus between the chips of the set.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void arrowKeysRoveFocusBetweenChips() throws Exception {
        runOnFx(() -> {
            RXChip a = new RXChip("a");
            RXChip b = new RXChip("b");
            RXChip c = new RXChip("c");
            RXChipSet set = attach(new RXChipSet(a, b, c));
            Scene scene = set.getScene();

            a.requestFocus();
            assertSame(a, scene.getFocusOwner());

            set.fireEvent(key(KeyCode.RIGHT));
            assertSame(b, scene.getFocusOwner());

            set.fireEvent(key(KeyCode.END));
            assertSame(c, scene.getFocusOwner());

            set.fireEvent(key(KeyCode.LEFT));
            assertSame(b, scene.getFocusOwner());

            set.fireEvent(key(KeyCode.HOME));
            assertSame(a, scene.getFocusOwner());
        });
    }

    /**
     * Verifies arrow-key roving steps over a disabled chip instead of trapping focus
     * on it (regression for the P2 review MINOR).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void arrowKeysSkipDisabledChips() throws Exception {
        runOnFx(() -> {
            RXChip a = new RXChip("a");
            RXChip b = new RXChip("b");
            RXChip c = new RXChip("c");
            b.setDisable(true);
            RXChipSet set = attach(new RXChipSet(a, b, c));
            Scene scene = set.getScene();

            a.requestFocus();
            assertSame(a, scene.getFocusOwner());

            set.fireEvent(key(KeyCode.RIGHT));
            assertSame(c, scene.getFocusOwner(), "RIGHT steps over the disabled chip b to c");

            set.fireEvent(key(KeyCode.LEFT));
            assertSame(a, scene.getFocusOwner(), "LEFT steps back over b to a");
        });
    }

    /**
     * Verifies the read-only selectedChips list cannot be mutated by callers
     * (regression for the P2 review MAJOR).
     */
    @Test
    public void selectedChipsIsUnmodifiable() {
        RXChip a = new RXChip("a", RXChip.ChipType.FILTER);
        RXChip b = new RXChip("b", RXChip.ChipType.FILTER);
        RXChipSet set = new RXChipSet(a, b);
        set.setSelectionMode(RXChipSet.SelectionMode.SINGLE);
        a.setSelected(true);
        assertEquals(List.of(a), set.getSelectedChips());
        assertThrows(UnsupportedOperationException.class, () -> set.getSelectedChips().add(b));
        assertThrows(UnsupportedOperationException.class, () -> set.getSelectedChips().clear());
        assertEquals(List.of(a), set.getSelectedChips(), "selection stays intact after rejected mutations");
    }

    // ==================== Layout ====================

    /**
     * Verifies the set reports a taller preferred height when its width forces the
     * chips to wrap (horizontal content bias).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void narrowWidthWrapsAndGrowsTaller() throws Exception {
        runOnFx(() -> {
            RXChip[] many = new RXChip[8];
            for (int i = 0; i < many.length; i++) {
                many[i] = new RXChip("chip-" + i);
            }
            RXChipSet set = attach(new RXChipSet(many));
            double wideHeight = set.prefHeight(2000.0);
            double narrowHeight = set.prefHeight(60.0);
            assertTrue(narrowHeight > wideHeight,
                    "a narrow width wraps the chips into more rows, growing the height");
        });
    }

    // ==================== Helpers ====================

    private static RXChipSet attach(RXChipSet set) {
        set.setSkin(new RXChipSetSkin(set));
        StackPane root = new StackPane(set);
        new Scene(root, 400.0, 200.0);
        root.applyCss();
        root.layout();
        return set;
    }

    private static KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
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
}
