package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXAutoCompleteEvent;
import io.github.leewyatt.rxcontrols.skins.RXAutoCompleteFieldSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.scene.control.Skin;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Text field with a local, synchronous autocomplete dropdown. As the user types,
 * {@link #getSuggestions() suggestions} are filtered by
 * {@link #filterFunctionProperty() filterFunction} and shown in an anchored popup;
 * choosing an item (mouse or keyboard) runs the
 * {@link #completionHandlerProperty() completionHandler} write-back strategy
 * (by default writing the item into the field) and then fires
 * {@link RXAutoCompleteEvent#COMPLETED}.
 *
 * <p>Focus stays in the editor while the popup is open — the dropdown is a passive
 * highlight surface driven by Down / Up / Enter / Escape. This is a minimal,
 * String-valued consumer of the shared suggestion-popup infrastructure; richer
 * value types, remote providers, and chip/tag inputs are separate controls. To
 * attach the same dropdown to an arbitrary {@code TextField} (or for non-String
 * suggestion types), use the {@link RXAutoCompletion} binding facade instead.
 *
 * @see RXAutoCompletion
 */
public class RXAutoCompleteField extends RXTextField {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-auto-complete-field";

    private static final PseudoClass POPUP_SHOWING_PSEUDO_CLASS = PseudoClass.getPseudoClass("popup-showing");

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
    public RXAutoCompleteField() {
        this(null);
    }

    /**
     * Creates an autocomplete field with initial text.
     *
     * @param text the initial text, or {@code null}
     */
    public RXAutoCompleteField(String text) {
        super(text);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXAutoCompleteFieldSkin(this);
    }

    // ==================== Suggestions ====================

    private final ObservableList<String> suggestions = FXCollections.observableArrayList();

    /**
     * The live list of candidate suggestions ({@code null} elements are not
     * supported). Mutate it in place; the dropdown rows track changes, and an open
     * dropdown hides when no suggestion matches anymore. Opening is always driven
     * by user input (typing or Down) or {@link #showSuggestions()}, never by data
     * arrival.
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

    // ==================== Completion Handler ====================

    private final ObjectProperty<Consumer<String>> completionHandler =
            new SimpleObjectProperty<>(this, "completionHandler", item -> {
                String text = item == null ? "" : item;
                setText(text);
                positionCaret(text.length());
            });

    /**
     * The write-back strategy invoked with the chosen suggestion when the user
     * commits one. The default writes the item into the field and moves the caret
     * to the end; replace it to customize write-back, or set a no-op handler to
     * suppress write-back entirely. A {@code null} value is treated as the default
     * by the skin. The handler must apply its changes synchronously — the skin
     * suppresses dropdown re-opening only for the duration of the call. After the
     * handler runs, {@link RXAutoCompleteEvent#COMPLETED} is fired.
     *
     * @return the completion-handler property
     */
    public final ObjectProperty<Consumer<String>> completionHandlerProperty() {
        return completionHandler;
    }

    /**
     * Returns the completion handler.
     *
     * @return the completion handler, or {@code null}
     */
    public final Consumer<String> getCompletionHandler() {
        return completionHandler.get();
    }

    /**
     * Sets the completion handler.
     *
     * @param value the completion handler, or {@code null} for the default
     */
    public final void setCompletionHandler(Consumer<String> value) {
        completionHandler.set(value);
    }

    // ==================== On Auto Completed ====================

    private final ObjectProperty<EventHandler<RXAutoCompleteEvent>> onAutoCompleted =
            new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(RXAutoCompleteEvent.COMPLETED, get());
                }

                @Override
                public Object getBean() {
                    return RXAutoCompleteField.this;
                }

                @Override
                public String getName() {
                    return "onAutoCompleted";
                }
            };

    /**
     * The handler invoked after a suggestion is committed and written back (the
     * {@link RXAutoCompleteEvent#COMPLETED} event). Observation only — customizing
     * the write-back is the job of {@link #completionHandlerProperty()
     * completionHandler}.
     *
     * @return the on-auto-completed property
     */
    public final ObjectProperty<EventHandler<RXAutoCompleteEvent>> onAutoCompletedProperty() {
        return onAutoCompleted;
    }

    /**
     * Returns the on-auto-completed handler.
     *
     * @return the on-auto-completed handler, or {@code null}
     */
    public final EventHandler<RXAutoCompleteEvent> getOnAutoCompleted() {
        return onAutoCompleted.get();
    }

    /**
     * Sets the on-auto-completed handler.
     *
     * @param value the on-auto-completed handler, or {@code null}
     */
    public final void setOnAutoCompleted(EventHandler<RXAutoCompleteEvent> value) {
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

    // ==================== Suggestion Cell Factory ====================

    private final ObjectProperty<Callback<RXListView<String>, RXListCell<String>>> suggestionCellFactory =
            new SimpleObjectProperty<>(this, "suggestionCellFactory");

    /**
     * An optional cell factory for the suggestion rows, forwarded to the popup's
     * list. {@code null} (the default) uses the built-in cell, which renders each
     * suggestion's text via {@link #converterProperty() converter}.
     *
     * @return the suggestion-cell-factory property
     */
    public final ObjectProperty<Callback<RXListView<String>, RXListCell<String>>> suggestionCellFactoryProperty() {
        return suggestionCellFactory;
    }

    /**
     * Returns the suggestion cell factory.
     *
     * @return the suggestion cell factory, or {@code null}
     */
    public final Callback<RXListView<String>, RXListCell<String>> getSuggestionCellFactory() {
        return suggestionCellFactory.get();
    }

    /**
     * Sets the suggestion cell factory.
     *
     * @param value the suggestion cell factory, or {@code null} for the default cell
     */
    public final void setSuggestionCellFactory(Callback<RXListView<String>, RXListCell<String>> value) {
        suggestionCellFactory.set(value);
    }

    // ==================== Visible Row Count ====================

    private final IntegerProperty visibleRowCount =
            new SimpleIntegerProperty(this, "visibleRowCount", DEFAULT_VISIBLE_ROW_COUNT);

    /**
     * Maximum number of suggestion rows shown before the dropdown scrolls.
     * Values below 1 are rendered as 1.
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

    // ==================== Popup Showing ====================

    private final ReadOnlyBooleanWrapper popupShowing =
            new ReadOnlyBooleanWrapper(this, "popupShowing", false) {
                @Override
                protected void invalidated() {
                    pseudoClassStateChanged(POPUP_SHOWING_PSEUDO_CLASS, get());
                }
            };

    /**
     * Whether the suggestion dropdown is currently showing. Mirrors the popup's
     * state; useful for CSS ({@code :popup-showing}) and observers.
     *
     * @return the read-only popup-showing property
     */
    public final ReadOnlyBooleanProperty popupShowingProperty() {
        return popupShowing.getReadOnlyProperty();
    }

    /**
     * Returns whether the dropdown is showing.
     *
     * @return whether the dropdown is showing
     */
    public final boolean isPopupShowing() {
        return popupShowing.get();
    }

    /**
     * Sets the popup-showing state. Intended for skin / behavior implementors, which
     * mirror the popup here; not for general use.
     *
     * @param value whether the dropdown is showing
     */
    public final void setPopupShowing(boolean value) {
        popupShowing.set(value);
    }

    // ==================== Public methods ====================

    /**
     * Opens the suggestion dropdown programmatically: applies the current filter —
     * an empty query is allowed — and shows the dropdown when the field is focused
     * and at least one suggestion matches. The entry point for asynchronous flows,
     * where results arriving are the continuation of the user's own typing. No-op
     * when no skin is attached.
     */
    public final void showSuggestions() {
        if (getSkin() instanceof RXAutoCompleteFieldSkin skin) {
            skin.showSuggestions();
        }
    }

    /**
     * Closes the suggestion dropdown and cancels any pending debounced re-filter.
     * No-op when no skin is attached.
     */
    public final void hideSuggestions() {
        if (getSkin() instanceof RXAutoCompleteFieldSkin skin) {
            skin.hideSuggestions();
        }
    }
}
