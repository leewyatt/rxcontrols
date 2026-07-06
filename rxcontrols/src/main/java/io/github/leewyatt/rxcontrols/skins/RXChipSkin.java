package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXChip;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import io.github.leewyatt.rxcontrols.utils.RXMouse;
import io.github.leewyatt.rxcontrols.utils.RXOS;
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
 * beside the label. A bounded {@link RippleDecoration} sits below the content,
 * clipped to the pill geometry; a press anywhere on the pill except the close
 * button ripples.</p>
 *
 * <p>Mouse and keyboard interaction is installed directly (not via the internal
 * {@code com.sun} {@code ButtonBehavior}), matching {@code ButtonBase}
 * semantics: a valid primary press arms, release fires; SPACE (and ENTER off
 * macOS) arms on press and fires on release; DELETE / BACKSPACE on a focused
 * removable chip requests removal. A {@link RXChip#selectableProperty() selectable}
 * chip toggles its selected state through {@link RXChip#fire()}. The close button consumes its
 * own press so it never arms or ripples the pill.</p>
 */
public class RXChipSkin extends LabeledSkinBase<RXChip> {

    // ==================== Constants ====================

    /** ENTER activates the chip everywhere except macOS (matching ButtonBehavior). */
    private static final boolean MAC = RXOS.isMacOS();

    // ==================== Fields ====================

    private final SkinDisposer disposer = new SkinDisposer();

    private final StackPane closeButton = new StackPane();
    private final Region closeIcon = new Region();
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

        ripple = new RippleDecoration(control, control.rippleEnabledProperty(),
                control.stateOverlayEnabledProperty(), control.rippleFillProperty(),
                control::getRippleOpacity, null, null);

        // Now that ripple + close button exist, rebuild the child list (the first
        // updateChildren from the LabeledSkinBase constructor ran before they did).
        updateChildren();
        updateCloseAccessibleText();

        // Toggling removable adds / removes the close button through updateChildren.
        disposer.registerListener(control.removableProperty(), this::updateChildren);
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
        if (closeButton != null && getSkinnable().isRemovable()) {
            getChildren().add(closeButton);
        }
    }

    private void updateCloseAccessibleText() {
        String text = getSkinnable().getText();
        closeButton.setAccessibleText("Remove " + (text == null ? "" : text));
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        RXChip control = getSkinnable();
        boolean removable = control.isRemovable();
        double closeW = removable ? snapSizeX(closeButton.prefWidth(-1)) : 0.0;
        double closeH = removable ? snapSizeY(closeButton.prefHeight(-1)) : 0.0;

        // The label (base-managed text + graphic) fills the area minus the trailing
        // close button; layoutLabelInArea truncates the text within that width.
        double labelW = Math.max(0.0, w - closeW);
        layoutLabelInArea(x, y, labelW, h, control.getAlignment());

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
                + closeWidth();
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset)
                + closeWidth();
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
        double labelWidth = width < 0.0 ? width : Math.max(0.0, width - closeWidth());
        double closeH = getSkinnable().isRemovable() ? snapSizeY(closeButton.prefHeight(-1)) : 0.0;
        return Math.max(
                super.computePrefHeight(labelWidth, topInset, rightInset, bottomInset, leftInset),
                topInset + closeH + bottomInset);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
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
    }

    private void onMousePressed(MouseEvent event) {
        RXChip control = getSkinnable();
        if (control.isFocusTraversable() && !control.isFocused()) {
            control.requestFocus();
        }
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
        if (!control.isFocused() && keyDown) {
            // Losing focus while a key is held would otherwise strand the chip armed
            // (the KEY_RELEASED goes to the new focus owner); matches ButtonBehavior.
            keyDown = false;
            control.disarm();
            ripple.release();
        }
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
