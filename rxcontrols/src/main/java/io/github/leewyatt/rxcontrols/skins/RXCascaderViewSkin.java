package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXCascaderCell;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderItem.LoadState;
import io.github.leewyatt.rxcontrols.RXCascaderView;
import javafx.collections.ObservableList;
import javafx.css.StyleOrigin;
import javafx.css.StyleableProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;

/**
 * Default skin for {@link RXCascaderView}. Renders the active path as one
 * {@link ListView} per column inside a horizontal box; cells come from
 * {@link RXCascaderView#cellFactoryProperty()} or default to
 * {@link RXCascaderCell}.
 *
 * @param <T> application value type
 */
public class RXCascaderViewSkin<T> extends RXSkinBase<RXCascaderView<T>> {

    // ==================== Constants ====================

    private static final String COLUMN_STYLE_CLASS = "rx-cascader-column";
    private static final String EMPTY_PLACEHOLDER_STYLE_CLASS = "rx-cascader-empty";
    private static final String EMPTY_PLACEHOLDER_TEXT = "No data";
    private static final int MIN_VISIBLE_ROW_COUNT = 1;

    // ==================== Nodes ====================

    private final HBox columnsBox = new HBox();
    private final List<ListView<RXCascaderItem<T>>> columns = new ArrayList<>();

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given view.
     *
     * @param control the skinnable view
     */
    public RXCascaderViewSkin(RXCascaderView<T> control) {
        super(control);
        columnsBox.getStyleClass().add("columns");
        getChildren().setAll(columnsBox);
        registerListeners(control);
        syncColumns();
    }

    private void registerListeners(RXCascaderView<T> control) {
        // Column structure: one explicit signal from the control replaces the old
        // active-path / root-list / loader listeners and the frontier monitor.
        disposer.registerListener(control.columnsRevisionProperty(), this::syncColumns);
        // Cell rendering only (same columns, cells re-render):
        disposer.registerListener(control.selectionModeProperty(), () -> {
            refreshColumns();
            control.requestLayout();
        });
        disposer.registerListener(control.selectedPathProperty(), this::refreshColumns);
        disposer.registerListener(control.getCheckedPaths(), this::refreshColumns);
        disposer.registerListener(control.visibleRowCountProperty(), control::requestLayout);
        disposer.registerListener(control.columnWidthProperty(), this::applyColumnSizing);
        disposer.registerListener(control.rowHeightProperty(), this::applyColumnSizing);
        disposer.registerListener(control.itemTextFactoryProperty(), this::refreshColumns);
        // A new cell factory changes the cell type, so every column must be rebuilt;
        // the tail-diff reuses by backing-list identity and would keep stale cells.
        disposer.registerListener(control.cellFactoryProperty(), this::rebuildAllColumns);
    }

    // ==================== Columns ====================

    /**
     * Re-syncs the rendered columns to the desired set with a keep-prefix /
     * replace-tail diff: columns whose backing list is unchanged (by identity) are
     * reused, only the changed tail is disposed and rebuilt, and the ordinal style
     * classes are restamped by position. This avoids the flicker and defensive CSS
     * re-pass of a full teardown.
     */
    private void syncColumns() {
        List<ObservableList<RXCascaderItem<T>>> desired = desiredBackingLists();
        int keep = 0;
        while (keep < columns.size() && keep < desired.size()
                && columns.get(keep).getItems() == desired.get(keep)) {
            keep++;
        }
        boolean changed = keep < columns.size() || keep < desired.size();
        for (int i = columns.size() - 1; i >= keep; i--) {
            disposeColumn(columns.remove(i));
        }
        for (int i = keep; i < desired.size(); i++) {
            columns.add(createColumn(desired.get(i)));
        }
        restampOrdinals();
        columnsBox.getChildren().setAll(columns);
        // Reused prefix columns (0..keep-1) keep their ListView (no teardown) but
        // their cells' active-path / selected / checked highlights derive from view
        // state, not item state, so the cells' own listeners do not catch a path
        // change; refresh them. Newly created tail columns already render fresh.
        for (int i = 0; i < keep; i++) {
            columns.get(i).refresh();
        }
        // Only newly created columns need a CSS pass (so author CSS overrides the
        // code defaults before pref measurement); reused columns already have it.
        if (changed && getSkinnable().getScene() != null) {
            columnsBox.applyCss();
        }
    }

