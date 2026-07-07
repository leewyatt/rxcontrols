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
 * (still tracking the text where possible), logs a warning, and notifies the
 * optional {@link #setOnDegraded(Runnable) degradation callback}.
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
    private Text boundTextNode;
    private ChangeListener<Skin<?>> pendingSkinListener;
    private Runnable onDegraded;

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
                    // Log first: a throwing degradation callback must not
                    // suppress the primary diagnostic.
                    logFallback("discovery", "skin-attached text-node discovery failed");
                    degrade();
                }
            } else if (newSkin != null) {
                // A different skin got attached — stop listening so the ghost
                // skin does not keep a live hook on the control. Should the
                // owner be attached later after all, the helper stays inert
                // (permanently masked, not failed).
                control.skinProperty().removeListener(pendingSkinListener);
                pendingSkinListener = null;
            }
        };
        control.skinProperty().addListener(pendingSkinListener);
    }

    /**
     * Sets a callback invoked when the helper degrades (text-node discovery or
     * rebind failure). Skins use it to hide their reveal affordance so the UI
     * does not offer a toggle that can no longer change the display.
     *
     * @param callback the degradation callback, may be {@code null}
     */
    public void setOnDegraded(Runnable callback) {
        this.onDegraded = callback;
    }

    /**
     * @return whether the helper has degraded (discovery or rebind failed);
     *         the mask stays permanently active in that state
     */
    public boolean isFailed() {
        return failed;
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
        // Flip before the snapshot below so the mask function fails closed.
        installed = false;
        if (displayBinding != null) {
            displayBinding.dispose();
            displayBinding = null;
        }
        if (boundTextNode != null) {
            // Symmetric with the rebind-failure path: detach the dead binding
            // (Property.bind keeps a strong reference to it, which keeps this
            // helper and the whole old skin graph alive) and pin the node back
            // to a masked snapshot so a skin swap while revealed cannot leave
            // plain text frozen on screen.
            boundTextNode.textProperty().unbind();
            boundTextNode.setText(displayText());
            boundTextNode = null;
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
            boundTextNode = textNode;
            textNode.textProperty().unbind();
            textNode.textProperty().bind(binding);
        } catch (RuntimeException ex) {
            // Fail closed but keep breathing: re-bind a minimal binding that
            // tracks the text only, so the (permanently masked, installed stays
            // false) display still follows edits like the native binding did.
            installed = false;
            if (displayBinding != null) {
                displayBinding.dispose();
                displayBinding = null;
            }
            boundTextNode = null;
            textNode.textProperty().unbind();
            try {
                StringBinding maskedOnly =
                        Bindings.createStringBinding(this::displayText, control.textProperty());
                textNode.textProperty().bind(maskedOnly);
                displayBinding = maskedOnly;
                boundTextNode = textNode;
            } catch (RuntimeException fallbackFailure) {
                // Last resort: a static masked snapshot.
                textNode.setText(displayText());
            }
            // Log first: a throwing degradation callback must not suppress the
            // primary diagnostic (or drop the original cause).
            logFallback("binding-install", ex.toString());
            degrade();
        }
    }

    private void degrade() {
        failed = true;
        if (onDegraded != null) {
            onDegraded.run();
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
