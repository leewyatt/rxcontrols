package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for {@link RXIndexedSelectionModel}: single/multiple selection,
 * the sorted-distinct {@code selectedIndices} / derived {@code selectedItems}
 * observable lists, coalesced changes, and selection maintenance as the items
 * list mutates (add/remove shift, removal drop, permutation re-map, swap). No
 * skin or layout is involved — the model is exercised directly.
 */
public class RXIndexedSelectionModelTest {

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

    private static RXTileView<String> view(int count) {
        ObservableList<String> items = FXCollections.observableArrayList();
        for (int i = 0; i < count; i++) {
            items.add("Item " + i);
        }
        return new RXTileView<>(items);
    }

    @Test
    public void defaultModelIsSingleAndNonNull() {
        MultipleSelectionModel<String> sm = view(5).getSelectionModel();
        assertNotNull(sm);
        assertInstanceOf(RXIndexedSelectionModel.class, sm);
        assertSame(SelectionMode.SINGLE, sm.getSelectionMode());
        assertTrue(sm.isEmpty());
        assertEquals(-1, sm.getSelectedIndex());
    }

    @Test
    public void singleSelectReplaces() {
        MultipleSelectionModel<String> sm = view(10).getSelectionModel();
        sm.select(2);
        assertEquals(List.of(2), sm.getSelectedIndices());
        sm.select(5);
        assertEquals(List.of(5), sm.getSelectedIndices(), "SINGLE replaces the prior selection");
        assertEquals(5, sm.getSelectedIndex());
        assertEquals("Item 5", sm.getSelectedItem());
    }

    @Test
    public void singleSelectOfAbsentItemDropsPriorAndResolvesOnLaterAdd() {
        RXTileView<String> view = view(3);
        ObservableList<String> items = view.getItems();
        MultipleSelectionModel<String> sm = view.getSelectionModel(); // SINGLE by default

        sm.select("Item 0");
        assertEquals(List.of(0), sm.getSelectedIndices());

        // Absent item: SINGLE select replaces, so the prior index must be dropped.
        sm.select("New");
        assertEquals(-1, sm.getSelectedIndex());
        assertEquals("New", sm.getSelectedItem());
        assertTrue(sm.getSelectedIndices().isEmpty(), "SINGLE select of an absent item drops the prior selection");

        // When it appears, the remembered item resolves — not the old survivor promoted.
        items.add("New");
        assertEquals("New", sm.getSelectedItem(), "the remembered absent item resolves, not the prior survivor");
        assertEquals(3, sm.getSelectedIndex());
        assertEquals(List.of(3), sm.getSelectedIndices());
    }

    @Test
    public void multipleSelectAccumulatesSortedAndDistinct() {
        MultipleSelectionModel<String> sm = view(10).getSelectionModel();
        sm.setSelectionMode(SelectionMode.MULTIPLE);
        sm.select(5);
        sm.select(1);
        sm.select(3);
        sm.select(1); // duplicate ignored
        assertEquals(List.of(1, 3, 5), sm.getSelectedIndices());
        assertEquals(1, sm.getSelectedIndex(), "the lead is the last index touched");
    }

    @Test
    public void selectIndicesIsSortedDistinctWithLastValidLead() {
        MultipleSelectionModel<String> sm = view(10).getSelectionModel();
        sm.setSelectionMode(SelectionMode.MULTIPLE);
        sm.selectIndices(5, 1, 3, 1, 99); // 99 out of range, 1 duplicate
        assertEquals(List.of(1, 3, 5), sm.getSelectedIndices());
        assertEquals(3, sm.getSelectedIndex(), "lead is the last valid distinct argument");
    }

    @Test
    public void selectedItemsTracksIndicesAndIsObservable() {
        MultipleSelectionModel<String> sm = view(10).getSelectionModel();
        sm.setSelectionMode(SelectionMode.MULTIPLE);
        AtomicInteger changes = new AtomicInteger();
        sm.getSelectedItems().addListener((ListChangeListener<String>) c -> changes.incrementAndGet());
        sm.select(0);
        sm.select(2);
        assertEquals(List.of("Item 0", "Item 2"), sm.getSelectedItems());
        assertTrue(changes.get() >= 2, "selectedItems fires changes as the selection grows");
    }

