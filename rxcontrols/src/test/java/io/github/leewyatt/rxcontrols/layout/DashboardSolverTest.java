package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.layout.DashboardSolver.Item;
import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout.Placement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DashboardSolver}: the push and block rules on the three cases of the design research (collision
 * and making room, locking and cancelling, column conversion and restoring), compacting, the corrections applied
 * to a restored snapshot, adding with and without a requested position, and a randomized run that no sequence of
 * operations ever leaves two cards overlapping.
 */
public class DashboardSolverTest {

    private static final int FOUR = 4;
    private static final int TWELVE = 12;

    // ==================== Case 1: collision and making room ====================

    /**
     * Verifies that dragging A one cell right pushes B and C down, and is rejected when blocking.
     */
    @Test
    public void dragRightPushesNeighboursDown() {
        List<Item<String>> start = caseOne(false, false);
        assertLayout(DashboardSolver.solve(start, "A", p(1, 0, 2, 1), false, FOUR),
                "A", p(1, 0, 2, 1), "B", p(2, 1, 1, 1), "C", p(2, 2, 1, 1));
        assertNull(DashboardSolver.solve(start, "A", p(1, 0, 2, 1), true, FOUR));
    }

    /**
     * Verifies that widening A's right edge by one cell pushes B and C down, and is rejected when blocking.
     */
    @Test
    public void widenRightPushesNeighboursDown() {
        List<Item<String>> start = caseOne(false, false);
        assertLayout(DashboardSolver.solve(start, "A", p(0, 0, 3, 1), false, FOUR),
                "A", p(0, 0, 3, 1), "B", p(2, 1, 1, 1), "C", p(2, 2, 1, 1));
        assertNull(DashboardSolver.solve(start, "A", p(0, 0, 3, 1), true, FOUR));
    }

    /**
     * Verifies the example's main step: the trend chart grows to three columns by two rows in one push.
     */
    @Test
    public void growTrendChartToThreeByTwo() {
        assertLayout(DashboardSolver.solve(caseOne(false, false), "A", p(0, 0, 3, 2), false, FOUR),
                "A", p(0, 0, 3, 2), "B", p(2, 2, 1, 1), "C", p(2, 3, 1, 1));
    }

    /**
     * Verifies that pulling the left and then the top edge is just another candidate: the right and bottom edges
     * stay and the cards in the way move down.
     */
    @Test
    public void westAndNorthEdgesAreOrdinaryCandidates() {
        List<Item<String>> start = List.of(item("A", 1, 1, 2, 1), item("B", 0, 1, 1, 1), item("C", 1, 0, 1, 1));
        Map<String, Placement> west = DashboardSolver.solve(start, "A", p(0, 1, 3, 1), false, FOUR);
        assertLayout(west, "A", p(0, 1, 3, 1), "B", p(0, 2, 1, 1), "C", p(1, 0, 1, 1));
        Map<String, Placement> north = DashboardSolver.solve(items(west), "A", p(0, 0, 3, 2), false, FOUR);
        assertLayout(north, "A", p(0, 0, 3, 2), "B", p(0, 2, 1, 1), "C", p(1, 2, 1, 1));
    }

    /**
     * Verifies that a preview is solved from the start layout: dragging away and back gives the start layout.
     */
    @Test
    public void dragAwayAndBackRestoresTheStart() {
        List<Item<String>> start = caseOne(false, false);
        assertNotNull(DashboardSolver.solve(start, "A", p(2, 0, 2, 1), false, FOUR));
        assertEquals(placements(start), DashboardSolver.solve(start, "A", p(0, 0, 2, 1), false, FOUR));
    }

    /**
     * Verifies that pushes chain: each pushed card moves down past everything settled before it, and a card
     * nothing lands on stays even when a card that was above it now ends up below it.
     */
    @Test
    public void pushesChainDown() {
        List<Item<String>> start = List.of(item("A", 0, 0, 1, 1), item("B", 0, 1, 1, 1), item("C", 0, 2, 2, 1),
                item("D", 1, 3, 1, 1), item("E", 1, 4, 1, 1));
        assertLayout(DashboardSolver.solve(start, "A", p(0, 0, 1, 3), false, FOUR),
                "A", p(0, 0, 1, 3), "B", p(0, 3, 1, 1), "C", p(0, 4, 2, 1), "D", p(1, 3, 1, 1),
                "E", p(1, 5, 1, 1));
    }

