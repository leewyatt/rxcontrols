package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.internal.fill.FillDecoration;

/**
 * Skin for {@link RXFillButton}: the {@link RXButtonSkin} plus a
 * {@link FillDecoration} layer between the button background and the ripple
 * layer. See the decoration for the progress model, triggers and the
 * {@code :filling} pseudo-class contract.
 */
public class RXFillButtonSkin extends RXButtonSkin {

    private final FillDecoration fill;

    /**
     * Creates the skin and wires the fill decoration layer.
     *
     * @param button the button this skin is attached to
     */
    public RXFillButtonSkin(RXFillButton button) {
        super(button);
        // The fill sweep is the hover affordance; the ripple hover overlay would
        // only tint the fill, so it is suppressed (the press ripple stays).
        setHoverOverlayEnabled(false);
        fill = new FillDecoration(button,
                button.fillAnimationProperty(),
                button.animationTriggerProperty(),
                button.animationDurationProperty(),
                button.fillInsetsProperty(),
                button.fillCornerRadiusProperty());
        updateChildren();
    }

    @Override
    protected void updateChildren() {
        super.updateChildren();
        // The first calls come from superclass constructors, before this
        // skin's fields are initialized.
        if (fill != null) {
            getChildren().add(0, fill.getLayer());
        }
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        fill.layout(getSkinnable().getWidth(), getSkinnable().getHeight());
    }

    /**
     * Stops the fill animation, removes the fill layer and unregisters all
     * fill listeners before the {@link RXButtonSkin} cleanup runs.
     */
    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(this::disposeFill, super::dispose);
    }

    private void disposeFill() {
        fill.dispose();
        getChildren().remove(fill.getLayer());
    }
}
