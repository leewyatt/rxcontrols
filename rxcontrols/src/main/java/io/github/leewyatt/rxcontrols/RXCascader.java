package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXCascaderSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Control;
import javafx.scene.control.ListCell;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Skin;
import javafx.util.Callback;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Popup cascader control backed by a reusable {@link RXCascaderView}. The control
 * owns the popup shell, the display field, and its own configuration properties;
 * the embedded view (kept private and used as popup content) owns path expansion,
 * single selection, multiple checked paths, disabled inheritance, lazy loading,
 * and tri-state check logic.
 *
 * <p>Configuration properties declared here ({@code selectionMode},
 * {@code itemTextFactory}, {@code visibleRowCount}, {@code cellFactory},
 * {@code childrenLoader}, {@code onChildrenLoadError}) drive the embedded view
 * through one-way bindings; the read-only {@code selectedPath} mirrors it back.
 * Each property's bean is this control, per the JavaFX convention. The root item
 * and result lists are the embedded view's own lists, shared directly.
 *
 * @param <T> application value type
 */
public class RXCascader<T> extends Control {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-cascader";

    // ==================== Fields ====================

    private final RXCascaderView<T> view = new RXCascaderView<>();

    // ==================== Constructor ====================

    /**
     * Creates an empty cascader.
     */
    public RXCascader() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setFocusTraversable(true);
        // Configuration flows down into the embedded popup view; the result
        // (selected path) flows back up into a read-only mirror. The item and
        // result lists are shared directly (see the getters), not bound here.
        view.selectionModeProperty().bind(selectionMode);
        view.itemTextFactoryProperty().bind(itemTextFactory);
        view.visibleRowCountProperty().bind(visibleRowCount);
        view.cellFactoryProperty().bind(cellFactory);
        view.childrenLoaderProperty().bind(childrenLoader);
        view.onChildrenLoadErrorProperty().bind(onChildrenLoadError);
        selectedPath.bind(view.selectedPathProperty());
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXCascaderSkin<>(this, view);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Items ====================

    /**
     * Root items shown in the first cascader column.
     *
     * @return mutable root item list
     */
    public final ObservableList<RXCascaderItem<T>> getRootItems() {
        return view.getRootItems();
    }

    /**
     * Expanded branch path.
     *
     * @return read-only active path list
     */
    public final ObservableList<RXCascaderItem<T>> getActivePath() {
        return view.getActivePath();
    }

    // ==================== Selection Mode ====================

    private final ObjectProperty<SelectionMode> selectionMode =
            new SimpleObjectProperty<>(this, "selectionMode", SelectionMode.SINGLE) {
                private SelectionMode lastValid = SelectionMode.SINGLE;

                @Override
                protected void invalidated() {
                    SelectionMode value = get();
                    if (value == null) {
                        if (!isBound()) {
                            set(lastValid);
                        }
                        throw new NullPointerException("selectionMode cannot be null");
                    }
                    lastValid = value;
                }
            };

    /**
     * Selection mode. {@link SelectionMode#SINGLE SINGLE} selects a single leaf
     * path (observe {@link #selectedPathProperty()}); {@link SelectionMode#MULTIPLE
     * MULTIPLE} checks multiple paths with cascading tri-state check boxes (observe
     * {@link #getCheckedPaths()}). This is the cascader's own meaning of the shared
     * JavaFX {@link SelectionMode} enum, not the row multi-select of a list view.
     *
     * @return selection-mode property
     */
    public final ObjectProperty<SelectionMode> selectionModeProperty() {
        return selectionMode;
    }

    /**
     * Returns the selection mode.
     *
     * @return selection mode
     */
    public final SelectionMode getSelectionMode() {
        return selectionMode.get();
    }

    /**
     * Sets the selection mode.
     *
     * @param value selection mode
     */
    public final void setSelectionMode(SelectionMode value) {
        selectionMode.set(value);
    }

    // ==================== Selected Path ====================

    private final ReadOnlyObjectWrapper<RXCascaderPath<T>> selectedPath =
            new ReadOnlyObjectWrapper<>(this, "selectedPath");

    /**
     * Selected path in single-selection mode.
     *
     * @return read-only selected-path property
     */
    public final ReadOnlyObjectProperty<RXCascaderPath<T>> selectedPathProperty() {
        return selectedPath.getReadOnlyProperty();
    }

    /**
     * Returns the selected path.
     *
     * @return selected path, or {@code null}
     */
    public final RXCascaderPath<T> getSelectedPath() {
        return selectedPath.get();
    }

    /**
     * Checked leaf paths in multiple-selection mode.
     *
     * @return read-only checked path list maintained by the embedded view
     */
    public final ObservableList<RXCascaderPath<T>> getCheckedPaths() {
        return view.getCheckedPaths();
    }

