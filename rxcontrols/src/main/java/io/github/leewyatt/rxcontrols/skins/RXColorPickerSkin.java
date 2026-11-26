package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXColorFormat;
import io.github.leewyatt.rxcontrols.RXColorPicker;
import io.github.leewyatt.rxcontrols.RXColorView;
import io.github.leewyatt.rxcontrols.internal.color.ColorMath;
import io.github.leewyatt.rxcontrols.internal.color.ColorSwatch;
import io.github.leewyatt.rxcontrols.internal.color.ColorViewAccess;
import io.github.leewyatt.rxcontrols.internal.popup.RXPopupSupport;
import io.github.leewyatt.rxcontrols.internal.popup.RXPopupWidthMode;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Default skin for {@link RXColorPicker}: a swatch (and optional text) as the
 * trigger, and a popup hosting an {@link RXColorView} built by this skin.
 *
 * <p>The picker and its view are kept in step by listeners, not bidirectional
 * bindings: a picker value bound by the application would make the view's
 * write-back throw. Each side writes the other only outside a sync, and a
 * value the picker did not take (bound, or changed by a listener) is written
 * back to the view. The view is locked against input while the picker's value
 * is bound; the lock is evaluated whenever input starts, since unbinding sends
 * no notification.
 *
 * <p>Keys the popup does not consume are redirected by JavaFX to the owner
 * scene's focus owner, the picker; the popup consumes the keys the picker or a
 * dialog would act on. A window-side close (a click outside, the owner going
 * away) runs inside the popup window's hide call, and reopening the same popup
 * there would be undone by the rest of that call: a show requested during it
 * is carried out once the call has returned.
 */
public class RXColorPickerSkin extends RXSkinBase<RXColorPicker> {

    // ==================== Constants ====================

    private static final double POPUP_GAP = 4.0;
    private static final double SWATCH_SIZE = 24.0;
    /** Keys a popup must not let through to the picker or a dialog. */
    private static final Set<KeyCode> POPUP_KEYS = EnumSet.of(KeyCode.ENTER, KeyCode.SPACE, KeyCode.F4,
            KeyCode.UP, KeyCode.DOWN, KeyCode.LEFT, KeyCode.RIGHT, KeyCode.PAGE_UP, KeyCode.PAGE_DOWN,
            KeyCode.HOME, KeyCode.END);

    // ==================== Nodes ====================

    private final HBox display = new HBox();
    private final ColorSwatch swatch = new ColorSwatch("color");
    private final Label text = new Label();
    private final RXColorView view = new RXColorView();
    private final RXPopupSupport popupSupport;

    // ==================== State ====================

    private boolean activated;
    private boolean disposed;
    /** Set while the picker and the view write each other. */
    private boolean syncing;
    /** Set while the popup window's hide call reports the close. */
    private boolean inPopupHide;
    private boolean reopenScheduled;
    /** Set briefly after a window-side close, so the click that caused it does not reopen. */
    private boolean suppressReopen;
    /** Set when the close should hand the focus back to the picker. */
    private boolean returnFocus;

    private final InvalidationListener skinListener = observable -> activate();

    // ==================== Constructor ====================

    /**
     * Creates the default skin for the given picker. The skin builds its nodes and
     * starts taking input once it is installed as the picker's skin.
     *
     * @param control the picker
     */
    public RXColorPickerSkin(RXColorPicker control) {
        super(control);
        display.getStyleClass().add("display");
        display.setAlignment(Pos.CENTER_LEFT);
        swatch.setPrefSize(SWATCH_SIZE, SWATCH_SIZE);
        swatch.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        swatch.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        text.getStyleClass().add("text");
        text.setMinWidth(Region.USE_PREF_SIZE);
        display.getChildren().addAll(swatch, text);

        popupSupport = new RXPopupSupport(view);
        popupSupport.setPopupStyleClass("rx-color-picker-popup");
        popupSupport.setWidthMode(RXPopupWidthMode.PREF_CONTENT);
        popupSupport.setOffset(0.0, POPUP_GAP);
        // The picker handles Escape itself: the popup's own Escape auto-hide does not
        // consume the key, which would let an enclosing dialog's cancel button fire.
        popupSupport.setHideOnEscape(false);
        disposer.registerDisposeTask(popupSupport::dispose);

        // Weak: setSkin drops a skin of the installed class without disposing it,
        // and nothing else may keep such a skin reachable from the picker.
        WeakInvalidationListener weakSkinListener = new WeakInvalidationListener(skinListener);
        control.skinProperty().addListener(weakSkinListener);
        disposer.registerDisposeTask(() -> control.skinProperty().removeListener(weakSkinListener));
    }

