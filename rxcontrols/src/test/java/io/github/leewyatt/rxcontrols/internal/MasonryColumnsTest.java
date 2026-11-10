package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.RXMasonryView;
import io.github.leewyatt.rxcontrols.internal.MasonryColumns.Resolution;
import io.github.leewyatt.rxcontrols.layout.RXBreakpoint;
import io.github.leewyatt.rxcontrols.layout.RXBreakpointProfile;
import io.github.leewyatt.rxcontrols.layout.RXMasonryPane;

import javafx.application.Platform;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MasonryColumns}, the single column resolver behind both
 * {@link RXMasonryPane} and the masonry view. Column-count and active-breakpoint
 * semantics are additionally locked against a live pane as a regression on the
 * pane's delegation wiring. Track-width geometry and the two-width contract are
 * asserted directly.
 */
public class MasonryColumnsTest {

    private static final double DELTA = 1.0e-9;

    /**
     * Starts the JavaFX toolkit so a live {@link RXMasonryPane} can be laid out.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    // ==================== Column-count parity with RXMasonryPane ====================

    /**
     * Verifies the width-based auto floor matches the pane, with and without a gap.
     */
    @Test
    public void autoFloorMatchesPane() {
        assertColumnParity(800.0, 100.0, 0.0, 0, 0, true, Map.of());
        assertColumnParity(830.0, 200.0, 10.0, 0, 0, true, Map.of());
        assertColumnParity(1000.0, 260.0, 8.0, 0, 0, true, Map.of());
    }

    /**
     * Verifies a forced column count and a maxColumns clamp match the pane.
     */
    @Test
    public void forcedAndClampedCountsMatchPane() {
        assertColumnParity(1000.0, 100.0, 0.0, 2, 0, true, Map.of());
        assertColumnParity(800.0, 100.0, 0.0, 0, 3, true, Map.of());
    }

    /**
     * Verifies breakpoint overrides, mobile-first cascade and the AUTO_COLUMNS
     * sentinel all match the pane under the default ANT_DESIGN profile.
     */
    @Test
    public void breakpointSemanticsMatchPane() {
        assertColumnParity(800.0, 100.0, 0.0, 0, 0, true, Map.of(RXBreakpoint.MD, 3));
        assertColumnParity(600.0, 100.0, 0.0, 0, 0, true, Map.of(RXBreakpoint.MD, 3));
        assertColumnParity(1500.0, 100.0, 0.0, 0, 0, true, Map.of(RXBreakpoint.MD, 3));
        assertColumnParity(1500.0, 100.0, 0.0, 0, 0, true, Map.of(RXBreakpoint.MD, 3, RXBreakpoint.XL, RXMasonryPane.AUTO_COLUMNS));
        assertColumnParity(1700.0, 100.0, 0.0, 0, 0, true,
                Map.of(RXBreakpoint.MD, 3, RXBreakpoint.XL, RXMasonryPane.AUTO_COLUMNS, RXBreakpoint.XXL, 5));
        // Active band (md@800) is AUTO; positive overrides at strictly wider bands
        // (lg/xl) must not leak in, so the count falls back to the auto floor (8).
        assertColumnParity(800.0, 100.0, 0.0, 0, 0, true,
                Map.of(RXBreakpoint.MD, RXMasonryPane.AUTO_COLUMNS, RXBreakpoint.LG, 4, RXBreakpoint.XL, 5));
        // Overrides combined with a positive gap: the cascade and the gapped auto
        // floor must not diverge once a gap is in play.
        assertColumnParity(830.0, 200.0, 10.0, 0, 0, true, Map.of(RXBreakpoint.MD, 3));
        assertColumnParity(1500.0, 200.0, 10.0, 0, 0, true,
                Map.of(RXBreakpoint.MD, 3, RXBreakpoint.XL, RXMasonryPane.AUTO_COLUMNS));
    }

