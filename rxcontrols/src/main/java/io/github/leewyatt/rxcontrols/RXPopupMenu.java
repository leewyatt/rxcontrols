package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXMenuEvent;
import io.github.leewyatt.rxcontrols.internal.popup.RXPopupSupport;
import io.github.leewyatt.rxcontrols.internal.popup.RXPopupWidthMode;
import io.github.leewyatt.rxcontrols.skins.RXMenuListSkin;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.input.KeyEvent;

/**
 * A popup command menu: an {@link RXMenuList} wrapped in an anchored, animated
 * floating surface. It opens against a node ({@link #show(Node)}) or at a screen
 * point ({@link #showAt(Node, double, double)} for context menus), restores focus
 * to the invoker on close, reports a precise {@link CloseReason}, and fires
 * {@code onShowing}/{@code onShown}/{@code onHiding}/{@code onHidden} lifecycle
 * callbacks.
 *
 * <p>This is a plain composition object — <b>not</b> a {@code Control} / {@code Node}
 * / {@code EventTarget}. It holds one internal {@link RXPopupSupport} (borrowing
 * its flip / shift / collision / multi-screen / HiDPI / RTL positioning) and one
 * {@link RXMenuList} (the keyboard / focus / rendering content). Lifecycle
 * callbacks are invoked directly, not dispatched through an event chain.
 *
 * <p>Activation follows <b>close-then-fire</b>: a (non-{@code keepOpen}) item first
 * closes the menu with {@link CloseReason#ACTION}, then its {@code onAction} fires,
 * so a handler that opens another menu or throws cannot leave this one stuck open.
 *
 * <p>Call {@link #dispose()} when done (an owning skin does this; a standalone
 * user must too) to release the support, stop animations, and unwire listeners.
 *
 * <p>Not thread-safe; use on the JavaFX Application Thread.
 */
public class RXPopupMenu {

    // Small gap between the anchor and the menu surface (Material dropdown).
    private static final double MENU_GAP = 4.0;

    /**
     * Why an {@link RXPopupMenu} closed. All values are produced:
     * {@link #ACTION} / {@link #ESCAPE} / {@link #TAB} / {@link #PROGRAMMATIC}
     * from their explicit paths, {@link #OUTSIDE} vs {@link #OWNER_DETACHED}
     * distinguished on auto-hide by whether the invoker is still realized, and
     * {@link #ACCELERATOR} when a registered accelerator activates an item.
     */
    public enum CloseReason {
        /** A command item was activated (close-then-fire). */
        ACTION,
        /** The Escape key was pressed. */
        ESCAPE,
        /** Outside interaction auto-hid the menu while the invoker was still realized. */
        OUTSIDE,
        /** The Tab key was pressed. */
        TAB,
        /** A programmatic {@link RXPopupMenu#hide()} call. */
        PROGRAMMATIC,
        /** The owner left the scene / its window hid. */
        OWNER_DETACHED,
        /** A registered accelerator activated an item. */
        ACCELERATOR
    }

    // ==================== State ====================

    private final RXMenuList menuList = new RXMenuList();
    private final RXPopupSupport support = new RXPopupSupport(menuList);
    private final ReadOnlyBooleanWrapper showing = new ReadOnlyBooleanWrapper(this, "showing", false);
    private final EventHandler<KeyEvent> menuKeyFilter = this::onMenuKeyPressed;
    // While shown, watch the invoker's (effective) disabled state so a "disabled
    // owner -> no menu" also closes a menu already open when the owner is disabled.
    private final InvalidationListener invokerDisabledListener = obs -> closeIfInvokerDisabled();

    private Node invoker;
    private CloseReason pendingReason;
    private boolean shownFired;
    private boolean popupHadFocus;
    private boolean disposed;

    // ==================== Constructor ====================

    /**
     * Creates an empty popup menu.
     */
    public RXPopupMenu() {
        support.setPlacement(RXPlacement.BOTTOM_START);
        support.setWidthMode(RXPopupWidthMode.PREF_CONTENT);
        support.setPopupStyleClass("rx-menu-popup");
        support.setOffset(getOffsetX(), getOffsetY());
        // Escape is handled precisely by this menu (with a CloseReason), not by the
        // popup window's built-in hide-on-escape (which would give no reason).
        support.setHideOnEscape(false);
        support.setOnHidden(this::onSupportHidden);
        // close-then-fire: a non-keepOpen item closes first (CloseReason.ACTION),
        // then fires; a keepOpen item fires without closing. The item's own
        // onAction exception propagates but cannot block the already-done close.
        menuList.setCommandActivator(item -> {
            if (!item.isKeepOpen()) {
                hide(CloseReason.ACTION);
            }
            item.fire();
        });
        menuList.addEventFilter(KeyEvent.KEY_PRESSED, menuKeyFilter);
        // Let the CONTEXT_MENU surface report its popup showing state and owner to
        // assistive technology (mirrors the platform ContextMenuContent surface).
        menuList.setPopupAccessibility(showing::get, () -> invoker);
    }

