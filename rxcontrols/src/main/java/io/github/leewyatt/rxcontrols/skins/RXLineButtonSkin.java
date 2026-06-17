package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXLineButton;
import io.github.leewyatt.rxcontrols.internal.line.LineDecoration;

/**
 * Skin for {@link RXLineButton}: the {@link RXButtonSkin} plus a
 * {@link LineDecoration} layer between the button background and the ripple
 * layer. See the decoration for the progress model, triggers and the
 * {@code :line-showing} pseudo-class contract.
 *
 * <p>The line reference box is the union of the text and graphic bounds,
 * recomputed every layout pass (content changes always trigger a layout);
 * with no visible content it falls back to the padded content area.</p>
 */
public class RXLineButtonSkin extends RXButtonSkin {

    private final LineDecoration line;

    /**
     * Creates the skin and wires the line decoration layer.
     *
     * @param button the button this skin is attached to
     */
    public RXLineButtonSkin(RXLineButton button) {
        super(button);
        line = new LineDecoration(button,
                button.lineAnimationProperty(),
                button.animationTriggerProperty(),
                button.animationDurationProperty(),
                button.lineThicknessProperty(),
                button.lineGapProperty());
        updateChildren();
    }

    @Override
    protected void updateChildren() {
        super.updateChildren();
        // The first calls come from superclass constructors, before this
        // skin's fields are initialized.
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
     * line listeners before the {@link RXButtonSkin} cleanup runs.
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
