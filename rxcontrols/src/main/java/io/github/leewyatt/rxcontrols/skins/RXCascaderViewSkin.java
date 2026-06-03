package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderView;
import io.github.leewyatt.rxcontrols.RXCascaderPath;
import io.github.leewyatt.rxcontrols.RXCascaderSelectionMode;
import javafx.animation.AnimationTimer;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.WeakInvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
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
    private static final long LOADING_SPINNER_CYCLE_NANOS = 900_000_000L;

    // ==================== Nodes ====================

    private final HBox columnsBox = new HBox();
    private final List<ListView<RXCascaderItem<T>>> columns = new ArrayList<>();
    private final List<WeakReference<RXCascaderColumnCell<T>>> cells = new ArrayList<>();
    private final ReadOnlyBooleanProperty treeShowing;

    private final AnimationTimer loadingSpinnerTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            onLoadingSpinnerFrame(now);
        }
    };

    private boolean loadingSpinnerTimerRunning;

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given view.
     *
     * @param control the skinnable view
     */
    public RXCascaderViewSkin(RXCascaderView<T> control) {
        super(control);
        treeShowing = controlTreeShowingProperty();
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
        disposer.registerListener(control.optionContentFactoryProperty(), this::refreshColumns);
        disposer.registerListener(control.childrenLoaderProperty(), this::rebuildColumns);
        disposer.registerListener(treeShowing, this::onTreeShowingChanged);
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
            RXCascaderColumnCell<T> cell =
                    new RXCascaderColumnCell<>(getSkinnable(), this::requestLoadingSpinnerAnimation);
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
        stopLoadingSpinnerTimer();
    }

    private void refreshColumns() {
        for (ListView<RXCascaderItem<T>> column : columns) {
            column.refresh();
        }
    }

    @Override
    protected void disposeSkin() {
        disposeColumns();
        stopLoadingSpinnerTimer();
    }

    // ==================== Loading Spinner ====================

    private void onLoadingSpinnerFrame(long now) {
        double angle = (now % LOADING_SPINNER_CYCLE_NANOS) * 360.0 / LOADING_SPINNER_CYCLE_NANOS;
        boolean active = false;
        for (WeakReference<RXCascaderColumnCell<T>> cellRef : cells) {
            RXCascaderColumnCell<T> cell = cellRef.get();
            if (cell != null && cell.isLoadingGlyphVisible()) {
                cell.setLoadingRotate(angle);
                active = true;
            }
        }
        if (!active || !treeShowing.get()) {
            stopLoadingSpinnerTimer();
        }
    }

    private void requestLoadingSpinnerAnimation() {
        if (treeShowing.get() && hasVisibleLoadingGlyph()) {
            startLoadingSpinnerTimer();
        } else {
            stopLoadingSpinnerTimer();
        }
    }

    private boolean hasVisibleLoadingGlyph() {
        for (WeakReference<RXCascaderColumnCell<T>> cellRef : cells) {
            RXCascaderColumnCell<T> cell = cellRef.get();
            if (cell != null && cell.isLoadingGlyphVisible()) {
                return true;
            }
        }
        return false;
    }

    private void startLoadingSpinnerTimer() {
        if (loadingSpinnerTimerRunning) {
            return;
        }
        loadingSpinnerTimerRunning = true;
        loadingSpinnerTimer.start();
    }

    private void stopLoadingSpinnerTimer() {
        if (!loadingSpinnerTimerRunning) {
            return;
        }
        loadingSpinnerTimerRunning = false;
        loadingSpinnerTimer.stop();
        resetLoadingGlyphRotations();
    }

    private void resetLoadingGlyphRotations() {
        for (WeakReference<RXCascaderColumnCell<T>> cellRef : cells) {
            RXCascaderColumnCell<T> cell = cellRef.get();
            if (cell != null) {
                cell.setLoadingRotate(0.0);
            }
        }
    }

    private void onTreeShowingChanged() {
        requestLoadingSpinnerAnimation();
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
        private final HBox container = new HBox();
        private final CheckBox checkBox = new CheckBox();
        private final StackPane content = new StackPane();
        private final Label textLabel = new Label();
        private final Region arrow = new Region();
        private final Region loadingGlyph = new Region();

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
        private Runnable spinnerStateChanged;

        private RXCascaderColumnCell(RXCascaderView<T> panel, Runnable spinnerStateChanged) {
            this.panel = panel;
            this.spinnerStateChanged = spinnerStateChanged;
            initializeNodes();
            registerHandlers();
        }

        private void initializeNodes() {
            getStyleClass().add("rx-cascader-cell");
            container.getStyleClass().add("container");
            checkBox.setAllowIndeterminate(false);
            checkBox.setFocusTraversable(false);
            content.getStyleClass().add("content");
            arrow.getStyleClass().add("arrow");
            arrow.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            arrow.setMouseTransparent(true);
            loadingGlyph.getStyleClass().add("loading");
            loadingGlyph.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            loadingGlyph.setMouseTransparent(true);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMaxWidth(Double.MAX_VALUE);
            container.getChildren().setAll(checkBox, content, loadingGlyph, arrow);
        }

        private void registerHandlers() {
            checkBox.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                RXCascaderItem<T> item = getItem();
                if (item != null && !panel.isEffectivelyDisabled(item)) {
                    panel.toggleCheck(item);
                    // Also focus the operated item: expand a branch one level so
                    // the displayed column path follows the checkbox we just
                    // toggled, instead of staying on an unrelated expanded branch.
                    if (!panel.isLeaf(item)) {
                        panel.expand(item);
                    }
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
                arrow.setVisible(false);
                arrow.setManaged(false);
                loadingGlyph.setVisible(false);
                loadingGlyph.setManaged(false);
                loadingGlyph.setRotate(0.0);
                textLabel.setText(null);
                content.getChildren().clear();
                resetPseudoClasses();
                notifySpinnerStateChanged();
                return;
            }

            observedItem = item;
            attachObservedItem(item);
            setText(null);
            setGraphic(container);
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
            arrow.setVisible(false);
            arrow.setManaged(false);
            loadingGlyph.setVisible(false);
            loadingGlyph.setManaged(false);
            loadingGlyph.setRotate(0.0);
            content.getChildren().clear();
            notifySpinnerStateChanged();
            spinnerStateChanged = null;
        }

        private void updateContent() {
            RXCascaderItem<T> item = getItem();
            if (item == null) {
                content.getChildren().clear();
                return;
            }
            Callback<RXCascaderItem<T>, Node> factory = panel.getOptionContentFactory();
            Node custom = factory == null ? null : factory.call(item);
            if (custom == null) {
                textLabel.setText(item.getText());
                content.getChildren().setAll(textLabel);
            } else {
                content.getChildren().setAll(custom);
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
            boolean showArrow = !loading && !leaf;
            arrow.setVisible(showArrow);
            arrow.setManaged(showArrow);
            loadingGlyph.setVisible(loading);
            loadingGlyph.setManaged(loading);
            if (!loading) {
                loadingGlyph.setRotate(0.0);
            }

            pseudoClassStateChanged(ACTIVE, active);
            pseudoClassStateChanged(IN_ACTIVE_PATH, inActivePath);
            pseudoClassStateChanged(IN_CHECKED_PATH, inCheckedPath);
            pseudoClassStateChanged(INDETERMINATE, item.isIndeterminate());
            pseudoClassStateChanged(LOADING, loading);
            pseudoClassStateChanged(LEAF, leaf);
            notifySpinnerStateChanged();
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

        private boolean isLoadingGlyphVisible() {
            return loadingGlyph.isVisible() && loadingGlyph.getScene() != null;
        }

        private void setLoadingRotate(double angle) {
            loadingGlyph.setRotate(angle);
        }

        private void notifySpinnerStateChanged() {
            if (spinnerStateChanged != null) {
                spinnerStateChanged.run();
            }
        }
    }
}
