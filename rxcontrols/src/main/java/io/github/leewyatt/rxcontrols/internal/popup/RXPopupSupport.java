package io.github.leewyatt.rxcontrols.internal.popup;

import io.github.leewyatt.rxcontrols.skins.SkinDisposer;
import io.github.leewyatt.rxcontrols.utils.RXTreeShowingProperty;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.util.List;
import java.util.Objects;

/**
 * Composition-based positioning and lifecycle helper for an anchored popup. Wraps
 * a single {@link PopupControl} whose content is a fixed {@link Region}, anchors
 * it to a (rebindable) {@link Node}, and keeps it glued on-screen as the anchor
 * moves, resizes, or its window moves. Content-agnostic: the data model lives in
 * consumers (e.g. a cascader view or a suggestion list).
 *
 * <p>Extracted from {@code RXCascaderSkin} so the cascader and future anchored
 * popups (autocomplete, tag input, pickers) share one positioning + lifecycle
 * implementation. Positioning uses only public {@link Screen} API and the pure
 * {@link RXPopupGeometry} resolver; it never touches {@code com.sun.*} or popup
 * skin internals.
 *
 * <h2>Lifecycle</h2>
 * The support keeps its own logical {@link #showingProperty() showing} state,
 * distinct from {@code PopupWindow.showing}: during an anchor rebind it hides and
 * re-shows the underlying window while reporting a stable {@code true}. Two
 * {@link SkinDisposer}s are held because {@code SkinDisposer} has no per-item
 * unregister: {@code lifecycleDisposer} owns anchor-independent registrations,
 * and {@code anchorDisposer} owns everything bound to the current anchor and is
 * rebuilt on rebind.
 *
 * <h2>Sizing</h2>
 * The popup window follows its scene root's <em>preferred</em> size
 * ({@code PopupControl}'s own min/pref/max properties have no layout consumer),
 * so the resolved width and max-height are applied to an internal shell pane
 * wrapped around the content — never to the content itself, whose min/pref/max
 * stay under user / CSS control.
 *
 * <h2>Content bounds contract</h2>
 * {@code PopupWindow} natively tracks the scene root's {@code boundsInLocal}:
 * every invalidation immediately resizes and repositions the OS window. The
 * content subtree must therefore never leak child bounds outside itself, even
 * transiently — an un-laid-out skin created mid-event (a fresh {@code ListView}
 * exposes its placeholder at negative y and default-sized scroll bars), an
 * unbounded halo, or an animated transform all show up as a visible
 * native-window jump. Consumers that rebuild content mid-event must clip the
 * offending node or settle it ({@code applyCss()} + {@code layout()}) before
 * returning to the event loop. Bounds-affecting animations on the content are
 * legitimate only when the window following them is the intended effect (the
 * suggestion popup's unfold entrance relies on exactly this tracking).
 *
 * <p>Not thread-safe; use on the JavaFX Application Thread.
 */
public final class RXPopupSupport {

    // ==================== Constants ====================

    private static final RXPlacement DEFAULT_PLACEMENT = RXPlacement.BOTTOM_START;
    private static final RXPopupWidthMode DEFAULT_WIDTH_MODE = RXPopupWidthMode.PREFER_ANCHOR_WIDTH;

    // ==================== Fields ====================

    private final Region content;
    // Resolved geometry lands on this wrapper (see the class-level Sizing note).
    private final StackPane shell;
    private final PopupControl popup = new PopupControl();
    private final EventHandler<WindowEvent> popupHiddenHandler = this::handlePopupHidden;

    /** Anchor-independent cleanup (WINDOW_HIDDEN handler, popup skin, content-size listeners). */
    private final SkinDisposer lifecycleDisposer = new SkinDisposer();
    /** Cleanup bound to the current anchor; rebuilt on each rebind. */
    private SkinDisposer anchorDisposer;

    private Node anchor;
    // Window-move reposition is tracked separately from the anchor listeners: the
    // anchor node's window can change without an anchor rebind, and SkinDisposer has
    // no per-item unregister, so a stable listener + the tracked window are used.
    private final InvalidationListener repositionListener = observable -> requestReposition();
    private Window listenerWindow;

