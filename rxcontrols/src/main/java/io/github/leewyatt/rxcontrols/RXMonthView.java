package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXMonthViewSkin;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Skin;

import java.time.Year;
import java.time.YearMonth;
import java.util.function.Predicate;

/**
 * Grid for picking a {@link YearMonth}: the twelve months of the displayed year (3 x 4). Usable on its own or as the content
 * of {@link RXMonthPicker}'s popup.
 *
 * <p>The header pages through years; its title drills up to coarser grids for fast
 * navigation, and picking there drills back down. Clicking a month (or pressing
 * Enter / Space on the keyboard cursor) sets the value and fires the
 * {@link #onActionProperty() action}. Months outside the bounds or rejected
 * by the filter are disabled.
 *
 * <p>CSS: style class {@code rx-month-view}; cells are {@code .date-cell} with
 * {@code .month-cell} and the pseudo-classes {@code :selected}, {@code :today} (the
 * current month), {@code :focused} (keyboard cursor) and {@code :disabled}. The root
 * has {@code :invalid} while the value violates the constraints.
 */
public class RXMonthView extends RXTemporalViewBase<YearMonth> {

    private static final String DEFAULT_STYLE_CLASS = "rx-month-view";

    // ==================== Constructors ====================

    /**
     * Creates a view with no selection.
     */
    public RXMonthView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a view with the given selection.
     *
     * @param value the initial value, or {@code null}
     */
    public RXMonthView(YearMonth value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXMonthViewSkin(this);
    }

    // ==================== Displayed Year ====================

    private final ObjectProperty<Year> displayedYear = new SimpleObjectProperty<>(this, "displayedYear");

    /**
     * The year whose months are shown. The skin keeps it in step with paging and reveals
     * the value's year when the value changes. {@code null} shows the value's year, or
     * the current year when there is no value.
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

    // ==================== Min Month ====================

    private final ObjectProperty<YearMonth> minMonth = new SimpleObjectProperty<>(this, "minMonth");

    /**
     * The earliest selectable month, or {@code null} for no lower bound.
     *
     * @return the min-month property
     */
    public final ObjectProperty<YearMonth> minMonthProperty() {
        return minMonth;
    }

    /**
     * Returns the earliest selectable month.
     *
     * @return the earliest selectable month
     */
    public final YearMonth getMinMonth() {
        return minMonth.get();
    }

    /**
     * Sets the earliest selectable month.
     *
     * @param value the earliest selectable month
     */
    public final void setMinMonth(YearMonth value) {
        minMonth.set(value);
    }

    // ==================== Max Month ====================

    private final ObjectProperty<YearMonth> maxMonth = new SimpleObjectProperty<>(this, "maxMonth");

    /**
     * The latest selectable month, or {@code null} for no upper bound.
     *
     * @return the max-month property
     */
    public final ObjectProperty<YearMonth> maxMonthProperty() {
        return maxMonth;
    }

    /**
     * Returns the latest selectable month.
     *
     * @return the latest selectable month
     */
    public final YearMonth getMaxMonth() {
        return maxMonth.get();
    }

    /**
     * Sets the latest selectable month.
     *
     * @param value the latest selectable month
     */
    public final void setMaxMonth(YearMonth value) {
        maxMonth.set(value);
    }

    // ==================== Month Filter ====================

    private final ObjectProperty<Predicate<YearMonth>> monthFilter = new SimpleObjectProperty<>(this, "monthFilter");

    /**
     * Decides which months within the bounds are selectable: {@code true} keeps a
     * month selectable, {@code false} disables it. {@code null} accepts every month.
     *
     * @return the month-filter property
     */
    public final ObjectProperty<Predicate<YearMonth>> monthFilterProperty() {
        return monthFilter;
    }

    /**
     * Returns the month filter.
     *
     * @return the month filter
     */
    public final Predicate<YearMonth> getMonthFilter() {
        return monthFilter.get();
    }

    /**
     * Sets the month filter.
     *
     * @param value the month filter
     */
    public final void setMonthFilter(Predicate<YearMonth> value) {
        monthFilter.set(value);
    }
}
