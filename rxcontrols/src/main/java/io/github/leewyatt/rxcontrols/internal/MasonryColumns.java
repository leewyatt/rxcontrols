package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.layout.RXBreakpoint;
import io.github.leewyatt.rxcontrols.layout.RXBreakpointProfile;

import java.util.Map;
import java.util.Objects;

/**
 * Stateless column-count and track-width resolver for masonry / waterfall layouts,
 * the single implementation shared by the virtualized view and the node-based pane.
 *
 * <p>The resolver takes two widths so a virtualized view can keep its responsive
 * decisions stable while a scrollbar appears or disappears:</p>
 * <ul>
 *   <li>{@code breakpointWidth} drives the active breakpoint and the mobile-first
 *       breakpoint column cascade. It is the pre-scrollbar content width, so it does
 *       not flip when a vertical scrollbar steals breadth.</li>
 *   <li>{@code layoutWidth} drives the {@code columnWidth} auto floor and the track
 *       width. It is the width actually available to lay tracks into during the
 *       current pass.</li>
 * </ul>
 *
 * <p>A node-based caller with no scrollbar passes the same width for both.</p>
 *
 * <p>Pure arithmetic with no node coupling: {@code columnWidth} and {@code hgap} are
 * expected to already be snapped and sanitized by the caller (a positive, finite
 * column width and a finite gap). To stay equivalent to the pane, snap them with the
 * matching node helper — {@code columnWidth} as a size ({@code snapSizeX}, which
 * rounds up) and {@code hgap} as a space ({@code snapSpaceX}, which rounds to
 * nearest) — since those round differently on fractional pixels.</p>
 */
public final class MasonryColumns {

    /**
     * RXBreakpoint override sentinel mirroring the public {@code AUTO_COLUMNS} control
     * constant: a cascade that resolves to this value breaks out of the override and
     * falls back to the {@code columnWidth} auto floor.
     */
    private static final int AUTO_COLUMNS = 0;

    /**
     * Defensive hard cap so a pathological tiny {@code columnWidth} or huge forced
     * count never asks the engine to allocate an unbounded column array.
     */
    private static final int MAX_RESOLVED_COLUMNS = 4096;

    /**
     * Floor on the effective per-column step of the auto count, as a fraction of
     * {@code columnWidth}. A negative {@code hgap} overlaps columns, so each extra
     * column costs {@code columnWidth + hgap} of width; near total overlap that
     * step approaches zero and the auto count would explode into thousands of
     * stacked columns. Clamping the step keeps the count bounded by the column
     * width and continuous as {@code hgap} crosses {@code -columnWidth}. The floor
     * bounds the count only — track placement keeps the caller's raw {@code hgap},
     * so the requested overlap still renders as such: with {@code fillWidth} the
     * stretched track keeps the placement step consistent with the count, while
     * fixed-width tracks pack tighter than the resolved count would fill.
     */
    private static final double MIN_COLUMN_STEP_RATIO = 0.1;

    /**
     * Resolved column geometry for a layout pass.
     *
     * @param columns         the number of columns, at least one
     * @param trackWidth      the width of a single column track
     * @param usedWidth       the total width the resolved tracks plus gaps occupy
     * @param activeBreakpoint the breakpoint resolved from {@code breakpointWidth}
     */
    public record Resolution(int columns, double trackWidth, double usedWidth,
                             RXBreakpoint activeBreakpoint) {
    }

    private MasonryColumns() {
    }

