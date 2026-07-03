package io.github.leewyatt.rxcontrols;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

/**
 * Immutable root-to-item path snapshot used by {@link RXCascaderView} and
 * {@link RXCascader}.
 *
 * <p>A path captures only its node identity chain ({@link #getItems() items}).
 * That is what a path fundamentally is — which nodes were traversed — and it
 * never goes stale: changing the view's {@code itemTextFactory} or an item's value
 * does not alter which nodes this path represents. Display text is a derived
 * view, not part of the snapshot: resolve it from the items with whatever scheme
 * you need (the view's {@code itemTextFactory}, or {@link #toString()} as a
 * value-based fallback).
 *
 * @param <T> application value type
 */
public final class RXCascaderPath<T> {

    private final List<RXCascaderItem<T>> items;

    /**
     * Creates a path snapshot from the given items.
     *
     * @param items root-to-item sequence
     */
    public RXCascaderPath(List<RXCascaderItem<T>> items) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
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
     * Returns the path values, derived from the items.
     *
     * @return immutable path values
     */
    public List<T> getValues() {
        List<T> values = new ArrayList<>(items.size());
        for (RXCascaderItem<T> item : items) {
            values.add(item.getValue());
        }
        return Collections.unmodifiableList(values);
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
     * Two paths are equal when they traverse the same item instances in the same
     * order. Equality is identity-based on the items (they do not override
     * {@code equals}), matching this snapshot's identity-chain contract, so a
     * re-selection of the same leaf compares equal and suppresses spurious change
     * events.
     *
     * @param obj object to compare
     * @return {@code true} if the other object is a path over the same items
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RXCascaderPath<?> other)) {
            return false;
        }
        return items.equals(other.items);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}, derived from the
     * item identity chain.
     *
     * @return the path hash code
     */
    @Override
    public int hashCode() {
        return items.hashCode();
    }

    /**
     * Returns a slash-separated value-based fallback representation. This uses
     * {@link RXCascaderItem#toString()} (the value), not the view's
     * {@code itemTextFactory}, so it is for debugging and not guaranteed to match the
     * visible cascader text — resolve display text from {@link #getItems()} with
     * the view's {@code itemTextFactory} for that.
     *
     * @return slash-separated value-based path text
     */
    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(" / ");
        for (RXCascaderItem<T> item : items) {
            joiner.add(String.valueOf(item));
        }
        return joiner.toString();
    }
}
