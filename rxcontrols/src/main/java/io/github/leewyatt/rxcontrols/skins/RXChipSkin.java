package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXChip;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.Locale;

/**
 * Default skin for {@link RXChip}.
 *
 * <p>Renders a pill holding an inner {@code .label} (the chip's text and leading
 * graphic, ellipsis-truncated), an optional leading {@code .check} wrapper around a
 * shape-backed {@code .check-icon} (shown only while a
 * {@link RXChip#selectableProperty() selectable} chip is
 * {@link RXChip#selectedProperty() selected}) and, when the chip is
 * {@link RXChip#removableProperty() removable}, a trailing {@code .close-button}
 * wrapper around a shape-backed {@code .close-icon}. A bounded
 * {@link RippleDecoration} sits below the content, clipped to the pill geometry;
 * a press anywhere on the pill except the close button ripples.</p>
 *
 * <p>Mouse and keyboard interaction is installed directly (not via the internal
 * {@code com.sun} {@code ButtonBehavior}), matching {@code ButtonBase}
 * semantics: a valid primary press arms, release fires; SPACE (and ENTER off
 * macOS) arms on press and fires on release; DELETE / BACKSPACE on a focused
 * removable chip requests removal. A {@link RXChip#selectableProperty() selectable}
 * chip toggles its selected state through {@link RXChip#fire()}. The close button consumes its
 * own press so it never arms or ripples the pill.</p>
 */
public class RXChipSkin extends RXSkinBase<RXChip> {

    // ==================== Constants ====================

    /** ENTER activates the chip everywhere except macOS (matching ButtonBehavior). */
    private static final boolean MAC =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

    // ==================== Fields ====================

    private final Label label = new Label();
    private final StackPane checkGraphic = new StackPane();
    private final Region checkIcon = new Region();
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

        // Label carries the built-in .label style class; it renders text + leading
        // graphic and is mouse-transparent so a click on the text arms the chip.
        label.setMouseTransparent(true);
        disposer.registerBinding(label.textProperty(), control.textProperty());
        disposer.registerBinding(label.graphicProperty(), control.graphicProperty());
        disposer.registerBinding(label.contentDisplayProperty(), control.contentDisplayProperty());
        disposer.registerBinding(label.graphicTextGapProperty(), control.graphicTextGapProperty());
        disposer.registerBinding(label.textOverrunProperty(), control.textOverrunProperty());
        disposer.registerBinding(label.ellipsisStringProperty(), control.ellipsisStringProperty());
        disposer.registerBinding(label.wrapTextProperty(), control.wrapTextProperty());
        disposer.registerBinding(label.alignmentProperty(), control.alignmentProperty());

        // Leading check: a shape-backed icon shown only while a selectable chip is
        // selected. Purely decorative (the selected state is conveyed to a11y through
        // the SELECTED attribute + TOGGLE_BUTTON role), so it is mouse-transparent.
        checkIcon.getStyleClass().add("check-icon");
        checkIcon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        checkIcon.setMouseTransparent(true);
        checkGraphic.getStyleClass().add("check");
        checkGraphic.setMouseTransparent(true);
        checkGraphic.getChildren().setAll(checkIcon);

        closeIcon.getStyleClass().add("close-icon");
        closeIcon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        closeIcon.setMouseTransparent(true);
        closeButton.getStyleClass().add("close-button");
        closeButton.getChildren().setAll(closeIcon);

        ripple = new RippleDecoration(control, control.rippleEnabledProperty(),
                control.stateOverlayEnabledProperty(), control.rippleFillProperty(),
                control::getRippleOpacity, null, null);

        getChildren().setAll(ripple.getLayer(), label);
        updateCheckGraphic();
        updateCloseButton();
        updateCloseAccessibleText();

        disposer.registerListener(control.selectableProperty(), this::updateCheckGraphic);
        disposer.registerListener(control.selectedProperty(), this::updateCheckGraphic);
        disposer.registerListener(control.removableProperty(), this::updateCloseButton);
        disposer.registerListener(control.textProperty(), this::updateCloseAccessibleText);
        disposer.registerListener(control.maxLabelWidthProperty(), control::requestLayout);

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

