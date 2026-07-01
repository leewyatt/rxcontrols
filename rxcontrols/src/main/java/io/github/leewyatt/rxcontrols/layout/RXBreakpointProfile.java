package io.github.leewyatt.rxcontrols.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Defines the column count and pixel thresholds for the responsive
 * {@link RXBreakpoint} tiers used by a responsive row.
 *
 * <p>A profile maps the subset of tiers it uses to inclusive minimum widths and
 * pairs them with a column count. The {@link RXBreakpoint} tier is the fixed
 * identity; the pixel threshold is profile-relative, so different frameworks
 * reuse the same tier at different widths.</p>
 *
 * <p>Three built-in presets are provided. {@link #ANT_DESIGN} is the default
 * because it matches this library's grid API (24 columns, {@code XS..XXXL}).
 * {@link #ELEMENT} and {@link #BOOTSTRAP} mirror those frameworks. Any other
 * convention can be built with {@link #of(int, Map)} or {@link #builder()}.</p>
 *
 * <p>Built-in presets:</p>
 * <pre>
 *                cols  XS   SM    MD    LG     XL     XXL    XXXL
 *   ANT_DESIGN    24   0    576   768   992    1200   1600   1920
 *   ELEMENT       24   0    768   992   1200   1920   --     --
 *   BOOTSTRAP     12   0    576   768   992    1200   1400   --
 * </pre>
 *
 * <p>Other ecosystems map onto the same tiers; port the pixel thresholds
 * deliberately rather than assuming {@code MD} is one fixed value (build via
 * {@link #of(int, Map)}; values in CSS pixels):</p>
 * <pre>
 *                cols  thresholds (px)
 *   Tailwind     (12)  SM 640 / MD 768 / LG 1024 / XL 1280 / XXL 1536   (base = mobile)
 *   MUI v5        12   XS 0 / SM 600 / MD 900 / LG 1200 / XL 1536
 *   Foundation    12   XS 0 / SM 640 / MD 1024 / LG 1200 / XL 1440      (small..xxlarge)
 *   Bulma         12   XS 0 / SM 769 / MD 1024 / LG 1216 / XL 1408      (mobile..fullhd)
 *   Material 3    --   XS 0 / SM 600 / MD 840 / LG 1200 / XL 1600       (Compact..ExtraLarge)
 * </pre>
 */
public final class RXBreakpointProfile {

    /**
     * Ant Design-style profile and library default: 24 columns and
     * XS/SM/MD/LG/XL/XXL/XXXL thresholds, aligned with Ant Design 6.3+
     * ({@code XXXL} = 1920). This is a fixed preset, not a live mirror of the
     * framework. Shares the XS..XL thresholds with {@link #BOOTSTRAP}.
     */
    public static final RXBreakpointProfile ANT_DESIGN = builder()
            .columns(24)
            .breakpoint(RXBreakpoint.XS, 0.0)
            .breakpoint(RXBreakpoint.SM, 576.0)
            .breakpoint(RXBreakpoint.MD, 768.0)
            .breakpoint(RXBreakpoint.LG, 992.0)
            .breakpoint(RXBreakpoint.XL, 1200.0)
            .breakpoint(RXBreakpoint.XXL, 1600.0)
            .breakpoint(RXBreakpoint.XXXL, 1920.0)
            .build();

    /**
     * Element-style profile: 24 columns and XS/SM/MD/LG/XL thresholds.
     */
    public static final RXBreakpointProfile ELEMENT = builder()
            .columns(24)
            .breakpoint(RXBreakpoint.XS, 0.0)
            .breakpoint(RXBreakpoint.SM, 768.0)
            .breakpoint(RXBreakpoint.MD, 992.0)
            .breakpoint(RXBreakpoint.LG, 1200.0)
            .breakpoint(RXBreakpoint.XL, 1920.0)
            .build();

    /**
     * Bootstrap-style profile: 12 columns and XS/SM/MD/LG/XL/XXL thresholds.
     */
    public static final RXBreakpointProfile BOOTSTRAP = builder()
            .columns(12)
            .breakpoint(RXBreakpoint.XS, 0.0)
            .breakpoint(RXBreakpoint.SM, 576.0)
            .breakpoint(RXBreakpoint.MD, 768.0)
            .breakpoint(RXBreakpoint.LG, 992.0)
            .breakpoint(RXBreakpoint.XL, 1200.0)
            .breakpoint(RXBreakpoint.XXL, 1400.0)
            .build();

    private final int columns;
    private final EnumMap<RXBreakpoint, Double> minWidths;
    private final List<RXBreakpoint> breakpoints;

    private RXBreakpointProfile(int columns, EnumMap<RXBreakpoint, Double> minWidths) {
        this.columns = columns;
        this.minWidths = minWidths;
        List<RXBreakpoint> sorted = new ArrayList<>(minWidths.keySet());
        sorted.sort(Comparator.comparingDouble((RXBreakpoint breakpoint) -> minWidths.get(breakpoint))
                .thenComparingInt(RXBreakpoint::ordinal));
        this.breakpoints = List.copyOf(sorted);
    }

    /**
     * Creates a profile with the given column count and tier thresholds.
     *
     * @param columns   the default number of columns
     * @param minWidths the inclusive minimum width for each tier the profile uses
     * @return the profile
     * @throws NullPointerException     if {@code minWidths} or one of its keys or
     *                                  values is {@code null}
     * @throws IllegalArgumentException if {@code columns <= 0}, the map is empty,
     *                                  or a width is not finite and non-negative
     */
    public static RXBreakpointProfile of(int columns, Map<RXBreakpoint, Double> minWidths) {
        Objects.requireNonNull(minWidths, "minWidths cannot be null");
        if (columns <= 0) {
            throw new IllegalArgumentException("columns must be greater than zero");
        }
        if (minWidths.isEmpty()) {
            throw new IllegalArgumentException("minWidths cannot be empty");
        }

        EnumMap<RXBreakpoint, Double> copy = new EnumMap<>(RXBreakpoint.class);
        for (Map.Entry<RXBreakpoint, Double> entry : minWidths.entrySet()) {
            RXBreakpoint breakpoint = Objects.requireNonNull(entry.getKey(), "breakpoint cannot be null");
            Double minWidth = Objects.requireNonNull(entry.getValue(), "minWidth cannot be null");
            if (!Double.isFinite(minWidth) || minWidth < 0.0) {
                throw new IllegalArgumentException("minWidth must be finite and non-negative");
            }
            copy.put(breakpoint, minWidth);
        }
        return new RXBreakpointProfile(columns, copy);
    }

    /**
     * Creates a builder for a custom profile.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns this profile's default column count.
     *
     * @return the column count
     */
    public int getColumns() {
        return columns;
    }

    /**
     * Returns the tiers this profile uses, in ascending minimum-width order.
     *
     * @return the ordered tiers
     */
    public List<RXBreakpoint> getBreakpoints() {
        return breakpoints;
    }

    /**
     * Returns the inclusive minimum width this profile assigns to a tier.
     *
     * @param breakpoint the tier
     * @return the minimum width
     * @throws NullPointerException     if {@code breakpoint} is {@code null}
     * @throws IllegalArgumentException if this profile does not use the tier
     */
    public double minWidthOf(RXBreakpoint breakpoint) {
        Objects.requireNonNull(breakpoint, "breakpoint cannot be null");
        Double minWidth = minWidths.get(breakpoint);
        if (minWidth == null) {
            throw new IllegalArgumentException("profile does not use breakpoint: " + breakpoint);
        }
        return minWidth;
    }

    /**
     * Resolves the active tier for the given row width.
     *
     * @param width the row width
     * @return the widest tier whose minimum width is not greater than
     *         {@code width}
     */
    public RXBreakpoint resolve(double width) {
        double normalizedWidth = Double.isNaN(width) || width < 0.0 ? 0.0 : width;
        RXBreakpoint resolved = breakpoints.get(0);
        for (RXBreakpoint breakpoint : breakpoints) {
            if (minWidths.get(breakpoint) <= normalizedWidth) {
                resolved = breakpoint;
            } else {
                break;
            }
        }
        return resolved;
    }

    /**
     * Builder for {@link RXBreakpointProfile}.
     */
    public static final class Builder {

        private int columns = -1;
        private final EnumMap<RXBreakpoint, Double> minWidths = new EnumMap<>(RXBreakpoint.class);

        private Builder() {
        }

        /**
         * Sets the default column count.
         *
         * @param columns the column count
         * @return this builder
         * @throws IllegalArgumentException if {@code columns <= 0}
         */
        public Builder columns(int columns) {
            if (columns <= 0) {
                throw new IllegalArgumentException("columns must be greater than zero");
            }
            this.columns = columns;
            return this;
        }

        /**
         * Adds or replaces a tier threshold.
         *
         * @param breakpoint the tier
         * @param minWidth   the inclusive minimum width
         * @return this builder
         * @throws NullPointerException     if {@code breakpoint} is {@code null}
         * @throws IllegalArgumentException if the width is not finite and
         *                                  non-negative
         */
        public Builder breakpoint(RXBreakpoint breakpoint, double minWidth) {
            Objects.requireNonNull(breakpoint, "breakpoint cannot be null");
            if (!Double.isFinite(minWidth) || minWidth < 0.0) {
                throw new IllegalArgumentException("minWidth must be finite and non-negative");
            }
            minWidths.put(breakpoint, minWidth);
            return this;
        }

        /**
         * Builds the profile.
         *
         * @return the profile
         * @throws IllegalStateException    if the column count was not set
         * @throws IllegalArgumentException if no tier was added
         */
        public RXBreakpointProfile build() {
            if (columns <= 0) {
                throw new IllegalStateException("columns must be set before build");
            }
            return RXBreakpointProfile.of(columns, minWidths);
        }
    }
}
