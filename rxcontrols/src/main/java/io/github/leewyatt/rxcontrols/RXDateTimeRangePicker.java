package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXDateTimeRangePickerSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Skin;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.util.function.Predicate;

/**
 * Date-time range picker: start and end fields for an {@link RXDateTimeRange} with a
 * popup {@link RXDateTimeView} that edits one endpoint at a time.
 *
 * <p>The popup opens on the endpoint of the field it was opened from, marked by the
 * bar under that field, and stays in place whichever endpoint it edits. Picking a day
 * or a time changes that endpoint's draft, which its field previews. The OK button
 * (Enter, or a double click on a time) confirms the endpoint: the popup goes on to the
 * other endpoint, or, once both endpoints have been gone through, commits the range
 * and closes. The other field cannot be entered before the current endpoint is
 * confirmed; Tab confirms the endpoint it leaves. The committed endpoints are put in
 * order, so the end may be picked before the start.
 *
 * <p>Escape discards the drafts. Closing the popup any other way commits them only
 * if a change was confirmed and the endpoint then shown was left as it was;
 * otherwise it discards them. Typed endpoints are committed together when focus
 * leaves the picker, and with Enter while the popup is closed. The endpoints are
 * local date-times without a time zone.
 *
 * <p>See {@link RXPickerBase} for the commit, focus and constraint model. CSS: style
 * classes {@code rx-picker} and {@code rx-date-time-range-picker}; the popup hosts an
 * {@code .rx-date-time-view} and a {@code .footer} with an {@code .ok-button}.
 */
public class RXDateTimeRangePicker extends RXPickerBase<RXDateTimeRange> {

    private static final String DEFAULT_STYLE_CLASS = "rx-date-time-range-picker";

    // ==================== Constructors ====================

    /**
     * Creates an empty date-time range picker.
     */
    public RXDateTimeRangePicker() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a date-time range picker with the given value.
     *
     * @param value the initial range, or {@code null}
     */
    public RXDateTimeRangePicker(RXDateTimeRange value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXDateTimeRangePickerSkin(this);
    }

    // ==================== Start Prompt Text ====================

    private final StringProperty startPromptText = new SimpleStringProperty(this, "startPromptText", "Start");

    /**
     * Text shown while the start field is empty. Defaults to {@code "Start"}.
     *
     * @return the start-prompt-text property
     */
    public final StringProperty startPromptTextProperty() {
        return startPromptText;
    }

    /**
     * Returns the start prompt text.
     *
     * @return the start prompt text
     */
    public final String getStartPromptText() {
        return startPromptText.get();
    }

    /**
     * Sets the start prompt text.
     *
     * @param value the start prompt text
     */
    public final void setStartPromptText(String value) {
        startPromptText.set(value);
    }

    // ==================== End Prompt Text ====================

    private final StringProperty endPromptText = new SimpleStringProperty(this, "endPromptText", "End");

    /**
     * Text shown while the end field is empty. Defaults to {@code "End"}.
     *
     * @return the end-prompt-text property
     */
    public final StringProperty endPromptTextProperty() {
        return endPromptText;
    }

    /**
     * Returns the end prompt text.
     *
     * @return the end prompt text
     */
    public final String getEndPromptText() {
        return endPromptText.get();
    }

    /**
     * Sets the end prompt text.
     *
     * @param value the end prompt text
     */
    public final void setEndPromptText(String value) {
        endPromptText.set(value);
    }

    // ==================== Converter ====================

    private final ObjectProperty<StringConverter<LocalDateTime>> converter = new SimpleObjectProperty<>(this, "converter");

    /**
     * Converts between each endpoint and its field text. {@code null} (the initial
     * value) uses the format of {@link RXDateTimePicker}. A converter may throw on text
     * it cannot parse; such text is flagged invalid and never committed.
     *
     * @return the converter property
     */
    public final ObjectProperty<StringConverter<LocalDateTime>> converterProperty() {
        return converter;
    }

    /**
     * Returns the converter.
     *
     * @return the converter
     */
    public final StringConverter<LocalDateTime> getConverter() {
        return converter.get();
    }

    /**
     * Sets the converter.
     *
     * @param value the converter
     */
    public final void setConverter(StringConverter<LocalDateTime> value) {
        converter.set(value);
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
     * The popup's time columns ask the filter once for each time and keep the answer
     * until another filter instance is set.
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
     * The week rules (first day of the week and week numbering) of the popup calendar.
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
     * Whether the popup calendar shows week numbers. Defaults to {@code false}.
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
     * Whether the popup calendar fills its grid with days of the adjacent months, which
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

    // ==================== Wheel Multiplier ====================

    private final DoubleProperty wheelMultiplier =
            new SimpleDoubleProperty(this, "wheelMultiplier", RXSmoothScrollOptions.DEFAULT_WHEEL_MULTIPLIER);

    /**
     * Multiplier applied to the distance a wheel or trackpad scroll moves a time
     * column in the popup: {@code 2} scrolls twice as far, {@code 0.5} half as far. A change
     * applies from the next scroll on; a column already moving keeps its distance. A
     * non-finite value is treated as {@link RXSmoothScrollOptions#DEFAULT_WHEEL_MULTIPLIER}.
     *
     * @return the wheel-multiplier property
     */
    public final DoubleProperty wheelMultiplierProperty() {
        return wheelMultiplier;
    }

    /**
     * Returns the wheel multiplier.
     *
     * @return the wheel multiplier
     */
    public final double getWheelMultiplier() {
        return wheelMultiplier.get();
    }

    /**
     * Sets the wheel multiplier.
     *
     * @param value the wheel multiplier
     */
    public final void setWheelMultiplier(double value) {
        wheelMultiplier.set(value);
    }

    // ==================== Ok Text ====================

    private final StringProperty okText = new SimpleStringProperty(this, "okText", "OK");

    /**
     * Text of the OK button. Defaults to {@code "OK"}.
     *
     * @return the ok-text property
     */
    public final StringProperty okTextProperty() {
        return okText;
    }

    /**
     * Returns the OK button text.
     *
     * @return the OK button text
     */
    public final String getOkText() {
        return okText.get();
    }

    /**
     * Sets the OK button text.
     *
     * @param value the OK button text
     */
    public final void setOkText(String value) {
        okText.set(value);
    }
}
