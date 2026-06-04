package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXCascaderSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Control;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.util.Callback;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Popup cascader control backed by a reusable {@link RXCascaderView}. The
 * control owns the popup shell and display text, while the view owns path
 * expansion, single selection, multiple checked paths, disabled inheritance,
 * lazy loading, and tri-state check logic.
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
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXCascaderSkin<>(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== View ====================

    /**
     * Returns the embedded view owned by this cascader. The returned node is
     * used as popup content and must not be inserted into another parent — for a
     * standalone inline cascader, create a separate {@link RXCascaderView}.
     *
     * @return embedded cascader view
     */
    public final RXCascaderView<T> getView() {
        return view;
    }

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

    /**
     * Selection mode.
     *
     * @return selection-mode property
     */
    public final ObjectProperty<RXCascaderSelectionMode> selectionModeProperty() {
        return view.selectionModeProperty();
    }

    /**
     * Returns the selection mode.
     *
     * @return selection mode
     */
    public final RXCascaderSelectionMode getSelectionMode() {
        return view.getSelectionMode();
    }

    /**
     * Sets the selection mode.
     *
     * @param value selection mode
     */
    public final void setSelectionMode(RXCascaderSelectionMode value) {
        view.setSelectionMode(value);
    }

    // ==================== Selected Path ====================

    /**
     * Selected path in single-selection mode.
     *
     * @return read-only selected-path property
     */
    public final ReadOnlyObjectProperty<RXCascaderPath<T>> selectedPathProperty() {
        return view.selectedPathProperty();
    }

    /**
     * Returns the selected path.
     *
     * @return selected path, or {@code null}
     */
    public final RXCascaderPath<T> getSelectedPath() {
        return view.getSelectedPath();
    }

    /**
     * Checked leaf paths in multiple-selection mode.
     *
     * @return read-only checked path list maintained by the view
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

    // ==================== Text Factory ====================

    /**
     * Converts an item value to its display text (single source of the visible
     * node text). When {@code null}, {@code String.valueOf(value)} is used.
     *
     * @return text-factory property
     */
    public final ObjectProperty<Callback<T, String>> textFactoryProperty() {
        return view.textFactoryProperty();
    }

    /**
     * Returns the text factory.
     *
     * @return text factory, or {@code null}
     */
    public final Callback<T, String> getTextFactory() {
        return view.getTextFactory();
    }

    /**
     * Sets the text factory.
     *
     * @param value text factory, or {@code null}
     */
    public final void setTextFactory(Callback<T, String> value) {
        view.setTextFactory(value);
    }

    // ==================== Path Text Factory ====================

    private final ObjectProperty<Callback<List<String>, String>> pathTextFactory =
            new SimpleObjectProperty<>(this, "pathTextFactory");

    /**
     * Optional formatter from a path's already-resolved per-node display texts
     * (root-to-leaf, produced by {@link #getTextFactory() textFactory}) to the
     * single string shown in the field. When {@code null} the node texts are
     * joined with {@code " / "}.
     *
     * @return path-text factory property
     */
    public final ObjectProperty<Callback<List<String>, String>> pathTextFactoryProperty() {
        return pathTextFactory;
    }

    /**
     * Returns the path-text factory.
     *
     * @return path-text factory, or {@code null}
     */
    public final Callback<List<String>, String> getPathTextFactory() {
        return pathTextFactory.get();
    }

    /**
     * Sets the path-text factory.
     *
     * @param value path-text factory, or {@code null}
     */
    public final void setPathTextFactory(Callback<List<String>, String> value) {
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

    // ==================== Delegated View Properties ====================

    /**
     * Number of visible popup rows used for preferred popup height.
     *
     * @return visible-row-count property
     */
    public final IntegerProperty visibleRowCountProperty() {
        return view.visibleRowCountProperty();
    }

    /**
     * Returns the visible row count.
     *
     * @return visible row count
     */
    public final int getVisibleRowCount() {
        return view.getVisibleRowCount();
    }

    /**
     * Sets the visible row count.
     *
     * @param value visible row count
     */
    public final void setVisibleRowCount(int value) {
        view.setVisibleRowCount(value);
    }

    /**
     * Optional factory for the cells of each popup column.
     *
     * @return cell-factory property
     */
    public final ObjectProperty<Callback<RXCascaderView<T>, ListCell<RXCascaderItem<T>>>> cellFactoryProperty() {
        return view.cellFactoryProperty();
    }

    /**
     * Returns the cell factory.
     *
     * @return cell factory, or {@code null}
     */
    public final Callback<RXCascaderView<T>, ListCell<RXCascaderItem<T>>> getCellFactory() {
        return view.getCellFactory();
    }

    /**
     * Sets the cell factory.
     *
     * @param value cell factory, or {@code null}
     */
    public final void setCellFactory(Callback<RXCascaderView<T>, ListCell<RXCascaderItem<T>>> value) {
        view.setCellFactory(value);
    }

    /**
     * Optional asynchronous loader used by unloaded branches.
     *
     * @return children-loader property
     */
    public final ObjectProperty<Function<RXCascaderItem<T>, CompletionStage<List<RXCascaderItem<T>>>>>
            childrenLoaderProperty() {
        return view.childrenLoaderProperty();
    }

    /**
     * Returns the children loader.
     *
     * @return children loader, or {@code null}
     */
    public final Function<RXCascaderItem<T>, CompletionStage<List<RXCascaderItem<T>>>> getChildrenLoader() {
        return view.getChildrenLoader();
    }

    /**
     * Sets the children loader.
     *
     * @param value children loader, or {@code null}
     */
    public final void setChildrenLoader(
            Function<RXCascaderItem<T>, CompletionStage<List<RXCascaderItem<T>>>> value) {
        view.setChildrenLoader(value);
    }

    /**
     * Optional callback invoked on the JavaFX thread when a lazy children load
     * fails.
     *
     * @return children-load-error callback property
     */
    public final ObjectProperty<BiConsumer<RXCascaderItem<T>, Throwable>> onChildrenLoadErrorProperty() {
        return view.onChildrenLoadErrorProperty();
    }

    /**
     * Returns the children-load-error callback.
     *
     * @return callback, or {@code null}
     */
    public final BiConsumer<RXCascaderItem<T>, Throwable> getOnChildrenLoadError() {
        return view.getOnChildrenLoadError();
    }

    /**
     * Sets the children-load-error callback.
     *
     * @param value callback, or {@code null}
     */
    public final void setOnChildrenLoadError(BiConsumer<RXCascaderItem<T>, Throwable> value) {
        view.setOnChildrenLoadError(value);
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
}
