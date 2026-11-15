package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXWeekPickerSkin;
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
 * Week picker: a text field for a week with a popup {@link RXWeekView}. Picking a week
 * commits it and closes the popup; typed text is committed with Enter or when focus
 * leaves the field.
 *
 * <p>The value is a {@link LocalDate}: a commit sets it to the first day of the week,
 * so the committed week is the seven days starting at the value. A value set to any
 * other day shows the week that contains it. Where a week starts follows the
 * {@link #weekFieldsProperty() week rules} in effect at the commit; under other rules
 * the same date can belong to a different week.
 *
 * <p>See {@link RXPickerBase} for the commit, focus and constraint model, and
 * {@link RXDatePicker} for the keys. CSS: style classes {@code rx-picker} and
 * {@code rx-week-picker}; children {@code .leading}, {@code .text-field} and
 * {@code .suffix} ({@code .icon}, {@code .clear-button}); the popup has style class
 * {@code rx-picker-popup} and hosts an {@code .rx-week-view}. Pseudo-classes:
 * {@code :focused}, {@code :showing}, {@code :editable}, {@code :has-leading},
 * {@code :invalid}.
 */
public class RXWeekPicker extends RXPickerBase<LocalDate> {

    private static final String DEFAULT_STYLE_CLASS = "rx-week-picker";

    // ==================== Constructors ====================

    /**
     * Creates an empty week picker.
     */
    public RXWeekPicker() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.DATE_PICKER);
    }

    /**
     * Creates a week picker with the given value.
     *
     * @param value a day of the initial week, or {@code null}
     */
    public RXWeekPicker(LocalDate value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXWeekPickerSkin(this);
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
     * Converts between the value and the field text. The picker formats the first day of
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
