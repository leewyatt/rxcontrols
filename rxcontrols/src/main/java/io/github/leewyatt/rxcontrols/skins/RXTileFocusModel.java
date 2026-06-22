package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTileView;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.scene.control.FocusModel;
import javafx.scene.control.MultipleSelectionModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Internal index-based focus model for {@link RXTileView}, owned by the skin and
 * never exposed on the control (the tile view is a single Tab stop; cells are
 * not focusable). It tracks a {@code focusedIndex} that drives the keyboard
 * focus ring, and shifts that index as the items list mutates so focus follows
 * the same item.
 *
 * @param <T> the item type
 */
final class RXTileFocusModel<T> extends FocusModel<T> {

    private final RXTileView<T> control;

    private final ListChangeListener<T> itemsContentListener = this::onItemsChanged;
    private final WeakListChangeListener<T> weakItemsContentListener =
            new WeakListChangeListener<>(itemsContentListener);
    private final InvalidationListener itemsSwapListener;
    private ObservableList<T> observedItems;

    RXTileFocusModel(RXTileView<T> control) {
        this.control = control;
        itemsSwapListener = obs -> attachItems(control.getItems());
        control.itemsProperty().addListener(new WeakInvalidationListener(itemsSwapListener));
        attachItems(control.getItems());
    }

    @Override
    protected int getItemCount() {
        ObservableList<T> items = control.getItems();
        return items == null ? 0 : items.size();
    }

    @Override
    protected T getModelItem(int index) {
        ObservableList<T> items = control.getItems();
        if (items == null || index < 0 || index >= items.size()) {
            return null;
        }
        return items.get(index);
    }

    private void attachItems(ObservableList<T> items) {
        T focusedItem = getFocusedItem();
        if (observedItems != null) {
            observedItems.removeListener(weakItemsContentListener);
        }
        observedItems = items;
        if (items != null) {
            items.addListener(weakItemsContentListener);
        }
        // A list swap invalidates the numeric index; re-resolve focus by item so the
        // focus ring follows the same item (or clears if absent), mirroring the
        // selection model's swap handling.
        focus((focusedItem != null && items != null) ? items.indexOf(focusedItem) : -1);
    }

    void moveItemsListenerToEnd() {
        if (observedItems != null) {
            observedItems.removeListener(weakItemsContentListener);
            observedItems.addListener(weakItemsContentListener);
        }
    }

    private void onItemsChanged(ListChangeListener.Change<? extends T> change) {
        int focusedIndex = getFocusedIndex();
        if (focusedIndex < 0) {
            return;
        }
        // Selection may observe this list change after focus; replay a snapshot so
        // deleting the focused item can still land on the post-change selection lead.
        SelectionSnapshot selectionSnapshot = selectionSnapshot();
        int removedFocusedFrom = -1;
        while (change.next()) {
            if (change.wasPermutated()) {
                if (focusedIndex >= change.getFrom() && focusedIndex < change.getTo()) {
                    focusedIndex = change.getPermutation(focusedIndex);
                }
                selectionSnapshot.applyPermutation(change);
            } else if (!change.wasUpdated()) {
                int from = change.getFrom();
                int removed = change.getRemovedSize();
                int delta = change.getAddedSize() - removed;
                if (focusedIndex >= from + removed) {
                    focusedIndex += delta;
                } else if (focusedIndex >= from) {
                    if (removed > 0 && change.getAddedSize() == 0) {
                        removedFocusedFrom = from;
                    }
                    focusedIndex = -1;
                }
                selectionSnapshot.applyAddOrRemove(change);
            }
        }
        int itemCount = getItemCount();
        if (focusedIndex < 0 && removedFocusedFrom >= 0 && itemCount > 0) {
            int selectionLead = selectionSnapshot.leadAfterChange(itemCount);
            if (selectionLead >= 0 && selectionLead < itemCount) {
                focusedIndex = selectionLead;
            } else {
                focusedIndex = Math.max(0, Math.min(removedFocusedFrom - 1, itemCount - 1));
            }
        }
        if (focusedIndex != getFocusedIndex()) {
            focus(focusedIndex < 0 || focusedIndex >= itemCount ? -1 : focusedIndex);
        }
    }

    private SelectionSnapshot selectionSnapshot() {
        MultipleSelectionModel<T> selectionModel = control.getSelectionModel();
        if (selectionModel == null) {
            return SelectionSnapshot.empty();
        }
        return new SelectionSnapshot(new ArrayList<>(selectionModel.getSelectedIndices()),
                selectionModel.getSelectedIndex(), selectionMatchesCurrentItems(selectionModel));
    }

    private boolean selectionMatchesCurrentItems(MultipleSelectionModel<T> selectionModel) {
        ObservableList<Integer> selectedIndices = selectionModel.getSelectedIndices();
        ObservableList<T> selectedItems = selectionModel.getSelectedItems();
        if (selectedIndices.size() != selectedItems.size()) {
            return false;
        }
        int itemCount = getItemCount();
        for (int i = 0; i < selectedIndices.size(); i++) {
            int index = selectedIndices.get(i);
            if (index < 0 || index >= itemCount || selectedItems.get(i) != getModelItem(index)) {
                return false;
            }
        }
        return true;
    }

    private static final class SelectionSnapshot {

        private final List<Integer> indices;
        private int lead;
        private final boolean current;
        private int pendingLeadRevertFrom = -1;

        private SelectionSnapshot(List<Integer> indices, int lead, boolean current) {
            this.indices = indices;
            this.lead = lead;
            this.current = current;
        }

        private static SelectionSnapshot empty() {
            return new SelectionSnapshot(List.of(), -1, true);
        }

        private void applyPermutation(ListChangeListener.Change<?> change) {
            if (current) {
                return;
            }
            int from = change.getFrom();
            int to = change.getTo();
            for (int i = 0; i < indices.size(); i++) {
                int index = indices.get(i);
                if (index >= from && index < to) {
                    indices.set(i, change.getPermutation(index));
                }
            }
            Collections.sort(indices);
            if (lead >= from && lead < to) {
                lead = change.getPermutation(lead);
            }
        }

        private void applyAddOrRemove(ListChangeListener.Change<?> change) {
            if (current) {
                return;
            }
            int from = change.getFrom();
            int removed = change.getRemovedSize();
            int delta = change.getAddedSize() - removed;
            if (delta == 0 && removed == 0) {
                return;
            }
            List<Integer> shifted = new ArrayList<>(indices.size());
            for (Integer index : indices) {
                if (index < from) {
                    shifted.add(index);
                } else if (index >= from + removed) {
                    shifted.add(index + delta);
                }
            }
            indices.clear();
            indices.addAll(shifted);

            if (lead >= from + removed) {
                lead += delta;
            } else if (lead >= from) {
                if (removed > 0 && change.getAddedSize() == 0) {
                    pendingLeadRevertFrom = from;
                }
                lead = -1;
            }
        }

        private int leadAfterChange(int itemCount) {
            if (current) {
                return lead >= 0 && lead < itemCount ? lead : -1;
            }
            if (!indices.isEmpty()) {
                return indices.get(indices.size() - 1);
            }
            if (pendingLeadRevertFrom >= 0 && itemCount > 0) {
                return Math.max(0, Math.min(pendingLeadRevertFrom - 1, itemCount - 1));
            }
            return lead >= 0 && lead < itemCount ? lead : -1;
        }
    }
}