    // ==================== Items ====================

    /**
     * The menu items, delegated to the internal {@link RXMenuList}.
     *
     * @return the modifiable item list
     */
    public final ObservableList<RXMenuItem> getItems() {
        return menuList.getItems();
    }

    /**
     * Returns the internal content list for advanced configuration (initial focus,
     * animation, ripple styling). Do not add it to a scene graph — it is owned by
     * this popup.
     *
     * @return the internal menu list
     */
    public final RXMenuList getMenuList() {
        return menuList;
    }

    // ==================== Offset ====================

    private final DoubleProperty offsetX = new SimpleDoubleProperty(this, "offsetX", 0.0);
    private final DoubleProperty offsetY = new SimpleDoubleProperty(this, "offsetY", MENU_GAP);

    {
        offsetX.addListener(o -> support.setOffset(getOffsetX(), getOffsetY()));
        offsetY.addListener(o -> support.setOffset(getOffsetX(), getOffsetY()));
    }

    /**
     * Horizontal nudge (vertical placement family) or gap (side family) from the
     * anchor.
     *
     * @return the offset-x property
     */
    public final DoubleProperty offsetXProperty() {
        return offsetX;
    }

    /**
     * Returns the horizontal offset.
     *
     * @return the horizontal offset
     */
    public final double getOffsetX() {
        return offsetX.get();
    }

    /**
     * Sets the horizontal offset.
     *
     * @param value the horizontal offset
     */
    public final void setOffsetX(double value) {
        offsetX.set(value);
    }

    /**
     * Vertical gap (vertical placement family) from the anchor. Default
     * {@value #MENU_GAP}.
     *
     * @return the offset-y property
     */
    public final DoubleProperty offsetYProperty() {
        return offsetY;
    }

    /**
     * Returns the vertical offset.
     *
     * @return the vertical offset
     */
    public final double getOffsetY() {
        return offsetY.get();
    }

    /**
     * Sets the vertical offset.
     *
     * @param value the vertical offset
     */
    public final void setOffsetY(double value) {
        offsetY.set(value);
    }

    // ==================== Show / hide ====================

    /**
     * Shows the menu below the anchor ({@code BOTTOM_START}).
     *
     * @param anchor the node to anchor to
     */
    public void show(Node anchor) {
        show(anchor, RXPlacement.BOTTOM_START);
    }

    /**
     * Shows the menu against the anchor with the given placement. A no-op if the
     * anchor is {@code null}, disabled, or not in a showing window, or if the menu
     * has no focusable item (all separators / headers, or empty).
     *
     * @param anchor    the node to anchor to
     * @param placement the preferred placement
     */
    public void show(Node anchor, RXPlacement placement) {
        // Guard (a): detached-owner pre-check — no-op without firing onShowing or
        // touching the support (aligns with "owner has no scene / disabled owner ->
        // no-op").
        if (disposed || !isRealized(anchor) || anchor.isDisabled() || !hasFocusableItem()) {
            return;
        }
        this.invoker = anchor;
        support.setPlacement(placement);
        fire(getOnShowing(), RXMenuEvent.MENU_SHOWING, null);
        support.show(anchor);
        confirmShown();
    }

    /**
     * Shows the menu at a screen point owned by {@code owner} (a context menu). A
     * no-op under the same conditions as {@link #show(Node, RXPlacement)}.
     *
     * @param owner   the node whose window hosts the menu and receives restored focus
     * @param screenX the anchor x in screen coordinates
     * @param screenY the anchor y in screen coordinates
     */
    public void showAt(Node owner, double screenX, double screenY) {
        if (disposed || !isRealized(owner) || owner.isDisabled() || !hasFocusableItem()) {
            return;
        }
        this.invoker = owner;
        // Pin a stable placement so the menu opens below-right of the cursor point
        // regardless of any placement a prior anchored show() left on the support.
        support.setPlacement(RXPlacement.BOTTOM_START);
        fire(getOnShowing(), RXMenuEvent.MENU_SHOWING, null);
        support.show(owner, screenX, screenY);
        confirmShown();
    }

    // Guard (b): only mark shown / fire SHOWN once the support actually shows, so a
    // failed/racing show does not surface a phantom SHOWN or (later) HIDDEN.
    private void confirmShown() {
        if (!support.isShowing()) {
            return;
        }
        showing.set(true);
        shownFired = true;
        popupHadFocus = true;
        if (invoker != null) {
            // Re-add defensively (single registration even on a re-entrant show).
            invoker.disabledProperty().removeListener(invokerDisabledListener);
            invoker.disabledProperty().addListener(invokerDisabledListener);
        }
        menuList.notifyAccessibleAttributeChanged(AccessibleAttribute.VISIBLE);
        // Force the skin so the entrance pivot uses real dimensions this frame.
        menuList.applyCss();
        RXMenuListSkin skin = menuSkin();
        if (skin != null) {
            skin.playEntrance(support.isOpenAbove());
        }
        // Focus the initial item after the popup window realizes and takes focus.
        Platform.runLater(this::focusInitialSafe);
        fire(getOnShown(), RXMenuEvent.MENU_SHOWN, null);
    }

