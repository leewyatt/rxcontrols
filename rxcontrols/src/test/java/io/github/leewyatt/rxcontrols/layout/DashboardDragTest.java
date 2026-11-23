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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the step-by-step drag with cards packed upward against recorded reference drags: random packed boards, sparse
 * and dense, some with locked cards, and pointer paths, with the cards expected to move after every pointer event. The
 * reference data comes from running an established web grid layout library's engine on the same boards and paths.
 */
public class DashboardDragTest {

    private static final String FIXTURES = "dashboard-drag-fixtures.txt";

    @Test
    public void everyRecordedDragGivesTheRecordedLayoutAfterEveryEvent() throws IOException {
        List<Case> cases = readCases();
        assertTrue(cases.size() > 400, "fixtures read: " + cases.size());
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
    public void aCardPassesALockedCardButNeverLandsOnIt() {
        DashboardDrag<String> drag = new DashboardDrag<>(List.of(item("C", 3, 0, 1, 1),
                new Item<>("L", new Placement(1, 3, 1, 1), true, 1, Integer.MAX_VALUE, 1, Integer.MAX_VALUE)),
                "C", 6, 100, 100, 10, 10);
        for (int i = 1; i <= 40; i++) {
            drag.step(300 - 5 * i, 10 * i);
        }
        assertEquals(Map.of("C", "1,4", "L", "1,3"), cells(drag.layout()));
        assertFalse(drag.step(100, 400), "the same target again changes nothing");
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
                    current = new Case(head[0], Integer.parseInt(numbers[0]), Integer.parseInt(numbers[1]),
                            Integer.parseInt(numbers[2]), Integer.parseInt(numbers[3]), numbers[4]);
                } else if (line.startsWith("cards ")) {
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
                        current.segments.add(new int[] {Integer.parseInt(values[0]), Integer.parseInt(values[1]),
                                Integer.parseInt(values[2])});
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
        final int cellWidth;
        final int cellHeight;
        final int margin;
        final String mover;
        final List<Item<String>> cards = new ArrayList<>();
        final List<int[]> segments = new ArrayList<>();
        final TreeMap<Integer, String> expected = new TreeMap<>();

        Case(String name, int columns, int cellWidth, int cellHeight, int margin, String mover) {
            this.name = name;
            this.columns = columns;
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            this.margin = margin;
            this.mover = mover;
        }

        // Replays the pointer events; returns how many there were. The reference caps each margin at a tenth of the
        // cell, rounded, before using it.
        int replay(List<String> mismatches) {
            double marginX = Math.min(margin, Math.round(cellWidth * 0.1));
            double marginY = Math.min(margin, Math.round(cellHeight * 0.1));
            DashboardDrag<String> drag = new DashboardDrag<>(cards, mover, columns, cellWidth, cellHeight, marginX,
                    marginY);
            Placement start = null;
            for (Item<String> card : cards) {
                if (card.key().equals(mover)) {
                    start = card.placement();
                }
            }
            Map<String, String> want = new TreeMap<>(cells(drag.layout()));
            int index = 0;
            long fromX = 0;
            long fromY = 0;
            for (int[] segment : segments) {
                for (int i = 1; i <= segment[2]; i++) {
                    long x = fromX + Math.round((segment[0] - fromX) * i / (double) segment[2]);
                    long y = fromY + Math.round((segment[1] - fromY) * i / (double) segment[2]);
                    drag.step(start.column() * (double) cellWidth + x, start.row() * (double) cellHeight + y);
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

    // Each card's column and row, as "x,y".
    private static Map<String, String> cells(Map<String, Placement> layout) {
        Map<String, String> cells = new TreeMap<>();
        layout.forEach((key, p) -> cells.put(key, p.column() + "," + p.row()));
        return cells;
    }
}