    /**
     * Resolves the column count and track geometry for a layout pass.
     *
     * @param breakpointWidth  the stable, pre-scrollbar content width driving the
     *                         active breakpoint and the breakpoint column cascade;
     *                         a non-finite value resolves as {@code 0}
     * @param layoutWidth      the width available this pass, driving the
     *                         {@code columnWidth} auto floor and the track width;
     *                         a non-finite value resolves as {@code 0}
     * @param columnCount      a forced column count, or a non-positive value to
     *                         resolve the count automatically
     * @param columnWidth      the target column width (snapped, positive, finite)
     * @param hgap             the horizontal gap between columns (snapped, finite)
     * @param maxColumns       an upper bound on the resolved count, or a non-positive
     *                         value for no bound
     * @param fillWidth        whether tracks stretch to fill {@code layoutWidth} or
     *                         stay at {@code columnWidth}
     * @param profile          the breakpoint profile; must not be {@code null}
     * @param breakpointColumns the per-breakpoint column overrides, keyed by tier;
     *                          {@code null} or empty means no overrides
     * @return the resolved column geometry
     * @throws NullPointerException if {@code profile} is {@code null}
     */
    public static Resolution resolve(double breakpointWidth, double layoutWidth,
                                     int columnCount, double columnWidth, double hgap,
                                     int maxColumns, boolean fillWidth,
                                     RXBreakpointProfile profile,
                                     Map<RXBreakpoint, Integer> breakpointColumns) {
        Objects.requireNonNull(profile, "profile cannot be null");
        // NaN or infinite widths (typical before the first sized layout pass) resolve
        // as zero instead of poisoning the track geometry with NaN.
        if (!Double.isFinite(breakpointWidth)) {
            breakpointWidth = 0.0;
        }
        if (!Double.isFinite(layoutWidth)) {
            layoutWidth = 0.0;
        }
        RXBreakpoint activeBreakpoint = profile.resolve(breakpointWidth);
        int columns = computeColumns(layoutWidth, columnCount, columnWidth, hgap, maxColumns,
                profile, breakpointColumns, activeBreakpoint);
        double trackWidth;
        if (fillWidth) {
            trackWidth = Math.max(0.0, (layoutWidth - (columns - 1) * hgap) / columns);
        } else {
            trackWidth = columnWidth;
        }
        double usedWidth = columns * trackWidth + (columns - 1) * hgap;
        return new Resolution(columns, trackWidth, usedWidth, activeBreakpoint);
    }

    private static int computeColumns(double layoutWidth, int columnCount, double columnWidth,
                                      double hgap, int maxColumns, RXBreakpointProfile profile,
                                      Map<RXBreakpoint, Integer> breakpointColumns,
                                      RXBreakpoint activeBreakpoint) {
        int columns;
        if (columnCount >= 1) {
            columns = columnCount;
        } else {
            Integer breakpointColumnCount =
                    resolveBreakpointColumns(profile, breakpointColumns, activeBreakpoint);
            if (breakpointColumnCount != null) {
                columns = breakpointColumnCount;
            } else {
                double step = Math.max(columnWidth + hgap, MIN_COLUMN_STEP_RATIO * columnWidth);
                columns = (int) Math.floor((layoutWidth + hgap) / step);
            }
        }
        if (columns < 1) {
            columns = 1;
        }
        if (maxColumns > 0 && columns > maxColumns) {
            columns = maxColumns;
        }
        if (columns > MAX_RESOLVED_COLUMNS) {
            columns = MAX_RESOLVED_COLUMNS;
        }
        return columns;
    }

    private static Integer resolveBreakpointColumns(RXBreakpointProfile profile,
                                                    Map<RXBreakpoint, Integer> breakpointColumns,
                                                    RXBreakpoint activeBreakpoint) {
        if (breakpointColumns == null || breakpointColumns.isEmpty()) {
            return null;
        }
        double activeMinWidth = profile.minWidthOf(activeBreakpoint);
        Integer resolved = null;
        for (RXBreakpoint breakpoint : profile.getBreakpoints()) {
            if (profile.minWidthOf(breakpoint) > activeMinWidth) {
                break;
            }
            Integer columns = breakpointColumns.get(breakpoint);
            if (columns != null) {
                resolved = columns;
            }
        }
        // An explicit AUTO_COLUMNS override breaks the cascade; returning null lets
        // the caller fall back to the columnWidth auto floor.
        if (resolved != null && resolved == AUTO_COLUMNS) {
            return null;
        }
        return resolved;
    }
}