    private void updateCheckGraphic() {
        boolean shouldShow = isCheckShown();
        boolean shown = getChildren().contains(checkGraphic);
        if (shouldShow && !shown) {
            getChildren().add(checkGraphic);
        } else if (!shouldShow && shown) {
            getChildren().remove(checkGraphic);
        }
    }

    private boolean isCheckShown() {
        return getSkinnable().isSelectable() && getSkinnable().isSelected();
    }

    private void updateCloseButton() {
        boolean shouldShow = getSkinnable().isRemovable();
        boolean shown = getChildren().contains(closeButton);
        if (shouldShow && !shown) {
            getChildren().add(closeButton);
        } else if (!shouldShow && shown) {
            getChildren().remove(closeButton);
        }
    }

    private void updateCloseAccessibleText() {
        String text = getSkinnable().getText();
        closeButton.setAccessibleText("Remove " + (text == null ? "" : text));
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        boolean checked = getChildren().contains(checkGraphic);
        boolean removable = getChildren().contains(closeButton);
        double checkW = checked ? snapSizeX(checkGraphic.prefWidth(-1)) : 0.0;
        double checkH = checked ? snapSizeY(checkGraphic.prefHeight(-1)) : 0.0;
        double closeW = removable ? snapSizeX(closeButton.prefWidth(-1)) : 0.0;
        double closeH = removable ? snapSizeY(closeButton.prefHeight(-1)) : 0.0;

        if (checked) {
            double checkY = contentY + (contentHeight - checkH) / 2.0;
            checkGraphic.resizeRelocate(snapPositionX(contentX), snapPositionY(checkY), checkW, checkH);
        }

        double labelAvailable = Math.max(0.0, contentWidth - checkW - closeW);
        double labelWidth = Math.min(labelAvailable, cappedLabelWidth());
        label.resizeRelocate(snapPositionX(contentX + checkW), snapPositionY(contentY),
                labelWidth, contentHeight);

        if (removable) {
            double closeX = contentX + contentWidth - closeW;
            double closeY = contentY + (contentHeight - closeH) / 2.0;
            closeButton.resizeRelocate(snapPositionX(closeX), snapPositionY(closeY), closeW, closeH);
        }

        ripple.layout(getSkinnable().getWidth(), getSkinnable().getHeight());
    }

    private double cappedLabelWidth() {
        double pref = label.prefWidth(-1);
        double cap = getSkinnable().getMaxLabelWidth();
        return cap >= 0.0 ? Math.min(pref, cap) : pref;
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + checkWidth() + snapSizeX(label.minWidth(-1)) + closeWidth() + rightInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + checkWidth() + snapSizeX(cappedLabelWidth()) + closeWidth() + rightInset;
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
        double labelH = snapSizeY(label.prefHeight(-1));
        double closeH = getSkinnable().isRemovable() ? snapSizeY(closeButton.prefHeight(-1)) : 0.0;
        double checkH = isCheckShown() ? snapSizeY(checkGraphic.prefHeight(-1)) : 0.0;
        return topInset + Math.max(Math.max(labelH, closeH), checkH) + bottomInset;
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    private double checkWidth() {
        return isCheckShown() ? snapSizeX(checkGraphic.prefWidth(-1)) : 0.0;
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
        if (validPrimaryPress(event) && !control.isArmed()) {
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

    private static boolean validPrimaryPress(MouseEvent event) {
        return event.getButton() == MouseButton.PRIMARY
                && !(event.isMiddleButtonDown() || event.isSecondaryButtonDown()
                || event.isShiftDown() || event.isControlDown()
                || event.isAltDown() || event.isMetaDown());
    }

    // ==================== Dispose ====================

    /**
     * Stops the press ripple, removes the ripple layer and unregisters all
     * ripple / interaction listeners before the standard {@link RXSkinBase}
     * cleanup runs.
     */
    @Override
    protected void disposeSkin() {
        ripple.dispose();
        getChildren().remove(ripple.getLayer());
    }
}
