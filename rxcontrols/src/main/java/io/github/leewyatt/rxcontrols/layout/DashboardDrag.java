package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.layout.DashboardSolver.Item;
import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout.Placement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One drag of one card with the cards kept packed upward, worked out step by step as the pointer moves: each step
 * starts from the layout the previous steps produced, not from the layout at the start of the drag.
 *
 * <p>Positions are whole cells; the pointer side is in pixels of a regular grid (a cell pitch and a margin inside each
 * cell), so that how far the dragged card covers another can be measured. A step:</p>
 * <ol>
 *     <li>turns the dragged element's position into a target cell, leaning back toward the cell it came from by the
 *     margin, so that a card has to pass a cell's middle by the margin to reach it;</li>
 *     <li>takes no notice of a card the dragged card covers by half or less, measured from the side it comes from;</li>
 *     <li>exchanges the dragged card with a card it covers that touches it and has the same size and row or column,
 *     or shares its row and height, or its column and width (the earlier of the two first, the other right after);</li>
 *     <li>moving down onto a card that could take the dragged card's place, or the place right above the target, puts
 *     the dragged card below that card instead, once per accepted step;</li>
 *     <li>otherwise pushes covered cards down below the target, cards covered by those further down;</li>
 *     <li>never puts the dragged card onto a locked card, and moves pushed cards past locked ones;</li>
 *     <li>packs every card that is not locked upward, the dragged card included, so the drop place is the highest it
 *     can reach.</li>
 * </ol>
 *
 * <p>Collisions are looked up in the order the cards are held in, which the steps re-sort as they go; a drag starts
 * with the cards in reading order of where they are, whatever came before. On a board without locked cards a pushed
 * card makes room across the whole width of its rows, so that a wide card cannot leap over smaller ones. A diagonal
 * step that covers too little is tried again along the axis it moves most on. A step that would leave two cards
 * overlapping, which pushing past locked cards can, is not taken. A chain of pushes that does not settle is
 * abandoned.</p>
 *
 * <p>The margin inside a cell is capped at a tenth of the cell, rounded, for the target cell's lean and the dragged
 * rectangle; the rectangles the first step measures cover against use the margin as given.</p>
 *
 * @param <K> the card key
 */
final class DashboardDrag<K> {

    // A pathological chain of pushes is abandoned rather than looped through.
    private static final int MAX_PUSHES_PER_CARD = 2;

    // The share of a card the dragged card must cover, measured from the side it comes from, before it counts.
    private static final double COVER_NEEDED = 0.5;

    // The largest margin, as a share of the cell, that a target cell leans back by.
    private static final double MARGIN_CAP = 0.1;

    private static final Comparator<Box<?>> TOP_DOWN =
            Comparator.comparingInt((Box<?> box) -> box.y).thenComparingInt(box -> box.x);

    private final List<Box<K>> boxes = new ArrayList<>();
    private final List<K> order = new ArrayList<>();
    private final Box<K> mover;
    private final int columns;
    private final double cellWidth;
    private final double cellHeight;
    // The margins, capped.
    private final double cappedX;
    private final double cappedY;
    private final boolean anyLocked;
    // Kept to take back a step that left cards overlapping.
    private final List<Box<K>> before = new ArrayList<>();
    private int[] beforeCells = new int[0];
    private double lastLeft;
    private double lastTop;
    private boolean skippedDown;

    // ==================== Construction ====================

    /**
     * Starts a drag from a layout.
     *
     * @param items      the cards at the start of the drag, a layout free of overlaps
     * @param moving     the key of the dragged card, which is not locked
     * @param columns    the column count
     * @param cellWidth  the horizontal pitch of a cell in pixels
     * @param cellHeight the vertical pitch of a cell in pixels
     * @param marginX    the horizontal margin inside a cell in pixels
     * @param marginY    the vertical margin inside a cell in pixels
     */
    DashboardDrag(List<Item<K>> items, K moving, int columns, double cellWidth, double cellHeight, double marginX,
                  double marginY) {
        Box<K> found = null;
        boolean locked = false;
        for (Item<K> item : items) {
            Box<K> box = new Box<>(item.key(), item.placement(), item.locked());
            boxes.add(box);
            order.add(item.key());
            locked |= item.locked();
            if (item.key().equals(moving)) {
                found = box;
            }
        }
        if (found == null) {
            throw new IllegalArgumentException("Not a card of the layout: " + moving);
        }
        boxes.sort(TOP_DOWN);
        mover = found;
        anyLocked = locked;
        this.columns = columns;
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        cappedX = Math.min(marginX, Math.round(cellWidth * MARGIN_CAP));
        cappedY = Math.min(marginY, Math.round(cellHeight * MARGIN_CAP));
        lastLeft = mover.x * cellWidth;
        lastTop = mover.y * cellHeight;
        measure(marginX, marginY);
    }

