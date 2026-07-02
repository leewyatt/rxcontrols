package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderView;
import io.github.leewyatt.rxcontrols.RXCascaderPath;
import io.github.leewyatt.rxcontrols.internal.CascaderText;
import io.github.leewyatt.rxcontrols.internal.popup.RXPopupSupport;
import io.github.leewyatt.rxcontrols.internal.popup.RXPopupWidthMode;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Default skin for {@link RXCascader}.
 *
 * @param <T> application value type
 */
public class RXCascaderSkin<T> extends RXSkinBase<RXCascader<T>> {

    // ==================== Constants ====================

    private static final double DEFAULT_PREF_WIDTH = 220.0;
    private static final double DEFAULT_PREF_HEIGHT = 34.0;
    private static final PseudoClass EMPTY = PseudoClass.getPseudoClass("empty");
    private static final PseudoClass SHOWING = PseudoClass.getPseudoClass("showing");

    // ==================== Nodes ====================

    private final HBox display = new HBox();
    private final Label textLabel = new Label();
    private final StackPane clearButton = new StackPane();
    private final Region clearGraphic = new Region();
    private final StackPane arrowButton = new StackPane();
    private final Region arrow = new Region();

    // ==================== State ====================

    /** The embedded view used as popup content, injected by the control. */
    private final RXCascaderView<T> view;

    /** Positioning + lifecycle for the popup shell hosting {@link #view}. */
    private final RXPopupSupport popupSupport;

    private boolean suppressReopen;

    /** Items of the currently displayed path(s) whose value the field text mirrors. */
    private final List<RXCascaderItem<T>> observedPathItems = new ArrayList<>();
    private final InvalidationListener pathValueListener = observable -> updateDisplay();

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given cascader. Intended to be created by the
     * control's {@code createDefaultSkin()}: {@code view} must be that control's
     * own embedded popup view, otherwise the popup is wired to a foreign view and
     * will not reflect the control's state.
     *
     * @param control the skinnable cascader
     * @param view    the control's embedded view to host as popup content
     */
    public RXCascaderSkin(RXCascader<T> control, RXCascaderView<T> view) {
        super(control);
        this.view = view;
        this.popupSupport = new RXPopupSupport(view);
        initializeNodes(control);
        initializePopupSupport();
        registerListeners(control);
        getChildren().setAll(display);
        // Observe the value of any selection that already existed before this skin
        // was created (skins are created lazily, after a select() can have run).
        rebindPathValueListeners();
        updateDisplay();
        syncPopupShowing();
    }