    // ==================== Prompt Text ====================

    private final StringProperty promptText =
            new SimpleStringProperty(this, "promptText", "");

    /**
     * Placeholder text shown when no path is selected.
     *
     * @return prompt-text property
     */
    public final StringProperty promptTextProperty() {
        return promptText;
    }

    /**
     * Returns the prompt text.
     *
     * @return prompt text
     */
    public final String getPromptText() {
        return promptText.get();
    }

    /**
     * Sets the prompt text.
     *
     * @param value prompt text, or {@code null}
     */
    public final void setPromptText(String value) {
        promptText.set(value);
    }

    // ==================== Item Text Factory ====================

    private final ObjectProperty<Callback<T, String>> itemTextFactory =
            new SimpleObjectProperty<>(this, "itemTextFactory");

    /**
     * Converts an item value to its display text (single source of the visible
     * node text). When {@code null}, {@code String.valueOf(value)} is used. A
     * {@code null} value, or a factory that returns {@code null}, yields the empty
     * string.
     *
     * @return item-text-factory property
     */
    public final ObjectProperty<Callback<T, String>> itemTextFactoryProperty() {
        return itemTextFactory;
    }

    /**
     * Returns the item text factory.
     *
     * @return item text factory, or {@code null}
     */
    public final Callback<T, String> getItemTextFactory() {
        return itemTextFactory.get();
    }

    /**
     * Sets the item text factory.
     *
     * @param value item text factory, or {@code null}
     */
    public final void setItemTextFactory(Callback<T, String> value) {
        itemTextFactory.set(value);
    }

    // ==================== Path Text Factory ====================

    private final ObjectProperty<Callback<RXCascaderPath<T>, String>> pathTextFactory =
            new SimpleObjectProperty<>(this, "pathTextFactory");

    /**
     * Optional formatter from a selected path to the single string shown in the
     * field. When {@code null}, the field shows the per-node display texts
     * (resolved by {@link #getItemTextFactory() itemTextFactory}) joined with
     * {@code " / "}.
     *
     * <p>To keep the field consistent with the columns, resolve node text from
     * each item's value via the same {@link #getItemTextFactory() itemTextFactory}
     * rather than {@code value.toString()}, which bypasses it.
     *
     * @return path-text factory property
     */
    public final ObjectProperty<Callback<RXCascaderPath<T>, String>> pathTextFactoryProperty() {
        return pathTextFactory;
    }

    /**
     * Returns the path-text factory.
     *
     * @return path-text factory, or {@code null}
     */
    public final Callback<RXCascaderPath<T>, String> getPathTextFactory() {
        return pathTextFactory.get();
    }

    /**
     * Sets the path-text factory.
     *
     * @param value path-text factory, or {@code null}
     */
    public final void setPathTextFactory(Callback<RXCascaderPath<T>, String> value) {
        pathTextFactory.set(value);
    }

    // ==================== Clearable ====================

    private final BooleanProperty clearable =
            new SimpleBooleanProperty(this, "clearable", false);

    /**
     * Whether a clear affordance is shown when a selection exists.
     *
     * @return clearable property
     */
    public final BooleanProperty clearableProperty() {
        return clearable;
    }

    /**
     * Returns whether the control is clearable.
     *
     * @return {@code true} if clearable
     */
    public final boolean isClearable() {
        return clearable.get();
    }

    /**
     * Sets whether the control is clearable.
     *
     * @param value {@code true} if clearable
     */
    public final void setClearable(boolean value) {
        clearable.set(value);
    }

    // ==================== Showing ====================

    private final ReadOnlyBooleanWrapper showing =
            new ReadOnlyBooleanWrapper(this, "showing", false);

    /**
     * Whether the popup is showing.
     *
     * @return read-only showing property
     */
    public final ReadOnlyBooleanProperty showingProperty() {
        return showing.getReadOnlyProperty();
    }

    /**
     * Returns whether the popup is showing.
     *
     * @return {@code true} if showing
     */
    public final boolean isShowing() {
        return showing.get();
    }

    /**
     * Requests the popup to show.
     */
    public final void show() {
        if (isDisabled()) {
            return;
        }
        showing.set(true);
    }

    /**
     * Requests the popup to hide.
     */
    public final void hide() {
        showing.set(false);
    }

    // ==================== Visible Row Count ====================

    private final IntegerProperty visibleRowCount =
            new SimpleIntegerProperty(this, "visibleRowCount", RXCascaderView.DEFAULT_VISIBLE_ROW_COUNT);

    /**
     * Number of visible popup rows used for the preferred popup height.
     *
     * @return visible-row-count property
     */
    public final IntegerProperty visibleRowCountProperty() {
        return visibleRowCount;
    }