    private RXPlacement placement = DEFAULT_PLACEMENT;
    private RXPopupWidthMode widthMode = DEFAULT_WIDTH_MODE;
    private double offsetX;
    private double offsetY;

    private Runnable onHidden;
    private boolean suppressOnHidden;
    private boolean disposed;

    private final ReadOnlyBooleanWrapper showing = new ReadOnlyBooleanWrapper(this, "showing", false);
    private final ReadOnlyBooleanWrapper openAbove = new ReadOnlyBooleanWrapper(this, "openAbove", false);

    // ==================== Constructor ====================

    /**
     * Creates a popup support hosting the given content region.
     *
     * @param content the popup content; must not be {@code null}
     * @throws NullPointerException if {@code content} is {@code null}
     */
    public RXPopupSupport(Region content) {
        this.content = Objects.requireNonNull(content, "content");
        this.shell = new StackPane(content);
        // Backstop only: reconfigure() already clamps the content into the anchor
        // node's screen visual bounds using preferred sizes; the framework autofix
        // re-clamps with the window's actual bounds against the anchor point's
        // screen. On the normal path it is a no-op — it steps in when the actual
        // size differs from the measured prefs (or a fullscreen stage changes the
        // screen-bounds source). Its anchor rewrite does not feed back into
        // reconfigure, so the two clamps cannot oscillate.
        popup.setAutoFix(true);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setConsumeAutoHidingEvents(false);
        popup.setSkin(new ContentPopupSkin(popup, shell));
        popup.addEventHandler(WindowEvent.WINDOW_HIDDEN, popupHiddenHandler);
        lifecycleDisposer.registerDisposeTask(
                () -> popup.removeEventHandler(WindowEvent.WINDOW_HIDDEN, popupHiddenHandler));
        lifecycleDisposer.registerDisposeTask(() -> popup.setSkin(null));
        lifecycleDisposer.registerDisposeTask(() -> shell.getChildren().remove(content));
        // The popup tracks the content's size; a size change means re-clamp / re-flip.
        lifecycleDisposer.registerListener(content.widthProperty(), this::requestReposition);
        lifecycleDisposer.registerListener(content.heightProperty(), this::requestReposition);
    }

    // ==================== Lifecycle ====================

    /**
     * Binds the anchor (installing reposition + auto-close tracking) and shows the
     * popup. When already showing on a different anchor this behaves like
     * {@link #setAnchor(Node)} (hide + re-show with the new owner). A {@code null}
     * anchor is ignored.
     *
     * @param anchor the node to anchor to
     */
    public void show(Node anchor) {
        if (disposed || anchor == null) {
            return;
        }
        if (anchor != this.anchor && showing.get() && popup.isShowing()) {
            // The framework ownerNode (CSS parent chain, auto-hide owner exemption,
            // tree-showing tracking) has no public setter: migrating anchors while
            // showing must go through the rebind's hide + re-show.
            setAnchor(anchor);
            return;
        }
        bindAnchor(anchor);
        showInternal();
    }

    /**
     * Rebinds to a new anchor. A move of the same node is handled by the
     * reposition listeners and is not a rebind. Rebinding a different node while
     * showing hides and re-shows the popup with the new owner (the framework's
     * {@code ownerNode} has no public setter and drives the CSS parent chain and
     * the auto-hide owner exemption), keeping logical {@code showing} true across
     * the transition.
     *
     * @param newAnchor the new anchor node
     */
    public void setAnchor(Node newAnchor) {
        if (disposed || newAnchor == anchor) {
            return;
        }
        boolean wasShowing = showing.get() && popup.isShowing();
        if (wasShowing) {
            suppressOnHidden = true;
            try {
                popup.hide();
            } finally {
                // A hide listener that throws must not leave the flag stuck,
                // which would silently swallow every future onHidden.
                suppressOnHidden = false;
            }
        }
        bindAnchor(newAnchor);
        if (wasShowing) {
            showInternal();
        }
    }

    /**
     * Hides the popup. Logical {@code showing} becomes {@code false} and, unless
     * suppressed, the {@code onHidden} callback runs via the window's
     * {@code WINDOW_HIDDEN} event.
     */
    public void hide() {
        if (disposed) {
            return;
        }
        showing.set(false);
        if (popup.isShowing()) {
            popup.hide();
        }
    }

