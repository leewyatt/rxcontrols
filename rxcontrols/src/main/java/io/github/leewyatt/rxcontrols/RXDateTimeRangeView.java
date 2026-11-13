package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXDateTimeRangeViewSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Skin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.function.Predicate;

/**
 * A range calendar beside time columns for picking an {@link RXDateTimeRange}, usable
 * on its own or as the content of {@link RXDateTimeRangePicker}'s popup.
 *
 * <p>Days are picked like in {@link RXDateRangeView}: the first pick anchors the range
 * and the second completes it in either order. The time columns edit one endpoint at
 * a time, chosen with the Start / End tabs above them (the End tab is selected after
 * the first day pick); on a single-day range the end time cannot precede the start
 * time. The value only ever changes to a complete range. The
 * {@link #onActionProperty() action} fires when the user confirms the end time
 * (Enter in the time columns, or a double click on an item).
 *
 * <p><b>Keyboard</b> (while focused): the calendar keys of {@link RXCalendarView}; once
 * a range is completed with Enter the keyboard moves to the start time, Enter there
 * moves on to the end time, and Enter on the end time confirms. Left on the hour
 * column returns to the calendar.
 *
 * <p>CSS: style class {@code rx-date-time-range-view}; a {@code .rx-calendar-panel}
 * beside a {@code .time-pane} holding {@code .tabs} ({@code .start-tab},
 * {@code .end-tab}, {@code :selected}) and the {@code .rx-time-columns}.
 */
public class RXDateTimeRangeView extends RXTemporalViewBase<RXDateTimeRange> {

    private static final String DEFAULT_STYLE_CLASS = "rx-date-time-range-view";

    // ==================== Constructors ====================

    /**
     * Creates a date-time range view with no selection.
     */
    public RXDateTimeRangeView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a date-time range view with the given selection.
     *
     * @param value the initial range, or {@code null}
     */
    public RXDateTimeRangeView(RXDateTimeRange value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXDateTimeRangeViewSkin(this);
    }

    // ==================== Displayed Month ====================

    private final ObjectProperty<YearMonth> displayedMonth = new SimpleObjectProperty<>(this, "displayedMonth");

    /**
     * The month shown by the first page. The skin keeps it in step with paging and
     * reveals the value when it changes. {@code null} shows the month of the value's
     * start, or the current month when there is no value.
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

    // ==================== Min Date Time ====================

    private final ObjectProperty<LocalDateTime> minDateTime = new SimpleObjectProperty<>(this, "minDateTime");

    /**
     * The earliest selectable date-time, or {@code null} for no lower bound.
     *
     * @return the min-date-time property
     */
    public final ObjectProperty<LocalDateTime> minDateTimeProperty() {
        return minDateTime;
    }

    /**
     * Returns the earliest selectable date-time.
     *
     * @return the earliest selectable date-time
     */
    public final LocalDateTime getMinDateTime() {
        return minDateTime.get();
    }

    /**
     * Sets the earliest selectable date-time.
     *
     * @param value the earliest selectable date-time
     */
    public final void setMinDateTime(LocalDateTime value) {
        minDateTime.set(value);
    }

    // ==================== Max Date Time ====================

    private final ObjectProperty<LocalDateTime> maxDateTime = new SimpleObjectProperty<>(this, "maxDateTime");

    /**
     * The latest selectable date-time, or {@code null} for no upper bound.
     *
     * @return the max-date-time property
     */
    public final ObjectProperty<LocalDateTime> maxDateTimeProperty() {
        return maxDateTime;
    }

    /**
     * Returns the latest selectable date-time.
     *
     * @return the latest selectable date-time
     */
    public final LocalDateTime getMaxDateTime() {
        return maxDateTime.get();
    }

