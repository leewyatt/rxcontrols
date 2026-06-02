package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXCascaderViewSkin;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Standalone multi-column cascader view. It owns the active path, single
 * selection, multiple checked paths, disabled inheritance, and cascading
 * tri-state check logic.
 *
 * <p>This control is usable on its own — drop it inline into any layout to get
 * a multi-column cascader without an input field or popup, and observe
 * {@link #selectedPathProperty()} / {@link #getCheckedPaths()} for the result.
 * {@link RXCascader} reuses it as its popup content.
 *
 * @param <T> application value type
 */
public class RXCascaderView<T> extends Control {

    /**
     * Default column width in pixels.
     */
    public static final double DEFAULT_COLUMN_WIDTH = 180.0;

    /**
     * Default row height in pixels.
     */
    public static final double DEFAULT_ROW_HEIGHT = 34.0;

    /**
     * Default visible row count.
     */
    public static final int DEFAULT_VISIBLE_ROW_COUNT = 6;

    private static final String DEFAULT_STYLE_CLASS = "rx-cascader-view";

    private final ObservableList<RXCascaderItem<T>> rootItems =
            FXCollections.observableArrayList();

    private final ObservableList<RXCascaderItem<T>> activePath =
            FXCollections.observableArrayList();

    private final ObservableList<RXCascaderItem<T>> readOnlyActivePath =
            FXCollections.unmodifiableObservableList(activePath);

    private final ObservableList<RXCascaderPath<T>> checkedPaths =
            FXCollections.observableArrayList();

    private final ObservableList<RXCascaderPath<T>> readOnlyCheckedPaths =
            FXCollections.unmodifiableObservableList(checkedPaths);

    private final Map<RXCascaderItem<T>, Long> loadGenerations = new IdentityHashMap<>();
    private final Map<RXCascaderItem<T>, Boolean> pendingChecks = new IdentityHashMap<>();
    private long nextLoadGeneration;

    /**
     * Creates an empty cascader panel.
     */
    public RXCascaderView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        rootItems.addListener((ListChangeListener<RXCascaderItem<T>>) change -> {
            while (change.next()) {
                for (RXCascaderItem<T> removed : change.getRemoved()) {
                    removed.setParentItem(null);
                    loadGenerations.remove(removed);
                    pendingChecks.remove(removed);
                }
                for (RXCascaderItem<T> added : change.getAddedSubList()) {
                    if (added != null) {
                        added.setParentItem(null);
                    }
                }
            }
            activePath.clear();
            selectedPath.set(null);
            refreshCheckedPaths();
            requestLayout();
        });
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXCascaderViewSkin<>(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Items ====================

    /**
     * Root items shown in the first column.
     *
     * @return mutable root item list
     */
    public final ObservableList<RXCascaderItem<T>> getRootItems() {
        return rootItems;
    }

    /**
     * Expanded branch path.
     *
     * @return read-only active path list
     */
    public final ObservableList<RXCascaderItem<T>> getActivePath() {
        return readOnlyActivePath;
    }

    // ==================== Selection Mode ====================

    private final ObjectProperty<RXCascaderSelectionMode> selectionMode =
            new SimpleObjectProperty<>(this, "selectionMode", RXCascaderSelectionMode.SINGLE) {
                private RXCascaderSelectionMode lastValid = RXCascaderSelectionMode.SINGLE;

                @Override
                protected void invalidated() {
                    RXCascaderSelectionMode value = get();
                    if (value == null) {
                        if (!isBound()) {
                            set(lastValid);
                        }
                        throw new NullPointerException("selectionMode cannot be null");
                    }
                    lastValid = value;
                    clearSelection();
                    requestLayout();
                }
            };

    /**
     * Selection mode.
     *
     * @return selection-mode property
     */
    public final ObjectProperty<RXCascaderSelectionMode> selectionModeProperty() {
        return selectionMode;
    }

    /**
     * Returns the selection mode.
     *
     * @return selection mode
     */
    public final RXCascaderSelectionMode getSelectionMode() {
        return selectionMode.get();
    }

    /**
     * Sets the selection mode.
     *
     * @param value selection mode
     */
    public final void setSelectionMode(RXCascaderSelectionMode value) {
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
     * @return read-only checked path list maintained by this panel
     */
    public final ObservableList<RXCascaderPath<T>> getCheckedPaths() {
        return readOnlyCheckedPaths;
    }

    // ==================== Column Width ====================

    private final DoubleProperty columnWidth =
            new SimpleDoubleProperty(this, "columnWidth", DEFAULT_COLUMN_WIDTH);

    /**
     * Preferred width for each column.
     *
     * @return column-width property
     */
    public final DoubleProperty columnWidthProperty() {
        return columnWidth;
    }

    /**
     * Returns the preferred column width.
     *
     * @return preferred column width
     */
    public final double getColumnWidth() {
        return columnWidth.get();
    }

    /**
     * Sets the preferred column width.
     *
     * @param value preferred column width
     */
    public final void setColumnWidth(double value) {
        columnWidth.set(value);
    }

    // ==================== Row Height ====================

    private final DoubleProperty rowHeight =
            new SimpleDoubleProperty(this, "rowHeight", DEFAULT_ROW_HEIGHT);

    /**
     * Fixed row height for all columns.
     *
     * @return row-height property
     */
    public final DoubleProperty rowHeightProperty() {
        return rowHeight;
    }

    /**
     * Returns the fixed row height.
     *
     * @return fixed row height
     */
    public final double getRowHeight() {
        return rowHeight.get();
    }

    /**
     * Sets the fixed row height.
     *
     * @param value fixed row height
     */
    public final void setRowHeight(double value) {
        rowHeight.set(value);
    }

    // ==================== Visible Row Count ====================

    private final IntegerProperty visibleRowCount =
            new SimpleIntegerProperty(this, "visibleRowCount", DEFAULT_VISIBLE_ROW_COUNT);

    /**
     * Number of visible rows used for preferred panel height.
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

    // ==================== Option Content Factory ====================

    private final ObjectProperty<Callback<RXCascaderItem<T>, Node>> optionContentFactory =
            new SimpleObjectProperty<>(this, "optionContentFactory");

    /**
     * Optional factory for the content area of a row.
     *
     * @return option-content factory property
     */
    public final ObjectProperty<Callback<RXCascaderItem<T>, Node>> optionContentFactoryProperty() {
        return optionContentFactory;
    }

    /**
     * Returns the option-content factory.
     *
     * @return option-content factory, or {@code null}
     */
    public final Callback<RXCascaderItem<T>, Node> getOptionContentFactory() {
        return optionContentFactory.get();
    }

    /**
     * Sets the option-content factory.
     *
     * @param value option-content factory, or {@code null}
     */
    public final void setOptionContentFactory(Callback<RXCascaderItem<T>, Node> value) {
        optionContentFactory.set(value);
    }

    // ==================== Children Loader ====================

    private final ObjectProperty<Function<RXCascaderItem<T>, CompletionStage<List<RXCascaderItem<T>>>>>
            childrenLoader = new SimpleObjectProperty<>(this, "childrenLoader");

    /**
     * Optional asynchronous loader used when an unloaded branch is expanded or
     * checked. The returned stage should complete with the loaded child items.
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

    // ==================== Public Operations ====================

    /**
     * Handles normal row activation.
     *
     * @param item item to activate
     */
    public final void activate(RXCascaderItem<T> item) {
        if (item == null || isEffectivelyDisabled(item)) {
            return;
        }
        if (getSelectionMode() == RXCascaderSelectionMode.MULTIPLE) {
            if (isLeaf(item)) {
                toggleCheck(item);
            } else {
                expand(item);
            }
            return;
        }
        if (isLeaf(item)) {
            selectedPath.set(createPath(item));
        } else {
            expand(item);
        }
    }

    /**
     * Expands a branch item.
     *
     * @param item branch item
     */
    public final void expand(RXCascaderItem<T> item) {
        if (item == null || isEffectivelyDisabled(item) || isLeaf(item)) {
            return;
        }
        activePath.setAll(pathItems(item));
        loadChildren(item);
        requestLayout();
    }

    /**
     * Toggles a check state in multiple-selection mode.
     *
     * @param item item to toggle
     */
    public final void toggleCheck(RXCascaderItem<T> item) {
        if (item == null || getSelectionMode() != RXCascaderSelectionMode.MULTIPLE) {
            return;
        }
        setCheckedCascade(item, !areEnabledLeavesChecked(item));
    }

    /**
     * Sets a check state in multiple-selection mode.
     *
     * @param item item to update
     * @param checked target checked state
     */
    public final void setCheckedCascade(RXCascaderItem<T> item, boolean checked) {
        if (item == null || isEffectivelyDisabled(item)) {
            return;
        }
        if (!item.isLoaded() && getChildrenLoader() != null) {
            pendingChecks.put(item, checked);
            item.setChecked(checked);
            item.setIndeterminate(false);
            loadChildren(item);
            updateUp(item.getParent());
            refreshCheckedPaths();
            requestLayout();
            return;
        }
        applyDown(item, checked);
        updateUp(item.getParent());
        refreshCheckedPaths();
        requestLayout();
    }

    /**
     * Clears both single and multiple selection state.
     */
    public final void clearSelection() {
        selectedPath.set(null);
        pendingChecks.clear();
        for (RXCascaderItem<T> root : rootItems) {
            clearCheckState(root);
        }
        checkedPaths.clear();
        requestLayout();
    }

    /**
     * Returns whether an item is effectively disabled.
     *
     * @param item item to test
     * @return {@code true} if disabled directly or by an ancestor
     */
    public final boolean isEffectivelyDisabled(RXCascaderItem<T> item) {
        if (item == null) {
            return false;
        }
        return item.isDisabled() || isEffectivelyDisabled(item.getParent());
    }

    /**
     * Returns whether an item is a leaf.
     *
     * @param item item to test
     * @return {@code true} if leaf
     */
    public final boolean isLeaf(RXCascaderItem<T> item) {
        if (item == null) {
            return true;
        }
        Boolean hint = item.getLeafHint();
        if (hint != null) {
            return hint;
        }
        if (!item.isLoaded() && getChildrenLoader() != null) {
            return false;
        }
        return item.getChildren().isEmpty();
    }

    /**
     * Starts loading children for an unloaded branch when a loader is present.
     *
     * @param item branch item to load
     */
    public final void loadChildren(RXCascaderItem<T> item) {
        Function<RXCascaderItem<T>, CompletionStage<List<RXCascaderItem<T>>>> loader = getChildrenLoader();
        if (item == null || loader == null || item.isLoaded() || item.isLoading()) {
            return;
        }

        long generation = ++nextLoadGeneration;
        loadGenerations.put(item, generation);
        item.setLoading(true);

        CompletionStage<List<RXCascaderItem<T>>> stage;
        try {
            stage = loader.apply(item);
        } catch (RuntimeException e) {
            item.setLoading(false);
            loadGenerations.remove(item);
            throw e;
        }

        if (stage == null) {
            completeLoad(item, generation, Collections.emptyList(), null);
            return;
        }
        stage.whenComplete((children, error) ->
                runOnFxThread(() -> completeLoad(item, generation, children, error)));
    }

    /**
     * Creates an immutable path snapshot for an item.
     *
     * @param item leaf or branch item
     * @return path snapshot
     */
    public final RXCascaderPath<T> createPath(RXCascaderItem<T> item) {
        return new RXCascaderPath<>(pathItems(item));
    }

    // ==================== State helpers ====================

    private void applyDown(RXCascaderItem<T> item, boolean checked) {
        if (isEffectivelyDisabled(item)) {
            return;
        }
        if (!item.isLoaded() && getChildrenLoader() != null) {
            pendingChecks.put(item, checked);
            item.setChecked(checked);
            item.setIndeterminate(false);
            loadChildren(item);
            return;
        }
        for (RXCascaderItem<T> child : item.getChildren()) {
            applyDown(child, checked);
        }
        if (isLeaf(item)) {
            item.setChecked(checked);
            item.setIndeterminate(false);
        } else {
            updateFromChildren(item);
        }
    }

    private void updateUp(RXCascaderItem<T> item) {
        RXCascaderItem<T> current = item;
        while (current != null) {
            updateFromChildren(current);
            current = current.getParent();
        }
    }

    private void updateFromChildren(RXCascaderItem<T> item) {
        int total = item.getChildren().size();
        boolean allChildrenChecked = total > 0;
        for (RXCascaderItem<T> child : item.getChildren()) {
            if (!child.isChecked() || child.isIndeterminate()) {
                allChildrenChecked = false;
                break;
            }
        }
        item.setChecked(allChildrenChecked);
        applyIndeterminateFromChildren(item);
    }

    private void applyIndeterminateFromChildren(RXCascaderItem<T> item) {
        int total = item.getChildren().size();
        double checkedWeight = 0.0;
        for (RXCascaderItem<T> child : item.getChildren()) {
            if (child.isIndeterminate()) {
                checkedWeight += 0.5;
            } else if (child.isChecked()) {
                checkedWeight += 1.0;
            }
        }
        item.setIndeterminate(total > 0 && checkedWeight != total && checkedWeight > 0.0);
    }

    private boolean areEnabledLeavesChecked(RXCascaderItem<T> item) {
        EnabledLeafSummary summary = enabledLeafSummary(item);
        return summary.hasLeaf && summary.allChecked;
    }

    private EnabledLeafSummary enabledLeafSummary(RXCascaderItem<T> item) {
        if (isEffectivelyDisabled(item)) {
            return new EnabledLeafSummary(false, true);
        }
        if (isLeaf(item) || (!item.isLoaded() && getChildrenLoader() != null)) {
            return new EnabledLeafSummary(true, item.isChecked());
        }

        boolean hasLeaf = false;
        boolean allChecked = true;
        for (RXCascaderItem<T> child : item.getChildren()) {
            EnabledLeafSummary childSummary = enabledLeafSummary(child);
            if (childSummary.hasLeaf) {
                hasLeaf = true;
                if (!childSummary.allChecked) {
                    allChecked = false;
                }
            }
        }
        return new EnabledLeafSummary(hasLeaf, allChecked);
    }

    private record EnabledLeafSummary(boolean hasLeaf, boolean allChecked) {
    }

    private void completeLoad(RXCascaderItem<T> item, long generation,
                              List<RXCascaderItem<T>> children, Throwable error) {
        Long currentGeneration = loadGenerations.get(item);
        if (currentGeneration == null || currentGeneration != generation) {
            return;
        }
        loadGenerations.remove(item);
        item.setLoading(false);

        if (error != null) {
            Boolean pendingCheck = pendingChecks.remove(item);
            if (pendingCheck != null) {
                item.setChecked(false);
                item.setIndeterminate(false);
                updateUp(item.getParent());
                refreshCheckedPaths();
            }
            requestLayout();
            return;
        }

        List<RXCascaderItem<T>> loadedChildren = children == null ? Collections.emptyList() : children;
        item.getChildren().setAll(loadedChildren);
        item.setLoaded(true);

        Boolean pendingCheck = pendingChecks.remove(item);
        if (pendingCheck != null) {
            applyDown(item, pendingCheck);
            updateUp(item.getParent());
            refreshCheckedPaths();
        }
        requestLayout();
    }

    private void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private void clearCheckState(RXCascaderItem<T> item) {
        item.setChecked(false);
        item.setIndeterminate(false);
        for (RXCascaderItem<T> child : item.getChildren()) {
            clearCheckState(child);
        }
    }

    private void refreshCheckedPaths() {
        List<RXCascaderPath<T>> paths = new ArrayList<>();
        for (RXCascaderItem<T> root : rootItems) {
            collectCheckedLeafPaths(root, paths);
        }
        checkedPaths.setAll(paths);
    }

    private void collectCheckedLeafPaths(RXCascaderItem<T> item, List<RXCascaderPath<T>> paths) {
        if (isLeaf(item)) {
            if (item.isChecked()) {
                paths.add(createPath(item));
            }
            return;
        }
        for (RXCascaderItem<T> child : item.getChildren()) {
            collectCheckedLeafPaths(child, paths);
        }
    }

    private List<RXCascaderItem<T>> pathItems(RXCascaderItem<T> item) {
        List<RXCascaderItem<T>> path = new ArrayList<>();
        RXCascaderItem<T> current = item;
        while (current != null) {
            path.add(0, current);
            current = current.getParent();
        }
        return path;
    }
}
