package io.github.leewyatt.rxcontrols;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.layout.Region;

/**
 * Convenience base for {@link RXDialog} content that wants a back-reference to its
 * hosting dialog. Extends {@link Region} and implements {@link RXDialogAware},
 * supplying the {@link #dialogProperty() dialog} property plumbing; subclasses add
 * their own children and layout and read {@link #getDialog()} to drive the dialog.
 * Content that must extend a different node type can implement {@link RXDialogAware}
 * directly instead.
 *
 * @see RXDialogLayout
 */
public class RXDialogContent extends Region implements RXDialogAware {

    private final ReadOnlyObjectWrapper<RXDialog<?>> dialog = new ReadOnlyObjectWrapper<>(this, "dialog");

    /**
     * {@inheritDoc}
     */
    @Override
    public final void setDialog(RXDialog<?> value) {
        dialog.set(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final RXDialog<?> getDialog() {
        return dialog.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ReadOnlyObjectProperty<RXDialog<?>> dialogProperty() {
        return dialog.getReadOnlyProperty();
    }
}
