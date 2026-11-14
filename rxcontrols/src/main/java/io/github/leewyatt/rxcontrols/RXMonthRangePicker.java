package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXMonthRangePickerSkin;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Skin;
import javafx.util.StringConverter;

import java.time.YearMonth;
import java.util.function.Predicate;

/**
 * Month range picker: start and end fields for an {@link RXMonthRange} with a popup
 * {@link RXMonthRangeView} showing two years.
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
 * classes {@code rx-picker} and {@code rx-month-range-picker}; children are two
 * {@code .text-field}s (the focused one {@code :active}, underlined by an
 * {@code .active-bar}) around a {@code .separator}; the popup hosts an
 * {@code .rx-month-range-view}.
 */
public class RXMonthRangePicker extends RXPickerBase<RXMonthRange> {

    private static final String DEFAULT_STYLE_CLASS = "rx-month-range-picker";

    // ==================== Constructors ====================

    /**
     * Creates an empty range picker.
     */
    public RXMonthRangePicker() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a range picker with the given value.
     *
     * @param value the initial range, or {@code null}
     */
    public RXMonthRangePicker(RXMonthRange value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXMonthRangePickerSkin(this);
    }

    // ==================== Start Prompt Text ====================

    private final StringProperty startPromptText = new SimpleStringProperty(this, "startPromptText", "Start month");

    /**
     * Text shown while the start field is empty. Defaults to {@code "Start month"}.
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

    private final StringProperty endPromptText = new SimpleStringProperty(this, "endPromptText", "End month");

    /**
     * Text shown while the end field is empty. Defaults to {@code "End month"}.
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

    private final ObjectProperty<StringConverter<YearMonth>> converter = new SimpleObjectProperty<>(this, "converter");

    /**
     * Converts between each endpoint and its field text. {@code null} (the initial
     * value) uses {@code uuuu-MM}, for example {@code 2026-03}. A converter may throw on text it
     * cannot parse; such text is flagged invalid and never committed.
     *
     * @return the converter property
     */
    public final ObjectProperty<StringConverter<YearMonth>> converterProperty() {
        return converter;
    }

    /**
     * Returns the converter.
     *
     * @return the converter
     */
    public final StringConverter<YearMonth> getConverter() {
        return converter.get();
    }

    /**
     * Sets the converter.
     *
     * @param value the converter
     */
    public final void setConverter(StringConverter<YearMonth> value) {
        converter.set(value);
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
