package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXLineLabel;
import io.github.leewyatt.rxcontrols.internal.line.LineDecoration;
import javafx.scene.control.skin.LabelSkin;

/**
 * Skin for {@link RXLineLabel}: the standard {@link LabelSkin} plus a
 * {@link LineDecoration} layer below the text. See the decoration for the
 * progress model, triggers and the {@code :line-showing} pseudo-class
 * contract.
 *
 * <p>The line reference box is the union of the text and graphic bounds,
 * recomputed every layout pass (content changes always trigger a layout);
 * with no visible content it falls back to the padded content area.</p>
 */
public class RXLineLabelSkin extends LabelSkin {

    private final LineDecoration line;

    /**
     * Creates the skin and wires the line decoration layer.
     *
     * @param label the label this skin is attached to
     */
    public RXLineLabelSkin(RXLineLabel label) {
        super(label);
        line = new LineDecoration(label,
                label.lineAnimationProperty(),
                label.animationTriggerProperty(),
                label.animationDurationProperty(),
                label.lineThicknessProperty(),
                label.lineGapProperty());
        updateChildren();
    }

    @Override
    protected void updateChildren() {
        super.updateChildren();
        // The first call comes from the LabeledSkinBase constructor, before
        // this skin's fields are initialized.
        if (line != null) {
            getChildren().add(0, line.getLayer());
        }
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        line.layout(getSkinnable().getWidth(), getSkinnable().getHeight(),
                LineDecoration.contentReferenceOf(getSkinnable(), getChildren(), x, y, w, h));
    }

    /**
     * Stops the line animation, removes the line layer and unregisters all
     * line listeners before the standard {@link LabelSkin} cleanup runs.
     */
    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(this::disposeLine, super::dispose);
    }

    private void disposeLine() {
        line.dispose();
        getChildren().remove(line.getLayer());
    }
}
