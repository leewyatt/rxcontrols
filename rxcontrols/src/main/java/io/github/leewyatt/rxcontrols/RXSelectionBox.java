package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXSelectionBoxSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.PaintConverter;
import javafx.css.converter.SizeConverter;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Skin;
import javafx.scene.paint.Paint;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * A single- or multiple-selection, searchable, virtualized drop-down selector.
 *
 * <p>The control shows a summary of the current selection in a collapsible
 * display area; activating it opens an anchored popup that hosts a virtualized
 * {@link RXListView} (only visible rows are realized, so tens of thousands of
 * items stay cheap). A source-index bridge keeps the authoritative selection on
 * the unfiltered {@code items} while the popup list renders a filtered view, so
 * selecting or searching never loses picks. The authoritative selection lives in
 * an {@link RXIndexedSelectionModel} over {@link #itemsProperty() items}; the
 * popup list carries its own single-selection cursor for keyboard navigation.</p>
 *
 * <p>Selection cardinality is chosen by {@link #selectionModeProperty()}: in
 * {@link SelectionMode#SINGLE} the display shows the chosen item and picking
 * closes the popup (see {@link #autoHideOnSelectionProperty()}); in
 * {@link SelectionMode#MULTIPLE} each row carries a checkbox, picking toggles the
 * item and the popup stays open, and the display shows a summary
 * (default {@code "N selected"}, overridable via
 * {@link #selectedItemsConverterProperty()}).</p>
 *
 * <p>This is a selection box, not an editable combo box: the optional search
 * field filters the visible items, it never commits free text as a value.</p>
 *
 * @param <T> the item type
 */
public class RXSelectionBox<T> extends Control {

    private static final String DEFAULT_STYLE_CLASS = "rx-selection-box";

    private static final int DEFAULT_MAX_VISIBLE_ROWS = 8;

    // ==================== Constructors ====================

    /**
     * Creates an empty selection box in single-selection mode.
     */
    public RXSelectionBox() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setFocusTraversable(true);
        // Keep the authoritative model's cardinality in step with selectionMode
        // and with model swaps, so the model is always the single source of truth
        // for the current selection.
        setSelectionMode(getSelectionMode());
    }

    /**
     * Creates a selection box over the given items in single-selection mode.
     *
     * @param items the items, or {@code null} for none
     */
    public RXSelectionBox(ObservableList<T> items) {
        this();
        setItems(items);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXSelectionBoxSkin<>(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Items ====================

    private final ObjectProperty<ObservableList<T>> items =
            new SimpleObjectProperty<>(this, "items", FXCollections.observableArrayList());

    /**
     * The items the user can select from. Never treated as {@code null} by the
     * skin (a {@code null} value renders as an empty list).
     *
     * @return the items property
     */
    public final ObjectProperty<ObservableList<T>> itemsProperty() {
        return items;
    }

    /**
     * Returns the items.
     *
     * @return the items, or {@code null}
     */
    public final ObservableList<T> getItems() {
        return items.get();
    }

    /**
     * Sets the items.
     *
     * @param value the items, or {@code null} for none
     */
    public final void setItems(ObservableList<T> value) {
        items.set(value);
    }

    // ==================== Selection Model ====================

    private final ObjectProperty<MultipleSelectionModel<T>> selectionModel =
            new SimpleObjectProperty<>(this, "selectionModel", new RXIndexedSelectionModel<>(itemsProperty())) {
                @Override
                protected void invalidated() {
                    MultipleSelectionModel<T> model = get();
                    if (model != null) {
                        model.setSelectionMode(effectiveSelectionMode());
                    }
                }
            };

    /**
     * Resolves the effective selection mode, coercing a {@code null} mode to
     * {@link SelectionMode#SINGLE}. Shared by both one-way syncs into the model so
     * the model never stores {@code null}.
     */
    private SelectionMode effectiveSelectionMode() {
        SelectionMode mode = getSelectionMode();
        return mode == null ? SelectionMode.SINGLE : mode;
    }

    /**
     * The authoritative selection model over {@link #itemsProperty() items}
     * (source indices, not the filtered popup view). Defaults to a non-null
     * {@link RXIndexedSelectionModel}. Its cardinality is kept in sync with
     * {@link #selectionModeProperty()}.
     *
     * @return the selection model property
     */
    public final ObjectProperty<MultipleSelectionModel<T>> selectionModelProperty() {
        return selectionModel;
    }

    /**
     * Returns the selection model.
     *
     * @return the selection model, or {@code null}
     */
    public final MultipleSelectionModel<T> getSelectionModel() {
        return selectionModel.get();
    }

    /**
     * Sets the selection model.
     *
     * @param value the selection model, or {@code null}
     */
    public final void setSelectionModel(MultipleSelectionModel<T> value) {
        selectionModel.set(value);
    }

    // ==================== Selection Mode ====================

    private final ObjectProperty<SelectionMode> selectionMode =
            new SimpleObjectProperty<>(this, "selectionMode", SelectionMode.SINGLE) {
                @Override
                protected void invalidated() {
                    MultipleSelectionModel<T> model = getSelectionModel();
                    if (model != null) {
                        model.setSelectionMode(effectiveSelectionMode());
                    }
                }
            };

    /**
     * Whether one or many items may be selected. Drives the popup indicator
     * (checkmark for single, checkbox for multiple) and the {@code :single} /
     * {@code :multiple} pseudo-classes. A {@code null} value is treated as
     * {@link SelectionMode#SINGLE} by the model.
     *
     * @return the selection mode property
     */
    public final ObjectProperty<SelectionMode> selectionModeProperty() {
        return selectionMode;
    }

    /**
     * Returns the selection mode.
     *
     * @return the selection mode
     */
    public final SelectionMode getSelectionMode() {
        return selectionMode.get();
    }

    /**
     * Sets the selection mode.
     *
     * @param value the selection mode
     */
    public final void setSelectionMode(SelectionMode value) {
        selectionMode.set(value);
    }

    // ==================== Prompt Text ====================

    private final StringProperty promptText = new SimpleStringProperty(this, "promptText");

    /**
     * The text shown in the display area when nothing is selected.
     *
     * @return the prompt text property
     */
    public final StringProperty promptTextProperty() {
        return promptText;
    }

    /**
     * Returns the prompt text.
     *
     * @return the prompt text, or {@code null}
     */
    public final String getPromptText() {
        return promptText.get();
    }

    /**
     * Sets the prompt text.
     *
     * @param value the prompt text, or {@code null}
     */
    public final void setPromptText(String value) {
        promptText.set(value);
    }

    // ==================== Graphic ====================

    private final ObjectProperty<Node> graphic = new SimpleObjectProperty<>(this, "graphic");

    /**
     * An optional graphic shown in the display area, before the selection
     * summary.
     *
     * @return the graphic property
     */
    public final ObjectProperty<Node> graphicProperty() {
        return graphic;
    }

    /**
     * Returns the graphic.
     *
     * @return the graphic, or {@code null}
     */
    public final Node getGraphic() {
        return graphic.get();
    }

    /**
     * Sets the graphic.
     *
     * @param value the graphic, or {@code null}
     */
    public final void setGraphic(Node value) {
        graphic.set(value);
    }

    // ==================== Converter ====================

    private final ObjectProperty<StringConverter<T>> converter = new SimpleObjectProperty<>(this, "converter");

    /**
     * Converts an item to its display text in the popup list and single-selection
     * summary. When {@code null}, {@code item.toString()} is used.
     *
     * @return the converter property
     */
    public final ObjectProperty<StringConverter<T>> converterProperty() {
        return converter;
    }

    /**
     * Returns the item converter.
     *
     * @return the converter, or {@code null}
     */
    public final StringConverter<T> getConverter() {
        return converter.get();
    }

    /**
     * Sets the item converter.
     *
     * @param value the converter, or {@code null}
     */
    public final void setConverter(StringConverter<T> value) {
        converter.set(value);
    }

    // ==================== Selected Items Converter ====================

    private final ObjectProperty<StringConverter<List<T>>> selectedItemsConverter =
            new SimpleObjectProperty<>(this, "selectedItemsConverter");

    /**
     * Converts the list of selected items to the multiple-selection summary text.
     * When {@code null}, the summary is {@code "N selected"}. Only consulted in
     * {@link SelectionMode#MULTIPLE} with more than one item selected; the empty
     * and single cases are handled by the control.
     *
     * @return the selected-items converter property
     */
    public final ObjectProperty<StringConverter<List<T>>> selectedItemsConverterProperty() {
        return selectedItemsConverter;
    }

    /**
     * Returns the selected-items converter.
     *
     * @return the converter, or {@code null}
     */
    public final StringConverter<List<T>> getSelectedItemsConverter() {
        return selectedItemsConverter.get();
    }

    /**
     * Sets the selected-items converter.
     *
     * @param value the converter, or {@code null}
     */
    public final void setSelectedItemsConverter(StringConverter<List<T>> value) {
        selectedItemsConverter.set(value);
    }

    // ==================== Searchable ====================

    private final BooleanProperty searchable = new SimpleBooleanProperty(this, "searchable", true);

    /**
     * Whether a search field is shown at the top of the popup to filter items.
     * Drives the {@code :searchable} pseudo-class.
     *
     * @return the searchable property
     */
    public final BooleanProperty searchableProperty() {
        return searchable;
    }

    /**
     * Returns whether searching is enabled.
     *
     * @return whether searching is enabled
     */
    public final boolean isSearchable() {
        return searchable.get();
    }

    /**
     * Sets whether searching is enabled.
     *
     * @param value {@code true} to show the search field
     */
    public final void setSearchable(boolean value) {
        searchable.set(value);
    }

    // ==================== Search Text ====================

    private final StringProperty searchText = new SimpleStringProperty(this, "searchText", "");

    /**
     * The current search query. Empty (or blank) shows all items. Drives the
     * {@code :filtered} pseudo-class.
     *
     * @return the search text property
     */
    public final StringProperty searchTextProperty() {
        return searchText;
    }

    /**
     * Returns the search text.
     *
     * @return the search text (never {@code null} unless set so)
     */
    public final String getSearchText() {
        return searchText.get();
    }

    /**
     * Sets the search text.
     *
     * @param value the search text
     */
    public final void setSearchText(String value) {
        searchText.set(value);
    }

    // ==================== Search Prompt Text ====================

    private final StringProperty searchPromptText = new SimpleStringProperty(this, "searchPromptText");

    /**
     * The prompt text shown in the empty search field.
     *
     * @return the search prompt text property
     */
    public final StringProperty searchPromptTextProperty() {
        return searchPromptText;
    }

    /**
     * Returns the search prompt text.
     *
     * @return the search prompt text, or {@code null}
     */
    public final String getSearchPromptText() {
        return searchPromptText.get();
    }

    /**
     * Sets the search prompt text.
     *
     * @param value the search prompt text, or {@code null}
     */
    public final void setSearchPromptText(String value) {
        searchPromptText.set(value);
    }

    // ==================== Filter Predicate ====================

    private final ObjectProperty<BiPredicate<T, String>> filterPredicate =
            new SimpleObjectProperty<>(this, "filterPredicate");

    /**
     * The predicate deciding whether an item matches the current search query
     * ({@code (item, query)}). When {@code null}, an item matches when its
     * display text contains the trimmed, case-insensitive query.
     *
     * @return the filter predicate property
     */
    public final ObjectProperty<BiPredicate<T, String>> filterPredicateProperty() {
        return filterPredicate;
    }

    /**
     * Returns the filter predicate.
     *
     * @return the filter predicate, or {@code null}
     */
    public final BiPredicate<T, String> getFilterPredicate() {
        return filterPredicate.get();
    }

    /**
     * Sets the filter predicate.
     *
     * @param value the filter predicate, or {@code null}
     */
    public final void setFilterPredicate(BiPredicate<T, String> value) {
        filterPredicate.set(value);
    }

    // ==================== Clear Search On Hide ====================

    private final BooleanProperty clearSearchOnHide = new SimpleBooleanProperty(this, "clearSearchOnHide", true);

    /**
     * Whether the search query is cleared when the popup hides.
     *
     * @return the clear-search-on-hide property
     */
    public final BooleanProperty clearSearchOnHideProperty() {
        return clearSearchOnHide;
    }

    /**
     * Returns whether the search query is cleared on hide.
     *
     * @return whether the search query is cleared on hide
     */
    public final boolean isClearSearchOnHide() {
        return clearSearchOnHide.get();
    }

    /**
     * Sets whether the search query is cleared on hide.
     *
     * @param value {@code true} to clear on hide
     */
    public final void setClearSearchOnHide(boolean value) {
        clearSearchOnHide.set(value);
    }

    // ==================== Placeholder ====================

    private final ObjectProperty<Node> placeholder = new SimpleObjectProperty<>(this, "placeholder");

    /**
     * The node shown in the popup when there are no items to display (for
     * instance, when the search query matches nothing).
     *
     * @return the placeholder property
     */
    public final ObjectProperty<Node> placeholderProperty() {
        return placeholder;
    }

    /**
     * Returns the placeholder.
     *
     * @return the placeholder, or {@code null}
     */
    public final Node getPlaceholder() {
        return placeholder.get();
    }

    /**
     * Sets the placeholder.
     *
     * @param value the placeholder, or {@code null}
     */
    public final void setPlaceholder(Node value) {
        placeholder.set(value);
    }

    // ==================== Section Key Factory ====================

    private final ObjectProperty<Callback<T, Object>> sectionKeyFactory =
            new SimpleObjectProperty<>(this, "sectionKeyFactory");

    /**
     * An optional grouping key extractor. When set, adjacent items with equal
     * keys are grouped under a section header in the popup list. When
     * {@code null}, the list is flat.
     *
     * @return the section key factory property
     */
    public final ObjectProperty<Callback<T, Object>> sectionKeyFactoryProperty() {
        return sectionKeyFactory;
    }

    /**
     * Returns the section key factory.
     *
     * @return the section key factory, or {@code null}
     */
    public final Callback<T, Object> getSectionKeyFactory() {
        return sectionKeyFactory.get();
    }

    /**
     * Sets the section key factory.
     *
     * @param value the section key factory, or {@code null}
     */
    public final void setSectionKeyFactory(Callback<T, Object> value) {
        sectionKeyFactory.set(value);
    }

    // ==================== Read Only ====================

    private final BooleanProperty readOnly = new SimpleBooleanProperty(this, "readOnly", false);

    /**
     * Whether the control is read-only: the current selection is still shown but
     * the popup cannot be opened and the selection cannot be changed through the
     * UI. The control stays focusable. Drives the {@code :readonly} pseudo-class.
     *
     * @return the read-only property
     */
    public final BooleanProperty readOnlyProperty() {
        return readOnly;
    }

    /**
     * Returns whether the control is read-only.
     *
     * @return whether the control is read-only
     */
    public final boolean isReadOnly() {
        return readOnly.get();
    }

    /**
     * Sets whether the control is read-only.
     *
     * @param value {@code true} for read-only
     */
    public final void setReadOnly(boolean value) {
        readOnly.set(value);
    }

    // ==================== Auto Hide On Selection ====================

    private final BooleanProperty autoHideOnSelection = new SimpleBooleanProperty(this, "autoHideOnSelection", true);

    /**
     * Whether picking an item in {@link SelectionMode#SINGLE} hides the popup.
     * Has no effect in {@link SelectionMode#MULTIPLE}, where toggling items keeps
     * the popup open.
     *
     * @return the auto-hide-on-selection property
     */
    public final BooleanProperty autoHideOnSelectionProperty() {
        return autoHideOnSelection;
    }

    /**
     * Returns whether picking hides the popup in single-selection mode.
     *
     * @return whether picking hides the popup in single-selection mode
     */
    public final boolean isAutoHideOnSelection() {
        return autoHideOnSelection.get();
    }

    /**
     * Sets whether picking hides the popup in single-selection mode.
     *
     * @param value {@code true} to hide on pick
     */
    public final void setAutoHideOnSelection(boolean value) {
        autoHideOnSelection.set(value);
    }

    // ==================== Animation Enabled ====================

    private final BooleanProperty animationEnabled = new SimpleBooleanProperty(this, "animationEnabled", true);

    /**
     * Whether the popup plays a short scale-and-fade entrance animation when
     * shown.
     *
     * @return the animation-enabled property
     */
    public final BooleanProperty animationEnabledProperty() {
        return animationEnabled;
    }

    /**
     * Returns whether the entrance animation is enabled.
     *
     * @return whether the entrance animation is enabled
     */
    public final boolean isAnimationEnabled() {
        return animationEnabled.get();
    }

    /**
     * Sets whether the entrance animation is enabled.
     *
     * @param value {@code true} to enable the entrance animation
     */
    public final void setAnimationEnabled(boolean value) {
        animationEnabled.set(value);
    }

    // ==================== Max Visible Rows ====================

    private final IntegerProperty maxVisibleRows =
            new SimpleIntegerProperty(this, "maxVisibleRows", DEFAULT_MAX_VISIBLE_ROWS);

    /**
     * The maximum number of item rows shown before the popup scrolls. Values
     * below one are treated as one at layout time.
     *
     * @return the max-visible-rows property
     */
    public final IntegerProperty maxVisibleRowsProperty() {
        return maxVisibleRows;
    }

    /**
     * Returns the maximum number of visible rows.
     *
     * @return the maximum number of visible rows
     */
    public final int getMaxVisibleRows() {
        return maxVisibleRows.get();
    }

    /**
     * Sets the maximum number of visible rows.
     *
     * @param value the maximum number of visible rows
     */
    public final void setMaxVisibleRows(int value) {
        maxVisibleRows.set(value);
    }

    // ==================== Popup Header ====================

    private final ObjectProperty<Node> popupHeader = new SimpleObjectProperty<>(this, "popupHeader");

    /**
     * An optional node placed at the top of the popup, below the search field.
     *
     * @return the popup header property
     */
    public final ObjectProperty<Node> popupHeaderProperty() {
        return popupHeader;
    }

    /**
     * Returns the popup header.
     *
     * @return the popup header, or {@code null}
     */
    public final Node getPopupHeader() {
        return popupHeader.get();
    }

    /**
     * Sets the popup header.
     *
     * @param value the popup header, or {@code null}
     */
    public final void setPopupHeader(Node value) {
        popupHeader.set(value);
    }

    // ==================== Popup Footer ====================

    private final ObjectProperty<Node> popupFooter = new SimpleObjectProperty<>(this, "popupFooter");

    /**
     * An optional node placed at the bottom of the popup, below the built-in
     * action buttons.
     *
     * @return the popup footer property
     */
    public final ObjectProperty<Node> popupFooterProperty() {
        return popupFooter;
    }

    /**
     * Returns the popup footer.
     *
     * @return the popup footer, or {@code null}
     */
    public final Node getPopupFooter() {
        return popupFooter.get();
    }

    /**
     * Sets the popup footer.
     *
     * @param value the popup footer, or {@code null}
     */
    public final void setPopupFooter(Node value) {
        popupFooter.set(value);
    }

    // ==================== Show Clear Button ====================

    private final BooleanProperty showClearButton = new SimpleBooleanProperty(this, "showClearButton", false);

    /**
     * Whether a Clear button is shown in the popup footer.
     *
     * @return the show-clear-button property
     */
    public final BooleanProperty showClearButtonProperty() {
        return showClearButton;
    }

    /**
     * Returns whether the Clear button is shown.
     *
     * @return whether the Clear button is shown
     */
    public final boolean isShowClearButton() {
        return showClearButton.get();
    }

    /**
     * Sets whether the Clear button is shown.
     *
     * @param value {@code true} to show the Clear button
     */
    public final void setShowClearButton(boolean value) {
        showClearButton.set(value);
    }

    // ==================== Show Select All Button ====================

    private final BooleanProperty showSelectAllButton = new SimpleBooleanProperty(this, "showSelectAllButton", false);

    /**
     * Whether a Select All button is shown in the popup footer. Only effective in
     * {@link SelectionMode#MULTIPLE}.
     *
     * @return the show-select-all-button property
     */
    public final BooleanProperty showSelectAllButtonProperty() {
        return showSelectAllButton;
    }

    /**
     * Returns whether the Select All button is shown.
     *
     * @return whether the Select All button is shown
     */
    public final boolean isShowSelectAllButton() {
        return showSelectAllButton.get();
    }

    /**
     * Sets whether the Select All button is shown.
     *
     * @param value {@code true} to show the Select All button
     */
    public final void setShowSelectAllButton(boolean value) {
        showSelectAllButton.set(value);
    }

    // ==================== Clear Button Text ====================

    private final StringProperty clearButtonText = new SimpleStringProperty(this, "clearButtonText", "Clear");

    /**
     * The caption of the Clear button.
     *
     * @return the clear button text property
     */
    public final StringProperty clearButtonTextProperty() {
        return clearButtonText;
    }

    /**
     * Returns the Clear button caption.
     *
     * @return the Clear button caption
     */
    public final String getClearButtonText() {
        return clearButtonText.get();
    }

    /**
     * Sets the Clear button caption.
     *
     * @param value the Clear button caption
     */
    public final void setClearButtonText(String value) {
        clearButtonText.set(value);
    }

    // ==================== Select All Button Text ====================

    private final StringProperty selectAllButtonText = new SimpleStringProperty(this, "selectAllButtonText", "Select All");

    /**
     * The caption of the Select All button.
     *
     * @return the select all button text property
     */
    public final StringProperty selectAllButtonTextProperty() {
        return selectAllButtonText;
    }

    /**
     * Returns the Select All button caption.
     *
     * @return the Select All button caption
     */
    public final String getSelectAllButtonText() {
        return selectAllButtonText.get();
    }

    /**
     * Sets the Select All button caption.
     *
     * @param value the Select All button caption
     */
    public final void setSelectAllButtonText(String value) {
        selectAllButtonText.set(value);
    }

    // ==================== Showing (read-only) ====================

    private final ReadOnlyBooleanWrapper showing = new ReadOnlyBooleanWrapper(this, "showing", false);

    /**
     * Whether the popup is currently showing. The single source of truth for the
     * popup lifecycle; {@link #show()} / {@link #hide()} flip it and the skin
     * observes it.
     *
     * @return the read-only showing property
     */
    public final ReadOnlyBooleanProperty showingProperty() {
        return showing.getReadOnlyProperty();
    }

    /**
     * Returns whether the popup is showing.
     *
     * @return whether the popup is showing
     */
    public final boolean isShowing() {
        return showing.get();
    }

    // ==================== Public API ====================

    /**
     * Shows the popup. No effect when the control is disabled or
     * {@link #isReadOnly() read-only}.
     */
    public final void show() {
        if (isDisabled() || isReadOnly()) {
            return;
        }
        showing.set(true);
    }

    /**
     * Hides the popup.
     */
    public final void hide() {
        showing.set(false);
    }

    /**
     * Clears the current selection.
     */
    public final void clearSelection() {
        MultipleSelectionModel<T> model = getSelectionModel();
        if (model != null) {
            model.clearSelection();
        }
    }

    /**
     * Selects all items. No effect in {@link SelectionMode#SINGLE}.
     */
    public final void selectAll() {
        MultipleSelectionModel<T> model = getSelectionModel();
        if (model != null) {
            model.selectAll();
        }
    }

    /**
     * Returns the selected item, or the lead item in
     * {@link SelectionMode#MULTIPLE}.
     *
     * @return the selected item, or {@code null}
     */
    public final T getSelectedItem() {
        MultipleSelectionModel<T> model = getSelectionModel();
        return model == null ? null : model.getSelectedItem();
    }

    /**
     * Returns the selected items in source order (unmodifiable). Empty when the
     * model is {@code null}.
     *
     * @return the selected items
     */
    public final ObservableList<T> getSelectedItems() {
        MultipleSelectionModel<T> model = getSelectionModel();
        return model == null ? FXCollections.emptyObservableList() : model.getSelectedItems();
    }

    // ==================== Ripple Fill ====================

    private final ObjectProperty<Paint> rippleFill =
            new StyleableObjectProperty<>(RXRipplePane.DEFAULT_RIPPLE_FILL) {
                @Override
                public CssMetaData<? extends Styleable, Paint> getCssMetaData() {
                    return StyleableProperties.RIPPLE_FILL;
                }

                @Override
                public Object getBean() {
                    return RXSelectionBox.this;
                }

                @Override
                public String getName() {
                    return "rippleFill";
                }
            };

    /**
     * Fill used for the display-area press ripple. Initial value is
     * {@link RXRipplePane#DEFAULT_RIPPLE_FILL}; setting {@code null} renders no
     * fill (transparent) per the JavaFX {@code Shape.setFill} convention.
     *
     * @return the ripple fill property
     */
    public final ObjectProperty<Paint> rippleFillProperty() {
        return rippleFill;
    }

    /**
     * Returns the ripple fill.
     *
     * @return the ripple fill, or {@code null}
     */
    public final Paint getRippleFill() {
        return rippleFill.get();
    }

    /**
     * Sets the ripple fill.
     *
     * @param value the ripple fill, or {@code null}
     */
    public final void setRippleFill(Paint value) {
        rippleFill.set(value);
    }

    // ==================== Ripple Opacity ====================

    private final DoubleProperty rippleOpacity =
            new StyleableDoubleProperty(RXRipplePane.DEFAULT_RIPPLE_OPACITY) {
                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.RIPPLE_OPACITY;
                }

                @Override
                public Object getBean() {
                    return RXSelectionBox.this;
                }

                @Override
                public String getName() {
                    return "rippleOpacity";
                }
            };

    /**
     * Peak opacity for the display-area press ripple. Initial value is
     * {@link RXRipplePane#DEFAULT_RIPPLE_OPACITY}.
     *
     * @return the ripple opacity property
     */
    public final DoubleProperty rippleOpacityProperty() {
        return rippleOpacity;
    }

    /**
     * Returns the ripple opacity.
     *
     * @return the ripple opacity
     */
    public final double getRippleOpacity() {
        return rippleOpacity.get();
    }

    /**
     * Sets the ripple opacity.
     *
     * @param value the ripple opacity
     */
    public final void setRippleOpacity(double value) {
        rippleOpacity.set(value);
    }

    // ==================== Ripple Enabled ====================

    private final BooleanProperty rippleEnabled =
            new StyleableBooleanProperty(RXRipplePane.DEFAULT_RIPPLE_ENABLED) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.RIPPLE_ENABLED;
                }

                @Override
                public Object getBean() {
                    return RXSelectionBox.this;
                }

                @Override
                public String getName() {
                    return "rippleEnabled";
                }
            };

    /**
     * Whether the display area shows a press ripple. Turning this off clears any
     * running ripple.
     *
     * @return the ripple-enabled property
     */
    public final BooleanProperty rippleEnabledProperty() {
        return rippleEnabled;
    }

    /**
     * Returns whether the display-area ripple is enabled.
     *
     * @return whether the display-area ripple is enabled
     */
    public final boolean isRippleEnabled() {
        return rippleEnabled.get();
    }

    /**
     * Sets whether the display-area ripple is enabled.
     *
     * @param value {@code true} to enable the ripple
     */
    public final void setRippleEnabled(boolean value) {
        rippleEnabled.set(value);
    }

    // ==================== State Overlay Enabled ====================

    private final BooleanProperty stateOverlayEnabled =
            new StyleableBooleanProperty(RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.RIPPLE_STATE_OVERLAY_ENABLED;
                }

                @Override
                public Object getBean() {
                    return RXSelectionBox.this;
                }

                @Override
                public String getName() {
                    return "stateOverlayEnabled";
                }
            };

    /**
     * Whether the low-opacity hover/press state overlay tints the display area.
     *
     * @return the state-overlay-enabled property
     */
    public final BooleanProperty stateOverlayEnabledProperty() {
        return stateOverlayEnabled;
    }

    /**
     * Returns whether the state overlay may show.
     *
     * @return whether the state overlay may show
     */
    public final boolean isStateOverlayEnabled() {
        return stateOverlayEnabled.get();
    }

    /**
     * Sets whether the state overlay may show.
     *
     * @param value {@code true} to allow the state overlay
     */
    public final void setStateOverlayEnabled(boolean value) {
        stateOverlayEnabled.set(value);
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXSelectionBox<?>, Paint> RIPPLE_FILL =
                new CssMetaData<>("-rx-ripple-fill",
                        PaintConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_FILL) {
                    @Override
                    public boolean isSettable(RXSelectionBox<?> box) {
                        return !box.rippleFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXSelectionBox<?> box) {
                        return (StyleableProperty<Paint>) box.rippleFillProperty();
                    }
                };

        private static final CssMetaData<RXSelectionBox<?>, Number> RIPPLE_OPACITY =
                new CssMetaData<>("-rx-ripple-opacity",
                        SizeConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_OPACITY) {
                    @Override
                    public boolean isSettable(RXSelectionBox<?> box) {
                        return !box.rippleOpacity.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSelectionBox<?> box) {
                        return (StyleableProperty<Number>) box.rippleOpacityProperty();
                    }
                };

        private static final CssMetaData<RXSelectionBox<?>, Boolean> RIPPLE_ENABLED =
                new CssMetaData<>("-rx-ripple-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_ENABLED) {
                    @Override
                    public boolean isSettable(RXSelectionBox<?> box) {
                        return !box.rippleEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXSelectionBox<?> box) {
                        return (StyleableProperty<Boolean>) box.rippleEnabledProperty();
                    }
                };

        private static final CssMetaData<RXSelectionBox<?>, Boolean> RIPPLE_STATE_OVERLAY_ENABLED =
                new CssMetaData<>("-rx-ripple-state-overlay-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED) {
                    @Override
                    public boolean isSettable(RXSelectionBox<?> box) {
                        return !box.stateOverlayEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXSelectionBox<?> box) {
                        return (StyleableProperty<Boolean>) box.stateOverlayEnabledProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(RIPPLE_FILL);
            styleables.add(RIPPLE_OPACITY);
            styleables.add(RIPPLE_ENABLED);
            styleables.add(RIPPLE_STATE_OVERLAY_ENABLED);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata list
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
