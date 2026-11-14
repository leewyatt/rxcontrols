package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXPickerBase;
import io.github.leewyatt.rxcontrols.RXTemporalViewBase;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts.ParseResult;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalKeyTarget;
import io.github.leewyatt.rxcontrols.internal.popup.RXPopupSupport;
import io.github.leewyatt.rxcontrols.internal.popup.RXPopupWidthMode;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared skeleton of the picker skins: text field layout with a trailing icon / clear
 * button, the popup lifecycle around a selection view, keyboard routing from the
 * fields into the popup view, focus mirroring, and the commit / revert flow of typed
 * text. Subclasses supply the fields, the view, the footer, and the value-specific
 * parsing, formatting and draft handling.
 *
 * <p>The popup never takes focus. Keys reach the view through a filter on each
 * field: Up / Down / Page Up / Page Down always go to the view while the popup is
 * open; Left / Right / Home / End and Enter go to it once its keyboard cursor is
 * shown (typing hides the cursor again, giving the arrows back to the caret).
 *
 * @param <T> the picker value type
 * @param <C> the picker type
 */
abstract class RXPickerSkinBase<T, C extends RXPickerBase<T>> extends RXSkinBase<C> {

    // ==================== Constants ====================

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final String FOCUSED_KEY = "FOCUSED";
    private static final double POPUP_GAP = 4.0;

    // ==================== Nodes ====================

    private final StackPane suffix = new StackPane();
    private final Region icon = new Region();
    private final StackPane clearButton = new StackPane();
    private final VBox popupContent = new VBox();
    private final HBox footer = new HBox();
    private final RXPopupSupport popupSupport;

    private final List<Node> layoutNodes = new ArrayList<>();
    private final List<TextField> editors = new ArrayList<>();
    private final List<Boolean> userEdited = new ArrayList<>();
    private RXTemporalViewBase<?> view;

    // ==================== State ====================

    private final InvalidationListener focusOwnerListener = observable -> onFocusOwnerChanged();
    private Scene observedScene;
    private boolean focusInside;
    private boolean writingText;
    private boolean invalidText;

    // ==================== Constructor ====================

    /**
     * Creates the skeleton. Subclasses build their fields and view, then call
     * {@link #install}.
     *
     * @param control the picker
     */
    protected RXPickerSkinBase(C control) {
        super(control);
        popupContent.getStyleClass().add("container");
        footer.getStyleClass().add("footer");
        popupSupport = new RXPopupSupport(popupContent);
        popupSupport.setPopupStyleClass("rx-picker-popup");
        popupSupport.setWidthMode(RXPopupWidthMode.PREF_CONTENT);
        popupSupport.setOffset(0.0, POPUP_GAP);
        // The picker handles Escape itself: the popup's own Escape auto-hide runs in
        // the owner window's capturing phase without consuming the key, which would
        // let an enclosing dialog's cancel button fire as well.
        popupSupport.setHideOnEscape(false);
        popupSupport.setOnHidden(this::onPopupHidden);
        disposer.registerDisposeTask(popupSupport::dispose);

        icon.getStyleClass().add("icon");
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        icon.setMouseTransparent(true);
        Region clearGraphic = new Region();
        clearGraphic.getStyleClass().add("graphic");
        clearGraphic.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        clearGraphic.setMouseTransparent(true);
        clearButton.getStyleClass().add("clear-button");
        clearButton.getChildren().add(clearGraphic);
        clearButton.setMouseTransparent(true);
        suffix.getStyleClass().add("suffix");
        suffix.getChildren().addAll(icon, clearButton);
    }

    /**
     * Installs the fields, view and footer buttons. Call once from the subclass
     * constructor, after the subclass state is ready.
     *
     * @param fieldRow       the field row in display order: text fields plus any separators
     * @param selectionView  the popup selection view (made non-focus-traversable here)
     * @param footerButtons  footer buttons, possibly none
     */
    protected final void install(List<Node> fieldRow, RXTemporalViewBase<?> selectionView, Button... footerButtons) {
        C control = getSkinnable();
        this.view = selectionView;
        selectionView.setFocusTraversable(false);
        popupContent.getChildren().add(selectionView);
        if (footerButtons.length > 0) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            for (Button button : footerButtons) {
                if (button.getStyleClass().contains("ok-button")) {
                    footer.getChildren().add(spacer);
                }
                footer.getChildren().add(button);
            }
            popupContent.getChildren().add(footer);
        }
        for (Node node : fieldRow) {
            layoutNodes.add(node);
            if (node instanceof TextField) {
                TextField editor = (TextField) node;
                editors.add(editor);
                userEdited.add(false);
                installEditor(control, editor);
            }
        }
        getChildren().setAll(layoutNodes);
        getChildren().add(suffix);
        // Remove only this skin's nodes: a replacement skin has already added its own.
        disposer.registerDisposeTask(() -> {
            getChildren().removeAll(layoutNodes);
            getChildren().remove(suffix);
        });

