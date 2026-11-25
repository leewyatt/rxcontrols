package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXTimeRangePickerSkin;
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

import java.time.LocalTime;
import java.util.function.Predicate;

/**
 * Time range picker: start and end fields for an {@link RXTimeRange} with a popup
 * {@link RXTimeView} that edits one endpoint at a time.
 *
 * <p>The popup opens on the endpoint of the field it was opened from, marked by the
 * bar under that field, and sits under the field of the endpoint it edits. Picking a
 * time changes that endpoint's draft, which its field previews. The OK button (Enter,
 * or a double click on a time) confirms the endpoint: the popup moves on to the other
 * endpoint, or, once both endpoints have been gone through, commits the range and
 * closes. An endpoint without a draft is picked starting from the other endpoint's
 * time. Clicking the other field moves the popup on to that endpoint and keeps the
 * draft of the one left, unless the field left holds typed text that is no acceptable
 * endpoint. Tab confirms the endpoint it leaves.
 *
 * <p>The committed endpoints are put in order, so the end may be picked before the
 * start. With {@link #orderedProperty() ordered} off they are committed as picked,
 * and a start after the end is a range that runs past midnight.
 *
 * <p>Escape discards the drafts. Closing the popup any other way commits them only
 * if both endpoints have been gone through, a draft was changed, and the endpoint
 * then shown was left as it was since the popup moved to it; otherwise it discards
 * them. Typed endpoints are committed together when focus leaves the picker, and with
 * Enter while the popup is closed. Typed and programmatic endpoints must satisfy
 * {@link #minTimeProperty() minTime}, {@link #maxTimeProperty() maxTime} and the
 * {@link #timeFilterProperty() timeFilter}; the steps only shape what the popup
 * columns offer.
 *
 * <p>See {@link RXPickerBase} for the commit, focus and constraint model. CSS: style
 * classes {@code rx-picker} and {@code rx-time-range-picker}; the popup hosts an
 * {@code .rx-time-view} and a {@code .footer} with an {@code .ok-button}.
 */
public class RXTimeRangePicker extends RXPickerBase<RXTimeRange> {

    private static final String DEFAULT_STYLE_CLASS = "rx-time-range-picker";

    // ==================== Constructors ====================

    /**
     * Creates an empty time range picker.
     */
    public RXTimeRangePicker() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a time range picker with the given value.
     *
     * @param value the initial range, or {@code null}
     */
    public RXTimeRangePicker(RXTimeRange value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXTimeRangePickerSkin(this);
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

    // ==================== Ordered ====================

    private final BooleanProperty ordered = new SimpleBooleanProperty(this, "ordered", true);

    /**
     * Whether the endpoints the user commits are put in order. Defaults to
     * {@code true}. When {@code false} they are committed as picked or typed, and a
     * start after the end is a range that runs past midnight. A value set by code is
     * never reordered.
     *
     * @return the ordered property
     */
    public final BooleanProperty orderedProperty() {
        return ordered;
    }

    /**
     * Returns whether committed endpoints are put in order.
     *
     * @return whether committed endpoints are put in order
     */
    public final boolean isOrdered() {
        return ordered.get();
    }

    /**
     * Sets whether committed endpoints are put in order.
     *
     * @param value whether committed endpoints are put in order
     */
    public final void setOrdered(boolean value) {
        ordered.set(value);
    }

    // ==================== Converter ====================

    private final ObjectProperty<StringConverter<LocalTime>> converter = new SimpleObjectProperty<>(this, "converter");

    /**
     * Converts between each endpoint and its field text. {@code null} (the initial
     * value) uses the format of {@link RXTimePicker}. A converter may throw on text it
     * cannot parse; such text is flagged invalid and never committed.
     *
     * @return the converter property
     */
    public final ObjectProperty<StringConverter<LocalTime>> converterProperty() {
        return converter;
    }

    /**
     * Returns the converter.
     *
     * @return the converter
     */
    public final StringConverter<LocalTime> getConverter() {
        return converter.get();
    }

    /**
     * Sets the converter.
     *
     * @param value the converter
     */
    public final void setConverter(StringConverter<LocalTime> value) {
        converter.set(value);
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

    // ==================== Min Time ====================

    private final ObjectProperty<LocalTime> minTime = new SimpleObjectProperty<>(this, "minTime");

    /**
     * The earliest selectable time, or {@code null} for no lower bound.
     *
     * @return the min-time property
     */
    public final ObjectProperty<LocalTime> minTimeProperty() {
        return minTime;
    }

    /**
     * Returns the earliest selectable time.
     *
     * @return the earliest selectable time
     */
    public final LocalTime getMinTime() {
        return minTime.get();
    }

    /**
     * Sets the earliest selectable time.
     *
     * @param value the earliest selectable time
     */
    public final void setMinTime(LocalTime value) {
        minTime.set(value);
    }

    // ==================== Max Time ====================

    private final ObjectProperty<LocalTime> maxTime = new SimpleObjectProperty<>(this, "maxTime");

    /**
     * The latest selectable time, or {@code null} for no upper bound.
     *
     * @return the max-time property
     */
    public final ObjectProperty<LocalTime> maxTimeProperty() {
        return maxTime;
    }

    /**
     * Returns the latest selectable time.
     *
     * @return the latest selectable time
     */
    public final LocalTime getMaxTime() {
        return maxTime.get();
    }

    /**
     * Sets the latest selectable time.
     *
     * @param value the latest selectable time
     */
    public final void setMaxTime(LocalTime value) {
        maxTime.set(value);
    }

    // ==================== Time Filter ====================

    private final ObjectProperty<Predicate<LocalTime>> timeFilter = new SimpleObjectProperty<>(this, "timeFilter");

    /**
     * Decides which times within the bounds are selectable: {@code true} keeps a
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