    /**
     * Verifies that candidates leaving the grid, or with an empty span, are rejected.
     */
    @Test
    public void candidatesOutsideTheGridAreRejected() {
        List<Item<String>> start = caseOne(false, false);
        assertNull(DashboardSolver.solve(start, "A", p(-1, 0, 2, 1), false, FOUR));
        assertNull(DashboardSolver.solve(start, "A", p(3, 0, 2, 1), false, FOUR));
        assertNull(DashboardSolver.solve(start, "A", p(0, -1, 2, 1), false, FOUR));
        assertNull(DashboardSolver.solve(start, "A", p(0, 0, 0, 1), false, FOUR));
        assertNull(DashboardSolver.solve(start, "A", p(0, 0, 1, 0), false, FOUR));
    }

    /**
     * Verifies that blocking accepts a candidate on free cells.
     */
    @Test
    public void blockAcceptsFreeCells() {
        assertLayout(DashboardSolver.solve(caseOne(false, false), "A", p(0, 1, 2, 1), true, FOUR),
                "A", p(0, 1, 2, 1), "B", p(2, 0, 1, 1), "C", p(2, 1, 1, 1));
    }

    // ==================== Case 2: locking ====================

    /**
     * Verifies that a candidate on a locked card is rejected for dragging and for widening alike.
     */
    @Test
    public void lockedCardRejectsDragAndResizeOntoIt() {
        List<Item<String>> start = caseOne(true, false);
        assertNull(DashboardSolver.solve(start, "A", p(1, 0, 2, 1), false, FOUR));
        assertNull(DashboardSolver.solve(start, "A", p(0, 0, 3, 1), false, FOUR));
    }

    /**
     * Verifies that a move clear of the locked card pushes only the unlocked neighbour.
     */
    @Test
    public void moveBesideLockedCardPushesOthers() {
        assertLayout(DashboardSolver.solve(caseOne(true, false), "A", p(1, 1, 2, 1), false, FOUR),
                "A", p(1, 1, 2, 1), "B", p(2, 0, 1, 1), "C", p(2, 2, 1, 1));
    }

    /**
     * Verifies that a pushed card passes a locked card below it instead of landing on it.
     */
    @Test
    public void pushedCardPassesLockedCard() {
        assertLayout(DashboardSolver.solve(caseOne(false, true), "A", p(0, 0, 3, 1), false, FOUR),
                "A", p(0, 0, 3, 1), "B", p(2, 2, 1, 1), "C", p(2, 1, 1, 1));
    }

    /**
     * Verifies that code may move a locked card: it pushes unlocked cards, and fails on another locked card.
     */
    @Test
    public void lockedCardItselfCanBeMovedByCode() {
        List<Item<String>> start = List.of(new Item<>("A", p(0, 0, 2, 1), true, 1, Integer.MAX_VALUE, 1,
                Integer.MAX_VALUE), item("B", 2, 0, 1, 1), item("C", 2, 1, 1, 1));
        assertLayout(DashboardSolver.solve(start, "A", p(1, 0, 2, 1), false, FOUR),
                "A", p(1, 0, 2, 1), "B", p(2, 1, 1, 1), "C", p(2, 2, 1, 1));
        List<Item<String>> twoLocked = List.of(start.get(0), locked("B", 2, 0, 1, 1), start.get(2));
        assertNull(DashboardSolver.solve(twoLocked, "A", p(1, 0, 2, 1), false, FOUR));
    }

    // ==================== Compacting ====================

    /**
     * Verifies that compacting after growing and shrinking the trend chart returns the start layout, and that
     * compacting twice changes nothing more.
     */
    @Test
    public void compactClosesHolesAndIsIdempotent() {
        Map<String, Placement> grown = DashboardSolver.solve(caseOne(false, false), "A", p(0, 0, 3, 2), false, FOUR);
        Map<String, Placement> shrunk = DashboardSolver.solve(items(grown), "A", p(0, 0, 2, 1), false, FOUR);
        assertLayout(shrunk, "A", p(0, 0, 2, 1), "B", p(2, 2, 1, 1), "C", p(2, 3, 1, 1));
        Map<String, Placement> compacted = DashboardSolver.compact(items(shrunk));
        assertEquals(placements(caseOne(false, false)), compacted);
        assertEquals(compacted, DashboardSolver.compact(items(compacted)));
    }

