package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderPanel;
import io.github.leewyatt.rxcontrols.RXCascaderPath;
import io.github.leewyatt.rxcontrols.RXCascaderSelectionMode;
import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;

/**
 * Default skin for {@link RXCascaderPanel}.
 *
 * @param <T> application value type
 */
public class RXCascaderPanelSkin<T> extends RXSkinBase<RXCascaderPanel<T>> {

    // ==================== Constants ====================

    private static final double MIN_COLUMN_WIDTH = 48.0;
    private static final double MIN_ROW_HEIGHT = 20.0;
    private static final int MIN_VISIBLE_ROW_COUNT = 1;

    // ==================== Nodes ====================

    private final HBox columnsBox = new HBox();
    private final List<ListView<RXCascaderItem<T>>> columns = new ArrayList<>();

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given panel.
     *
     * @param control the skinnable panel
     */
    public RXCascaderPanelSkin(RXCascaderPanel<T> control) {
        super(control);
        columnsBox.getStyleClass().add("rx-cascader-columns");
        getChildren().setAll(columnsBox);
        registerListeners(control);
        rebuildColumns();
    }

    private void registerListeners(RXCascaderPanel<T> control) {
        disposer.registerListener(control.getRootItems(), this::rebuildColumns);
        disposer.registerListener(control.getActivePath(), this::rebuildColumns);
        disposer.registerListener(control.selectionModeProperty(), () -> {
            refreshColumns();
            control.requestLayout();
        });
        disposer.registerListener(control.selectedPathProperty(), this::refreshColumns);
        disposer.registerListener(control.getCheckedPaths(), this::refreshColumns);
        disposer.registerListener(control.columnWidthProperty(), () -> {
            applyColumnMetrics();
            control.requestLayout();
        });
        disposer.registerListener(control.rowHeightProperty(), () -> {
            applyColumnMetrics();
            control.requestLayout();
        });
        disposer.registerListener(control.visibleRowCountProperty(), control::requestLayout);
        disposer.registerListener(control.optionContentFactoryProperty(), this::refreshColumns);
        disposer.registerListener(control.childrenLoaderProperty(), this::rebuildColumns);
    }

    // ==================== Columns ====================

    private void rebuildColumns() {
        columns.clear();
        List<ListView<RXCascaderItem<T>>> views = new ArrayList<>();
        addColumn(views, getSkinnable().getRootItems());
        for (RXCascaderItem<T> item : getSkinnable().getActivePath()) {
            if (!getSkinnable().isLeaf(item)) {
                addColumn(views, item.getChildren());
            }
        }
        columns.addAll(views);
        columnsBox.getChildren().setAll(views);
        applyColumnMetrics();
    }

    private void addColumn(List<ListView<RXCascaderItem<T>>> views,
                           ObservableList<RXCascaderItem<T>> items) {
        ListView<RXCascaderItem<T>> listView = new ListView<>(items);
        listView.getStyleClass().add("rx-cascader-column");
        listView.setFocusTraversable(false);
        listView.setCellFactory(view -> new RXCascaderColumnCell<>(getSkinnable()));
        views.add(listView);
    }

    private void applyColumnMetrics() {
        double columnWidth = sanitizedColumnWidth();
        double rowHeight = sanitizedRowHeight();
        for (ListView<RXCascaderItem<T>> column : columns) {
            column.setFixedCellSize(rowHeight);
            column.setPrefWidth(columnWidth);
            column.setMinWidth(columnWidth);
            column.setMaxWidth(columnWidth);
        }
    }