    /**
     * Verifies the pane and the view expose the same {@code AUTO_COLUMNS} sentinel,
     * so the shared resolver's cascade-break behavior applies identically to both.
     */
    @Test
    public void autoColumnsSentinelIsSharedAcrossControls() {
        assertEquals(RXMasonryPane.AUTO_COLUMNS, RXMasonryView.AUTO_COLUMNS);
        // The sentinel value breaks the override cascade back to the auto floor.
        assertEquals(8, MasonryColumns.resolve(800.0, 800.0, 0, 100.0, 0.0, 0, true,
                RXBreakpointProfile.ANT_DESIGN,
                Map.of(RXBreakpoint.MD, RXMasonryPane.AUTO_COLUMNS), 0.0).columns());
    }

    // ==================== Robustness ====================

    /**
     * Verifies a {@code null} override map is tolerated as "no overrides" and the
     * defensive column cap and degenerate widths never crash or produce garbage.
     */
    @Test
    public void toleratesNullMapAndCapsPathologicalCounts() {
        // null map -> no overrides -> auto floor(800 / 100) = 8, no NPE.
        assertEquals(8, MasonryColumns.resolve(800.0, 800.0, 0, 100.0, 0.0, 0, true,
                RXBreakpointProfile.ANT_DESIGN, null, 0.0).columns());
        // A pathological tiny columnWidth is capped at MAX_RESOLVED_COLUMNS (4096).
        assertEquals(4096, MasonryColumns.resolve(1.0e6, 1.0e6, 0, 0.001, 0.0, 0, true,
                RXBreakpointProfile.ANT_DESIGN, Map.of(), 0.0).columns());
        // A huge forced count is capped too.
        assertEquals(4096, MasonryColumns.resolve(800.0, 800.0, 100000, 100.0, 0.0, 0, true,
                RXBreakpointProfile.ANT_DESIGN, Map.of(), 0.0).columns());
    }

    /**
     * Verifies near-total overlap keeps the auto count bounded and continuous: the
     * effective per-column step is floored at a fraction of {@code columnWidth}, so
     * {@code hgap} approaching {@code -columnWidth} can neither explode the count
     * into thousands of stacked columns nor cliff-jump across the boundary.
     */
    @Test
    public void nearTotalOverlapKeepsAutoCountBounded() {
        int atMinus99 = overlappedAutoColumns(-99.0);
        int atMinus100 = overlappedAutoColumns(-100.0);
        int atMinus101 = overlappedAutoColumns(-101.0);

        // Bounded by columnWidth: at most the non-overlap count divided by the
        // minimum step ratio (800 / 100 / 0.1 = 80).
        assertTrue(atMinus99 <= 80, "bounded near total overlap: " + atMinus99);
        // Monotone and continuous across hgap = -columnWidth (no 4096 -> 1 cliff).
        assertTrue(atMinus99 >= atMinus100 && atMinus100 >= atMinus101,
                atMinus99 + " >= " + atMinus100 + " >= " + atMinus101);
        assertTrue(atMinus100 - atMinus101 <= 1, "no cliff at the boundary");
        // The pane delegates to this resolver, so both stay in the same regime.
        assertColumnParity(800.0, 100.0, -99.0, 0, 0, true, Map.of());

        // The step floor bounds the count only: with fillWidth the stretched track
        // keeps the placement step consistent with the count, while fixed-width
        // tracks keep the caller's raw hgap and render the requested overlap.
        Resolution stretched = MasonryColumns.resolve(800.0, 800.0, 0, 100.0, -99.0, 0, true,
                RXBreakpointProfile.ANT_DESIGN, Map.of(), 0.0);
        assertTrue(stretched.trackWidth() - 99.0 > 0.0, "stretched step stays positive");
        assertEquals(800.0, stretched.usedWidth(), DELTA);
        Resolution fixed = MasonryColumns.resolve(800.0, 800.0, 0, 100.0, -99.0, 0, false,
                RXBreakpointProfile.ANT_DESIGN, Map.of(), 0.0);
        assertEquals(100.0, fixed.trackWidth(), DELTA, "fixed track keeps columnWidth");
        assertEquals(fixed.columns() * 100.0 - (fixed.columns() - 1) * 99.0, fixed.usedWidth(), DELTA);
    }

