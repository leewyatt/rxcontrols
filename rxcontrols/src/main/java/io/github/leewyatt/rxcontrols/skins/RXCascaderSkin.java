package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderPanel;
import io.github.leewyatt.rxcontrols.RXCascaderPath;
import io.github.leewyatt.rxcontrols.RXCascaderSelectionMode;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.WindowEvent;
import javafx.util.Callback;

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
    private final Label clearButton = new Label("x");
    private final Label arrow = new Label("v");
    private final PopupControl popup = new PopupControl();
    private final EventHandler<WindowEvent> popupHiddenHandler = this::handlePopupHidden;

    // ==================== State ====================

    private boolean suppressReopen;

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given cascader.
     *
     * @param control the skinnable cascader
     */
    public RXCascaderSkin(RXCascader<T> control) {
        super(control);
        initializeNodes(control);
        initializePopup(control);
        registerListeners(control);
        getChildren().setAll(display);
        updateDisplay();
        syncPopupShowing();
    }

    private void initializeNodes(RXCascader<T> control) {
        display.getStyleClass().add("rx-cascader-display");
        display.setAlignment(Pos.CENTER_LEFT);
        textLabel.getStyleClass().add("rx-cascader-display-text");
        textLabel.setMaxWidth(Double.MAX_VALUE);
        clearButton.getStyleClass().add("rx-cascader-clear-button");
        arrow.getStyleClass().add("rx-cascader-arrow");
        HBox.setHgrow(textLabel, Priority.ALWAYS);
        display.getChildren().setAll(textLabel, clearButton, arrow);

        disposer.registerEventHandler(display, MouseEvent.MOUSE_CLICKED, event -> handleDisplayClicked(control, event));
        disposer.registerEventHandler(clearButton, MouseEvent.MOUSE_CLICKED, event -> handleClearClicked(control, event));
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, event -> handleKeyPressed(control, event));
    }

    private void initializePopup(RXCascader<T> control) {
        popup.getStyleClass().add("rx-cascader-popup");
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);
        popup.setSkin(new CascaderPopupSkin<>(popup, control.getPanel()));
        popup.addEventHandler(WindowEvent.WINDOW_HIDDEN, popupHiddenHandler);
        disposer.registerDisposeTask(() -> popup.removeEventHandler(WindowEvent.WINDOW_HIDDEN, popupHiddenHandler));
    }

    private void registerListeners(RXCascader<T> control) {
        disposer.registerListener(control.showingProperty(), this::syncPopupShowing);
        disposer.registerListener(control.disabledProperty(), () -> {
            if (control.isDisabled()) {
                control.hide();
            }
        });
        disposer.registerListener(control.selectedPathProperty(), () -> handleSelectionChanged(control));
        disposer.registerListener(control.getCheckedPaths(), this::updateDisplay);
        disposer.registerListener(control.selectionModeProperty(), this::updateDisplay);
        disposer.registerListener(control.promptTextProperty(), this::updateDisplay);
        disposer.registerListener(control.pathTextFactoryProperty(), this::updateDisplay);
        disposer.registerListener(control.clearableProperty(), this::updateDisplay);
        disposer.registerListener(control.getPanel().widthProperty(), this::positionPopupIfShowing);
        disposer.registerListener(control.getPanel().heightProperty(), this::positionPopupIfShowing);
        // Keep the popup glued to the control when surrounding layout moves it
        // (e.g. a sibling node grows and shifts the control within its parent).
        // The local-to-scene transform changes whenever any ancestor relayouts.
        disposer.registerListener(control.localToSceneTransformProperty(), this::positionPopupIfShowing);
        disposer.registerListener(control.boundsInLocalProperty(), this::positionPopupIfShowing);
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

    private void handlePopupHidden(WindowEvent event) {
        getSkinnable().hide();
        suppressReopen = true;
        Platform.runLater(() -> suppressReopen = false);
    }

    private void handleSelectionChanged(RXCascader<T> control) {
        updateDisplay();
        if (control.getSelectionMode() == RXCascaderSelectionMode.SINGLE
                && control.getSelectedPath() != null) {
            control.hide();
        }
    }

    // ==================== Display ====================

    private void updateDisplay() {
        RXCascader<T> control = getSkinnable();
        boolean hasSelection = hasSelection(control);
        String displayText = hasSelection ? selectedText(control) : promptText(control);

        textLabel.setText(displayText);
        clearButton.setVisible(control.isClearable() && hasSelection);
        clearButton.setManaged(control.isClearable() && hasSelection);
        arrow.setText(control.isShowing() ? "^" : "v");

        control.pseudoClassStateChanged(EMPTY, !hasSelection);
        control.pseudoClassStateChanged(SHOWING, control.isShowing());
        textLabel.pseudoClassStateChanged(EMPTY, !hasSelection);
    }

    private boolean hasSelection(RXCascader<T> control) {
        if (control.getSelectionMode() == RXCascaderSelectionMode.MULTIPLE) {
            return !control.getCheckedPaths().isEmpty();
        }
        return control.getSelectedPath() != null;
    }

    private String selectedText(RXCascader<T> control) {
        if (control.getSelectionMode() == RXCascaderSelectionMode.MULTIPLE) {
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
        String text = factory == null ? path.toString() : factory.call(path);
        return text == null ? "" : text;
    }

    // ==================== Popup ====================

    private void syncPopupShowing() {
        updateDisplay();
        if (getSkinnable().isShowing()) {
            showPopup();
        } else {
            hidePopup();
        }
    }

    private void showPopup() {
        RXCascader<T> control = getSkinnable();
        if (control.getScene() == null || control.getScene().getWindow() == null) {
            control.hide();
            return;
        }

        control.getPanel().applyCss();

        Bounds screenBounds = control.localToScreen(control.getBoundsInLocal());
        if (screenBounds == null) {
            control.hide();
            return;
        }
        if (popup.isShowing()) {
            popup.setAnchorX(screenBounds.getMinX());
            popup.setAnchorY(screenBounds.getMaxY());
        } else {
            popup.show(control, screenBounds.getMinX(), screenBounds.getMaxY());
        }
    }

    private void hidePopup() {
        if (popup.isShowing()) {
            popup.hide();
        }
    }

    private void positionPopupIfShowing() {
        if (!getSkinnable().isShowing() || !popup.isShowing()) {
            return;
        }
        RXCascader<T> control = getSkinnable();
        Bounds screenBounds = control.localToScreen(control.getBoundsInLocal());
        if (screenBounds != null) {
            popup.setAnchorX(screenBounds.getMinX());
            popup.setAnchorY(screenBounds.getMaxY());
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

    @Override
    protected void disposeSkin() {
        popup.hide();
        popup.setSkin(null);
    }

    // ==================== Popup Skin ====================

    private static final class CascaderPopupSkin<T> implements Skin<PopupControl> {

        private PopupControl popup;
        private Node content;

        private CascaderPopupSkin(PopupControl popup, RXCascaderPanel<T> panel) {
            this.popup = popup;
            this.content = panel;
        }

        @Override
        public PopupControl getSkinnable() {
            return popup;
        }

        @Override
        public Node getNode() {
            return content;
        }

        @Override
        public void dispose() {
            popup = null;
            content = null;
        }
    }
}
