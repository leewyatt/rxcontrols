package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXAutoCompleteEvent;
import io.github.leewyatt.rxcontrols.internal.popup.AutoCompletionSupport;
import io.github.leewyatt.rxcontrols.skins.SkinDisposer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.control.TextField;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Attaches an autocomplete dropdown to any {@link TextField} — including the
 * RXMaterial field family and third-party fields — without requiring a dedicated
 * control. {@link #bind(TextField) bind} wires the shared suggestion
 * infrastructure to the field and returns this handle for configuration;
 * {@link #unbind(TextField) unbind} (or {@link #dispose()}) detaches it again.
 * {@link RXAutoCompleteField} is the String-specialized, FXML-friendly control on
 * the same infrastructure; this facade is the generic, code-first counterpart.
 *
 * <pre>{@code
 * RXAutoCompletion<Book> completion = RXAutoCompletion.bind(searchField);
 * completion.setConverter(bookTitleConverter);
 * completion.getSuggestions().setAll(books);
 * }</pre>
 *
 * <p>As the user types, the {@link #getSuggestions() suggestions} are filtered by
 * {@link #filterFactoryProperty() filterFactory} (by default a case-insensitive
 * substring match on each item's display text) and shown in an anchored popup;
 * committing an item (mouse or keyboard) runs the
 * {@link #completionHandlerProperty() completionHandler} write-back (by default
 * writing the item's display text into the field) and fires
 * {@link RXAutoCompleteEvent#COMPLETED} with the field as target.
 *
 * <p>Binding the same field again silently replaces the previous binding
 * (disposing it first). A field carries at most one binding. Forgetting to
 * dispose does not pin long-lived objects — every listener hangs off the field
 * itself and the popup tracks its anchor — but {@code dispose()} is the
 * deterministic cleanup entry point.
 *
 * <p>The suggestions list does not support {@code null} elements. The whole API
 * is confined to the JavaFX Application Thread. For asynchronous sources, keep
 * the fetch orchestration outside the component: push results into
 * {@link #getSuggestions()} on the FX thread and call {@link #showSuggestions()}
 * — arriving results are the continuation of the user's own typing. Pair this
 * with {@link #acceptAll()} when the server has already filtered.
 *
 * @param <T> the suggestion item type
 * @see RXAutoCompleteField
 */
public final class RXAutoCompletion<T> {

    // ==================== Constants ====================

    /** Registry key in the field's properties map; one binding per field. */
    private static final Object KEY = new Object();

    /** Default {@link #visibleRowCountProperty() visible row count}; matches the suggestion-popup default. */
    public static final int DEFAULT_VISIBLE_ROW_COUNT = 8;

    // ==================== Static factories ====================

    /**
     * Binds an autocomplete dropdown to the given field with an initially empty
     * suggestions list. An existing binding on the field is silently replaced
     * (disposed first).
     *
     * @param field the text field to attach to
     * @param <T>   the suggestion item type
     * @return the new binding handle
     * @throws NullPointerException     if {@code field} is {@code null}
     * @throws IllegalArgumentException if {@code field} is an
     *                                  {@link RXAutoCompleteField} (it owns its own
     *                                  dropdown; configure the control directly)
     */
    public static <T> RXAutoCompletion<T> bind(TextField field) {
        return bindInternal(field, FXCollections.observableArrayList());
    }

    /**
     * Binds an autocomplete dropdown to the given field, adopting {@code suggestions}
     * as the live backing list: {@link #getSuggestions()} returns the same instance,
     * and external mutations drive the dropdown rows directly.
     *
     * @param field       the text field to attach to
     * @param suggestions the live suggestions list to adopt
     * @param <T>         the suggestion item type
     * @return the new binding handle
     * @throws NullPointerException     if {@code field} or {@code suggestions} is {@code null}
     * @throws IllegalArgumentException if {@code field} is an {@link RXAutoCompleteField}
     */
    public static <T> RXAutoCompletion<T> bind(TextField field, ObservableList<T> suggestions) {
        Objects.requireNonNull(suggestions, "suggestions");
        return bindInternal(field, suggestions);
    }

    /**
     * Binds an autocomplete dropdown to the given field, copying {@code suggestions}
     * into the binding's own live list (later changes to the source collection are
     * not reflected; mutate {@link #getSuggestions()} instead).
     *
     * @param field       the text field to attach to
     * @param suggestions the initial suggestions, copied
     * @param <T>         the suggestion item type
     * @return the new binding handle
     * @throws NullPointerException     if {@code field} or {@code suggestions} is {@code null}
     * @throws IllegalArgumentException if {@code field} is an {@link RXAutoCompleteField}
     */
    public static <T> RXAutoCompletion<T> bind(TextField field, Collection<? extends T> suggestions) {
        Objects.requireNonNull(suggestions, "suggestions");
        return bindInternal(field, FXCollections.observableArrayList(suggestions));
    }

    /**
     * Detaches the binding currently attached to the given field, if any:
     * equivalent to calling {@link #dispose()} on that binding. A field without a
     * binding is a no-op, so the call is idempotent.
     *
     * @param field the text field to detach from
     * @throws NullPointerException if {@code field} is {@code null}
     */
    public static void unbind(TextField field) {
        Objects.requireNonNull(field, "field");
        if (field.getProperties().get(KEY) instanceof RXAutoCompletion<?> existing) {
            existing.dispose();
        }
    }

    /**
     * Returns a filter factory that accepts every suggestion regardless of the
     * query. Use it when the suggestions are already filtered elsewhere (typically
     * a server-side search feeding {@link #getSuggestions()}), where the default
     * substring filter would wrongly re-filter fuzzy-matched results.
     *
     * @param <T> the suggestion item type
     * @return the all-accepting filter factory
     */
    public static <T> Function<String, Predicate<T>> acceptAll() {
        return query -> item -> true;
    }

    private static <T> RXAutoCompletion<T> bindInternal(TextField field, ObservableList<T> suggestions) {
        Objects.requireNonNull(field, "field");
        if (field instanceof RXAutoCompleteField) {
            throw new IllegalArgumentException(
                    "field is an RXAutoCompleteField, which already owns a suggestion dropdown -"
                            + " binding would open two popups; configure the control's own API instead");
        }
        if (field.getProperties().get(KEY) instanceof RXAutoCompletion<?> existing) {
            existing.dispose();
        }
        RXAutoCompletion<T> completion = new RXAutoCompletion<>(field, suggestions);
        field.getProperties().put(KEY, completion);
        return completion;
    }

    // ==================== Fields ====================

    private final TextField field;
    private final ObservableList<T> suggestions;
    private final AutoCompletionSupport<T> support;
    private final SkinDisposer disposer = new SkinDisposer();

    // A disposed handle must never attach anything to the field again (a later
    // setOnAutoCompleted would otherwise add a handler nothing ever removes).
    private boolean disposed;

    // ==================== Constructor ====================

    private RXAutoCompletion(TextField field, ObservableList<T> suggestions) {
        this.field = field;
        this.suggestions = suggestions;
        // Binding-version strategy seams: the default filter and the default
        // write-back both act on the item's display text (converter-aware); the
        // String control's seams act on the raw item instead.
        this.support = new AutoCompletionSupport<>(field,
                this::getFilterFactory,
                this::defaultFilterPredicate,
                this::getCompletionHandler,
                this::displayTextOf);
        support.setSuggestions(suggestions);

        disposer.registerDisposeTask(support::dispose);
        disposer.registerListener(support.popupShowingProperty(),
                () -> popupShowing.set(support.isPopupShowing()));
        disposer.registerDisposeTask(() -> setOnAutoCompleted(null));
    }

    // ==================== Lifecycle ====================

    /**
     * Detaches this binding from its field: cancels any pending re-filter, hides
     * and disposes the popup, and removes every listener, key filter, and event
     * handler it installed. Idempotent, and identity-checked against the field's
     * registry — a stale handle disposed after being replaced only cleans up its
     * own leftovers and never detaches the newer binding. Afterwards the handle is
     * inert: configuration changes and show / hide calls no longer have any effect.
     */
    public void dispose() {
        disposed = true;
        disposer.dispose();
        popupShowing.set(false);
        if (field.getProperties().get(KEY) == this) {
            field.getProperties().remove(KEY);
        }
    }

    // ==================== Suggestions ====================

    /**
     * The live list of candidate suggestions ({@code null} elements are not
     * supported). Mutate it in place; the dropdown rows track changes, and an open
     * dropdown hides when no suggestion matches anymore. Opening is always driven
     * by user input (typing or Down) or {@link #showSuggestions()}, never by data
     * arrival.
     *
     * @return the mutable suggestions list
     */
    public ObservableList<T> getSuggestions() {
        return suggestions;
    }

    // ==================== Filter Factory ====================

    private final ObjectProperty<Function<String, Predicate<T>>> filterFactory =
            new SimpleObjectProperty<>(this, "filterFactory") {
                @Override
                protected void invalidated() {
                    support.requestRefilter();
                }
            };

    /**
     * Maps the current query text to a predicate selecting matching suggestions.
     * {@code null} (the default) falls back to a case-insensitive substring match
     * on each item's display text. See {@link #acceptAll()} for pre-filtered
     * (asynchronous / server-side) sources.
     *
     * @return the filter-factory property
     */
    public ObjectProperty<Function<String, Predicate<T>>> filterFactoryProperty() {
        return filterFactory;
    }

    /**
     * Returns the filter factory.
     *
     * @return the filter factory, or {@code null}
     */
    public Function<String, Predicate<T>> getFilterFactory() {
        return filterFactory.get();
    }

    /**
     * Sets the filter factory.
     *
     * @param value the filter factory, or {@code null} for the default
     */
    public void setFilterFactory(Function<String, Predicate<T>> value) {
        filterFactory.set(value);
    }

    // ==================== Completion Handler ====================

    private final ObjectProperty<Consumer<T>> completionHandler =
            new SimpleObjectProperty<>(this, "completionHandler");

    /**
     * The write-back strategy invoked with the chosen suggestion when the user
     * commits one. {@code null} (the default) writes the item's display text into
     * the field and moves the caret to the end; replace it to customize write-back,
     * or set a no-op handler to suppress write-back entirely. The handler must
     * apply its changes synchronously — dropdown re-opening is suppressed only for
     * the duration of the call. After the handler runs,
     * {@link RXAutoCompleteEvent#COMPLETED} is fired on the field.
     *
     * @return the completion-handler property
     */
    public ObjectProperty<Consumer<T>> completionHandlerProperty() {
        return completionHandler;
    }

    /**
     * Returns the completion handler.
     *
     * @return the completion handler, or {@code null}
     */
    public Consumer<T> getCompletionHandler() {
        return completionHandler.get();
    }

    /**
     * Sets the completion handler.
     *
     * @param value the completion handler, or {@code null} for the default write-back
     */
    public void setCompletionHandler(Consumer<T> value) {
        completionHandler.set(value);
    }

    // ==================== Converter ====================

    private final ObjectProperty<StringConverter<T>> converter =
            new SimpleObjectProperty<>(this, "converter") {
                @Override
                protected void invalidated() {
                    support.setConverter(get());
                    // Unlike the String control, the default filter here reads the
                    // display text, so a converter swap changes which rows match.
                    support.requestRefilter();
                }
            };

    /**
     * Supplies each item's display text: the dropdown row text, the input of the
     * default filter, and the default write-back. When {@code null} (the default),
     * items render via {@code toString()}. Only {@code toString} is used;
     * {@code fromString} is never called.
     *
     * @return the converter property
     */
    public ObjectProperty<StringConverter<T>> converterProperty() {
        return converter;
    }

    /**
     * Returns the converter.
     *
     * @return the converter, or {@code null}
     */
    public StringConverter<T> getConverter() {
        return converter.get();
    }

    /**
     * Sets the converter.
     *
     * @param value the converter, or {@code null} for {@code toString()}
     */
    public void setConverter(StringConverter<T> value) {
        converter.set(value);
    }

    // ==================== Suggestion Cell Factory ====================

    private final ObjectProperty<Callback<RXListView<T>, RXListCell<T>>> suggestionCellFactory =
            new SimpleObjectProperty<>(this, "suggestionCellFactory") {
                @Override
                protected void invalidated() {
                    support.setCellFactory(get());
                }
            };

    /**
     * An optional cell factory for the suggestion rows. {@code null} (the default)
     * uses the built-in cell, which renders each item's display text via
     * {@link #converterProperty() converter}.
     *
     * @return the suggestion-cell-factory property
     */
    public ObjectProperty<Callback<RXListView<T>, RXListCell<T>>> suggestionCellFactoryProperty() {
        return suggestionCellFactory;
    }

    /**
     * Returns the suggestion cell factory.
     *
     * @return the suggestion cell factory, or {@code null}
     */
    public Callback<RXListView<T>, RXListCell<T>> getSuggestionCellFactory() {
        return suggestionCellFactory.get();
    }

    /**
     * Sets the suggestion cell factory.
     *
     * @param value the suggestion cell factory, or {@code null} for the default cell
     */
    public void setSuggestionCellFactory(Callback<RXListView<T>, RXListCell<T>> value) {
        suggestionCellFactory.set(value);
    }

    // ==================== Visible Row Count ====================

    private final IntegerProperty visibleRowCount =
            new SimpleIntegerProperty(this, "visibleRowCount", DEFAULT_VISIBLE_ROW_COUNT) {
                @Override
                protected void invalidated() {
                    support.setMaxVisibleRows(get());
                }
            };

    /**
     * Maximum number of suggestion rows shown before the dropdown scrolls; default
     * {@value #DEFAULT_VISIBLE_ROW_COUNT}. Values below 1 are rendered as 1.
     *
     * @return the visible-row-count property
     */
    public IntegerProperty visibleRowCountProperty() {
        return visibleRowCount;
    }

    /**
     * Returns the maximum visible row count.
     *
     * @return the visible row count
     */
    public int getVisibleRowCount() {
        return visibleRowCount.get();
    }

    /**
     * Sets the maximum visible row count.
     *
     * @param value the visible row count
     */
    public void setVisibleRowCount(int value) {
        visibleRowCount.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated =
            new SimpleBooleanProperty(this, "animated", true) {
                @Override
                protected void invalidated() {
                    support.setAnimated(get());
                }
            };

    /**
     * Whether the dropdown plays its fade / scale-in entrance animation.
     *
     * @return the animated property
     */
    public BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether the entrance animation is enabled.
     *
     * @return {@code true} if animated
     */
    public boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether the entrance animation is enabled.
     *
     * @param value {@code true} to animate
     */
    public void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== Popup Showing ====================

    private final ReadOnlyBooleanWrapper popupShowing =
            new ReadOnlyBooleanWrapper(this, "popupShowing", false);

    /**
     * Whether the suggestion dropdown is currently showing. Mirrors the popup's
     * state; {@code false} after {@link #dispose()}.
     *
     * @return the read-only popup-showing property
     */
    public ReadOnlyBooleanProperty popupShowingProperty() {
        return popupShowing.getReadOnlyProperty();
    }

    /**
     * Returns whether the dropdown is showing.
     *
     * @return whether the dropdown is showing
     */
    public boolean isPopupShowing() {
        return popupShowing.get();
    }

    // ==================== On Auto Completed ====================

    private final ObjectProperty<EventHandler<RXAutoCompleteEvent>> onAutoCompleted =
            new SimpleObjectProperty<>(this, "onAutoCompleted") {
                // Managed via add/removeEventHandler so the binding never occupies the
                // field's single setEventHandler slot for this event type.
                private EventHandler<RXAutoCompleteEvent> attached;

                @Override
                protected void invalidated() {
                    if (attached != null) {
                        field.removeEventHandler(RXAutoCompleteEvent.COMPLETED, attached);
                        attached = null;
                    }
                    if (disposed) {
                        return;
                    }
                    attached = get();
                    if (attached != null) {
                        field.addEventHandler(RXAutoCompleteEvent.COMPLETED, attached);
                    }
                }
            };

    /**
     * The handler invoked after a suggestion is committed and written back (the
     * {@link RXAutoCompleteEvent#COMPLETED} event, fired with the bound field as
     * target). Observation only — customizing the write-back is the job of
     * {@link #completionHandlerProperty() completionHandler}.
     *
     * @return the on-auto-completed property
     */
    public ObjectProperty<EventHandler<RXAutoCompleteEvent>> onAutoCompletedProperty() {
        return onAutoCompleted;
    }

    /**
     * Returns the on-auto-completed handler.
     *
     * @return the on-auto-completed handler, or {@code null}
     */
    public EventHandler<RXAutoCompleteEvent> getOnAutoCompleted() {
        return onAutoCompleted.get();
    }

    /**
     * Sets the on-auto-completed handler.
     *
     * @param value the on-auto-completed handler, or {@code null}
     */
    public void setOnAutoCompleted(EventHandler<RXAutoCompleteEvent> value) {
        onAutoCompleted.set(value);
    }

    // ==================== Public methods ====================

    /**
     * Opens the suggestion dropdown programmatically: applies the current filter —
     * an empty query is allowed — and shows the dropdown when the field is focused
     * and at least one suggestion matches. The entry point for asynchronous flows,
     * where results arriving are the continuation of the user's own typing.
     */
    public void showSuggestions() {
        support.showSuggestions();
    }

    /**
     * Closes the suggestion dropdown and cancels any pending debounced re-filter.
     */
    public void hideSuggestions() {
        support.hideSuggestions();
    }

    // ==================== Seams ====================

    private Predicate<T> defaultFilterPredicate(String query) {
        String needle = (query == null ? "" : query).toLowerCase(Locale.ROOT);
        return item -> displayTextOf(item).toLowerCase(Locale.ROOT).contains(needle);
    }

    private String displayTextOf(T item) {
        return support.displayText(item);
    }
}
