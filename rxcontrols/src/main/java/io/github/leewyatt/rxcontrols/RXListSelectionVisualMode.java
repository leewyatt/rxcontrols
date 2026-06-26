package io.github.leewyatt.rxcontrols;

import javafx.scene.control.SelectionMode;

/**
 * How an {@link RXListView} renders its selection. This is purely the visual
 * affordance — there is a single selection state (the
 * {@link RXListView#selectionModelProperty() selectionModel}); every mode shows
 * that same state, so switching modes never loses or duplicates it. The
 * cardinality (single / multiple) lives on the selection model, independent of
 * this axis.
 *
 * <ul>
 *   <li>{@link #ROW} — full-row highlight (plain list look).</li>
 *   <li>{@link #CHECKMARK} — a lightweight leading tick on each selected row.</li>
 *   <li>{@link #CHECKBOX} — a leading checkbox reflecting the selected state;
 *       clicking a row toggles it (the checkbox is a display-only indicator).</li>
 *   <li>{@link #AUTO} — derives from the cardinality.</li>
 * </ul>
 */
public enum RXListSelectionVisualMode {

    /** Derived: {@code SINGLE} resolves to {@link #ROW}, {@code MULTIPLE} to {@link #CHECKBOX}. */
    AUTO,

    /** Full-row highlight of the selected rows. */
    ROW,

    /** A lightweight leading checkmark on each selected row. */
    CHECKMARK,

    /** A leading checkbox per row, checked when the row is selected; row click toggles selection. */
    CHECKBOX;

    /**
     * Resolves the effective visual mode from a configured mode and the current
     * cardinality: {@code null} or {@link #AUTO} derives from {@code selectionMode}
     * ({@code MULTIPLE} → {@link #CHECKBOX}, otherwise {@link #ROW}); any explicit
     * mode (including {@link #CHECKBOX} under single selection) is honored as-is.
     *
     * @param configured    the configured visual mode, or {@code null}
     * @param selectionMode the current selection cardinality, or {@code null}
     * @return the resolved, concrete visual mode (never {@code AUTO})
     */
    public static RXListSelectionVisualMode resolve(RXListSelectionVisualMode configured, SelectionMode selectionMode) {
        RXListSelectionVisualMode mode = configured == null ? AUTO : configured;
        if (mode == AUTO) {
            mode = selectionMode == SelectionMode.MULTIPLE ? CHECKBOX : ROW;
        }
        return mode;
    }
}