    /**
     * Sets the latest selectable date-time.
     *
     * @param value the latest selectable date-time
     */
    public final void setMaxDateTime(LocalDateTime value) {
        maxDateTime.set(value);
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

    // ==================== Time Filter ====================

    private final ObjectProperty<Predicate<LocalTime>> timeFilter = new SimpleObjectProperty<>(this, "timeFilter");

    /**
     * Decides which times of day are selectable on every date: {@code true} keeps a
     * time selectable, {@code false} disables it. {@code null} accepts every time.
     *
     * @return the time-filter property
     */
    public final ObjectProperty<Predicate<LocalTime>> timeFilterProperty() {
        return timeFilter;
    }

    /**
     * Returns the time filter.
     *
     * @return the time filter
     */
    public final Predicate<LocalTime> getTimeFilter() {
        return timeFilter.get();
    }

    /**
     * Sets the time filter.
     *
     * @param value the time filter
     */
    public final void setTimeFilter(Predicate<LocalTime> value) {
        timeFilter.set(value);
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

    // ==================== Show Seconds ====================

    private final BooleanProperty showSeconds = new SimpleBooleanProperty(this, "showSeconds", false);

    /**
     * Whether a seconds column is shown. Defaults to {@code false}.
     *
     * @return the show-seconds property
     */
    public final BooleanProperty showSecondsProperty() {
        return showSeconds;
    }

    /**
     * Returns whether seconds are shown.
     *
     * @return whether seconds are shown
     */
    public final boolean isShowSeconds() {
        return showSeconds.get();
    }

    /**
     * Sets whether seconds are shown.
     *
     * @param value whether seconds are shown
     */
    public final void setShowSeconds(boolean value) {
        showSeconds.set(value);
    }

    // ==================== Twelve Hour Clock ====================

    private final BooleanProperty twelveHourClock = new SimpleBooleanProperty(this, "twelveHourClock", false);

    /**
     * Whether hours use a 12-hour clock with an AM / PM column. Defaults to
     * {@code false} (24-hour).
     *
     * @return the twelve-hour-clock property
     */
    public final BooleanProperty twelveHourClockProperty() {
        return twelveHourClock;
    }

    /**
     * Returns whether a 12-hour clock is used.
     *
     * @return whether a 12-hour clock is used
     */
    public final boolean isTwelveHourClock() {
        return twelveHourClock.get();
    }

    /**
     * Sets whether a 12-hour clock is used.
     *
     * @param value whether a 12-hour clock is used
     */
    public final void setTwelveHourClock(boolean value) {
        twelveHourClock.set(value);
    }

    // ==================== Hour Step ====================

    private final IntegerProperty hourStep = new SimpleIntegerProperty(this, "hourStep", 1);

    /**
     * Interval between selectable hours. Values below 1 are treated as 1. Defaults to 1.
     *
     * @return the hour-step property
     */
    public final IntegerProperty hourStepProperty() {
        return hourStep;
    }

    /**
     * Returns the hour step.
     *
     * @return the hour step
     */
    public final int getHourStep() {
        return hourStep.get();
    }

    /**
     * Sets the hour step.
     *
     * @param value the hour step
     */
    public final void setHourStep(int value) {
        hourStep.set(value);
    }

    // ==================== Minute Step ====================

    private final IntegerProperty minuteStep = new SimpleIntegerProperty(this, "minuteStep", 1);

    /**
     * Interval between selectable minutes. Values below 1 are treated as 1. Defaults to 1.
     *
     * @return the minute-step property
     */
    public final IntegerProperty minuteStepProperty() {
        return minuteStep;
    }

    /**
     * Returns the minute step.
     *
     * @return the minute step
     */
    public final int getMinuteStep() {
        return minuteStep.get();
    }

    /**
     * Sets the minute step.
     *
     * @param value the minute step
     */
    public final void setMinuteStep(int value) {
        minuteStep.set(value);
    }

    // ==================== Second Step ====================

    private final IntegerProperty secondStep = new SimpleIntegerProperty(this, "secondStep", 1);

    /**
     * Interval between selectable seconds. Values below 1 are treated as 1. Defaults to 1.
     *
     * @return the second-step property
     */
    public final IntegerProperty secondStepProperty() {
        return secondStep;
    }

    /**
     * Returns the second step.
     *
     * @return the second step
     */
    public final int getSecondStep() {
        return secondStep.get();
    }

    /**
     * Sets the second step.
     *
     * @param value the second step
     */
    public final void setSecondStep(int value) {
        secondStep.set(value);
    }

    // ==================== Start Time Text ====================

    private final StringProperty startTimeText = new SimpleStringProperty(this, "startTimeText", "Start");

    /**
     * Text of the start-time tab. Defaults to {@code "Start"}.
     *
     * @return the start-time-text property
     */
    public final StringProperty startTimeTextProperty() {
        return startTimeText;
    }

    /**
     * Returns the start-time tab text.
     *
     * @return the start-time tab text
     */
    public final String getStartTimeText() {
        return startTimeText.get();
    }

    /**
     * Sets the start-time tab text.
     *
     * @param value the start-time tab text
     */
    public final void setStartTimeText(String value) {
        startTimeText.set(value);
    }

    // ==================== End Time Text ====================

    private final StringProperty endTimeText = new SimpleStringProperty(this, "endTimeText", "End");

    /**
     * Text of the end-time tab. Defaults to {@code "End"}.
     *
     * @return the end-time-text property
     */
    public final StringProperty endTimeTextProperty() {
        return endTimeText;
    }

    /**
     * Returns the end-time tab text.
     *
     * @return the end-time tab text
     */
    public final String getEndTimeText() {
        return endTimeText.get();
    }

    /**
     * Sets the end-time tab text.
     *
     * @param value the end-time tab text
     */
    public final void setEndTimeText(String value) {
        endTimeText.set(value);
    }
}
