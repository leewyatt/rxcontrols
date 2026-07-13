package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXMenuButton;
import io.github.leewyatt.rxcontrols.RXMenuItem;
import io.github.leewyatt.rxcontrols.RXPopupMenu;
import io.github.leewyatt.rxcontrols.internal.MenuAcceleratorSupport;
import io.github.leewyatt.rxcontrols.internal.ripple.ArmedRippleTrigger;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import io.github.leewyatt.rxcontrols.utils.RXMouse;
import io.github.leewyatt.rxcontrols.utils.RXOS;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.skin.LabeledSkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;

/**
 * Skin for {@link RXMenuButton}: the {@link LabeledSkinBase} text / graphic layout
 * plus an armed press ripple ({@link ArmedRippleTrigger}), a trailing drop-down
 * arrow, and a privately held {@link RXPopupMenu} the skin drives from the
 * control's {@code showing} flag.
 *
 * <p>{@code LabeledSkinBase} carries no button behavior, so this skin installs the
 * standard {@code ButtonBase} semantics itself: a valid primary press arms (the
 * ripple follows {@code armed}); a click, or {@code SPACE} / {@code ENTER},
 * toggles the menu; {@code DOWN} opens it. The popup's own close paths (Escape, an
 * item activation, an outside click) pull {@code showing} back through the popup's
 * showing property, with a short {@code suppressReopen} guard so the closing click
 * does not immediately re-open.
 */
public class RXMenuButtonSkin extends LabeledSkinBase<RXMenuButton> {

    private static final boolean MAC = RXOS.isMacOS();
    // Horizontal gap reserved between the label area and the trailing arrow.
    private static final double ARROW_LABEL_GAP = 8.0;

    private final SkinDisposer disposer = new SkinDisposer();
    private final RippleDecoration ripple;
    private final ArmedRippleTrigger rippleTrigger;
    private final Region arrow = new Region();
    private final RXPopupMenu popupMenu = new RXPopupMenu();
    private final MenuAcceleratorSupport accelerators;

    private boolean keyDown;
    private boolean suppressReopen;

    /**
     * Creates the skin and wires the ripple, arrow, popup, and button behavior.
     *
     * @param control the menu button this skin is attached to
     */
    public RXMenuButtonSkin(RXMenuButton control) {
        super(control);

        ripple = new RippleDecoration(control, control.rippleEnabledProperty(),
                control.stateOverlayEnabledProperty(), control.rippleFillProperty(),
                control::getRippleOpacity, null, null);
        rippleTrigger = new ArmedRippleTrigger(control, ripple,
                control::isRippleEnabled, () -> false);
        rippleTrigger.installPointerTracking(disposer);
        disposer.registerListener(control.armedProperty(), rippleTrigger::handleArmedChanged);
        rippleTrigger.installPlayRipple(disposer);

        arrow.getStyleClass().add("arrow");
        arrow.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        arrow.setMouseTransparent(true);

        syncItems();
        disposer.registerListener(control.getItems(), this::syncItems);
        disposer.registerListener(control.showingProperty(), this::syncPopupShowing);
        disposer.registerListener(popupMenu.showingProperty(), this::syncControlShowing);
        // "disabled owner -> no menu" while open is handled by RXPopupMenu itself
        // (it watches the invoker's disabled state), so no button-level watch here.

        // Register item accelerators as real scene shortcuts while the button is in
        // a scene (works whether or not the menu is open).
        accelerators = new MenuAcceleratorSupport(control, control.getItems(), this::onAccelerator);

        installBehavior(control);
        // Reflect any pre-existing showing state (a skin attached to an already-open
        // control, e.g. a skin swap mid-show). Harmless when closed (guarded no-op).
        syncPopupShowing();
        updateChildren();
    }

    // ==================== Popup sync ====================

    private void syncItems() {
        popupMenu.getMenuList().getItems().setAll(getSkinnable().getItems());
    }

