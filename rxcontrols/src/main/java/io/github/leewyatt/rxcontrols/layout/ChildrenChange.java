package io.github.leewyatt.rxcontrols.layout;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The children one notification of a pane's children list really took out and really
 * put in. A child the notification both removes and adds never left the list: JavaFX
 * reports a child that {@code setAll} keeps, in a new place or the same, as removed and
 * added at once, without marking a permutation. Such a child is in neither list, so
 * whatever the pane is doing with it goes on.
 *
 * @param removed the children that left the list
 * @param added   the children that joined it
 */
record ChildrenChange(List<Node> removed, List<Node> added) {

    /**
     * Reads a whole notification, every sub-change of it.
     *
     * @param change the notification, not yet read
     * @return the children it took out and put in
     */
    static ChildrenChange of(ListChangeListener.Change<? extends Node> change) {
        Set<Node> removed = new LinkedHashSet<>();
        Set<Node> added = new LinkedHashSet<>();
        while (change.next()) {
            if (change.wasRemoved()) {
                removed.addAll(change.getRemoved());
            }
            if (change.wasAdded()) {
                added.addAll(change.getAddedSubList());
            }
        }
        Set<Node> kept = new LinkedHashSet<>(removed);
        kept.retainAll(added);
        removed.removeAll(kept);
        added.removeAll(kept);
        return new ChildrenChange(List.copyOf(removed), List.copyOf(added));
    }
}
