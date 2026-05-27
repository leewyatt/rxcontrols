package io.github.leewyatt.rxcontrols.layout;

/**
 * Horizontal distribution of remaining space for each responsive row line.
 *
 * <p>Column offsets are treated as occupied space, equivalent to fixed
 * flexbox margins. Justification distributes only the remaining free space
 * after spans and offsets are accounted for.</p>
 */
public enum RXRowJustify {
    /**
     * Keep remaining space after the last column.
     */
    START,
    /**
     * Split remaining space before and after the line.
     */
    CENTER,
    /**
     * Place remaining space before the first column.
     */
    END,
    /**
     * Distribute remaining space between columns.
     */
    SPACE_BETWEEN,
    /**
     * Distribute remaining space around columns with half-size edge gaps.
     */
    SPACE_AROUND,
    /**
     * Distribute remaining space evenly between columns and both edges.
     */
    SPACE_EVENLY
}