    /**
     * Toggles the menu against the anchor: hides if showing, shows otherwise.
     *
     * @param anchor the node to anchor to
     */
    public void toggle(Node anchor) {
        if (isShowing()) {
            hide(CloseReason.PROGRAMMATIC);
        } else {
            show(anchor);
        }
    }

    /**
     * Hides the menu with {@link CloseReason#PROGRAMMATIC}.
     */
    public void hide() {
        hide(CloseReason.PROGRAMMATIC);
    }

    /**
     * Hides the menu with the given reason. A no-op if not showing. Firing
     * {@code onHiding} and consuming that event vetoes the close (only on this
     * explicit path; auto-hide / owner-detach cannot be vetoed).
     *
     * @param reason why the menu is closing
     */
    public void hide(CloseReason reason) {
        if (!showing.get()) {
            return;
        }
        RXMenuEvent hiding = fire(getOnHiding(), RXMenuEvent.MENU_HIDING, reason);
        if (hiding.isConsumed()) {
            return;
        }
        this.pendingReason = reason;
        support.hide();
    }

    // Every hide path (explicit hide, outside auto-hide, owner detach) converges
    // here, so cleanup + focus-restore live in exactly one place.
    private void onSupportHidden() {
        boolean hadFocus = popupHadFocus;
        if (invoker != null) {
            invoker.disabledProperty().removeListener(invokerDisabledListener);
        }
        RXMenuListSkin skin = menuSkin();
        if (skin != null) {
            skin.stopEntrance();
        }
        if (!shownFired) {
            // Failed / never-shown open (showImpl raced to notifyHidden): reset quietly.
            pendingReason = null;
            showing.set(false);
            return;
        }
        // No explicit reason means the support auto-hid: distinguish the owner
        // leaving the scene / its window hiding (OWNER_DETACHED) from an ordinary
        // outside interaction (OUTSIDE) by whether the invoker is still realized.
        CloseReason reason;
        if (pendingReason != null) {
            reason = pendingReason;
        } else {
            reason = isRealized(invoker) ? CloseReason.OUTSIDE : CloseReason.OWNER_DETACHED;
        }
        pendingReason = null;
        shownFired = false;
        popupHadFocus = false;
        showing.set(false);
        menuList.notifyAccessibleAttributeChanged(AccessibleAttribute.VISIBLE);
        // Restore focus to the invoker except when interaction moved elsewhere
        // (OUTSIDE) or the invoker is gone (OWNER_DETACHED).
        if (hadFocus && reason != CloseReason.OUTSIDE && reason != CloseReason.OWNER_DETACHED
                && invoker != null) {
            invoker.requestFocus();
        }
        fire(getOnHidden(), RXMenuEvent.MENU_HIDDEN, reason);
    }

    // A menu must not stay open on a disabled owner (anchor §5). This covers a
    // standalone popup as well as a button-hosted one (invoker = the button), so
    // the owning skin does not need its own disabled watch.
    private void closeIfInvokerDisabled() {
        if (showing.get() && invoker != null && invoker.isDisabled()) {
            hide(CloseReason.PROGRAMMATIC);
        }
    }

    private void onMenuKeyPressed(KeyEvent event) {
        switch (event.getCode()) {
            case ESCAPE -> {
                event.consume();
                hide(CloseReason.ESCAPE);
            }
            case TAB -> {
                // Tab closes the menu and returns focus to the invoker; menu items
                // are not in the Tab traversal chain (RXMenuList design).
                event.consume();
                hide(CloseReason.TAB);
            }
            default -> {
            }
        }
    }

    private void focusInitialSafe() {
        if (!showing.get()) {
            return;
        }
        RXMenuListSkin skin = menuSkin();
        if (skin != null) {
            skin.focusInitial();
        }
    }

    /**
     * Returns whether the menu is showing.
     *
     * @return {@code true} if showing
     */
    public boolean isShowing() {
        return showing.get();
    }

    /**
     * Returns the showing state as a read-only property.
     *
     * @return the read-only showing property
     */
    public ReadOnlyBooleanProperty showingProperty() {
        return showing.getReadOnlyProperty();
    }

