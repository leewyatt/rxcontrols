package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXChip;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import io.github.leewyatt.rxcontrols.utils.RXMouse;
import io.github.leewyatt.rxcontrols.utils.RXOS;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.skin.LabeledSkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Default skin for {@link RXChip}.
 *
 * <p>Renders a pill whose text and leading graphic are laid out by
 * {@link LabeledSkinBase} (the built-in {@code .text} node, ellipsis-truncated)
 * and, when the chip is {@link RXChip#removableProperty() removable}, a trailing
 * {@code .close-button} wrapper around a shape-backed {@code .close-icon} placed
 * beside the label. A {@link RXChip#selectableProperty() selectable} chip reserves a
 * leading slot for a shape-backed {@code .check} mark that shows while it is selected,
 * ahead of any graphic, which stays in place. A bounded {@link RippleDecoration}
 * sits below the content, clipped to the pill geometry; a press anywhere on the
 * pill except the close button ripples.</p>
 *
 * <p>Mouse and keyboard interaction is installed directly (not via the internal
 * {@code com.sun} {@code ButtonBehavior}), matching {@code ButtonBase}
 * semantics: a valid primary press arms, release fires; SPACE (and ENTER off
 * macOS) arms on press and fires on release; DELETE / BACKSPACE on a focused
 * removable chip requests removal. A {@link RXChip#selectableProperty() selectable}
 * chip toggles its selected state through {@link RXChip#fire()}. The close button consumes its
 * own press so it never arms or ripples the pill.</p>
 *
 * <p>The focus indicator follows the {@code :focus-shown} pseudo-class rather than
 * {@code :focused}: it is held back while the focus came from a pointer press, since
 * a user who just clicked the chip knows where focus is, and comes back on the next
 * non-modifier key press or the next time focus arrives without one.</p>
 */
public class RXChipSkin extends LabeledSkinBase<RXChip> {

    // ==================== Constants ====================

    /** ENTER activates the chip everywhere except macOS (matching ButtonBehavior). */
    private static final boolean MAC = RXOS.isMacOS();

    // Not :focus-visible: from JavaFX 19 the platform writes that pseudo-class on
    // every node, and two writers would overwrite each other.
    private static final PseudoClass FOCUS_SHOWN = PseudoClass.getPseudoClass("focus-shown");

    // ==================== Fields ====================

    private final SkinDisposer disposer = new SkinDisposer();

    private final StackPane closeButton = new StackPane();
    private final Region closeIcon = new Region();
    private final Region check = new Region();
    private final RippleDecoration ripple;

    /** True while armed via the keyboard; gates the mouse path and arms focus-loss disarm. */
    private boolean keyDown;

    /**
     * True while a primary press that landed on the pill (not the close button) is
     * still in progress. Scopes the drag-back-in re-arm to a pill press: the chip's
     * {@code isPressed()} is set for a close-button press too (JavaFX marks the whole
     * ancestor chain pressed before the close button's consume runs), so re-arming
     * on {@code isPressed()} alone would fire the primary action from a close gesture.
     */
    private boolean pressedOnChip;

    /** True when the focus the chip currently holds arrived via a pointer press. */
    private boolean mouseFocus;

    // ==================== Constructor ====================

    /**
     * Creates a skin for the given chip.
     *
     * @param control the chip this skin is attached to
     */
    public RXChipSkin(RXChip control) {
        super(control);

        closeIcon.getStyleClass().add("close-icon");
        closeIcon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        closeIcon.setMouseTransparent(true);
        closeButton.getStyleClass().add("close-button");
        closeButton.getChildren().setAll(closeIcon);
        check.getStyleClass().add("check");
        check.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        check.setMouseTransparent(true);
        updateCheckVisibility();

        ripple = new RippleDecoration(control, control.rippleEnabledProperty(),
                control.stateOverlayEnabledProperty(), control.rippleFillProperty(),
                control::getRippleOpacity, null, null);

        // Now that ripple + close button exist, rebuild the child list (the first
        // updateChildren from the LabeledSkinBase constructor ran before they did).
        updateChildren();
        updateCloseAccessibleText();

        // Toggling removable adds / removes the close button through updateChildren.
        disposer.registerListener(control.removableProperty(), this::updateChildren);
        // The check slot exists only on a selectable chip; selection shows the mark
        // and shifts the label, so it needs a new layout but never a new size.
        disposer.registerListener(control.selectableProperty(), this::updateChildren);
        disposer.registerListener(control.selectedProperty(), () -> {
            updateCheckVisibility();
            control.requestLayout();
        });
        disposer.registerListener(control.textProperty(), this::updateCloseAccessibleText);

        // The close button consumes its own press so it never arms / ripples the
        // pill, and removes the chip on a click.
        disposer.registerEventHandler(closeButton, MouseEvent.MOUSE_PRESSED, MouseEvent::consume);
        disposer.registerEventHandler(closeButton, MouseEvent.MOUSE_CLICKED, event -> {
            control.remove();
            event.consume();
        });

        installInteraction();
    }

    // ==================== Children ====================

    @Override
    protected void updateChildren() {
        // Rebuilds the label (text + graphic) via setAll, so the ripple layer and
        // close button must be re-added after every call.
        super.updateChildren();
        if (ripple != null) {
            getChildren().add(0, ripple.getLayer());
        }
        if (check != null && getSkinnable().isSelectable()) {
            getChildren().add(check);
            updateCheckVisibility();
        }
        if (closeButton != null && getSkinnable().isRemovable()) {
            getChildren().add(closeButton);
        }
    }

    private void updateCloseAccessibleText() {
        String text = getSkinnable().getText();
        closeButton.setAccessibleText("Remove " + (text == null ? "" : text));
    }

    private void updateCheckVisibility() {
        RXChip control = getSkinnable();
        check.setVisible(control.isSelectable() && control.isSelected());
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        RXChip control = getSkinnable();
        boolean removable = control.isRemovable();
        double closeW = removable ? snapSizeX(closeButton.prefWidth(-1)) : 0.0;
        double closeH = removable ? snapSizeY(closeButton.prefHeight(-1)) : 0.0;

        // The label (base-managed text + graphic) fills the area minus the check slot
        // and the trailing close button; layoutLabelInArea truncates the text within
        // that width. The slot is reserved whether or not the chip is selected, so a
        // toggle never resizes it or reflows its container: selected, the check fills
        // the slot; unselected, the label takes half of it on each side and stays
        // centred in the pill.
        double slotW = checkSlotWidth();
        double labelW = Math.max(0.0, w - slotW - closeW);
        double labelX = control.isSelected() ? x + slotW : x + slotW / 2.0;
        layoutLabelInArea(labelX, y, labelW, h, control.getAlignment());

        if (slotW > 0.0) {
            double checkW = snapSizeX(check.prefWidth(-1));
            double checkH = snapSizeY(check.prefHeight(-1));
            check.resizeRelocate(snapPositionX(x), snapPositionY(y + (h - checkH) / 2.0), checkW, checkH);
        }

        if (removable) {
            double closeX = x + w - closeW;
            double closeY = y + (h - closeH) / 2.0;
            closeButton.resizeRelocate(snapPositionX(closeX), snapPositionY(closeY), closeW, closeH);
        }

        ripple.layout(control.getWidth(), control.getHeight());
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return super.computeMinWidth(height, topInset, rightInset, bottomInset, leftInset)
                + checkSlotWidth() + closeWidth();
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset)
                + checkSlotWidth() + closeWidth();
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        // A pill hugs its content; it must not be stretched by a container.
        return computePrefWidth(height, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double labelWidth = width < 0.0 ? width : Math.max(0.0, width - checkSlotWidth() - closeWidth());
        double closeH = getSkinnable().isRemovable() ? snapSizeY(closeButton.prefHeight(-1)) : 0.0;
        double checkH = getSkinnable().isSelectable() ? snapSizeY(check.prefHeight(-1)) : 0.0;
        return Math.max(
                super.computePrefHeight(labelWidth, topInset, rightInset, bottomInset, leftInset),
                topInset + Math.max(closeH, checkH) + bottomInset);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    /** The check mark plus the graphic-text gap before the label; zero unless selectable. */
    private double checkSlotWidth() {
        RXChip control = getSkinnable();
        if (!control.isSelectable()) {
            return 0.0;
        }
        return snapSizeX(check.prefWidth(-1)) + snapSpaceX(control.getGraphicTextGap());
    }

    private double closeWidth() {
        return getSkinnable().isRemovable() ? snapSizeX(closeButton.prefWidth(-1)) : 0.0;
    }

    // ==================== Interaction ====================

    private void installInteraction() {
        RXChip control = getSkinnable();
        disposer.registerEventHandler(control, MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_EXITED, this::onMouseExited);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_ENTERED, this::onMouseEntered);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
        disposer.registerEventHandler(control, KeyEvent.KEY_RELEASED, this::onKeyReleased);
        disposer.registerListener(control.focusedProperty(), this::handleFocusChanged);
        // A replacing skin is built while the old one is still installed; take over its
        // pointer-focus state, which also holds while the window is inactive.
        if (control.getSkin() instanceof RXChipSkin oldSkin) {
            mouseFocus = oldSkin.mouseFocus;
        }
        disposer.registerDisposeTask(() -> {
            // A replacing RXChipSkin is built (and sets the state) before this skin is
            // disposed; leave its state alone and clear only for any other successor.
            if (control.getSkin() == this || !(control.getSkin() instanceof RXChipSkin)) {
                control.pseudoClassStateChanged(FOCUS_SHOWN, false);
            }
        });
        updateFocusShown();
    }

    private void onMousePressed(MouseEvent event) {
        RXChip control = getSkinnable();
        // Set before requestFocus so the focus change already sees a pointer focus;
        // a press that does not make the chip the focus owner must not mark a later
        // focus. Ownership, not isFocused(): a click into an inactive window makes the
        // chip the owner before the window activation focuses it.
        mouseFocus = true;
        if (control.isFocusTraversable() && !control.isFocused()) {
            control.requestFocus();
        }
        Scene scene = control.getScene();
        mouseFocus = scene != null && scene.getFocusOwner() == control;
        updateFocusShown();
        if (RXMouse.isPlainPrimaryPress(event) && !control.isArmed()) {
            // Reached only for a pill press: the close button consumes its own press,
            // so this handler never runs for a close gesture.
            pressedOnChip = true;
            control.arm();
            if (control.isRippleEnabled() && !control.isDisabled()) {
                ripple.press(event.getX(), event.getY(), false);
            }
        }
    }

    private void onMouseReleased(MouseEvent event) {
        RXChip control = getSkinnable();
        // Gated on !keyDown so a stray mouse release does not fire a keyboard-armed chip.
        if (control.isArmed() && !keyDown) {
            control.fire();
            control.disarm();
        }
        // The keyboard path owns its ripple release; a stray mouse release during a
        // key hold must not cut the keyboard ripple short.
        if (!keyDown) {
            pressedOnChip = false;
            ripple.release();
        }
    }

    private void onMouseExited(MouseEvent event) {
        RXChip control = getSkinnable();
        if (control.isArmed() && !keyDown) {
            control.disarm();
        }
        if (!keyDown) {
            ripple.release();
        }
    }

    private void onMouseEntered(MouseEvent event) {
        RXChip control = getSkinnable();
        if (pressedOnChip && control.isPressed() && !keyDown) {
            // Pressed on the pill, dragged out and back in: re-arm so a release still fires.
            control.arm();
        }
    }

    private void onKeyPressed(KeyEvent event) {
        RXChip control = getSkinnable();
        // A key press means the user is on the keyboard now, so show the indicator.
        // Modifiers and shortcut chords (Cmd+C, Cmd+Tab) do not count.
        if (mouseFocus && !event.getCode().isModifierKey()
                && !event.isMetaDown() && !event.isControlDown() && !event.isAltDown()) {
            mouseFocus = false;
            updateFocusShown();
        }
        if (isActivationKey(event.getCode())) {
            if (!control.isPressed() && !control.isArmed()) {
                keyDown = true;
                control.arm();
                if (control.isRippleEnabled() && !control.isDisabled()) {
                    ripple.press(0.0, 0.0, true);
                }
            }
            // Consume every activation-key press (including OS auto-repeat while held)
            // so a held SPACE / ENTER cannot leak to a scene-level default-button
            // accelerator, matching ButtonBehavior's unconditional auto-consume.
            event.consume();
        } else if (isRemovalKey(event.getCode()) && control.isRemovable()) {
            control.remove();
            event.consume();
        }
    }

    private void onKeyReleased(KeyEvent event) {
        RXChip control = getSkinnable();
        if (isActivationKey(event.getCode()) && keyDown) {
            // Keyboard path: disarm then fire (the opposite order from the mouse path).
            keyDown = false;
            control.disarm();
            control.fire();
            ripple.release();
            event.consume();
        }
    }

    private void handleFocusChanged() {
        RXChip control = getSkinnable();
        if (!control.isFocused()) {
            // Reset pointer-focus tracking so the next focus shows the indicator again,
            // but only when focus left the chip: a window deactivation keeps the scene's
            // focus owner, and returning to the window should not reveal the indicator.
            Scene scene = control.getScene();
            if (scene == null || scene.getFocusOwner() != control) {
                mouseFocus = false;
            }
            if (keyDown) {
                // Losing focus while a key is held would otherwise strand the chip armed
                // (the KEY_RELEASED goes to the new focus owner); matches ButtonBehavior.
                keyDown = false;
                control.disarm();
                ripple.release();
            }
        }
        updateFocusShown();
    }

    private void updateFocusShown() {
        RXChip control = getSkinnable();
        control.pseudoClassStateChanged(FOCUS_SHOWN, control.isFocused() && !mouseFocus);
    }

    private static boolean isActivationKey(KeyCode code) {
        return code == KeyCode.SPACE || (!MAC && code == KeyCode.ENTER);
    }

    private static boolean isRemovalKey(KeyCode code) {
        return code == KeyCode.DELETE || code == KeyCode.BACK_SPACE;
    }

    // ==================== Dispose ====================

    /**
     * Stops the press ripple, removes the ripple layer and unregisters all ripple /
     * interaction listeners before the standard {@link LabeledSkinBase} cleanup runs.
     * Safe to call more than once.
     */
    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(this::disposeRipple, disposer::dispose, super::dispose);
    }

    private void disposeRipple() {
        ripple.dispose();
        getChildren().remove(ripple.getLayer());
    }
}
