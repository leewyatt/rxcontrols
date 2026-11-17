package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXSidebar.SidebarMode;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.leewyatt.rxcontrols.SidebarTestSupport.hasPseudoClass;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the sidebar control without a skin: property defaults, the mode
 * pseudo-classes, the selection rules (who can be selected, when the selection
 * is dropped, selecting before adding, expanding the groups above the
 * selection) and the CSS metadata.
 */
public class RXSidebarTest {

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        SidebarTestSupport.startToolkit();
    }

    /**
     * Property defaults.
     */
    @Test
    public void propertyDefaults() {
        RXSidebar sidebar = new RXSidebar();
        assertSame(SidebarMode.EXPANDED, sidebar.getMode());
        assertEquals(RXSidebar.DEFAULT_EXPANDED_WIDTH, sidebar.getExpandedWidth());
        assertEquals(RXSidebar.DEFAULT_MINI_WIDTH, sidebar.getMiniWidth());
        assertEquals(RXSidebar.DEFAULT_INDENT, sidebar.getIndent());
        assertTrue(sidebar.isAnimated());
        assertNull(sidebar.getSelectedItem());
        assertNull(sidebar.getHeader());
        assertNull(sidebar.getFooter());
        assertFalse(sidebar.isFocusTraversable());
        assertTrue(sidebar.getStyleClass().contains("rx-sidebar"));
    }

    /**
     * The mode drives :expanded / :mini; a null mode counts as expanded.
     */
    @Test
    public void modePseudoClasses() {
        RXSidebar sidebar = new RXSidebar();
        assertTrue(hasPseudoClass(sidebar, "expanded"));
        sidebar.setMode(SidebarMode.MINI);
        assertTrue(hasPseudoClass(sidebar, "mini"));
        assertFalse(hasPseudoClass(sidebar, "expanded"));
        sidebar.setMode(null);
        assertTrue(hasPseudoClass(sidebar, "expanded"));
    }

    /**
     * One page is selected at a time across the three lists, and its flag
     * follows.
     */
    @Test
    public void selectionIsExclusiveAcrossTheLists() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarItem top = RXSidebarItem.of("Top");
        RXSidebarItem main = RXSidebarItem.of("Main");
        RXSidebarItem bottom = RXSidebarItem.of("Bottom");
        sidebar.getTopItems().add(top);
        sidebar.getItems().add(main);
        sidebar.getBottomItems().add(bottom);

        sidebar.selectItem(top);
        sidebar.selectItem(bottom);
        assertSame(bottom, sidebar.getSelectedItem());
        assertFalse(top.isSelected());
        assertTrue(bottom.isSelected());

        sidebar.clearSelection();
        assertNull(sidebar.getSelectedItem());
        assertFalse(bottom.isSelected());
    }

    /**
     * Only a page of this sidebar can be selected; a group, an action and an
     * item of another sidebar are refused.
     */
    @Test
    public void selectItemRejectsWhatIsNotAPage() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarItem group = RXSidebarItem.group("Group", null, RXSidebarItem.of("Child"));
        RXSidebarItem action = RXSidebarItem.action("Action", null, null);
        sidebar.getItems().addAll(group, action);

        RXSidebarItem elsewhere = RXSidebarItem.of("Elsewhere");
        new RXSidebar().getItems().add(elsewhere);

        for (RXSidebarItem item : List.of(group, action, elsewhere)) {
            assertThrows(IllegalArgumentException.class, () -> sidebar.selectItem(item));
        }
        assertNull(sidebar.getSelectedItem());
    }

    /**
     * The selection is dropped when the item leaves the tree, alone or with a
     * group above it.
     */
    @Test
    public void leavingTheTreeClearsTheSelection() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarItem page = RXSidebarItem.of("Page");
        RXSidebarItem nested = RXSidebarItem.of("Nested");
        RXSidebarItem group = RXSidebarItem.group("Group", null, nested);
        sidebar.getItems().addAll(page, group);

        sidebar.selectItem(page);
        sidebar.getItems().remove(page);
        assertNull(sidebar.getSelectedItem());
        assertFalse(page.isSelected());

        sidebar.selectItem(nested);
        sidebar.getItems().remove(group);
        assertNull(sidebar.getSelectedItem());
        assertFalse(nested.isSelected());
    }

    /**
     * The selection is dropped when the selected item stops being a page.
     */
    @Test
    public void becomingUnselectableClearsTheSelection() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarItem a = RXSidebarItem.of("A");
        RXSidebarItem b = RXSidebarItem.of("B");
        sidebar.getItems().addAll(a, b);

        sidebar.selectItem(a);
        a.setSelectable(false);
        assertNull(sidebar.getSelectedItem());
        assertFalse(a.isSelected());

        sidebar.selectItem(b);
        b.getItems().add(RXSidebarItem.of("Child"));
        assertNull(sidebar.getSelectedItem());
        assertFalse(b.isSelected());
    }

    /**
     * An item selected before it is added carries no flag until it is in the
     * tree, and is adopted when added.
     */
    @Test
    public void selectionBeforeAddingIsAdoptedOnAdd() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarItem pending = RXSidebarItem.of("Pending");
        RXSidebarItem group = RXSidebarItem.group("Group", null, pending);

        sidebar.selectItem(pending);
        assertSame(pending, sidebar.getSelectedItem());
        assertFalse(pending.isSelected());

        sidebar.getItems().add(group);
        assertSame(pending, sidebar.getSelectedItem());
        assertTrue(pending.isSelected());
        assertTrue(group.isExpanded());
    }

    /**
     * A pending selection that is no longer a page when it is added is
     * dropped.
     */
    @Test
    public void pendingSelectionThatStoppedBeingAPageIsDropped() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarItem pending = RXSidebarItem.of("Pending");
        sidebar.selectItem(pending);
        pending.setSelectable(false);

        sidebar.getItems().add(pending);
        assertNull(sidebar.getSelectedItem());
        assertFalse(pending.isSelected());
    }

    /**
     * Selecting expands every group above the item, except one whose expanded
     * property is bound.
     */
    @Test
    public void selectingExpandsTheGroupsAbove() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarItem leaf = RXSidebarItem.of("Leaf");
        RXSidebarItem inner = RXSidebarItem.group("Inner", null, leaf);
        RXSidebarItem outer = RXSidebarItem.group("Outer", null, inner);
        sidebar.getItems().add(outer);
        inner.expandedProperty().bind(new SimpleBooleanProperty(false));

        sidebar.selectItem(leaf);
        assertTrue(outer.isExpanded());
        assertFalse(inner.isExpanded());

        outer.setExpanded(false);
        assertSame(leaf, sidebar.getSelectedItem(), "collapsing a group keeps the selection");
    }

    /**
     * A selection change notifies once, and a listener may redirect it.
     */
    @Test
    public void listenersSeeOneChangeAndMayRedirect() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarItem a = RXSidebarItem.of("A");
        RXSidebarItem b = RXSidebarItem.of("B");
        RXSidebarItem locked = RXSidebarItem.of("Locked");
        sidebar.getItems().addAll(a, b, locked);

        AtomicInteger changes = new AtomicInteger();
        sidebar.selectedItemProperty().addListener((obs, old, item) -> changes.incrementAndGet());
        sidebar.selectItem(a);
        sidebar.selectItem(a);
        assertEquals(1, changes.get());

        sidebar.selectedItemProperty().addListener((obs, old, item) -> {
            if (item == locked) {
                sidebar.selectItem(b);
            }
        });
        sidebar.selectItem(locked);
        assertSame(b, sidebar.getSelectedItem());
        assertTrue(b.isSelected());
        assertFalse(locked.isSelected());
        assertFalse(a.isSelected());
    }

    /**
     * The styleable properties are published as CSS metadata.
     */
    @Test
    public void cssMetadataExposesStyleables() {
        List<String> names = RXSidebar.getClassCssMetaData().stream()
                .map(CssMetaData<? extends Styleable, ?>::getProperty).toList();
        assertTrue(names.containsAll(List.of("-rx-animated", "-rx-animation-duration", "-rx-indent")));
    }
}
