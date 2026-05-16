package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXPasswordField;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Skin;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Skin for {@link RXPasswordField}. It keeps the left/right slot layout from
 * {@link RXFieldBaseSkin} and replaces JavaFX's internal text binding so
 * {@code showPassword} and {@code echoChar} refresh the rendered text
 * immediately.
 * <p>
 * Until that replacement binding is installed, {@link #maskText(String)}
 * always returns a masked value. If JavaFX internals change and the internal
 * text node cannot be found, the field safely degrades to permanent masking
 * and logs a warning.
 * <p>
 * JavaFX's original text binding also calls this {@code maskText} override, so
 * the default masked state is what prevents early reveal before replacement.
 */
public class RXPasswordFieldSkin extends RXFieldBaseSkin {

    private static final Logger LOGGER = Logger.getLogger(RXPasswordFieldSkin.class.getName());

    private boolean dynamicTextBindingInstalled = false;
    private boolean dynamicTextBindingFailed = false;
    private StringBinding displayTextBinding;
    private ChangeListener<Skin<?>> pendingSkinListener;
    private final ChangeListener<Object> ineffectiveToggleListener =
            (obs, oldVal, newVal) -> logIneffectiveToggle(obs);

    public RXPasswordFieldSkin(RXPasswordField control) {
        super(control, control.leftProperty(), control.rightProperty(), control.textPaddingProperty());
        control.showPasswordProperty().addListener(ineffectiveToggleListener);
        control.echoCharProperty().addListener(ineffectiveToggleListener);
        tryInstallDynamicTextBinding(control);
    }

    @Override
    protected String maskText(String txt) {
        // Use the argument supplied by JavaFX's text binding; reading
        // control.getText() here can observe a stale value during recompute.
        txt = (txt == null) ? "" : txt;

        RXPasswordField field = (RXPasswordField) getSkinnable();
        Character echo = (field == null) ? null : field.getEchoChar();
        char ch = (echo == null) ? RXPasswordField.DEFAULT_ECHO_CHAR : echo;

        // Safe by default: before the replacement binding is installed, never
        // reveal plain text through JavaFX's original binding.
        if (!dynamicTextBindingInstalled || field == null || !field.isShowPassword()) {
            return String.valueOf(ch).repeat(txt.length());
        }
        return txt;
    }

    // ==================== Dynamic binding installation ====================

    private void tryInstallDynamicTextBinding(RXPasswordField control) {
        // Wait until JavaFX has attached this skin. During skin replacement,
        // a constructor-time search can still see the previous skin's nodes.
        pendingSkinListener = (obs, oldSkin, newSkin) -> {
            if (newSkin == this) {
                control.skinProperty().removeListener(pendingSkinListener);
                pendingSkinListener = null;
                Text retryNode = findTextFieldSkinTextNode();
                if (retryNode != null) {
                    rebindTextNode(control, retryNode);
                } else {
                    dynamicTextBindingFailed = true;
                    logFallback("discovery", "skin-attached textNode discovery failed");
                }
            }
        };
        control.skinProperty().addListener(pendingSkinListener);
    }

    /**
     * Finds the parent {@code TextFieldSkin.textNode} by first narrowing the
     * search to the clipped textGroup pane created by {@code TextFieldSkin}.
     * This avoids matching user-supplied nodes in the left/right wrappers.
     */
    private Text findTextFieldSkinTextNode() {
        List<Text> filtered = getChildren().stream()
                .filter(Pane.class::isInstance)
                .map(Pane.class::cast)
                .filter(pane -> pane.getClip() instanceof Rectangle)
                .flatMap(pane -> pane.getChildren().stream())
                .filter(Text.class::isInstance)
                .map(Text.class::cast)
                .filter(t -> t.layoutXProperty().isBound())
                .toList();
        if (filtered.size() == 1) {
            return filtered.get(0);
        }
        return null;
    }

    private void rebindTextNode(RXPasswordField control, Text textNode) {
        try {
            StringBinding binding = Bindings.createStringBinding(
                    () -> maskText(control.getText() == null ? "" : control.getText()),
                    control.textProperty(),
                    control.showPasswordProperty(),
                    control.echoCharProperty());
            // bind() computes immediately, so the flag must be true first.
            dynamicTextBindingInstalled = true;
            displayTextBinding = binding;
            textNode.textProperty().unbind();
            textNode.textProperty().bind(binding);
        } catch (RuntimeException ex) {
            // The original binding may already be removed, so leave the node
            // in a known masked state instead of relying on future refreshes.
            dynamicTextBindingInstalled = false;
            dynamicTextBindingFailed = true;
            if (displayTextBinding != null) {
                displayTextBinding.dispose();
                displayTextBinding = null;
            }
            textNode.textProperty().unbind();
            String safeMask = maskText(control.getText() == null ? "" : control.getText());
            textNode.setText(safeMask);
            logFallback("binding-install", ex.toString());
        }
    }

    private void logIneffectiveToggle(ObservableValue<?> source) {
        if (dynamicTextBindingInstalled || !dynamicTextBindingFailed) {
            return;
        }
        LOGGER.log(Level.WARNING,
                "RXPasswordField cannot refresh the displayed password because"
                        + " its dynamic text binding could not be installed."
                        + " [property={0}, javafx.runtime.version={1}]",
                new Object[]{
                        source,
                        System.getProperty("javafx.runtime.version")
                });
    }

    private void logFallback(String source, String detail) {
        LOGGER.log(Level.WARNING,
                "JavaFX TextFieldSkin internals appear to have changed at runtime."
                        + " showPassword dynamic toggle is disabled; mask remains active."
                        + " [source={0}, javafx.runtime.version={1}, java.version={2}, detail={3}]",
                new Object[]{
                        source,
                        System.getProperty("javafx.runtime.version"),
                        System.getProperty("java.version"),
                        detail
                });
    }

    // ==================== Lifecycle ====================

    @Override
    public void dispose() {
        RXPasswordField control = (RXPasswordField) getSkinnable();
        if (control != null) {
            control.showPasswordProperty().removeListener(ineffectiveToggleListener);
            control.echoCharProperty().removeListener(ineffectiveToggleListener);
            if (pendingSkinListener != null) {
                control.skinProperty().removeListener(pendingSkinListener);
                pendingSkinListener = null;
            }
        }
        if (displayTextBinding != null) {
            displayTextBinding.dispose();
            displayTextBinding = null;
        }
        super.dispose();
    }
}
