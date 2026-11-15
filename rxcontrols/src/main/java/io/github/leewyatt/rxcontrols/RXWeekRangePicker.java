package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXWeekRangePickerSkin;
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

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.function.Predicate;

/**
 * Week range picker: start and end fields for a range of whole weeks with a popup
 * {@link RXWeekRangeView} showing two months, or as many as
 * {@link #monthCountProperty() monthCount} says.
 *
 * <p>The value is an {@link RXDateRange}: a commit sets it to the days from the first
 * day of the first week to the last day of the last week. A value with other
 * endpoints shows the weeks that contain them. Where a week starts follows the
 * {@link #weekFieldsProperty() week rules}.
 *
 * <p>The first pick in the popup marks the first week (shown in the start field while
 * the end field takes focus); the second pick completes the range in either order,
 * commits it as a whole and closes the popup. Closing before the second pick
 * discards the half-picked range. Typed weeks are committed together, with Enter or
 * when focus leaves the picker, and are put in order; Enter in the start field while
 * the end field is empty moves to the end field. Clearing both fields commits
 * {@code null}; a single empty field is not a value and is reverted.
 *
 * <p>See {@link RXPickerBase} for the commit, focus and constraint model. CSS: style
 * classes {@code rx-picker} and {@code rx-week-range-picker}; children are two
 * {@code .text-field}s (the focused one {@code :active}, underlined by an
 * {@code .active-bar}) around a {@code .separator}; the popup hosts an
 * {@code .rx-week-range-view}.
 */
public class RXWeekRangePicker extends RXPickerBase<RXDateRange> {

    private static final String DEFAULT_STYLE_CLASS = "rx-week-range-picker";

    // ==================== Constructors ====================

    /**
     * Creates an empty week range picker.
     */
    public RXWeekRangePicker() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a week range picker with the given value.
     *
     * @param value the initial range, or {@code null}
     */
    public RXWeekRangePicker(RXDateRange value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXWeekRangePickerSkin(this);
    }

    // ==================== Start Prompt Text ====================

    private final StringProperty startPromptText = new SimpleStringProperty(this, "startPromptText", "Start week");

    /**
     * Text shown while the start field is empty. Defaults to {@code "Start week"}.
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

    private final StringProperty endPromptText = new SimpleStringProperty(this, "endPromptText", "End week");

    /**
     * Text shown while the end field is empty. Defaults to {@code "End week"}.
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
     * Converts between each endpoint week and its field text. The picker formats the first day of
     * a week, and takes the week of the date the converter parses. {@code null} (the
     * initial value) uses the {@code 2026-W46} form: the week-based year and the week
     * number of the {@link #weekFieldsProperty() week rules}. A converter may throw on
     * text it cannot parse; such text is flagged invalid and never committed.
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
     * @param value the converter, or {@code null} for the default form
     */
    public final void setConverter(StringConverter<LocalDate> value) {
        converter.set(value);
    }

    // ==================== Month Count ====================

    private final IntegerProperty monthCount = new SimpleIntegerProperty(this, "monthCount", 2);

    /**
     * Number of consecutive months the popup shows side by side. Values below 1 are
     * treated as 1. Defaults to 2.
     *
     * @return the month-count property
     */
    public final IntegerProperty monthCountProperty() {
        return monthCount;
    }

    /**
     * Returns the number of months the popup shows.
     *
     * @return the number of months the popup shows
     */
    public final int getMonthCount() {
        return monthCount.get();
    }

    /**
     * Sets the number of months the popup shows.
     *
     * @param value the number of months the popup shows
     */
    public final void setMonthCount(int value) {
        monthCount.set(value);
    }

    // ==================== Min Week ====================

    private final ObjectProperty<LocalDate> minWeek = new SimpleObjectProperty<>(this, "minWeek");

    /**
     * The earliest selectable week, given as any of its days, or {@code null} for no
     * lower bound.
     *
     * @return the min-week property
     */
    public final ObjectProperty<LocalDate> minWeekProperty() {
        return minWeek;
    }

    /**
     * Returns a day of the earliest selectable week.
     *
     * @return a day of the earliest selectable week
     */
    public final LocalDate getMinWeek() {
        return minWeek.get();
    }

    /**
     * Sets a day of the earliest selectable week.
     *
     * @param value a day of the earliest selectable week, or {@code null}
     */
    public final void setMinWeek(LocalDate value) {
        minWeek.set(value);
    }

    // ==================== Max Week ====================

    private final ObjectProperty<LocalDate> maxWeek = new SimpleObjectProperty<>(this, "maxWeek");

    /**
     * The latest selectable week, given as any of its days, or {@code null} for no
     * upper bound.
     *
     * @return the max-week property
     */
    public final ObjectProperty<LocalDate> maxWeekProperty() {
        return maxWeek;
    }

    /**
     * Returns a day of the latest selectable week.
     *
     * @return a day of the latest selectable week
     */
    public final LocalDate getMaxWeek() {
        return maxWeek.get();
    }

    /**
     * Sets a day of the latest selectable week.
     *
     * @param value a day of the latest selectable week, or {@code null}
     */
    public final void setMaxWeek(LocalDate value) {
        maxWeek.set(value);
    }

    // ==================== Week Filter ====================

    private final ObjectProperty<Predicate<LocalDate>> weekFilter = new SimpleObjectProperty<>(this, "weekFilter");

    /**
     * Decides which weeks within the bounds are selectable. It is asked with the first
     * day of a week: {@code true} keeps the week selectable, {@code false} disables it.
     * {@code null} accepts every week.
     *
     * @return the week-filter property
     */
    public final ObjectProperty<Predicate<LocalDate>> weekFilterProperty() {
        return weekFilter;
    }

    /**
     * Returns the week filter.
     *
     * @return the week filter
     */
    public final Predicate<LocalDate> getWeekFilter() {
        return weekFilter.get();
    }

    /**
     * Sets the week filter.
     *
     * @param value the filter, or {@code null} to accept every week
     */
    public final void setWeekFilter(Predicate<LocalDate> value) {
        weekFilter.set(value);
    }

    // ==================== Week Fields ====================

    private final ObjectProperty<WeekFields> weekFields = new SimpleObjectProperty<>(this, "weekFields");

    /**
     * The week rules: the first day of the week, which is where a week starts, and the
     * week numbering. {@code null} (the initial value) uses the rules of the locale.
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
     * @param value the week fields, or {@code null} for the locale's rules
     */
    public final void setWeekFields(WeekFields value) {
        weekFields.set(value);
    }

    // ==================== Show Week Numbers ====================

    private final BooleanProperty showWeekNumbers = new SimpleBooleanProperty(this, "showWeekNumbers", true);

    /**
     * Whether a week-number column is shown, numbered by the
     * {@link #weekFieldsProperty() week rules}. Clicking a week number picks its week.
     * Defaults to {@code true}.
     *
     * @return the show-week-numbers property
     */
    public final BooleanProperty showWeekNumbersProperty() {
        return showWeekNumbers;
    }

    /**
     * Returns whether week numbers are shown.
     *
     * @return {@code true} if week numbers are shown
     */
    public final boolean isShowWeekNumbers() {
        return showWeekNumbers.get();
    }

    /**
     * Sets whether week numbers are shown.
     *
     * @param value {@code true} to show week numbers
     */
    public final void setShowWeekNumbers(boolean value) {
        showWeekNumbers.set(value);
    }
}