    /**
     * Verifies that compacting leaves locked cards where they are and never lifts a card past one.
     */
    @Test
    public void compactKeepsLockedCards() {
        List<Item<String>> items = List.of(locked("L", 0, 2, 2, 1), item("A", 0, 5, 1, 1), item("B", 2, 6, 1, 2),
                item("C", 3, 1, 1, 1));
        assertLayout(DashboardSolver.compact(items),
                "L", p(0, 2, 2, 1), "A", p(0, 3, 1, 1), "B", p(2, 0, 1, 2), "C", p(3, 0, 1, 1));
    }

    // ==================== Case 3: columns and restoring ====================

    /**
     * Verifies the 12 to 4 conversion and back, edits made at 4 columns, and a layout that does not divide.
     */
    @Test
    public void rescaleKeepsSharedEdges() {
        Placement a = p(0, 0, 6, 1);
        Placement b = p(6, 0, 3, 1);
        Placement c = p(6, 1, 3, 1);
        assertEquals(p(0, 0, 2, 1), DashboardSolver.rescale(a, TWELVE, FOUR));
        assertEquals(p(2, 0, 1, 1), DashboardSolver.rescale(b, TWELVE, FOUR));
        assertEquals(p(2, 1, 1, 1), DashboardSolver.rescale(c, TWELVE, FOUR));
        assertEquals(a, DashboardSolver.rescale(p(0, 0, 2, 1), FOUR, TWELVE));
        assertEquals(b, DashboardSolver.rescale(p(2, 0, 1, 1), FOUR, TWELVE));
        assertEquals(p(9, 0, 3, 1), DashboardSolver.rescale(p(3, 0, 1, 1), FOUR, TWELVE));
        assertEquals(p(0, 0, 2, 1), DashboardSolver.rescale(p(0, 0, 5, 1), TWELVE, FOUR));
        assertEquals(p(2, 0, 2, 1), DashboardSolver.rescale(p(5, 0, 7, 1), TWELVE, FOUR));
        assertEquals(p(0, 0, 6, 1), DashboardSolver.rescale(p(0, 0, 2, 1), FOUR, TWELVE));
        assertEquals(p(6, 0, 6, 1), DashboardSolver.rescale(p(2, 0, 2, 1), FOUR, TWELVE));
    }

    /**
     * Verifies that every 4-column placement survives 4 to 12 and back.
     */
    @Test
    public void rescaleRoundTripsThroughMoreColumns() {
        for (int from = 1; from <= TWELVE; from++) {
            for (int column = 0; column < from; column++) {
                for (int span = 1; column + span <= from; span++) {
                    Placement placement = p(column, 3, span, 2);
                    Placement back = DashboardSolver.rescale(DashboardSolver.rescale(placement, from, TWELVE),
                            TWELVE, from);
                    assertEquals(placement, back, from + " columns: " + placement);
                }
            }
        }
    }

    /**
     * Verifies the corrections of a snapshot that overlaps and leaves the grid.
     */
    @Test
    public void arrangeRepairsOverlapsAndBounds() {
        List<Item<String>> items = List.of(item("A", 0, 0, 1, 1), item("B", 0, 1, 1, 1), item("C", 0, 2, 1, 1));
        Map<String, Placement> target = map("A", p(0, 0, 6, 2), "B", p(4, 1, 3, 1), "C", p(10, 0, 5, 1));
        assertLayout(DashboardSolver.arrange(items, target, TWELVE, TWELVE, TWELVE),
                "A", p(0, 0, 6, 2), "B", p(4, 2, 3, 1), "C", p(7, 0, 5, 1));
    }

    /**
     * Verifies that negative positions become 0, spans become at least 1 and spans wider than the grid shrink.
     */
    @Test
    public void arrangeClampsIntoTheGrid() {
        List<Item<String>> items = List.of(item("A", 0, 0, 1, 1), item("B", 0, 1, 1, 1));
        Map<String, Placement> target = map("A", p(-3, -2, 0, -1), "B", p(2, 5, 40, 1));
        assertLayout(DashboardSolver.arrange(items, target, FOUR, FOUR, FOUR),
                "A", p(0, 0, 1, 1), "B", p(0, 5, 4, 1));
    }

