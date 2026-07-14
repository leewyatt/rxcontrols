package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXFloatingActionButton;

/**
 * Skin for {@link RXFloatingActionButton}. It reuses {@link RXButtonSkin}'s
 * bounded ripple and standard button behavior unchanged; the FAB shape,
 * elevation, and size variants are provided by CSS.
 */
public class RXFloatingActionButtonSkin extends RXButtonSkin {

    /**
     * Creates the FAB skin.
     *
     * @param button the floating action button
     */
    public RXFloatingActionButtonSkin(RXFloatingActionButton button) {
        super(button);
    }
}
