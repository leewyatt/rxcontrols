package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.RXSelectionBox;
import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.RXListSelectionVisualMode;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import io.github.leewyatt.rxcontrols.internal.popup.RXPopupSupport;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.css.PseudoClass;
import javafx.event.EventTarget;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Default skin for {@link RXSelectionBox}.
 *
 * <p>Renders the collapsed display area (graphic, selection summary, drop
 * arrow), a display-area press ripple, and an anchored popup hosting a
 * virtualized {@link RXListView}. The authoritative selection lives in the
 * control's {@link RXSelectionBox#getSelectionModel() model} over the source
 * {@code items}; the popup list carries its own single-selection cursor for
 * keyboard navigation, and a {@link FilteredList} bridges the filtered view back
 * to source indices. Because the cursor never writes the authoritative model and
 * the cells only read it, there is no bidirectional sync loop.</p>
 *
 * <p>When searchable, opening the popup moves key focus into the editable search
 * field (a real {@link RXTextField} bidirectionally bound to {@code searchText}, so
 * it shows a caret and takes typed input natively); {@code Up}/{@code Down} move the
 * list cursor, {@code Enter} activates it, and {@code Escape} closes, all intercepted
 * on the field before its text behavior. When not searchable the popup has no
 * focusable content, so focus stays on the control and the same navigation runs from
 * the control's own key handler.</p>
 *
 * @param <T> the item type
 */
public class RXSelectionBoxSkin<T> extends RXSkinBase<RXSelectionBox<T>> {

    private static final double DEFAULT_PREF_WIDTH = 150.0;
    private static final double POPUP_CELL_SIZE = 34.0;
    private static final Duration ENTRANCE_DURATION = Duration.millis(180);
    private static final double ENTRANCE_START_SCALE_Y = 0.92;

    private static final PseudoClass EMPTY = PseudoClass.getPseudoClass("empty");
    private static final PseudoClass SHOWING = PseudoClass.getPseudoClass("showing");
    private static final PseudoClass SINGLE = PseudoClass.getPseudoClass("single");
    private static final PseudoClass MULTIPLE = PseudoClass.getPseudoClass("multiple");
    private static final PseudoClass READONLY = PseudoClass.getPseudoClass("readonly");
    private static final PseudoClass SEARCHABLE = PseudoClass.getPseudoClass("searchable");
    private static final PseudoClass FILTERED = PseudoClass.getPseudoClass("filtered");

    // ==================== Display nodes ====================

    private final HBox display = new HBox();
    private final StackPane graphicHolder = new StackPane();
    private final Label summaryLabel = new Label();
    private final StackPane arrowButton = new StackPane();
    private final Region arrow = new Region();
    private final RippleDecoration ripple;

    // ==================== Popup nodes ====================

    private final VBox popupBody = new VBox();
    private final RXTextField searchField = new RXTextField();
    private final StackPane headerHolder = new StackPane();
    private final RXListView<T> popupList = new RXListView<>();
    private final HBox actionsBox = new HBox();
    private final RXButton clearButton = new RXButton();
    private final RXButton selectAllButton = new RXButton();
    private final StackPane footerHolder = new StackPane();
    private final RXPopupSupport popupSupport;
    private final Scale entranceScale = new Scale(1.0, 1.0, 0.0, 0.0);
    private Timeline entrance;

    // ==================== Data bridge ====================

    private final ObservableList<T> backing = FXCollections.observableArrayList();
    private final FilteredList<T> filtered = new FilteredList<>(backing);

    private ObservableList<T> observedItems;
    private final ListChangeListener<T> itemsContentListener = change -> syncBacking();
    private final WeakListChangeListener<T> weakItemsContentListener =
            new WeakListChangeListener<>(itemsContentListener);

    private MultipleSelectionModel<T> observedModel;
    private final InvalidationListener selectionContentListener = obs -> updateDisplay();
    private final WeakInvalidationListener weakSelectionContentListener =
            new WeakInvalidationListener(selectionContentListener);

