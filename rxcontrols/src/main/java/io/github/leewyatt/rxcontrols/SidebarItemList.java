package io.github.leewyatt.rxcontrols;

import javafx.collections.ModifiableObservableListBase;
import javafx.scene.control.Labeled;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * One of a sidebar's three item lists. Rejects an item that is already held by any
 * of the three, and rejects {@code null}, so a caller finds out at the call site.
 *
 * <p>A second membership cannot mean a second row: an item is a {@link
 * javafx.scene.Node Node}, and a node has one parent. What it actually does is move
 * the single row to the other band while the model keeps claiming both places, and
 * strip the item of its selection wiring on the way. That is not a state worth
 * representing, so it is refused — as JavaFX itself refuses the same impossibility
 * in {@code Parent.getChildren()} and {@code ToggleGroup.getToggles()}.</p>
 *
 * <p>Every mutation funnels through {@link #doAdd} / {@link #doSet}; the bulk entry
 * points additionally pre-validate the whole argument, so a rejected bulk call
 * leaves the list unchanged rather than partially mutated.</p>
 */
final class SidebarItemList extends ModifiableObservableListBase<RXSidebarItem> {

    private final List<RXSidebarItem> backing = new ArrayList<>();
    private final RXSidebar sidebar;

    SidebarItemList(RXSidebar sidebar) {
        this.sidebar = sidebar;
    }

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
        backing.add(index, requireAbsent(element));
    }

    @Override
    protected RXSidebarItem doSet(int index, RXSidebarItem element) {
        if (backing.get(index) == element) {
            return backing.set(index, element); // replacing an item with itself
        }
        return backing.set(index, requireAbsent(element));
    }

    @Override
    protected RXSidebarItem doRemove(int index) {
        return backing.remove(index);
    }

    @Override
    public boolean addAll(Collection<? extends RXSidebarItem> elements) {
        requireAllAddable(elements);
        return super.addAll(elements);
    }

    @Override
    public boolean addAll(int index, Collection<? extends RXSidebarItem> elements) {
        requireAllAddable(elements);
        return super.addAll(index, elements);
    }

    @Override
    public boolean setAll(Collection<? extends RXSidebarItem> elements) {
        // Snapshot first: setAll is clear()-then-addAll, so passing this list itself
        // (or a live sublist of it) would have clear() empty the argument mid-flight.
        // The copy also lets a rejected call leave the list untouched.
        List<RXSidebarItem> snapshot = new ArrayList<>(elements);
        // This list is about to be replaced wholesale, so its own current contents
        // are not a conflict — only the other two bands and duplicates within the
        // argument are.
        requireNoDuplicates(snapshot);
        for (RXSidebarItem element : snapshot) {
            Objects.requireNonNull(element, "sidebar item must not be null");
            requireNotInOtherBands(element);
        }
        return super.setAll(snapshot);
    }

    private void requireAllAddable(Collection<? extends RXSidebarItem> elements) {
        requireNoDuplicates(elements);
        for (RXSidebarItem element : elements) {
            requireAbsent(element);
        }
    }

    private void requireNoDuplicates(Collection<? extends RXSidebarItem> elements) {
        List<RXSidebarItem> seen = new ArrayList<>(elements.size());
        for (RXSidebarItem element : elements) {
            for (RXSidebarItem other : seen) {
                if (other == element) {
                    throw new IllegalArgumentException(
                            describe(element) + " appears twice in the same call;"
                                    + " a sidebar item can only be in the sidebar once");
                }
            }
            seen.add(element);
        }
    }

    private RXSidebarItem requireAbsent(RXSidebarItem element) {
        Objects.requireNonNull(element, "sidebar item must not be null");
        if (containsIdentical(this, element)) {
            throw new IllegalArgumentException(
                    describe(element) + " is already in this list;"
                            + " a sidebar item can only be in the sidebar once");
        }
        requireNotInOtherBands(element);
        return element;
    }

    private void requireNotInOtherBands(RXSidebarItem element) {
        if (isOtherBand(sidebar.getTopItems()) && containsIdentical(sidebar.getTopItems(), element)) {
            throw new IllegalArgumentException(alreadyHeld(element, "topItems"));
        }
        if (isOtherBand(sidebar.getItems()) && containsIdentical(sidebar.getItems(), element)) {
            throw new IllegalArgumentException(alreadyHeld(element, "items"));
        }
        if (isOtherBand(sidebar.getBottomItems()) && containsIdentical(sidebar.getBottomItems(), element)) {
            throw new IllegalArgumentException(alreadyHeld(element, "bottomItems"));
        }
    }

    private boolean isOtherBand(List<RXSidebarItem> band) {
        return band != this;
    }

    // Identity, not equals: two distinct items may compare equal, and it is the node
    // that cannot be in two places.
    private static boolean containsIdentical(List<RXSidebarItem> band, RXSidebarItem element) {
        for (RXSidebarItem candidate : band) {
            if (candidate == element) {
                return true;
            }
        }
        return false;
    }

    private static String alreadyHeld(RXSidebarItem element, String band) {
        return describe(element) + " is already in the sidebar's " + band
                + "; a sidebar item can only be in the sidebar once";
    }

    private static String describe(RXSidebarItem element) {
        if (element == null) {
            return "null";
        }
        String text = (element.asNode() instanceof Labeled labeled) ? labeled.getText() : null;
        return text == null ? element.getClass().getSimpleName()
                : element.getClass().getSimpleName() + " \"" + text + "\"";
    }
}
