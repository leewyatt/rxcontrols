package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXListSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXListRowPlan}: the row / Y / item / data-row lookups of the flat
 * and grouped layouts in fixed and variable height mode, with expanded, partly
 * collapsed and fully collapsed sections. Pure arithmetic, no JavaFX toolkit.
 */
public class RXListRowPlanTest {

    private static final double EPS = 1e-9;
    private static final double HEADER = 30.0;
    private static final double ROW = 20.0;

    // Three sections over nine items: [0..2], [3..4], [5..8].
    private static final List<RXListSection> SECTIONS = List.of(
            new RXListSection("a", 0, 0, 3),
            new RXListSection("b", 1, 3, 2),
            new RXListSection("c", 2, 5, 4));

    private static RXListRowPlan fixed(boolean showHeaders, double spacing, boolean... collapsed) {
        return RXListRowPlan.fixed(SECTIONS, showHeaders, HEADER, ROW, 9, spacing, collapsed);
    }

    private static RXListRowPlan variable(boolean showHeaders, boolean... collapsed) {
        return variable(showHeaders, 0.0, collapsed);
    }

    // Item i is 10 * (i + 1) tall, so every row has a distinct height.
    private static RXListRowPlan variable(boolean showHeaders, double spacing, boolean... collapsed) {
        double[] heights = new double[9];
        for (int i = 0; i < heights.length; i++) {
            heights[i] = 10.0 * (i + 1);
        }
        return RXListRowPlan.variable(SECTIONS, showHeaders, HEADER, heights, spacing, collapsed);
    }

    // ==================== Flat / empty ====================

    @Test
    public void flatPlanNumbersRowsByItem() {
        RXListRowPlan plan = RXListRowPlan.fixed(List.of(), true, HEADER, ROW, 5, 0.0, null);
        assertEquals(5, plan.totalVisualRows());
        assertEquals(5, plan.dataRowCount());
        assertEquals(100.0, plan.contentHeight(), EPS);
        for (int i = 0; i < 5; i++) {
            assertEquals(i, plan.visualRowOfItem(i));
            assertEquals(i, plan.dataRowOfItem(i));
            assertEquals(i, plan.itemOfDataRow(i));
            assertTrue(plan.isItemShown(i));
        }
        assertEquals(2, plan.firstVisualRowAt(45.0));
        assertEquals(-1, plan.sectionIndexOfItem(2));
        assertEquals(-1, plan.sectionIndexAt(10.0));
        assertFalse(plan.headersShown());
    }

    @Test
    public void emptyPlanHasNoRows() {
        RXListRowPlan plan = RXListRowPlan.fixed(List.of(), true, HEADER, ROW, 0, 0.0, null);
        assertEquals(0, plan.totalVisualRows());
        assertEquals(0, plan.dataRowCount());
        assertEquals(0.0, plan.contentHeight(), EPS);
        assertEquals(-1, plan.firstVisualRowAt(0.0));
        assertEquals(-1, plan.dataRowOfItem(0));
        assertEquals(-1, plan.itemOfDataRow(0));
        assertFalse(plan.isItemShown(0));
        assertEquals(-1.0, plan.itemTop(0), EPS);
    }

    // ==================== Fixed mode ====================

    @Test
    public void fixedAllExpandedMatchesNoFlags() {
        RXListRowPlan none = fixed(true, 0.0);
        RXListRowPlan allFalse = fixed(true, 0.0, false, false, false);
        for (RXListRowPlan plan : List.of(none, allFalse)) {
            assertEquals(12, plan.totalVisualRows());
            assertEquals(9, plan.dataRowCount());
            assertEquals(3 * HEADER + 9 * ROW, plan.contentHeight(), EPS);
            assertEquals(90.0, plan.sectionTop(1), EPS);
            assertEquals(160.0, plan.sectionTop(2), EPS);
            assertEquals(5, plan.visualRowOfItem(3));
            assertEquals(3, plan.dataRowOfItem(3));
            assertEquals(5, plan.itemOfDataRow(5));
            assertFalse(plan.sectionCollapsed(1));
        }
    }