    private final ListChangeListener<T> filteredListener = change -> updatePopupHeight();
    private final WeakListChangeListener<T> weakFilteredListener =
            new WeakListChangeListener<>(filteredListener);

    private boolean suppressReopen;

    // ==================== Construction ====================

    /**
     * Creates the skin.
     *
     * @param control the selection box
     */
    public RXSelectionBoxSkin(RXSelectionBox<T> control) {
        super(control);

        buildDisplay(control);
        buildPopup(control);

        ripple = new RippleDecoration(display, control.rippleEnabledProperty(),
                control.stateOverlayEnabledProperty(), control.rippleFillProperty(),
                control::getRippleOpacity, null, null);
        ripple.getLayer().setManaged(false);
        display.getChildren().add(0, ripple.getLayer());

        popupSupport = new RXPopupSupport(popupBody);
        popupSupport.setPopupStyleClass("rx-selection-box-popup");
        popupSupport.setOnHidden(this::onPopupHidden);

        getChildren().setAll(display);

        registerListeners(control);

        rebindItems();
        rebindModel();
        updateGraphic();
        updateDisplay();
        updateHeader();
        updateFooter();
        updateActions();
        updateFilter();
        updatePseudoClasses();
        syncPopupShowing();
    }

    private void buildDisplay(RXSelectionBox<T> control) {
        display.getStyleClass().add("display");
        display.setAlignment(Pos.CENTER_LEFT);

        graphicHolder.getStyleClass().add("graphic");

        summaryLabel.getStyleClass().add("summary");
        summaryLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(summaryLabel, Priority.ALWAYS);

        arrow.getStyleClass().add("arrow");
        arrow.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        arrow.setMouseTransparent(true);
        arrowButton.getStyleClass().add("arrow-button");
        arrowButton.setMouseTransparent(true);
        arrowButton.getChildren().add(arrow);

        display.getChildren().addAll(graphicHolder, summaryLabel, arrowButton);
    }

    private void buildPopup(RXSelectionBox<T> control) {
        popupBody.getStyleClass().add("popup-content");
        popupBody.getTransforms().add(entranceScale);

        Region searchIcon = new Region();
        searchIcon.getStyleClass().add("search-icon");
        searchIcon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        searchIcon.setMouseTransparent(true);
        searchField.getStyleClass().add("search-field");
        searchField.setLeft(searchIcon);

        headerHolder.getStyleClass().add("popup-header");
        footerHolder.getStyleClass().add("popup-footer");

        popupList.setFocusTraversable(false);
        popupList.setFixedCellSize(POPUP_CELL_SIZE);
        popupList.setItems(filtered);
        popupList.setSelectionMode(SelectionMode.SINGLE);
        popupList.setSelectionVisualMode(RXListSelectionVisualMode.ROW);
        popupList.setCellFactory(view -> new SelectionCell<>(control, filtered));
        VBox.setVgrow(popupList, Priority.ALWAYS);

        clearButton.getStyleClass().add("clear-button");
        clearButton.setFocusTraversable(false);
        selectAllButton.getStyleClass().add("select-all-button");
        selectAllButton.setFocusTraversable(false);
        actionsBox.getStyleClass().add("actions-box");
        actionsBox.getChildren().addAll(clearButton, selectAllButton);

        popupBody.getChildren().addAll(searchField, headerHolder, popupList, actionsBox, footerHolder);
    }

    // ==================== Listener wiring ====================

