package io.github.leewyatt.rxcontrols.internal;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Skin;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reusable mask / reveal plumbing for password skins built on
 * {@code TextFieldSkin}. It locates the skin's internal text node — without
 * reflection, by walking the control's scene graph — and rebinds it to a
 * {@link StringBinding} that recomputes the displayed text from a caller-supplied
 * mask function whenever the text or the reveal / echo dependencies change. If
 * the node cannot be found or the rebind fails, it degrades to a permanent mask
 * and logs a warning.
 * <p>
 * The {@code maskText} override and the default-masked guard stay in each skin
 * (JavaFX invokes {@code maskText} on the skin itself); they consult
 * {@link #isInstalled()} so plain text is never revealed before the dynamic
 * binding is in place.
 * <p>
 * Modeled on the {@code BoundedClipSupport} pattern: a focused helper a skin
 * holds as a field, installs once, and tears down via a single
 * {@link #dispose()} registered on the skin's disposer.
 */
public final class PasswordMaskSupport {

    /** Produces the displayed text from the raw text (typically the skin's {@code maskText}). */
    @FunctionalInterface
    public interface MaskFunction {
        /**
         * Maps raw text to its display form.
         *
         * @param rawText the control's current text (never {@code null})
         * @return the text to display
         */
        String mask(String rawText);
    }

    private static final Logger LOGGER = Logger.getLogger(PasswordMaskSupport.class.getName());

    private final Skin<?> owner;
    private final TextInputControl control;
    private final MaskFunction maskFunction;
    private final Observable[] dependencies;
    private final InvalidationListener ineffectiveToggleListener;

    private boolean installed;
    private boolean failed;
    private StringBinding displayBinding;
    private ChangeListener<Skin<?>> pendingSkinListener;

    /**
     * Creates the helper.
     *
     * @param owner        the skin that owns this helper (used to detect attach)
     * @param control      the password control being skinned
     * @param maskFunction maps raw text to displayed text (the skin's mask logic)
     * @param dependencies extra observables the displayed text depends on
     *                     (e.g. reveal flag, echo character)
     */
    public PasswordMaskSupport(Skin<?> owner, TextInputControl control,
                               MaskFunction maskFunction, Observable... dependencies) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.control = Objects.requireNonNull(control, "control");
        this.maskFunction = Objects.requireNonNull(maskFunction, "maskFunction");
        this.dependencies = dependencies.clone();
        this.ineffectiveToggleListener = this::logIneffectiveToggle;
        for (Observable dependency : this.dependencies) {
            dependency.addListener(ineffectiveToggleListener);
        }
    }

    /**
     * Installs the dynamic binding once JavaFX has attached the owner skin. A
     * constructor-time search can still see the previous skin's nodes during
     * skin replacement, so the discovery waits for the attach.
     */
    public void install() {
        pendingSkinListener = (obs, oldSkin, newSkin) -> {
            if (newSkin == owner) {
                control.skinProperty().removeListener(pendingSkinListener);
                pendingSkinListener = null;
                Text node = findTextNode();
                if (node != null) {
                    rebind(node);
                } else {
                    failed = true;
                    logFallback("discovery", "skin-attached text-node discovery failed");
                }
            }
        };
        control.skinProperty().addListener(pendingSkinListener);
    }

    /**
     * @return whether the dynamic display binding is installed; skins gate their
     *         reveal logic on this so a failed install stays permanently masked
     */
    public boolean isInstalled() {
        return installed;
    }

    /**
     * Removes listeners and disposes the binding. Register on the skin's disposer.
     */
    public void dispose() {
        for (Observable dependency : dependencies) {
            dependency.removeListener(ineffectiveToggleListener);
        }
        if (pendingSkinListener != null) {
            control.skinProperty().removeListener(pendingSkinListener);
            pendingSkinListener = null;
        }
        if (displayBinding != null) {
            displayBinding.dispose();
            displayBinding = null;
        }
    }

    /**
     * Finds the {@code TextFieldSkin} text node by narrowing to the clipped
     * text-group pane and matching the bound-layoutX {@link Text}. Avoids
     * matching user-supplied nodes in the side wrappers or other decorations.
     */
    private Text findTextNode() {
        List<Text> filtered = control.getChildrenUnmodifiable().stream()
                .filter(Pane.class::isInstance)
                .map(Pane.class::cast)
                .filter(pane -> pane.getClip() instanceof Rectangle)
                .flatMap(pane -> pane.getChildrenUnmodifiable().stream())
                .filter(Text.class::isInstance)
                .map(Text.class::cast)
                .filter(text -> text.layoutXProperty().isBound())
                .toList();
        if (filtered.size() == 1) {
            return filtered.get(0);
        }
        return null;
    }

    private void rebind(Text textNode) {
        try {
            Observable[] deps = new Observable[dependencies.length + 1];
            deps[0] = control.textProperty();
            System.arraycopy(dependencies, 0, deps, 1, dependencies.length);
            StringBinding binding = Bindings.createStringBinding(this::displayText, deps);
            // bind() computes immediately, so the flag must be true first.
            installed = true;
            displayBinding = binding;
            textNode.textProperty().unbind();
            textNode.textProperty().bind(binding);
        } catch (RuntimeException ex) {
            // Leave the node in a known masked state rather than relying on
            // future refreshes.
            installed = false;
            failed = true;
            if (displayBinding != null) {
                displayBinding.dispose();
                displayBinding = null;
            }
            textNode.textProperty().unbind();
            textNode.setText(displayText());
            logFallback("binding-install", ex.toString());
        }
    }

    private String displayText() {
        String text = control.getText();
        return maskFunction.mask(text == null ? "" : text);
    }

    private void logIneffectiveToggle(Observable source) {
        if (installed || !failed) {
            return;
        }
        LOGGER.log(Level.WARNING,
                "Password display cannot refresh because its dynamic text binding"
                        + " could not be installed."
                        + " [property={0}, javafx.runtime.version={1}]",
                new Object[]{source, System.getProperty("javafx.runtime.version")});
    }

    private void logFallback(String source, String detail) {
        LOGGER.log(Level.WARNING,
                "JavaFX TextFieldSkin internals appear to have changed at runtime."
                        + " Password reveal is disabled; the mask remains active."
                        + " [source={0}, javafx.runtime.version={1}, java.version={2}, detail={3}]",
                new Object[]{
                        source,
                        System.getProperty("javafx.runtime.version"),
                        System.getProperty("java.version"),
                        detail
                });
    }
}