    @Test
    public void fixedPartialCollapseDropsTheSectionItems() {
        RXListRowPlan plan = fixed(true, 0.0, false, true, false);
        // Rows: s0 header 0, items 1..3; s1 header 4; s2 header 5, items 6..9.
        assertEquals(10, plan.totalVisualRows());
        assertEquals(7, plan.dataRowCount());
        assertEquals(3 * HEADER + 7 * ROW, plan.contentHeight(), EPS);
        assertTrue(plan.sectionCollapsed(1));
        assertEquals(120.0, plan.sectionTop(2), EPS, "s2 moves up by the collapsed items");

        assertFalse(plan.isItemShown(3));
        assertFalse(plan.isItemShown(4));
        assertEquals(-1, plan.visualRowOfItem(3));
        assertEquals(-1, plan.dataRowOfItem(4));
        assertEquals(-1.0, plan.itemTop(3), EPS);
        assertEquals(1, plan.sectionIndexOfItem(3), "a hidden item still knows its section");

        assertEquals(6, plan.visualRowOfItem(5));
        assertEquals(3, plan.dataRowOfItem(5));
        assertEquals(5, plan.itemOfDataRow(3));
        assertEquals(2, plan.itemOfDataRow(2));
        assertEquals(150.0, plan.itemTop(5), EPS);

        RXListRowPlan.RowInfo header = plan.rowInfo(4);
        assertTrue(header.header());
        assertEquals(1, header.section().sectionIndex());
        assertEquals(90.0, header.top(), EPS);
        assertEquals(4, plan.firstVisualRowAt(95.0));
        assertEquals(5, plan.firstVisualRowAt(120.0));
    }

    @Test
    public void spacingGapAfterACollapsedHeaderResolvesToThatHeader() {
        RXListRowPlan plan = fixed(true, 10.0, false, true, false);
        // s1 top = 30 + 60 + 10 = 100, header to 130, gap to s2 top = 140.
        assertEquals(100.0, plan.sectionTop(1), EPS);
        assertEquals(140.0, plan.sectionTop(2), EPS);
        assertEquals(4, plan.firstVisualRowAt(135.0));
        assertEquals(1, plan.sectionIndexAt(135.0));
        assertEquals(2, plan.sectionIndexAt(140.0));
        assertEquals(0, plan.sectionIndexAt(95.0), "the gap before a section belongs to the previous one");
    }

    @Test
    public void collapseIsIgnoredWhileHeadersAreHidden() {
        RXListRowPlan plan = fixed(false, 0.0, false, true, false);
        assertFalse(plan.headersShown());
        assertFalse(plan.sectionCollapsed(1));
        assertEquals(9, plan.totalVisualRows());
        assertEquals(9, plan.dataRowCount());
        assertEquals(9 * ROW, plan.contentHeight(), EPS);
        assertTrue(plan.isItemShown(3));
        assertEquals(3, plan.visualRowOfItem(3));
        assertEquals(3, plan.dataRowOfItem(3));
    }

    @Test
    public void allCollapsedLeavesOnlyHeaders() {
        RXListRowPlan plan = fixed(true, 0.0, true, true, true);
        assertEquals(3, plan.totalVisualRows());
        assertEquals(0, plan.dataRowCount());
        assertEquals(3 * HEADER, plan.contentHeight(), EPS);
        assertEquals(-1, plan.itemOfDataRow(0));
        for (int i = 0; i < 9; i++) {
            assertEquals(-1, plan.visualRowOfItem(i));
            assertFalse(plan.isItemShown(i));
        }
        assertEquals(1, plan.firstVisualRowAt(50.0));
        assertEquals(2, plan.firstVisualRowAt(1000.0), "past the end clamps to the last row");
        assertTrue(plan.rowInfo(2).header());
    }

    @Test
    public void leadingAndTrailingCollapsedSectionsKeepDataRowsContiguous() {
        RXListRowPlan leading = fixed(true, 0.0, true, true, false);
        assertEquals(4, leading.dataRowCount());
        assertEquals(5, leading.itemOfDataRow(0));
        assertEquals(8, leading.itemOfDataRow(3));
        assertEquals(-1, leading.itemOfDataRow(4));

        RXListRowPlan trailing = fixed(true, 0.0, false, false, true);
        assertEquals(5, trailing.dataRowCount());
        assertEquals(4, trailing.itemOfDataRow(4));
        assertEquals(-1, trailing.itemOfDataRow(5));
    }

    @Test
    public void shortOrMissingFlagsMeanExpanded() {
        RXListRowPlan shortFlags = fixed(true, 0.0, true);
        assertTrue(shortFlags.sectionCollapsed(0));
        assertFalse(shortFlags.sectionCollapsed(1));
        assertFalse(shortFlags.sectionCollapsed(2));
        assertFalse(shortFlags.sectionCollapsed(7), "out of range is not collapsed");
        assertEquals(3 + 2 + 4, shortFlags.totalVisualRows(), "only section 0's three items are hidden");
    }

    @Test
    public void duplicateKeyRunsCollapseByTheirOwnFlags() {
        // Runs a, b, a — the skin passes the same flag for both "a" runs.
        List<RXListSection> sections = List.of(
                new RXListSection("a", 0, 0, 2),
                new RXListSection("b", 1, 2, 2),
                new RXListSection("a", 2, 4, 2));
        RXListRowPlan plan = RXListRowPlan.fixed(sections, true, HEADER, ROW, 6, 0.0,
                new boolean[] {true, false, true});
        assertEquals(5, plan.totalVisualRows());
        assertEquals(2, plan.dataRowCount());
        assertEquals(-1, plan.visualRowOfItem(0));
        assertEquals(-1, plan.visualRowOfItem(5));
        assertEquals(0, plan.dataRowOfItem(2));
        assertEquals(3, plan.itemOfDataRow(1));
        assertEquals(2, plan.visualRowOfItem(2));
    }

