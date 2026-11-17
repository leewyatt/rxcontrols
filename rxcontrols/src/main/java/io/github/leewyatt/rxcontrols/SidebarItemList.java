package io.github.leewyatt.rxcontrols;

import javafx.beans.InvalidationListener;
import javafx.collections.ModifiableObservableListBase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * A list of sidebar items: one of a sidebar's three top-level lists, or the
 * child list of an item. It enforces that an item has a single owner and that
 * the tree has no cycle, and it maintains each item's {@code parent} and
 * {@code sidebar}.
 *
 * <p>An item that already belongs to a list is rejected rather than moved, as
 * are {@code null}, the same item twice in one call, and an item that is the
 * owner of this list or one of its ancestors. Every rejection is an
 * {@link IllegalArgumentException}. The bulk entry points validate the whole
 * argument first, so a rejected call leaves the list unchanged.</p>
 *
 * <p>Which list holds an item is recorded as the mutation happens, but
 * {@code parent}, {@code sidebar} and the sidebar's selection are only updated
 * once the change has finished. A sort, a {@code set} of an item onto itself and
 * a {@code setAll} of the same items all report an item as both removed and
 * added; judging by where the item ends up keeps such a change from looking
 * like the item left the tree.</p>
 */
final class SidebarItemList extends ModifiableObservableListBase<RXSidebarItem> {

    private final List<RXSidebarItem> backing = new ArrayList<>();
    // The items the change in progress has added or removed.
    private final List<RXSidebarItem> touched = new ArrayList<>();
    private final RXSidebar sidebar;
    private final RXSidebarItem ownerItem;

    SidebarItemList(RXSidebar sidebar) {
        this(sidebar, null);
    }

    SidebarItemList(RXSidebarItem ownerItem) {
        this(null, ownerItem);
    }

    private SidebarItemList(RXSidebar sidebar, RXSidebarItem ownerItem) {
        this.sidebar = sidebar;
        this.ownerItem = ownerItem;
        // An invalidation listener, and the first one: those are notified in
        // registration order and ahead of every change listener, so whoever
        // else observes this list sees settled ownership.
        addListener((InvalidationListener) observable -> settleOwnership());
    }

    RXSidebarItem getOwnerItem() {
        return ownerItem;
    }

    RXSidebar resolveSidebar() {
        return ownerItem == null ? sidebar : ownerItem.getSidebar();
    }

    // ==================== Storage ====================

    @Override
    public RXSidebarItem get(int index) {
        return backing.get(index);
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    protected void doAdd(int index, RXSidebarItem element) {
        requireAddable(element);
        backing.add(index, element);
        element.setOwningList(this);
        touched.add(element);
    }

    @Override
    protected RXSidebarItem doSet(int index, RXSidebarItem element) {
        RXSidebarItem old = backing.get(index);
        if (old == element) {
            return old;
        }
        requireAddable(element);
        backing.set(index, element);
        old.setOwningList(null);
        element.setOwningList(this);
        touched.add(old);
        touched.add(element);
        return old;
    }

    @Override
    protected RXSidebarItem doRemove(int index) {
        RXSidebarItem removed = backing.remove(index);
        removed.setOwningList(null);
        touched.add(removed);
        return removed;
    }

    // ==================== Bulk entry points ====================

    @Override
    public boolean addAll(Collection<? extends RXSidebarItem> elements) {
        List<RXSidebarItem> snapshot = new ArrayList<>(elements);
        requireAllAddable(snapshot, false);
        return super.addAll(snapshot);
    }

    @Override
    public boolean addAll(int index, Collection<? extends RXSidebarItem> elements) {
        List<RXSidebarItem> snapshot = new ArrayList<>(elements);
        requireAllAddable(snapshot, false);
        return super.addAll(index, snapshot);
    }

    @Override
    public boolean setAll(Collection<? extends RXSidebarItem> elements) {
        // Snapshot first: setAll is clear-then-addAll, so passing this list itself
        // would have the clear empty the argument.
        List<RXSidebarItem> snapshot = new ArrayList<>(elements);
        // This list is about to be replaced wholesale, so its own current items
        // are not a conflict.
        requireAllAddable(snapshot, true);
        return super.setAll(snapshot);
    }

    @Override
    public void sort(Comparator<? super RXSidebarItem> comparator) {
        // The inherited sort writes the items back one position at a time, so
        // each write would find its item still held at another index.
        List<RXSidebarItem> sorted = new ArrayList<>(backing);
        sorted.sort(comparator);
        setAll(sorted);
    }

    // ==================== Validation ====================

    private void requireAllAddable(List<RXSidebarItem> elements, boolean replacingContents) {
        for (int i = 0; i < elements.size(); i++) {
            RXSidebarItem element = elements.get(i);
            for (int j = 0; j < i; j++) {
                if (elements.get(j) == element) {
                    throw new IllegalArgumentException(describe(element)
                            + " appears twice in the same call; a sidebar item can only be in one place");
                }
            }
            if (replacingContents && element != null && element.getOwningList() == this) {
                continue;
            }
            requireAddable(element);
        }
    }

    private void requireAddable(RXSidebarItem element) {
        if (element == null) {
            throw new IllegalArgumentException("a sidebar item must not be null");
        }
        if (element.getOwningList() != null) {
            throw new IllegalArgumentException(describe(element)
                    + " already belongs to a list; remove it from there first");
        }
        for (RXSidebarItem ancestor = ownerItem; ancestor != null; ancestor = ownerOf(ancestor)) {
            if (ancestor == element) {
                throw new IllegalArgumentException(describe(element)
                        + " cannot be added to its own subtree");
            }
        }
    }

    private static RXSidebarItem ownerOf(RXSidebarItem item) {
        SidebarItemList list = item.getOwningList();
        return list == null ? null : list.getOwnerItem();
    }

    private static String describe(RXSidebarItem element) {
        if (element == null) {
            return "null";
        }
        String name = element.getClass().getSimpleName();
        return element.getText() == null ? name : name + " \"" + element.getText() + "\"";
    }

    // ==================== Ownership ====================

    private void settleOwnership() {
        List<RXSidebarItem> settling = new ArrayList<>(touched);
        touched.clear();
        for (RXSidebarItem item : settling) {
            item.syncOwnership();
        }
        RXSidebar owner = resolveSidebar();
        if (owner != null) {
            owner.revalidateSelection();
        }
    }
}
