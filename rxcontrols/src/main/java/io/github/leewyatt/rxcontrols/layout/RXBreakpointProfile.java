package io.github.leewyatt.rxcontrols.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Defines the column count and ordered breakpoints used by a responsive row.
 *
 * <p>Three built-in presets are provided. {@link #ANT_DESIGN} is the default
 * because it matches this library's grid API (24 columns, {@code xs..xxl}).
 * {@link #ELEMENT} and {@link #BOOTSTRAP} mirror those frameworks. Any other
 * convention can be built with {@link #of(int, List)} or {@link #builder()}.</p>
 *
 * <p>Built-in presets:</p>
 * <pre>
 *                cols  xs   sm    md    lg     xl     xxl
 *   ANT_DESIGN    24   0    576   768   992    1200   1600
 *   ELEMENT       24   0    768   992   1200   1920   --
 *   BOOTSTRAP     12   0    576   768   992    1200   1400
 * </pre>
 *
 * <p>Other ecosystems, for reference (build via {@link #of(int, List)}; values
 * in CSS pixels). Note that frameworks reuse the same names for different
 * widths, so port pixels deliberately rather than assuming {@code md} is one
 * fixed value:</p>
 * <pre>
 *                cols  breakpoints (px)
 *   Tailwind     (12)  sm 640 / md 768 / lg 1024 / xl 1280 / 2xl 1536   (no xs; base = mobile)
 *   MUI v5        12   xs 0 / sm 600 / md 900 / lg 1200 / xl 1536
 *   Foundation    12   small 0 / medium 640 / large 1024 / xlarge 1200 / xxlarge 1440
 *   Bulma         12   mobile 0 / tablet 769 / desktop 1024 / widescreen 1216 / fullhd 1408
 *   Material 3    --   Compact 0 / Medium 600 / Expanded 840 / Large 1200 / ExtraLarge 1600
 * </pre>
 *
 * <p>Profiles that use non-{@code xs..xxl} names (Foundation, Bulma, Material)
 * are still fully supported, but the typed {@code RXCol.xsProperty()..xxlProperty()}
 * accessors only apply to profiles using those names; otherwise use
 * {@link RXCol#setBreakpointSpec(String, RXColSpec)}.</p>
 */
public final class RXBreakpointProfile {

    /**
     * Ant Design-style profile and library default: 24 columns and
     * xs/sm/md/lg/xl/xxl breakpoints. Shares the xs..xl thresholds with
     * {@link #BOOTSTRAP}; only the column count and {@code xxl} differ.
     */
    public static final RXBreakpointProfile ANT_DESIGN = RXBreakpointProfile.of(24, List.of(
            new RXBreakpoint("xs", 0.0),
            new RXBreakpoint("sm", 576.0),
            new RXBreakpoint("md", 768.0),
            new RXBreakpoint("lg", 992.0),
            new RXBreakpoint("xl", 1200.0),
            new RXBreakpoint("xxl", 1600.0)));

    /**
     * Element-style profile: 24 columns and xs/sm/md/lg/xl breakpoints.
     */
    public static final RXBreakpointProfile ELEMENT = RXBreakpointProfile.of(24, List.of(
            new RXBreakpoint("xs", 0.0),
            new RXBreakpoint("sm", 768.0),
            new RXBreakpoint("md", 992.0),
            new RXBreakpoint("lg", 1200.0),
            new RXBreakpoint("xl", 1920.0)));

    /**
     * Bootstrap-style profile: 12 columns and xs/sm/md/lg/xl/xxl breakpoints.
     */
    public static final RXBreakpointProfile BOOTSTRAP = RXBreakpointProfile.of(12, List.of(
            new RXBreakpoint("xs", 0.0),
            new RXBreakpoint("sm", 576.0),
            new RXBreakpoint("md", 768.0),
            new RXBreakpoint("lg", 992.0),
            new RXBreakpoint("xl", 1200.0),
            new RXBreakpoint("xxl", 1400.0)));

    private final int columns;
    private final List<RXBreakpoint> breakpoints;

    private RXBreakpointProfile(int columns, List<RXBreakpoint> breakpoints) {
        this.columns = columns;
        this.breakpoints = breakpoints;
    }

    /**
     * Creates a profile with the given column count and breakpoints.
     *
     * @param columns     the default number of columns
     * @param breakpoints the breakpoints, sorted by width internally
     * @return the profile
     * @throws NullPointerException     if {@code breakpoints} or one of its
     *                                  entries is {@code null}
     * @throws IllegalArgumentException if {@code columns <= 0}, the list is
     *                                  empty, or breakpoint names repeat
     */
    public static RXBreakpointProfile of(int columns, List<RXBreakpoint> breakpoints) {
        Objects.requireNonNull(breakpoints, "breakpoints cannot be null");
        if (columns <= 0) {
            throw new IllegalArgumentException("columns must be greater than zero");
        }
        if (breakpoints.isEmpty()) {
            throw new IllegalArgumentException("breakpoints cannot be empty");
        }

        List<RXBreakpoint> sorted = new ArrayList<>(breakpoints);
        for (RXBreakpoint breakpoint : sorted) {
            Objects.requireNonNull(breakpoint, "breakpoint cannot be null");
        }
        sorted.sort(Comparator.naturalOrder());

        Set<String> names = new HashSet<>();
        for (RXBreakpoint breakpoint : sorted) {
            if (!names.add(breakpoint.getName())) {
                throw new IllegalArgumentException("duplicate breakpoint name: " + breakpoint.getName());
            }
        }

        return new RXBreakpointProfile(columns, List.copyOf(sorted));
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
     * Returns the breakpoints in ascending minimum-width order.
     *
     * @return the ordered breakpoints
     */
    public List<RXBreakpoint> getBreakpoints() {
        return breakpoints;
    }

    /**
     * Resolves the active breakpoint for the given row width.
     *
     * @param width the row width
     * @return the largest breakpoint whose minimum width is not greater than
     *         {@code width}
     */
    public RXBreakpoint resolve(double width) {
        double normalizedWidth = Double.isNaN(width) || width < 0.0 ? 0.0 : width;
        RXBreakpoint resolved = breakpoints.get(0);
        for (RXBreakpoint breakpoint : breakpoints) {
            if (breakpoint.getMinWidth() <= normalizedWidth) {
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
        private final List<RXBreakpoint> breakpoints = new ArrayList<>();

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
         * Adds a breakpoint.
         *
         * @param name     the breakpoint name
         * @param minWidth the inclusive minimum width
         * @return this builder
         * @throws NullPointerException     if {@code name} is {@code null}
         * @throws IllegalArgumentException if the name is blank or the width is
         *                                  not finite and non-negative
         */
        public Builder breakpoint(String name, double minWidth) {
            breakpoints.add(new RXBreakpoint(name, minWidth));
            return this;
        }

        /**
         * Adds a breakpoint.
         *
         * @param breakpoint the breakpoint
         * @return this builder
         * @throws NullPointerException if {@code breakpoint} is {@code null}
         */
        public Builder breakpoint(RXBreakpoint breakpoint) {
            breakpoints.add(Objects.requireNonNull(breakpoint, "breakpoint cannot be null"));
            return this;
        }

        /**
         * Builds the profile.
         *
         * @return the profile
         * @throws IllegalStateException if the column count was not set
         * @throws IllegalArgumentException if no breakpoint was added or
         *                                  breakpoint names repeat
         */
        public RXBreakpointProfile build() {
            if (columns <= 0) {
                throw new IllegalStateException("columns must be set before build");
            }
            return RXBreakpointProfile.of(columns, breakpoints);
        }
    }
}