    // ==================== Stepping ====================

    /**
     * Restarts the lean of the target cell from the given element position, as after an accepted step.
     *
     * @param left the dragged element's left edge in grid pixels
     * @param top  the dragged element's top edge in grid pixels
     */
    void anchor(double left, double top) {
        lastLeft = left;
        lastTop = top;
    }

    /**
     * The cell the dragged element at this position aims at, leaning back toward the last accepted position.
     *
     * @param left the dragged element's left edge in grid pixels
     * @param top  the dragged element's top edge in grid pixels
     * @return the column and row, the column not yet kept inside the grid
     */
    int[] target(double left, double top) {
        double x = left + (left > lastLeft ? -cappedX : cappedX);
        double y = top + (top > lastTop ? -cappedY : cappedY);
        return new int[] {(int) Math.round(x / cellWidth), (int) Math.round(y / cellHeight)};
    }

    /**
     * Moves the dragged element to a position and applies the step it leads to.
     *
     * @param left the dragged element's left edge in grid pixels
     * @param top  the dragged element's top edge in grid pixels
     * @return {@code true} if the step was accepted
     */
    boolean step(double left, double top) {
        int[] cell = target(left, top);
        if (mover.x == cell[0] && mover.y == cell[1]) {
            return false;
        }
        Rect dragged = new Rect(left + cappedX, top + cappedY, mover.w * cellWidth - cappedX - cappedX,
                mover.h * cellHeight - cappedY - cappedY);
        boolean wasSkippedDown = skippedDown;
        keep();
        boolean accepted = move(mover, cell[0], cell[1], null, false, true, dragged);
        if (overlapping()) {
            restore();
            skippedDown = wasSkippedDown;
            return false;
        }
        if (accepted) {
            lastLeft = left;
            lastTop = top;
            measure(cappedX, cappedY);
            skippedDown = false;
        }
        return accepted;
    }

    private void keep() {
        before.clear();
        before.addAll(boxes);
        beforeCells = new int[boxes.size() * 2];
        for (int i = 0; i < before.size(); i++) {
            beforeCells[2 * i] = before.get(i).x;
            beforeCells[2 * i + 1] = before.get(i).y;
        }
    }

    private void restore() {
        boxes.clear();
        boxes.addAll(before);
        for (int i = 0; i < boxes.size(); i++) {
            boxes.get(i).x = beforeCells[2 * i];
            boxes.get(i).y = beforeCells[2 * i + 1];
        }
    }