    // ==================== Activation ====================

    // A skin is constructed before setSkin installs it, and setSkin ignores a second
    // skin of the installed class. Taking the picker over only once installed keeps a
    // skin that never gets installed from adding nodes or answering input twice.
    private void activate() {
        RXColorPicker control = getSkinnable();
        if (activated || disposed || control == null || control.getSkin() != this) {
            return;
        }
        activated = true;
        getChildren().setAll(display);

        // Configuration flows down; value and format are synced both ways.
        disposer.registerBinding(view.alphaEnabledProperty(), control.alphaEnabledProperty());
        disposer.registerBinding(view.clearableProperty(), control.clearableProperty());
        Bindings.bindContent(view.getPresets(), control.getPresets());
        disposer.registerDisposeTask(() -> Bindings.unbindContent(view.getPresets(), control.getPresets()));
        ColorViewAccess.setInputLock(view, () -> control.valueProperty().isBound());
        disposer.registerDisposeTask(() -> ColorViewAccess.setInputLock(view, null));
        pickerToView();
        disposer.registerListener(control.valueProperty(), live(this::onPickerValueChanged));
        disposer.registerListener(view.valueProperty(), live(this::viewToPicker));
        disposer.registerListener(control.formatProperty(), live(this::onPickerValueChanged));
        disposer.registerListener(view.formatProperty(), live(this::viewToPicker));
        disposer.registerEventHandler(view, ActionEvent.ACTION, event -> {
            if (!disposed && getSkinnable() != null) {
                getSkinnable().fireEvent(new ActionEvent());
            }
        });

        disposer.registerListener(control.showTextProperty(), live(this::updateDisplay));
        disposer.registerListener(control.promptTextProperty(), live(this::updateDisplay));
        disposer.registerListener(control.showingProperty(), live(this::syncPopup));
        disposer.registerListener(control.disabledProperty(), live(() -> {
            if (control.isDisabled()) {
                control.hide();
            }
        }));

        popupSupport.setOnHidden(this::onPopupHidden);
        disposer.registerEventHandler(display, MouseEvent.MOUSE_CLICKED, this::onDisplayClicked);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onPickerKey);
        disposer.registerEventFilter(view, KeyEvent.KEY_PRESSED, this::onPopupKeyFilter);
        disposer.registerEventHandler(view, KeyEvent.KEY_PRESSED, this::onPopupKey);

