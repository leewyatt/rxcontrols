package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXDateRangeViewSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Skin;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.function.Predicate;

/**
 * Calendar for picking an {@link RXDateRange}, showing one or more consecutive months
 * side by side. Usable on its own or as the content of {@link RXDateRangePicker}'s
 * popup.
 *
 * <p>The first pick sets an anchor; moving the pointer (or the keyboard cursor)
 * previews the range from the anchor; the second pick completes the range, in either
 * order, updates the value and fires the {@link #onActionProperty() action}. The
 * value only ever changes to a complete range, so listeners never see a half-picked
 * one. Only the endpoints must be selectable: a range may span disabled dates.
 * Escape (while focused) or setting the value drops a pending anchor.
 *
 * <p>Keyboard and drill-up navigation work as in {@link RXCalendarView}. CSS: style
 * class {@code rx-date-range-view}; day cells carry {@code :range-start},
 * {@code :range-end}, {@code :in-range} and, while previewing, {@code :preview}.
 */
public class RXDateRangeView extends RXTemporalViewBase<RXDateRange> {

    private static final String DEFAULT_STYLE_CLASS = "rx-date-range-view";

    // ==================== Constructors ====================

    /**
     * Creates a range calendar with no selection.
     */
    public RXDateRangeView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a range calendar with the given selection.
     *
     * @param value the initial range, or {@code null}
     */
    public RXDateRangeView(RXDateRange value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXDateRangeViewSkin(this);
    }

    // ==================== Displayed Month ====================

    private final ObjectProperty<YearMonth> displayedMonth = new SimpleObjectProperty<>(this, "displayedMonth");

    /**
     * The month shown by the first page. The skin keeps it in step with paging and
     * keyboard navigation and reveals the value when it changes; setting it pages the
     * calendar. {@code null} shows the month of the value's start, or the current
     * month when there is no value.
     *
     * @return the displayed-month property
     */
    public final ObjectProperty<YearMonth> displayedMonthProperty() {
        return displayedMonth;
    }

    /**
     * Returns the first displayed month.
     *
     * @return the first displayed month
     */
    public final YearMonth getDisplayedMonth() {
        return displayedMonth.get();
    }

    /**
     * Sets the first displayed month.
     *
     * @param value the first displayed month
     */
    public final void setDisplayedMonth(YearMonth value) {
        displayedMonth.set(value);
    }

    // ==================== Month Count ====================

    private final IntegerProperty monthCount = new SimpleIntegerProperty(this, "monthCount", 2);

    /**
     * Number of consecutive months shown side by side. Values below 1 are treated as 1.
     * Defaults to 2.
     *
     * @return the month-count property
     */
    public final IntegerProperty monthCountProperty() {
        return monthCount;
    }

    /**
     * Returns the number of months shown.
     *
     * @return the number of months shown
     */
    public final int getMonthCount() {
        return monthCount.get();
    }

    /**
     * Sets the number of months shown.
     *
     * @param value the number of months shown
     */
    public final void setMonthCount(int value) {
        monthCount.set(value);
    }

    // ==================== Min Date ====================

    private final ObjectProperty<LocalDate> minDate = new SimpleObjectProperty<>(this, "minDate");

    /**
     * The earliest selectable date, or {@code null} for no lower bound.
     *
     * @return the min-date property
     */
    public final ObjectProperty<LocalDate> minDateProperty() {
        return minDate;
    }

    /**
     * Returns the earliest selectable date.
     *
     * @return the earliest selectable date
     */
    public final LocalDate getMinDate() {
        return minDate.get();
    }

    /**
     * Sets the earliest selectable date.
     *
     * @param value the earliest selectable date
     */
    public final void setMinDate(LocalDate value) {
        minDate.set(value);
    }

    // ==================== Max Date ====================

    private final ObjectProperty<LocalDate> maxDate = new SimpleObjectProperty<>(this, "maxDate");

    /**
     * The latest selectable date, or {@code null} for no upper bound.
     *
     * @return the max-date property
     */
    public final ObjectProperty<LocalDate> maxDateProperty() {
        return maxDate;
    }

    /**
     * Returns the latest selectable date.
     *
     * @return the latest selectable date
     */
    public final LocalDate getMaxDate() {
        return maxDate.get();
    }

    /**
     * Sets the latest selectable date.
     *
     * @param value the latest selectable date
     */
    public final void setMaxDate(LocalDate value) {
        maxDate.set(value);
    }

    // ==================== Date Filter ====================

    private final ObjectProperty<Predicate<LocalDate>> dateFilter = new SimpleObjectProperty<>(this, "dateFilter");

    /**
     * Decides which dates within the bounds are selectable: {@code true} keeps a
     * date selectable, {@code false} disables it. {@code null} accepts every date.
     *
     * @return the date-filter property
     */
    public final ObjectProperty<Predicate<LocalDate>> dateFilterProperty() {
        return dateFilter;
    }

    /**
     * Returns the date filter.
     *
     * @return the date filter
     */
    public final Predicate<LocalDate> getDateFilter() {
        return dateFilter.get();
    }

    /**
     * Sets the date filter.
     *
     * @param value the date filter
     */
    public final void setDateFilter(Predicate<LocalDate> value) {
        dateFilter.set(value);
    }

    // ==================== Week Fields ====================

    private final ObjectProperty<WeekFields> weekFields = new SimpleObjectProperty<>(this, "weekFields");

    /**
     * The week rules (first day of the week and week numbering) of the calendar.
     * {@code null} (the initial value) uses the rules of the locale.
     *
     * @return the week-fields property
     */
    public final ObjectProperty<WeekFields> weekFieldsProperty() {
        return weekFields;
    }

    /**
     * Returns the week rules.
     *
     * @return the week rules
     */
    public final WeekFields getWeekFields() {
        return weekFields.get();
    }

    /**
     * Sets the week rules.
     *
     * @param value the week rules
     */
    public final void setWeekFields(WeekFields value) {
        weekFields.set(value);
    }

    // ==================== Show Week Numbers ====================

    private final BooleanProperty showWeekNumbers = new SimpleBooleanProperty(this, "showWeekNumbers", false);

    /**
     * Whether the calendar shows week numbers. Defaults to {@code false}.
     *
     * @return the show-week-numbers property
     */
    public final BooleanProperty showWeekNumbersProperty() {
        return showWeekNumbers;
    }

    /**
     * Returns whether week numbers are shown.
     *
     * @return whether week numbers are shown
     */
    public final boolean isShowWeekNumbers() {
        return showWeekNumbers.get();
    }

    /**
     * Sets whether week numbers are shown.
     *
     * @param value whether week numbers are shown
     */
    public final void setShowWeekNumbers(boolean value) {
        showWeekNumbers.set(value);
    }

    // ==================== Show Adjacent Days ====================

    private final BooleanProperty showAdjacentDays = new SimpleBooleanProperty(this, "showAdjacentDays", true);

    /**
     * Whether the calendar fills its grid with days of the adjacent months, which
     * stay selectable. Defaults to {@code true}.
     *
     * @return the show-adjacent-days property
     */
    public final BooleanProperty showAdjacentDaysProperty() {
        return showAdjacentDays;
    }

    /**
     * Returns whether adjacent days are shown.
     *
     * @return whether adjacent days are shown
     */
    public final boolean isShowAdjacentDays() {
        return showAdjacentDays.get();
    }

    /**
     * Sets whether adjacent days are shown.
     *
     * @param value whether adjacent days are shown
     */
    public final void setShowAdjacentDays(boolean value) {
        showAdjacentDays.set(value);
    }
}