    private void syncPopupShowing() {
        RXMenuButton control = getSkinnable();
        if (control.isShowing()) {
            Point2D anchor = control.getContextAnchor();
            if (anchor != null) {
                popupMenu.showAt(control, anchor.getX(), anchor.getY());
            } else {
                popupMenu.show(control, control.getPlacement());
            }
            // The popup can validly refuse to open (no focusable item, or the button
            // is not in a realized window). Reconcile so showing never pins true with
            // no popup behind it. Loop-safe: control.hide() re-enters here and calls
            // popupMenu.hide(), a no-op since the popup is not showing.
            if (!popupMenu.isShowing()) {
                control.hide();
            }
        } else {
            popupMenu.hide();
        }
    }

    private void syncControlShowing() {
        RXMenuButton control = getSkinnable();
        // The popup closed on its own (Escape, an item, an outside click): pull the
        // control's truth back and briefly guard against the same click re-opening.
        if (!popupMenu.isShowing() && control.isShowing()) {
            control.hide();
            suppressReopen = true;
            Platform.runLater(() -> suppressReopen = false);
        }
    }

    private void toggle() {
        RXMenuButton control = getSkinnable();
        if (control.isShowing()) {
            control.hide();
        } else if (!suppressReopen && !control.isDisabled()) {
            control.show();
        }
    }

    private void onAccelerator(RXMenuItem item) {
        // A disabled button, or a disabled / non-focusable item (separator, header),
        // is inert: its command must not fire via the still-registered scene
        // accelerator, mirroring the click / keyboard path (RXMenuList.activate()).
        if (item.isDisable() || !item.isFocusable() || getSkinnable().isDisabled()) {
            return;
        }
        // Toggle a selectable item, but re-activating the already-selected radio must
        // not clear its group — same guard as RXMenuList.activate() / ToggleButton.fire().
        if (item.isSelectable()) {
            ToggleGroup group = item.getToggleGroup();
            boolean selectedRadio = group != null && item.isSelected() && group.getSelectedToggle() != null;
            if (!selectedRadio) {
                item.setSelected(!item.isSelected());
            }
        }
        // If the menu happens to be open, close it precisely as ACCELERATOR
        // (unless the item keeps it open); then fire the item's action.
        if (popupMenu.isShowing() && !item.isKeepOpen()) {
            popupMenu.hide(RXPopupMenu.CloseReason.ACCELERATOR);
        }
        item.fire();
    }

    // ==================== Button behavior ====================

