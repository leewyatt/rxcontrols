package io.github.leewyatt.rxcontrols;

/**
 * Immutable metadata for one section of an {@link RXListView}, derived from the
 * items and the {@link RXListView#sectionKeyFactoryProperty() sectionKeyFactory}
 * and published through {@link RXListView#sectionsProperty()}.
 *
 * <p>A section is a maximal run of adjacent items that share the same key (by
 * {@link java.util.Objects#equals(Object, Object) equals}); the items are
 * <em>not</em> reordered, so two non-adjacent runs of the same key produce two
 * separate sections with distinct {@link #sectionIndex()} values. The record is
 * intentionally non-generic and carries no item list — the items themselves stay
 * in {@link RXListView#getItems()} and can be sliced with
 * {@code getItems().subList(firstItemIndex(), firstItemIndex() + itemCount())}.
 *
 * @param key            the shared section key, possibly {@code null}
 * @param sectionIndex   the zero-based position of this section among all
 *                       sections (stable across duplicate keys)
 * @param firstItemIndex the index of this section's first item in the items list
 * @param itemCount      the number of items in this section (always {@code >= 1})
 */
public record RXListSection(Object key, int sectionIndex, int firstItemIndex, int itemCount) {

    /**
     * Returns the index one past this section's last item — the exclusive upper
     * bound of its item range.
     *
     * @return {@code firstItemIndex + itemCount}
     */
    public int endItemIndex() {
        return firstItemIndex + itemCount;
    }
}
