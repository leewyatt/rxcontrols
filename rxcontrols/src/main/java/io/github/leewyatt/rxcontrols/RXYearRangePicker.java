package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXYearRangePickerSkin;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Skin;
import javafx.util.StringConverter;

import java.time.Year;
import java.util.function.Predicate;

/**
 * Year range picker: start and end fields for an {@link RXYearRange} with a popup
 * {@link RXYearRangeView} showing two decades, or as many as
 * {@link #decadeCountProperty() decadeCount} says.
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
 * classes {@code rx-picker} and {@code rx-year-range-picker}; children are two
 * {@code .text-field}s (the focused one {@code :active}, underlined by an
 * {@code .active-bar}) around a {@code .separator}; the popup hosts an
 * {@code .rx-year-range-view}.
 */
public class RXYearRangePicker extends RXPickerBase<RXYearRange> {

    private static final String DEFAULT_STYLE_CLASS = "rx-year-range-picker";

    // ==================== Constructors ====================

    /**
     * Creates an empty range picker.
     */
    public RXYearRangePicker() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a range picker with the given value.
     *
     * @param value the initial range, or {@code null}
     */
    public RXYearRangePicker(RXYearRange value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXYearRangePickerSkin(this);
    }

    // ==================== Start Prompt Text ====================

    private final StringProperty startPromptText = new SimpleStringProperty(this, "startPromptText", "Start year");

    /**
     * Text shown while the start field is empty. Defaults to {@code "Start year"}.
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

    private final StringProperty endPromptText = new SimpleStringProperty(this, "endPromptText", "End year");

    /**
     * Text shown while the end field is empty. Defaults to {@code "End year"}.
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

    private final ObjectProperty<StringConverter<Year>> converter = new SimpleObjectProperty<>(this, "converter");

    /**
     * Converts between each endpoint and its field text. {@code null} (the initial
     * value) uses {@code uuuu}. A converter may throw on text it
     * cannot parse; such text is flagged invalid and never committed.
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

    // ==================== Decade Count ====================

    private final IntegerProperty decadeCount = new SimpleIntegerProperty(this, "decadeCount", 2);

    /**
     * Number of consecutive decades the popup shows side by side. Values below 1 are
     * treated as 1. Defaults to 2.
     *
     * @return the decade-count property
     */
    public final IntegerProperty decadeCountProperty() {
        return decadeCount;
    }

    /**
     * Returns the number of decades the popup shows.
     *
     * @return the number of decades the popup shows
     */
    public final int getDecadeCount() {
        return decadeCount.get();
    }

    /**
     * Sets the number of decades the popup shows.
     *
     * @param value the number of decades the popup shows
     */
    public final void setDecadeCount(int value) {
        decadeCount.set(value);
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
