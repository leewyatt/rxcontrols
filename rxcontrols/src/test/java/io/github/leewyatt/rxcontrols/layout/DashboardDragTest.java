package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.layout.DashboardSolver.Item;
import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout.Placement;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the step-by-step drag with cards packed upward against recorded reference drags: random packed boards, sparse
 * and dense, some with locked cards, on four grid geometries (one with a margin above a tenth of the cell, one with
 * fractional pixels), and pointer paths, with the cards expected to move after every pointer event. The reference
 * data comes from running an established web grid layout library's engine on the same boards and paths, leaving out
 * the drags where it lets cards overlap. Also: that no drag ever leaves cards overlapping or outside the grid.
 */
public class DashboardDragTest {

    private static final String FIXTURES = "dashboard-drag-fixtures.txt";

    @Test
    public void everyRecordedDragGivesTheRecordedLayoutAfterEveryEvent() throws IOException {
        List<Case> cases = readCases();
        Set<String> boards = new HashSet<>();
        for (Case c : cases) {
            boards.add(c.columns + " " + c.cardsLine);
        }
        assertTrue(boards.size() > 900, "distinct boards: " + boards.size() + " of " + cases.size());
        List<String> mismatches = new ArrayList<>();
        int events = 0;
        for (Case c : cases) {
            events += c.replay(mismatches);
        }
        assertEquals(List.of(), mismatches.subList(0, Math.min(5, mismatches.size())),
                mismatches.size() + " of " + cases.size() + " cases differ");
        assertTrue(events > 10_000, "events replayed: " + events);
    }

    @Test
    public void aCardDraggedOntoItsRowNeighbourTakesItsPlace() {
        DashboardDrag<String> drag = new DashboardDrag<>(List.of(item("1", 0, 0, 1, 1), item("2", 1, 0, 1, 1),
                item("3", 2, 0, 1, 1), item("4", 3, 0, 1, 1)), "1", 4, 100, 100, 10, 10);
        for (int x = 5; x <= 200; x += 5) {
            drag.step(x, 0);
        }
        assertEquals(Map.of("1", "2,0", "2", "0,0", "3", "1,0", "4", "3,0"), cells(drag.layout()));
    }

    @Test
    public void aCardDraggedUpAgainstALockedCardNeverLandsOnIt() {
        DashboardDrag<String> drag = new DashboardDrag<>(List.of(item("D", 1, 0, 1, 1), locked("B", 2, 0, 1, 2),
                item("C", 3, 0, 3, 1), item("A", 0, 2, 4, 1)), "A", 6, 120, 80, 5, 5);
        Map<String, String> before = cells(drag.layout());
        for (int i = 1; i <= 16; i++) {
            drag.step(0, 160 - 8 * i);
        }
        assertEquals(before, cells(drag.layout()));
    }

    @Test
    public void noDragLeavesCardsOverlappingOrOutsideTheGrid() {
        Random random = new Random(20261124);
        int steps = 0;
        for (int board = 0; board < 3000; board++) {
            int columns = 1 + random.nextInt(8);
            List<Item<String>> items = randomPackedBoard(random, columns);
            List<Item<String>> movable = new ArrayList<>();
            for (Item<String> item : items) {
                if (!item.locked()) {
                    movable.add(item);
                }
            }
            if (movable.isEmpty()) {
                continue;
            }
            Item<String> moved = movable.get(random.nextInt(movable.size()));
            DashboardDrag<String> drag = new DashboardDrag<>(items, moved.key(), columns, 100, 80, 5, 5);
            double x = moved.placement().column() * 100.0;
            double y = moved.placement().row() * 80.0;
            for (int i = 0; i < 60; i++) {
                x += random.nextInt(41) - 20;
                y += random.nextInt(41) - 20;
                drag.step(x, y);
                steps++;
                assertSound(drag.layout(), columns, "board " + board + " step " + i);
            }
        }
        assertTrue(steps > 100_000, "steps: " + steps);
    }

    // A random layout packed upward, a quarter of its cards locked where they were put.
    private static List<Item<String>> randomPackedBoard(Random random, int columns) {
        List<Item<String>> placed = new ArrayList<>();
        int count = 3 + random.nextInt(12);
        for (int i = 0; i < count; i++) {
            int width = 1 + random.nextInt(Math.min(3, columns));
            Placement at = new Placement(random.nextInt(columns - width + 1), random.nextInt(8), width,
                    1 + random.nextInt(3));
            boolean free = true;
            for (Item<String> other : placed) {
                free &= !DashboardSolver.overlaps(other.placement(), at);
            }
            if (free) {
                placed.add(new Item<>("c" + i, at, random.nextInt(4) == 0, 1, Integer.MAX_VALUE, 1,
                        Integer.MAX_VALUE));
            }
        }
        Map<String, Placement> packed = DashboardSolver.compact(placed);
        List<Item<String>> items = new ArrayList<>();
        for (Item<String> item : placed) {
            items.add(new Item<>(item.key(), packed.get(item.key()), item.locked(), 1, Integer.MAX_VALUE, 1,
                    Integer.MAX_VALUE));
        }
        return items;
    }