    /**
     * Returns the logical showing state.
     *
     * @return {@code true} if the popup is logically showing
     */
    public boolean isShowing() {
        return showing.get();
    }

    /**
     * Returns the logical showing state as a read-only property. This is the
     * support's own state, not a direct mirror of {@code PopupWindow.showing};
     * an anchor rebind does not surface a transient {@code false}.
     *
     * @return the read-only showing property
     */
    public ReadOnlyBooleanProperty showingProperty() {
        return showing.getReadOnlyProperty();
    }

    /**
     * Recomputes the popup position and size for the current anchor. A no-op when
     * not showing.
     */
    public void requestReposition() {
        reconfigure();
    }

    /**
     * Returns whether the last resolved geometry placed a vertical-family popup
     * above the anchor (the flip case); {@code false} for below or the side
     * family. Consumers use it for direction-aware visuals (e.g. the suggestion
     * popup's entrance pivot). Updated on every reposition; keeps its last value
     * while hidden.
     *
     * @return {@code true} when the popup opens above the anchor
     */
    public boolean isOpenAbove() {
        return openAbove.get();
    }

    /**
     * Returns the {@link #isOpenAbove() open-above} state as a read-only property.
     *
     * @return the read-only open-above property
     */
    public ReadOnlyBooleanProperty openAboveProperty() {
        return openAbove.getReadOnlyProperty();
    }

    /**
     * Releases all resources. Unbinds the hidden callback first so hiding the
     * window does not call back into the host, then hides the popup, then disposes
     * the anchor and lifecycle registrations (the latter clears the popup skin).
     *
     * <p>The logical {@link #showingProperty() showing} state is deliberately
     * <em>not</em> flipped to {@code false}: a disposed support fires no further
     * callbacks, and a host being replaced (skin swap) keeps its control-level
     * showing state so the next skin can re-open the popup from it. A host that
     * mirrors this property into its own state must reset that mirror itself
     * (see {@code RXChipInputSkin}).
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        onHidden = null;
        suppressOnHidden = true;
        if (popup.isShowing()) {
            popup.hide();
        }
        if (anchorDisposer != null) {
            anchorDisposer.dispose();
            anchorDisposer = null;
        }
        detachWindowListeners();
        lifecycleDisposer.dispose();
        anchor = null;
    }

    // ==================== Positioning config ====================

    /**
     * Sets the preferred placement (default {@code BOTTOM_START}). {@code null}
     * restores the default.
     *
     * @param placement the preferred placement
     */
    public void setPlacement(RXPlacement placement) {
        this.placement = (placement == null) ? DEFAULT_PLACEMENT : placement;
        requestReposition();
    }

