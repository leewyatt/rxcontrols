package io.github.leewyatt.rxcontrols;

/**
 * Horizontal distribution of cells within a {@link RXGridView} row when the row
 * has spare width (that is, when {@code stretchCells} is {@code false} and the
 * resolved cells do not fill the content width exactly).
 *
 * <p>The leftover width of a row — the difference between the content width and
 * the space the fixed-width cells plus gaps consume — is placed according to the
 * selected value. When {@code stretchCells} is {@code true} the cells are grown
 * to fill the row instead and this setting has no visible effect.
 */
public enum RXGridJustify {

    /** Pack cells against the leading edge; trailing space stays empty. */
    START,

    /** Center the cells, splitting the leftover width before and after them. */
    CENTER,

    /** Pack cells against the trailing edge; leading space stays empty. */
    END
}
