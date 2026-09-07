package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.internal.KeyboardTraversalMonitor;
import io.github.leewyatt.rxcontrols.internal.ripple.ArmedRippleTrigger;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.skin.ButtonSkin;
import javafx.scene.input.MouseEvent;

/**
 * Skin for {@link RXButton}: the standard {@link ButtonSkin} plus a
 * {@link RippleDecoration} placed above the button background and below the
 * label. The decoration owns the ripple layer, hover overlay and shared
 * lifecycle; this skin only supplies the armed-driven trigger.
 *
 * <p>The ripple lifecycle is driven by {@code armedProperty}, which already
 * covers every start/stop path of {@code ButtonBehavior} (valid primary
 * press, SPACE/ENTER activation, drag-exit disarm, focus-loss disarm). A
 * mouse-press event filter only records the pointer location; re-arming while
 * still pressed (dragging back in) does not start a new ripple.</p>
 *
 * <p>The skin also drives a {@code key-focused} pseudo-class: it is set while the
 * button holds focus that a traversal key brought in, and stays clear for focus
 * gained by a pointer press, by the window opening, or programmatically. Themes key
 * their focus styling off it so only keyboard users get a focus indicator. See
 * {@link KeyboardTraversalMonitor}.</p>
 */
public class RXButtonSkin extends ButtonSkin {

    private static final PseudoClass KEY_FOCUSED = PseudoClass.getPseudoClass("key-focused");

    private final SkinDisposer disposer = new SkinDisposer();
    private final RippleDecoration ripple;
    private final ArmedRippleTrigger rippleTrigger;

    /**
     * Creates the skin and wires the ripple triggers.
     *
     * @param button the button this skin is attached to
     */
    public RXButtonSkin(RXButton button) {
        super(button);
        ripple = new RippleDecoration(button, button.rippleEnabledProperty(),
                button.stateOverlayEnabledProperty(), button.rippleFillProperty(),
                button::getRippleOpacity, null, button.rippleCornerRadiusProperty());

        // Pointer tracking + armed-driven press/release + PLAY_RIPPLE are shared
        // across the button skins; see ArmedRippleTrigger.
        rippleTrigger = new ArmedRippleTrigger(button, ripple,
                button::isRippleEnabled, button::isRippleCentered);
        rippleTrigger.installPointerTracking(disposer);
        disposer.registerListener(button.armedProperty(), rippleTrigger::handleArmedChanged);
        rippleTrigger.installPlayRipple(disposer);

        // Pressing an already focused button drops the indicator, matching what the
        // scene monitor does for a press that moves focus in.
        disposer.registerEventFilter(button, MouseEvent.MOUSE_PRESSED,
                event -> button.pseudoClassStateChanged(KEY_FOCUSED, false));
        disposer.registerListener(button.focusedProperty(), this::handleFocusChanged);
        disposer.registerListener(button.sceneProperty(),
                () -> KeyboardTraversalMonitor.install(button.getScene()));
        KeyboardTraversalMonitor.install(button.getScene());
        disposer.registerDisposeTask(() -> button.pseudoClassStateChanged(KEY_FOCUSED, false));

        updateChildren();
    }

    @Override
    protected void updateChildren() {
        super.updateChildren();
        // The first call comes from the LabeledSkinBase constructor, before
        // this skin's fields are initialized.
        if (ripple != null) {
            getChildren().add(0, ripple.getLayer());
        }
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        ripple.layout(getSkinnable().getWidth(), getSkinnable().getHeight());
    }

    /**
     * Stops ripple animations, removes the ripple layer and unregisters all
     * ripple listeners before the standard {@link ButtonSkin} cleanup runs.
     */
    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(this::disposeRipple, disposer::dispose, super::dispose);
    }

    // ==================== Focus ====================

    private void handleFocusChanged() {
        Button button = getSkinnable();
        button.pseudoClassStateChanged(KEY_FOCUSED, button.isFocused()
                && KeyboardTraversalMonitor.isKeyboardTraversal(button.getScene()));
    }

    // ==================== Ripple Trigger ====================

    private void disposeRipple() {
        ripple.dispose();
        getChildren().remove(ripple.getLayer());
    }
}