    private void registerListeners(RXSelectionBox<T> control) {
        disposer.registerListener(control.showingProperty(), this::syncPopupShowing);
        disposer.registerListener(control.itemsProperty(), this::rebindItems);
        disposer.registerListener(control.selectionModelProperty(), this::rebindModel);
        disposer.registerListener(control.selectionModeProperty(), this::onModeChanged);
        disposer.registerListener(control.searchTextProperty(), this::onSearchChanged);
        disposer.registerListener(control.filterPredicateProperty(), this::updateFilter);
        disposer.registerListener(control.searchableProperty(), this::onSearchableChanged);
        disposer.registerListener(control.readOnlyProperty(), this::onReadOnlyChanged);
        disposer.registerListener(control.promptTextProperty(), this::updateDisplay);
        disposer.registerListener(control.converterProperty(), this::updateDisplay);
        disposer.registerListener(control.selectedItemsConverterProperty(), this::updateDisplay);
        disposer.registerListener(control.graphicProperty(), this::updateGraphic);
        disposer.registerListener(control.popupHeaderProperty(), this::updateHeader);
        disposer.registerListener(control.popupFooterProperty(), this::updateFooter);
        disposer.registerListener(control.maxVisibleRowsProperty(), this::updatePopupHeight);
        disposer.registerListener(control.showClearButtonProperty(), this::updateActions);
        disposer.registerListener(control.showSelectAllButtonProperty(), this::updateActions);
        disposer.registerListener(control.disabledProperty(), this::onDisabledChanged);

        filtered.addListener(weakFilteredListener);

        disposer.registerBinding(popupList.converterProperty(), control.converterProperty());
        disposer.registerBinding(popupList.placeholderProperty(), control.placeholderProperty());
        disposer.registerBinding(popupList.sectionKeyFactoryProperty(), control.sectionKeyFactoryProperty());
        // The search field is the editable source of the query: bind bidirectionally so
        // both typing and a programmatic setSearchText() stay in sync.
        searchField.textProperty().bindBidirectional(control.searchTextProperty());
        disposer.registerDisposeTask(
                () -> searchField.textProperty().unbindBidirectional(control.searchTextProperty()));
        disposer.registerBinding(searchField.promptTextProperty(), control.searchPromptTextProperty());
        disposer.registerBinding(searchField.visibleProperty(), control.searchableProperty());
        disposer.registerBinding(searchField.managedProperty(), control.searchableProperty());
        disposer.registerBinding(clearButton.textProperty(), control.clearButtonTextProperty());
        disposer.registerBinding(selectAllButton.textProperty(), control.selectAllButtonTextProperty());

        clearButton.setOnAction(event -> control.clearSelection());
        selectAllButton.setOnAction(event -> control.selectAll());

        disposer.registerEventHandler(display, MouseEvent.MOUSE_CLICKED, this::onDisplayClicked);
        disposer.registerEventHandler(display, MouseEvent.MOUSE_PRESSED, this::onDisplayPressed);
        disposer.registerEventHandler(display, MouseEvent.MOUSE_RELEASED, event -> ripple.release());
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
        disposer.registerEventFilter(searchField, KeyEvent.KEY_PRESSED, this::onSearchFieldKeyPressed);
        disposer.registerEventHandler(popupList, MouseEvent.MOUSE_CLICKED, this::onListClicked);

        disposer.registerDisposeTask(popupSupport::dispose);
        disposer.registerDisposeTask(this::disposeRipple);
        disposer.registerDisposeTask(() -> filtered.removeListener(weakFilteredListener));
        disposer.registerDisposeTask(this::detachItems);
        disposer.registerDisposeTask(this::detachModel);
    }

    private void disposeRipple() {
        ripple.dispose();
        display.getChildren().remove(ripple.getLayer());
    }

    // ==================== Items / backing sync ====================

    private void rebindItems() {
        detachItems();
        observedItems = getSkinnable().getItems();
        if (observedItems != null) {
            observedItems.addListener(weakItemsContentListener);
        }
        syncBacking();
    }

    private void detachItems() {
        if (observedItems != null) {
            observedItems.removeListener(weakItemsContentListener);
            observedItems = null;
        }
    }

    private void syncBacking() {
        ObservableList<T> source = getSkinnable().getItems();
        if (source == null) {
            backing.clear();
        } else {
            backing.setAll(source);
        }
        updatePopupHeight();
    }

