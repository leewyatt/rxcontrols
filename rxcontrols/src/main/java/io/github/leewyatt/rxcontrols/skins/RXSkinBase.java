package io.github.leewyatt.rxcontrols.skins;

import javafx.scene.control.Control;
import javafx.scene.control.SkinBase;

/**
 * Convenience base for skins that extend {@link SkinBase} directly. Embeds a
 * {@link SkinDisposer} and disposes it automatically before delegating to
 * {@code super.dispose()}. Subclasses override {@link #disposeSkin()} for
 * their own cleanup.
 *
 * <p>For skins that must extend a JavaFX subclass (e.g. {@code TextFieldSkin},
 * {@code PaginationSkin}), use {@link SkinDisposer} directly via composition
 * — Java's single-inheritance rules out a shared abstract base in that case.
 *
 * @param <C> the control type
 */
public abstract class RXSkinBase<C extends Control> extends SkinBase<C> {

    /**
     * Cleanup bag for resources {@code SkinBase} does not auto-manage
     * (binds, animations, transforms, custom listener objects).
     */
    protected final SkinDisposer disposer = new SkinDisposer();

    /**
     * Constructs the skin for the given control.
     *
     * @param control the control this skin is attached to
     */
    protected RXSkinBase(C control) {
        super(control);
    }

    /**
     * Runs all registered cleanup tasks via {@link SkinDisposer#dispose()},
     * then delegates to {@link SkinBase#dispose()}.
     */
    @Override
    public final void dispose() {
        RuntimeException error = null;
        try {
            disposeSkin();
        } catch (RuntimeException e) {
            error = e;
        } finally {
            try {
                disposer.dispose();
            } catch (RuntimeException e) {
                if (error == null) {
                    error = e;
                } else {
                    error.addSuppressed(e);
                }
            } finally {
                try {
                    super.dispose();
                } catch (RuntimeException e) {
                    if (error == null) {
                        error = e;
                    } else {
                        error.addSuppressed(e);
                    }
                }
            }
        }
        if (error != null) {
            throw error;
        }
    }

    /**
     * Releases resources owned directly by the subclass before registered
     * disposer tasks and {@link SkinBase} cleanup run. The default
     * implementation does nothing.
     */
    protected void disposeSkin() {
    }
}
