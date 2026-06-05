package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXCascaderViewSkin;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Control;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
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
     * Default fixed cell size (row height) in pixels.
     */
    public static final double DEFAULT_FIXED_CELL_SIZE = 34.0;

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

    /** Sentinel returned by {@link #startLoad} when no load was started; live generations are >= 1. */
    private static final long NO_LOAD = 0L;

    private final Map<RXCascaderItem<T>, Long> loadGenerations = new IdentityHashMap<>();
    private final Map<RXCascaderItem<T>, Boolean> pendingChecks = new IdentityHashMap<>();
    private long nextLoadGeneration;

    /**
     * Creates an empty cascader view.
     */
    public RXCascaderView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        rootItems.addListener((ListChangeListener<RXCascaderItem<T>>) change -> {
            while (change.next()) {
                for (RXCascaderItem<T> removed : change.getRemoved()) {
                    removed.setParentItem(null);
                }
                for (RXCascaderItem<T> added : change.getAddedSubList()) {
                    if (added != null) {
                        added.setParentItem(null);
                    }
                }
            }
            // Swapping roots is one of the three reset entry points: drop all
            // navigation and in-flight loads, but keep the new roots' children
            // and any seeded check state, then rebuild the derived paths.
            clearNavAndPending();
            refreshCheckedPaths();
            requestLayout();
        });
        childrenLoader.addListener((obs, oldLoader, newLoader) -> {
            if (newLoader != null) {
                resetTree();
            } else {
                switchToEager();
            }
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
     * @return read-only checked path list maintained by this view
     */
    public final ObservableList<RXCascaderPath<T>> getCheckedPaths() {
        return readOnlyCheckedPaths;
    }

    // ==================== Visible Row Count ====================

    private final IntegerProperty visibleRowCount =
            new SimpleIntegerProperty(this, "visibleRowCount", DEFAULT_VISIBLE_ROW_COUNT);

    /**
     * Preferred visible row count: the panel shows this many row slots. Fewer
     * items leave blank space, more items scroll within the column. (This is a
     * fixed row-slot count, not an "at most N rows" cap.)
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
     * Optional factory for the cells of each column. When {@code null} the view
     * uses the built-in {@link RXCascaderCell}. The factory receives this view so
     * a custom cell can route interaction back to it; it may return an
     * {@link RXCascaderCell} subclass (recommended) or any {@link ListCell}.
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

    // ==================== Item Text Factory ====================

    private final ObjectProperty<Callback<T, String>> itemTextFactory =
            new SimpleObjectProperty<>(this, "itemTextFactory");

    /**
     * Converts an item value to its display text. When {@code null} the view
     * falls back to {@code String.valueOf(value)}. Items do not store text; this
     * is the single source of the visible node text, used by the built-in cell
     * and by the field's default path text.
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

    // ==================== Children Load Error ====================

    private final ObjectProperty<BiConsumer<RXCascaderItem<T>, Throwable>> onChildrenLoadError =
            new SimpleObjectProperty<>(this, "onChildrenLoadError");

    /**
     * Optional callback invoked on the JavaFX thread when a lazy children load
     * fails, either because the loader stage completes exceptionally or because
     * {@code childrenLoader.apply} throws synchronously. The failing item stays a
     * retriable branch (no column is added); expanding it again retries. When
     * {@code null} the failure is silent.
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
        // Three-step order: (1) establish the loading state so the skin's first
        // rebuild already sees a loading frontier (deferred column + frontier
        // monitor), (2) retarget the active path, (3) only then invoke the loader.
        // Invoking last means a loader that completes or fails inline runs its
        // completion (and any error callback) after the active path is in place,
        // matching the async path and avoiding a later setAll clobbering it.
        long generation = startLoad(item);
        activePath.setAll(pathItems(item));
        if (generation != NO_LOAD) {
            runLoad(item, generation);
        }
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
        if (isUnresolvedLazyBranch(item)) {
            // Record (or overwrite) the pending intent; loadChildren no-ops if a
            // load is already in flight, so a second check while loading still
            // updates the pending value to honor the user's latest action.
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
     * Forces a same-source reload of the whole lazy tree: every root is reset to
     * an unloaded branch, navigation and selection are cleared, and children are
     * lazily fetched again with the current loader. In eager mode (no loader set)
     * this is a no-op.
     */
    public final void reload() {
        if (getChildrenLoader() == null) {
            return;
        }
        resetTree();
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
        if (getChildrenLoader() == null) {
            return item.getChildren().isEmpty();
        }
        return item.isLoaded() && item.getChildren().isEmpty();
    }

    /**
     * Whether this is a lazy branch whose children are not yet available: a
     * loader is present, the item is unloaded, childless, and not declared a
     * leaf. A check on such a node is recorded as pending and replayed once the
     * children arrive. Unlike {@link #needsLoad}, this stays {@code true} while
     * the node is already loading, so a later check still overwrites the pending
     * intent instead of being ignored.
     *
     * @param item item to test
     * @return {@code true} if the children are not yet resolved
     */
    private boolean isUnresolvedLazyBranch(RXCascaderItem<T> item) {
        return getChildrenLoader() != null
                && !item.isLoaded()
                && item.getChildren().isEmpty()
                && !Boolean.TRUE.equals(item.getLeafHint());
    }

    /**
     * Whether expanding or checking this item should start a new lazy load: an
     * unresolved lazy branch that is not already loading.
     *
     * @param item item to test
     * @return {@code true} if a lazy load should be started
     */
    private boolean needsLoad(RXCascaderItem<T> item) {
        return isUnresolvedLazyBranch(item) && !item.isLoading();
    }

    /**
     * Starts loading children for an unloaded branch when a loader is present.
     *
     * @param item branch item to load
     */
    public final void loadChildren(RXCascaderItem<T> item) {
        long generation = startLoad(item);
        if (generation != NO_LOAD) {
            runLoad(item, generation);
        }
    }

    /**
     * Establishes the loading state for a branch that needs a lazy load: flips
     * {@code loading} on and registers the generation, without yet invoking the
     * loader. Returning the generation lets the caller defer the actual
     * {@link #runLoad} until after navigation is in place (see {@link #expand}).
     *
     * @param item branch item to load
     * @return the load generation, or {@link #NO_LOAD} if no load is needed
     */
    private long startLoad(RXCascaderItem<T> item) {
        if (item == null || !needsLoad(item)) {
            return NO_LOAD;
        }
        long generation = ++nextLoadGeneration;
        loadGenerations.put(item, generation);
        item.setLoading(true);
        return generation;
    }

    /**
     * Invokes the loader and routes its result (or failure) to
     * {@link #completeLoad}. Must be paired with a prior {@link #startLoad} that
     * returned {@code generation}.
     */
    private void runLoad(RXCascaderItem<T> item, long generation) {
        Function<RXCascaderItem<T>, CompletionStage<List<RXCascaderItem<T>>>> loader = getChildrenLoader();
        Long current = loadGenerations.get(item);
        if (loader == null || current == null || current != generation) {
            // A listener that ran during the active-path update between startLoad
            // and here (reload(), a loader swap, or a root change) already
            // canceled this load and reset the item's loading flag; do not invoke
            // the possibly side-effecting loader for a superseded request.
            return;
        }

        CompletionStage<List<RXCascaderItem<T>>> stage;
        try {
            stage = loader.apply(item);
        } catch (RuntimeException e) {
            // A synchronous throw routes to the same failure path as a stage
            // error: a retriable branch plus the error callback, never rethrown.
            completeLoad(item, generation, null, e);
            return;
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
        if (isUnresolvedLazyBranch(item)) {
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
        if (isLeaf(item) || isUnresolvedLazyBranch(item)) {
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

        if (error != null) {
            // Keep loaded=false so the branch can be retried; roll back any
            // pending check and surface the failure through the callback.
            item.setLoading(false);
            Boolean pendingCheck = pendingChecks.remove(item);
            if (pendingCheck != null) {
                item.setChecked(false);
                item.setIndeterminate(false);
                updateUp(item.getParent());
                refreshCheckedPaths();
            }
            BiConsumer<RXCascaderItem<T>, Throwable> handler = getOnChildrenLoadError();
            if (handler != null) {
                handler.accept(item, error);
            }
            requestLayout();
            return;
        }

        List<RXCascaderItem<T>> loadedChildren = children == null ? Collections.emptyList() : children;
        item.getChildren().setAll(loadedChildren);
        item.setLoaded(true);
        // Flip loading off last so the skin's frontier monitor, which rebuilds on
        // this transition, observes the fully populated, loaded branch.
        item.setLoading(false);

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

    // ==================== Reset and invalidation ====================

    /**
     * Cancels all in-flight loads: every node currently loading is reset to a
     * stable {@code loading=false} before it can leave the tree (so a detached
     * item never keeps a dangling loading flag), and the generation / pending
     * maps are cleared so late callbacks bail in {@link #completeLoad}.
     */
    private void cancelInFlight() {
        for (RXCascaderItem<T> item : loadGenerations.keySet()) {
            item.setLoading(false);
        }
        loadGenerations.clear();
        pendingChecks.clear();
    }

    /**
     * Shared invalidation core for the three reset entry points: cancels
     * in-flight loads and clears navigation. It intentionally leaves
     * {@code checkedPaths} and each item's checked state alone — those are
     * handled per entry point.
     */
    private void clearNavAndPending() {
        cancelInFlight();
        activePath.clear();
        selectedPath.set(null);
    }

    /**
     * Full-tree reset shared by {@link #reload()} and switching to a non-null
     * loader: clears navigation and in-flight loads, then discards everything
     * below the same roots and all check state so the tree returns to a blank
     * slate ready to lazily reload.
     */
    private void resetTree() {
        clearNavAndPending();
        for (RXCascaderItem<T> root : rootItems) {
            clearCheckState(root);
            root.getChildren().clear();
            root.setLoaded(false);
            root.setLoading(false);
        }
        checkedPaths.clear();
        requestLayout();
    }

    /**
     * Switches to eager mode when the loader is cleared: cancels in-flight loads
     * but keeps the current tree, navigation, and item check state as a static
     * tree. The derived checked paths are recomputed because clearing the loader
     * changes which nodes are leaves.
     */
    private void switchToEager() {
        cancelInFlight();
        refreshCheckedPaths();
        requestLayout();
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