    // ==================== Selection model attach ====================

    private void rebindModel() {
        detachModel();
        observedModel = getSkinnable().getSelectionModel();
        if (observedModel != null) {
            // Coerce null -> SINGLE like the control's own sync paths, so this
            // attach/swap path never leaves the model with a null (MULTIPLE-behaving) mode.
            SelectionMode mode = getSkinnable().getSelectionMode();
            observedModel.setSelectionMode(mode == null ? SelectionMode.SINGLE : mode);
            observedModel.getSelectedItems().addListener(weakSelectionContentListener);
        }
        // Rebuild cells so their per-cell listeners re-attach to the new model.
        popupList.setCellFactory(view -> new SelectionCell<>(getSkinnable(), filtered));
        updateDisplay();
        updateActions();
    }

    private void detachModel() {
        if (observedModel != null) {
            observedModel.getSelectedItems().removeListener(weakSelectionContentListener);
            observedModel = null;
        }
    }

    // ==================== Property reactions ====================

    private void onModeChanged() {
        updatePseudoClasses();
        updateActions();
        updateDisplay();
    }

    private void onSearchChanged() {
        updateFilter();
        updatePseudoClasses();
    }

    private void onSearchableChanged() {
        RXSelectionBox<T> control = getSkinnable();
        if (!control.isSearchable()) {
            // Turning search off must not leave a stale filter that the user can no
            // longer reach (the search field is hidden): clear the query.
            control.setSearchText("");
            // The search field (which may hold key focus) is now hidden; hand focus to
            // the control so the non-searchable keyboard-navigation path stays reachable.
            if (control.isShowing()) {
                control.requestFocus();
            }
        }
        updatePseudoClasses();
    }

    private void onDisabledChanged() {
        if (getSkinnable().isDisabled()) {
            getSkinnable().hide();
        }
    }

    private void onReadOnlyChanged() {
        // A control turned read-only while its popup is open must close it, so no
        // further UI interaction can change the selection (mirrors the disabled path).
        if (getSkinnable().isReadOnly()) {
            getSkinnable().hide();
        }
        updatePseudoClasses();
    }

    private void updateGraphic() {
        Node graphic = getSkinnable().getGraphic();
        if (graphic == null) {
            graphicHolder.getChildren().clear();
            graphicHolder.setVisible(false);
            graphicHolder.setManaged(false);
        } else {
            graphicHolder.getChildren().setAll(graphic);
            graphicHolder.setVisible(true);
            graphicHolder.setManaged(true);
        }
    }

    private void updateHeader() {
        Node header = getSkinnable().getPopupHeader();
        if (header == null) {
            headerHolder.getChildren().clear();
            headerHolder.setVisible(false);
            headerHolder.setManaged(false);
        } else {
            headerHolder.getChildren().setAll(header);
            headerHolder.setVisible(true);
            headerHolder.setManaged(true);
        }
    }

    private void updateFooter() {
        Node footer = getSkinnable().getPopupFooter();
        if (footer == null) {
            footerHolder.getChildren().clear();
            footerHolder.setVisible(false);
            footerHolder.setManaged(false);
        } else {
            footerHolder.getChildren().setAll(footer);
            footerHolder.setVisible(true);
            footerHolder.setManaged(true);
        }
    }

    private void updateActions() {
        RXSelectionBox<T> control = getSkinnable();
        boolean multiple = control.getSelectionMode() == SelectionMode.MULTIPLE;
        boolean showClear = control.isShowClearButton();
        boolean showSelectAll = control.isShowSelectAllButton() && multiple;
        setVisibleManaged(clearButton, showClear);
        setVisibleManaged(selectAllButton, showSelectAll);
        setVisibleManaged(actionsBox, showClear || showSelectAll);
    }

    private static void setVisibleManaged(Node node, boolean value) {
        node.setVisible(value);
        node.setManaged(value);
    }

