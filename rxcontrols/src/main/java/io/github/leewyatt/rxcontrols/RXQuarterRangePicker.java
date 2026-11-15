package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXQuarterRangePickerSkin;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Skin;
import javafx.util.StringConverter;

import java.util.function.Predicate;

/**
 * Quarter range picker: start and end fields for an {@link RXQuarterRange} with a popup
 * {@link RXQuarterRangeView} showing two years, or as many as
 * {@link #yearCountProperty() yearCount} says.
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
 * classes {@code rx-picker} and {@code rx-quarter-range-picker}; children are two
 * {@code .text-field}s (the focused one {@code :active}, underlined by an
 * {@code .active-bar}) around a {@code .separator}; the popup hosts an
 * {@code .rx-quarter-range-view}.
 */
public class RXQuarterRangePicker extends RXPickerBase<RXQuarterRange> {

    private static final String DEFAULT_STYLE_CLASS = "rx-quarter-range-picker";

    // ==================== Constructors ====================

    /**
     * Creates an empty range picker.
     */
    public RXQuarterRangePicker() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a range picker with the given value.
     *
     * @param value the initial range, or {@code null}
     */
    public RXQuarterRangePicker(RXQuarterRange value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXQuarterRangePickerSkin(this);
    }

    // ==================== Start Prompt Text ====================

    private final StringProperty startPromptText = new SimpleStringProperty(this, "startPromptText", "Start quarter");

    /**
     * Text shown while the start field is empty. Defaults to {@code "Start quarter"}.
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

    private final StringProperty endPromptText = new SimpleStringProperty(this, "endPromptText", "End quarter");

    /**
     * Text shown while the end field is empty. Defaults to {@code "End quarter"}.
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

    private final ObjectProperty<StringConverter<RXYearQuarter>> converter = new SimpleObjectProperty<>(this, "converter");

    /**
     * Converts between each endpoint and its field text. {@code null} (the initial
     * value) uses the {@code 2026-Q1} form. A converter may throw on text it
     * cannot parse; such text is flagged invalid and never committed.
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

    // ==================== Year Count ====================

    private final IntegerProperty yearCount = new SimpleIntegerProperty(this, "yearCount", 2);

    /**
     * Number of consecutive years the popup shows side by side. Values below 1 are
     * treated as 1. Defaults to 2.
     *
     * @return the year-count property
     */
    public final IntegerProperty yearCountProperty() {
        return yearCount;
    }

    /**
     * Returns the number of years the popup shows.
     *
     * @return the number of years the popup shows
     */
    public final int getYearCount() {
        return yearCount.get();
    }

    /**
     * Sets the number of years the popup shows.
     *
     * @param value the number of years the popup shows
     */
    public final void setYearCount(int value) {
        yearCount.set(value);
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
