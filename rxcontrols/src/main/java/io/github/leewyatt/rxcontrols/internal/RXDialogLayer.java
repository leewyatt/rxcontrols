package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.RXDialog;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

/**
 * The layer of an {@code RXOverlayPane} that carries its open dialogs. Each
 * dialog fills the layer, so its skin can paint a scrim over the whole pane
 * and a centered card; the last child is the top-most dialog, and stacking,
 * focus and ESC are scoped to it by the dialogs' own skins.
 *
 * <p>The layer is driven by {@link RXDialog} (show attaches, hide-completed
 * detaches), not by the skin, which keeps scene-graph mounting out of the
 * skin.</p>
 */
public final class RXDialogLayer extends StackPane {

    /**
     * Creates an empty layer.
     */
    public RXDialogLayer() {
        // Region turns pickOnBounds on; the empty parts of the layer must let
        // the mouse through to the content below.
        setPickOnBounds(false);
    }

    /**
     * Puts the dialog on top of this layer's stack.
     *
     * @param dialog the dialog to show
     */
    public void attach(RXDialog<?> dialog) {
        // A previous show may have left the dialog in a layer (a re-show while
        // its close is still running, possibly in another pane): take it out of
        // there first, so that it is never added twice.
        detach(dialog);
        getChildren().add(dialog);
    }

    /**
     * Takes the dialog out of the layer it is in. Does nothing if it is in
     * none.
     *
     * @param dialog the dialog to detach
     */
    public static void detach(RXDialog<?> dialog) {
        if (!(dialog.getParent() instanceof RXDialogLayer layer)) {
            return;
        }
        ObservableList<Node> children = layer.getChildren();
        if (layer.getScene() != null) {
            children.remove(dialog);
            return;
        }
        // Off its scene, the layer may be leaving it right now, with JavaFX
        // walking this child list by index to tell each dialog. Removing one
        // here would shift the rest and make the walk skip the next dialog,
        // which would then never learn that it left the scene. An empty node
        // keeps the dialog's place instead, and is removed once that is over.
        Node placeholder = new Group();
        children.set(children.indexOf(dialog), placeholder);
        Platform.runLater(() -> children.remove(placeholder));
    }
}
