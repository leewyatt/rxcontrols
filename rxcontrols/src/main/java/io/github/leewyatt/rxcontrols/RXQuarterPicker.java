package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXQuarterPickerSkin;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Skin;
import javafx.util.StringConverter;

import java.util.function.Predicate;

/**
 * Quarter picker: a text field for an {@link RXYearQuarter} with a popup {@link RXQuarterView}. Picking a
 * quarter commits it and closes the popup; typed text is committed with Enter or when
 * focus leaves the field.
 *
 * <p>See {@link RXPickerBase} for the commit, focus and constraint model. CSS: style
 * classes {@code rx-picker} and {@code rx-quarter-picker}; the popup hosts an
 * {@code .rx-quarter-view}.
 */
public class RXQuarterPicker extends RXPickerBase<RXYearQuarter> {

    private static final String DEFAULT_STYLE_CLASS = "rx-quarter-picker";

    // ==================== Constructors ====================

    /**
     * Creates an empty quarter picker.
     */
    public RXQuarterPicker() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a quarter picker with the given value.
     *
     * @param value the initial value, or {@code null}
     */
    public RXQuarterPicker(RXYearQuarter value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXQuarterPickerSkin(this);
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

    private final ObjectProperty<StringConverter<RXYearQuarter>> converter = new SimpleObjectProperty<>(this, "converter");

    /**
     * Converts between the value and the field text. {@code null} (the initial value)
     * uses the {@code 2026-Q1} form. A converter may throw on text it cannot parse; such text is
     * flagged invalid and never committed.
     *
     * @return the converter property
     */
    public final ObjectProperty<StringConverter<RXYearQuarter>> converterProperty() {
        return converter;
    }

    /**
     * Returns the converter.
     *
     * @return the converter
     */
    public final StringConverter<RXYearQuarter> getConverter() {
        return converter.get();
    }

    /**
     * Sets the converter.
     *
     * @param value the converter
     */
    public final void setConverter(StringConverter<RXYearQuarter> value) {
        converter.set(value);
    }

    // ==================== Min Quarter ====================

    private final ObjectProperty<RXYearQuarter> minQuarter = new SimpleObjectProperty<>(this, "minQuarter");

    /**
     * The earliest selectable quarter, or {@code null} for no lower bound.
     *
     * @return the min-quarter property
     */
    public final ObjectProperty<RXYearQuarter> minQuarterProperty() {
        return minQuarter;
    }

    /**
     * Returns the earliest selectable quarter.
     *
     * @return the earliest selectable quarter
     */
    public final RXYearQuarter getMinQuarter() {
        return minQuarter.get();
    }

    /**
     * Sets the earliest selectable quarter.
     *
     * @param value the earliest selectable quarter
     */
    public final void setMinQuarter(RXYearQuarter value) {
        minQuarter.set(value);
    }

    // ==================== Max Quarter ====================

    private final ObjectProperty<RXYearQuarter> maxQuarter = new SimpleObjectProperty<>(this, "maxQuarter");

    /**
     * The latest selectable quarter, or {@code null} for no upper bound.
     *
     * @return the max-quarter property
     */
    public final ObjectProperty<RXYearQuarter> maxQuarterProperty() {
        return maxQuarter;
    }

    /**
     * Returns the latest selectable quarter.
     *
     * @return the latest selectable quarter
     */
    public final RXYearQuarter getMaxQuarter() {
        return maxQuarter.get();
    }

    /**
     * Sets the latest selectable quarter.
     *
     * @param value the latest selectable quarter
     */
    public final void setMaxQuarter(RXYearQuarter value) {
        maxQuarter.set(value);
    }

    // ==================== Quarter Filter ====================

    private final ObjectProperty<Predicate<RXYearQuarter>> quarterFilter = new SimpleObjectProperty<>(this, "quarterFilter");

    /**
     * Decides which quarters within the bounds are selectable: {@code true} keeps a
     * quarter selectable, {@code false} disables it. {@code null} accepts every quarter.
     *
     * @return the quarter-filter property
     */
    public final ObjectProperty<Predicate<RXYearQuarter>> quarterFilterProperty() {
        return quarterFilter;
    }

    /**
     * Returns the quarter filter.
     *
     * @return the quarter filter
     */
    public final Predicate<RXYearQuarter> getQuarterFilter() {
        return quarterFilter.get();
    }

    /**
     * Sets the quarter filter.
     *
     * @param value the quarter filter
     */
    public final void setQuarterFilter(Predicate<RXYearQuarter> value) {
        quarterFilter.set(value);
    }
}
