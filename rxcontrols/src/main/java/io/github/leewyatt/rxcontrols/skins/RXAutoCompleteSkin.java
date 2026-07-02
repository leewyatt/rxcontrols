package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXAutoComplete;
import io.github.leewyatt.rxcontrols.internal.popup.RXSuggestionPopup;
import javafx.animation.PauseTransition;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXAutoComplete}. Extends the text-field skin and wires a
 * shared {@link RXSuggestionPopup} to the editor: text changes filter (debounced)
 * and open / close the dropdown; Down / Up / Enter / Escape drive the highlight
 * cursor with focus kept in the editor; a chosen item runs the control's
 * {@code onAutoCompleted} handler.
 */
public class RXAutoCompleteSkin extends RXTextFieldSkin {

    // ==================== Constants ====================

    private static final Duration FILTER_DEBOUNCE = Duration.millis(150);
    private static final double POPUP_GAP = 4.0;

    // ==================== Fields ====================

    private final RXAutoComplete control;
    private final RXSuggestionPopup<String> popup = new RXSuggestionPopup<>();
    private final PauseTransition filterDebounce = new PauseTransition(FILTER_DEBOUNCE);

    // Set while writing a chosen value back into the field, so the resulting text
    // change does not re-open the popup.
    private boolean suppressAutoShow;

    // ==================== Constructor ====================

    /**
     * Creates the skin for the given autocomplete control.
     *
     * @param control the autocomplete control
     */
    public RXAutoCompleteSkin(RXAutoComplete control) {
        super(control);
        this.control = control;

        popup.setSuggestions(control.getSuggestions());
        popup.setConverter(control.getConverter());
        popup.setMaxVisibleRows(control.getVisibleRowCount());
        popup.setAnimated(control.isAnimated());
        popup.setOffset(0, POPUP_GAP);
        popup.setOnSuggestionSelected(this::handleCommit);
        // Any hide (including PopupControl's native outside-click auto-hide, which does
        // not route through hidePopup()) must cancel a pending debounce, else a queued
        // re-filter re-opens the just-dismissed dropdown ~150ms later.
        popup.setOnHidden(filterDebounce::stop);

        filterDebounce.setOnFinished(event -> applyFilterAndMaybeShow(false));

        disposer.registerListener(control.textProperty(), this::onTextChanged);
        disposer.registerListener(control.focusedProperty(), this::onFocusChanged);
        disposer.registerListener(control.filterFunctionProperty(), this::onTextChanged);
        disposer.registerListener(control.converterProperty(),
                () -> popup.setConverter(control.getConverter()));
        disposer.registerListener(control.visibleRowCountProperty(),
                () -> popup.setMaxVisibleRows(control.getVisibleRowCount()));
        disposer.registerListener(control.animatedProperty(),
                () -> popup.setAnimated(control.isAnimated()));
        disposer.registerEventFilter(control, KeyEvent.KEY_PRESSED, this::handleKeyPressed);
        disposer.registerDisposeTask(filterDebounce::stop);
        disposer.registerDisposeTask(popup::dispose);
    }

    // ==================== Input handling ====================

    private void onTextChanged() {
        if (suppressAutoShow) {
            return;
        }
        filterDebounce.playFromStart();
    }

    private void onFocusChanged() {
        if (!control.isFocused()) {
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
        String query = control.getText() == null ? "" : control.getText();
        Function<String, Predicate<String>> function = control.getFilterFunction();
        if (function == null) {
            function = RXAutoComplete.DEFAULT_FILTER_FUNCTION;
        }
        popup.setFilterPredicate(function.apply(query));
        boolean shouldShow = control.isFocused()
                && !popup.getFilteredSuggestions().isEmpty()
                && (openOnEmptyText || !query.isEmpty());
        if (shouldShow) {
            popup.show(control);
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

    private void handleCommit(String item) {
        // The popup already hid itself (hideOnSelect); cancel any pending debounce so
        // it cannot re-open right after the value is written back.
        filterDebounce.stop();
        suppressAutoShow = true;
        try {
            Consumer<String> handler = control.getOnAutoCompleted();
            if (handler != null) {
                handler.accept(item);
            }
        } finally {
            suppressAutoShow = false;
        }
    }
}
