package io.github.leewyatt.rxcontrols.internal.popup;

import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.event.RXAutoCompleteEvent;
import io.github.leewyatt.rxcontrols.skins.SkinDisposer;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Shared autocomplete wiring between a host {@link TextField} and an
 * {@link RXSuggestionPopup}: debounced filtering on text changes, popup show / hide
 * driven by focus and matches, keyboard navigation (Down / Up / Enter / Escape) via
 * a key filter on the field, and the commit pipeline (completion handler or default
 * write-back, then the {@link RXAutoCompleteEvent#COMPLETED} notification fired on
 * the field).
 *
 * <p>Consumers parameterize the strategy seams: where the current filter factory
 * and completion handler are read from, the fallback filter used when that source
 * is {@code null}, and the default write-back text of a committed item.
 * {@code RXAutoCompleteFieldSkin} consumes it with {@code T = String} (config
 * source = control properties); the binding facade consumes it with any {@code T}
 * (config source = handle properties). Use on the JavaFX Application Thread.
 *
 * @param <T> suggestion item type
 */
public final class AutoCompletionSupport<T> {

    // ==================== Constants ====================

    private static final Duration FILTER_DEBOUNCE = Duration.millis(150);
    private static final double POPUP_GAP = 4.0;

    // ==================== Fields ====================

    private final TextField field;
    private final Supplier<Function<String, Predicate<T>>> filterFactory;
    private final Function<String, Predicate<T>> defaultFilterFactory;
    private final Supplier<Consumer<T>> completionHandler;
    private final Function<T, String> defaultCompletionText;

    private final RXSuggestionPopup<T> popup = new RXSuggestionPopup<>();
    private final PauseTransition filterDebounce = new PauseTransition(FILTER_DEBOUNCE);
    private final SkinDisposer disposer = new SkinDisposer();

    private StringConverter<T> converter;

    // Set while writing a chosen value back into the field, so the resulting text
    // change does not re-open the popup.
    private boolean suppressAutoShow;

    // A disposed support is inert: a stale consumer (an unbound facade handle, a
    // manually disposed skin) must not run user filter code, queue the debounce, or
    // attach listeners to external suggestion lists anymore.
    private boolean disposed;

    // ==================== Constructor ====================

    /**
     * Creates the autocomplete wiring for the given field.
     *
     * @param field                 the host text field (anchor, text / focus source,
     *                              key-filter target, and event target)
     * @param filterFactory         reads the consumer's current filter factory; may
     *                              yield {@code null}
     * @param defaultFilterFactory  the filter used when {@code filterFactory}
     *                              yields {@code null}
     * @param completionHandler     reads the consumer's current completion handler;
     *                              may yield {@code null} for the default write-back
     * @param defaultCompletionText maps a committed item to its default write-back
     *                              text (also carried on the completed event)
     * @throws NullPointerException if any argument is {@code null}
     */
    public AutoCompletionSupport(TextField field,
            Supplier<Function<String, Predicate<T>>> filterFactory,
            Function<String, Predicate<T>> defaultFilterFactory,
            Supplier<Consumer<T>> completionHandler,
            Function<T, String> defaultCompletionText) {
        this.field = Objects.requireNonNull(field, "field");
        this.filterFactory = Objects.requireNonNull(filterFactory, "filterFactory");
        this.defaultFilterFactory = Objects.requireNonNull(defaultFilterFactory, "defaultFilterFactory");
        this.completionHandler = Objects.requireNonNull(completionHandler, "completionHandler");
        this.defaultCompletionText = Objects.requireNonNull(defaultCompletionText, "defaultCompletionText");

        popup.setOffset(0, POPUP_GAP);
        popup.setOnSuggestionSelected(this::handleCommit);
        // Any hide (including PopupControl's native outside-click auto-hide, which does
        // not route through hidePopup()) must cancel a pending debounce, else a queued
        // re-filter re-opens the just-dismissed dropdown ~150ms later.
        popup.setOnHidden(filterDebounce::stop);

        filterDebounce.setOnFinished(event -> applyFilterAndMaybeShow(false));

        disposer.registerListener(field.textProperty(), this::onTextChanged);
        disposer.registerListener(field.focusedProperty(), this::onFocusChanged);
        disposer.registerListener(popup.getFilteredSuggestions(), this::onFilteredSuggestionsChanged);
        disposer.registerEventFilter(field, KeyEvent.KEY_PRESSED, this::handleKeyPressed);
        disposer.registerDisposeTask(filterDebounce::stop);
        disposer.registerDisposeTask(popup::dispose);
    }

    // ==================== Config passthrough ====================

    /**
     * Sets the suggestion source list.
     *
     * @param value the source list, or {@code null} for empty
     */
    public void setSuggestions(ObservableList<T> value) {
        if (disposed) {
            return;
        }
        popup.setSuggestions(value);
    }

    /**
     * Sets the converter supplying the dropdown row text (and the
     * {@link #displayText(Object) display text} of an item).
     *
     * @param value the converter, or {@code null} for {@code toString()}
     */
    public void setConverter(StringConverter<T> value) {
        if (disposed) {
            return;
        }
        converter = value;
        popup.setConverter(value);
    }

    /**
     * Sets a custom suggestion cell factory.
     *
     * @param value the cell factory, or {@code null} for the built-in cell
     */
    public void setCellFactory(Callback<RXListView<T>, RXListCell<T>> value) {
        if (disposed) {
            return;
        }
        popup.setCellFactory(value);
    }

    /**
     * Sets the maximum number of rows shown before the dropdown scrolls.
     *
     * @param value the maximum visible rows; values below 1 are rendered as 1
     */
    public void setMaxVisibleRows(int value) {
        if (disposed) {
            return;
        }
        popup.setMaxVisibleRows(value);
    }

    /**
     * Sets whether the dropdown plays its entrance animation.
     *
     * @param value the animated flag
     */
    public void setAnimated(boolean value) {
        if (disposed) {
            return;
        }
        popup.setAnimated(value);
    }

    // ==================== State + entry points ====================

    /**
     * Returns the popup's logical showing state as a read-only property.
     *
     * @return the showing property
     */
    public ReadOnlyBooleanProperty popupShowingProperty() {
        return popup.showingProperty();
    }

    /**
     * Returns whether the dropdown is currently showing.
     *
     * @return {@code true} if showing
     */
    public boolean isPopupShowing() {
        return popup.isShowing();
    }

    /**
     * Applies the current filter and shows the dropdown when the field is focused
     * and at least one suggestion matches — an empty query is allowed (the
     * programmatic-show semantic). Unlike the Down key it does not cancel a pending
     * debounced re-filter.
     */
    public void showSuggestions() {
        applyFilterAndMaybeShow(true);
    }

    /**
     * Hides the dropdown and cancels any pending debounced re-filter.
     */
    public void hideSuggestions() {
        if (disposed) {
            return;
        }
        hidePopup();
    }

    /**
     * Schedules a debounced re-filter, exactly as if the field text had changed
     * (a no-op inside the commit write-back window). Consumers call this when a
     * filter-relevant config value changes.
     */
    public void requestRefilter() {
        onTextChanged();
    }

    /**
     * Returns the display text of an item: the converter's text when a converter is
     * set (a {@code null} result renders as {@code ""}), otherwise
     * {@code toString()} ({@code null} items render as {@code ""}). Never returns
     * {@code null}.
     *
     * @param item the item, or {@code null}
     * @return the non-null display text
     */
    public String displayText(T item) {
        if (converter != null) {
            String text = converter.toString(item);
            return text == null ? "" : text;
        }
        return item == null ? "" : item.toString();
    }

    /**
     * Releases all resources: cancels the pending debounce, detaches the field
     * listeners and key filter, and disposes the popup (hiding it). Idempotent.
     * Afterwards the support is inert — every configuration setter and entry point
     * is a no-op, so a stale consumer can never run user callbacks again.
     */
    public void dispose() {
        disposed = true;
        disposer.dispose();
    }

    // ==================== Input handling ====================

    private void onTextChanged() {
        if (disposed || suppressAutoShow) {
            return;
        }
        filterDebounce.playFromStart();
    }

    private void onFocusChanged() {
        if (!field.isFocused()) {
            hidePopup();
        }
    }

    // One-way close-on-empty: an open dropdown whose matches vanish (a live
    // suggestions mutation) hides; re-opening stays typing / Down driven, so an
    // empty popup never lingers and data arrival never pops the dropdown open.
    private void onFilteredSuggestionsChanged() {
        if (popup.isShowing() && popup.getFilteredSuggestions().isEmpty()) {
            hidePopup();
        }
    }

    // Dismiss the popup and cancel any pending debounce, so a queued re-filter
    // cannot re-open the dropdown after it was intentionally closed (commit / Escape
    // / focus loss / no matches).
    private void hidePopup() {
        filterDebounce.stop();
        popup.hide();
    }

    private void applyFilterAndMaybeShow(boolean openOnEmptyText) {
        if (disposed) {
            return;
        }
        String query = field.getText() == null ? "" : field.getText();
        Function<String, Predicate<T>> factory = filterFactory.get();
        if (factory == null) {
            factory = defaultFilterFactory;
        }
        popup.setFilterPredicate(factory.apply(query));
        boolean shouldShow = field.isFocused()
                && !popup.getFilteredSuggestions().isEmpty()
                && (openOnEmptyText || !query.isEmpty());
        if (shouldShow) {
            popup.show(field);
        } else {
            hidePopup();
        }
    }

    private void handleKeyPressed(KeyEvent event) {
        switch (event.getCode()) {
            case DOWN -> {
                // The user is navigating now: cancel any pending re-filter, which would
                // otherwise fire and clear the highlight just established below.
                filterDebounce.stop();
                if (popup.isShowing()) {
                    popup.moveHighlight(1);
                } else {
                    applyFilterAndMaybeShow(true);
                    if (popup.isShowing()) {
                        popup.moveHighlight(1);
                    }
                }
                event.consume();
            }
            case UP -> {
                if (popup.isShowing()) {
                    filterDebounce.stop();
                    popup.moveHighlight(-1);
                    event.consume();
                }
            }
            case ENTER -> {
                if (popup.isShowing() && popup.highlightedItem() != null) {
                    popup.selectHighlighted();
                    event.consume();
                } else {
                    // No highlight: ENTER commits the typed text as-is, so the dropdown's
                    // job is done — dismiss it (and any queued re-filter, which would
                    // otherwise pop it open right after the commit) but leave the event
                    // un-consumed so it still reaches onAction / the default button.
                    hidePopup();
                }
            }
            case ESCAPE -> {
                if (popup.isShowing()) {
                    hidePopup();
                    event.consume();
                } else {
                    // Popup not yet visible but a debounce may be queued: cancel it so it
                    // does not pop open right after the user pressed Escape.
                    filterDebounce.stop();
                }
            }
            default -> {
            }
        }
    }

    // ==================== Commit pipeline ====================

    private void handleCommit(T item) {
        // The popup already hid itself (hideOnSelect); cancel any pending debounce so
        // it cannot re-open right after the value is written back.
        filterDebounce.stop();
        suppressAutoShow = true;
        try {
            String completionText = defaultCompletionText.apply(item);
            Consumer<T> handler = completionHandler.get();
            if (handler != null) {
                handler.accept(item);
            } else {
                // null handler = the consumer's default write-back.
                String text = completionText == null ? "" : completionText;
                field.setText(text);
                field.positionCaret(text.length());
            }
            // Observation event; fired inside the suppress window so a handler that
            // touches the text does not queue a dropdown re-open.
            field.fireEvent(new RXAutoCompleteEvent(RXAutoCompleteEvent.COMPLETED, item, completionText));
        } finally {
            suppressAutoShow = false;
        }
    }
}