    private void updateDisplay() {
        RXSelectionBox<T> control = getSkinnable();
        List<T> selected = control.getSelectedItems();
        int count = selected.size();
        String text;
        if (count == 0) {
            String prompt = control.getPromptText();
            text = prompt == null ? "" : prompt;
        } else if (count == 1) {
            text = textOf(selected.get(0));
        } else {
            StringConverter<List<T>> converter = control.getSelectedItemsConverter();
            text = converter == null ? count + " selected" : nullToEmpty(converter.toString(selected));
        }
        summaryLabel.setText(text);
        control.setAccessibleText(text);
        control.pseudoClassStateChanged(EMPTY, count == 0);
    }

    private void updatePseudoClasses() {
        RXSelectionBox<T> control = getSkinnable();
        boolean multiple = control.getSelectionMode() == SelectionMode.MULTIPLE;
        control.pseudoClassStateChanged(SHOWING, control.isShowing());
        control.pseudoClassStateChanged(SINGLE, !multiple);
        control.pseudoClassStateChanged(MULTIPLE, multiple);
        control.pseudoClassStateChanged(READONLY, control.isReadOnly());
        control.pseudoClassStateChanged(SEARCHABLE, control.isSearchable());
        String query = control.getSearchText();
        control.pseudoClassStateChanged(FILTERED, query != null && !query.isBlank());
    }

    // ==================== Filtering ====================

    private void updateFilter() {
        RXSelectionBox<T> control = getSkinnable();
        String query = control.getSearchText();
        if (query == null || query.isBlank()) {
            filtered.setPredicate(null);
        } else {
            BiPredicate<T, String> predicate = control.getFilterPredicate();
            if (predicate != null) {
                filtered.setPredicate(item -> predicate.test(item, query));
            } else {
                String needle = query.trim().toLowerCase();
                filtered.setPredicate(item -> textOf(item).toLowerCase().contains(needle));
            }
        }
        resetCursorAfterFilter();
        updatePopupHeight();
    }

    private void resetCursorAfterFilter() {
        MultipleSelectionModel<T> cursor = popupList.getSelectionModel();
        if (cursor == null) {
            return;
        }
        cursor.clearSelection();
        String query = getSkinnable().getSearchText();
        if (query != null && !query.isBlank() && !filtered.isEmpty()) {
            cursor.select(0);
            popupList.scrollTo(0);
        }
    }

    private void updatePopupHeight() {
        int count = filtered.size();
        int rows = count == 0 ? 1 : Math.min(count, Math.max(1, getSkinnable().getMaxVisibleRows()));
        double cellSize = popupList.getFixedCellSize();
        if (!(cellSize > 0.0)) {
            cellSize = RXListView.DEFAULT_FIXED_CELL_SIZE;
        }
        double insets = popupList.getInsets().getTop() + popupList.getInsets().getBottom();
        popupList.setPrefHeight(rows * cellSize + sectionHeaderExtent(rows) + insets);
        popupSupport.requestReposition();
    }

    /**
     * Extra vertical extent contributed by section headers within the first
     * {@code rows} data rows, so the popup does not clip its content (or show a
     * premature scrollbar) when a {@code sectionKeyFactory} is set.
     */
    private double sectionHeaderExtent(int rows) {
        Callback<T, Object> keyFactory = getSkinnable().getSectionKeyFactory();
        if (keyFactory == null || !popupList.isShowSectionHeaders()) {
            return 0.0;
        }
        int headers = 0;
        Object previousKey = null;
        int limit = Math.min(rows, filtered.size());
        for (int i = 0; i < limit; i++) {
            Object key = keyFactory.call(filtered.get(i));
            if (i == 0 || !Objects.equals(key, previousKey)) {
                headers++;
            }
            previousKey = key;
        }
        return headers * (popupList.getSectionHeaderHeight() + popupList.getSectionSpacing());
    }

    // ==================== Popup show / hide ====================

