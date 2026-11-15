package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXYearPickerSkin;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Skin;
import javafx.util.StringConverter;

import java.time.Year;
import java.util.function.Predicate;

/**
 * Year picker: a text field for a {@link Year} with a popup {@link RXYearView}. Picking a
 * year commits it and closes the popup; typed text is committed with Enter or when
 * focus leaves the field.
 *
 * <p>See {@link RXPickerBase} for the commit, focus and constraint model. CSS: style
 * classes {@code rx-picker} and {@code rx-year-picker}; the popup hosts an
 * {@code .rx-year-view}.
 */
public class RXYearPicker extends RXPickerBase<Year> {

    private static final String DEFAULT_STYLE_CLASS = "rx-year-picker";

    // ==================== Constructors ====================

    /**
     * Creates an empty year picker.
     */
    public RXYearPicker() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a year picker with the given value.
     *
     * @param value the initial value, or {@code null}
     */
    public RXYearPicker(Year value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXYearPickerSkin(this);
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

    private final ObjectProperty<StringConverter<Year>> converter = new SimpleObjectProperty<>(this, "converter");

    /**
     * Converts between the value and the field text. {@code null} (the initial value)
     * uses {@code uuuu}. A converter may throw on text it cannot parse; such text is
     * flagged invalid and never committed.
     *
     * @return the converter property
     */
    public final ObjectProperty<StringConverter<Year>> converterProperty() {
        return converter;
    }

    /**
     * Returns the converter.
     *
     * @return the converter
     */
    public final StringConverter<Year> getConverter() {
        return converter.get();
    }

    /**
     * Sets the converter.
     *
     * @param value the converter
     */
    public final void setConverter(StringConverter<Year> value) {
        converter.set(value);
    }

    // ==================== Min Year ====================

    private final ObjectProperty<Year> minYear = new SimpleObjectProperty<>(this, "minYear");

    /**
     * The earliest selectable year, or {@code null} for no lower bound.
     *
     * @return the min-year property
     */
    public final ObjectProperty<Year> minYearProperty() {
        return minYear;
    }

    /**
     * Returns the earliest selectable year.
     *
     * @return the earliest selectable year
     */
    public final Year getMinYear() {
        return minYear.get();
    }

    /**
     * Sets the earliest selectable year.
     *
     * @param value the earliest selectable year
     */
    public final void setMinYear(Year value) {
        minYear.set(value);
    }

    // ==================== Max Year ====================

    private final ObjectProperty<Year> maxYear = new SimpleObjectProperty<>(this, "maxYear");

    /**
     * The latest selectable year, or {@code null} for no upper bound.
     *
     * @return the max-year property
     */
    public final ObjectProperty<Year> maxYearProperty() {
        return maxYear;
    }

    /**
     * Returns the latest selectable year.
     *
     * @return the latest selectable year
     */
    public final Year getMaxYear() {
        return maxYear.get();
    }

    /**
     * Sets the latest selectable year.
     *
     * @param value the latest selectable year
     */
    public final void setMaxYear(Year value) {
        maxYear.set(value);
    }

    // ==================== Year Filter ====================

    private final ObjectProperty<Predicate<Year>> yearFilter = new SimpleObjectProperty<>(this, "yearFilter");

    /**
     * Decides which years within the bounds are selectable: {@code true} keeps a
     * year selectable, {@code false} disables it. {@code null} accepts every year.
     *
     * @return the year-filter property
     */
    public final ObjectProperty<Predicate<Year>> yearFilterProperty() {
        return yearFilter;
    }

    /**
     * Returns the year filter.
     *
     * @return the year filter
     */
    public final Predicate<Year> getYearFilter() {
        return yearFilter.get();
    }

    /**
     * Sets the year filter.
     *
     * @param value the year filter
     */
    public final void setYearFilter(Predicate<Year> value) {
        yearFilter.set(value);
    }
}
