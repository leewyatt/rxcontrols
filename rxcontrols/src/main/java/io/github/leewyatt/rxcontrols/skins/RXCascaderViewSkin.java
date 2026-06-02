package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderView;
import io.github.leewyatt.rxcontrols.RXCascaderPath;
import io.github.leewyatt.rxcontrols.RXCascaderSelectionMode;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Default skin for {@link RXCascaderView}.
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
    private final List<WeakReference<RXCascaderColumnCell<T>>> cells = new ArrayList<>();

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given view.
     *
     * @param control the skinnable view
     */
    public RXCascaderViewSkin(RXCascaderView<T> control) {
        super(control);
        columnsBox.getStyleClass().add("rx-cascader-columns");
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
        disposer.registerListener(control.optionContentFactoryProperty(), this::refreshColumns);
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
        listView.setCellFactory(view -> {
            RXCascaderColumnCell<T> cell = new RXCascaderColumnCell<>(getSkinnable());
            registerCell(cell);
            return cell;
        });
        views.add(listView);
    }

    private void registerCell(RXCascaderColumnCell<T> cell) {
        cells.removeIf(cellRef -> cellRef.get() == null);
        cells.add(new WeakReference<>(cell));
    }

    private void disposeColumns() {
        disposeCells();
        for (ListView<RXCascaderItem<T>> column : columns) {
            column.setCellFactory(null);
            column.setItems(null);
        }
        columnsBox.getChildren().clear();
        columns.clear();
    }

    private void disposeCells() {
        for (WeakReference<RXCascaderColumnCell<T>> cellRef : cells) {
            RXCascaderColumnCell<T> cell = cellRef.get();
            if (cell != null) {
                cell.disposeCell();
            }
        }
        cells.clear();
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

    // ==================== Cell ====================

    private static final class RXCascaderColumnCell<T> extends ListCell<RXCascaderItem<T>> {

        private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
        private static final PseudoClass IN_ACTIVE_PATH = PseudoClass.getPseudoClass("in-active-path");
        private static final PseudoClass IN_CHECKED_PATH = PseudoClass.getPseudoClass("in-checked-path");
        private static final PseudoClass INDETERMINATE = PseudoClass.getPseudoClass("indeterminate");
        private static final PseudoClass LOADING = PseudoClass.getPseudoClass("loading");
        private static final PseudoClass LEAF = PseudoClass.getPseudoClass("leaf");

        private final RXCascaderView<T> panel;
        private final HBox row = new HBox();
        private final CheckBox checkBox = new CheckBox();
        private final StackPane contentPane = new StackPane();
        private final Label textLabel = new Label();
        private final Region spacer = new Region();
        private final Label postfix = new Label();

        private final InvalidationListener stateListener = observable -> updateState();
        private final InvalidationListener contentListener = observable -> updateContent();
        private final ListChangeListener<RXCascaderItem<T>> childrenListener = change -> updateState();
        private final WeakInvalidationListener weakStateListener =
                new WeakInvalidationListener(stateListener);
        private final WeakInvalidationListener weakContentListener =
                new WeakInvalidationListener(contentListener);
        private final WeakListChangeListener<RXCascaderItem<T>> weakChildrenListener =
                new WeakListChangeListener<>(childrenListener);

        private RXCascaderItem<T> observedItem;

        private RXCascaderColumnCell(RXCascaderView<T> panel) {
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
            item.checkedProperty().addListener(weakStateListener);
            item.indeterminateProperty().addListener(weakStateListener);
            item.disabledProperty().addListener(weakStateListener);
            item.loadingProperty().addListener(weakStateListener);
            item.leafHintProperty().addListener(weakStateListener);
            item.textProperty().addListener(weakContentListener);
            item.getChildren().addListener(weakChildrenListener);
        }

        private void detachObservedItem() {
            if (observedItem == null) {
                return;
            }
            observedItem.checkedProperty().removeListener(weakStateListener);
            observedItem.indeterminateProperty().removeListener(weakStateListener);
            observedItem.disabledProperty().removeListener(weakStateListener);
            observedItem.loadingProperty().removeListener(weakStateListener);
            observedItem.leafHintProperty().removeListener(weakStateListener);
            observedItem.textProperty().removeListener(weakContentListener);
            observedItem.getChildren().removeListener(weakChildrenListener);
            observedItem = null;
        }

        private void disposeCell() {
            detachObservedItem();
            setText(null);
            setGraphic(null);
            contentPane.getChildren().clear();
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