    private void syncPopupShowing() {
        RXSelectionBox<T> control = getSkinnable();
        boolean showing = control.isShowing();
        if (showing) {
            prepareForShow();
            popupSupport.show(control);
            if (popupSupport.isShowing()) {
                playEntrance();
                if (control.isSearchable()) {
                    // Move focus into the editable search field so it shows a caret and
                    // takes typed input directly. The popup window legitimately holds key
                    // focus; runLater lets the popup window realize first.
                    Platform.runLater(searchField::requestFocus);
                }
            }
        } else {
            // If focus was inside the popup (search field), hand it back to the control
            // so keyboard interaction continues; an outside auto-hide leaves it alone.
            boolean popupHadFocus = searchField.isFocused();
            stopEntrance();
            popupSupport.hide();
            if (control.isClearSearchOnHide()) {
                control.setSearchText("");
            }
            if (popupHadFocus) {
                control.requestFocus();
            }
        }
        updatePseudoClasses();
    }

    private void playEntrance() {
        if (entrance != null) {
            entrance.stop();
            entrance = null;
        }
        if (!getSkinnable().isAnimationEnabled()) {
            resetEntranceState();
            return;
        }
        entranceScale.setY(ENTRANCE_START_SCALE_Y);
        popupBody.setOpacity(0.0);
        entrance = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(popupBody.opacityProperty(), 0.0),
                        new KeyValue(entranceScale.yProperty(), ENTRANCE_START_SCALE_Y)),
                new KeyFrame(ENTRANCE_DURATION,
                        new KeyValue(popupBody.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(entranceScale.yProperty(), 1.0, Interpolator.EASE_OUT)));
        entrance.setOnFinished(event -> resetEntranceState());
        entrance.playFromStart();
    }

    private void stopEntrance() {
        if (entrance != null) {
            entrance.stop();
            entrance = null;
        }
        resetEntranceState();
    }

    private void resetEntranceState() {
        popupBody.setOpacity(1.0);
        entranceScale.setY(1.0);
    }

    private void prepareForShow() {
        updatePopupHeight();
        MultipleSelectionModel<T> model = getSkinnable().getSelectionModel();
        MultipleSelectionModel<T> cursor = popupList.getSelectionModel();
        if (cursor == null) {
            return;
        }
        // Reveal the lead selection by its exact source index (handles duplicate
        // values correctly), mapped through the filter to a view row.
        int sourceIndex = model == null ? -1 : model.getSelectedIndex();
        int viewIndex = sourceIndex < 0 ? -1 : filtered.getViewIndex(sourceIndex);
        if (viewIndex >= 0) {
            cursor.select(viewIndex);
            popupList.scrollTo(viewIndex, ScrollAlignment.CENTER);
        } else {
            cursor.clearSelection();
        }
    }

    private void onPopupHidden() {
        getSkinnable().hide();
        suppressReopen = true;
        Platform.runLater(() -> suppressReopen = false);
    }

    // ==================== Mouse ====================

    private void onDisplayClicked(MouseEvent event) {
        RXSelectionBox<T> control = getSkinnable();
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        control.requestFocus();
        if (control.isReadOnly()) {
            event.consume();
            return;
        }
        if (control.isShowing()) {
            control.hide();
        } else if (!suppressReopen) {
            control.show();
        }
        event.consume();
    }

    private void onDisplayPressed(MouseEvent event) {
        RXSelectionBox<T> control = getSkinnable();
        if (event.getButton() != MouseButton.PRIMARY
                || !control.isRippleEnabled() || control.isDisabled() || control.isReadOnly()) {
            return;
        }
        Point2D local = display.sceneToLocal(event.getSceneX(), event.getSceneY());
        ripple.press(local.getX(), local.getY(), false);
    }

    private void onListClicked(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (getSkinnable().isReadOnly() || getSkinnable().isDisabled()) {
            return;
        }
        RXListCell<T> cell = enclosingCell(event.getTarget());
        if (cell == null || cell.isEmpty()) {
            return;
        }
        // Do not pull focus back to the control here: a searchable popup keeps key
        // focus in the search field (so multi-select stays type-to-filter after a
        // click), and a non-searchable popup already holds focus on the control. The
        // single-select close path returns focus to the control on hide.
        int viewIndex = cell.getIndex();
        MultipleSelectionModel<T> cursor = popupList.getSelectionModel();
        if (cursor != null && viewIndex >= 0) {
            cursor.select(viewIndex);
        }
        activate(viewIndex);
    }

    @SuppressWarnings("unchecked")
    private RXListCell<T> enclosingCell(EventTarget target) {
        if (!(target instanceof Node)) {
            return null;
        }
        Node node = (Node) target;
        while (node != null) {
            if (node instanceof RXListCell) {
                return (RXListCell<T>) node;
            }
            node = node.getParent();
        }
        return null;
    }

    // ==================== Keyboard ====================

    /**
     * Key handling on the control itself. When searchable, the popup's editable
     * search field owns key focus while open, so this only opens the popup on
     * Down/Up (closed) and drives navigation when the popup is not searchable (no
     * focusable content, so focus stays on the control).
     */
    private void onKeyPressed(KeyEvent event) {
        RXSelectionBox<T> control = getSkinnable();
        if (control.isDisabled() || control.isReadOnly()) {
            return;
        }
        KeyCode code = event.getCode();
        if (!control.isShowing()) {
            // Down / Up open the popup (ComboBox convention). Enter and other keys
            // are left to bubble so an enclosing form's default button still sees them.
            if (code == KeyCode.DOWN || code == KeyCode.UP) {
                control.show();
                pressRippleCentered();
                event.consume();
            }
            return;
        }
        if (handleNavigationKey(code)) {
            event.consume();
        }
    }

    /**
     * Navigation shared by the control (non-searchable popup) and the search field
     * (searchable popup). Returns whether the key was a navigation key.
     */
    private boolean handleNavigationKey(KeyCode code) {
        switch (code) {
            case ESCAPE:
                getSkinnable().hide();
                return true;
            case UP:
                moveCursor(-1);
                return true;
            case DOWN:
                moveCursor(1);
                return true;
            case ENTER:
                activateCursor();
                return true;
            default:
                return false;
        }
    }

    /**
     * Key focus lives in the editable search field while a searchable popup is open;
     * intercept navigation keys before the text field's own behavior and let all
     * other keys (letters, backspace, caret movement) edit the query natively.
     */
    private void onSearchFieldKeyPressed(KeyEvent event) {
        if (handleNavigationKey(event.getCode())) {
            event.consume();
        }
    }

    private void moveCursor(int delta) {
        MultipleSelectionModel<T> cursor = popupList.getSelectionModel();
        int count = filtered.size();
        if (cursor == null || count == 0) {
            return;
        }
        int current = cursor.getSelectedIndex();
        int next;
        if (current < 0) {
            next = delta > 0 ? 0 : count - 1;
        } else {
            next = clampIndex(current + delta, count);
        }
        cursor.select(next);
        popupList.scrollTo(next);
    }

    private void activateCursor() {
        MultipleSelectionModel<T> cursor = popupList.getSelectionModel();
        if (cursor == null) {
            return;
        }
        int viewIndex = cursor.getSelectedIndex();
        if (viewIndex < 0 && !filtered.isEmpty()) {
            viewIndex = 0;
        }
        activate(viewIndex);
    }

    private void activate(int viewIndex) {
        if (viewIndex < 0 || viewIndex >= filtered.size()) {
            return;
        }
        RXSelectionBox<T> control = getSkinnable();
        MultipleSelectionModel<T> model = control.getSelectionModel();
        if (model == null) {
            return;
        }
        int sourceIndex = filtered.getSourceIndex(viewIndex);
        if (sourceIndex < 0) {
            return;
        }
        if (control.getSelectionMode() == SelectionMode.MULTIPLE) {
            if (model.isSelected(sourceIndex)) {
                model.clearSelection(sourceIndex);
            } else {
                model.select(sourceIndex);
            }
        } else {
            model.clearAndSelect(sourceIndex);
            if (control.isAutoHideOnSelection()) {
                control.hide();
            }
        }
    }

    private void pressRippleCentered() {
        RXSelectionBox<T> control = getSkinnable();
        if (!control.isRippleEnabled() || control.isDisabled() || control.isReadOnly()) {
            return;
        }
        ripple.press(0.0, 0.0, true);
        ripple.release();
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        display.resizeRelocate(x, y, w, h);
        ripple.layout(display.getWidth(), display.getHeight());
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double content = Math.max(DEFAULT_PREF_WIDTH, display.prefWidth(-1));
        return leftInset + content + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + display.prefHeight(-1) + bottomInset;
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected void disposeSkin() {
        // The entrance Timeline is rebuilt on each show; stop the live one by reading
        // the field rather than registering a stale reference on the disposer.
        if (entrance != null) {
            entrance.stop();
            entrance = null;
        }
        clearButton.setOnAction(null);
        selectAllButton.setOnAction(null);
    }

    // ==================== Helpers ====================

    private String textOf(T item) {
        StringConverter<T> converter = getSkinnable().getConverter();
        if (converter != null) {
            return nullToEmpty(converter.toString(item));
        }
        return item == null ? "" : item.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int clampIndex(int index, int count) {
        if (index < 0) {
            return 0;
        }
        if (index >= count) {
            return count - 1;
        }
        return index;
    }

    // ==================== Custom multi/single cell ====================

    /**
     * List cell that renders a leading membership indicator (checkbox in
     * multiple mode, checkmark in single mode) driven by the authoritative
     * selection model, mapped through the filtered view to source indices.
     */
    private static final class SelectionCell<T> extends RXListCell<T> {

        private static final PseudoClass CELL_CHECKED = PseudoClass.getPseudoClass("checked");
        private static final PseudoClass CELL_MULTIPLE = PseudoClass.getPseudoClass("multiple");

        private final RXSelectionBox<T> box;
        private final FilteredList<T> filtered;
        private final StackPane indicator = new StackPane();
        private final Region check = new Region();
        private final Label label = new Label();
        private final HBox content = new HBox();
        private final InvalidationListener refresher = obs -> refreshIndicator();

        SelectionCell(RXSelectionBox<T> box, FilteredList<T> filtered) {
            this.box = box;
            this.filtered = filtered;

            check.getStyleClass().add("check");
            check.setMouseTransparent(true);
            indicator.getStyleClass().add("select-box");
            indicator.setMouseTransparent(true);
            indicator.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            indicator.getChildren().add(check);

            label.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(label, Priority.ALWAYS);

            content.getStyleClass().add("select-content");
            content.setAlignment(Pos.CENTER_LEFT);
            content.getChildren().addAll(indicator, label);

            MultipleSelectionModel<T> model = box.getSelectionModel();
            if (model != null) {
                model.getSelectedIndices().addListener(new WeakInvalidationListener(refresher));
            }
            box.selectionModeProperty().addListener(new WeakInvalidationListener(refresher));
        }

        @Override
        protected Node createContent(T item) {
            label.setText(primaryText(item));
            refreshIndicator();
            return content;
        }

        private void refreshIndicator() {
            boolean multiple = box.getSelectionMode() == SelectionMode.MULTIPLE;
            indicator.pseudoClassStateChanged(CELL_MULTIPLE, multiple);
            boolean checked = false;
            MultipleSelectionModel<T> model = box.getSelectionModel();
            int viewIndex = getIndex();
            if (model != null && viewIndex >= 0 && viewIndex < filtered.size()) {
                int sourceIndex = filtered.getSourceIndex(viewIndex);
                checked = sourceIndex >= 0 && model.isSelected(sourceIndex);
            }
            indicator.pseudoClassStateChanged(CELL_CHECKED, checked);
        }
    }
}
