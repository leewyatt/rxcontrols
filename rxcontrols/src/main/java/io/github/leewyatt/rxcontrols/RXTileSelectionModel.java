package io.github.leewyatt.rxcontrols;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Default {@link MultipleSelectionModel} for {@link RXTileView}. JavaFX exposes
 * no reusable public multiple-selection implementation (the {@code TableView} /
 * {@code ListView} models hang off {@code com.sun} internals), so this is
 * hand-rolled on the public API.
 *
 * <p>The selected indices are kept in a sorted, distinct observable list — the
 * read-only {@link #getSelectedIndices()} view — which fires precise list changes
 * for free (single-item add/remove for {@code select}/{@code clearSelection},
 * one coalesced replace for {@code clearAndSelect}/{@code selectIndices}/
 * {@code selectAll}). {@link #getSelectedItems()} is derived from the indices.
 * The model maintains the selection as the items list mutates (add/remove shift,
 * permutation re-map, removal drop) and re-resolves the lead on a list swap, all
 * independently of any skin so a headless control selects correctly.
 *
 * <p>Focus is not managed here — the skin owns the (internal) focus model and
 * coordinates focus, anchor and scrolling around the selection.
 *
 * @param <T> the item type
 */
public class RXTileSelectionModel<T> extends MultipleSelectionModel<T> {

    private final RXTileView<T> tileView;

    private final ObservableList<Integer> selectedIndicesBacking = FXCollections.observableArrayList();
    private final ObservableList<Integer> selectedIndicesView =
            FXCollections.unmodifiableObservableList(selectedIndicesBacking);
    private final ObservableList<T> selectedItemsBacking = FXCollections.observableArrayList();
    private final ObservableList<T> selectedItemsView =
            FXCollections.unmodifiableObservableList(selectedItemsBacking);

    private final ListChangeListener<T> itemsContentListener = this::onItemsChanged;
    // One reusable weak wrapper so detach removes the exact instance that was
    // attached (removeListener(raw) would not match a fresh weak wrapper).
    private final WeakListChangeListener<T> weakItemsContentListener =
            new WeakListChangeListener<>(itemsContentListener);
    private final InvalidationListener itemsSwapListener;
    private ObservableList<T> observedItems;

    // Set to the removal start that dropped the lead during one items change, so the
    // lead can revert to the prior existing row (mirrors ListView RT-28637) instead
    // of leaving selectedIndex/selectedItem desynced. -1 when no such drop happened.
    private int pendingLeadRevertFrom = -1;

    /**
     * Creates a selection model bound to the given tile view's items.
     *
     * @param tileView the owning tile view
     */
    public RXTileSelectionModel(RXTileView<T> tileView) {
        this.tileView = tileView;
        selectedIndicesBacking.addListener((ListChangeListener<Integer>) change -> syncSelectedItems());

        itemsSwapListener = obs -> attachItems(tileView.getItems());
        // Weak so a replaced model (setSelectionModel) is not pinned by the live
        // items property / list (AGENTS §3.1).
        tileView.itemsProperty().addListener(new WeakInvalidationListener(itemsSwapListener));
        attachItems(tileView.getItems());
    }

    // ==================== Selected indices / items ====================

    @Override
    public ObservableList<Integer> getSelectedIndices() {
        return selectedIndicesView;
    }

    @Override
    public ObservableList<T> getSelectedItems() {
        return selectedItemsView;
    }

    private void syncSelectedItems() {
        List<T> items = new ArrayList<>(selectedIndicesBacking.size());
        for (Integer index : selectedIndicesBacking) {
            items.add(getModelItem(index));
        }
        selectedItemsBacking.setAll(items);
    }

    // ==================== Select ====================

    @Override
    public void select(int index) {
        if (index < 0) {
            clearSelection();
            return;
        }
        if (index >= getItemCount()) {
            return;
        }
        if (getSelectionMode() == SelectionMode.SINGLE) {
            if (!(selectedIndicesBacking.size() == 1 && selectedIndicesBacking.get(0) == index)) {
                selectedIndicesBacking.setAll(List.of(index));
            }
        } else if (!isSelected(index)) {
            insertSorted(index);
        }
        setSelectedIndex(index);
        setSelectedItem(getModelItem(index));
    }

    @Override
    public void select(T obj) {
        ObservableList<T> items = tileView.getItems();
        if (items != null) {
            int index = items.indexOf(obj);
            if (index >= 0) {
                select(index);
                return;
            }
        }
        // Not in the list: remember the item so a later add can re-resolve it.
        setSelectedIndex(-1);
        setSelectedItem(obj);
    }

    @Override
    public void selectIndices(int index, int... indices) {
        int itemCount = getItemCount();
        List<Integer> valid = new ArrayList<>();
        addValid(valid, index, itemCount);
        if (indices != null) {
            for (int i : indices) {
                addValid(valid, i, itemCount);
            }
        }
        if (valid.isEmpty()) {
            return;
        }
        int lead = valid.get(valid.size() - 1);
        if (getSelectionMode() == SelectionMode.SINGLE) {
            selectedIndicesBacking.setAll(List.of(lead));
        } else {
            // Retain existing selection (MultipleSelectionModel contract), merge,
            // and emit one coalesced replace.
            TreeSet<Integer> merged = new TreeSet<>(selectedIndicesBacking);
            merged.addAll(valid);
            selectedIndicesBacking.setAll(new ArrayList<>(merged));
        }
        setSelectedIndex(lead);
        setSelectedItem(getModelItem(lead));
    }

    @Override
    public void selectAll() {
        if (getSelectionMode() == SelectionMode.SINGLE) {
            return;
        }
        int itemCount = getItemCount();
        if (itemCount == 0) {
            return;
        }
        List<Integer> all = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            all.add(i);
        }
        selectedIndicesBacking.setAll(all);
        setSelectedIndex(itemCount - 1);
        setSelectedItem(getModelItem(itemCount - 1));
    }

    @Override
    public void selectFirst() {
        if (getItemCount() > 0) {
            select(0);
        }
    }

    @Override
    public void selectLast() {
        int itemCount = getItemCount();
        if (itemCount > 0) {
            select(itemCount - 1);
        }
    }

    @Override
    public void selectPrevious() {
        int lead = getSelectedIndex();
        if (lead > 0) {
            select(lead - 1);
        }
    }

    @Override
    public void selectNext() {
        int lead = getSelectedIndex();
        if (lead >= 0 && lead < getItemCount() - 1) {
            select(lead + 1);
        }
    }

    @Override
    public void clearAndSelect(int index) {
        if (index < 0 || index >= getItemCount()) {
            clearSelection();
            return;
        }
        // Set the lead first so observers never see a transient -1, then emit one
        // coalesced replace of the index list.
        setSelectedIndex(index);
        setSelectedItem(getModelItem(index));
        if (!(selectedIndicesBacking.size() == 1 && selectedIndicesBacking.get(0) == index)) {
            selectedIndicesBacking.setAll(List.of(index));
        }
    }

    // ==================== Clear ====================

    @Override
    public void clearSelection(int index) {
        int pos = Collections.binarySearch(selectedIndicesBacking, index);
        if (pos >= 0) {
            selectedIndicesBacking.remove(pos);
            if (getSelectedIndex() == index) {
                setSelectedIndex(-1);
                setSelectedItem(null);
            }
        }
    }

    @Override
    public void clearSelection() {
        setSelectedIndex(-1);
        setSelectedItem(null);
        if (!selectedIndicesBacking.isEmpty()) {
            selectedIndicesBacking.clear();
        }
    }

    @Override
    public boolean isSelected(int index) {
        return index >= 0 && Collections.binarySearch(selectedIndicesBacking, index) >= 0;
    }

    @Override
    public boolean isEmpty() {
        return selectedIndicesBacking.isEmpty();
    }

    // ==================== Items maintenance ====================

    private void attachItems(ObservableList<T> items) {
        if (observedItems != null) {
            observedItems.removeListener(weakItemsContentListener);
        }
        observedItems = items;
        if (items != null) {
            items.addListener(weakItemsContentListener);
        }
        resolveSelectionForNewList();
    }

    // A list swap invalidates the old indices; re-resolve the lead item against the
    // new list (collapsing any multi-selection to that item), else clear.
    private void resolveSelectionForNewList() {
        T leadItem = getSelectedItem();
        ObservableList<T> items = tileView.getItems();
        int index = (leadItem != null && items != null) ? items.indexOf(leadItem) : -1;
        if (index >= 0) {
            setSelectedIndex(index);
            setSelectedItem(getModelItem(index));
            selectedIndicesBacking.setAll(List.of(index));
        } else {
            setSelectedIndex(-1);
            setSelectedItem(null);
            if (!selectedIndicesBacking.isEmpty()) {
                selectedIndicesBacking.clear();
            }
        }
    }

    private void onItemsChanged(ListChangeListener.Change<? extends T> change) {
        ObservableList<T> items = tileView.getItems();
        int itemCount = items == null ? 0 : items.size();
        if (itemCount == 0) {
            clearSelection();
            return;
        }
        pendingLeadRevertFrom = -1;
        while (change.next()) {
            if (change.wasPermutated()) {
                applyPermutation(change);
            } else if (change.wasUpdated()) {
                syncSelectedItems();
            } else {
                applyAddOrRemove(change);
            }
        }
        updateLeadAfterItemsChange();
        syncSelectedItems();
    }

    private void applyPermutation(ListChangeListener.Change<? extends T> change) {
        List<Integer> remapped = new ArrayList<>(selectedIndicesBacking.size());
        for (Integer index : selectedIndicesBacking) {
            if (index >= change.getFrom() && index < change.getTo()) {
                remapped.add(change.getPermutation(index));
            } else {
                remapped.add(index);
            }
        }
        Collections.sort(remapped);
        selectedIndicesBacking.setAll(remapped);

        int lead = getSelectedIndex();
        if (lead >= change.getFrom() && lead < change.getTo()) {
            setSelectedIndex(change.getPermutation(lead));
        }
    }

    private void applyAddOrRemove(ListChangeListener.Change<? extends T> change) {
        int from = change.getFrom();
        int removed = change.getRemovedSize();
        int delta = change.getAddedSize() - removed;
        if (delta == 0 && removed == 0) {
            return;
        }
        List<Integer> shifted = new ArrayList<>(selectedIndicesBacking.size());
        for (Integer index : selectedIndicesBacking) {
            if (index < from) {
                shifted.add(index);
            } else if (index >= from + removed) {
                shifted.add(index + delta);
            }
            // Indices within the removed range are dropped.
        }
        if (shifted.size() != selectedIndicesBacking.size()
                || !shifted.equals(selectedIndicesBacking)) {
            selectedIndicesBacking.setAll(shifted);
        }

        int lead = getSelectedIndex();
        if (lead >= from + removed) {
            setSelectedIndex(lead + delta);
        } else if (lead >= from) {
            // The lead item was removed. Only a pure removal (no replacement) reverts
            // to the prior row; a replace/setAll lets the item re-resolve or clear.
            if (change.getAddedSize() == 0) {
                pendingLeadRevertFrom = from;
            }
            setSelectedIndex(-1);
        }
    }

    // Keep the lead consistent after an items change: re-sync the item for a still
    // valid lead; promote a survivor or revert to the prior row when the lead was
    // removed; otherwise re-resolve a remembered item or clear a dangling one.
    private void updateLeadAfterItemsChange() {
        int itemCount = getItemCount();
        int lead = getSelectedIndex();
        if (lead >= 0 && lead < itemCount) {
            setSelectedItem(getModelItem(lead));
            pendingLeadRevertFrom = -1;
            return;
        }
        // The lead is gone (removed, or shifted out of range).
        if (!selectedIndicesBacking.isEmpty()) {
            // Other selections survive: promote the highest as the new lead.
            int survivor = selectedIndicesBacking.get(selectedIndicesBacking.size() - 1);
            setSelectedIndex(survivor);
            setSelectedItem(getModelItem(survivor));
            pendingLeadRevertFrom = -1;
            return;
        }
        if (pendingLeadRevertFrom >= 0 && itemCount > 0) {
            // The sole selected (lead) item was removed: revert to the prior existing
            // row so selectedIndex/selectedItem stay in sync (ListView RT-28637).
            int prior = Math.max(0, Math.min(pendingLeadRevertFrom - 1, itemCount - 1));
            pendingLeadRevertFrom = -1;
            select(prior);
            return;
        }
        pendingLeadRevertFrom = -1;
        // No selection: re-resolve a remembered item (a prior select(T) miss whose
        // item later appeared), else clear so no removed item dangles on the lead.
        T leadItem = getSelectedItem();
        ObservableList<T> items = tileView.getItems();
        if (leadItem != null && items != null) {
            int index = items.indexOf(leadItem);
            if (index >= 0) {
                select(index);
                return;
            }
        }
        if (lead != -1) {
            setSelectedIndex(-1);
        }
        setSelectedItem(null);
    }

    // ==================== Helpers ====================

    private void insertSorted(int index) {
        int pos = Collections.binarySearch(selectedIndicesBacking, index);
        if (pos < 0) {
            selectedIndicesBacking.add(-pos - 1, index);
        }
    }

    private static void addValid(List<Integer> target, int index, int itemCount) {
        if (index >= 0 && index < itemCount && !target.contains(index)) {
            target.add(index);
        }
    }

    private int getItemCount() {
        ObservableList<T> items = tileView.getItems();
        return items == null ? 0 : items.size();
    }

    private T getModelItem(int index) {
        ObservableList<T> items = tileView.getItems();
        if (items == null || index < 0 || index >= items.size()) {
            return null;
        }
        return items.get(index);
    }
}
