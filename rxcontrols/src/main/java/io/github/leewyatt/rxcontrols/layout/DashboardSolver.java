package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout.Placement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The cell-coordinate rules of {@link RXDashboardPane}: solving an edit against the other cards, compacting,
 * correcting a restored snapshot, converting between column counts and finding the first free area. Every
 * function is pure: it reads its arguments, never changes them, and returns a new map whose keys follow the order
 * of the items passed in. Keys identify cards; the pane passes its cards, which compare by identity.
 *
 * <p>Here cards only ever move straight down when pushed, and only straight up when compacted; nothing moves
 * sideways, and locked cards never move. Solving from the layout a gesture started with makes dragging a card away and
 * back again restore every neighbour. A drag that keeps the cards packed upward, which may exchange cards sideways, is
 * worked out step by step by {@link DashboardDrag} instead.</p>
 */
final class DashboardSolver {

    /**
     * The largest row {@link #clamp} lets a card start at. Rows are unbounded in principle; capping the rows and
     * row spans that come from code and snapshots keeps every sum of rows and spans the rules compute inside
     * {@code int}, since pushing only ever adds spans of cards already in the layout.
     */
    static final int MAX_ROW = 10_000;

    /**
     * The largest row span {@link #clamp} lets a card have, for the same reason as {@link #MAX_ROW}.
     */
    static final int MAX_ROW_SPAN = 10_000;

    // Top to bottom, then left to right: the order neighbours are settled and cards are compacted in.
    private static final Comparator<Item<?>> READING_ORDER =
            Comparator.comparingInt((Item<?> item) -> item.placement().row())
                    .thenComparingInt(item -> item.placement().column());

