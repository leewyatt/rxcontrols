package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.RXSidebarHeader;
import io.github.leewyatt.rxcontrols.RXSidebarItem;
import io.github.leewyatt.rxcontrols.RXSidebarSeparator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the sidebar's visibility computation and its stock text filter.
 */
public class SidebarVisibilityTest {

    /**
     * The text filter matches text and keywords by containment, ignoring case;
     * a blank query is no filter.
     */
    @Test
    public void textFilterMatchesTextAndKeywords() {
        assertNull(SidebarVisibility.textFilter(null));
        assertNull(SidebarVisibility.textFilter("   "));

        RXSidebarItem item = RXSidebarItem.of("RXSidebar");
        item.getKeywords().addAll("Menu", "Navigation");
        assertTrue(SidebarVisibility.textFilter("side").test(item));
        assertTrue(SidebarVisibility.textFilter(" NAVI ").test(item));
        assertFalse(SidebarVisibility.textFilter("button").test(item));
        assertFalse(SidebarVisibility.textFilter("x").test(new RXSidebarItem()));
    }

    /**
     * Without a filter everything is shown except invisible items and what is
     * below them.
     */
    @Test
    public void withoutAFilterOnlyInvisibleSubtreesAreLeftOut() {
        RXSidebarItem hiddenChild = RXSidebarItem.of("HiddenChild");
        RXSidebarItem hiddenGroup = RXSidebarItem.group("HiddenGroup", null, hiddenChild);
        hiddenGroup.setVisible(false);
        List<RXSidebarItem> list = List.of(new RXSidebarHeader("Caption"), new RXSidebarSeparator(),
                RXSidebarItem.of("Page"), hiddenGroup);

        assertEquals(List.of("Caption", "null", "Page"), shown(list, null));
    }

    /**
     * An item is shown when it, something below it, or something above it
     * matches.
     */
    @Test
    public void aMatchShowsItsAncestorsAndItsSubtree() {
        RXSidebarItem date = RXSidebarItem.group("Date", null,
                RXSidebarItem.of("Calendar"), RXSidebarItem.of("Clock"));
        RXSidebarItem input = RXSidebarItem.group("Input", null,
                RXSidebarItem.of("Date field"), RXSidebarItem.of("Text field"));
        RXSidebarItem other = RXSidebarItem.group("Other", null, RXSidebarItem.of("Badge"));
        List<RXSidebarItem> list = List.of(date, input, other);

        assertEquals(List.of("Date", "Calendar", "Clock", "Input", "Date field"),
                shown(list, SidebarVisibility.textFilter("date")));
    }

    /**
     * The rules hold at any depth and across lists: a deep match shows every
     * group above it, a matching group shows its whole subtree bar invisible
     * items, and a caption in a child list follows its own section.
     */
    @Test
    public void theRulesHoldAtAnyDepth() {
        RXSidebarItem leaf = RXSidebarItem.of("Leaf");
        RXSidebarItem hidden = RXSidebarItem.of("Hidden");
        hidden.setVisible(false);
        RXSidebarItem inner = RXSidebarItem.group("Inner", null,
                new RXSidebarHeader("Inside"), leaf, new RXSidebarHeader("Empty"), hidden);
        RXSidebarItem outer = RXSidebarItem.group("Outer", null, inner, RXSidebarItem.of("Sibling"));
        List<RXSidebarItem> main = List.of(outer);
        List<RXSidebarItem> bottom = List.of(RXSidebarItem.of("Leaflet"));

        Set<RXSidebarItem> deep = SidebarVisibility.shownItems(List.of(main, bottom),
                SidebarVisibility.textFilter("leaf"));
        assertEquals(List.of("Outer", "Inner", "Inside", "Leaf"), texts(main, deep));
        assertEquals(List.of("Leaflet"), texts(bottom, deep));

        Set<RXSidebarItem> whole = SidebarVisibility.shownItems(List.of(main, bottom),
                SidebarVisibility.textFilter("outer"));
        assertEquals(List.of("Outer", "Inner", "Inside", "Leaf", "Sibling"), texts(main, whole));
        assertEquals(List.of(), texts(bottom, whole));
    }

    /**
     * Invisible items are neither shown nor asked; captions and separators are
     * never asked.
     */
    @Test
    public void theFilterIsOnlyAskedAboutVisibleOrdinaryItems() {
        RXSidebarItem hidden = RXSidebarItem.of("Hidden");
        hidden.setVisible(false);
        RXSidebarItem group = RXSidebarItem.group("Group", null, hidden);
        List<RXSidebarItem> list = List.of(new RXSidebarHeader("Caption"), new RXSidebarSeparator(), group);
        List<String> asked = new ArrayList<>();

        assertEquals(List.of(), shown(list, item -> {
            asked.add(item.getText());
            return "Hidden".equals(item.getText());
        }));
        assertEquals(List.of("Group"), asked);
    }

    /**
     * Under a filter a caption is shown only when an item before the next
     * caption is, and separators are hidden.
     */
    @Test
    public void captionsFollowTheirSectionAndSeparatorsHide() {
        List<RXSidebarItem> list = List.of(
                new RXSidebarHeader("First"), RXSidebarItem.of("Alpha"), new RXSidebarSeparator(),
                new RXSidebarHeader("Second"), RXSidebarItem.of("Beta"));

        assertEquals(List.of("Second", "Beta"), shown(list, SidebarVisibility.textFilter("beta")));
    }

    // The texts of the shown items, in tree order.
    private static List<String> shown(List<RXSidebarItem> list, Predicate<RXSidebarItem> filter) {
        Set<RXSidebarItem> shown = SidebarVisibility.shownItems(List.of(list), filter);
        List<String> texts = new ArrayList<>();
        collect(list, shown, texts);
        assertEquals(shown.size(), texts.size());
        return texts;
    }

    private static List<String> texts(List<RXSidebarItem> list, Set<RXSidebarItem> shown) {
        List<String> texts = new ArrayList<>();
        collect(list, shown, texts);
        return texts;
    }

    private static void collect(List<RXSidebarItem> items, Set<RXSidebarItem> shown, List<String> texts) {
        for (RXSidebarItem item : items) {
            if (shown.contains(item)) {
                texts.add(String.valueOf(item.getText()));
            }
            collect(item.getItems(), shown, texts);
        }
    }
}