        disposer.registerEventHandler(suffix, MouseEvent.MOUSE_PRESSED, this::handleSuffixPressed);
        // A filter: the text field's own behavior consumes the presses it receives.
        disposer.registerEventFilter(control, MouseEvent.MOUSE_PRESSED, this::handleControlPressed);
        // A field's Enter fires an ActionEvent that would bubble to the picker's
        // onAction; consume that copy here (consuming on the field would eat Enter
        // for an enclosing dialog's default button).
        disposer.registerEventFilter(control, ActionEvent.ACTION, event -> {
            if (editors.contains(event.getTarget())) {
                event.consume();
            }
        });
        disposer.registerListener(control.showingProperty(), this::syncPopup);
        disposer.registerListener(control.disabledProperty(), () -> {
            if (control.isDisabled()) {
                control.hide();
            }
        });
        disposer.registerListener(control.sceneProperty(), this::observeScene);
        disposer.registerDisposeTask(() -> {
            if (observedScene != null) {
                observedScene.focusOwnerProperty().removeListener(focusOwnerListener);
                observedScene = null;
            }
        });
        observeScene();
        disposer.registerDisposeTask(() -> control.getProperties().put(FOCUSED_KEY, false));
        disposer.registerListener(control.hoverProperty(), this::updateSuffix);
        disposer.registerListener(control.clearableProperty(), this::updateSuffix);
        disposer.registerListener(control.valueProperty(), this::onValueChanged);
        disposer.registerListener(control.editableProperty(), this::updateEditable);
        disposer.registerEventHandler(selectionView, ActionEvent.ACTION, event -> {
            event.consume();
            onViewAction();
        });
        updateEditable();
        updateSuffix();
        refreshFields();
        syncPopup();
    }

    private void installEditor(C control, TextField editor) {
        int index = editors.size() - 1;
        disposer.registerEventFilter(editor, KeyEvent.KEY_PRESSED, this::handleEditorKey);
        disposer.registerListener(editor.textProperty(), () -> {
            if (!writingText) {
                userEdited.set(index, true);
                setInvalidText(false);
                onFieldsEdited();
            }
        });
        disposer.registerListener(editor.focusedProperty(), this::updateFocus);
        disposer.registerEventHandler(editor, MouseEvent.MOUSE_PRESSED, event -> clearKeyboardCursor());
    }

    // ==================== Subclass hooks ====================

    /**
     * Seeds the popup view's draft from the committed value, just before the popup opens.
     */
    protected abstract void seedDraft();

    /**
     * Writes the committed value's text into the fields, using {@link #setFieldText}.
     */
    protected abstract void writeFieldsFromValue();

    /**
     * Parses the fields' current text.
     *
     * @return the parse outcome; {@code EMPTY} commits {@code null}
     */
    protected abstract ParseResult<T> parseFields();

    /**
     * Returns whether a value satisfies the picker's constraints.
     *
     * @param value the value, never {@code null}
     * @return {@code true} if the value may be committed
     */
    protected abstract boolean isAllowed(T value);

    /**
     * Called when the popup view fires its action (the user completed a pick).
     */
    protected abstract void onViewAction();

    /**
     * Shows a value parsed from typed text in the open popup, as a draft. The default
     * does nothing.
     *
     * @param value the parsed, allowed value
     */
    protected void showTypedDraft(T value) {
    }

    /**
     * Called for Enter while the popup is open, there is no typed text and the view's
     * keyboard cursor is hidden. The default closes the popup; pickers that confirm a
     * draft commit it instead.
     */
    protected void confirmDraft() {
        getSkinnable().hide();
    }

    /**
     * Called after the popup closed without a commit. The default restores the
     * fields' text from the committed value unless the user typed into them.
     */
    protected void onPopupClosed() {
        if (!anyUserEdited()) {
            refreshFields();
        }
    }

    // ==================== Commit ====================

    /**
     * Commits a value: sets it, refreshes the fields, fires an {@link ActionEvent}
     * on the picker and closes the popup.
     *
     * @param value the value to commit, or {@code null} to clear
     */
    protected final void commit(T value) {
        C control = getSkinnable();
        control.setValue(value);
        refreshFields();
        control.fireEvent(new ActionEvent());
        control.hide();
    }

    /**
     * Commits the typed text when the user edited a field and it parses to an allowed
     * value (blank text commits {@code null}); otherwise flags the text invalid.
     *
     * @return {@code true} if a commit happened
     */
    protected final boolean commitTypedText() {
        if (!anyUserEdited()) {
            return false;
        }
        ParseResult<T> result = parseFields();
        switch (result.status()) {
            case EMPTY:
                commit(null);
                return true;
            case VALID:
                if (isAllowed(result.value())) {
                    commit(result.value());
                    return true;
                }
                setInvalidText(true);
                return false;
            case INVALID:
            default:
                setInvalidText(true);
                return false;
        }
    }

    /**
     * Rewrites every field from the committed value and clears the typed-text state.
     */
    protected final void refreshFields() {
        writeFieldsFromValue();
        for (int i = 0; i < userEdited.size(); i++) {
            userEdited.set(i, false);
        }
        setInvalidText(false);
    }

    /**
     * Writes text into a field without marking it as typed by the user (used for the
     * committed value and for draft previews).
     *
     * @param field the field
     * @param text  the text, {@code null} treated as empty
     */
    protected final void setFieldText(TextField field, String text) {
        writingText = true;
        try {
            field.setText(text == null ? "" : text);
            int index = editors.indexOf(field);
            if (index >= 0) {
                userEdited.set(index, false);
            }
        } finally {
            writingText = false;
        }
    }

    /**
     * Returns whether the user typed into any field since it was last written.
     *
     * @return {@code true} if a field holds user-typed text
     */
    protected final boolean anyUserEdited() {
        return userEdited.contains(Boolean.TRUE);
    }

    /**
     * Recomputes the {@code :invalid} pseudo-class from the typed text and the
     * committed value.
     */
    protected final void updateInvalid() {
        C control = getSkinnable();
        T value = control.getValue();
        control.pseudoClassStateChanged(INVALID, invalidText || (value != null && !isAllowed(value)));
    }

    /**
     * Marks a field as the active one (the {@code :active} pseudo-class).
     *
     * @param field  the field
     * @param active whether it is active
     */
    protected final void setFieldActive(TextField field, boolean active) {
        field.pseudoClassStateChanged(ACTIVE, active);
    }

    /**
     * Shows or hides the popup footer.
     *
     * @param shown {@code true} to show the footer
     */
    protected final void setFooterShown(boolean shown) {
        footer.setVisible(shown);
        footer.setManaged(shown);
    }

    /**
     * Returns the popup view.
     *
     * @return the view installed by {@link #install}
     */
    protected final RXTemporalViewBase<?> view() {
        return view;
    }

    /**
     * Binds a footer button's text and registers its action, consuming the button's
     * own action event.
     *
     * @param styleClass the button style class
     * @param text       the text source
     * @param action     the action
     * @return the button, not focus-traversable so it never pulls focus into the popup
     */
    protected final Button footerButton(String styleClass, ObservableValue<String> text, Runnable action) {
        Button button = new Button();
        button.getStyleClass().add(styleClass);
        button.setFocusTraversable(false);
        disposer.registerBinding(button.textProperty(), text);
        disposer.registerEventHandler(button, ActionEvent.ACTION, event -> {
            event.consume();
            action.run();
        });
        return button;
    }

    // ==================== Draft from typing ====================

    private void onFieldsEdited() {
        if (!getSkinnable().isShowing()) {
            return;
        }
        clearKeyboardCursor();
        ParseResult<T> result = parseFields();
        if (result.status() == ParseResult.Status.VALID && isAllowed(result.value())) {
            showTypedDraft(result.value());
        }
    }

    private void setInvalidText(boolean invalid) {
        invalidText = invalid;
        for (TextField editor : editors) {
            editor.pseudoClassStateChanged(INVALID, invalid);
        }
        updateInvalid();
    }

    private void onValueChanged() {
        refreshFields();
        if (getSkinnable().isShowing()) {
            seedDraft();
        }
        updateSuffix();
    }

    // ==================== Keyboard ====================

    private void handleEditorKey(KeyEvent event) {
        // Every filter on a node runs even after an earlier one consumed the event.
        if (event.isConsumed()) {
            return;
        }
        C control = getSkinnable();
        KeyCode code = event.getCode();
        boolean plain = !event.isShiftDown() && !event.isControlDown() && !event.isAltDown() && !event.isMetaDown();
        boolean altOnly = event.isAltDown() && !event.isShiftDown() && !event.isControlDown() && !event.isMetaDown();
        if (code == KeyCode.ESCAPE && plain) {
            if (control.isShowing()) {
                // Escape discards both the popup draft and any typed text.
                refreshFields();
                control.hide();
                event.consume();
            } else if (anyUserEdited()) {
                refreshFields();
                event.consume();
            }
            return;
        }
        if ((code == KeyCode.F4 && plain) || ((code == KeyCode.UP || code == KeyCode.DOWN) && altOnly)) {
            toggle();
            event.consume();
            return;
        }
        TemporalKeyTarget target = keyTarget();
        if (code == KeyCode.SPACE && plain && !control.isEditable()) {
            if (!control.isShowing()) {
                control.show();
            } else if (target != null) {
                target.handleKey(event);
            }
            event.consume();
            return;
        }
        if (!control.isShowing()) {
            if (code == KeyCode.ENTER && plain) {
                // Not consumed: Enter still reaches an enclosing default button.
                commitTypedText();
            }
            return;
        }
        boolean cursorShown = target != null && target.isKeyboardCursorVisible();
        if (code == KeyCode.ENTER && plain) {
            if (cursorShown && target.handleKey(event)) {
                event.consume();
                return;
            }
            if (anyUserEdited()) {
                commitTypedText();
            } else {
                confirmDraft();
            }
            event.consume();
            return;
        }
        if (target != null && isRoutable(event, cursorShown || !control.isEditable())) {
            if (target.handleKey(event)) {
                event.consume();
            }
            return;
        }
        if (code == KeyCode.BACK_SPACE || code == KeyCode.DELETE || code.isLetterKey() || code.isDigitKey()) {
            clearKeyboardCursor();
        }
    }

    private static boolean isRoutable(KeyEvent event, boolean caretKeysToo) {
        if (event.isAltDown() || event.isControlDown() || event.isMetaDown()) {
            return false;
        }
        switch (event.getCode()) {
            case UP:
            case DOWN:
                return !event.isShiftDown();
            case PAGE_UP:
            case PAGE_DOWN:
                return true;
            case LEFT:
            case RIGHT:
            case HOME:
            case END:
                return caretKeysToo && !event.isShiftDown();
            default:
                return false;
        }
    }

    /**
     * Returns whether the popup view shows its keyboard cursor, i.e. whether Enter and
     * the caret keys currently belong to the view.
     *
     * @return {@code true} if the view's keyboard cursor is shown
     */
    protected final boolean isViewKeyboardCursorVisible() {
        TemporalKeyTarget target = keyTarget();
        return target != null && target.isKeyboardCursorVisible();
    }

    private TemporalKeyTarget keyTarget() {
        Skin<?> skin = view == null ? null : view.getSkin();
        return skin instanceof TemporalKeyTarget ? (TemporalKeyTarget) skin : null;
    }

    private void clearKeyboardCursor() {
        TemporalKeyTarget target = keyTarget();
        if (target != null) {
            target.clearKeyboardCursor();
        }
    }

    // ==================== Mouse ====================

    private void handleSuffixPressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        event.consume();
        C control = getSkinnable();
        focusFirstEditor();
        if (isClearVisible()) {
            commit(null);
        } else if (control.isShowing()) {
            control.hide();
        } else {
            control.show();
        }
    }

    private void handleControlPressed(MouseEvent event) {
        C control = getSkinnable();
        if (event.getButton() != MouseButton.PRIMARY || control.isDisabled() || isInSuffix(event.getTarget())) {
            return;
        }
        if (!isInside(event.getTarget(), editors)) {
            focusFirstEditor();
        }
        if (!control.isShowing()) {
            control.show();
        }
    }

    private boolean isInSuffix(Object target) {
        return isInside(target, List.of(suffix));
    }

    private static boolean isInside(Object target, List<? extends Node> containers) {
        for (Node node = target instanceof Node ? (Node) target : null; node != null; node = node.getParent()) {
            if (containers.contains(node)) {
                return true;
            }
        }
        return false;
    }

    private void toggle() {
        C control = getSkinnable();
        if (control.isShowing()) {
            control.hide();
        } else {
            control.show();
        }
    }

    // ==================== Focus ====================

    /** Mirrors the fields' visual focus (window focus included) onto the picker. */
    private void updateFocus() {
        boolean any = false;
        for (TextField editor : editors) {
            any |= editor.isFocused();
        }
        getSkinnable().getProperties().put(FOCUSED_KEY, any);
    }

    private void observeScene() {
        Scene scene = getSkinnable().getScene();
        if (scene == observedScene) {
            return;
        }
        if (observedScene != null) {
            observedScene.focusOwnerProperty().removeListener(focusOwnerListener);
        }
        observedScene = scene;
        if (scene != null) {
            scene.focusOwnerProperty().addListener(focusOwnerListener);
        }
        onFocusOwnerChanged();
    }

    /**
     * Commit-or-revert follows the scene's focus owner rather than node focus, so moving
     * between the picker's own fields is not a leave, and window deactivation (switching
     * applications) neither commits nor reverts typed text.
     */
    private void onFocusOwnerChanged() {
        Node owner = observedScene == null ? null : observedScene.getFocusOwner();
        if (owner != null && owner == getSkinnable()) {
            // requestFocus() on the picker: hand focus to a field without counting it
            // as leaving the picker.
            focusFirstEditor();
            return;
        }
        boolean inside = editors.contains(owner);
        boolean left = focusInside && !inside;
        focusInside = inside;
        if (left) {
            onFocusLeft();
        }
    }

    private void onFocusLeft() {
        if (anyUserEdited() && !commitTypedText()) {
            refreshFields();
        }
        getSkinnable().hide();
    }

    private void focusFirstEditor() {
        for (TextField editor : editors) {
            if (editor.isFocused()) {
                return;
            }
        }
        if (!editors.isEmpty()) {
            editors.get(0).requestFocus();
        }
    }

    private void updateEditable() {
        boolean editable = getSkinnable().isEditable();
        for (TextField editor : editors) {
            editor.setEditable(editable);
        }
    }

    // ==================== Suffix ====================

    private boolean isClearVisible() {
        C control = getSkinnable();
        return control.isClearable() && control.isHover() && control.getValue() != null && !control.isDisabled();
    }

    private void updateSuffix() {
        boolean clear = isClearVisible();
        clearButton.setVisible(clear);
        icon.setVisible(!clear);
    }

    // ==================== Popup ====================

    private void syncPopup() {
        C control = getSkinnable();
        if (control.isShowing()) {
            seedDraft();
            popupSupport.show(control);
        } else {
            popupSupport.hide();
            clearKeyboardCursor();
            onPopupClosed();
        }
    }

    /**
     * Every hide path of the popup window (outside click, owner detach) lands here and
     * pulls the picker's showing state back. A press on the picker itself never
     * auto-hides the popup (the picker is the popup's owner node), so no reopen guard
     * is needed.
     */
    private void onPopupHidden() {
        getSkinnable().hide();
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        double fixed = suffix.prefWidth(h);
        int growCount = 0;
        for (Node node : layoutNodes) {
            if (node instanceof TextField) {
                growCount++;
            } else {
                fixed += node.prefWidth(h);
            }
        }
        double growWidth = growCount == 0 ? 0.0 : Math.max(0.0, (w - fixed) / growCount);
        double cursorX = x;
        for (Node node : layoutNodes) {
            double nodeWidth = node instanceof TextField ? growWidth : node.prefWidth(h);
            layoutInArea(node, cursorX, y, nodeWidth, h, 0.0, HPos.CENTER,
                    VPos.CENTER);
            cursorX += nodeWidth;
        }
        layoutInArea(suffix, x + w - suffix.prefWidth(h), y, suffix.prefWidth(h), h, 0.0,
                HPos.CENTER, VPos.CENTER);
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double width = suffix.prefWidth(-1);
        for (Node node : layoutNodes) {
            width += node.prefWidth(-1);
        }
        return leftInset + width + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double height = suffix.prefHeight(-1);
        for (Node node : layoutNodes) {
            height = Math.max(height, node.prefHeight(-1));
        }
        return topInset + height + bottomInset;
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        // Not SkinBase's default, which measures the children's current positions and
        // so ratchets the minimum up to the last laid-out width.
        double width = suffix.prefWidth(-1);
        for (Node node : layoutNodes) {
            width += node instanceof TextField ? node.minWidth(-1) : node.prefWidth(-1);
        }
        return leftInset + width + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return getSkinnable().prefWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }
}
