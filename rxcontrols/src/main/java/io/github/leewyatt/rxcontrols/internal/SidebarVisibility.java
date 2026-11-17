package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.RXSidebarHeader;
import io.github.leewyatt.rxcontrols.RXSidebarItem;
import io.github.leewyatt.rxcontrols.RXSidebarItemBase;
import io.github.leewyatt.rxcontrols.RXSidebarSeparator;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Decides which items of a sidebar tree are on display under the items' own
 * {@code visible} flags and an optional filter, and provides the stock text
 * filter. Pure computation over the item model; no scene graph involved.
 */
public final class SidebarVisibility {

    private SidebarVisibility() {
    }

    /**
     * Builds the stock filter: an item matches when its text or one of its
     * keywords contains the query, ignoring case and the white space around the
     * query.
     *
     * @param query the search text
     * @return the filter, or {@code null} when the query is {@code null} or blank
     */
    public static Predicate<RXSidebarItem> textFilter(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return item -> {
            if (contains(item.getText(), needle)) {
                return true;
            }
            for (String keyword : item.getKeywords()) {
                if (contains(keyword, needle)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
    }

    /**
     * Computes the items on display.
     *
     * <p>An item whose {@code visible} is false is never shown, nor is anything
     * below it, and the filter is not asked about them. Without a filter every
     * other item is shown. With one, an ordinary item is shown when it matches,
     * when an item below it matches, or when an item above it matches; a
     * caption is shown when an item between it and the next caption of its list
     * is shown; separators are not shown. The filter is only asked about
     * ordinary items.</p>
     *
     * @param lists  the top-level lists of the tree
     * @param filter the filter, or {@code null} for none
     * @return the shown items, compared by identity
     */
    public static Set<RXSidebarItemBase> shownItems(List<? extends List<RXSidebarItemBase>> lists,
                                                Predicate<? super RXSidebarItem> filter) {
        Set<RXSidebarItemBase> shown = Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<RXSidebarItemBase> list : lists) {
            collect(list, filter, false, shown);
        }
        return shown;
    }

    // Returns whether anything in the list is shown.
    private static boolean collect(List<RXSidebarItemBase> items, Predicate<? super RXSidebarItem> filter,
                                   boolean ancestorMatched, Set<RXSidebarItemBase> shown) {
        boolean any = false;
        RXSidebarItemBase pendingCaption = null;
        for (RXSidebarItemBase item : items) {
            if (!item.isVisible()) {
                continue;
            }
            if (item instanceof RXSidebarSeparator) {
                if (filter == null) {
                    shown.add(item);
                }
                continue;
            }
            if (item instanceof RXSidebarHeader) {
                if (filter == null) {
                    shown.add(item);
                } else {
                    pendingCaption = item;
                }
                continue;
            }
            RXSidebarItem ordinary = (RXSidebarItem) item;
            boolean matched = filter == null || ancestorMatched || filter.test(ordinary);
            boolean below = collect(ordinary.getItems(), filter, filter != null && matched, shown);
            if (matched || below) {
                shown.add(item);
                any = true;
                if (pendingCaption != null) {
                    shown.add(pendingCaption);
                    pendingCaption = null;
                }
            }
        }
        return any;
    }
}
