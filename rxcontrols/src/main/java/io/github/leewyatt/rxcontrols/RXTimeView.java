package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXTimeViewSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Skin;

import java.time.LocalTime;
import java.util.function.Predicate;

/**
 * Scrolling columns for picking a {@link LocalTime}: hours, minutes, optional seconds
 * and an optional AM / PM column. Usable on its own or as the content of
 * {@link RXTimePicker}'s popup.
 *
 * <p>Clicking an item changes that unit at once; the other units snap to the nearest
 * selectable time, so the value is always selectable after a pick. Items no
 * selectable time uses are disabled: an hour stays enabled while any of its minutes
 * is selectable. The value has whole-second precision.
 *
 * <p>Picks only update the value; the {@link #onActionProperty() action} fires when
 * the user confirms with Enter / Space (keyboard cursor shown) or double-clicks an
 * item.
 *
 * <p><b>Keyboard</b> (while focused): the first arrow key shows the cursor on the
 * hour column; Left / Right choose the column (mirrored under right-to-left
 * orientation), Up / Down move its selection, Page Up / Page Down move five items,
 * Home / End jump to the first / last item, Enter / Space confirm.
 *
 * <p>CSS: style class {@code rx-time-view}; columns are {@code .time-column} scroll
 * panes ({@code .hour-column}, {@code .minute-column}, {@code .second-column},
 * {@code .meridiem-column}, {@code :active} for the keyboard column) of
 * {@code .time-cell} labels with {@code :selected}, {@code :focused} and
 * {@code :disabled}. The root has {@code :invalid} while the value violates the
 * constraints.
 */
public class RXTimeView extends RXTemporalViewBase<LocalTime> {

    private static final String DEFAULT_STYLE_CLASS = "rx-time-view";

    // ==================== Constructors ====================

    /**
     * Creates a time view with no selection.
     */
    public RXTimeView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a time view with the given selection.
     *
     * @param value the initial value, or {@code null}
     */
    public RXTimeView(LocalTime value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXTimeViewSkin(this);
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
}
