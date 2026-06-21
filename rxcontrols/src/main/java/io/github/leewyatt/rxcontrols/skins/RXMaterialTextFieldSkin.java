package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXMaterialTextField;

/**
 * Default skin for {@link RXMaterialTextField}. Forwards the control's Material
 * properties to {@link RXMaterialFieldBaseSkin}, which renders the floating
 * label, activation lines, and supporting row.
 */
public class RXMaterialTextFieldSkin extends RXMaterialFieldBaseSkin {

    /**
     * Creates the skin for the given control.
     *
     * @param control the field being skinned
     */
    public RXMaterialTextFieldSkin(RXMaterialTextField control) {
        super(control,
                control.leadingNodeProperty(),
                control.trailingNodeProperty(),
                control.textPaddingProperty(),
                control.labelTextProperty(),
                control.helperTextProperty(),
                control.errorTextProperty(),
                control.invalidProperty(),
                control.floatingLabelProperty(),
                control.animatedProperty(),
                control.animationDurationProperty(),
                control.labelFloatScaleProperty(),
                control.variantProperty(),
                control.showClearButtonProperty());
    }
}