    /**
     * Verifies that a locked card claims its area first and the unlocked card on it moves down.
     */
    @Test
    public void arrangePlacesLockedCardsFirst() {
        List<Item<String>> items = List.of(item("X", 0, 0, 1, 1), locked("L", 0, 3, 1, 1), locked("M", 3, 0, 1, 1));
        Map<String, Placement> target = map("X", p(0, 0, 2, 2), "L", p(1, 1, 1, 1), "M", p(1, 1, 2, 1));
        // L claims (1,1); M overlaps L and is settled like any card: it moves below L; X moves below L too.
        assertLayout(DashboardSolver.arrange(items, target, FOUR, FOUR, FOUR),
                "X", p(0, 2, 2, 2), "L", p(1, 1, 1, 1), "M", p(1, 4, 2, 1));
    }

    /**
     * Verifies that a card the target lacks keeps its own place when free, and otherwise takes the first free area.
     */
    @Test
    public void arrangeTriesOwnPlaceForCardsTheTargetLacks() {
        Map<String, Placement> target = map("A", p(0, 0, 6, 1), "B", p(6, 0, 3, 1), "C", p(6, 1, 3, 1));
        List<Item<String>> free = List.of(item("A", 0, 0, 1, 1), item("B", 0, 1, 1, 1), item("C", 0, 2, 1, 1),
                item("D", 0, 1, 3, 1));
        assertEquals(p(0, 1, 3, 1), DashboardSolver.arrange(free, target, TWELVE, TWELVE, TWELVE).get("D"));
        List<Item<String>> taken = List.of(item("A", 0, 0, 1, 1), item("B", 0, 1, 1, 1), item("C", 0, 2, 1, 1),
                item("D", 0, 0, 3, 1));
        assertEquals(p(9, 0, 3, 1), DashboardSolver.arrange(taken, target, TWELVE, TWELVE, TWELVE).get("D"));
    }

    /**
     * Verifies that target placements and the own places of the other cards are converted to the new columns.
     */
    @Test
    public void arrangeConvertsColumnCounts() {
        List<Item<String>> items = List.of(item("A", 0, 0, 1, 1), item("B", 0, 1, 1, 1), item("D", 3, 2, 1, 1));
        Map<String, Placement> target = map("A", p(0, 0, 6, 1), "B", p(6, 0, 3, 1));
        assertLayout(DashboardSolver.arrange(items, target, TWELVE, FOUR, TWELVE),
                "A", p(0, 0, 6, 1), "B", p(6, 0, 3, 1), "D", p(9, 2, 3, 1));
        assertLayout(DashboardSolver.arrange(items, target, TWELVE, FOUR, FOUR),
                "A", p(0, 0, 2, 1), "B", p(2, 0, 1, 1), "D", p(3, 2, 1, 1));
    }

    /**
     * Verifies that span limits apply when arranging: the minimum wins over a smaller maximum, the grid wins over
     * both.
     */
    @Test
    public void clampAppliesSpanLimits() {
        Item<String> limited = new Item<>("A", p(0, 0, 1, 1), false, 3, 2, 2, 5);
        assertEquals(p(0, 0, 3, 2), DashboardSolver.clamp(limited, p(0, 0, 1, 1), TWELVE));
        assertEquals(p(0, 0, 3, 5), DashboardSolver.clamp(limited, p(0, 0, 9, 9), TWELVE));
        assertEquals(p(0, 0, 2, 2), DashboardSolver.clamp(limited, p(5, 0, 1, 1), 2));
        assertEquals(p(0, DashboardSolver.MAX_ROW, 1, DashboardSolver.MAX_ROW_SPAN),
                DashboardSolver.clamp(item("B", 0, 0, 1, 1), p(0, Integer.MAX_VALUE, 1, Integer.MAX_VALUE), FOUR));
    }

    // ==================== Adding ====================