        updateDisplay();
        syncPopup();
    }

    // A listener removed while its property is notifying still gets that notification,
    // so an earlier listener that replaced the skin would reach this one after dispose.
    private Runnable live(Runnable action) {
        return () -> {
            if (!disposed) {
                action.run();
            }
        };
    }

    // ==================== Sync ====================

    // Value and format both show in the trigger and in the view.
    private void onPickerValueChanged() {
        pickerToView();
        updateDisplay();
    }

    private void pickerToView() {
        if (syncing) {
            return;
        }
        RXColorPicker control = getSkinnable();
        syncing = true;
        try {
            view.setValue(control.getValue());
            view.setFormat(control.getFormat());
        } finally {
            syncing = false;
        }
    }

    private void viewToPicker() {
        if (syncing) {
            return;
        }
        RXColorPicker control = getSkinnable();
        syncing = true;
        try {
            control.setValue(view.getValue());
            if (!control.formatProperty().isBound()) {
                control.setFormat(view.getFormat());
            }
        } finally {
            syncing = false;
        }
        // A listener may have replaced this skin; a bound or rewritten picker value
        // wins over the view's.
        if (!disposed && (!Objects.equals(control.getValue(), view.getValue())
                || control.getFormat() != view.getFormat())) {
            pickerToView();
        }
        updateDisplay();
    }

    // ==================== Display ====================

    private void updateDisplay() {
        RXColorPicker control = getSkinnable();
        Color value = control.getValue();
        swatch.showColor(value);
        boolean showText = control.isShowText();
        text.setVisible(showText);
        text.setManaged(showText);
        if (showText) {
            RXColorFormat format = control.getFormat();
            text.setText(value == null ? control.getPromptText() : ColorMath.toText(value, format));
        }
    }

    // ==================== Popup ====================

    private void syncPopup() {
        RXColorPicker control = getSkinnable();
        if (control.isShowing()) {
            if (inPopupHide) {
                // Reopening inside the window's hide call would be undone by its rest.
                scheduleReopen();
                return;
            }
            if (popupSupport.isShowing()) {
                return;
            }
            popupSupport.show(control);
            if (popupSupport.isShowing()) {
                Platform.runLater(() -> {
                    if (!disposed && popupSupport.isShowing()) {
                        view.requestFocus();
                    }
                });
            }
        } else {
            popupSupport.hide();
            // Keyboard closes hand the focus back; a click elsewhere keeps its own target.
            if (!disposed && returnFocus) {
                control.requestFocus();
            }
            returnFocus = false;
        }
    }

    private void scheduleReopen() {
        if (reopenScheduled) {
            return;
        }
        reopenScheduled = true;
        Platform.runLater(() -> {
            reopenScheduled = false;
            if (!disposed) {
                syncPopup();
            }
        });
    }

    /** Every close path ends here, through the popup window's hidden event. */
    private void onPopupHidden() {
        if (disposed) {
            return;
        }
        inPopupHide = true;
        try {
            getSkinnable().hide();
        } finally {
            inPopupHide = false;
        }
        suppressReopen = true;
        Platform.runLater(() -> suppressReopen = false);
    }

    private void closeFromKeyboard() {
        returnFocus = true;
        // Abandoned text in a field must not be applied by the focus loss the close causes.
        ColorViewAccess.cancelEdits(view);
        getSkinnable().hide();
    }

    // ==================== Input ====================

    private void onDisplayClicked(MouseEvent event) {
        RXColorPicker control = getSkinnable();
        if (event.getButton() != MouseButton.PRIMARY || control.isDisabled()) {
            return;
        }
        event.consume();
        control.requestFocus();
        if (control.isShowing()) {
            control.hide();
        } else if (!suppressReopen) {
            control.show();
        }
    }

    private void onPickerKey(KeyEvent event) {
        RXColorPicker control = getSkinnable();
        if (control.isDisabled()) {
            return;
        }
        KeyCode code = event.getCode();
        if (!control.isShowing()) {
            // Enter is left to bubble, so an enclosing form's default button still sees it.
            if (code == KeyCode.SPACE || code == KeyCode.F4 || code == KeyCode.DOWN
                    || (code == KeyCode.UP && event.isAltDown())) {
                event.consume();
                control.show();
            }
            return;
        }
        // The popup is open but its scene has no focus yet: the keys come here.
        if (code == KeyCode.ESCAPE || code == KeyCode.F4 || code == KeyCode.SPACE
                || (code == KeyCode.UP && event.isAltDown())) {
            event.consume();
            closeFromKeyboard();
        }
    }

    // A filter, so it runs before a text field's own behavior consumes Escape.
    private void onPopupKeyFilter(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code == KeyCode.ESCAPE || code == KeyCode.F4 || (code == KeyCode.UP && event.isAltDown())) {
            event.consume();
            closeFromKeyboard();
        }
    }

    // A handler: whatever the parts left unconsumed would otherwise be redirected to
    // the picker (Space would close the popup) or reach a dialog's default button.
    private void onPopupKey(KeyEvent event) {
        if (POPUP_KEYS.contains(event.getCode())) {
            event.consume();
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        display.resizeRelocate(x, y, w, h);
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset,
                                      double leftInset) {
        return leftInset + display.prefWidth(-1) + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset,
                                       double leftInset) {
        return topInset + display.prefHeight(-1) + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset, double bottomInset,
                                     double leftInset) {
        return computePrefWidth(height, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset,
                                      double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    // ==================== Dispose ====================

    @Override
    protected void disposeSkin() {
        disposed = true;
    }
}