    private void initializeNodes(RXCascader<T> control) {
        display.getStyleClass().add("display");
        textLabel.setMaxWidth(Double.MAX_VALUE);

        clearGraphic.getStyleClass().add("graphic");
        clearGraphic.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        clearGraphic.setMouseTransparent(true);
        clearButton.getStyleClass().add("clear-button");
        clearButton.getChildren().add(clearGraphic);

        arrow.getStyleClass().add("arrow");
        arrow.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        arrow.setMouseTransparent(true);
        arrowButton.getStyleClass().add("arrow-button");
        arrowButton.getChildren().add(arrow);

        HBox.setHgrow(textLabel, Priority.ALWAYS);
        display.getChildren().setAll(textLabel, clearButton, arrowButton);

        disposer.registerEventHandler(display, MouseEvent.MOUSE_CLICKED, event -> handleDisplayClicked(control, event));
        disposer.registerEventHandler(clearButton, MouseEvent.MOUSE_CLICKED, event -> handleClearClicked(control, event));
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, event -> handleKeyPressed(control, event));
    }

    private void initializePopupSupport() {
        // The cascader popup width follows the view's own (multi-column) width, not
        // the trigger width, so it uses PREF_CONTENT rather than the default
        // anchor-width lower bound. The shell stays transparent and token-less.
        popupSupport.setPopupStyleClass("rx-cascader-popup");
        popupSupport.setWidthMode(RXPopupWidthMode.PREF_CONTENT);
        // Preserve the cascader's historical close semantics: the event that
        // auto-hides the popup is consumed (PopupWindow's own default), so it does
        // not additionally act on whatever sits beneath the popup. RXPopupSupport
        // defaults this to false (combo-box style) for other consumers.
        popupSupport.setConsumeAutoHidingEvents(true);
        popupSupport.setOnHidden(this::onPopupHidden);
        disposer.registerDisposeTask(popupSupport::dispose);
    }

    private void registerListeners(RXCascader<T> control) {
        disposer.registerListener(control.showingProperty(), this::syncPopupShowing);
        disposer.registerListener(control.disabledProperty(), () -> {
            if (control.isDisabled()) {
                control.hide();
            }
        });
        disposer.registerListener(control.selectedPathProperty(), () -> handleSelectionChanged(control));
        disposer.registerListener(control.getCheckedPaths(), this::onSelectionItemsChanged);
        disposer.registerListener(control.selectionModeProperty(), this::onSelectionItemsChanged);
        disposer.registerListener(control.promptTextProperty(), this::updateDisplay);
        disposer.registerListener(control.pathTextFactoryProperty(), this::updateDisplay);
        disposer.registerListener(control.itemTextFactoryProperty(), this::updateDisplay);
        disposer.registerListener(control.separatorProperty(), this::updateDisplay);
        disposer.registerListener(control.showAllLevelsProperty(), this::updateDisplay);
        disposer.registerListener(control.clearableProperty(), this::updateDisplay);
        // The field mirrors the live value of the displayed path items; drop those
        // listeners on dispose.
        disposer.registerDisposeTask(this::clearPathValueListeners);
    }

    // ==================== Events ====================

    private void handleDisplayClicked(RXCascader<T> control, MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || control.isDisabled()) {
            return;
        }
        control.requestFocus();
        if (control.isShowing()) {
            control.hide();
        } else if (!suppressReopen) {
            // Guard against the auto-hide/reopen race: a press on the display
            // can auto-hide the popup before this click runs, which would
            // otherwise immediately reopen it.
            control.show();
        }
        event.consume();
    }

    private void handleClearClicked(RXCascader<T> control, MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || !control.isClearable()) {
            return;
        }
        control.clearSelection();
        control.requestFocus();
        event.consume();
    }

    private void handleKeyPressed(RXCascader<T> control, KeyEvent event) {
        if (control.isDisabled()) {
            return;
        }
        if (event.getCode() == KeyCode.ESCAPE) {
            control.hide();
            event.consume();
        } else if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
            if (control.isShowing()) {
                control.hide();
            } else {
                control.show();
            }
            event.consume();
        }
    }

    /**
     * Driven by the popup support on every hide path (auto-hide, Escape,
     * programmatic hide, owner detach): pull the control's showing state back and
     * arm the reopen guard for the current pulse.
     */
    private void onPopupHidden() {
        getSkinnable().hide();
        suppressReopen = true;
        Platform.runLater(() -> suppressReopen = false);
    }

    private void handleSelectionChanged(RXCascader<T> control) {
        rebindPathValueListeners();
        updateDisplay();
        if (control.getSelectionMode() != SelectionMode.MULTIPLE
                && control.getSelectedPath() != null) {
            control.hide();
        }
    }

    private void onSelectionItemsChanged() {
        rebindPathValueListeners();
        updateDisplay();
    }

    /**
     * Rebinds the field's value listeners to the items of the currently displayed
     * path(s) — the selected path in single mode, or every checked path's items in
     * multiple mode — so changing a displayed item's value refreshes the field.
     */
    private void rebindPathValueListeners() {
        clearPathValueListeners();
        RXCascader<T> control = getSkinnable();
        if (control.getSelectionMode() == SelectionMode.MULTIPLE) {
            for (RXCascaderPath<T> path : control.getCheckedPaths()) {
                for (RXCascaderItem<T> item : path.getItems()) {
                    if (!observedPathItems.contains(item)) {
                        observedPathItems.add(item);
                    }
                }
            }
        } else {
            RXCascaderPath<T> selected = control.getSelectedPath();
            if (selected != null) {
                observedPathItems.addAll(selected.getItems());
            }
        }
        for (RXCascaderItem<T> item : observedPathItems) {
            item.valueProperty().addListener(pathValueListener);
        }
    }

    private void clearPathValueListeners() {
        for (RXCascaderItem<T> item : observedPathItems) {
            item.valueProperty().removeListener(pathValueListener);
        }
        observedPathItems.clear();
    }

    // ==================== Display ====================

    private void updateDisplay() {
        RXCascader<T> control = getSkinnable();
        boolean hasSelection = hasSelection(control);
        String displayText = hasSelection ? selectedText(control) : promptText(control);

        textLabel.setText(displayText);
        clearButton.setVisible(control.isClearable() && hasSelection);
        clearButton.setManaged(control.isClearable() && hasSelection);

        control.pseudoClassStateChanged(EMPTY, !hasSelection);
        control.pseudoClassStateChanged(SHOWING, control.isShowing());
        textLabel.pseudoClassStateChanged(EMPTY, !hasSelection);
    }

    private boolean hasSelection(RXCascader<T> control) {
        if (control.getSelectionMode() == SelectionMode.MULTIPLE) {
            return !control.getCheckedPaths().isEmpty();
        }
        return control.getSelectedPath() != null;
    }

    private String selectedText(RXCascader<T> control) {
        if (control.getSelectionMode() == SelectionMode.MULTIPLE) {
            StringJoiner joiner = new StringJoiner(", ");
            for (RXCascaderPath<T> path : control.getCheckedPaths()) {
                joiner.add(formatPath(control, path));
            }
            return joiner.toString();
        }
        return formatPath(control, control.getSelectedPath());
    }

    private String promptText(RXCascader<T> control) {
        String promptText = control.getPromptText();
        return promptText == null ? "" : promptText;
    }

    private String formatPath(RXCascader<T> control, RXCascaderPath<T> path) {
        if (path == null) {
            return "";
        }
        Callback<RXCascaderPath<T>, String> factory = control.getPathTextFactory();
        String text = factory == null
                ? defaultPathText(control, path)
                : factory.call(path);
        return text == null ? "" : text;
    }

    private String defaultPathText(RXCascader<T> control, RXCascaderPath<T> path) {
        Callback<T, String> itemTextFactory = control.getItemTextFactory();
        if (!control.isShowAllLevels()) {
            RXCascaderItem<T> leaf = path.getLeaf();
            return leaf == null ? "" : CascaderText.resolve(itemTextFactory, leaf.getValue());
        }
        String separator = control.getSeparator();
        StringJoiner joiner = new StringJoiner(separator == null ? RXCascader.DEFAULT_SEPARATOR : separator);
        for (RXCascaderItem<T> item : path.getItems()) {
            joiner.add(CascaderText.resolve(itemTextFactory, item.getValue()));
        }
        return joiner.toString();
    }

    // ==================== Popup ====================

    private void syncPopupShowing() {
        updateDisplay();
        if (getSkinnable().isShowing()) {
            popupSupport.show(getSkinnable());
        } else {
            popupSupport.hide();
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        display.resizeRelocate(x, y, Math.max(0.0, w), Math.max(0.0, h));
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + Math.max(DEFAULT_PREF_WIDTH, display.prefWidth(height)) + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + Math.max(DEFAULT_PREF_HEIGHT, display.prefHeight(width)) + bottomInset;
    }
}
