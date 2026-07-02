package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXAutoCompleteSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Skin;
import javafx.util.StringConverter;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Text field with a local, synchronous autocomplete dropdown. As the user types,
 * {@link #getSuggestions() suggestions} are filtered by
 * {@link #filterFunctionProperty() filterFunction} and shown in an anchored popup;
 * choosing an item (mouse or keyboard) runs {@link #onAutoCompletedProperty()
 * onAutoCompleted}, which by default writes the item back into the field.
 *
 * <p>Focus stays in the editor while the popup is open — the dropdown is a passive
 * highlight surface driven by Down / Up / Enter / Escape. This is a minimal,
 * String-valued consumer of the shared suggestion-popup infrastructure; richer
 * value types, remote providers, and chip/tag inputs are separate controls.
 */
public class RXAutoComplete extends RXTextField {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-auto-complete";

    /** Default filter: case-insensitive substring match against each suggestion. */
    public static final Function<String, Predicate<String>> DEFAULT_FILTER_FUNCTION =
            query -> {
                String needle = (query == null ? "" : query).toLowerCase(Locale.ROOT);
                return candidate -> candidate != null
                        && candidate.toLowerCase(Locale.ROOT).contains(needle);
            };

    /** Default {@link #visibleRowCountProperty() visible row count}; matches the suggestion-popup default. */
    public static final int DEFAULT_VISIBLE_ROW_COUNT = 8;

    // ==================== Constructors ====================

    /**
     * Creates an empty autocomplete field.
     */
    public RXAutoComplete() {
        this(null);
    }

    /**
     * Creates an autocomplete field with initial text.
     *
     * @param text the initial text, or {@code null}
     */
    public RXAutoComplete(String text) {
        super(text);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXAutoCompleteSkin(this);
    }

    // ==================== Suggestions ====================

    private final ObservableList<String> suggestions = FXCollections.observableArrayList();

    /**
     * The live list of candidate suggestions. Mutate it in place; the popup tracks
     * changes.
     *
     * @return the mutable suggestions list
     */
    public final ObservableList<String> getSuggestions() {
        return suggestions;
    }

    // ==================== Filter Function ====================

    private final ObjectProperty<Function<String, Predicate<String>>> filterFunction =
            new SimpleObjectProperty<>(this, "filterFunction", DEFAULT_FILTER_FUNCTION);

    /**
     * Maps the current query text to a predicate selecting matching suggestions.
     * A {@code null} value (or a {@code null} predicate result) is treated as
     * {@link #DEFAULT_FILTER_FUNCTION} / show-all by the skin.
     *
     * @return the filter-function property
     */
    public final ObjectProperty<Function<String, Predicate<String>>> filterFunctionProperty() {
        return filterFunction;
    }

    /**
     * Returns the filter function.
     *
     * @return the filter function, or {@code null}
     */
    public final Function<String, Predicate<String>> getFilterFunction() {
        return filterFunction.get();
    }

    /**
     * Sets the filter function.
     *
     * @param value the filter function, or {@code null} for the default
     */
    public final void setFilterFunction(Function<String, Predicate<String>> value) {
        filterFunction.set(value);
    }

    // ==================== On Auto Completed ====================

    private final ObjectProperty<Consumer<String>> onAutoCompleted =
            new SimpleObjectProperty<>(this, "onAutoCompleted", item -> {
                String text = item == null ? "" : item;
                setText(text);
                positionCaret(text.length());
            });

    /**
     * Invoked with the chosen suggestion when the user commits one. The default
     * writes the item into the field and moves the caret to the end; replace it to
     * customize write-back. A {@code null} value disables commit handling.
     *
     * @return the on-auto-completed property
     */
    public final ObjectProperty<Consumer<String>> onAutoCompletedProperty() {
        return onAutoCompleted;
    }

    /**
     * Returns the commit handler.
     *
     * @return the commit handler, or {@code null}
     */
    public final Consumer<String> getOnAutoCompleted() {
        return onAutoCompleted.get();
    }

    /**
     * Sets the commit handler.
     *
     * @param value the commit handler, or {@code null}
     */
    public final void setOnAutoCompleted(Consumer<String> value) {
        onAutoCompleted.set(value);
    }

    // ==================== Converter ====================

    private final ObjectProperty<StringConverter<String>> converter =
            new SimpleObjectProperty<>(this, "converter");

    /**
     * Optional converter supplying each dropdown row's text. When {@code null},
     * suggestions render as their own value.
     *
     * @return the converter property
     */
    public final ObjectProperty<StringConverter<String>> converterProperty() {
        return converter;
    }

    /**
     * Returns the converter.
     *
     * @return the converter, or {@code null}
     */
    public final StringConverter<String> getConverter() {
        return converter.get();
    }

    /**
     * Sets the converter.
     *
     * @param value the converter, or {@code null}
     */
    public final void setConverter(StringConverter<String> value) {
        converter.set(value);
    }

    // ==================== Visible Row Count ====================

    private final IntegerProperty visibleRowCount =
            new SimpleIntegerProperty(this, "visibleRowCount", DEFAULT_VISIBLE_ROW_COUNT);

    /**
     * Maximum number of suggestion rows shown before the dropdown scrolls.
     *
     * @return the visible-row-count property
     */
    public final IntegerProperty visibleRowCountProperty() {
        return visibleRowCount;
    }

    /**
     * Returns the maximum visible row count.
     *
     * @return the visible row count
     */
    public final int getVisibleRowCount() {
        return visibleRowCount.get();
    }

    /**
     * Sets the maximum visible row count.
     *
     * @param value the visible row count
     */
    public final void setVisibleRowCount(int value) {
        visibleRowCount.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated = new SimpleBooleanProperty(this, "animated", true);

    /**
     * Whether the dropdown plays its fade / scale-in entrance animation.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether the entrance animation is enabled.
     *
     * @return {@code true} if animated
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether the entrance animation is enabled.
     *
     * @param value {@code true} to animate
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }
}
