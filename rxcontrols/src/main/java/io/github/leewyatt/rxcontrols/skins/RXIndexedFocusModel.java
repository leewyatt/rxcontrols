package io.github.leewyatt.rxcontrols.skins;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.scene.control.FocusModel;
import javafx.scene.control.MultipleSelectionModel;

import java.util.function.Supplier;

/**
 * Reusable index-based focus model for virtualized, data-driven RX controls (such
 * as {@code RXTileView} and {@code RXMasonryView}), owned by the skin and never
 * exposed on the control (those views are a single Tab stop; cells are not
 * focusable). It tracks a {@code focusedIndex} that drives the keyboard focus ring,
 * and shifts that index as the items list mutates so focus follows the same item.
 *
 * <p>It reads the lead from a supplied selection model (rather than a fixed
 * control) so it stays decoupled from any one control and survives selection-model
 * swaps.</p>
 *
 * @param <T> the item type
 */
class RXIndexedFocusModel<T> extends FocusModel<T> {

    private final ObservableValue<? extends ObservableList<T>> itemsProperty;
    private final Supplier<MultipleSelectionModel<T>> selectionModelSupplier;

    private final ListChangeListener<T> itemsContentListener = this::onItemsChanged;
    private final WeakListChangeListener<T> weakItemsContentListener =
            new WeakListChangeListener<>(itemsContentListener);
    private final InvalidationListener itemsSwapListener;
    private final WeakInvalidationListener weakItemsSwapListener;
    private ObservableList<T> observedItems;
    // The selection model observes item mutations before focus; keep the last
    // explicit focus/selection relationship so removals do not infer it afterward.
    private boolean focusedSelectionLead;

    RXIndexedFocusModel(ObservableValue<? extends ObservableList<T>> itemsProperty,
                        Supplier<MultipleSelectionModel<T>> selectionModelSupplier) {
        this.itemsProperty = itemsProperty;
        this.selectionModelSupplier = selectionModelSupplier;
        itemsSwapListener = obs -> attachItems(getItems());
        weakItemsSwapListener = new WeakInvalidationListener(itemsSwapListener);
        itemsProperty.addListener(weakItemsSwapListener);
        attachItems(getItems());
    }

    @Override
    protected int getItemCount() {
        ObservableList<T> items = getItems();
        return items == null ? 0 : items.size();
    }

    @Override
    protected T getModelItem(int index) {
        ObservableList<T> items = getItems();
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
        syncSelectionLeadState();
    }

    void moveItemsObserversToEnd() {
        // Selection owns the lead semantics. Keep focus after it both for the current
        // items list and for future list swaps.
        itemsProperty.removeListener(weakItemsSwapListener);
        itemsProperty.addListener(weakItemsSwapListener);
        if (observedItems != null) {
            observedItems.removeListener(weakItemsContentListener);
            observedItems.addListener(weakItemsContentListener);
        }
    }

    private void onItemsChanged(ListChangeListener.Change<? extends T> change) {
        int focusedIndex = getFocusedIndex();
        if (focusedIndex < 0) {
            focusedSelectionLead = false;
            return;
        }
        boolean removedFocusWasSelectionLead = focusedSelectionLead;
        int removedFocusedFrom = -1;
        while (change.next()) {
            if (change.wasPermutated()) {
                if (focusedIndex >= change.getFrom() && focusedIndex < change.getTo()) {
                    focusedIndex = change.getPermutation(focusedIndex);
                }
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
            }
        }
        int itemCount = getItemCount();
        if (focusedIndex < 0 && removedFocusedFrom >= 0 && itemCount > 0) {
            int selectionLead = selectionLead();
            if (removedFocusWasSelectionLead && selectionLead >= 0 && selectionLead < itemCount) {
                focusedIndex = selectionLead;
            } else {
                focusedIndex = Math.max(0, Math.min(removedFocusedFrom - 1, itemCount - 1));
            }
        }
        if (focusedIndex != getFocusedIndex()) {
            focus(focusedIndex < 0 || focusedIndex >= itemCount ? -1 : focusedIndex);
        }
        syncSelectionLeadState();
    }

    void syncSelectionLeadState() {
        int focusedIndex = getFocusedIndex();
        focusedSelectionLead = focusedIndex >= 0 && focusedIndex == selectionLead();
    }

    private int selectionLead() {
        MultipleSelectionModel<T> selectionModel = selectionModelSupplier.get();
        return selectionModel == null ? -1 : selectionModel.getSelectedIndex();
    }

    private ObservableList<T> getItems() {
        return itemsProperty.getValue();
    }
}