    /**
     * Returns the visible row count.
     *
     * @return visible row count
     */
    public final int getVisibleRowCount() {
        return visibleRowCount.get();
    }

    /**
     * Sets the visible row count.
     *
     * @param value visible row count
     */
    public final void setVisibleRowCount(int value) {
        visibleRowCount.set(value);
    }

    // ==================== Cell Factory ====================

    private final ObjectProperty<Callback<RXCascaderView<T>, ListCell<RXCascaderItem<T>>>> cellFactory =
            new SimpleObjectProperty<>(this, "cellFactory");

    /**
     * Optional factory for the cells of each popup column.
     *
     * @return cell-factory property
     */
    public final ObjectProperty<Callback<RXCascaderView<T>, ListCell<RXCascaderItem<T>>>> cellFactoryProperty() {
        return cellFactory;
    }

    /**
     * Returns the cell factory.
     *
     * @return cell factory, or {@code null}
     */
    public final Callback<RXCascaderView<T>, ListCell<RXCascaderItem<T>>> getCellFactory() {
        return cellFactory.get();
    }

    /**
     * Sets the cell factory.
     *
     * @param value cell factory, or {@code null}
     */
    public final void setCellFactory(Callback<RXCascaderView<T>, ListCell<RXCascaderItem<T>>> value) {
        cellFactory.set(value);
    }

    // ==================== Children Loader ====================

    private final ObjectProperty<Function<RXCascaderItem<T>, CompletionStage<List<RXCascaderItem<T>>>>>
            childrenLoader = new SimpleObjectProperty<>(this, "childrenLoader");

    /**
     * Optional asynchronous loader used by unloaded branches.
     *
     * @return children-loader property
     */
    public final ObjectProperty<Function<RXCascaderItem<T>, CompletionStage<List<RXCascaderItem<T>>>>>
            childrenLoaderProperty() {
        return childrenLoader;
    }

    /**
     * Returns the children loader.
     *
     * @return children loader, or {@code null}
     */
    public final Function<RXCascaderItem<T>, CompletionStage<List<RXCascaderItem<T>>>> getChildrenLoader() {
        return childrenLoader.get();
    }

    /**
     * Sets the children loader.
     *
     * @param value children loader, or {@code null}
     */
    public final void setChildrenLoader(
            Function<RXCascaderItem<T>, CompletionStage<List<RXCascaderItem<T>>>> value) {
        childrenLoader.set(value);
    }

    // ==================== Children Load Error ====================

    private final ObjectProperty<BiConsumer<RXCascaderItem<T>, Throwable>> onChildrenLoadError =
            new SimpleObjectProperty<>(this, "onChildrenLoadError");

    /**
     * Optional callback invoked on the JavaFX thread when a lazy children load
     * fails.
     *
     * @return children-load-error callback property
     */
    public final ObjectProperty<BiConsumer<RXCascaderItem<T>, Throwable>> onChildrenLoadErrorProperty() {
        return onChildrenLoadError;
    }

    /**
     * Returns the children-load-error callback.
     *
     * @return callback, or {@code null}
     */
    public final BiConsumer<RXCascaderItem<T>, Throwable> getOnChildrenLoadError() {
        return onChildrenLoadError.get();
    }

    /**
     * Sets the children-load-error callback.
     *
     * @param value callback, or {@code null}
     */
    public final void setOnChildrenLoadError(BiConsumer<RXCascaderItem<T>, Throwable> value) {
        onChildrenLoadError.set(value);
    }

    // ==================== Operations ====================

    /**
     * Forces a same-source reload of the whole lazy tree. In eager mode (no
     * loader set) this is a no-op.
     */
    public final void reload() {
        view.reload();
    }

    /**
     * Clears both single and multiple selection state.
     */
    public final void clearSelection() {
        view.clearSelection();
    }

    /**
     * Programmatically sets the single selection to the path ending at the given
     * leaf. Applies only in single-selection mode; ignored in multiple mode, or
     * when the item is {@code null}, effectively disabled, or not a leaf.
     *
     * @param leaf leaf item to select
     */
    public final void select(RXCascaderItem<T> leaf) {
        view.select(leaf);
    }

    /**
     * Sets a cascading check state: the item and its enabled descendants are
     * (un)checked and ancestors roll up to the matching tri-state. Applies only in
     * multiple-selection mode; ignored in single mode. Use this for programmatic
     * checking instead of writing {@link RXCascaderItem#setChecked} directly.
     *
     * @param item    item to update
     * @param checked target checked state
     */
    public final void setCheckedCascade(RXCascaderItem<T> item, boolean checked) {
        view.setCheckedCascade(item, checked);
    }
}
