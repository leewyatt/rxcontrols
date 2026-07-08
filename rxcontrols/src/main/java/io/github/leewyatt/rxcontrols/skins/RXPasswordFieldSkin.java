package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXPasswordField;
import io.github.leewyatt.rxcontrols.internal.PasswordMaskSupport;

/**
 * Skin for {@link RXPasswordField}. It keeps the left/right slot layout from
 * {@link RXFieldBaseSkin} and replaces JavaFX's internal text binding (via the
 * shared {@link PasswordMaskSupport}) so {@code revealPassword} and
 * {@code echoChar} refresh the rendered text immediately.
 * <p>
 * Until that replacement binding is installed, {@link #maskText(String)} always
 * returns a masked value; JavaFX's original text binding also calls this
 * override, so the default masked state is what prevents early reveal before
 * replacement. If JavaFX internals change and the text node cannot be found, the
 * helper degrades to permanent masking and logs a warning.
 */
public class RXPasswordFieldSkin extends RXFieldBaseSkin {

    private final PasswordMaskSupport maskSupport;

    /**
     * Creates the skin for the given control.
     *
     * @param control the password field being skinned
     */
    public RXPasswordFieldSkin(RXPasswordField control) {
        super(control, control.leftProperty(), control.rightProperty(), control.textPaddingProperty());
        maskSupport = new PasswordMaskSupport(this, control, this::maskText,
                control.revealPasswordProperty(), control.echoCharProperty());
        maskSupport.install();
        disposer.registerDisposeTask(maskSupport::dispose);
    }

    @Override
    protected String maskText(String txt) {
        // Use the argument supplied by JavaFX's text binding; reading
        // control.getText() here can observe a stale value during recompute.
        txt = (txt == null) ? "" : txt;

        RXPasswordField field = (RXPasswordField) getSkinnable();
        Character echo = (field == null) ? null : field.getEchoChar();
        char ch = (echo == null) ? RXPasswordField.DEFAULT_ECHO_CHAR : echo;

        // Safe by default: before the replacement binding is installed (including
        // during super()'s construction, when maskSupport is not yet assigned and
        // JavaFX's original binding already calls this), never reveal plain text.
        if (maskSupport == null || !maskSupport.isInstalled() || field == null || !field.isRevealPassword()) {
            return String.valueOf(ch).repeat(txt.length());
        }
        return txt;
    }
}
