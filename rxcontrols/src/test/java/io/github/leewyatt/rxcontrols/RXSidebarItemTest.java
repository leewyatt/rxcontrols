package io.github.leewyatt.rxcontrols;

import javafx.collections.FXCollections;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the sidebar item model: property defaults, the factories, the
 * single-owner and no-cycle rules of the item lists, and the upkeep of
 * {@code parent} and {@code sidebar}.
 */
public class RXSidebarItemTest {

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        SidebarTestSupport.startToolkit();
    }

    /**
     * A new item is a visible, enabled, selectable, collapsed leaf in no list.
     */
    @Test
    public void defaults() {
        RXSidebarItem item = new RXSidebarItem();
        assertNull(item.getText());
        assertNull(item.getGraphic());
        assertTrue(item.isVisible());
        assertFalse(item.isDisable());
        assertTrue(item.isSelectable());
        assertFalse(item.isExpanded());
        assertFalse(item.isSelected());
        assertNull(item.getParent());
        assertNull(item.getSidebar());
        assertTrue(item.getItems().isEmpty());
        assertTrue(item.getKeywords().isEmpty());
    }

    /**
     * The factories build a page, an action and a group.
     */
    @Test
    public void factories() {
        Region icon = new Region();
        RXSidebarItem page = RXSidebarItem.of("Page", icon);
        assertEquals("Page", page.getText());
        assertSame(icon, page.getGraphic());
        assertTrue(page.isSelectable());

        RXSidebarItem action = RXSidebarItem.action("Action", null, event -> { });
        assertFalse(action.isSelectable());
        assertTrue(action.getOnAction() != null);

        RXSidebarItem child = RXSidebarItem.of("Child");
        RXSidebarItem group = RXSidebarItem.group("Group", null, child);
        assertEquals(List.of(child), group.getItems());
        assertSame(group, child.getParent());
    }

    /**
     * Adding a subtree to a sidebar sets parent and sidebar throughout it;
     * removing it clears them again.
     */
    @Test
    public void ownershipFollowsTheLists() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarItem leaf = RXSidebarItem.of("Leaf");
        RXSidebarItem inner = RXSidebarItem.group("Inner", null, leaf);
        RXSidebarItem outer = RXSidebarItem.group("Outer", null, inner);
        assertNull(leaf.getSidebar());

        sidebar.getItems().add(outer);
        assertNull(outer.getParent());
        assertSame(inner, leaf.getParent());
        assertSame(sidebar, outer.getSidebar());
        assertSame(sidebar, leaf.getSidebar());

        RXSidebarItem late = RXSidebarItem.of("Late");
        inner.getItems().add(late);
        assertSame(sidebar, late.getSidebar());

        sidebar.getItems().remove(outer);
        assertNull(outer.getSidebar());
        assertNull(leaf.getSidebar());
        assertSame(inner, leaf.getParent());
    }

    /**
     * An item that already belongs to a list, null, a duplicate and a cycle
     * are all rejected.
     */
    @Test
    public void listsRejectWhatCannotBeOwnedOnce() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebar other = new RXSidebar();
        RXSidebarItem top = RXSidebarItem.of("Top");
        RXSidebarItem child = RXSidebarItem.of("Child");
        RXSidebarItem group = RXSidebarItem.group("Group", null, child);
        sidebar.getItems().addAll(top, group);

        assertThrows(IllegalArgumentException.class, () -> sidebar.getItems().add(top));
        assertThrows(IllegalArgumentException.class, () -> sidebar.getBottomItems().add(top));
        assertThrows(IllegalArgumentException.class, () -> other.getItems().add(top));
        assertThrows(IllegalArgumentException.class, () -> group.getItems().add(top));
        assertThrows(IllegalArgumentException.class, () -> sidebar.getItems().add(child));
        assertThrows(IllegalArgumentException.class, () -> sidebar.getItems().add(null));

        RXSidebarItem free = RXSidebarItem.of("Free");
        assertThrows(IllegalArgumentException.class, () -> sidebar.getTopItems().addAll(free, free));

        RXSidebarItem root = RXSidebarItem.group("Root", null, RXSidebarItem.group("Mid", null));
        RXSidebarItem mid = root.getItems().get(0);
        assertThrows(IllegalArgumentException.class, () -> mid.getItems().add(root));
        assertThrows(IllegalArgumentException.class, () -> root.getItems().add(root));
    }

    /**
     * A rejected bulk call leaves the list as it was.
     */
    @Test
    public void rejectedBulkCallChangesNothing() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarItem held = RXSidebarItem.of("Held");
        sidebar.getItems().add(held);
        RXSidebarItem fresh = RXSidebarItem.of("Fresh");

        assertThrows(IllegalArgumentException.class, () -> sidebar.getTopItems().addAll(fresh, held));
        assertTrue(sidebar.getTopItems().isEmpty());
        assertNull(fresh.getSidebar());

        assertThrows(IllegalArgumentException.class,
                () -> sidebar.getItems().setAll(fresh, RXSidebarItem.of("X"), fresh));
        assertEquals(List.of(held), sidebar.getItems());
        assertSame(sidebar, held.getSidebar());
    }

    /**
     * Sorting, setting an item onto itself and setAll of the same items keep
     * the items owned and the selection in place.
     */
    @Test
    public void movesWithinAListKeepOwnershipAndSelection() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarItem b = RXSidebarItem.of("B");
        RXSidebarItem a = RXSidebarItem.of("A");
        sidebar.getItems().addAll(b, a);
        sidebar.selectItem(b);

        FXCollections.sort(sidebar.getItems(), Comparator.comparing(RXSidebarItem::getText));
        assertEquals(List.of(a, b), sidebar.getItems());
        sidebar.getItems().sort(Comparator.comparing(RXSidebarItem::getText).reversed());
        assertEquals(List.of(b, a), sidebar.getItems());
        sidebar.getItems().sort(Comparator.comparing(RXSidebarItem::getText));
        sidebar.getItems().set(1, b);
        sidebar.getItems().setAll(b, a);
        sidebar.getItems().setAll(sidebar.getItems());

        assertEquals(List.of(b, a), sidebar.getItems());
        assertSame(sidebar, b.getSidebar());
        assertSame(b, sidebar.getSelectedItem());
        assertTrue(b.isSelected());
    }

    /**
     * An item removed from one place can be added to another.
     */
    @Test
    public void aRemovedItemCanBeAddedElsewhere() {
        RXSidebar sidebar = new RXSidebar();
        RXSidebarItem group = RXSidebarItem.group("Group", null);
        RXSidebarItem item = RXSidebarItem.of("Item");
        sidebar.getItems().addAll(group, item);

        sidebar.getItems().remove(item);
        group.getItems().add(item);

        assertSame(group, item.getParent());
        assertSame(sidebar, item.getSidebar());
    }
}