    /**
     * Sets the gap from the anchor. For the vertical placement family the gap is
     * {@code offsetY} (with {@code offsetX} a horizontal nudge); for the side
     * family the roles swap.
     *
     * @param offsetX horizontal offset
     * @param offsetY vertical offset
     */
    public void setOffset(double offsetX, double offsetY) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        requestReposition();
    }

    /**
     * Sets the width strategy (default {@code PREFER_ANCHOR_WIDTH}). {@code null}
     * restores the default.
     *
     * @param mode the width mode
     */
    public void setWidthMode(RXPopupWidthMode mode) {
        this.widthMode = (mode == null) ? DEFAULT_WIDTH_MODE : mode;
        requestReposition();
    }

    // ==================== Close semantics ====================

    /**
     * Sets whether the popup auto-hides on outside interaction (default
     * {@code true}).
     *
     * @param value auto-hide flag
     */
    public void setAutoHide(boolean value) {
        popup.setAutoHide(value);
    }

    /**
     * Sets whether Escape hides the popup (default {@code true}).
     *
     * @param value hide-on-escape flag
     */
    public void setHideOnEscape(boolean value) {
        popup.setHideOnEscape(value);
    }

    /**
     * Sets whether auto-hiding events are consumed (default {@code false}, so the
     * closing click still reaches the underlying content, matching combo box /
     * context menu behavior).
     *
     * @param value consume flag
     */
    public void setConsumeAutoHidingEvents(boolean value) {
        popup.setConsumeAutoHidingEvents(value);
    }

    /**
     * Sets the callback invoked on every hide path (auto-hide, Escape, programmatic
     * hide, owner detach), driven by the window's {@code WINDOW_HIDDEN} event. The
     * host uses it to pull its own showing state back.
     *
     * @param callback the hidden callback, or {@code null}
     */
    public void setOnHidden(Runnable callback) {
        this.onHidden = callback;
    }

    // ==================== Appearance ====================

    /**
     * Adds a style class to the transparent popup shell (e.g.
     * {@code "rx-suggestion-popup"}). The shell is intentionally token-less; the
     * content carries theming.
     *
     * @param styleClass the shell style class; {@code null} is ignored
     */
    public void setPopupStyleClass(String styleClass) {
        if (styleClass != null && !popup.getStyleClass().contains(styleClass)) {
            popup.getStyleClass().add(styleClass);
        }
    }

    // ==================== Internal ====================

    private void bindAnchor(Node newAnchor) {
        if (newAnchor == anchor && anchorDisposer != null) {
            return;
        }
        if (anchorDisposer != null) {
            anchorDisposer.dispose();
        }
        detachWindowListeners();
        anchorDisposer = new SkinDisposer();
        anchor = newAnchor;
        if (newAnchor == null) {
            return;
        }
        // Keep the popup glued to the anchor as it moves / resizes within its scene
        // (boundsInParent covers own position + size; localToSceneTransform covers
        // ancestor-driven moves). Window x/y/size are tracked separately at show time.
        anchorDisposer.registerListener(newAnchor.boundsInParentProperty(), this::requestReposition);
        anchorDisposer.registerListener(newAnchor.localToSceneTransformProperty(), this::requestReposition);
        // Auto-close when the anchor leaves the scene / its window hides. Explicit
        // ownership (new + dispose), not the shared RXTreeShowingProperty.of(node).
        RXTreeShowingProperty tracker = new RXTreeShowingProperty(newAnchor);
        anchorDisposer.registerListener(tracker, () -> {
            if (!tracker.get()) {
                hide();
            }
        });
        anchorDisposer.registerDisposeTask(tracker::dispose);
    }

    private void showInternal() {
        if (anchor == null) {
            // Reached when a rebind cleared the anchor while showing (the window
            // hide was suppressed): roll the host back explicitly so logical
            // showing and onHidden stay consistent with the other failure paths.
            notifyHidden();
            return;
        }
        Scene scene = anchor.getScene();
        // The isShowing check matters: PopupWindow.showImpl silently refuses to
        // show against a non-showing root window, which would leave logical
        // showing stuck at true (localToScreen is non-null there, just NaN).
        if (scene == null || scene.getWindow() == null || !scene.getWindow().isShowing()
                || anchor.localToScreen(anchor.getBoundsInLocal()) == null) {
            // Cannot show: roll the host back via onHidden.
            notifyHidden();
            return;
        }
        // Flip logical showing on only once the show will actually proceed, so a
        // failed show does not surface a transient true->false on showingProperty().
        showing.set(true);
        // Mirror the anchor's effective orientation so START/END and content flip under RTL.
        content.setNodeOrientation(anchor.getEffectiveNodeOrientation());
        if (!popup.isShowing()) {
            Bounds anchorScreen = anchor.localToScreen(anchor.getBoundsInLocal());
            // Provisional anchor below the node; reconfigure once the popup is measured.
            popup.show(anchor, anchorScreen.getMinX(), anchorScreen.getMaxY());
        }
        installWindowListeners(scene.getWindow());
        reconfigure();
    }

    private void reconfigure() {
        if (disposed || !showing.get() || anchor == null || !popup.isShowing()) {
            return;
        }
        Bounds anchorScreen = anchor.localToScreen(anchor.getBoundsInLocal());
        if (anchorScreen == null) {
            return;
        }
        Screen screen = screenFor(anchorScreen);
        Rectangle2D visual = screen.getVisualBounds();
        double naturalW = content.prefWidth(-1);
        // Measure height at the width the popup will actually take, so width-dependent
        // (wrapping) content still yields a correct flip / max-height decision.
        double resolvedWidth = RXPopupGeometry.resolveWidth(widthMode, anchorScreen.getWidth(), naturalW);
        double naturalH = content.prefHeight(resolvedWidth);
        boolean rtl = anchor.getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;
        RXPopupGeometry.Result result = RXPopupGeometry.resolve(
                anchorScreen.getMinX(), anchorScreen.getMinY(),
                anchorScreen.getWidth(), anchorScreen.getHeight(),
                naturalW, naturalH,
                visual.getMinX(), visual.getMinY(), visual.getMaxX(), visual.getMaxY(),
                placement, widthMode, offsetX, offsetY, rtl,
                screen.getOutputScaleX(), screen.getOutputScaleY());
        applyWidth(result.width);
        // The height cap flows through the shell's pref height; the window follows
        // it. RXPopupGeometry.USE_COMPUTED_SIZE == Region.USE_COMPUTED_SIZE (-1),
        // so an uncapped result clears the override.
        shell.setPrefHeight(result.maxHeight);
        popup.setAnchorX(result.anchorX);
        popup.setAnchorY(result.anchorY);
        openAbove.set(placement.isVertical() && !result.after);
    }

    private void applyWidth(double width) {
        switch (widthMode) {
            case MATCH_ANCHOR_WIDTH:
                shell.setMinWidth(width);
                shell.setPrefWidth(width);
                shell.setMaxWidth(width);
                break;
            case PREFER_ANCHOR_WIDTH:
                // width already = max(anchorWidth, contentPref); a min floor is
                // enough (the popup root's pref computation bounds the shell's
                // pref by its min, so the floor reaches the window size).
                shell.setMinWidth(width);
                shell.setPrefWidth(Region.USE_COMPUTED_SIZE);
                shell.setMaxWidth(Region.USE_COMPUTED_SIZE);
                break;
            case PREF_CONTENT:
            default:
                shell.setMinWidth(Region.USE_COMPUTED_SIZE);
                shell.setPrefWidth(Region.USE_COMPUTED_SIZE);
                shell.setMaxWidth(Region.USE_COMPUTED_SIZE);
                break;
        }
    }

    private void installWindowListeners(Window window) {
        if (window == listenerWindow) {
            return;
        }
        detachWindowListeners();
        listenerWindow = window;
        if (window != null) {
            window.xProperty().addListener(repositionListener);
            window.yProperty().addListener(repositionListener);
            window.widthProperty().addListener(repositionListener);
            window.heightProperty().addListener(repositionListener);
        }
    }

    private void detachWindowListeners() {
        if (listenerWindow != null) {
            listenerWindow.xProperty().removeListener(repositionListener);
            listenerWindow.yProperty().removeListener(repositionListener);
            listenerWindow.widthProperty().removeListener(repositionListener);
            listenerWindow.heightProperty().removeListener(repositionListener);
            listenerWindow = null;
        }
    }

    private void handlePopupHidden(WindowEvent event) {
        // Every hide path converges here; drop the owner-window listeners before
        // the suppress check so even a rebind's hide releases the window and a
        // hidden popup never pins this object island to a long-lived window
        // (showInternal reinstalls them on the next show).
        detachWindowListeners();
        if (suppressOnHidden) {
            return;
        }
        notifyHidden();
    }

    private void notifyHidden() {
        showing.set(false);
        if (onHidden != null) {
            onHidden.run();
        }
    }

    private static Screen screenFor(Bounds anchorScreen) {
        List<Screen> screens = Screen.getScreensForRectangle(
                anchorScreen.getMinX(), anchorScreen.getMinY(),
                Math.max(1.0, anchorScreen.getWidth()),
                Math.max(1.0, anchorScreen.getHeight()));
        return screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
    }

    // ==================== Popup skin ====================

    private static final class ContentPopupSkin implements Skin<PopupControl> {

        private PopupControl popup;
        private Region node;

        private ContentPopupSkin(PopupControl popup, Region node) {
            this.popup = popup;
            this.node = node;
        }

        @Override
        public PopupControl getSkinnable() {
            return popup;
        }

        @Override
        public Node getNode() {
            return node;
        }

        @Override
        public void dispose() {
            popup = null;
            node = null;
        }
    }
}