    /**
     * Verifies the three outcomes of adding at a requested area under both policies: accepted, pushing, or moved
     * to the first free area.
     */
    @Test
    public void addHonoursTheRequestedArea() {
        List<Item<String>> existing = List.of(item("A", 0, 0, 2, 1), locked("L", 2, 0, 1, 1));
        Item<String> added = item("N", 0, 0, 1, 1);
        // Free cells: accepted under both policies.
        assertEquals(p(3, 0, 1, 1), DashboardSolver.add(existing, added, p(3, 0, 1, 1), false, FOUR).get("N"));
        assertEquals(p(3, 0, 1, 1), DashboardSolver.add(existing, added, p(3, 0, 1, 1), true, FOUR).get("N"));
        // On an unlocked card: push moves it down, block sends the new card to the first free area.
        assertLayout(DashboardSolver.add(existing, added, p(0, 0, 1, 1), false, FOUR),
                "A", p(0, 1, 2, 1), "L", p(2, 0, 1, 1), "N", p(0, 0, 1, 1));
        assertLayout(DashboardSolver.add(existing, added, p(0, 0, 1, 1), true, FOUR),
                "A", p(0, 0, 2, 1), "L", p(2, 0, 1, 1), "N", p(3, 0, 1, 1));
        // On a locked card: the first free area under both policies.
        assertEquals(p(3, 0, 1, 1), DashboardSolver.add(existing, added, p(2, 0, 1, 1), false, FOUR).get("N"));
        // Out of bounds: clamped back in first.
        assertEquals(p(3, 0, 1, 1), DashboardSolver.add(existing, added, p(9, -4, 1, 1), false, FOUR).get("N"));
    }

    /**
     * Verifies first-fit placement against a brute-force scan on random layouts.
     */
    @Test
    public void firstFitMatchesBruteForce() {
        Random random = new Random(20261123L);
        for (int round = 0; round < 300; round++) {
            int columns = 1 + random.nextInt(8);
            List<Placement> placed = new ArrayList<>();
            for (int i = 0; i < random.nextInt(8); i++) {
                Placement candidate = p(random.nextInt(columns), random.nextInt(5), 1, 1 + random.nextInt(2));
                candidate = candidate.withSpan(1 + random.nextInt(columns - candidate.column()), candidate.rowSpan());
                if (!overlapsAny(candidate, placed)) {
                    placed.add(candidate);
                }
            }
            int width = 1 + random.nextInt(columns);
            int height = 1 + random.nextInt(3);
            assertEquals(bruteFirstFit(placed, width, height, columns),
                    DashboardSolver.firstFit(placed, width, height, columns));
        }
    }

    // ==================== Randomized ====================

    /**
     * Verifies that no sequence of pushes, blocks, compacts, adds, removes and arrangements ever leaves two cards
     * overlapping or outside the grid, that a push never moves a card up or sideways, and that locked cards only
     * move when they are the card being edited.
     */
    @Test
    public void neverOverlapsWhateverTheSequence() {
        Random random = new Random(7L);
        for (int run = 0; run < 40; run++) {
            int columns = 1 + random.nextInt(TWELVE);
            List<Item<String>> items = new ArrayList<>();
            int nextId = 0;
            for (int step = 0; step < 250; step++) {
                int operation = random.nextInt(10);
                if (items.isEmpty() || operation == 0) {
                    Item<String> added = new Item<>("c" + nextId++, randomPlacement(random, columns), random.nextInt(5) == 0,
                            1, Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
                    Map<String, Placement> result = random.nextBoolean()
                            ? DashboardSolver.add(items, added, added.placement(), random.nextBoolean(), columns)
                            : DashboardSolver.addAtFirstFit(items, added, columns);
                    items.add(added);
                    items = apply(items, result);
                } else if (operation == 1) {
                    items.remove(random.nextInt(items.size()));
                } else if (operation == 2) {
                    items = apply(items, DashboardSolver.compact(items));
                } else if (operation == 3) {
                    int newColumns = 1 + random.nextInt(TWELVE);
                    Map<String, Placement> target = new LinkedHashMap<>();
                    for (Item<String> item : items) {
                        if (random.nextInt(4) != 0) {
                            target.put(item.key(), randomPlacement(random, columns + 3));
                        }
                    }
                    items = apply(items, DashboardSolver.arrange(items, target, columns + 3, columns, newColumns));
                    columns = newColumns;
                } else {
                    Item<String> edited = items.get(random.nextInt(items.size()));
                    boolean block = random.nextBoolean();
                    Map<String, Placement> result = DashboardSolver.solve(items, edited.key(),
                            randomPlacement(random, columns), block, columns);
                    if (result != null) {
                        assertPushRules(items, edited.key(), result);
                        items = apply(items, result);
                    }
                }
                assertValid(items, columns);
            }
        }
    }

    // ==================== Helpers ====================

    private static List<Item<String>> caseOne(boolean lockB, boolean lockC) {
        return List.of(item("A", 0, 0, 2, 1),
                lockB ? locked("B", 2, 0, 1, 1) : item("B", 2, 0, 1, 1),
                lockC ? locked("C", 2, 1, 1, 1) : item("C", 2, 1, 1, 1));
    }

    private static Item<String> item(String key, int column, int row, int columnSpan, int rowSpan) {
        return Item.of(key, p(column, row, columnSpan, rowSpan), false);
    }

    private static Item<String> locked(String key, int column, int row, int columnSpan, int rowSpan) {
        return Item.of(key, p(column, row, columnSpan, rowSpan), true);
    }

    private static Placement p(int column, int row, int columnSpan, int rowSpan) {
        return new Placement(column, row, columnSpan, rowSpan);
    }

    private static Map<String, Placement> map(Object... keysAndPlacements) {
        Map<String, Placement> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndPlacements.length; i += 2) {
            map.put((String) keysAndPlacements[i], (Placement) keysAndPlacements[i + 1]);
        }
        return map;
    }

