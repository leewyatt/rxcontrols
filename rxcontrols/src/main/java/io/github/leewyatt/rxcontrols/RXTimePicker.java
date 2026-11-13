package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXTimePickerSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Skin;
import javafx.util.StringConverter;

import java.time.LocalTime;
import java.util.function.Predicate;

/**
 * Time picker: a text field for a {@link LocalTime} with a popup {@link RXTimeView}.
 *
 * <p>Picking in the popup edits a draft that the field previews; the draft is
 * committed by the OK button, Enter, or a double click on an item, and discarded when
 * the popup closes any other way. The Now button commits the current time at once.
 * Typed text is committed with Enter or when focus leaves the field. Typed and
 * programmatic values must satisfy {@link #minTimeProperty() minTime},
 * {@link #maxTimeProperty() maxTime} and the {@link #timeFilterProperty() timeFilter};
 * the steps only shape what the popup columns offer.
 *
 * <p>See {@link RXPickerBase} for the commit, focus and constraint model. CSS: style
 * classes {@code rx-picker} and {@code rx-time-picker}; the popup hosts an
 * {@code .rx-time-view} and a {@code .footer} with {@code .now-button} and
 * {@code .ok-button}.
 */
public class RXTimePicker extends RXPickerBase<LocalTime> {

    private static final String DEFAULT_STYLE_CLASS = "rx-time-picker";

    // ==================== Constructors ====================

    /**
     * Creates an empty time picker.
     */
    public RXTimePicker() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a time picker with the given value.
     *
     * @param value the initial value, or {@code null}
     */
    public RXTimePicker(LocalTime value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXTimePickerSkin(this);
    }

    // ==================== Prompt Text ====================

    private final StringProperty promptText = new SimpleStringProperty(this, "promptText", "");

    /**
     * Text shown while the field is empty.
     *
     * @return the prompt-text property
     */
    public final StringProperty promptTextProperty() {
        return promptText;
    }

    /**
     * Returns the prompt text.
     *
     * @return the prompt text
     */
    public final String getPromptText() {
        return promptText.get();
    }

    /**
     * Sets the prompt text.
     *
     * @param value the prompt text
     */
    public final void setPromptText(String value) {
        promptText.set(value);
    }

    // ==================== Converter ====================

    private final ObjectProperty<StringConverter<LocalTime>> converter = new SimpleObjectProperty<>(this, "converter");

    /**
     * Converts between the value and the field text. {@code null} (the initial value)
     * uses {@code HH:mm}, with {@code :ss} when seconds are shown and
     * {@code hh:mm a} on a 12-hour clock. A converter may throw on text it cannot parse; such text is
     * flagged invalid and never committed.
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

    // ==================== Show Now ====================

    private final BooleanProperty showNow = new SimpleBooleanProperty(this, "showNow", true);

    /**
     * Whether the popup shows a Now button that commits the current time, truncated to
     * the steps. The button is disabled when that time is not selectable. Defaults to
     * {@code true}.
     *
     * @return the show-now property
     */
    public final BooleanProperty showNowProperty() {
        return showNow;
    }

    /**
     * Returns whether the Now button is shown.
     *
     * @return whether the Now button is shown
     */
    public final boolean isShowNow() {
        return showNow.get();
    }

    /**
     * Sets whether the Now button is shown.
     *
     * @param value whether the Now button is shown
     */
    public final void setShowNow(boolean value) {
        showNow.set(value);
    }

    // ==================== Now Text ====================

    private final StringProperty nowText = new SimpleStringProperty(this, "nowText", "Now");

    /**
     * Text of the Now button. Defaults to {@code "Now"}.
     *
     * @return the now-text property
     */
    public final StringProperty nowTextProperty() {
        return nowText;
    }

    /**
     * Returns the Now button text.
     *
     * @return the Now button text
     */
    public final String getNowText() {
        return nowText.get();
    }

    /**
     * Sets the Now button text.
     *
     * @param value the Now button text
     */
    public final void setNowText(String value) {
        nowText.set(value);
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