    // ==================== Variable mode ====================

    @Test
    public void variablePartialCollapseSkipsHiddenHeights() {
        RXListRowPlan plan = variable(true, false, true, false);
        // s0: header 0..30, items 30 / 40 / 60 -> 90; s1: header 90..120 (collapsed);
        // s2: header 120..150, items 150 / 210 / 280 / 360 -> 450.
        assertTrue(plan.variable());
        assertEquals(10, plan.totalVisualRows());
        assertEquals(450.0, plan.contentHeight(), EPS);
        assertEquals(-1.0, plan.itemTop(3), EPS);
        assertEquals(150.0, plan.itemTop(5), EPS);
        assertEquals(210.0, plan.itemTop(6), EPS);
        assertEquals(4, plan.firstVisualRowAt(100.0));
        assertEquals(7, plan.firstVisualRowAt(215.0));
        RXListRowPlan.RowInfo row = plan.rowInfo(7);
        assertEquals(6, row.itemIndex());
        assertEquals(210.0, row.top(), EPS);
        assertEquals(70.0, row.height(), EPS);
    }

    @Test
    public void variableAllCollapsedLeavesOnlyHeaders() {
        RXListRowPlan plan = variable(true, true, true, true);
        assertEquals(3, plan.totalVisualRows());
        assertEquals(3 * HEADER, plan.contentHeight(), EPS);
        assertEquals(2, plan.firstVisualRowAt(75.0));
        assertEquals(-1.0, plan.itemTop(8), EPS);
    }

    @Test
    public void variableCollapseIsIgnoredWhileHeadersAreHidden() {
        RXListRowPlan plan = variable(false, true, true, true);
        assertEquals(9, plan.totalVisualRows());
        assertEquals(450.0, plan.contentHeight(), EPS);
        assertEquals(100.0, plan.itemTop(4), EPS);
    }

    @Test
    public void variableGroupedHeightsAreSanitized() {
        List<RXListSection> sections = List.of(new RXListSection("a", 0, 0, 3));
        RXListRowPlan plan = RXListRowPlan.variable(sections, true, HEADER,
                new double[] {Double.NaN, -5.0, 10.0}, 0.0, null);
        assertEquals(HEADER + 10.0, plan.contentHeight(), EPS);
        assertEquals(HEADER, plan.itemTop(2), EPS);
    }

    @Test
    public void rowInfoRejectsRowsOutsideThePlan() {
        RXListRowPlan plan = fixed(true, 0.0, false, true, false);
        assertThrows(IndexOutOfBoundsException.class, () -> plan.rowInfo(plan.visualRowOfItem(3)));
        assertThrows(IndexOutOfBoundsException.class, () -> plan.rowInfo(plan.totalVisualRows()));
        RXListRowPlan varPlan = variable(true, false, true, false);
        assertThrows(IndexOutOfBoundsException.class, () -> varPlan.rowInfo(-1));
    }

    // ==================== Round trips ====================

    @Test
    public void lookupsRoundTripInBothModes() {
        List<RXListRowPlan> plans = List.of(
                fixed(true, 0.0),
                fixed(true, 10.0, false, true, false),
                fixed(true, 0.0, true, false, true),
                fixed(false, 5.0, true, true, true),
                variable(true),
                variable(true, false, true, false),
                variable(true, true, false, true),
                variable(true, 12.0, false, true, true),
                variable(false, 12.0, true, false, true));
        for (RXListRowPlan plan : plans) {
            int shown = 0;
            for (int i = 0; i < 9; i++) {
                if (!plan.isItemShown(i)) {
                    continue;
                }
                int dataRow = plan.dataRowOfItem(i);
                assertEquals(shown++, dataRow, "shown data rows are numbered contiguously");
                assertEquals(i, plan.itemOfDataRow(dataRow));
                int visualRow = plan.visualRowOfItem(i);
                assertEquals(i, plan.rowInfo(visualRow).itemIndex());
                assertEquals(visualRow, plan.firstVisualRowAt(plan.itemTop(i)));
            }
            assertEquals(shown, plan.dataRowCount());
            for (int r = 0; r < plan.totalVisualRows(); r++) {
                RXListRowPlan.RowInfo info = plan.rowInfo(r);
                assertEquals(r, plan.firstVisualRowAt(info.top()), "row " + r + " is found at its own top");
                if (info.header()) {
                    assertEquals(r, plan.visualRowOfSection(info.section().sectionIndex()));
                }
            }
        }
    }
}
