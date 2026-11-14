package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXMonthRangeViewSkin;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Skin;

import java.time.Year;
import java.time.YearMonth;
import java.util.function.Predicate;

/**
 * Grid for picking an {@link RXMonthRange}, showing the months of one or more consecutive years
 * side by side. Usable on its own or as the content of {@link RXMonthRangePicker}'s
 * popup.
 *
 * <p>The first pick sets an anchor; moving the pointer (or the keyboard cursor)
 * previews the range from the anchor; the second pick completes the range, in either
 * order, updates the value and fires the {@link #onActionProperty() action}. The
 * value only ever changes to a complete range, so listeners never see a half-picked
 * one. Only the endpoints must be selectable: a range may span disabled months.
 * Escape (while focused) or setting the value drops a pending anchor.
 *
 * <p>Keyboard and drill-up navigation work as in {@link RXMonthView}. CSS: style
 * class {@code rx-month-range-view}; cells are {@code .date-cell} with
 * {@code .month-cell} and carry {@code :range-start}, {@code :range-end},
 * {@code :in-range} and, while previewing, {@code :preview}. The first and the last
 * range month of each grid row also carry {@code :segment-start} and
 * {@code :segment-end}. The root has {@code :invalid} while an endpoint of the value
 * violates the constraints.
 */
public class RXMonthRangeView extends RXTemporalViewBase<RXMonthRange> {

    private static final String DEFAULT_STYLE_CLASS = "rx-month-range-view";

    // ==================== Constructors ====================

    /**
     * Creates a range view with no selection.
     */
    public RXMonthRangeView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a range view with the given selection.
     *
     * @param value the initial range, or {@code null}
     */
    public RXMonthRangeView(RXMonthRange value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXMonthRangeViewSkin(this);
    }

    // ==================== Displayed Year ====================

    private final ObjectProperty<Year> displayedYear = new SimpleObjectProperty<>(this, "displayedYear");

    /**
     * The year shown by the first page. The skin keeps it in step with paging and reveals
     * the value when it changes; setting it pages the view. {@code null} shows the year
     * of the value's start, or the current year when there is no value.
     *
     * @return the displayed-year property
     */
    public final ObjectProperty<Year> displayedYearProperty() {
        return displayedYear;
    }

    /**
     * Returns the first displayed year.
     *
     * @return the first displayed year
     */
    public final Year getDisplayedYear() {
        return displayedYear.get();
    }

    /**
     * Sets the first displayed year.
     *
     * @param value the first displayed year
     */
    public final void setDisplayedYear(Year value) {
        displayedYear.set(value);
    }

    // ==================== Year Count ====================

    private final IntegerProperty yearCount = new SimpleIntegerProperty(this, "yearCount", 2);

    /**
     * Number of consecutive years shown side by side. Values below 1 are treated as 1.
     * Defaults to 2.
     *
     * @return the year-count property
     */
    public final IntegerProperty yearCountProperty() {
        return yearCount;
    }

    /**
     * Returns the number of years shown.
     *
     * @return the number of years shown
     */
    public final int getYearCount() {
        return yearCount.get();
    }

    /**
     * Sets the number of years shown.
     *
     * @param value the number of years shown
     */
    public final void setYearCount(int value) {
        yearCount.set(value);
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
