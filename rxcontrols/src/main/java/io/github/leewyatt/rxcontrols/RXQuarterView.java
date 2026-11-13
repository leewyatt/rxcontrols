package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXQuarterViewSkin;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Skin;

import java.time.Year;
import java.util.function.Predicate;

/**
 * Grid for picking an {@link RXYearQuarter}: the four quarters of the displayed year (4 x 1). Usable on its own or as the content
 * of {@link RXQuarterPicker}'s popup.
 *
 * <p>The header pages through years; its title drills up to coarser grids for fast
 * navigation, and picking there drills back down. Clicking a quarter (or pressing
 * Enter / Space on the keyboard cursor) sets the value and fires the
 * {@link #onActionProperty() action}. Quarters outside the bounds or rejected
 * by the filter are disabled.
 *
 * <p>CSS: style class {@code rx-quarter-view}; cells are {@code .date-cell} with
 * {@code .quarter-cell} and the pseudo-classes {@code :selected}, {@code :today} (the
 * current quarter), {@code :focused} (keyboard cursor) and {@code :disabled}. The root
 * has {@code :invalid} while the value violates the constraints.
 */
public class RXQuarterView extends RXTemporalViewBase<RXYearQuarter> {

    private static final String DEFAULT_STYLE_CLASS = "rx-quarter-view";

    // ==================== Constructors ====================

    /**
     * Creates a view with no selection.
     */
    public RXQuarterView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a view with the given selection.
     *
     * @param value the initial value, or {@code null}
     */
    public RXQuarterView(RXYearQuarter value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXQuarterViewSkin(this);
    }

    // ==================== Displayed Year ====================

    private final ObjectProperty<Year> displayedYear = new SimpleObjectProperty<>(this, "displayedYear");

    /**
     * The year whose quarters are shown. The skin keeps it in step with paging and
     * reveals the value's year when the value changes. {@code null} shows the value's
     * year, or the current year when there is no value.
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

    // ==================== Min Quarter ====================

    private final ObjectProperty<RXYearQuarter> minQuarter = new SimpleObjectProperty<>(this, "minQuarter");

    /**
     * The earliest selectable quarter, or {@code null} for no lower bound.
     *
     * @return the min-quarter property
     */
    public final ObjectProperty<RXYearQuarter> minQuarterProperty() {
        return minQuarter;
    }

    /**
     * Returns the earliest selectable quarter.
     *
     * @return the earliest selectable quarter
     */
    public final RXYearQuarter getMinQuarter() {
        return minQuarter.get();
    }

    /**
     * Sets the earliest selectable quarter.
     *
     * @param value the earliest selectable quarter
     */
    public final void setMinQuarter(RXYearQuarter value) {
        minQuarter.set(value);
    }

    // ==================== Max Quarter ====================

    private final ObjectProperty<RXYearQuarter> maxQuarter = new SimpleObjectProperty<>(this, "maxQuarter");

    /**
     * The latest selectable quarter, or {@code null} for no upper bound.
     *
     * @return the max-quarter property
     */
    public final ObjectProperty<RXYearQuarter> maxQuarterProperty() {
        return maxQuarter;
    }

    /**
     * Returns the latest selectable quarter.
     *
     * @return the latest selectable quarter
     */
    public final RXYearQuarter getMaxQuarter() {
        return maxQuarter.get();
    }

    /**
     * Sets the latest selectable quarter.
     *
     * @param value the latest selectable quarter
     */
    public final void setMaxQuarter(RXYearQuarter value) {
        maxQuarter.set(value);
    }

    // ==================== Quarter Filter ====================

    private final ObjectProperty<Predicate<RXYearQuarter>> quarterFilter = new SimpleObjectProperty<>(this, "quarterFilter");

    /**
     * Decides which quarters within the bounds are selectable: {@code true} keeps a
     * quarter selectable, {@code false} disables it. {@code null} accepts every quarter.
     *
     * @return the quarter-filter property
     */
    public final ObjectProperty<Predicate<RXYearQuarter>> quarterFilterProperty() {
        return quarterFilter;
    }

    /**
     * Returns the quarter filter.
     *
     * @return the quarter filter
     */
    public final Predicate<RXYearQuarter> getQuarterFilter() {
        return quarterFilter.get();
    }

    /**
     * Sets the quarter filter.
     *
     * @param value the quarter filter
     */
    public final void setQuarterFilter(Predicate<RXYearQuarter> value) {
        quarterFilter.set(value);
    }
}
