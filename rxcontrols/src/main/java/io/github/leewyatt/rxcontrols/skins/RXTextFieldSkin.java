package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTextField;

/**
 * Default skin for {@link RXTextField}. Delegates layout and pseudo-class
 * wiring to {@link RXFieldBaseSkin}, binding the base skin's "effective"
 * observables directly to the control's properties.
 */
public class RXTextFieldSkin extends RXFieldBaseSkin {

    public RXTextFieldSkin(RXTextField control) {
        super(control, control.leftProperty(), control.rightProperty(), control.textPaddingProperty());
    }
}
