package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXSidebar.SidebarMode;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link RXSidebar} control: property defaults, the
 * control-owned selection wiring (selectedItem &lt;-&gt; internal ToggleGroup
 * two-way sync, nav membership), the read-only selection projection driven by
 * {@code selectItem} / {@code clearSelection}, and the {@code :expanded}/{@code :mini}
 * mode pseudo-classes. Operates on the model only — no skin/scene required.
 */
public class RXSidebarTest {

    private static final double EPSILON = 0.0001;
    private static final PseudoClass EXPANDED = PseudoClass.getPseudoClass("expanded");
    private static final PseudoClass MINI = PseudoClass.getPseudoClass("mini");

    /**
     * Starts the JavaFX toolkit so controls can be constructed.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    /**
     * Verifies the property defaults and the default style class.
     */
    @Test
    public void propertyDefaults() {
        RXSidebar sidebar = new RXSidebar();

        assertTrue(sidebar.getStyleClass().contains("rx-sidebar"));
        assertSame(SidebarMode.EXPANDED, sidebar.getMode());
        assertEquals(260.0, sidebar.getExpandedWidth(), EPSILON);
        assertEquals(64.0, sidebar.getMiniWidth(), EPSILON);
        assertTrue(sidebar.isAnimated());
        assertEquals(Duration.millis(200.0), sidebar.getAnimationDuration());
        assertSame(Interpolator.EASE_BOTH, sidebar.getAnimationInterpolator());
        assertNull(sidebar.getSelectedItem());
        assertTrue(sidebar.getTopItems().isEmpty());
        assertTrue(sidebar.getItems().isEmpty());
        assertTrue(sidebar.getBottomItems().isEmpty());
    }

    /**
     * Verifies the mode pseudo-classes track {@code mode}, with {@code null}
     * resolving to the default (expanded) at the use site.
     */
    @Test
    public void modePseudoClasses() {
        RXSidebar sidebar = new RXSidebar();
        assertTrue(sidebar.getPseudoClassStates().contains(EXPANDED));
        assertFalse(sidebar.getPseudoClassStates().contains(MINI));

        sidebar.setMode(SidebarMode.MINI);
        assertTrue(sidebar.getPseudoClassStates().contains(MINI));
        assertFalse(sidebar.getPseudoClassStates().contains(EXPANDED));

        // null is lenient -> resolves to DEFAULT_MODE (expanded) for the pseudo-class.
        sidebar.setMode(null);
        assertTrue(sidebar.getPseudoClassStates().contains(EXPANDED));
        assertFalse(sidebar.getPseudoClassStates().contains(MINI));
        assertNull(sidebar.getMode());
    }

    /**
     * Verifies clicking a nav item (group -&gt; selectedItem path) updates
     * selectedItem.
     */
    @Test
    public void navClickUpdatesSelection() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        RXSidebarNavItem b = new RXSidebarNavItem("B");
        sidebar.getItems().addAll(a, b);

        a.fire();
        assertSame(a, sidebar.getSelectedItem());
        assertTrue(a.isSelected());

