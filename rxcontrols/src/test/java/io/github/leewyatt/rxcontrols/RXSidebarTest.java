package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.SidebarMode;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2 gate tests for the {@link RXSidebar} control: property defaults, the
 * control-owned selection wiring (selectedItem &lt;-&gt; internal ToggleGroup
 * two-way sync, nav membership, lenient non-nav storage), the derived
 * {@code selectedNavigationItem} view, and the {@code :expanded}/{@code :mini}
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
     * Verifies the V1 property defaults and the default style class.
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
        assertNull(sidebar.getSelectedNavigationItem());
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
     * Verifies clicking a nav item (group -&gt; selectedItem path) updates both
     * selectedItem and the derived selectedNavigationItem.
     */
    @Test
    public void navClickUpdatesSelection() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        RXSidebarNavItem b = new RXSidebarNavItem("B");
        sidebar.getItems().addAll(a, b);

        a.fire();
        assertSame(a, sidebar.getSelectedItem());
        assertSame(a, sidebar.getSelectedNavigationItem());
        assertTrue(a.isSelected());

        b.fire();
        assertSame(b, sidebar.getSelectedItem());
        assertSame(b, sidebar.getSelectedNavigationItem());
        assertTrue(b.isSelected());
        assertFalse(a.isSelected());
    }

    /**
     * Verifies programmatic setSelectedItem(nav) (selectedItem -&gt; group path)
     * lights the nav item and clears the previous selection.
     */
    @Test
    public void programmaticSelectMirrorsToGroup() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        RXSidebarNavItem b = new RXSidebarNavItem("B");
        sidebar.getItems().addAll(a, b);

        sidebar.setSelectedItem(a);
        assertTrue(a.isSelected());
        assertSame(a, sidebar.getSelectedNavigationItem());

        sidebar.setSelectedItem(b);
        assertTrue(b.isSelected());
        assertFalse(a.isSelected());
        assertSame(b, sidebar.getSelectedNavigationItem());
    }

    /**
     * Verifies setSelectedItem(null) clears the group and derived view.
     */
    @Test
    public void setSelectedNullClearsGroup() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        sidebar.getItems().add(a);

        sidebar.setSelectedItem(a);
        assertTrue(a.isSelected());

        sidebar.setSelectedItem(null);
        assertNull(sidebar.getSelectedItem());
        assertNull(sidebar.getSelectedNavigationItem());
        assertFalse(a.isSelected());
    }

    /**
     * Verifies an action item never joins the selection group: firing it does
     * not change selectedItem, and storing it leniently keeps selectedItem but
     * clears the derived nav view and the group.
     */
    @Test
    public void actionItemNeverSelects() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem nav = new RXSidebarNavItem("Nav");
        RXSidebarActionItem action = new RXSidebarActionItem("Act");
        sidebar.getItems().addAll(nav, action);

        sidebar.setSelectedItem(nav);
        assertTrue(nav.isSelected());

        // Firing the action runs its command; selection is unchanged.
        action.fire();
        assertSame(nav, sidebar.getSelectedItem());
        assertSame(nav, sidebar.getSelectedNavigationItem());
        assertTrue(nav.isSelected());

        // Storing a non-nav item leniently: kept in selectedItem, but no group
        // selection and no derived nav view.
        sidebar.setSelectedItem(action);
        assertSame(action, sidebar.getSelectedItem());
        assertNull(sidebar.getSelectedNavigationItem());
        assertFalse(nav.isSelected());
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
        assertSame(a, sidebar.getSelectedNavigationItem());
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
        sidebar.setSelectedItem(a);
        assertTrue(a.isSelected());

        sidebar.getItems().remove(a);
        assertNull(sidebar.getSelectedItem());
        assertNull(sidebar.getSelectedNavigationItem());
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
            sidebar.setSelectedItem(a);
            b.fire();
            sidebar.setSelectedItem(null);
            a.fire();
        }
        // Reaching here without StackOverflowError proves the guard holds.
        assertSame(a, sidebar.getSelectedItem());
        assertSame(a, sidebar.getSelectedNavigationItem());
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

        sidebar.setSelectedItem(a);
        assertSame(a, sidebar.getSelectedItem());
        assertSame(a, sidebar.getSelectedNavigationItem());
        assertTrue(a.isSelected());

        sidebar.getItems().add(a);
        assertSame(a, sidebar.getSelectedItem());
        assertSame(a, sidebar.getSelectedNavigationItem());
        assertTrue(a.isSelected());
    }

    /**
     * Verifies a single discrete selection change fires the selectedItem and
     * selectedNavigationItem listeners exactly once each (no double-fire).
     */
    @Test
    public void selectionFiresChangeListenerExactlyOnce() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        sidebar.getItems().add(a);

        AtomicInteger selCount = new AtomicInteger();
        AtomicInteger navCount = new AtomicInteger();
        sidebar.selectedItemProperty().addListener((obs, o, n) -> selCount.incrementAndGet());
        sidebar.selectedNavigationItemProperty().addListener((obs, o, n) -> navCount.incrementAndGet());

        a.fire();
        assertEquals(1, selCount.get());
        assertEquals(1, navCount.get());
    }

    /**
     * Verifies a non-nav item that is not in any list is still stored leniently
     * with no derived navigation view.
     */
    @Test
    public void orphanNonNavItemStoredLeniently() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarActionItem orphan = new RXSidebarActionItem("X");

        sidebar.setSelectedItem(orphan);
        assertSame(orphan, sidebar.getSelectedItem());
        assertNull(sidebar.getSelectedNavigationItem());
    }

    /**
     * Verifies binding selectedItemProperty to an external model drives the
     * group and derived view; the re-entrancy guard keeps the bound property
     * from being set back (which would throw).
     */
    @Test
    public void boundSelectedItemMirrorsToGroupAndDerivedView() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        RXSidebarNavItem b = new RXSidebarNavItem("B");
        sidebar.getItems().addAll(a, b);

        ObjectProperty<RXSidebarItem> external = new SimpleObjectProperty<>();
        sidebar.selectedItemProperty().bind(external);

        external.set(a);
        assertSame(a, sidebar.getSelectedItem());
        assertSame(a, sidebar.getSelectedNavigationItem());
        assertTrue(a.isSelected());

        external.set(b);
        assertSame(b, sidebar.getSelectedItem());
        assertTrue(b.isSelected());
        assertFalse(a.isSelected());

        sidebar.selectedItemProperty().unbind();
        assertSame(b, sidebar.getSelectedItem());
        assertSame(b, sidebar.getSelectedNavigationItem());
    }

    /**
     * Pins the emergent add/remove/re-add behavior: removing the selected nav
     * clears the control selection, but the orphaned toggle keeps its selected
     * flag, so re-adding it re-adopts it and resurrects the selection.
     */
    @Test
    public void removeThenReaddResurrectsSelection() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarNavItem a = new RXSidebarNavItem("A");
        sidebar.getItems().add(a);
        sidebar.setSelectedItem(a);
        assertTrue(a.isSelected());

        sidebar.getItems().remove(a);
        assertNull(sidebar.getSelectedItem());

        sidebar.getItems().add(a);
        assertSame(a, sidebar.getSelectedItem());
        assertSame(a, sidebar.getSelectedNavigationItem());
        assertTrue(a.isSelected());
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