    @Test
    public void selectRangeSelectsInclusiveExclusive() {
        MultipleSelectionModel<String> sm = view(10).getSelectionModel();
        sm.setSelectionMode(SelectionMode.MULTIPLE);
        sm.selectRange(2, 5); // 2,3,4
        assertEquals(List.of(2, 3, 4), sm.getSelectedIndices());
    }

    @Test
    public void clearAndSelectEmitsOneChangePerCall() {
        MultipleSelectionModel<String> sm = view(10).getSelectionModel();
        AtomicInteger changes = new AtomicInteger();
        sm.getSelectedIndices().addListener((ListChangeListener<Integer>) c -> changes.incrementAndGet());
        sm.clearAndSelect(3);
        assertEquals(1, changes.get(), "clearAndSelect from empty is a single change");
        sm.clearAndSelect(5);
        assertEquals(2, changes.get(), "replace is a single coalesced change, not clear-then-add");
        assertEquals(List.of(5), sm.getSelectedIndices());
    }

    @Test
    public void clearAndSelectRangeEmitsOneChangeWithoutIntermediateClear() {
        RXIndexedSelectionModel<String> sm = (RXIndexedSelectionModel<String>) view(10).getSelectionModel();
        sm.setSelectionMode(SelectionMode.MULTIPLE);
        sm.select(0);
        List<List<Integer>> snapshots = new ArrayList<>();
        sm.getSelectedIndices().addListener((ListChangeListener<Integer>) change -> {
            while (change.next()) {
                snapshots.add(List.copyOf(sm.getSelectedIndices()));
            }
        });

        sm.clearAndSelectRange(0, 4);

        assertEquals(List.of(List.of(0, 1, 2, 3)), snapshots);
        assertEquals(3, sm.getSelectedIndex(), "the final range index becomes the lead");
    }

    @Test
    public void clearAndSelectRangeDescendingKeepsTargetAsLead() {
        RXIndexedSelectionModel<String> sm = (RXIndexedSelectionModel<String>) view(10).getSelectionModel();
        sm.setSelectionMode(SelectionMode.MULTIPLE);

        sm.clearAndSelectRange(3, -1);

        assertEquals(List.of(0, 1, 2, 3), sm.getSelectedIndices());
        assertEquals(0, sm.getSelectedIndex(), "descending ranges use the last traversed valid index as lead");
    }

    @Test
    public void clearAndSelectIndicesEmitsOneChangeWithoutIntermediateClear() {
        RXIndexedSelectionModel<String> sm = (RXIndexedSelectionModel<String>) view(10).getSelectionModel();
        sm.setSelectionMode(SelectionMode.MULTIPLE);
        sm.select(0);
        List<List<Integer>> snapshots = new ArrayList<>();
        sm.getSelectedIndices().addListener((ListChangeListener<Integer>) change -> {
            while (change.next()) {
                snapshots.add(List.copyOf(sm.getSelectedIndices()));
            }
        });

        sm.clearAndSelectIndices(List.of(7, 1, 4), 4);

        assertEquals(List.of(List.of(1, 4, 7)), snapshots);
        assertEquals(List.of(1, 4, 7), sm.getSelectedIndices());
        assertEquals(4, sm.getSelectedIndex(), "the explicit selected lead is preserved");
    }

    @Test
    public void clearSelectionByIndexAndAll() {
        MultipleSelectionModel<String> sm = view(10).getSelectionModel();
        sm.setSelectionMode(SelectionMode.MULTIPLE);
        sm.selectIndices(1, 2, 3);
        sm.clearSelection(2);
        assertEquals(List.of(1, 3), sm.getSelectedIndices());
        sm.clearSelection();
        assertTrue(sm.isEmpty());
        assertEquals(-1, sm.getSelectedIndex());
    }

