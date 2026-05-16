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
 * Default skin for {@link RXPasswordField}. Responsible for two things on top
 * of what {@link RXFieldBaseSkin} already provides:
 *
 * <ol>
 *   <li>Overrides {@link #maskText(String)} so that
 *       {@link RXPasswordField#getEchoChar()} replaces the JavaFX-builtin
 *       {@code BULLET} and {@link RXPasswordField#isShowPassword()} can
 *       reveal plain text.</li>
 *   <li>Replaces the parent {@link javafx.scene.text.Text} node's text
 *       binding so the mask refreshes whenever {@code showPassword} or
 *       {@code echoChar} changes (the parent skin's binding only listens to
 *       the control's text property).</li>
 * </ol>
 *
 * <h2>Safety contract</h2>
 * The {@code dynamicTextBindingInstalled} flag defaults to {@code false} and
 * is flipped to {@code true} only after the new binding has been installed.
 * Before that point — including the synchronous window between {@code super()}
 * returning and binding installation, and the window where the skin is
 * waiting for {@code skinProperty} to settle so the internal {@code textNode}
 * can be discovered — {@code maskText} returns a fully masked string
 * regardless of {@code showPassword}. This guarantees that no plain-text
 * password leaks through the parent's still-live string binding.
 * <p>
 * If the structural search fails permanently (i.e. JavaFX internals shifted in
 * a way that breaks the heuristic), the flag stays {@code false} and the field
 * degrades to a permanent mask while still respecting the configured
 * {@code echoChar}. A warning is logged once, including the JavaFX runtime
 * version and failure detail, so downstream maintainers can diagnose.
 */
public class RXPasswordFieldSkin extends RXFieldBaseSkin {

    private static final Logger LOGGER = Logger.getLogger(RXPasswordFieldSkin.class.getName());

    private boolean dynamicTextBindingInstalled = false;
    private StringBinding displayTextBinding;
    private ChangeListener<Skin<?>> pendingSkinListener;
    private final ChangeListener<Object> ineffectiveToggleListener =
            (obs, oldVal, newVal) -> logIneffectiveToggle(obs);

    public RXPasswordFieldSkin(RXPasswordField control) {
        super(control, control.leftProperty(), control.rightProperty(), control.textPaddingProperty());
        // Per §11.0 Q1: while the dynamic binding is not yet installed (or has
        // been disabled by a fallback), changing showPassword / echoChar does
        // not visually take effect — warn the user once per toggle so they
        // know why nothing happened.
        control.showPasswordProperty().addListener(ineffectiveToggleListener);
        control.echoCharProperty().addListener(ineffectiveToggleListener);
        // Why not stash `control` in a subclass field: TextFieldSkin installs
        // textNode.textProperty().bind(StringBinding{ ... maskText(...) }) inside
        // its constructor (super chain). The binding's computeValue is a virtual
        // dispatch to our maskText, so any code path that triggers an eager
        // compute during super() would observe a still-null subclass field and
        // NPE. getSkinnable() is assigned by SkinBase in the super chain, so
        // reaching for the control via (RXPasswordField) getSkinnable() inside
        // maskText is safe even mid-super-construction.
        tryInstallDynamicTextBinding(control);
    }

    @Override
    protected String maskText(String txt) {
        // Must use the passed-in argument: the parent's StringBinding feeds the
        // latest textProperty value here. Reading control.getText() at binding
        // recompute time can return a stale value.
        if (txt == null) {
            txt = "";
        }

        RXPasswordField field = (RXPasswordField) getSkinnable();
        Character echo = (field == null) ? null : field.getEchoChar();
        char ch = (echo == null) ? RXPasswordField.DEFAULT_ECHO_CHAR : echo;

        // Default-safe: unless our dynamic binding has been installed AND the
        // user explicitly asked to reveal, fully mask. Covers (A) the
        // synchronous window before lookup, (B) the asynchronous wait for
        // skin attachment, and (C) the fallback terminal state.
        if (!dynamicTextBindingInstalled || field == null || !field.isShowPassword()) {
            return String.valueOf(ch).repeat(txt.length());
        }
        return txt;
    }

    // ==================== Dynamic binding installation ====================

    private void tryInstallDynamicTextBinding(RXPasswordField control) {
        // Never sync-lookup in the constructor: when this skin is replacing an
        // already-attached skin (field.setSkin(new RXPasswordFieldSkin(field))),
        // the swap happens AFTER our constructor returns. A sync
        // control.lookupAll(".text") here would hit the previous skin's
        // textNode, bind our StringBinding to a node that's about to be
        // disposed, set dynamicTextBindingInstalled=true on this skin, and
        // leave OUR textNode driven by the parent's builtin binding — which
        // then enters our maskText with installed=true and leaks plaintext on
        // showPassword toggle. Always wait for the attachment via skinProperty
        // == this; by then JavaFX has swapped the children and this skin's
        // direct child list contains the textGroup that is actually displayed.
        pendingSkinListener = (obs, oldSkin, newSkin) -> {
            if (newSkin == this) {
                control.skinProperty().removeListener(pendingSkinListener);
                pendingSkinListener = null;
                Text retryNode = findTextFieldSkinTextNode();
                if (retryNode != null) {
                    rebindTextNode(control, retryNode);
                } else {
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
            // Flip the flag before bind() — the first compute happens inside
            // bind() and must see the flag true so it honours showPassword.
            // The whole sequence is on the FX thread, no race.
            dynamicTextBindingInstalled = true;
            displayTextBinding = binding;
            textNode.textProperty().unbind();
            textNode.textProperty().bind(binding);
        } catch (RuntimeException ex) {
            // First compute (inside bind) could in principle throw if a future
            // override or hook injects an exception path into maskText. Roll
            // every partial side effect back so the textNode lands in an
            // explicit safe state — we cannot resurrect the parent's binding,
            // so static-set the textNode to a mask and accept that future
            // text/echoChar changes will not refresh until skin replacement.
            dynamicTextBindingInstalled = false;
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
        if (dynamicTextBindingInstalled) {
            return;
        }
        LOGGER.log(Level.WARNING,
                "RXPasswordField property changed while the dynamic mask binding"
                        + " is not installed; the UI will not reflect this change."
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
        }
        if (pendingSkinListener != null && control != null) {
            control.skinProperty().removeListener(pendingSkinListener);
            pendingSkinListener = null;
        }
        if (displayTextBinding != null) {
            // textNode lives in the parent skin's subtree which is torn down
            // by super.dispose(); the binding is released alongside, but
            // disposing explicitly drops the strong references to the
            // control's three observables immediately.
            displayTextBinding.dispose();
            displayTextBinding = null;
        }
        super.dispose();
    }
}