        b.fire();
        assertSame(b, sidebar.getSelectedItem());
        assertTrue(b.isSelected());
        assertFalse(a.isSelected());
    }

    /**
     * Verifies programmatic selectItem(nav) (selectedItem -&gt; group path)
     * lights the nav item and clears the previous selection.
     */
    @Test
    public void programmaticSelectMirrorsToGroup() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        RXSidebarNavItem b = new RXSidebarNavItem("B");
        sidebar.getItems().addAll(a, b);

        sidebar.selectItem(a);
        assertTrue(a.isSelected());
        assertSame(a, sidebar.getSelectedItem());

        sidebar.selectItem(b);
        assertTrue(b.isSelected());
        assertFalse(a.isSelected());
        assertSame(b, sidebar.getSelectedItem());
    }

    /**
     * Verifies selectItem(null) clears the group.
     */
    @Test
    public void setSelectedNullClearsGroup() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        sidebar.getItems().add(a);

        sidebar.selectItem(a);
        assertTrue(a.isSelected());

        sidebar.clearSelection();
        assertNull(sidebar.getSelectedItem());
        assertFalse(a.isSelected());
    }

    /**
     * Verifies an action item never joins the selection group: firing it runs
     * its command and leaves the selection untouched.
     */
    @Test
    public void actionItemNeverSelects() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem nav = new RXSidebarNavItem("Nav");
        RXSidebarActionItem action = new RXSidebarActionItem("Act");
        sidebar.getItems().addAll(nav, action);

        sidebar.selectItem(nav);
        assertTrue(nav.isSelected());

        AtomicInteger fired = new AtomicInteger();
        action.setOnAction(event -> fired.incrementAndGet());
        action.fire();

        assertEquals(1, fired.get(), "the action's command must run");
        assertSame(nav, sidebar.getSelectedItem(), "firing an action must not change the selection");
        assertTrue(nav.isSelected());
        // Selecting an action item is not merely ignored, it is impossible:
        // selectItem only accepts RXSidebarNavItem.
    }

    /**
     * Verifies the radio-like rule: re-firing the selected nav item keeps it
     * selected (the user can never reach "no view" by re-clicking).
     */
    @Test
    public void reclickSelectedNavKeepsSelection() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        sidebar.getItems().add(a);

        a.fire();
        assertSame(a, sidebar.getSelectedItem());

        a.fire();
        assertSame(a, sidebar.getSelectedItem());
        assertTrue(a.isSelected());
        assertSame(a, sidebar.getSelectedItem());
    }

    /**
     * Verifies selection is mutually exclusive across all three lists (one
     * shared group spanning top / main / bottom).
     */
    @Test
    public void selectionIsExclusiveAcrossAllThreeLists() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem top = new RXSidebarNavItem("Top");
        RXSidebarNavItem main = new RXSidebarNavItem("Main");
        RXSidebarNavItem bottom = new RXSidebarNavItem("Bottom");
        sidebar.getTopItems().add(top);
        sidebar.getItems().add(main);
        sidebar.getBottomItems().add(bottom);

        top.fire();
        assertSame(top, sidebar.getSelectedItem());

        bottom.fire();
        assertSame(bottom, sidebar.getSelectedItem());
        assertFalse(top.isSelected());
        assertFalse(main.isSelected());
        assertTrue(bottom.isSelected());
    }

    /**
     * Verifies removing the selected nav item from its list leaves the group and
     * clears the selection (no dangling selectedItem).
     */
    @Test
    public void removingSelectedNavClearsSelection() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        sidebar.getItems().add(a);
        sidebar.selectItem(a);
        assertTrue(a.isSelected());

        sidebar.getItems().remove(a);
        assertNull(sidebar.getSelectedItem());
    }

    /**
     * Verifies the re-entrancy guard: rapid alternating programmatic and
     * click-path selection changes converge without infinite recursion.
     */
    @Test
    public void rapidSelectionChangesDoNotLoop() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        RXSidebarNavItem b = new RXSidebarNavItem("B");
        sidebar.getItems().addAll(a, b);

        for (int i = 0; i < 50; i++) {
            sidebar.selectItem(a);
            b.fire();
            sidebar.clearSelection();
            a.fire();
        }
        // Reaching here without StackOverflowError proves the guard holds.
        assertSame(a, sidebar.getSelectedItem());
        assertTrue(a.isSelected());
    }

    /**
     * Verifies selecting a nav item BEFORE it is added to any list stays
     * consistent once it joins: the group adopts the already-selected toggle.
     */
    @Test
    public void setSelectionBeforeAddThenAddStaysConsistent() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");

        sidebar.selectItem(a);
        assertSame(a, sidebar.getSelectedItem());
        assertTrue(a.isSelected());

        sidebar.getItems().add(a);
        assertSame(a, sidebar.getSelectedItem());
        assertTrue(a.isSelected());
    }

    /**
     * Verifies a single discrete selection change fires the selectedItem
     * listener exactly once (no double-fire).
     */
    @Test
    public void selectionFiresChangeListenerExactlyOnce() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        sidebar.getItems().add(a);

        AtomicInteger selCount = new AtomicInteger();
        AtomicInteger navCount = new AtomicInteger();
        sidebar.selectedItemProperty().addListener((obs, o, n) -> selCount.incrementAndGet());
        sidebar.selectedItemProperty().addListener((obs, o, n) -> navCount.incrementAndGet());

        a.fire();
        assertEquals(1, selCount.get());
        assertEquals(1, navCount.get());
    }

    /**
     * The selection is a read-only projection: the sidebar owns it, because a
     * user click has to be able to write it. Handing out a writable property
     * would let an external binding own it too, and the two writers would
     * collide on the first click.
     */
    @Test
    public void selectedItemPropertyIsAReadOnlyProjection() {
        RXSidebar sidebar = new RXSidebar();
        assertFalse(sidebar.selectedItemProperty() instanceof Property,
                "selectedItemProperty must not be writable/bindable from outside");
    }

    /**
     * The supported replacement for binding: an external model drives the rail
     * through {@code selectItem}, and the rail is observed with a listener.
     * There is no automatic write-back — the app owns that half — but crucially
     * a user click still lands, and the rail never ends up highlighting one item
     * while {@code selectedItem} names another. That desync is what a two-way
     * binding on a control-owned property would reintroduce.
     */
    @Test
    public void externalModelDrivesSelectionAndClicksStillWork() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        RXSidebarNavItem b = new RXSidebarNavItem("B");
        sidebar.getItems().addAll(a, b);

        ObjectProperty<RXSidebarNavItem> route = new SimpleObjectProperty<>();
        route.addListener((obs, old, value) -> sidebar.selectItem(value));
        AtomicReference<RXSidebarNavItem> observed = new AtomicReference<>();
        sidebar.selectedItemProperty().addListener((obs, old, value) -> observed.set(value));

        route.set(a);
        assertSame(a, sidebar.getSelectedItem());
        assertSame(a, observed.get());
        assertTrue(a.isSelected());

        // The click path writes the same state without throwing and without
        // leaving the rail highlighting one item while the model names another.
        b.fire();
        assertSame(b, sidebar.getSelectedItem());
        assertSame(b, observed.get());
        assertTrue(b.isSelected());
        assertFalse(a.isSelected());
    }

    /**
     * A pending selection (an item selected before it was added to any list) is
     * held by the control, not by the ToggleGroup — the group has never seen the
     * item. The control must therefore own the whole invariant for it: clearing
     * or superseding a pending selection has to actually deselect it, or the
     * stale flag makes the group adopt it on add and hijack the real selection.
     */
    @Test
    public void pendingSelectionIsFullyOwnedByTheControl() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem pending = new RXSidebarNavItem("Pending");
        RXSidebarNavItem mounted = new RXSidebarNavItem("Mounted");
        sidebar.getItems().add(mounted);

        // Superseding a mounted selection with a pending one deselects the mounted
        // one — otherwise both would render selected.
        sidebar.selectItem(mounted);
        sidebar.selectItem(pending);
        assertSame(pending, sidebar.getSelectedItem());
        assertFalse(mounted.isSelected(), "the outgoing mounted item must not stay selected");
        assertTrue(pending.isSelected());

        // Clearing a pending selection really clears it: adding the item later
        // must not resurrect it.
        sidebar.clearSelection();
        assertNull(sidebar.getSelectedItem());
        assertFalse(pending.isSelected(), "a cleared pending item must not keep its selected flag");
        sidebar.getItems().add(pending);
        assertNull(sidebar.getSelectedItem(), "adding a cleared item must not resurrect the selection");

        // One pending superseding another leaves only the latest armed.
        sidebar.getItems().clear();
        RXSidebarNavItem first = new RXSidebarNavItem("First");
        RXSidebarNavItem second = new RXSidebarNavItem("Second");
        sidebar.selectItem(first);
        sidebar.selectItem(second);
        assertFalse(first.isSelected());
        sidebar.getItems().addAll(first, second);
        assertSame(second, sidebar.getSelectedItem(), "the stale flag must not hijack the selection");
    }

    /**
     * An application listener may re-select from inside the change notification
     * (route normalization: "A is not a real destination, go to B"). That is a
     * fresh write, not the control's own echo, so it must fully apply — on the
     * click path too, where the group already wrote the state being replaced.
     * Getting this wrong leaves selectedItem naming B while nothing is highlighted.
     */
    @Test
    public void listenerMayRedirectSelectionOnBothPaths() {
        for (boolean byClick : new boolean[]{false, true}) {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarNavItem a = new RXSidebarNavItem("A");
            RXSidebarNavItem b = new RXSidebarNavItem("B");
            sidebar.getItems().addAll(a, b);
            sidebar.selectedItemProperty().addListener((obs, old, value) -> {
                if (value == a) {
                    sidebar.selectItem(b);
                }
            });

            if (byClick) {
                a.fire();
            } else {
                sidebar.selectItem(a);
            }

            String path = byClick ? "click path" : "programmatic path";
            assertSame(b, sidebar.getSelectedItem(), path + ": the redirect must win");
            assertTrue(b.isSelected(), path + ": the redirected item must actually be highlighted");
            assertFalse(a.isSelected(), path + ": the rejected item must not stay highlighted");
        }
    }

    /**
     * selectItem(null) clears the selection, matching clearSelection().
     */
    @Test
    public void selectItemNullClearsSelection() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        sidebar.getItems().add(a);

        sidebar.selectItem(a);
        sidebar.selectItem(null);
        assertNull(sidebar.getSelectedItem());
        assertFalse(a.isSelected());

        sidebar.selectItem(a);
        sidebar.clearSelection();
        assertNull(sidebar.getSelectedItem());
        assertFalse(a.isSelected());
    }

    /**
     * Removing the selected item clears the selection and leaves the item
     * unselected, so merely adding it back does not resurrect it — and, with
     * something else selected by then, cannot hijack that selection either.
     * Only an explicit selectItem re-selects.
     */
    @Test
    public void removedItemDoesNotResurrectOrHijackOnReadd() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        RXSidebarNavItem b = new RXSidebarNavItem("B");
        sidebar.getItems().addAll(a, b);
        sidebar.selectItem(a);
        assertTrue(a.isSelected());

        sidebar.getItems().remove(a);
        assertNull(sidebar.getSelectedItem());
        assertFalse(a.isSelected(), "a removed item must not keep its selected flag");

        // Nothing else selected: re-adding must not resurrect it.
        sidebar.getItems().add(a);
        assertNull(sidebar.getSelectedItem());
        assertFalse(a.isSelected());

        // Something else selected: re-adding must not steal from it.
        sidebar.selectItem(b);
        sidebar.getItems().remove(a);
        sidebar.getItems().add(a);
        assertSame(b, sidebar.getSelectedItem(), "adding an item must not change the selection");
        assertTrue(b.isSelected());
        assertFalse(a.isSelected());

        sidebar.selectItem(a);
        assertSame(a, sidebar.getSelectedItem());
        assertFalse(b.isSelected());
    }

    /**
     * An item is a Node, so it can only be in one place. A second membership does
     * not add a second row — it moves the one row and strips the item of its
     * selection wiring — so the lists refuse it at the call site rather than
     * corrupting quietly. Same answer JavaFX gives for the same impossibility in
     * {@code Parent.getChildren()} and {@code ToggleGroup.getToggles()}.
     */
    @Test
    public void listsRejectAnItemTheSidebarAlreadyHolds() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("Inbox");
        sidebar.getItems().add(a);

        assertThrows(IllegalArgumentException.class, () -> sidebar.getItems().add(a),
                "twice in the same list");
        assertThrows(IllegalArgumentException.class, () -> sidebar.getTopItems().add(a),
                "already in another band");
        assertThrows(IllegalArgumentException.class, () -> sidebar.getBottomItems().add(a),
                "already in another band");
        assertThrows(NullPointerException.class, () -> sidebar.getItems().add(null));

        assertEquals(1, sidebar.getItems().size(), "a rejected add must not change the list");
    }

    /**
     * A rejected bulk call leaves the list untouched rather than half-applied.
     */
    @Test
    public void aRejectedBulkAddChangesNothing() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        RXSidebarNavItem b = new RXSidebarNavItem("B");

        assertThrows(IllegalArgumentException.class, () -> sidebar.getItems().addAll(a, b, a),
                "the same item twice in one call");
        assertTrue(sidebar.getItems().isEmpty(), "nothing may be added when the call is refused");
    }

    /**
     * The rejection must not catch the legitimate moves: reordering with setAll,
     * removing and re-adding, and setting an index to the item already there all
     * keep the "once in the sidebar" invariant.
     */
    @Test
    public void legitimateListMovesAreNotRejected() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        RXSidebarNavItem b = new RXSidebarNavItem("B");
        sidebar.getItems().addAll(a, b);

        sidebar.getItems().setAll(List.of(b, a));   // reorder: both are already here
        assertEquals(List.of(b, a), List.copyOf(sidebar.getItems()));

        sidebar.getItems().remove(a);
        sidebar.getItems().add(a);                  // gone, so addable again
        assertEquals(2, sidebar.getItems().size());

        int index = sidebar.getItems().indexOf(a);
        sidebar.getItems().set(index, a);           // replacing an item with itself
        assertEquals(2, sidebar.getItems().size());

        // ...but a set that would duplicate it is still refused.
        assertThrows(IllegalArgumentException.class,
                () -> sidebar.getItems().set(1 - index, a));
    }

    /**
     * A move that keeps the item in the sidebar keeps its selection too. A reorder
     * or an in-place set reports the item as removed-then-added, but it never left,
     * so the current navigation selection must survive it.
     */
    @Test
    public void listMovesThatKeepTheItemKeepTheSelection() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        RXSidebarNavItem b = new RXSidebarNavItem("B");
        sidebar.getItems().addAll(a, b);
        sidebar.selectItem(a);

        sidebar.getItems().setAll(List.of(b, a));   // reorder
        assertSame(a, sidebar.getSelectedItem(), "reordering must not drop the selection");
        assertTrue(a.isSelected());

        int index = sidebar.getItems().indexOf(a);
        sidebar.getItems().set(index, a);           // in-place replace with itself
        assertSame(a, sidebar.getSelectedItem(), "an in-place set must not drop the selection");
        assertTrue(a.isSelected());
    }

    /**
     * setAll is defined as clear-then-add, so it must snapshot its argument: passing
     * the list itself, or a live sublist of it, must not empty it mid-flight, and a
     * rejected setAll must leave the list untouched.
     */
    @Test
    public void setAllIsAtomicAgainstItsOwnContents() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        RXSidebarNavItem b = new RXSidebarNavItem("B");
        sidebar.getItems().addAll(a, b);

        sidebar.getItems().setAll(sidebar.getItems());
        assertEquals(List.of(a, b), List.copyOf(sidebar.getItems()), "setAll(self) is a no-op");

        sidebar.getItems().setAll(sidebar.getItems().subList(0, 2));
        assertEquals(List.of(a, b), List.copyOf(sidebar.getItems()), "setAll(live sublist) keeps them");

        // A rejected bulk call leaves the list intact — including one that names an
        // item the other band already holds.
        RXSidebarNavItem shared = new RXSidebarNavItem("Shared");
        sidebar.getTopItems().add(shared);
        assertThrows(IllegalArgumentException.class,
                () -> sidebar.getItems().setAll(List.of(a, shared)));
        assertEquals(List.of(a, b), List.copyOf(sidebar.getItems()), "a rejected setAll changes nothing");
    }

    /**
     * Moving an item between bands still works — remove, then add.
     */
    @Test
    public void anItemCanBeMovedBetweenBands() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        sidebar.getItems().add(a);

        sidebar.getItems().remove(a);
        sidebar.getBottomItems().add(a);

        assertTrue(sidebar.getItems().isEmpty());
        assertEquals(List.of(a), List.copyOf(sidebar.getBottomItems()));
    }

    /**
     * Verifies the control exposes its two styleable CSS properties.
     */
    @Test
    public void cssMetadataExposesStyleables() {
        boolean hasAnimated = RXSidebar.getClassCssMetaData().stream()
                .anyMatch(m -> "-rx-animated".equals(m.getProperty()));
        boolean hasDuration = RXSidebar.getClassCssMetaData().stream()
                .anyMatch(m -> "-rx-animation-duration".equals(m.getProperty()));
        assertTrue(hasAnimated);
        assertTrue(hasDuration);
    }
}
