package io.github.leewyatt.rxcontrols;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable root-to-item path snapshot used by {@link RXCascaderPanel} and
 * {@link RXCascader}.
 *
 * @param <T> application value type
 */
public final class RXCascaderPath<T> {

    private final List<RXCascaderItem<T>> items;
    private final List<T> values;
    private final List<String> texts;

    /**
     * Creates a path snapshot from the given items.
     *
     * @param items root-to-item sequence
     */
    public RXCascaderPath(List<RXCascaderItem<T>> items) {
        List<RXCascaderItem<T>> itemCopy = new ArrayList<>(items);
        List<T> valueCopy = new ArrayList<>(itemCopy.size());
        List<String> textCopy = new ArrayList<>(itemCopy.size());
        for (RXCascaderItem<T> item : itemCopy) {
            valueCopy.add(item.getValue());
            String text = item.getText();
            textCopy.add(text == null ? "" : text);
        }
        this.items = Collections.unmodifiableList(itemCopy);
        this.values = Collections.unmodifiableList(valueCopy);
        this.texts = Collections.unmodifiableList(textCopy);
    }

    /**
     * Returns the path items.
     *
     * @return immutable path items
     */
    public List<RXCascaderItem<T>> getItems() {
        return items;
    }

    /**
     * Returns the path values.
     *
     * @return immutable path values
     */
    public List<T> getValues() {
        return values;
    }

    /**
     * Returns the path display texts.
     *
     * @return immutable path texts
     */
    public List<String> getTexts() {
        return texts;
    }

    /**
     * Returns the leaf item.
     *
     * @return leaf item, or {@code null} for an empty path
     */
    public RXCascaderItem<T> getLeaf() {
        return items.isEmpty() ? null : items.get(items.size() - 1);
    }

    /**
     * Returns whether this path contains the given item instance.
     *
     * @param item item to test
     * @return {@code true} if the item is in this path
     */
    public boolean contains(RXCascaderItem<T> item) {
        return items.contains(item);
    }

    /**
     * Returns the default slash-separated path text.
     *
     * @return slash-separated path text
     */
    @Override
    public String toString() {
        return String.join(" / ", texts);
    }
}
