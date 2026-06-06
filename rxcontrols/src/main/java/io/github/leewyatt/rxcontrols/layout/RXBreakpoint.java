package io.github.leewyatt.rxcontrols.layout;

import java.util.Objects;

/**
 * A named responsive breakpoint with a minimum row width.
 */
public final class RXBreakpoint implements Comparable<RXBreakpoint> {

    private final String name;
    private final double minWidth;

    /**
     * Creates a breakpoint.
     *
     * <p>The name is used verbatim as a bare CSS pseudo-class (e.g. {@code :md}).
     * Prefer a lowercase CSS identifier — letters, digits and hyphens, not
     * starting with a digit and without spaces (join words with {@code -}) — and
     * avoid names that collide with JavaFX built-in pseudo-classes such as
     * {@code hover}, {@code focused}, {@code pressed} or {@code disabled}.</p>
     *
     * @param name     the breakpoint name, also used as the row pseudo-class
     * @param minWidth the inclusive minimum width for this breakpoint
     * @throws NullPointerException     if {@code name} is {@code null}
     * @throws IllegalArgumentException if the name is blank or the width is not
     *                                  finite and non-negative
     */
    public RXBreakpoint(String name, double minWidth) {
        Objects.requireNonNull(name, "name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (!Double.isFinite(minWidth) || minWidth < 0.0) {
            throw new IllegalArgumentException("minWidth must be finite and non-negative");
        }
        this.name = name;
        this.minWidth = minWidth;
    }

    /**
     * Returns the breakpoint name.
     *
     * @return the breakpoint name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the inclusive minimum width for this breakpoint.
     *
     * @return the minimum width
     */
    public double getMinWidth() {
        return minWidth;
    }

    @Override
    public int compareTo(RXBreakpoint other) {
        int byWidth = Double.compare(minWidth, other.minWidth);
        if (byWidth != 0) {
            return byWidth;
        }
        return name.compareTo(other.name);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RXBreakpoint other)) {
            return false;
        }
        return name.equals(other.name) && Double.compare(minWidth, other.minWidth) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, minWidth);
    }

    @Override
    public String toString() {
        return "RXBreakpoint[name=" + name + ", minWidth=" + minWidth + "]";
    }
}
