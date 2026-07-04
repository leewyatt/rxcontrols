package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXChipEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXChipInputSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.SimpleStyleableDoubleProperty;
import javafx.css.SimpleStyleableIntegerProperty;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.converter.SizeConverter;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.input.KeyCode;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A tag / token input: an inline text editor plus a live, wrapping collection of
 * removable input chips, with an anchored autocomplete popup. Typing edits the
 * {@link #editorTextProperty() editor text} and filters {@link #getSuggestions()
 * suggestions}; committing it (Enter, choosing a suggestion, or {@link
 * #commitInput()}) turns the text into a chip in {@link #getChips() chips}, governed
 * by {@link #customInputPolicyProperty() customInputPolicy}. The popup state is
 * observable via {@link #popupShowingProperty() popupShowing}.
 *
 * <p>A {@link Control}; its skin owns a custom chip-flow-editor layout (the editor
 * fills the remainder of the current row and wraps to a fresh row when the remainder
 * drops below {@code -rx-editor-min-width}), the index-aligned chip-node mapping and
 * the suggestion popup. This control owns the model and the commit decision, so
 * {@link #commitInput()} and the {@code :error} state work without a skin attached.</p>
 *
 * @param <T> the chip item type
 */
public class RXChipInput<T> extends Control {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-chip-input";

    /** Default {@link #editorMinWidthProperty() editor min width}, in pixels. */
    public static final double DEFAULT_EDITOR_MIN_WIDTH = 60.0;

    /** Default {@link #maxRowsProperty() maxRows}: unlimited. */
    public static final int DEFAULT_MAX_ROWS = -1;

    /** Default {@link #visibleRowCountProperty() visible row count} of the popup. */
    public static final int DEFAULT_VISIBLE_ROW_COUNT = 8;

    private static final PseudoClass ERROR_PSEUDO_CLASS = PseudoClass.getPseudoClass("error");
    private static final PseudoClass HAS_CHIPS_PSEUDO_CLASS = PseudoClass.getPseudoClass("has-chips");
    private static final PseudoClass EMPTY_PSEUDO_CLASS = PseudoClass.getPseudoClass("empty");
    private static final PseudoClass POPUP_SHOWING_PSEUDO_CLASS = PseudoClass.getPseudoClass("popup-showing");

    // ==================== Custom Input Policy ====================

    /**
     * How the editor's text is committed when it does not match a suggestion.
     */
    public enum CustomInputPolicy {
        /** Unmatched text is rejected: the text is kept and {@code :error} is flashed. */
        STRICT,
        /** Unmatched text becomes a new chip (not added to the suggestion source). */
        FREE,
        /** Unmatched text becomes a new chip and is appended to the suggestion source. */
        CREATE
    }

    // ==================== Fields ====================

    private final ObservableList<T> chips = FXCollections.observableArrayList();
    private final ObservableList<T> suggestions = FXCollections.observableArrayList();
    private final ObservableSet<KeyCode> separatorKeys = FXCollections.observableSet(KeyCode.ENTER);

    // ==================== Constructors ====================

    /**
     * Creates an empty chip input.
     */
    public RXChipInput() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        chips.addListener((ListChangeListener<T>) change -> updateChipStatePseudoClasses());
        updateChipStatePseudoClasses();
    }

    /**
     * Creates an empty chip input with the given converter.
     *
     * @param converter the item/text converter
     */
    public RXChipInput(StringConverter<T> converter) {
        this();
        setConverter(converter);
    }

    /**
     * Returns the mutable list of committed chip items. This is the single source of
     * truth; the skin mirrors it into chip nodes by index.
     *
     * @return the chips list
     */
    public final ObservableList<T> getChips() {
        return chips;
    }

    /**
     * Returns the mutable list of autocomplete candidates. The {@link
     * CustomInputPolicy#CREATE} policy appends freshly created items here.
     *
     * @return the suggestions list
     */
    public final ObservableList<T> getSuggestions() {
        return suggestions;
    }

    /**
     * Returns the mutable set of keys that commit the current editor text like Enter
     * (for example add {@link KeyCode#COMMA} for comma-separated tags). {@link
     * KeyCode#ENTER} always commits regardless of this set.
     *
     * @return the separator-keys set
     */
    public final ObservableSet<KeyCode> getSeparatorKeys() {
        return separatorKeys;
    }

    // ==================== Converter ====================

    private final ObjectProperty<StringConverter<T>> converter =
            new SimpleObjectProperty<>(this, "converter");

    /**
     * The item/text converter: {@code toString} renders a chip's text, {@code
     * fromString} parses committed text into an item. When {@code null}, the text is
     * used directly (the item type is treated as {@code String}).
     *
     * @return the converter property
     */
    public final ObjectProperty<StringConverter<T>> converterProperty() {
        return converter;
    }

    /**
     * Returns the converter.
     *
     * @return the converter, or {@code null}
     */
    public final StringConverter<T> getConverter() {
        return converter.get();
    }

    /**
     * Sets the converter.
     *
     * @param value the converter, or {@code null}
     */
    public final void setConverter(StringConverter<T> value) {
        converter.set(value);
    }

    // ==================== On Create Item ====================

    private final ObjectProperty<Function<String, T>> onCreateItem =
            new SimpleObjectProperty<>(this, "onCreateItem");

    /**
     * A creation hook that overrides {@code converter.fromString} when committing
     * unmatched text; returning {@code null} rejects the text ({@code :error}).
     *
     * @return the on-create-item property
     */
    public final ObjectProperty<Function<String, T>> onCreateItemProperty() {
        return onCreateItem;
    }

    /**
     * Returns the create hook.
     *
     * @return the create hook, or {@code null}
     */
    public final Function<String, T> getOnCreateItem() {
        return onCreateItem.get();
    }

    /**
     * Sets the create hook.
     *
     * @param value the create hook, or {@code null}
     */
    public final void setOnCreateItem(Function<String, T> value) {
        onCreateItem.set(value);
    }

    // ==================== Custom Input Policy ====================

    private final ObjectProperty<CustomInputPolicy> customInputPolicy =
            new SimpleObjectProperty<>(this, "customInputPolicy", CustomInputPolicy.FREE);

    /**
     * The policy for committing unmatched editor text. Default {@link
     * CustomInputPolicy#FREE}. {@code null} is treated as {@code FREE}.
     *
     * @return the custom-input-policy property
     */
    public final ObjectProperty<CustomInputPolicy> customInputPolicyProperty() {
        return customInputPolicy;
    }

    /**
     * Returns the custom-input policy.
     *
     * @return the custom-input policy, or {@code null}
     */
    public final CustomInputPolicy getCustomInputPolicy() {
        return customInputPolicy.get();
    }

    /**
     * Sets the custom-input policy.
     *
     * @param value the policy, or {@code null} for the default
     */
    public final void setCustomInputPolicy(CustomInputPolicy value) {
        customInputPolicy.set(value);
    }

    private CustomInputPolicy customInputPolicyOrDefault() {
        CustomInputPolicy value = getCustomInputPolicy();
        return value == null ? CustomInputPolicy.FREE : value;
    }

    // ==================== Allow Duplicates ====================

    private final BooleanProperty allowDuplicates = new BooleanPropertyBase(false) {
        @Override
        public Object getBean() {
            return RXChipInput.this;
        }

        @Override
        public String getName() {
            return "allowDuplicates";
        }
    };

    /**
     * Whether committing an item already in {@link #getChips()} is allowed. Default
     * {@code false}: duplicates (by {@code T.equals}) are ignored.
     *
     * @return the allow-duplicates property
     */
    public final BooleanProperty allowDuplicatesProperty() {
        return allowDuplicates;
    }

    /**
     * Returns whether duplicates are allowed.
     *
     * @return whether duplicates are allowed
     */
    public final boolean isAllowDuplicates() {
        return allowDuplicates.get();
    }

    /**
     * Sets whether duplicates are allowed.
     *
     * @param value {@code true} to allow duplicate chips
     */
    public final void setAllowDuplicates(boolean value) {
        allowDuplicates.set(value);
    }

    // ==================== Editable ====================

    private final BooleanProperty editable = new BooleanPropertyBase(true) {
        @Override
        public Object getBean() {
            return RXChipInput.this;
        }

        @Override
        public String getName() {
            return "editable";
        }
    };

    /**
     * Whether the editor accepts typing. Default {@code true}. When {@code false} the
     * editor is read-only but existing chips can still be shown and removed.
     *
     * @return the editable property
     */
    public final BooleanProperty editableProperty() {
        return editable;
    }

    /**
     * Returns whether the editor is editable.
     *
     * @return whether the editor is editable
     */
    public final boolean isEditable() {
        return editable.get();
    }

    /**
     * Sets whether the editor is editable.
     *
     * @param value {@code true} to allow typing
     */
    public final void setEditable(boolean value) {
        editable.set(value);
    }

    // ==================== Prompt Text ====================

    private final StringProperty promptText = new SimpleStringProperty(this, "promptText", "");

    /**
     * The prompt shown while the input is empty (no chips and no editor text).
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

    // ==================== Chip Factory ====================

    private final ObjectProperty<Callback<T, RXChip>> chipFactory =
            new SimpleObjectProperty<>(this, "chipFactory");

    /**
     * An optional factory for the chip node of an item. When {@code null} a default
     * removable input chip labelled by the converter is used. The skin binds the
     * item to its node and installs the close handler.
     *
     * @return the chip-factory property
     */
    public final ObjectProperty<Callback<T, RXChip>> chipFactoryProperty() {
        return chipFactory;
    }

    /**
     * Returns the chip factory.
     *
     * @return the chip factory, or {@code null}
     */
    public final Callback<T, RXChip> getChipFactory() {
        return chipFactory.get();
    }

    /**
     * Sets the chip factory.
     *
     * @param value the chip factory, or {@code null} for the default
     */
    public final void setChipFactory(Callback<T, RXChip> value) {
        chipFactory.set(value);
    }

    // ==================== Editor Text ====================

    private final StringProperty editorText = new SimpleStringProperty(this, "editorText", "") {
        @Override
        protected void invalidated() {
            // Editing dismisses a commit-failure flash and refreshes the empty state.
            setErrorState(false);
            updateChipStatePseudoClasses();
        }
    };

    /**
     * The current editor text (the in-progress, uncommitted input). Writable so it
     * can be pre-filled or cleared programmatically; the skin binds it to the editor.
     *
     * @return the editor-text property
     */
    public final StringProperty editorTextProperty() {
        return editorText;
    }

    /**
     * Returns the editor text. Empty by default; the commit path and prompt state
     * tolerate a {@code null} value.
     *
     * @return the editor text
     */
    public final String getEditorText() {
        return editorText.get();
    }

    /**
     * Sets the editor text.
     *
     * @param value the editor text
     */
    public final void setEditorText(String value) {
        editorText.set(value);
    }

    // ==================== Editor Min Width (styleable) ====================

    private final DoubleProperty editorMinWidth = new SimpleStyleableDoubleProperty(
            StyleableProperties.EDITOR_MIN_WIDTH, this, "editorMinWidth", DEFAULT_EDITOR_MIN_WIDTH);

    /**
     * The editor's minimum usable width in pixels. Settable from CSS via
     * {@code -rx-editor-min-width}. Below this remainder the editor wraps to a new row.
     *
     * @return the editor-min-width property
     */
    public final DoubleProperty editorMinWidthProperty() {
        return editorMinWidth;
    }

    /**
     * Returns the editor minimum width.
     *
     * @return the editor minimum width
     */
    public final double getEditorMinWidth() {
        return editorMinWidth.get();
    }

    /**
     * Sets the editor minimum width.
     *
     * @param value the editor minimum width
     */
    public final void setEditorMinWidth(double value) {
        editorMinWidth.set(value);
    }

    // ==================== Max Rows (styleable) ====================

    private final IntegerProperty maxRows = new SimpleStyleableIntegerProperty(
            StyleableProperties.MAX_ROWS, this, "maxRows", DEFAULT_MAX_ROWS);

    /**
     * The maximum number of visible chip rows before the content scrolls vertically.
     * Default {@code -1} (unlimited: the input grows with the row count). Settable
     * from CSS via {@code -rx-max-rows}.
     *
     * @return the max-rows property
     */
    public final IntegerProperty maxRowsProperty() {
        return maxRows;
    }

    /**
     * Returns the maximum visible rows.
     *
     * @return the maximum visible rows, or a non-positive value for unlimited
     */
    public final int getMaxRows() {
        return maxRows.get();
    }

    /**
     * Sets the maximum visible rows.
     *
     * @param value the maximum visible rows, or a non-positive value for unlimited
     */
    public final void setMaxRows(int value) {
        maxRows.set(value);
    }

    // ==================== Suggestions / popup ====================

    private final ObjectProperty<Function<String, Predicate<T>>> filterFunction =
            new SimpleObjectProperty<>(this, "filterFunction");

    /**
     * Maps the current query text to a predicate that filters {@link #getSuggestions()}.
     * When {@code null} the skin uses a case-insensitive contains match on the
     * converter's text.
     *
     * @return the filter-function property
     */
    public final ObjectProperty<Function<String, Predicate<T>>> filterFunctionProperty() {
        return filterFunction;
    }

    /**
     * Returns the filter function.
     *
     * @return the filter function, or {@code null}
     */
    public final Function<String, Predicate<T>> getFilterFunction() {
        return filterFunction.get();
    }

    /**
     * Sets the filter function.
     *
     * @param value the filter function, or {@code null} for the default
     */
    public final void setFilterFunction(Function<String, Predicate<T>> value) {
        filterFunction.set(value);
    }

    private final BooleanProperty filterSelectedOptions =
            new SimpleBooleanProperty(this, "filterSelectedOptions", false);

    /**
     * Whether items already present in {@link #getChips()} are hidden from the
     * suggestion popup. Default {@code false}.
     *
     * @return the filter-selected-options property
     */
    public final BooleanProperty filterSelectedOptionsProperty() {
        return filterSelectedOptions;
    }

    /**
     * Returns whether selected options are filtered out of the popup.
     *
     * @return whether selected options are filtered out
     */
    public final boolean isFilterSelectedOptions() {
        return filterSelectedOptions.get();
    }

    /**
     * Sets whether selected options are filtered out of the popup.
     *
     * @param value {@code true} to hide already-chipped items
     */
    public final void setFilterSelectedOptions(boolean value) {
        filterSelectedOptions.set(value);
    }

    private final ObjectProperty<Callback<RXListView<T>, RXListCell<T>>> suggestionCellFactory =
            new SimpleObjectProperty<>(this, "suggestionCellFactory");

    /**
     * An optional cell factory for the suggestion rows, forwarded to the popup's list.
     *
     * @return the suggestion-cell-factory property
     */
    public final ObjectProperty<Callback<RXListView<T>, RXListCell<T>>> suggestionCellFactoryProperty() {
        return suggestionCellFactory;
    }

    /**
     * Returns the suggestion cell factory.
     *
     * @return the suggestion cell factory, or {@code null}
     */
    public final Callback<RXListView<T>, RXListCell<T>> getSuggestionCellFactory() {
        return suggestionCellFactory.get();
    }

    /**
     * Sets the suggestion cell factory.
     *
     * @param value the suggestion cell factory, or {@code null}
     */
    public final void setSuggestionCellFactory(Callback<RXListView<T>, RXListCell<T>> value) {
        suggestionCellFactory.set(value);
    }

    private final IntegerProperty visibleRowCount =
            new SimpleIntegerProperty(this, "visibleRowCount", DEFAULT_VISIBLE_ROW_COUNT);

    /**
     * The maximum number of visible suggestion rows in the popup. Default
     * {@value #DEFAULT_VISIBLE_ROW_COUNT}.
     *
     * @return the visible-row-count property
     */
    public final IntegerProperty visibleRowCountProperty() {
        return visibleRowCount;
    }

    /**
     * Returns the visible row count.
     *
     * @return the visible row count
     */
    public final int getVisibleRowCount() {
        return visibleRowCount.get();
    }

    /**
     * Sets the visible row count.
     *
     * @param value the visible row count
     */
    public final void setVisibleRowCount(int value) {
        visibleRowCount.set(value);
    }

    private final BooleanProperty hideOnSelect = new SimpleBooleanProperty(this, "hideOnSelect", true);

    /**
     * Whether the popup closes after a suggestion is chosen. Default {@code true};
     * set {@code false} to keep it open for rapid multi-tagging.
     *
     * @return the hide-on-select property
     */
    public final BooleanProperty hideOnSelectProperty() {
        return hideOnSelect;
    }

    /**
     * Returns whether the popup hides on select.
     *
     * @return whether the popup hides on select
     */
    public final boolean isHideOnSelect() {
        return hideOnSelect.get();
    }

    /**
     * Sets whether the popup hides on select.
     *
     * @param value {@code true} to hide the popup after choosing
     */
    public final void setHideOnSelect(boolean value) {
        hideOnSelect.set(value);
    }

    private final BooleanProperty animated = new SimpleBooleanProperty(this, "animated", true);

    /**
     * Whether the suggestion popup animates its entrance. Default {@code true}.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether the popup is animated.
     *
     * @return whether the popup is animated
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether the popup is animated.
     *
     * @param value {@code true} to animate the popup entrance
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    private final ReadOnlyBooleanWrapper popupShowing =
            new ReadOnlyBooleanWrapper(this, "popupShowing", false) {
                @Override
                protected void invalidated() {
                    pseudoClassStateChanged(POPUP_SHOWING_PSEUDO_CLASS, get());
                }
            };

    /**
     * Whether the suggestion popup is currently showing. Mirrors the popup's state;
     * useful for CSS ({@code :popup-showing}) and observers.
     *
     * @return the read-only popup-showing property
     */
    public final ReadOnlyBooleanProperty popupShowingProperty() {
        return popupShowing.getReadOnlyProperty();
    }

    /**
     * Returns whether the popup is showing.
     *
     * @return whether the popup is showing
     */
    public final boolean isPopupShowing() {
        return popupShowing.get();
    }

    /**
     * Sets the popup-showing state. Intended for skin / behavior implementors, which
     * mirror the popup here; not for general use.
     *
     * @param value whether the popup is showing
     */
    public final void setPopupShowing(boolean value) {
        popupShowing.set(value);
    }

    // ==================== On Chip Added / Removed ====================

    private final ObjectProperty<EventHandler<RXChipEvent>> onChipAdded =
            new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(RXChipEvent.ADDED, get());
                }

                @Override
                public Object getBean() {
                    return RXChipInput.this;
                }

                @Override
                public String getName() {
                    return "onChipAdded";
                }
            };

    /**
     * The handler invoked after a chip is added (the {@link RXChipEvent#ADDED} event).
     *
     * @return the on-chip-added property
     */
    public final ObjectProperty<EventHandler<RXChipEvent>> onChipAddedProperty() {
        return onChipAdded;
    }

    /**
     * Returns the on-chip-added handler.
     *
     * @return the on-chip-added handler, or {@code null}
     */
    public final EventHandler<RXChipEvent> getOnChipAdded() {
        return onChipAdded.get();
    }

    /**
     * Sets the on-chip-added handler.
     *
     * @param value the on-chip-added handler, or {@code null}
     */
    public final void setOnChipAdded(EventHandler<RXChipEvent> value) {
        onChipAdded.set(value);
    }

    private final ObjectProperty<EventHandler<RXChipEvent>> onChipRemoved =
            new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(RXChipEvent.REMOVED, get());
                }

                @Override
                public Object getBean() {
                    return RXChipInput.this;
                }

                @Override
                public String getName() {
                    return "onChipRemoved";
                }
            };

    /**
     * The handler invoked after a chip is removed (the {@link RXChipEvent#REMOVED}
     * event).
     *
     * @return the on-chip-removed property
     */
    public final ObjectProperty<EventHandler<RXChipEvent>> onChipRemovedProperty() {
        return onChipRemoved;
    }

    /**
     * Returns the on-chip-removed handler.
     *
     * @return the on-chip-removed handler, or {@code null}
     */
    public final EventHandler<RXChipEvent> getOnChipRemoved() {
        return onChipRemoved.get();
    }

    /**
     * Sets the on-chip-removed handler.
     *
     * @param value the on-chip-removed handler, or {@code null}
     */
    public final void setOnChipRemoved(EventHandler<RXChipEvent> value) {
        onChipRemoved.set(value);
    }

    // ==================== Public methods ====================

    /**
     * Commits the current editor text as a chip, following {@link
     * #customInputPolicyProperty() customInputPolicy} (as if Enter were pressed).
     * Empty text is a no-op.
     */
    public final void commitInput() {
        String text = getEditorText();
        if (text == null || text.isEmpty()) {
            return;
        }
        T created = createFromText(text);
        switch (customInputPolicyOrDefault()) {
            case STRICT -> setErrorState(true);
            case FREE -> {
                if (acceptCreated(created)) {
                    getChips().add(created);
                    clearInput();
                }
            }
            case CREATE -> {
                if (acceptCreated(created)) {
                    getChips().add(created);
                    if (!getSuggestions().contains(created)) {
                        getSuggestions().add(created);
                    }
                    clearInput();
                }
            }
        }
    }

    /**
     * Clears the editor text (does not remove chips).
     */
    public final void clearInput() {
        setEditorText("");
    }

    /**
     * Removes the first chip equal to {@code item} (by {@code T.equals}). When a skin
     * is attached the removal runs through the vetoable {@link RXChipEvent#REMOVE}
     * flow on the chip node and fires {@link RXChipEvent#REMOVED} on success; without
     * a skin the item is removed directly and no {@code REMOVED} event is fired (that
     * event originates in the skin).
     *
     * @param item the item to remove
     * @return {@code true} if a chip was removed
     */
    public final boolean removeChip(T item) {
        int index = getChips().indexOf(item);
        if (index < 0) {
            return false;
        }
        if (getSkin() instanceof RXChipInputSkin<?> skin) {
            return skin.requestRemoveAt(index);
        }
        getChips().remove(index);
        return true;
    }

    /**
     * Opens the suggestion popup (as if the user pressed the open shortcut). No-op
     * when no skin is attached.
     */
    public final void showSuggestions() {
        if (getSkin() instanceof RXChipInputSkin<?> skin) {
            skin.showSuggestions();
        }
    }

    /**
     * Closes the suggestion popup. No-op when no skin is attached.
     */
    public final void hideSuggestions() {
        if (getSkin() instanceof RXChipInputSkin<?> skin) {
            skin.hideSuggestions();
        }
    }

    // ==================== Commit helpers ====================

    @SuppressWarnings("unchecked")
    private T createFromText(String text) {
        Function<String, T> creator = getOnCreateItem();
        if (creator != null) {
            return creator.apply(text);
        }
        StringConverter<T> conv = getConverter();
        if (conv != null) {
            try {
                return conv.fromString(text);
            } catch (RuntimeException parseFailure) {
                return null;
            }
        }
        return (T) text;
    }

    private boolean acceptCreated(T created) {
        if (created == null) {
            setErrorState(true);
            return false;
        }
        // Duplicates are silently ignored (no error) when not allowed.
        return isAllowDuplicates() || !getChips().contains(created);
    }

    private void setErrorState(boolean error) {
        pseudoClassStateChanged(ERROR_PSEUDO_CLASS, error);
    }

    private void updateChipStatePseudoClasses() {
        boolean hasChips = !getChips().isEmpty();
        boolean empty = !hasChips && (getEditorText() == null || getEditorText().isEmpty());
        pseudoClassStateChanged(HAS_CHIPS_PSEUDO_CLASS, hasChips);
        pseudoClassStateChanged(EMPTY_PSEUDO_CLASS, empty);
    }

    // ==================== Control ====================

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXChipInputSkin<>(this);
    }

    /**
     * Returns the user-agent stylesheet used by RXControls.
     *
     * @return the user-agent stylesheet URL
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    /**
     * The input wraps chips horizontally, so it reports a horizontal content bias:
     * its height depends on the width it is given.
     *
     * @return {@link Orientation#HORIZONTAL}
     */
    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXChipInput<?>, Number> EDITOR_MIN_WIDTH =
                new CssMetaData<>("-rx-editor-min-width", SizeConverter.getInstance(), DEFAULT_EDITOR_MIN_WIDTH) {
                    @Override
                    public boolean isSettable(RXChipInput<?> input) {
                        return !input.editorMinWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXChipInput<?> input) {
                        return (StyleableProperty<Number>) input.editorMinWidthProperty();
                    }
                };

        private static final CssMetaData<RXChipInput<?>, Number> MAX_ROWS =
                new CssMetaData<>("-rx-max-rows", SizeConverter.getInstance(), DEFAULT_MAX_ROWS) {
                    @Override
                    public boolean isSettable(RXChipInput<?> input) {
                        return !input.maxRows.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXChipInput<?> input) {
                        return (StyleableProperty<Number>) input.maxRowsProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(EDITOR_MIN_WIDTH);
            styleables.add(MAX_ROWS);
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

    /**
     * Returns the CSS metadata associated with this control.
     *
     * @return the CSS metadata list
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