    private static void assertSound(Map<String, Placement> layout, int columns, String where) {
        List<Placement> placements = new ArrayList<>(layout.values());
        for (int i = 0; i < placements.size(); i++) {
            Placement p = placements.get(i);
            assertTrue(p.column() >= 0 && p.row() >= 0 && p.column() + p.columnSpan() <= columns,
                    where + ": outside the grid " + p);
            for (int j = i + 1; j < placements.size(); j++) {
                assertFalse(DashboardSolver.overlaps(p, placements.get(j)), where + ": " + p + " overlaps "
                        + placements.get(j));
            }
        }
    }

    // ==================== Fixtures ====================

    private static List<Case> readCases() throws IOException {
        List<Case> cases = new ArrayList<>();
        try (InputStream in = Objects.requireNonNull(DashboardDragTest.class.getResourceAsStream(FIXTURES));
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            Case current = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("case ")) {
                    String[] head = line.substring(5).split(" \\| ");
                    String[] numbers = head[1].split(" ");
                    current = new Case(head[0], Integer.parseInt(numbers[0]), Double.parseDouble(numbers[1]),
                            Double.parseDouble(numbers[2]), Double.parseDouble(numbers[3]), numbers[4]);
                } else if (line.startsWith("cards ")) {
                    current.cardsLine = line;
                    for (String card : line.substring(6).split(" ")) {
                        String[] parts = card.split(":");
                        String[] values = parts[1].split(",");
                        current.cards.add(new Item<>(parts[0], new Placement(Integer.parseInt(values[0]),
                                Integer.parseInt(values[1]), Integer.parseInt(values[2]),
                                Integer.parseInt(values[3])), values.length > 4, 1, Integer.MAX_VALUE, 1,
                                Integer.MAX_VALUE));
                    }
                } else if (line.startsWith("path ")) {
                    for (String segment : line.substring(5).split(" ")) {
                        String[] values = segment.split(",");
                        current.segments.add(new double[] {Double.parseDouble(values[0]),
                                Double.parseDouble(values[1]), Integer.parseInt(values[2])});
                    }
                } else if (line.startsWith("at ")) {
                    int space = line.indexOf(' ', 3);
                    current.expected.put(Integer.parseInt(line.substring(3, space)), line.substring(space + 1));
                } else if (line.equals("end")) {
                    cases.add(current);
                    current = null;
                }
            }
        }
        return cases;
    }

    private static final class Case {
        final String name;
        final int columns;
        final double cellWidth;
        final double cellHeight;
        final double margin;
        final String mover;
        final List<Item<String>> cards = new ArrayList<>();
        String cardsLine;
        final List<double[]> segments = new ArrayList<>();
        final TreeMap<Integer, String> expected = new TreeMap<>();

        Case(String name, int columns, double cellWidth, double cellHeight, double margin, String mover) {
            this.name = name;
            this.columns = columns;
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            this.margin = margin;
            this.mover = mover;
        }

        // Replays the pointer events; returns how many there were. The margin goes in as recorded: the drag caps it.
        int replay(List<String> mismatches) {
            DashboardDrag<String> drag = new DashboardDrag<>(cards, mover, columns, cellWidth, cellHeight, margin,
                    margin);
            Placement start = null;
            for (Item<String> card : cards) {
                if (card.key().equals(mover)) {
                    start = card.placement();
                }
            }
            Map<String, String> want = new TreeMap<>(cells(drag.layout()));
            int index = 0;
            double fromX = 0;
            double fromY = 0;
            for (double[] segment : segments) {
                int count = (int) segment[2];
                for (int i = 1; i <= count; i++) {
                    // The same arithmetic, in the same order, as the recording: each axis rounded per step.
                    double x = fromX + Math.round((segment[0] - fromX) * i / count);
                    double y = fromY + Math.round((segment[1] - fromY) * i / count);
                    drag.step(start.column() * cellWidth + x, start.row() * cellHeight + y);
                    String moved = expected.get(index);
                    if (moved != null) {
                        for (String card : moved.split(" ")) {
                            int colon = card.indexOf(':');
                            want.put(card.substring(0, colon), card.substring(colon + 1));
                        }
                    }
                    Map<String, String> got = cells(drag.layout());
                    if (!got.equals(want)) {
                        mismatches.add(name + " @" + index + ": want " + want + " got " + got);
                        return index + 1;
                    }
                    index++;
                }
                fromX = segment[0];
                fromY = segment[1];
            }
            return index;
        }
    }

    // ==================== Helpers ====================

    private static Item<String> item(String key, int column, int row, int columnSpan, int rowSpan) {
        return new Item<>(key, new Placement(column, row, columnSpan, rowSpan), false, 1, Integer.MAX_VALUE, 1,
                Integer.MAX_VALUE);
    }

    private static Item<String> locked(String key, int column, int row, int columnSpan, int rowSpan) {
        return new Item<>(key, new Placement(column, row, columnSpan, rowSpan), true, 1, Integer.MAX_VALUE, 1,
                Integer.MAX_VALUE);
    }

    // Each card's column and row, as "x,y".
    private static Map<String, String> cells(Map<String, Placement> layout) {
        Map<String, String> cells = new TreeMap<>();
        layout.forEach((key, p) -> cells.put(key, p.column() + "," + p.row()));
        return cells;
    }
}
