package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.CloseReason;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;

/**
 * Implemented by an {@link RXDialog} content node that wants a back-reference to
 * its hosting dialog. When such a node is set as a dialog's
 * {@link RXDialog#contentProperty() content}, the dialog injects itself — and
 * clears the reference when the node stops being its content — so the content can
 * drive the dialog (e.g. a header close button calling
 * {@link RXDialog#requestClose(ButtonType, CloseReason) requestClose}).
 *
 * <p>The interface is intentionally <strong>not</strong> a {@link Node}: a node opts
 * in by also implementing it ({@code class MyContent extends VBox implements
 * RXDialogAware}), so {@link RXDialog#setContent(Node)} keeps accepting any
 * {@code Node} and plain nodes work unchanged (they simply receive no injection).
 * {@link RXDialogContent} is a ready-made {@link javafx.scene.layout.Region} base
 * that supplies the property plumbing.</p>
 */
public interface RXDialogAware {

    /**
     * Sets (or, with {@code null}, clears) the hosting dialog. Called by
     * {@link RXDialog} when this node becomes — or stops being — its content; not
     * intended to be called by application code. This is host-controlled state: any
     * value an application sets is overwritten the next time the node is added to or
     * removed from a dialog's {@link RXDialog#contentProperty() content}.
     *
     * @param dialog the hosting dialog, or {@code null} when detached
     */
    void setDialog(RXDialog<?> dialog);

    /**
     * Returns the hosting dialog, or {@code null} when this node is not currently an
     * {@link RXDialog}'s content.
     *
     * @return the hosting dialog, or {@code null}
     */
    RXDialog<?> getDialog();

    /**
     * The hosting dialog as a read-only property, so the content can react when it is
     * attached to or detached from a dialog.
     *
     * @return the read-only dialog property
     */
    ReadOnlyObjectProperty<RXDialog<?>> dialogProperty();
}
