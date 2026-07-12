package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXAutoCompleteField;
import io.github.leewyatt.rxcontrols.event.RXAutoCompleteEvent;
import io.github.leewyatt.rxcontrols.internal.popup.AutoCompletionSupport;

/**
 * Default skin for {@link RXAutoCompleteField}. Extends the text-field skin and
 * delegates the dropdown wiring to a String-valued {@link AutoCompletionSupport}:
 * text changes filter (debounced) and open / close the dropdown; Down / Up / Enter
 * / Escape drive the highlight cursor with focus kept in the editor; a chosen item
 * runs the control's {@code completionHandler} write-back and fires
 * {@link RXAutoCompleteEvent#COMPLETED}. The skin's own job is mirroring the
 * control's configuration properties into the support.
 */
public class RXAutoCompleteFieldSkin extends RXTextFieldSkin {

    // ==================== Fields ====================

    private final AutoCompletionSupport<String> support;

    // ==================== Constructor ====================

    /**
     * Creates the skin for the given autocomplete field.
     *
     * @param control the autocomplete field
     */
    public RXAutoCompleteFieldSkin(RXAutoCompleteField control) {
        super(control);
        // Field-version strategy seams: the default filter and the default write-back
        // act on the raw suggestion string itself; only row text goes through the
        // converter (inside the popup's cell).
        support = new AutoCompletionSupport<>(control,
                control::getFilterFactory,
                RXAutoCompleteField.DEFAULT_FILTER_FACTORY,
                control::getCompletionHandler,
                item -> item);

        support.setSuggestions(control.getSuggestions());
        support.setConverter(control.getConverter());
        support.setCellFactory(control.getSuggestionCellFactory());
        support.setMaxVisibleRows(control.getVisibleRowCount());
        support.setAnimated(control.isAnimated());

        disposer.registerListener(control.filterFactoryProperty(), support::requestRefilter);
        disposer.registerListener(control.converterProperty(),
                () -> support.setConverter(control.getConverter()));
        disposer.registerListener(control.suggestionCellFactoryProperty(),
                () -> support.setCellFactory(control.getSuggestionCellFactory()));
        disposer.registerListener(control.visibleRowCountProperty(),
                () -> support.setMaxVisibleRows(control.getVisibleRowCount()));
        disposer.registerListener(control.animatedProperty(),
                () -> support.setAnimated(control.isAnimated()));
        disposer.registerListener(support.popupShowingProperty(),
                () -> control.setPopupShowing(support.isPopupShowing()));
        disposer.registerDisposeTask(support::dispose);
        // RXPopupSupport.dispose() hides without flipping its logical showing state, so
        // clear the mirrored control state explicitly, else :popup-showing stays stuck.
        disposer.registerDisposeTask(() -> control.setPopupShowing(false));
    }

    // ==================== Programmatic show / hide ====================

    /**
     * Opens the suggestion popup (used by {@link RXAutoCompleteField#showSuggestions()}).
     */
    public void showSuggestions() {
        support.showSuggestions();
    }

    /**
     * Closes the suggestion popup (used by {@link RXAutoCompleteField#hideSuggestions()}).
     */
    public void hideSuggestions() {
        support.hideSuggestions();
    }
}