    /**
     * The backing lists, in order, that the rendered columns should show: the root
     * items, then the children of each active-path branch that should get a column.
     */
    private List<ObservableList<RXCascaderItem<T>>> desiredBackingLists() {
        List<ObservableList<RXCascaderItem<T>>> lists = new ArrayList<>();
        lists.add(getSkinnable().getRootItems());
        for (RXCascaderItem<T> item : getSkinnable().getActivePath()) {
            if (shouldAddColumn(item)) {
                lists.add(item.getChildren());
            }
        }
        return lists;
    }

    /**
     * Whether the children of an active-path branch should get a column. A
     * loading frontier is deferred (no column until it finishes), and a lazy
     * branch that failed or has not loaded yet shows no empty column. Only an
     * eager branch, a loaded lazy branch, or one with already-attached children
     * gets a column.
     */
    private boolean shouldAddColumn(RXCascaderItem<T> item) {
        RXCascaderView<T> view = getSkinnable();
        if (view.isLeaf(item) || item.getLoadState() == LoadState.LOADING) {
            return false;
        }
        return view.getChildrenLoader() == null
                || item.getLoadState() == LoadState.LOADED
                || !item.getChildren().isEmpty();
    }

    private ListView<RXCascaderItem<T>> createColumn(ObservableList<RXCascaderItem<T>> items) {
        ListView<RXCascaderItem<T>> listView = new ListView<>(items);
        listView.getStyleClass().add(COLUMN_STYLE_CLASS);
        listView.setFocusTraversable(false);
        // Discoverable defaults from the view's -rx-column-width / -rx-row-height;
        // author CSS (-fx-pref-width / -fx-fixed-cell-size) on .rx-cascader-column
        // still overrides because AUTHOR origin outranks the USER origin of these
        // set calls. min/max are left unset so a single column can be widened via
        // CSS while HBox (hgrow=NEVER) keeps each column at its preferred width.
        listView.setPrefWidth(columnWidthOrDefault());
        listView.setFixedCellSize(rowHeightOrDefault());
        // Only a forced-branch (leafHint=false) column ends up empty; loading
        // shows no column and a loaded-empty branch is a leaf, so this only ever
        // renders for that one case.
        Label placeholder = new Label(EMPTY_PLACEHOLDER_TEXT);
        placeholder.getStyleClass().add(EMPTY_PLACEHOLDER_STYLE_CLASS);
        listView.setPlaceholder(placeholder);
        Callback<RXCascaderView<T>, ListCell<RXCascaderItem<T>>> factory = getSkinnable().getCellFactory();
        listView.setCellFactory(view -> factory != null
                ? factory.call(getSkinnable())
                : new RXCascaderCell<>(getSkinnable()));
        return listView;
    }

    /**
     * Stamps each column's positional ordinal style class
     * ({@code rx-cascader-column-N}), removing any stale ordinal so a tail-diff
     * always leaves lookups and author CSS targeting the column at that position.
     */
    private void restampOrdinals() {
        String ordinalPrefix = COLUMN_STYLE_CLASS + "-";
        for (int i = 0; i < columns.size(); i++) {
            ListView<RXCascaderItem<T>> column = columns.get(i);
            column.getStyleClass().removeIf(styleClass -> styleClass.startsWith(ordinalPrefix));
            column.getStyleClass().add(ordinalPrefix + i);
        }
    }

    private void disposeColumn(ListView<RXCascaderItem<T>> column) {
        column.setCellFactory(null);
        column.setItems(null);
    }

