package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTileView;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.scene.control.FocusModel;

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
    private final ChangeListener<ObservableList<T>> itemsSwapListener;
    private ObservableList<T> observedItems;

    RXTileFocusModel(RXTileView<T> control) {
        this.control = control;
        itemsSwapListener = (obs, oldList, newList) -> attachItems(newList);
        control.itemsProperty().addListener(new WeakChangeListener<>(itemsSwapListener));
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

    private void onItemsChanged(ListChangeListener.Change<? extends T> change) {
        int focusedIndex = getFocusedIndex();
        if (focusedIndex < 0) {
            return;
        }
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
            focusedIndex = Math.max(0, Math.min(removedFocusedFrom - 1, itemCount - 1));
        }
        if (focusedIndex != getFocusedIndex()) {
            focus(focusedIndex < 0 || focusedIndex >= itemCount ? -1 : focusedIndex);
        }
    }
}