    /**
     * One card as the rules see it: where it is, whether it is locked, and the spans it accepts.
     *
     * @param key           the card
     * @param placement     where the card is now
     * @param locked        whether the card is locked: other cards never push it and never land on it
     * @param minColumnSpan the smallest column span the card accepts
     * @param maxColumnSpan the largest column span the card accepts
     * @param minRowSpan    the smallest row span the card accepts
     * @param maxRowSpan    the largest row span the card accepts
     * @param <K>           the key type
     */
    record Item<K>(K key, Placement placement, boolean locked,
                   int minColumnSpan, int maxColumnSpan, int minRowSpan, int maxRowSpan) {

        /**
         * Returns an item that accepts any span.
         *
         * @param key       the card
         * @param placement where the card is now
         * @param locked    whether the card is locked
         * @param <K>       the key type
         * @return the item
         */
        static <K> Item<K> of(K key, Placement placement, boolean locked) {
            return new Item<>(key, placement, locked, 1, Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
        }

        /**
         * Returns this item at another placement.
         *
         * @param other the new placement
         * @return the moved item
         */
        Item<K> at(Placement other) {
            return new Item<>(key, other, locked, minColumnSpan, maxColumnSpan, minRowSpan, maxRowSpan);
        }
    }

    private DashboardSolver() {
    }

    // ==================== Editing ====================

    /**
     * Solves an edit: {@code edited} takes the candidate area and the other cards make room according to the
     * collision policy. The candidate is rejected when it leaves the grid, overlaps a locked card, or, when
     * blocking, overlaps any card. Otherwise, when pushing, every card that is not locked keeps its place unless it
     * overlaps a card already settled, in which case it moves straight down until it is free; cards settle top to
     * bottom, left to right, after the edited card and the locked cards.
     *
     * @param start     the layout to solve from; may or may not contain the edited card
     * @param edited    the card being edited; its own placement in {@code start}, if any, is ignored
     * @param candidate the area the edited card asks for
     * @param block     {@code true} to reject any overlap instead of pushing
     * @param columns   the column count, at least 1
     * @param <K>       the key type
     * @return the solved layout, the edited card last if {@code start} does not contain it, or {@code null} if the
     *         candidate is rejected
     */
    static <K> Map<K, Placement> solve(List<Item<K>> start, K edited, Placement candidate, boolean block,
                                       int columns) {
        if (!fits(candidate, columns)) {
            return null;
        }
        Map<K, Placement> placed = new LinkedHashMap<>();
        placed.put(edited, candidate);
        List<Item<K>> movable = new ArrayList<>();
        for (Item<K> item : start) {
            if (item.key().equals(edited)) {
                continue;
            }
            boolean hit = overlaps(item.placement(), candidate);
            if (hit && (item.locked() || block)) {
                return null;
            }
            if (item.locked()) {
                placed.put(item.key(), item.placement());
            } else {
                movable.add(item);
            }
        }
        settle(movable, placed);
        return inOrder(start, placed);
    }

    /**
     * Adds a card at a requested area. The request is first clamped into the grid and into the card's span limits,
     * then solved like an edit; when the solver rejects it, the card goes to the first free area instead and no
     * other card moves.
     *
     * @param existing  the cards already in the layout
     * @param added     the card being added
     * @param requested the area asked for
     * @param block     {@code true} to reject any overlap instead of pushing
     * @param columns   the column count, at least 1
     * @param <K>       the key type
     * @return the layout with the added card last
     */
    static <K> Map<K, Placement> add(List<Item<K>> existing, Item<K> added, Placement requested, boolean block,
                                     int columns) {
        Placement candidate = clamp(added, requested, columns);
        Map<K, Placement> solved = solve(existing, added.key(), candidate, block, columns);
        if (solved != null) {
            return solved;
        }
        Map<K, Placement> result = placements(existing);
        result.put(added.key(), firstFit(result.values(), candidate.columnSpan(), candidate.rowSpan(), columns));
        return result;
    }

    /**
     * Adds a card with no requested position at the first free area, keeping its spans.
     *
     * @param existing the cards already in the layout
     * @param added    the card being added; its placement supplies the spans
     * @param columns  the column count, at least 1
     * @param <K>      the key type
     * @return the layout with the added card last
     */
    static <K> Map<K, Placement> addAtFirstFit(List<Item<K>> existing, Item<K> added, int columns) {
        Placement spans = clamp(added, added.placement(), columns);
        Map<K, Placement> result = placements(existing);
        result.put(added.key(), firstFit(result.values(), spans.columnSpan(), spans.rowSpan(), columns));
        return result;
    }

    // ==================== Compacting ====================

    /**
     * Moves every card that is not locked straight up as far as it goes, top to bottom, left to right. Locked
     * cards stay; no card changes column. Compacting a compacted layout changes nothing.
     *
     * @param items the layout, free of overlaps
     * @param <K>   the key type
     * @return the compacted layout
     */
    static <K> Map<K, Placement> compact(List<Item<K>> items) {
        Map<K, Placement> placed = new LinkedHashMap<>();
        List<Item<K>> movable = new ArrayList<>();
        for (Item<K> item : items) {
            if (item.locked()) {
                placed.put(item.key(), item.placement());
            } else {
                movable.add(item);
            }
        }
        movable.sort(READING_ORDER);
        for (Item<K> item : movable) {
            Placement own = item.placement();
            // A card can rise to the bottom of the lowest settled card above it in its columns. In a layout free
            // of overlaps nothing settled can sit across its rows, so that row is free.
            int floor = 0;
            for (Placement other : placed.values()) {
                int bottom = other.row() + other.rowSpan();
                if (overlapsColumns(own, other) && bottom <= own.row()) {
                    floor = Math.max(floor, bottom);
                }
            }
            Placement raised = own.withPosition(own.column(), floor);
            placed.put(item.key(), overlapsAny(raised, placed.values()) ? own : raised);
        }
        return inOrder(items, placed);
    }

    // ==================== Restoring ====================

    /**
     * Arranges every card from a target layout, the way a restored snapshot or a column-count change is applied.
     * Cards with a target placement are converted from {@code targetColumns} to {@code columns}, clamped into the
     * grid and into their span limits, then freed of overlaps: locked cards claim their area first, the others
     * settle top to bottom, left to right, moving straight down. The cards without a target placement follow in
     * the order given: each first tries its own placement, converted from {@code currentColumns}, and keeps it if
     * it is free, otherwise takes the first free area with the same spans.
     *
     * @param items          every card, with its current placement, in collection order
     * @param target         the target placement of some of the cards, in {@code targetColumns}
     * @param targetColumns  the column count of the target placements, at least 1
     * @param currentColumns the column count of the items' current placements, at least 1
     * @param columns        the column count to arrange for, at least 1
     * @param <K>            the key type
     * @return the arranged layout
     */
    static <K> Map<K, Placement> arrange(List<Item<K>> items, Map<K, Placement> target, int targetColumns,
                                         int currentColumns, int columns) {
        Map<K, Placement> placed = new LinkedHashMap<>();
        List<Item<K>> matched = new ArrayList<>();
        List<Item<K>> unmatched = new ArrayList<>();
        for (Item<K> item : items) {
            Placement wanted = target.get(item.key());
            if (wanted == null) {
                unmatched.add(item);
            } else {
                matched.add(item.at(clamp(item, rescale(wanted, targetColumns, columns), columns)));
            }
        }
        List<Item<K>> movable = new ArrayList<>();
        for (Item<K> item : matched) {
            if (item.locked() && !overlapsAny(item.placement(), placed.values())) {
                placed.put(item.key(), item.placement());
            } else {
                movable.add(item);
            }
        }
        settle(movable, placed);
        for (Item<K> item : unmatched) {
            Placement own = clamp(item, rescale(item.placement(), currentColumns, columns), columns);
            if (overlapsAny(own, placed.values())) {
                own = firstFit(placed.values(), own.columnSpan(), own.rowSpan(), columns);
            }
            placed.put(item.key(), own);
        }
        return inOrder(items, placed);
    }

    // ==================== Primitives ====================

    /**
     * Clamps a placement into the grid and into a card's span limits. Spans first take the card's limits (the
     * minimum wins over a smaller maximum, and neither goes below 1), then the grid: the column span never exceeds
     * the column count and the row span never exceeds {@link #MAX_ROW_SPAN}. The card then moves left until it
     * fits, and negative positions become 0.
     *
     * @param item      the card whose span limits apply
     * @param placement the placement to clamp
     * @param columns   the column count, at least 1
     * @return the clamped placement
     */
    static Placement clamp(Item<?> item, Placement placement, int columns) {
        int columnSpan = Math.min(limit(placement.columnSpan(), item.minColumnSpan(), item.maxColumnSpan()), columns);
        int rowSpan = Math.min(limit(placement.rowSpan(), item.minRowSpan(), item.maxRowSpan()), MAX_ROW_SPAN);
        int column = Math.max(0, Math.min(placement.column(), columns - columnSpan));
        int row = Math.max(0, Math.min(placement.row(), MAX_ROW));
        return new Placement(column, row, columnSpan, rowSpan);
    }

    /**
     * Converts a placement to another column count: the left and right edges are each scaled and rounded, so two
     * cards that share an edge still share it afterwards. Rows and row spans do not change. The result may need
     * {@link #clamp} to fit the grid.
     *
     * @param placement the placement
     * @param from      the column count it refers to, at least 1
     * @param to        the column count to convert to, at least 1
     * @return the converted placement
     */
    static Placement rescale(Placement placement, int from, int to) {
        if (from == to) {
            return placement;
        }
        long left = roundedRatio((long) placement.column() * to, from);
        long right = roundedRatio(((long) placement.column() + placement.columnSpan()) * to, from);
        int column = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, left));
        int columnSpan = (int) Math.max(1, Math.min(Integer.MAX_VALUE, right - left));
        return new Placement(column, placement.row(), columnSpan, placement.rowSpan());
    }

    /**
     * Returns the first area of the given spans that overlaps none of the placed cards, scanning rows from the top
     * and, within a row, columns from the left.
     *
     * @param placed     the areas already taken
     * @param columnSpan the column span, clamped to {@code [1, columns]}
     * @param rowSpan    the row span, at least 1
     * @param columns    the column count, at least 1
     * @return the first free area
     */
    static Placement firstFit(Collection<Placement> placed, int columnSpan, int rowSpan, int columns) {
        int width = Math.max(1, Math.min(columnSpan, columns));
        int height = Math.max(1, rowSpan);
        // The first free area starts on row 0 or right below some card, and in column 0 or right after some card:
        // one row or column earlier would otherwise be free as well.
        TreeSet<Integer> rows = new TreeSet<>();
        TreeSet<Integer> starts = new TreeSet<>();
        rows.add(0);
        starts.add(0);
        for (Placement other : placed) {
            rows.add(other.row() + other.rowSpan());
            if (other.column() + other.columnSpan() <= columns - width) {
                starts.add(other.column() + other.columnSpan());
            }
        }
        for (int row : rows) {
            for (int column : starts) {
                Placement candidate = new Placement(column, row, width, height);
                if (!overlapsAny(candidate, placed)) {
                    return candidate;
                }
            }
        }
        // Unreachable: the row below every card is free.
        throw new IllegalStateException("No free area");
    }

    /**
     * Returns whether two placements share at least one cell.
     *
     * @param a one placement
     * @param b the other placement
     * @return whether they overlap
     */
    static boolean overlaps(Placement a, Placement b) {
        return overlapsColumns(a, b)
                && (long) a.row() < (long) b.row() + b.rowSpan() && (long) b.row() < (long) a.row() + a.rowSpan();
    }

    /**
     * Returns whether a placement lies inside a grid of the given column count.
     *
     * @param placement the placement
     * @param columns   the column count
     * @return whether it starts on a non-negative cell, spans at least one cell each way and ends inside the
     *         columns
     */
    static boolean fits(Placement placement, int columns) {
        return placement.column() >= 0 && placement.row() >= 0 && placement.columnSpan() >= 1
                && placement.rowSpan() >= 1 && (long) placement.column() + placement.columnSpan() <= columns;
    }

    // ==================== Helpers ====================

    // Settles the movable cards into the placed ones, top to bottom, left to right: each keeps its place or moves
    // down to the first row where it overlaps nothing settled. Jumping to the bottom of the card in the way is the
    // smallest move that clears it, so no free row is skipped.
    private static <K> void settle(List<Item<K>> movable, Map<K, Placement> placed) {
        List<Item<K>> ordered = new ArrayList<>(movable);
        ordered.sort(READING_ORDER);
        for (Item<K> item : ordered) {
            Placement moved = item.placement();
            boolean free = false;
            while (!free) {
                free = true;
                for (Placement other : placed.values()) {
                    if (overlaps(moved, other)) {
                        moved = moved.withPosition(moved.column(), other.row() + other.rowSpan());
                        free = false;
                    }
                }
            }
            placed.put(item.key(), moved);
        }
    }

    private static boolean overlapsColumns(Placement a, Placement b) {
        return (long) a.column() < (long) b.column() + b.columnSpan()
                && (long) b.column() < (long) a.column() + a.columnSpan();
    }

    private static boolean overlapsAny(Placement placement, Collection<Placement> others) {
        for (Placement other : others) {
            if (overlaps(placement, other)) {
                return true;
            }
        }
        return false;
    }

    // The minimum wins over a smaller maximum; neither goes below 1.
    private static int limit(int span, int min, int max) {
        int low = Math.max(1, min);
        int high = Math.max(low, max);
        return Math.max(low, Math.min(high, span));
    }

    // value / divisor rounded half up, for a positive divisor; exact for any long value.
    private static long roundedRatio(long value, int divisor) {
        long quotient = Math.floorDiv(value, divisor);
        long remainder = Math.floorMod(value, divisor);
        return 2 * remainder >= divisor ? quotient + 1 : quotient;
    }

    private static <K> Map<K, Placement> placements(List<Item<K>> items) {
        Map<K, Placement> result = new LinkedHashMap<>();
        for (Item<K> item : items) {
            result.put(item.key(), item.placement());
        }
        return result;
    }

    // The placed cards in the order of the items, then any placed card the items do not contain.
    private static <K> Map<K, Placement> inOrder(List<Item<K>> items, Map<K, Placement> placed) {
        Map<K, Placement> result = new LinkedHashMap<>();
        for (Item<K> item : items) {
            Placement placement = placed.get(item.key());
            if (placement != null) {
                result.put(item.key(), placement);
            }
        }
        for (Map.Entry<K, Placement> entry : placed.entrySet()) {
            result.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
