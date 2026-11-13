package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXDateRangePickerSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Skin;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.function.Predicate;

/**
 * Date range picker: start and end fields for an {@link RXDateRange} with a popup
 * {@link RXDateRangeView} showing two months.
 *
 * <p>The first pick in the popup marks the start (shown in the start field while the
 * end field takes focus); the second pick completes the range in either order,
 * commits it as a whole and closes the popup. Closing before the second pick
 * discards the half-picked range. Typed endpoints are committed together, with Enter
 * or when focus leaves the picker, and are put in order; Enter in the start field
 * while the end field is empty moves to the end field. Clearing both fields commits
 * {@code null}; a single empty field is not a value and is reverted.
 *
 * <p>See {@link RXPickerBase} for the commit, focus and constraint model. CSS: style
 * classes {@code rx-picker} and {@code rx-date-range-picker}; children are two
 * {@code .text-field}s (the focused one {@code :active}) around a {@code .separator};
 * the popup hosts an {@code .rx-date-range-view}.
 */
public class RXDateRangePicker extends RXPickerBase<RXDateRange> {

    private static final String DEFAULT_STYLE_CLASS = "rx-date-range-picker";

    // ==================== Constructors ====================

    /**
     * Creates an empty range picker.
     */
    public RXDateRangePicker() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a range picker with the given value.
     *
     * @param value the initial range, or {@code null}
     */
    public RXDateRangePicker(RXDateRange value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXDateRangePickerSkin(this);
    }

    // ==================== Start Prompt Text ====================

    private final StringProperty startPromptText = new SimpleStringProperty(this, "startPromptText", "Start date");

    /**
     * Text shown while the start field is empty. Defaults to {@code "Start date"}.
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

    private final StringProperty endPromptText = new SimpleStringProperty(this, "endPromptText", "End date");

    /**
     * Text shown while the end field is empty. Defaults to {@code "End date"}.
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

    private final ObjectProperty<StringConverter<LocalDate>> converter = new SimpleObjectProperty<>(this, "converter");

    /**
     * Converts between each endpoint and its field text. {@code null} (the initial
     * value) uses the locale's short date format with a four-digit year. A converter
     * may throw on text it cannot parse; such text is flagged invalid and never
     * committed.
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
}
