package io.github.leewyatt.rxcontrols.skins;

import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

/**
 * Drives the {@code :focus-shown} pseudo-class on a node: set while the node is
 * focused, unless that focus came from a pointer press and no key has been pressed
 * since. A user who just clicked a button knows where the focus is; a user on the
 * keyboard needs to see it. Focus from anything else (traversal keys, code, the
 * focus a window hands out when it opens) shows the indicator.
 *
 * <p>Pointer focus is tracked by focus ownership, not by {@code isFocused()}: when
 * the window deactivates, the node stays the scene's focus owner with
 * {@code focused == false}, and returning to the window must not reveal the
 * indicator on a node the user clicked.</p>
 *
 * <p>The tracker in use is kept in the node's properties. A replacing skin's tracker
 * is installed before the old skin is disposed, so it takes over the old one's state
 * and the old one leaves the pseudo-class alone when it is disposed.</p>
 *
 * <p>Not {@code :focus-visible}: from JavaFX 19 the platform writes that pseudo-class
 * on every node, and two writers would overwrite each other.</p>
 */
final class FocusShownTracker {

    static final PseudoClass FOCUS_SHOWN = PseudoClass.getPseudoClass("focus-shown");

    private static final Object PROPERTY_KEY = new Object();

    private final Node node;

    /** True when the focus the node holds, or will get on window activation, came from a pointer press. */
    private boolean pointerFocus;

    /** True from a press reaching the node until that press has been handled or released. */
    private boolean pressing;

    private FocusShownTracker(Node node) {
        this.node = node;
    }

    /**
     * Starts tracking the node; everything is unregistered when the disposer runs.
     * Call it after the node's behavior has added its mouse handlers, so the press
     * handler here sees the focus the behavior requested.
     *
     * @param node     the node that carries the pseudo-class
     * @param disposer the skin's disposer
     */
    static void install(Node node, SkinDisposer disposer) {
        FocusShownTracker tracker = new FocusShownTracker(node);
        if (node.getProperties().get(PROPERTY_KEY) instanceof FocusShownTracker previous) {
            tracker.pointerFocus = previous.pointerFocus;
        }
        node.getProperties().put(PROPERTY_KEY, tracker);
        disposer.registerEventFilter(node, MouseEvent.MOUSE_PRESSED, event -> tracker.pressing = true);
        disposer.registerEventHandler(node, MouseEvent.MOUSE_PRESSED, event -> tracker.onPressHandled());
        // A filter as well: a press that a child consumed never bubbles to the handler.
        disposer.registerEventFilter(node, MouseEvent.MOUSE_RELEASED, event -> tracker.pressing = false);
        disposer.registerEventFilter(node, KeyEvent.KEY_PRESSED, tracker::onKeyPressed);
        disposer.registerListener(node.focusedProperty(), tracker::onFocusChanged);
        disposer.registerDisposeTask(tracker::dispose);
        tracker.update();
    }

    private void onPressHandled() {
        pressing = false;
        // Ownership after the behavior ran, not isFocused(): a click into an inactive
        // window makes the node the owner before the window activation focuses it, and
        // a press that did not make it the owner (not focus traversable) must not mark
        // the next focus as pointer focus.
        pointerFocus = isFocusOwner();
        update();
    }

    private void onKeyPressed(KeyEvent event) {
        // Modifiers and shortcut chords (Cmd+C, Cmd+Tab) do not mean the user moved
        // to the keyboard.
        if (pointerFocus && !event.getCode().isModifierKey()
                && !event.isMetaDown() && !event.isControlDown() && !event.isAltDown()) {
            pointerFocus = false;
            update();
        }
    }

    private void onFocusChanged() {
        if (node.isFocused()) {
            if (pressing) {
                pointerFocus = true;
            }
        } else if (!isFocusOwner()) {
            // Focus left the node. A deactivating window keeps the owner, and that
            // pointer focus still holds when the window comes back.
            pointerFocus = false;
            pressing = false;
        }
        update();
    }

    private boolean isFocusOwner() {
        Scene scene = node.getScene();
        return scene != null && scene.getFocusOwner() == node;
    }

    private void update() {
        node.pseudoClassStateChanged(FOCUS_SHOWN, node.isFocused() && !pointerFocus);
    }

    private void dispose() {
        // A replacing skin's tracker has already taken over; leave its state alone.
        if (node.getProperties().get(PROPERTY_KEY) == this) {
            node.getProperties().remove(PROPERTY_KEY);
            node.pseudoClassStateChanged(FOCUS_SHOWN, false);
        }
    }
}
