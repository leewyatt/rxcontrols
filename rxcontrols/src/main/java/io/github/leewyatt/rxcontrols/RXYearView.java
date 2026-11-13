package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXYearViewSkin;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Skin;

import java.time.Year;
import java.util.function.Predicate;

/**
 * Grid for picking a {@link Year}: the years of the displayed decade, plus the last year of the previous and
 * the first year of the next decade (3 x 4). Usable on its own or as the content
 * of {@link RXYearPicker}'s popup.
 *
 * <p>The header pages through years; its title drills up to coarser grids for fast
 * navigation, and picking there drills back down. Clicking a year (or pressing
 * Enter / Space on the keyboard cursor) sets the value and fires the
 * {@link #onActionProperty() action}. Years outside the bounds or rejected
 * by the filter are disabled.
 *
 * <p>CSS: style class {@code rx-year-view}; cells are {@code .date-cell} with
 * {@code .year-cell} and the pseudo-classes {@code :selected}, {@code :today} (the
 * current year), {@code :focused} (keyboard cursor) and {@code :disabled}. The root
 * has {@code :invalid} while the value violates the constraints.
 */
public class RXYearView extends RXTemporalViewBase<Year> {

    private static final String DEFAULT_STYLE_CLASS = "rx-year-view";

    // ==================== Constructors ====================

    /**
     * Creates a view with no selection.
     */
    public RXYearView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a view with the given selection.
     *
     * @param value the initial value, or {@code null}
     */
    public RXYearView(Year value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXYearViewSkin(this);
    }

    // ==================== Displayed Year ====================

    private final ObjectProperty<Year> displayedYear = new SimpleObjectProperty<>(this, "displayedYear");

    /**
     * A year of the decade shown. The skin keeps it in step with paging and reveals the
     * value's decade when the value changes. {@code null} shows the value's decade, or
     * the current decade when there is no value.
     *
     * @return the displayed-year property
     */
    public final ObjectProperty<Year> displayedYearProperty() {
        return displayedYear;
    }

    /**
     * Returns the displayed year.
     *
     * @return the displayed year
     */
    public final Year getDisplayedYear() {
        return displayedYear.get();
    }

    /**
     * Sets the displayed year.
     *
     * @param value the displayed year
     */
    public final void setDisplayedYear(Year value) {
        displayedYear.set(value);
    }

    // ==================== Min Year ====================

    private final ObjectProperty<Year> minYear = new SimpleObjectProperty<>(this, "minYear");

    /**
     * The earliest selectable year, or {@code null} for no lower bound.
     *
     * @return the min-year property
     */
    public final ObjectProperty<Year> minYearProperty() {
        return minYear;
    }

    /**
     * Returns the earliest selectable year.
     *
     * @return the earliest selectable year
     */
    public final Year getMinYear() {
        return minYear.get();
    }

    /**
     * Sets the earliest selectable year.
     *
     * @param value the earliest selectable year
     */
    public final void setMinYear(Year value) {
        minYear.set(value);
    }

    // ==================== Max Year ====================

    private final ObjectProperty<Year> maxYear = new SimpleObjectProperty<>(this, "maxYear");

    /**
     * The latest selectable year, or {@code null} for no upper bound.
     *
     * @return the max-year property
     */
    public final ObjectProperty<Year> maxYearProperty() {
        return maxYear;
    }

    /**
     * Returns the latest selectable year.
     *
     * @return the latest selectable year
     */
    public final Year getMaxYear() {
        return maxYear.get();
    }

    /**
     * Sets the latest selectable year.
     *
     * @param value the latest selectable year
     */
    public final void setMaxYear(Year value) {
        maxYear.set(value);
    }

    // ==================== Year Filter ====================

    private final ObjectProperty<Predicate<Year>> yearFilter = new SimpleObjectProperty<>(this, "yearFilter");

    /**
     * Decides which years within the bounds are selectable: {@code true} keeps a
     * year selectable, {@code false} disables it. {@code null} accepts every year.
     *
     * @return the year-filter property
     */
    public final ObjectProperty<Predicate<Year>> yearFilterProperty() {
        return yearFilter;
    }

    /**
     * Returns the year filter.
     *
     * @return the year filter
     */
    public final Predicate<Year> getYearFilter() {
        return yearFilter.get();
    }

    /**
     * Sets the year filter.
     *
     * @param value the year filter
     */
    public final void setYearFilter(Predicate<Year> value) {
        yearFilter.set(value);
    }
}