    private void refreshColumns() {
        for (ListView<RXCascaderItem<T>> column : columns) {
            column.refresh();
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        columnsBox.resizeRelocate(x, y, Math.max(0.0, w), Math.max(0.0, h));
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        int columnCount = Math.max(1, columns.size());
        return leftInset + columnCount * sanitizedColumnWidth() + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + sanitizedRowHeight() * sanitizedVisibleRowCount() + bottomInset;
    }

    private double sanitizedColumnWidth() {
        double value = getSkinnable().getColumnWidth();
        if (Double.isNaN(value) || Double.isInfinite(value) || value < MIN_COLUMN_WIDTH) {
            return RXCascaderPanel.DEFAULT_COLUMN_WIDTH;
        }
        return value;
    }

    private double sanitizedRowHeight() {
        double value = getSkinnable().getRowHeight();
        if (Double.isNaN(value) || Double.isInfinite(value) || value < MIN_ROW_HEIGHT) {
            return RXCascaderPanel.DEFAULT_ROW_HEIGHT;
        }
        return value;
    }

    private int sanitizedVisibleRowCount() {
        return Math.max(MIN_VISIBLE_ROW_COUNT, getSkinnable().getVisibleRowCount());
    }

    // ==================== Cell ====================

    private static final class RXCascaderColumnCell<T> extends ListCell<RXCascaderItem<T>> {

        private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
        private static final PseudoClass IN_ACTIVE_PATH = PseudoClass.getPseudoClass("in-active-path");
        private static final PseudoClass IN_CHECKED_PATH = PseudoClass.getPseudoClass("in-checked-path");
        private static final PseudoClass INDETERMINATE = PseudoClass.getPseudoClass("indeterminate");
        private static final PseudoClass LOADING = PseudoClass.getPseudoClass("loading");
        private static final PseudoClass LEAF = PseudoClass.getPseudoClass("leaf");

        private final RXCascaderPanel<T> panel;
        private final HBox row = new HBox();
        private final CheckBox checkBox = new CheckBox();
        private final StackPane contentPane = new StackPane();
        private final Label textLabel = new Label();
        private final Region spacer = new Region();
        private final Label postfix = new Label();

        private final InvalidationListener stateListener = observable -> updateState();
        private final InvalidationListener contentListener = observable -> updateContent();
        private final ListChangeListener<RXCascaderItem<T>> childrenListener = change -> updateState();

        private RXCascaderItem<T> observedItem;

        private RXCascaderColumnCell(RXCascaderPanel<T> panel) {
            this.panel = panel;
            initializeNodes();
            registerHandlers();
        }

        private void initializeNodes() {
            getStyleClass().add("rx-cascader-cell");
            row.getStyleClass().add("rx-cascader-cell-container");
            row.setAlignment(Pos.CENTER_LEFT);
            checkBox.getStyleClass().add("rx-cascader-cell-check-box");
            checkBox.setAllowIndeterminate(false);
            checkBox.setFocusTraversable(false);
            contentPane.getStyleClass().add("rx-cascader-cell-content");
            textLabel.getStyleClass().add("rx-cascader-cell-label");
            postfix.getStyleClass().add("rx-cascader-cell-postfix");
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().setAll(checkBox, contentPane, spacer, postfix);
        }

        private void registerHandlers() {
            checkBox.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                RXCascaderItem<T> item = getItem();
                if (item != null && !panel.isEffectivelyDisabled(item)) {
                    panel.toggleCheck(item);
                }
                event.consume();
            });
            checkBox.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
            addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getButton() == MouseButton.PRIMARY && getItem() != null) {
                    panel.activate(getItem());
                    event.consume();
                }
            });
        }

        @Override
        protected void updateItem(RXCascaderItem<T> item, boolean empty) {
            detachObservedItem();
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                observedItem = null;
                setDisable(false);
                checkBox.setVisible(false);
                checkBox.setManaged(false);
                checkBox.setSelected(false);
                checkBox.setIndeterminate(false);
                checkBox.setDisable(false);
                postfix.setText("");
                textLabel.setText(null);
                contentPane.getChildren().clear();
                resetPseudoClasses();
                return;
            }

            observedItem = item;
            attachObservedItem(item);
            setText(null);
            setGraphic(row);
            updateContent();
            updateState();
        }

        private void attachObservedItem(RXCascaderItem<T> item) {
            item.checkedProperty().addListener(stateListener);
            item.indeterminateProperty().addListener(stateListener);
            item.disabledProperty().addListener(stateListener);
            item.loadingProperty().addListener(stateListener);
            item.leafHintProperty().addListener(stateListener);
            item.textProperty().addListener(contentListener);
            item.getChildren().addListener(childrenListener);
        }

        private void detachObservedItem() {
            if (observedItem == null) {
                return;
            }
            observedItem.checkedProperty().removeListener(stateListener);
            observedItem.indeterminateProperty().removeListener(stateListener);
            observedItem.disabledProperty().removeListener(stateListener);
            observedItem.loadingProperty().removeListener(stateListener);
            observedItem.leafHintProperty().removeListener(stateListener);
            observedItem.textProperty().removeListener(contentListener);
            observedItem.getChildren().removeListener(childrenListener);
            observedItem = null;
        }

        private void updateContent() {
            RXCascaderItem<T> item = getItem();
            if (item == null) {
                contentPane.getChildren().clear();
                return;
            }
            Callback<RXCascaderItem<T>, Node> factory = panel.getOptionContentFactory();
            Node content = factory == null ? null : factory.call(item);
            if (content == null) {
                textLabel.setText(item.getText());
                contentPane.getChildren().setAll(textLabel);
            } else {
                contentPane.getChildren().setAll(content);
            }
        }

        private void updateState() {
            RXCascaderItem<T> item = getItem();
            if (item == null) {
                return;
            }
            boolean multiple = panel.getSelectionMode() == RXCascaderSelectionMode.MULTIPLE;
            boolean disabled = panel.isEffectivelyDisabled(item);
            boolean leaf = panel.isLeaf(item);
            boolean loading = item.isLoading();
            RXCascaderPath<T> selectedPath = panel.getSelectedPath();
            boolean active = selectedPath != null && selectedPath.getLeaf() == item;
            boolean inActivePath = panel.getActivePath().contains(item);
            boolean inCheckedPath = isInCheckedPath(item);

            checkBox.setVisible(multiple);
            checkBox.setManaged(multiple);
            checkBox.setDisable(disabled);
            checkBox.setSelected(item.isChecked());
            checkBox.setIndeterminate(item.isIndeterminate());
            setDisable(disabled);
            postfix.setText(loading ? "..." : (leaf ? "" : ">"));

            pseudoClassStateChanged(ACTIVE, active);
            pseudoClassStateChanged(IN_ACTIVE_PATH, inActivePath);
            pseudoClassStateChanged(IN_CHECKED_PATH, inCheckedPath);
            pseudoClassStateChanged(INDETERMINATE, item.isIndeterminate());
            pseudoClassStateChanged(LOADING, loading);
            pseudoClassStateChanged(LEAF, leaf);
        }

        private boolean isInCheckedPath(RXCascaderItem<T> item) {
            for (RXCascaderPath<T> path : panel.getCheckedPaths()) {
                if (path.contains(item)) {
                    return true;
                }
            }
            return false;
        }

        private void resetPseudoClasses() {
            pseudoClassStateChanged(ACTIVE, false);
            pseudoClassStateChanged(IN_ACTIVE_PATH, false);
            pseudoClassStateChanged(IN_CHECKED_PATH, false);
            pseudoClassStateChanged(INDETERMINATE, false);
            pseudoClassStateChanged(LOADING, false);
            pseudoClassStateChanged(LEAF, false);
        }
    }
}