    private boolean overlapping() {
        for (int i = 0; i < boxes.size(); i++) {
            Box<K> a = boxes.get(i);
            Spot spot = new Spot(a.x, a.y, a.w, a.h);
            for (int j = i + 1; j < boxes.size(); j++) {
                if (spot.overlaps(boxes.get(j))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @return the placements, in the order the cards were given
     */
    Map<K, Placement> layout() {
        Map<K, Box<K>> byKey = new LinkedHashMap<>();
        for (Box<K> box : boxes) {
            byKey.put(box.key, box);
        }
        Map<K, Placement> result = new LinkedHashMap<>();
        for (K key : order) {
            Box<K> box = byKey.get(key);
            result.put(key, new Placement(box.x, box.y, box.w, box.h));
        }
        return result;
    }

    // ==================== Moving ====================

    // Moves a card toward a cell and resolves what it lands on. The dragged card at the top level measures its cover
    // with the dragged rectangle; pushed cards are moved nested, without packing. Returns whether the card moved.
    private boolean move(Box<K> box, int x, int y, Box<K> skip, boolean nested, boolean pack, Rect dragged) {
        Spot to = new Spot(Math.max(x, 0), Math.max(y, 0), box.w, box.h);
        if (to.x + to.w > columns) {
            to.x = columns - to.w;
        }
        if (to.x == box.x && to.y == box.y) {
            return false;
        }
        int fromX = box.x;
        int fromY = box.y;
        List<Box<K>> hits = all(box, to, skip);
        boolean go = true;
        if (!hits.isEmpty()) {
            boolean dragging = box == mover && !nested;
            Box<K> hit = dragging ? covered(box, dragged, hits) : hits.get(0);
            if (hit != null) {
                go = !resolve(box, to, hit, skip, nested, pack);
            } else {
                go = false;
                if (dragging && to.x != box.x && to.y != box.y) {
                    boolean across = Math.abs(to.x - box.x) >= Math.abs(to.y - box.y);
                    Spot along = across ? new Spot(to.x, box.y, to.w, to.h) : new Spot(box.x, to.y, to.w, to.h);
                    List<Box<K>> alongHits = all(box, along, skip);
                    Box<K> alongHit = alongHits.isEmpty() ? null : covered(box, dragged, alongHits);
                    if (alongHits.isEmpty() || alongHit != null) {
                        go = alongHits.isEmpty() || !resolve(box, along, alongHit, skip, nested, pack);
                        if (go) {
                            to.x = along.x;
                            to.y = along.y;
                        }
                    }
                }
            }
        }
        if (go && anyIsLocked(all(box, to, skip))) {
            go = false;
        }
        if (go && (to.x != box.x || to.y != box.y)) {
            box.x = to.x;
            box.y = to.y;
        }
        if (pack) {
            pack();
        }
        return box.x != fromX || box.y != fromY;
    }

    // Makes room for a card going to a spot it covers another card at. Returns whether the card itself moved (past
    // another, or exchanged), in which case the caller does not move it to the spot.
    private boolean resolve(Box<K> box, Spot to, Box<K> hit, Box<K> skip, boolean nested, boolean pack) {
        boxes.sort(TOP_DOWN.reversed());
        Box<K> other = hit != null ? hit : first(box, to, null);
        if (other == null) {
            return false;
        }
        if (box == mover && !nested && exchange(box, other)) {
            return true;
        }
        // On a board without locked cards, a pushed card (or the dragged one going up, sideways or after skipping)
        // makes room across the full width of its rows; otherwise the area follows the spot as it changes.
        Spot area = to;
        if (!anyLocked && (box != mover || skippedDown || to.y <= box.y)) {
            area = new Spot(0, to.y, columns, to.h);
            other = first(box, area, skip);
        }
        boolean moved = false;
        int pushes = 0;
        while (true) {
            if (other == null) {
                other = first(box, area, skip);
            }
            if (other == null || pushes++ > boxes.size() * MAX_PUSHES_PER_CARD) {
                return moved;
            }
            boolean stepped;
            if (other.locked || (box == mover && !skippedDown && to.y > box.y && fitsAbove(other, box, to))) {
                if (box == mover) {
                    skippedDown = skippedDown || to.y > box.y;
                }
                stepped = move(box, to.x, other.y + other.h, null, true, false, null);
                if (other.locked && stepped) {
                    to.x = box.x;
                    to.y = box.y;
                } else if (!other.locked && stepped && pack) {
                    pack();
                    to.y = other.y + other.h;
                    box.x = to.x;
                    box.y = to.y;
                }
                moved = moved || stepped;
            } else {
                stepped = move(other, other.x, to.y + to.h, box, true, false, null);
            }
            if (!stepped) {
                return moved;
            }
            other = null;
        }
    }

    // Whether a card the dragged one moves down onto could take the dragged card's row, or the rows right above the
    // target, so that the dragged card should go below it instead of pushing it.
    private boolean fitsAbove(Box<K> other, Box<K> dragged, Spot to) {
        return first(other, new Spot(other.x, dragged.y, other.w, other.h), dragged) == null
                || first(other, new Spot(other.x, to.y - other.h, other.w, other.h), dragged) == null;
    }

    // Exchanges the dragged card with a touching card of the same size in its row or column, or sharing its row and
    // height, or its column and width. With different sizes, the earlier card takes the earlier place and the other
    // goes right after it.
    private boolean exchange(Box<K> a, Box<K> b) {
        if (a.locked || b.locked) {
            return false;
        }
        Boolean touch = null;
        if (a.w == b.w && a.h == b.h && (a.x == b.x || a.y == b.y)) {
            touch = touching(a, b);
            if (touch) {
                return swap(a, b);
            }
        }
        if (Boolean.FALSE.equals(touch)) {
            return false;
        }
        if (a.w == b.w && a.x == b.x) {
            touch = touching(a, b);
            if (touch) {
                return b.y < a.y ? swap(b, a) : swap(a, b);
            }
            return false;
        }
        if (a.h == b.h && a.y == b.y) {
            touch = touching(a, b);
            if (touch) {
                return b.x < a.x ? swap(b, a) : swap(a, b);
            }
        }
        return false;
    }

    // The second card takes the first one's place; the first takes the second's, or goes right after it when their
    // heights or widths differ.
    private static boolean swap(Box<?> first, Box<?> second) {
        int x = second.x;
        int y = second.y;
        second.x = first.x;
        second.y = first.y;
        if (first.h != second.h) {
            first.x = x;
            first.y = second.y + second.h;
        } else if (first.w != second.w) {
            first.x = second.x + second.w;
            first.y = y;
        } else {
            first.x = x;
            first.y = y;
        }
        return true;
    }

    // ==================== Packing ====================

    // Every card that is not locked rises as far as nothing is in its way, top to bottom.
    private void pack() {
        boxes.sort(TOP_DOWN);
        for (int i = 0; i < boxes.size(); i++) {
            Box<K> box = boxes.get(i);
            if (box.locked) {
                continue;
            }
            while (box.y > 0) {
                int up = i == 0 ? 0 : box.y - 1;
                if (i != 0 && first(box, new Spot(box.x, up, box.w, box.h), null) != null) {
                    break;
                }
                box.y = up;
            }
        }
    }

    // ==================== Cover ====================

    // The card covered the most, past half of it, by the dragged rectangle stretched back to where the dragged card
    // was last accepted; only from the sides the dragged card was clear of. Locked cards never count.
    private Box<K> covered(Box<K> box, Rect dragged, List<Box<K>> hits) {
        Rect from = box.rect;
        double x = dragged.x;
        double y = dragged.y;
        double w = dragged.w;
        double h = dragged.h;
        if (y > from.y) {
            h = h + y - from.y;
            y = from.y;
        } else {
            h = h + from.y - y;
        }
        if (x > from.x) {
            w = w + x - from.x;
            x = from.x;
        } else {
            w = w + from.x - x;
        }
        Box<K> best = null;
        double most = COVER_NEEDED;
        for (Box<K> hit : hits) {
            if (hit.locked) {
                continue;
            }
            Rect r = hit.rect;
            double down = Double.MAX_VALUE;
            double across = Double.MAX_VALUE;
            if (from.y + from.h <= r.y) {
                down = (y + h - r.y) / r.h;
            } else if (from.y >= r.y + r.h) {
                down = (r.y + r.h - y) / r.h;
            }
            if (from.x + from.w <= r.x) {
                across = (x + w - r.x) / r.w;
            } else if (from.x >= r.x + r.w) {
                across = (r.x + r.w - x) / r.w;
            }
            double cover = Math.min(across, down);
            if (cover > most) {
                most = cover;
                best = hit;
            }
        }
        return best;
    }

    // Every card's rectangle in grid pixels, as of the last accepted step.
    private void measure(double insetX, double insetY) {
        for (Box<K> box : boxes) {
            box.rect = new Rect(box.x * cellWidth + insetX, box.y * cellHeight + insetY,
                    box.w * cellWidth - insetX - insetX, box.h * cellHeight - insetY - insetY);
        }
    }

    // ==================== Lookup ====================

    private Box<K> first(Box<K> skip, Spot area, Box<K> alsoSkip) {
        for (Box<K> box : boxes) {
            if (box != skip && box != alsoSkip && area.overlaps(box)) {
                return box;
            }
        }
        return null;
    }

    private List<Box<K>> all(Box<K> skip, Spot area, Box<K> alsoSkip) {
        List<Box<K>> hits = new ArrayList<>();
        for (Box<K> box : boxes) {
            if (box != skip && box != alsoSkip && area.overlaps(box)) {
                hits.add(box);
            }
        }
        return hits;
    }

    private static boolean anyIsLocked(List<? extends Box<?>> hits) {
        for (Box<?> hit : hits) {
            if (hit.locked) {
                return true;
            }
        }
        return false;
    }

    // Overlapping or sharing an edge or a corner.
    private static boolean touching(Box<?> a, Box<?> b) {
        double left = b.x - 0.5;
        double top = b.y - 0.5;
        return !(a.y >= top + b.h + 1 || a.y + a.h <= top || a.x + a.w <= left || a.x >= left + b.w + 1);
    }

    // ==================== Records ====================

    private static final class Box<K> {
        final K key;
        final int w;
        final int h;
        final boolean locked;
        int x;
        int y;
        Rect rect;

        Box(K key, Placement placement, boolean locked) {
            this.key = key;
            this.x = placement.column();
            this.y = placement.row();
            this.w = placement.columnSpan();
            this.h = placement.rowSpan();
            this.locked = locked;
        }
    }

    private static final class Spot {
        int x;
        int y;
        final int w;
        final int h;

        Spot(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean overlaps(Box<?> box) {
            return !(box.y >= y + h || box.y + box.h <= y || box.x + box.w <= x || box.x >= x + w);
        }
    }

    private record Rect(double x, double y, double w, double h) {
    }
}