    private static void assertLayout(Map<String, Placement> actual, Object... expected) {
        assertNotNull(actual, "rejected");
        assertEquals(map(expected), actual);
    }

    private static Map<String, Placement> placements(List<Item<String>> items) {
        Map<String, Placement> map = new LinkedHashMap<>();
        for (Item<String> item : items) {
            map.put(item.key(), item.placement());
        }
        return map;
    }

    private static List<Item<String>> items(Map<String, Placement> layout) {
        List<Item<String>> items = new ArrayList<>();
        layout.forEach((key, placement) -> items.add(Item.of(key, placement, false)));
        return items;
    }

    private static List<Item<String>> apply(List<Item<String>> items, Map<String, Placement> result) {
        List<Item<String>> updated = new ArrayList<>();
        for (Item<String> item : items) {
            updated.add(item.at(result.get(item.key())));
        }
        assertEquals(items.size(), result.size());
        return updated;
    }

    private static Placement randomPlacement(Random random, int columns) {
        int columnSpan = 1 + random.nextInt(Math.max(1, Math.min(columns, 4)));
        int column = random.nextInt(Math.max(1, columns - columnSpan + 1));
        return p(column, random.nextInt(6), columnSpan, 1 + random.nextInt(3));
    }

    private static void assertPushRules(List<Item<String>> start, String edited, Map<String, Placement> result) {
        for (Item<String> item : start) {
            if (item.key().equals(edited)) {
                continue;
            }
            Placement before = item.placement();
            Placement after = result.get(item.key());
            if (item.locked()) {
                assertEquals(before, after, "locked card moved");
            }
            assertEquals(before.column(), after.column(), "moved sideways");
            assertEquals(before.columnSpan(), after.columnSpan());
            assertEquals(before.rowSpan(), after.rowSpan());
            assertTrue(after.row() >= before.row(), "moved up");
        }
    }

    private static void assertValid(List<Item<String>> items, int columns) {
        for (int i = 0; i < items.size(); i++) {
            Placement a = items.get(i).placement();
            assertTrue(DashboardSolver.fits(a, columns), a + " outside " + columns + " columns");
            for (int j = i + 1; j < items.size(); j++) {
                assertFalse(DashboardSolver.overlaps(a, items.get(j).placement()),
                        items.get(i).key() + " overlaps " + items.get(j).key());
            }
        }
    }

    private static boolean overlapsAny(Placement candidate, List<Placement> placed) {
        for (Placement other : placed) {
            if (DashboardSolver.overlaps(candidate, other)) {
                return true;
            }
        }
        return false;
    }

    private static Placement bruteFirstFit(List<Placement> placed, int width, int height, int columns) {
        for (int row = 0; ; row++) {
            for (int column = 0; column + width <= columns; column++) {
                Placement candidate = p(column, row, width, height);
                if (!overlapsAny(candidate, placed)) {
                    return candidate;
                }
            }
        }
    }
}