    /**
     * Verifies non-finite widths resolve as zero (single column, zero track)
     * instead of poisoning the track geometry with NaN.
     */
    @Test
    public void nonFiniteWidthsResolveAsZero() {
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            Resolution r = MasonryColumns.resolve(bad, bad, 0, 100.0, 10.0, 0, true,
                    RXBreakpointProfile.ANT_DESIGN, Map.of(), 0.0);
            assertEquals(1, r.columns(), "columns @ " + bad);
            assertEquals(0.0, r.trackWidth(), DELTA, "trackWidth @ " + bad);
            assertEquals(0.0, r.usedWidth(), DELTA, "usedWidth @ " + bad);
        }
    }

    /**
     * Verifies the single-column fillWidth case (where the inter-track gap term
     * vanishes) and a negative gap both keep the used-width sum invariant.
     */
    @Test
    public void fillWidthTrackSumInvariantHoldsAtEdges() {
        // Single column: the (columns-1)*hgap term is zero, so the track is the full
        // layout width regardless of the gap.
        Resolution single = MasonryColumns.resolve(500.0, 500.0, 1, 100.0, 20.0, 0, true,
                RXBreakpointProfile.ANT_DESIGN, Map.of(), 0.0);
        assertEquals(1, single.columns());
        assertEquals(500.0, single.trackWidth(), DELTA);
        assertEquals(500.0, single.usedWidth(), DELTA);

        // A negative gap widens tracks; the used width still sums back to the layout
        // width (tracks overlap by the gap).
        Resolution overlap = MasonryColumns.resolve(300.0, 300.0, 3, 100.0, -10.0, 0, true,
                RXBreakpointProfile.ANT_DESIGN, Map.of(), 0.0);
        assertEquals(3, overlap.columns());
        assertEquals(320.0 / 3.0, overlap.trackWidth(), DELTA);
        assertEquals(300.0, overlap.usedWidth(), DELTA);
    }

    // ==================== Snapped column edges ====================

    @Test
    public void filledEdgesStayOnTheGridAndInsideTheWidth() {
        for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
            for (double rawGap : new double[] {10.0, 0.0, -10.0}) {
                // Callers pass a snapped gap (snapSpaceX); 10 px is off the 1.25 and 1.75 grids.
                double hgap = Math.round(rawGap * scale) / scale;
                // Steps of 1/12 px land between the 1, 1.5 and 2 grids (thirds and halves).
                for (double width = 300.0; width <= 500.0; width += 1.0 / 12.0) {
                    Resolution r = MasonryColumns.resolve(width, width, 0, 60.0, hgap, 0, true,
                            RXBreakpointProfile.ANT_DESIGN, Map.of(), scale);
                    String at = "scale " + scale + " hgap " + hgap + " width " + width;
                    int n = r.columns();
                    assertOnGrid(r.trackWidth(), scale, at + ": track");
                    assertEquals(0.0, r.columnX(0.0, 0), DELTA, at + ": first column at the leading edge");
                    for (int k = 0; k < n; k++) {
                        assertOnGrid(r.columnX(0.0, k), scale, at + ": column " + k + " x");
                        if (k > 0) {
                            double gap = r.columnX(0.0, k) - (r.columnX(0.0, k - 1) + r.trackWidth());
                            assertTrue(gap >= hgap - DELTA, at + ": gap " + gap + " before column " + k);
                        }
                    }
                    double right = r.columnX(0.0, n - 1) + r.trackWidth();
                    assertTrue(right <= width + DELTA, at + ": last column ends at " + right);
                    assertTrue(right >= width - 1.0 / scale - DELTA, at + ": the columns fill, ending at " + right);
                    for (int start = 0; start < n; start++) {
                        for (int span = 1; start + span <= n; span++) {
                            double spanRight = r.columnX(0.0, start) + r.spanWidth(0.0, start, span);
                            assertEquals(r.columnX(0.0, start + span - 1) + r.trackWidth(), spanRight, DELTA,
                                    at + ": span " + span + " from " + start + " ends with its last column");
                        }
                    }
                }
            }
        }
    }

    @Test
    public void filledSpanWidthsSurviveACeilingSnap() {
        // layoutInArea ceils the area width; a span width carrying floating residue
        // above its grid value would come out one device pixel wider. The 1.25x and
        // 1.75x grids are left out: there the JavaFX ceiling also moves some exact
        // grid values.
        for (double scale : new double[] {1.0, 1.5, 2.0}) {
            for (double width = 300.0; width <= 500.0; width += 1.0 / 12.0) {
                Resolution r = MasonryColumns.resolve(width, width, 0, 60.0, 10.0, 0, true,
                        RXBreakpointProfile.ANT_DESIGN, Map.of(), scale);
                int n = r.columns();
                for (int start = 0; start < n; start++) {
                    for (int span = 1; start + span <= n; span++) {
                        double spanWidth = r.spanWidth(0.0, start, span);
                        double ceiled = Math.ceil(spanWidth * scale - 1.0e-14) / scale;
                        assertEquals(spanWidth, ceiled, DELTA, "scale " + scale + " width " + width
                                + ": span " + span + " from " + start);
                    }
                }
            }
        }
    }

    @Test
    public void unsnappedFillKeepsTheContinuousGeometry() {
        Resolution r = MasonryColumns.resolve(301.0, 301.0, 3, 60.0, 10.0, 0, true,
                RXBreakpointProfile.ANT_DESIGN, Map.of(), 0.0);
        assertEquals(281.0 / 3.0, r.trackWidth(), DELTA);
        assertEquals(10.0, r.hgap(), DELTA);
        assertEquals(2.0 * (281.0 / 3.0 + 10.0), r.columnX(0.0, 2), DELTA);
        assertEquals(2.0 * 281.0 / 3.0 + 10.0, r.spanWidth(0.0, 0, 2), DELTA);
    }

    @Test
    public void fixedWidthEdgesSnapLikeANode() {
        // fillWidth=false keeps the node snapping it had: positions round, widths ceil.
        for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
            Resolution r = MasonryColumns.resolve(500.0, 500.0, 0, 60.0, 10.3, 0, false,
                    RXBreakpointProfile.ANT_DESIGN, Map.of(), scale);
            assertEquals(60.0, r.trackWidth(), DELTA, "fixed track keeps columnWidth @ " + scale);
            assertEquals(10.3, r.hgap(), DELTA, "fixed gap keeps hgap @ " + scale);
            double startX = 7.3;
            for (int k = 0; k < r.columns(); k++) {
                assertEquals(Math.round((startX + k * 70.3) * scale) / scale, r.columnX(startX, k), DELTA,
                        "column " + k + " x rounds @ " + scale);
            }
            assertEquals(Math.ceil((2 * 60.0 + 10.3) * scale - 1e-14) / scale, r.spanWidth(startX, 1, 2), DELTA,
                    "span width ceils @ " + scale);
        }
    }

    @Test
    public void filledTracksLoseLessThanOneDevicePixel() {
        double scale = 1.5;
        for (int pixels = 300; pixels <= 1500; pixels++) {
            // A pane width on the grid minus two padding insets snapped to 2/3.
            double layoutWidth = pixels / scale - 2.0 * (1.0 / scale);
            Resolution resolution = MasonryColumns.resolve(layoutWidth, layoutWidth, 3, 100.0, 12.0, 0,
                    true, RXBreakpointProfile.ANT_DESIGN, Map.of(), scale);
            double ideal = (layoutWidth - 24.0) / 3.0;
            assertTrue(resolution.trackWidth() > ideal - 1.0 / scale + 1.0e-6, "layout width " + layoutWidth
                    + ": track " + resolution.trackWidth() + " is a whole device pixel under " + ideal);
        }
    }

    private static void assertOnGrid(double value, double scale, String message) {
        double scaled = value * scale;
        assertEquals(Math.rint(scaled), scaled, 1.0e-6, message + " " + value + " is on the " + scale + "x grid");
    }

    // ==================== Track / used width ====================

    /**
     * Verifies fillWidth stretches tracks to consume the layout width, and the
     * resolved geometry sums back to the used width.
     */
    @Test
    public void fillWidthStretchesTracks() {
        Resolution r = MasonryColumns.resolve(630.0, 630.0, 3, 100.0, 15.0, 0, true,
                RXBreakpointProfile.ANT_DESIGN, Map.of(), 0.0);

        assertEquals(3, r.columns());
        // (630 - 2*15) / 3 = 200
        assertEquals(200.0, r.trackWidth(), DELTA);
        assertEquals(630.0, r.usedWidth(), DELTA);
    }

    /**
     * Verifies a non-fill resolution keeps tracks at columnWidth and leaves slack.
     */
    @Test
    public void nonFillKeepsColumnWidth() {
        Resolution r = MasonryColumns.resolve(800.0, 800.0, 3, 100.0, 10.0, 0, false,
                RXBreakpointProfile.ANT_DESIGN, Map.of(), 0.0);

        assertEquals(3, r.columns());
        assertEquals(100.0, r.trackWidth(), DELTA);
        // 3 * 100 + 2 * 10 = 320
        assertEquals(320.0, r.usedWidth(), DELTA);
    }

    // ==================== Two-width contract ====================

    /**
     * Verifies the active breakpoint and the breakpoint column cascade follow
     * {@code breakpointWidth}, while the track width follows {@code layoutWidth}.
     */
    @Test
    public void overrideUsesBreakpointWidthTrackUsesLayoutWidth() {
        // breakpointWidth 800 resolves to "md"; the md override forces 3 columns
        // independent of layoutWidth. fillWidth tracks divide layoutWidth (600), not
        // breakpointWidth (800).
        Resolution r = MasonryColumns.resolve(800.0, 600.0, 0, 100.0, 0.0, 0, true,
                RXBreakpointProfile.ANT_DESIGN, Map.of(RXBreakpoint.MD, 3), 0.0);

        assertEquals(3, r.columns());
        assertEquals(RXBreakpoint.MD, r.activeBreakpoint());
        assertEquals(200.0, r.trackWidth(), DELTA);
    }

    /**
     * Verifies the auto floor uses {@code layoutWidth} while the active breakpoint
     * still reflects {@code breakpointWidth}, proving the two are not conflated.
     */
    @Test
    public void autoFloorUsesLayoutWidthNotBreakpointWidth() {
        // breakpointWidth 1100 resolves to "lg" but has no override; the auto floor
        // counts columns from layoutWidth 500 -> floor(500 / 100) = 5, not from 1100.
        Resolution r = MasonryColumns.resolve(1100.0, 500.0, 0, 100.0, 0.0, 0, true,
                RXBreakpointProfile.ANT_DESIGN, Map.of(), 0.0);

        assertEquals(5, r.columns());
        assertEquals(RXBreakpoint.LG, r.activeBreakpoint());
    }

    // ==================== Helpers ====================

    private static int overlappedAutoColumns(double hgap) {
        return MasonryColumns.resolve(800.0, 800.0, 0, 100.0, hgap, 0, true,
                RXBreakpointProfile.ANT_DESIGN, Map.of(), 0.0).columns();
    }

    private void assertColumnParity(double width, double columnWidth, double hgap,
                                    int columnCount, int maxColumns, boolean fillWidth,
                                    Map<RXBreakpoint, Integer> overrides) {
        RXMasonryPane pane = new RXMasonryPane();
        pane.setColumnWidth(columnWidth);
        pane.setHgap(hgap);
        pane.setColumnCount(columnCount);
        pane.setMaxColumns(maxColumns);
        pane.setFillWidth(fillWidth);
        overrides.forEach(pane::setBreakpointColumns);
        layout(pane, width, 1000.0);

        Resolution resolution = MasonryColumns.resolve(width, width, columnCount, columnWidth, hgap,
                maxColumns, fillWidth, RXBreakpointProfile.ANT_DESIGN, overrides, 0.0);

        String label = "width=" + width + " columnWidth=" + columnWidth + " hgap=" + hgap
                + " columnCount=" + columnCount + " maxColumns=" + maxColumns + " overrides=" + overrides;
        assertEquals(pane.getActualColumnCount(), resolution.columns(), "columns @ " + label);
        assertEquals(pane.getActiveBreakpoint(), resolution.activeBreakpoint(),
                "activeBreakpoint @ " + label);
    }

    private static void layout(Region region, double width, double height) {
        region.resize(width, height);
        region.layout();
    }
}
