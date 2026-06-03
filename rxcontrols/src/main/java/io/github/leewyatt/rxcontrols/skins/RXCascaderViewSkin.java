package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXCascaderCell;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderView;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
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
        rebuildColumns();
    }

    private void registerListeners(RXCascaderView<T> control) {
        disposer.registerListener(control.getRootItems(), this::rebuildColumns);
        disposer.registerListener(control.getActivePath(), this::rebuildColumns);
        disposer.registerListener(control.selectionModeProperty(), () -> {
            refreshColumns();
            control.requestLayout();
        });
        disposer.registerListener(control.selectedPathProperty(), this::refreshColumns);
        disposer.registerListener(control.getCheckedPaths(), this::refreshColumns);
        disposer.registerListener(control.visibleRowCountProperty(), control::requestLayout);
        disposer.registerListener(control.cellFactoryProperty(), this::rebuildColumns);
        disposer.registerListener(control.childrenLoaderProperty(), this::rebuildColumns);
    }

    // ==================== Columns ====================

    private void rebuildColumns() {
        disposeColumns();
        List<ListView<RXCascaderItem<T>>> views = new ArrayList<>();
        addColumn(views, getSkinnable().getRootItems(), views.size());
        for (RXCascaderItem<T> item : getSkinnable().getActivePath()) {
            if (!getSkinnable().isLeaf(item)) {
                addColumn(views, item.getChildren(), views.size());
            }
        }
        columns.addAll(views);
        columnsBox.getChildren().setAll(views);
        // Ensure freshly created columns complete a CSS pass before they are used
        // for pref measurement / popup repositioning, so author CSS overrides the
        // code defaults without a one-frame default-size jump.
        if (getSkinnable().getScene() != null) {
            columnsBox.applyCss();
        }
    }

    private void addColumn(List<ListView<RXCascaderItem<T>>> views,
                           ObservableList<RXCascaderItem<T>> items, int columnIndex) {
        ListView<RXCascaderItem<T>> listView = new ListView<>(items);
        listView.getStyleClass().addAll(COLUMN_STYLE_CLASS, COLUMN_STYLE_CLASS + "-" + columnIndex);
        listView.setFocusTraversable(false);
        // Code defaults only; author CSS (-fx-pref-width / -fx-fixed-cell-size) can
        // override because AUTHOR origin outranks the USER origin of these set calls.
        // min/max are left unset so a single column can be widened via CSS while
        // HBox (hgrow=NEVER) keeps each column at its preferred width.
        listView.setPrefWidth(RXCascaderView.DEFAULT_COLUMN_WIDTH);
        listView.setFixedCellSize(RXCascaderView.DEFAULT_FIXED_CELL_SIZE);
        Callback<RXCascaderView<T>, ListCell<RXCascaderItem<T>>> factory = getSkinnable().getCellFactory();
        listView.setCellFactory(view -> factory != null
                ? factory.call(getSkinnable())
                : new RXCascaderCell<>(getSkinnable()));
        views.add(listView);
    }

    private void disposeColumns() {
        for (ListView<RXCascaderItem<T>> column : columns) {
            column.setCellFactory(null);
            column.setItems(null);
        }
        columnsBox.getChildren().clear();
        columns.clear();
    }

    private void refreshColumns() {
        for (ListView<RXCascaderItem<T>> column : columns) {
            column.refresh();
        }
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
        double width = 0.0;
        for (ListView<RXCascaderItem<T>> column : columns) {
            width += column.prefWidth(-1.0);
        }
        if (columns.isEmpty()) {
            width = RXCascaderView.DEFAULT_COLUMN_WIDTH;
        }
        return leftInset + width + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double content = 0.0;
        for (ListView<RXCascaderItem<T>> column : columns) {
            content = Math.max(content, columnContentHeight(column));
        }
        if (columns.isEmpty()) {
            content = RXCascaderView.DEFAULT_FIXED_CELL_SIZE * sanitizedVisibleRowCount();
        }
        return topInset + content + bottomInset;
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
        return fixed > 0.0 ? fixed : RXCascaderView.DEFAULT_FIXED_CELL_SIZE;
    }

    private int sanitizedVisibleRowCount() {
        return Math.max(MIN_VISIBLE_ROW_COUNT, getSkinnable().getVisibleRowCount());
    }
}