    @Test
    public void selectByItemResolvesIndex() {
        MultipleSelectionModel<String> sm = view(10).getSelectionModel();
        sm.select("Item 4");
        assertEquals(4, sm.getSelectedIndex());
        assertTrue(sm.isSelected(4));
    }

    @Test
    public void selectAllIsNoOpInSingleAndFullInMultiple() {
        MultipleSelectionModel<String> single = view(6).getSelectionModel();
        single.selectAll();
        assertTrue(single.isEmpty(), "selectAll is a no-op in SINGLE mode");

        MultipleSelectionModel<String> multi = view(6).getSelectionModel();
        multi.setSelectionMode(SelectionMode.MULTIPLE);
        multi.selectAll();
        assertEquals(6, multi.getSelectedIndices().size());
    }

    @Test
    public void switchingToSingleCollapsesToLead() {
        MultipleSelectionModel<String> sm = view(10).getSelectionModel();
        sm.setSelectionMode(SelectionMode.MULTIPLE);
        sm.selectIndices(1, 3, 5);
        sm.setSelectionMode(SelectionMode.SINGLE);
        assertEquals(List.of(5), sm.getSelectedIndices(), "collapses to the lead (last selected)");
    }

    // ==================== Items maintenance ====================

    @Test
    public void selectionShiftsWhenItemsInsertedBefore() {
        RXTileView<String> view = view(10);
        ObservableList<String> items = view.getItems();
        MultipleSelectionModel<String> sm = view.getSelectionModel();
        sm.setSelectionMode(SelectionMode.MULTIPLE);
        sm.selectIndices(2, 4);
        items.add(0, "x");
        assertEquals(List.of(3, 5), sm.getSelectedIndices(), "indices shift by the inserted count");
    }

    @Test
    public void selectionShiftsWhenItemsRemovedBefore() {
        RXTileView<String> view = view(10);
        ObservableList<String> items = view.getItems();
        MultipleSelectionModel<String> sm = view.getSelectionModel();
        sm.setSelectionMode(SelectionMode.MULTIPLE);
        sm.selectIndices(2, 4);
        items.remove(0);
        assertEquals(List.of(1, 3), sm.getSelectedIndices());
    }

    @Test
    public void removingASelectedItemDropsItFromSelection() {
        RXTileView<String> view = view(10);
        ObservableList<String> items = view.getItems();
        MultipleSelectionModel<String> sm = view.getSelectionModel();
        sm.setSelectionMode(SelectionMode.MULTIPLE);
        sm.selectIndices(2, 4);
        items.remove(2); // removes the selected index 2; 4 shifts to 3
        assertEquals(List.of(3), sm.getSelectedIndices());
    }

    @Test
    public void replacingAllItemsClearsSelection() {
        RXTileView<String> view = view(5);
        ObservableList<String> items = view.getItems();
        MultipleSelectionModel<String> sm = view.getSelectionModel();
        sm.setSelectionMode(SelectionMode.MULTIPLE);
        sm.selectIndices(1, 2);
        items.setAll("a", "b", "c");
        assertTrue(sm.isEmpty(), "a full replace whose items are absent clears the selection");
        assertEquals(-1, sm.getSelectedIndex());
    }

    @Test
    public void permutationRemapsSelection() {
        ObservableList<String> items = FXCollections.observableArrayList("c", "a", "b");
        RXTileView<String> view = new RXTileView<>(items);
        MultipleSelectionModel<String> sm = view.getSelectionModel();
        sm.select(0); // "c"
        FXCollections.sort(items); // [a, b, c]; "c" moves to index 2
        assertTrue(sm.isSelected(2), "selection follows the item through a permutation");
        assertFalse(sm.isSelected(0));
        assertEquals(2, sm.getSelectedIndex());
        assertEquals("c", sm.getSelectedItem());
    }