    /**
     * Releases the internal support and menu list, stops the entrance animation,
     * and unwires listeners. Idempotent.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        RXMenuListSkin skin = menuSkin();
        if (skin != null) {
            skin.stopEntrance();
        }
        menuList.removeEventFilter(KeyEvent.KEY_PRESSED, menuKeyFilter);
        menuList.setCommandActivator(null);
        // Clear the internal list so the skin tears down every per-item cell (its
        // listeners live on the shared RXMenuItem value objects, which the invoker
        // still references — leaving them attached would pin the whole menu view).
        menuList.getItems().clear();
        support.dispose();
        // Drop the anchor reference (and its disabled watch) so a retained (but
        // disposed) menu does not pin the invoker node.
        if (invoker != null) {
            invoker.disabledProperty().removeListener(invokerDisabledListener);
        }
        invoker = null;
        shownFired = false;
        popupHadFocus = false;
        showing.set(false);
    }

    // ==================== Lifecycle callbacks ====================

    private final ObjectProperty<EventHandler<RXMenuEvent>> onShowing =
            new SimpleObjectProperty<>(this, "onShowing");

    /**
     * Handler fired just before the menu shows, e.g. to refresh item state. It runs
     * only when the menu will actually open — the anchor is realized and at least one
     * focusable item is already present — so it cannot populate an otherwise-empty menu.
     *
     * @return the on-showing property
     */
    public final ObjectProperty<EventHandler<RXMenuEvent>> onShowingProperty() {
        return onShowing;
    }

    /**
     * Returns the on-showing handler.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<RXMenuEvent> getOnShowing() {
        return onShowing.get();
    }

    /**
     * Sets the on-showing handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnShowing(EventHandler<RXMenuEvent> value) {
        onShowing.set(value);
    }

    private final ObjectProperty<EventHandler<RXMenuEvent>> onShown =
            new SimpleObjectProperty<>(this, "onShown");

    /**
     * Handler fired once the menu is fully shown.
     *
     * @return the on-shown property
     */
    public final ObjectProperty<EventHandler<RXMenuEvent>> onShownProperty() {
        return onShown;
    }

    /**
     * Returns the on-shown handler.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<RXMenuEvent> getOnShown() {
        return onShown.get();
    }

    /**
     * Sets the on-shown handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnShown(EventHandler<RXMenuEvent> value) {
        onShown.set(value);
    }

    private final ObjectProperty<EventHandler<RXMenuEvent>> onHiding =
            new SimpleObjectProperty<>(this, "onHiding");

    /**
     * Handler fired before the menu hides; {@link RXMenuEvent#consume()} vetoes the
     * close (explicit-hide path only).
     *
     * @return the on-hiding property
     */
    public final ObjectProperty<EventHandler<RXMenuEvent>> onHidingProperty() {
        return onHiding;
    }

    /**
     * Returns the on-hiding handler.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<RXMenuEvent> getOnHiding() {
        return onHiding.get();
    }

    /**
     * Sets the on-hiding handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnHiding(EventHandler<RXMenuEvent> value) {
        onHiding.set(value);
    }

    private final ObjectProperty<EventHandler<RXMenuEvent>> onHidden =
            new SimpleObjectProperty<>(this, "onHidden");

    /**
     * Handler fired once the menu has hidden, carrying the close reason.
     *
     * @return the on-hidden property
     */
    public final ObjectProperty<EventHandler<RXMenuEvent>> onHiddenProperty() {
        return onHidden;
    }

    /**
     * Returns the on-hidden handler.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<RXMenuEvent> getOnHidden() {
        return onHidden.get();
    }

    /**
     * Sets the on-hidden handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnHidden(EventHandler<RXMenuEvent> value) {
        onHidden.set(value);
    }

    // ==================== Internal ====================

    private RXMenuEvent fire(EventHandler<RXMenuEvent> handler, EventType<RXMenuEvent> type,
                             CloseReason reason) {
        RXMenuEvent event = new RXMenuEvent(type, reason);
        if (handler != null) {
            handler.handle(event);
        }
        return event;
    }

    private boolean hasFocusableItem() {
        // Mirror the skin's isNavigable: a command item counts when it is focusable,
        // or when it is disabled but the list runs in APG (disabledItemsFocusable) mode.
        boolean apg = menuList.isDisabledItemsFocusable();
        for (RXMenuItem item : menuList.getItems()) {
            if (item instanceof RXMenuSeparator || item instanceof RXMenuHeader) {
                continue;
            }
            if (item.isFocusable() || (item.isDisable() && apg)) {
                return true;
            }
        }
        return false;
    }

    private RXMenuListSkin menuSkin() {
        Skin<?> skin = menuList.getSkin();
        return skin instanceof RXMenuListSkin ? (RXMenuListSkin) skin : null;
    }

    private static boolean isRealized(Node node) {
        return node != null
                && node.getScene() != null
                && node.getScene().getWindow() != null
                && node.getScene().getWindow().isShowing();
    }
}
