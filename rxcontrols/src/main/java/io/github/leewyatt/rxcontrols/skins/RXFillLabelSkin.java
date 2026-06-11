package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXFillLabel;
import io.github.leewyatt.rxcontrols.internal.fill.FillDecoration;
import javafx.scene.control.skin.LabelSkin;

/**
 * Skin for {@link RXFillLabel}: the standard {@link LabelSkin} plus a
 * {@link FillDecoration} layer below the text. See the decoration for the
 * progress model, triggers and the {@code :filling} pseudo-class contract.
 */
public class RXFillLabelSkin extends LabelSkin {

    private final FillDecoration fill;

    /**
     * Creates the skin and wires the fill decoration layer.
     *
     * @param label the label this skin is attached to
     */
    public RXFillLabelSkin(RXFillLabel label) {
        super(label);
        fill = new FillDecoration(label,
                label.fillAnimationProperty(),
                label.animationTriggerProperty(),
                label.animationDurationProperty(),
                label.fillInsetsProperty(),
                label.fillRadiusProperty());
        updateChildren();
    }

    @Override
    protected void updateChildren() {
        super.updateChildren();
        // The first call comes from the LabeledSkinBase constructor, before
        // this skin's fields are initialized.
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
     * fill listeners before the standard {@link LabelSkin} cleanup runs.
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
