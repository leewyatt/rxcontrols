package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXFloatingActionButton;

/**
 * Skin for {@link RXFloatingActionButton}. It reuses {@link RXButtonSkin}'s
 * bounded ripple and standard button behavior unchanged; the FAB shape,
 * elevation, and size variants are provided by CSS. It also sets the
 * {@code :focus-shown} pseudo-class, so the focus ring shows for keyboard
 * focus but not after a click.
 */
public class RXFloatingActionButtonSkin extends RXButtonSkin {

    private final SkinDisposer disposer = new SkinDisposer();

    /**
     * Creates the FAB skin.
     *
     * @param button the floating action button
     */
    public RXFloatingActionButtonSkin(RXFloatingActionButton button) {
        super(button);
        FocusShownTracker.install(button, disposer);
    }

    /**
     * Stops tracking the focus source before the button skin's own cleanup runs.
     */
    @Override
    public void dispose() {
        if (getSkinnable() == null) {
            return;
        }
        SkinDisposer.disposeInOrder(disposer::dispose, super::dispose);
    }
}
