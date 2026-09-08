package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.internal.ripple.ArmedRippleTrigger;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleDecoration;
import javafx.scene.control.skin.ButtonSkin;

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
 */
public class RXButtonSkin extends ButtonSkin {

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

    // ==================== Ripple Trigger ====================

    private void disposeRipple() {
        ripple.dispose();
        getChildren().remove(ripple.getLayer());
    }
}