    private void installBehavior(RXMenuButton control) {
        disposer.registerEventHandler(control, MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_EXITED, this::onMouseExited);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_ENTERED, this::onMouseEntered);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_CLICKED, this::onMouseClicked);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
        disposer.registerEventHandler(control, KeyEvent.KEY_RELEASED, this::onKeyReleased);
        disposer.registerListener(control.focusedProperty(), this::onFocusChanged);
    }

    private void onMousePressed(MouseEvent event) {
        RXMenuButton control = getSkinnable();
        if (control.isFocusTraversable() && !control.isFocused()) {
            control.requestFocus();
        }
        if (RXMouse.isPlainPrimaryPress(event) && !control.isArmed()) {
            control.arm();
        }
    }

    private void onMouseReleased(MouseEvent event) {
        RXMenuButton control = getSkinnable();
        if (control.isArmed() && !keyDown) {
            control.disarm();
        }
    }

    private void onMouseExited(MouseEvent event) {
        RXMenuButton control = getSkinnable();
        if (control.isArmed() && !keyDown) {
            control.disarm();
        }
    }

    private void onMouseEntered(MouseEvent event) {
        RXMenuButton control = getSkinnable();
        // Pressed, dragged out and back in: re-arm so the ripple resumes.
        if (control.isPressed() && !keyDown) {
            control.arm();
        }
    }

    private void onMouseClicked(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            toggle();
            event.consume();
        }
    }

    private void onKeyPressed(KeyEvent event) {
        RXMenuButton control = getSkinnable();
        KeyCode code = event.getCode();
        if (isActivationKey(code)) {
            if (!control.isPressed() && !control.isArmed()) {
                keyDown = true;
                control.arm();
            }
            event.consume();
        } else if (code == KeyCode.DOWN || code == KeyCode.KP_DOWN) {
            // Standard menu-button affordance: Down opens the menu.
            control.show();
            event.consume();
        }
    }

    private void onKeyReleased(KeyEvent event) {
        RXMenuButton control = getSkinnable();
        if (isActivationKey(event.getCode()) && keyDown) {
            // Keyboard path: disarm then fire (toggle), the opposite order from the mouse.
            keyDown = false;
            control.disarm();
            control.fire();
            event.consume();
        }
    }

    private void onFocusChanged() {
        RXMenuButton control = getSkinnable();
        // A key held while focus leaves would otherwise strand the button armed
        // (the KEY_RELEASED goes to the new focus owner); matches ButtonBehavior.
        if (!control.isFocused() && keyDown) {
            keyDown = false;
            control.disarm();
        }
    }

    private static boolean isActivationKey(KeyCode code) {
        return code == KeyCode.SPACE || (!MAC && code == KeyCode.ENTER);
    }

    // ==================== Children / layout ====================

    @Override
    protected void updateChildren() {
        super.updateChildren();
        // The first call comes from the LabeledSkinBase constructor, before this
        // skin's fields are initialized.
        if (ripple != null) {
            getChildren().add(0, ripple.getLayer());
        }
        if (arrow != null) {
            getChildren().add(arrow);
        }
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        double arrowW = snapSizeX(arrow.prefWidth(-1));
        double arrowH = snapSizeY(arrow.prefHeight(-1));
        double gap = snapSizeX(ARROW_LABEL_GAP);
        // The label fills the area minus the trailing arrow and its gap;
        // layoutLabelInArea truncates within labelW. The arrow region is symmetric
        // (no CSS padding), so a :showing flip rotates cleanly about its center.
        double labelW = Math.max(0.0, w - arrowW - gap);
        layoutLabelInArea(x, y, labelW, h, getSkinnable().getAlignment());
        double arrowX = x + w - arrowW;
        double arrowY = y + (h - arrowH) / 2.0;
        arrow.resizeRelocate(snapPositionX(arrowX), snapPositionY(arrowY), arrowW, arrowH);
        ripple.layout(getSkinnable().getWidth(), getSkinnable().getHeight());
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset)
                + snapSizeX(ARROW_LABEL_GAP) + snapSizeX(arrow.prefWidth(-1));
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return super.computeMinWidth(height, topInset, rightInset, bottomInset, leftInset)
                + snapSizeX(ARROW_LABEL_GAP) + snapSizeX(arrow.prefWidth(-1));
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        // Feed super the width the label actually gets (minus arrow + gap) so a
        // wrapText label wraps to its true line count; keep the arrow from being
        // clipped when it is the taller of the two.
        return Math.max(
                super.computePrefHeight(labelWidth(width), topInset, rightInset, bottomInset, leftInset),
                topInset + snapSizeY(arrow.prefHeight(-1)) + bottomInset);
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return Math.max(
                super.computeMinHeight(labelWidth(width), topInset, rightInset, bottomInset, leftInset),
                topInset + snapSizeY(arrow.prefHeight(-1)) + bottomInset);
    }

    // The label's laid-out width (minus the trailing arrow and gap), preserving the
    // -1 unconstrained sentinel so single-line height stays width-independent.
    private double labelWidth(double width) {
        if (width < 0.0) {
            return width;
        }
        return Math.max(0.0, width - snapSizeX(ARROW_LABEL_GAP) - snapSizeX(arrow.prefWidth(-1)));
    }

    // ==================== Dispose ====================

    /**
     * Disposes the popup, stops the ripple, and unregisters all listeners before
     * the standard {@link LabeledSkinBase} cleanup.
     */
    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(this::disposeParts, disposer::dispose, super::dispose);
    }

    private void disposeParts() {
        accelerators.dispose();
        popupMenu.dispose();
        ripple.dispose();
        getChildren().remove(ripple.getLayer());
        getChildren().remove(arrow);
    }
}
