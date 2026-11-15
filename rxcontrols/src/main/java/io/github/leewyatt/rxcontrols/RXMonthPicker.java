package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXMonthPickerSkin;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Skin;
import javafx.util.StringConverter;

import java.time.YearMonth;
import java.util.function.Predicate;

/**
 * Month picker: a text field for a {@link YearMonth} with a popup {@link RXMonthView}. Picking a
 * month commits it and closes the popup; typed text is committed with Enter or when
 * focus leaves the field.
 *
 * <p>See {@link RXPickerBase} for the commit, focus and constraint model. CSS: style
 * classes {@code rx-picker} and {@code rx-month-picker}; the popup hosts an
 * {@code .rx-month-view}.
 */
public class RXMonthPicker extends RXPickerBase<YearMonth> {

    private static final String DEFAULT_STYLE_CLASS = "rx-month-picker";

    // ==================== Constructors ====================

    /**
     * Creates an empty month picker.
     */
    public RXMonthPicker() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a month picker with the given value.
     *
     * @param value the initial value, or {@code null}
     */
    public RXMonthPicker(YearMonth value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXMonthPickerSkin(this);
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

    private final ObjectProperty<StringConverter<YearMonth>> converter = new SimpleObjectProperty<>(this, "converter");

    /**
     * Converts between the value and the field text. {@code null} (the initial value)
     * uses {@code uuuu-MM}, for example {@code 2026-03}. A converter may throw on text it cannot parse; such text is
     * flagged invalid and never committed.
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
