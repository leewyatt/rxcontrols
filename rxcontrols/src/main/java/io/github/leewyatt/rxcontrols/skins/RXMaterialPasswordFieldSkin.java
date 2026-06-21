package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXMaterialPasswordField;
import io.github.leewyatt.rxcontrols.internal.PasswordMaskSupport;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.List;

/**
 * Skin for {@link RXMaterialPasswordField}: the shared Material decoration of
 * {@link RXMaterialFieldBaseSkin} plus password masking and a built-in reveal
 * (eye) button composed into the trailing slot (before the clear button).
 * <p>
 * Masking reuses {@link PasswordMaskSupport} for the text-node discovery,
 * dynamic rebind, and graceful degradation; the {@link #maskText(String)}
 * override and the default-masked guard stay here because JavaFX invokes
 * {@code maskText} on the skin (including during {@code super(...)}, before this
 * skin's fields are assigned — hence the null guard).
 */
public class RXMaterialPasswordFieldSkin extends RXMaterialFieldBaseSkin {

    private static final String REVEAL_BUTTON_CLASS = "reveal-button";
    private static final String GRAPHIC_CLASS = "graphic";

    private final Region revealGraphic = new Region();
    private final StackPane revealButton = new StackPane(revealGraphic);
    private final PasswordMaskSupport maskSupport;

    /**
     * Creates the skin for the given control.
     *
     * @param control the password field being skinned
     */
    public RXMaterialPasswordFieldSkin(RXMaterialPasswordField control) {
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
                control.showClearButtonProperty());

        revealGraphic.getStyleClass().add(GRAPHIC_CLASS);
        revealGraphic.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        revealGraphic.setMouseTransparent(true);
        revealButton.getStyleClass().add(REVEAL_BUTTON_CLASS);

        maskSupport = new PasswordMaskSupport(this, control, this::maskText,
                control.revealPasswordProperty(), control.echoCharProperty());
        maskSupport.install();

        disposer.registerEventHandler(revealButton, MouseEvent.MOUSE_CLICKED, this::onRevealClicked);
        disposer.registerListener(control.showRevealButtonProperty(), this::refreshTrailing);
        disposer.registerDisposeTask(maskSupport::dispose);

        // The reveal button field was null during super()'s updateTrailing; rebuild
        // the trailing composition now that it exists.
        refreshTrailing();
    }

    @Override
    protected List<Node> builtinTrailingAffordances() {
        // Present only when enabled; null during super() before the field exists.
        if (revealButton == null || !showRevealButtonEnabled()) {
            return List.of();
        }
        return List.of(revealButton);
    }

    @Override
    protected String maskText(String txt) {
        txt = (txt == null) ? "" : txt;
        RXMaterialPasswordField field = (RXMaterialPasswordField) getSkinnable();
        Character echo = (field == null) ? null : field.getEchoChar();
        char ch = (echo == null) ? RXMaterialPasswordField.DEFAULT_ECHO_CHAR : echo;
        // Safe by default: before the dynamic binding is installed (including
        // during super(), when maskSupport is not yet assigned), never reveal.
        if (maskSupport == null || !maskSupport.isInstalled() || field == null || !field.isRevealPassword()) {
            return String.valueOf(ch).repeat(txt.length());
        }
        return txt;
    }

    private boolean showRevealButtonEnabled() {
        RXMaterialPasswordField field = (RXMaterialPasswordField) getSkinnable();
        return field != null && field.isShowRevealButton();
    }

    private void onRevealClicked(MouseEvent event) {
        RXMaterialPasswordField field = (RXMaterialPasswordField) getSkinnable();
        if (field != null) {
            field.setRevealPassword(!field.isRevealPassword());
        }
        event.consume();
    }
}