    @Test
    public void itemsSwapReResolvesAndDetachesOldList() {
        ObservableList<String> listA = FXCollections.observableArrayList("a", "b", "c");
        RXTileView<String> view = new RXTileView<>(listA);
        MultipleSelectionModel<String> sm = view.getSelectionModel();
        sm.select(1); // "b"

        ObservableList<String> listB = FXCollections.observableArrayList("b", "x");
        view.setItems(listB);
        assertEquals(0, sm.getSelectedIndex(), "the selected item 'b' re-resolves to its index in the new list");

        listA.add(0, "z"); // mutate the OLD list
        assertEquals(0, sm.getSelectedIndex(), "the old list's listener was detached on swap");
    }

    @Test
    public void selectedNullItemReResolvesOnItemsSwap() {
        ObservableList<String> listA = FXCollections.observableArrayList("a", null, "c");
        RXTileView<String> view = new RXTileView<>(listA);
        MultipleSelectionModel<String> sm = view.getSelectionModel();
        sm.select(1);
        assertEquals(1, sm.getSelectedIndex());
        assertNull(sm.getSelectedItem());

        ObservableList<String> listB = FXCollections.observableArrayList("x", "y", null);
        view.setItems(listB);

        assertEquals(2, sm.getSelectedIndex(), "the selected null item re-resolves in the new list");
        assertEquals(List.of(2), sm.getSelectedIndices());
        assertNull(sm.getSelectedItem());
    }

    @Test
    public void noSelectionDoesNotResolveNullOnItemsSwap() {
        RXTileView<String> view = new RXTileView<>(FXCollections.observableArrayList("a"));
        MultipleSelectionModel<String> sm = view.getSelectionModel();

        view.setItems(FXCollections.observableArrayList((String) null));

        assertEquals(-1, sm.getSelectedIndex(), "an unselected null value is not treated as a remembered selection");
        assertTrue(sm.getSelectedIndices().isEmpty());
        assertNull(sm.getSelectedItem());
    }

    @Test
    public void removingTheLeadItemRevertsToPriorRow() {
        RXTileView<String> view = view(10);
        ObservableList<String> items = view.getItems();
        MultipleSelectionModel<String> sm = view.getSelectionModel();
        sm.select(3);
        items.remove(3); // removes the sole selected (lead) item
        assertEquals(2, sm.getSelectedIndex(), "the lead reverts to the prior existing row (RT-28637)");
        assertEquals("Item 2", sm.getSelectedItem(), "selectedIndex and selectedItem stay in sync");
        assertEquals(List.of(2), sm.getSelectedIndices());
        assertTrue(sm.isSelected(2));
    }

    @Test
    public void removingTheLeadInMultipleModePromotesASurvivor() {
        RXTileView<String> view = view(10);
        ObservableList<String> items = view.getItems();
        MultipleSelectionModel<String> sm = view.getSelectionModel();
        sm.setSelectionMode(SelectionMode.MULTIPLE);
        sm.selectIndices(1, 5); // lead = 5
        items.remove(5); // removes the lead; index 1 survives
        assertEquals(List.of(1), sm.getSelectedIndices());
        assertEquals(1, sm.getSelectedIndex(), "a surviving selection becomes the new lead");
        assertEquals("Item 1", sm.getSelectedItem(), "no dangling item is left on the lead");
    }

    @Test
    public void selectingAnAbsentItemThenAddingItResolvesTheIndex() {
        ObservableList<String> items = FXCollections.observableArrayList("a", "b", "c");
        RXTileView<String> view = new RXTileView<>(items);
        MultipleSelectionModel<String> sm = view.getSelectionModel();
        sm.select("z"); // not present: remembered, no index yet
        assertEquals(-1, sm.getSelectedIndex());
        assertEquals("z", sm.getSelectedItem());
        items.add("z"); // the remembered item appears
        assertEquals(3, sm.getSelectedIndex(), "the remembered item re-resolves to its new index");
        assertTrue(sm.isSelected(3));
    }
}
