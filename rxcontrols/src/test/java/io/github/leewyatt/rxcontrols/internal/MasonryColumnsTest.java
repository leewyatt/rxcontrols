package io.github.leewyatt.rxcontrols.internal;

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

/**
 * Tests for {@link MasonryColumns}. The column-count and active-breakpoint semantics
 * are locked against a live {@link RXMasonryPane} (the authority), confirming the
 * shared resolver is column-semantics-equivalent to the pane's inline resolution.
 * Track-width geometry and the two-width contract are asserted directly.
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
                RXBreakpointProfile.ANT_DESIGN, null).columns());
        // A pathological tiny columnWidth is capped at MAX_RESOLVED_COLUMNS (4096).
        assertEquals(4096, MasonryColumns.resolve(1.0e6, 1.0e6, 0, 0.001, 0.0, 0, true,
                RXBreakpointProfile.ANT_DESIGN, Map.of()).columns());
        // A huge forced count is capped too.
        assertEquals(4096, MasonryColumns.resolve(800.0, 800.0, 100000, 100.0, 0.0, 0, true,
                RXBreakpointProfile.ANT_DESIGN, Map.of()).columns());
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
                RXBreakpointProfile.ANT_DESIGN, Map.of());
        assertEquals(1, single.columns());
        assertEquals(500.0, single.trackWidth(), DELTA);
        assertEquals(500.0, single.usedWidth(), DELTA);

        // A negative gap widens tracks; the used width still sums back to the layout
        // width (tracks overlap by the gap).
        Resolution overlap = MasonryColumns.resolve(300.0, 300.0, 3, 100.0, -10.0, 0, true,
                RXBreakpointProfile.ANT_DESIGN, Map.of());
        assertEquals(3, overlap.columns());
        assertEquals(320.0 / 3.0, overlap.trackWidth(), DELTA);
        assertEquals(300.0, overlap.usedWidth(), DELTA);
    }

    // ==================== Track / used width ====================

    /**
     * Verifies fillWidth stretches tracks to consume the layout width, and the
     * resolved geometry sums back to the used width.
     */
    @Test
    public void fillWidthStretchesTracks() {
        Resolution r = MasonryColumns.resolve(630.0, 630.0, 3, 100.0, 15.0, 0, true,
                RXBreakpointProfile.ANT_DESIGN, Map.of());

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
                RXBreakpointProfile.ANT_DESIGN, Map.of());

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
                RXBreakpointProfile.ANT_DESIGN, Map.of(RXBreakpoint.MD, 3));

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
                RXBreakpointProfile.ANT_DESIGN, Map.of());

        assertEquals(5, r.columns());
        assertEquals(RXBreakpoint.LG, r.activeBreakpoint());
    }

    // ==================== Helpers ====================

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
                maxColumns, fillWidth, RXBreakpointProfile.ANT_DESIGN, overrides);

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