    private void disposeColumns() {
        for (ListView<RXCascaderItem<T>> column : columns) {
            disposeColumn(column);
        }
        columnsBox.getChildren().clear();
        columns.clear();
    }

    /**
     * Full rebuild used when the cell factory changes: dispose every column and
     * re-sync so all columns are recreated with the new cell type.
     */
    private void rebuildAllColumns() {
        disposeColumns();
        syncColumns();
    }

    private void refreshColumns() {
        for (ListView<RXCascaderItem<T>> column : columns) {
            column.refresh();
        }
    }

    /**
     * Re-applies the view's column-width / row-height defaults (USER origin) to the
     * existing columns when those properties change. A column whose size is already
     * set by author CSS (AUTHOR / INLINE origin) is left untouched, so the
     * "author wins" contract holds even for runtime size changes.
     */
    private void applyColumnSizing() {
        for (ListView<RXCascaderItem<T>> column : columns) {
            if (!cssAuthored(column.prefWidthProperty())) {
                column.setPrefWidth(columnWidthOrDefault());
            }
            if (!cssAuthored(column.fixedCellSizeProperty())) {
                column.setFixedCellSize(rowHeightOrDefault());
            }
        }
        getSkinnable().requestLayout();
    }

    private double columnWidthOrDefault() {
        double width = getSkinnable().getColumnWidth();
        return width > 0.0 ? width : RXCascaderView.DEFAULT_COLUMN_WIDTH;
    }

    private double rowHeightOrDefault() {
        double height = getSkinnable().getRowHeight();
        return height > 0.0 ? height : RXCascaderView.DEFAULT_FIXED_CELL_SIZE;
    }

    private static boolean cssAuthored(Object property) {
        if (!(property instanceof StyleableProperty)) {
            return false;
        }
        StyleOrigin origin = ((StyleableProperty<?>) property).getStyleOrigin();
        return origin == StyleOrigin.AUTHOR || origin == StyleOrigin.INLINE;
    }

    @Override
    protected void disposeSkin() {
        disposeColumns();
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        columnsBox.resizeRelocate(x, y, Math.max(0.0, w), Math.max(0.0, h));
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        if (columns.isEmpty()) {
            return leftInset + columnWidthOrDefault() + rightInset;
        }
        // Delegate to the HBox so its -fx-spacing and -fx-padding (author-settable on
        // .rx-cascader-view > .columns) are counted; the popup takes this width.
        return leftInset + columnsBox.prefWidth(height) + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double content = 0.0;
        for (ListView<RXCascaderItem<T>> column : columns) {
            content = Math.max(content, columnContentHeight(column));
        }
        if (columns.isEmpty()) {
            content = rowHeightOrDefault() * sanitizedVisibleRowCount();
        }
        // The row-slot height stays fixed (columnContentHeight), but the columns box
        // may carry its own vertical padding that must be added.
        Insets boxInsets = columnsBox.getInsets();
        return topInset + boxInsets.getTop() + content + boxInsets.getBottom() + bottomInset;
    }

    /**
     * Height a column needs to show {@code visibleRowCount} fixed-size rows plus
     * its own vertical insets. Item count is intentionally not consulted: the
     * panel keeps a fixed row-slot height so expanding short/long columns does not
     * make the popup jump (decision: fixed height, no shrink).
     */
    private double columnContentHeight(ListView<RXCascaderItem<T>> column) {
        Insets in = column.getInsets();
        return sanitizedVisibleRowCount() * cellSize(column) + in.getTop() + in.getBottom();
    }

    private double cellSize(ListView<RXCascaderItem<T>> column) {
        double fixed = column.getFixedCellSize();
        return fixed > 0.0 ? fixed : rowHeightOrDefault();
    }

    private int sanitizedVisibleRowCount() {
        return Math.max(MIN_VISIBLE_ROW_COUNT, getSkinnable().getVisibleRowCount());
    }
}
