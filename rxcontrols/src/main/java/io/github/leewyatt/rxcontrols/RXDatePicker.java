package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXDatePickerSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Skin;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.function.Predicate;

/**
 * Date picker: a text field for a {@link LocalDate} with a popup
 * {@link RXCalendarView}. Picking a day commits it and closes the popup; typed text
 * is committed with Enter or when focus leaves the field.
 *
 * <p>See {@link RXPickerBase} for the commit, focus and constraint model. Keys while
 * the field is focused: Alt+Down / Alt+Up / F4 toggle the popup, Escape closes it
 * (discarding the draft) or reverts typed text; while the popup is open, Up / Down /
 * Page Up / Page Down move the calendar's keyboard cursor, after which Left / Right /
 * Home / End and Enter work on the calendar too.
 *
 * <p>CSS: style classes {@code rx-picker} and {@code rx-date-picker}; children
 * {@code .text-field} and {@code .suffix} ({@code .icon}, {@code .clear-button}); the
 * popup has style class {@code rx-picker-popup} and hosts an {@code .rx-calendar-view}
 * plus a {@code .footer}. Pseudo-classes: {@code :focused}, {@code :showing},
 * {@code :editable}, {@code :invalid}. The popup calendar declares its own color
 * tokens, so a token override meant for both must also target
 * {@code .rx-picker-popup .rx-calendar-view}.
 */
public class RXDatePicker extends RXPickerBase<LocalDate> {

    private static final String DEFAULT_STYLE_CLASS = "rx-date-picker";

    // ==================== Constructors ====================

    /**
     * Creates an empty date picker.
     */
    public RXDatePicker() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.DATE_PICKER);
    }

    /**
     * Creates a date picker with the given value.
     *
     * @param value the initial value, or {@code null}
     */
    public RXDatePicker(LocalDate value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXDatePickerSkin(this);
    }

    /** {@inheritDoc} */
    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        // The DATE_PICKER role makes assistive technology query DATE as a LocalDate.
        if (attribute == AccessibleAttribute.DATE) {
            return getValue();
        }
        return super.queryAccessibleAttribute(attribute, parameters);
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

    private final ObjectProperty<StringConverter<LocalDate>> converter = new SimpleObjectProperty<>(this, "converter");

    /**
     * Converts between the value and the field text. {@code null} (the initial value)
     * uses the locale's short date format with a four-digit year; parsing
     * also accepts single-digit days and months. A converter may throw on text it cannot parse; such text is
     * flagged invalid and never committed.
     *
     * @return the converter property
     */
    public final ObjectProperty<StringConverter<LocalDate>> converterProperty() {
        return converter;
    }

    /**
     * Returns the converter.
     *
     * @return the converter
     */
    public final StringConverter<LocalDate> getConverter() {
        return converter.get();
    }

    /**
     * Sets the converter.
     *
     * @param value the converter
     */
    public final void setConverter(StringConverter<LocalDate> value) {
        converter.set(value);
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

    // ==================== Show Today ====================

    private final BooleanProperty showToday = new SimpleBooleanProperty(this, "showToday", true);

    /**
     * Whether the popup shows a Today button that commits today's date. The button is
     * disabled when today is not selectable. Defaults to {@code true}.
     *
     * @return the show-today property
     */
    public final BooleanProperty showTodayProperty() {
        return showToday;
    }

    /**
     * Returns whether the Today button is shown.
     *
     * @return whether the Today button is shown
     */
    public final boolean isShowToday() {
        return showToday.get();
    }

    /**
     * Sets whether the Today button is shown.
     *
     * @param value whether the Today button is shown
     */
    public final void setShowToday(boolean value) {
        showToday.set(value);
    }

    // ==================== Today Text ====================

    private final StringProperty todayText = new SimpleStringProperty(this, "todayText", "Today");

    /**
     * Text of the Today button. Defaults to {@code "Today"}.
     *
     * @return the today-text property
     */
    public final StringProperty todayTextProperty() {
        return todayText;
    }

    /**
     * Returns the Today button text.
     *
     * @return the Today button text
     */
    public final String getTodayText() {
        return todayText.get();
    }

    /**
     * Sets the Today button text.
     *
     * @param value the Today button text
     */
    public final void setTodayText(String value) {
        todayText.set(value);
    }
}
