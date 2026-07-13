package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.internal.popup.RXPlacement;
import io.github.leewyatt.rxcontrols.skins.RXMenuButtonSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.PaintConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Skin;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.paint.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A button that pops up an {@link RXPopupMenu} of {@link RXMenuItem} commands when
 * activated, with an armed press ripple and a trailing drop-down arrow.
 *
 * <p>{@code RXMenuButton} extends {@link ButtonBase}, so a valid primary press
 * arms it (driving the ripple) and clicking / {@code SPACE} / {@code ENTER}
 * toggles the menu. {@link #fire()} is overridden to toggle the menu rather than
 * dispatch an {@code ActionEvent}. The {@link #getItems() items} live here (the
 * single source of truth); the skin owns the popup and mirrors the items into it.
 *
 * <p>The {@link #showingProperty() showing} flag is the single truth the skin
 * observes to open / close the popup; the popup's own close paths (Escape, an
 * item activation, an outside click) pull this flag back to {@code false}.
 */
public class RXMenuButton extends ButtonBase {

    private static final String DEFAULT_STYLE_CLASS = "rx-menu-button";
    private static final PseudoClass SHOWING_PSEUDO_CLASS = PseudoClass.getPseudoClass("showing");

    // ==================== Constructors ====================

    /**
     * Creates a menu button with an empty text caption.
     */
    public RXMenuButton() {
        initialize();
    }

    /**
     * Creates a menu button with the given text caption.
     *
     * @param text the text caption, or {@code null}
     */
    public RXMenuButton(@NamedArg("text") String text) {
        super(text, null);
        initialize();
    }

    /**
     * Creates a menu button with the given text caption and graphic.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the graphic node, or {@code null}
     */
    public RXMenuButton(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
        super(text, graphic);
        initialize();
    }

    private void initialize() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.MENU_BUTTON);
    }

    /**
     * Creates the default skin.
     *
     * @return the default skin
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXMenuButtonSkin(this);
    }

    /**
     * Returns the user-agent stylesheet used by RXControls.
     *
     * @return the user-agent stylesheet URL
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Items ====================

    private final ObservableList<RXMenuItem> items = FXCollections.observableArrayList();

    /**
     * The menu items shown when the button opens. This is the single source of
     * truth; the skin mirrors it into the internal popup.
     *
     * @return the modifiable item list
     */
    public final ObservableList<RXMenuItem> getItems() {
        return items;
    }

    // ==================== Showing ====================

    private final ReadOnlyBooleanWrapper showing = new ReadOnlyBooleanWrapper(this, "showing", false) {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(SHOWING_PSEUDO_CLASS, get());
        }
    };

    /**
     * Whether the menu popup is open. Read-only to callers; drive it with
     * {@link #show()} / {@link #hide()} / {@link #fire()}.
     *
     * @return the read-only showing property
     */
    public final ReadOnlyBooleanProperty showingProperty() {
        return showing.getReadOnlyProperty();
    }

    /**
     * Returns whether the menu is showing.
     *
     * @return {@code true} if showing
     */
    public final boolean isShowing() {
        return showing.get();
    }

    /**
     * Opens the menu at the button ({@link #placementProperty() placement}). A
     * no-op when the button is disabled; an empty / all-header menu still opens
     * nothing (the skin's popup guards that).
     */
    public void show() {
        if (!isDisabled()) {
            contextAnchor = null;
            showing.set(true);
        }
    }

    /**
     * Opens the menu at a screen point (a context menu) rather than at the button.
     * A no-op when the button is disabled. The button's own popup is reused, so the
     * button must be in a realized window; focus restores to the button on close.
     *
     * @param screenX the anchor x in screen coordinates
     * @param screenY the anchor y in screen coordinates
     */
    public void showAt(double screenX, double screenY) {
        if (!isDisabled()) {
            contextAnchor = new Point2D(screenX, screenY);
            showing.set(true);
        }
    }

    /**
     * Closes the menu.
     */
    public void hide() {
        contextAnchor = null;
        showing.set(false);
    }

    // The pending screen point when opened via showAt (a context menu), or null
    // when opened at the button. Read by the skin as it opens the popup.
    private Point2D contextAnchor;

    /**
     * Returns the pending context-menu screen anchor set by {@link #showAt}, or
     * {@code null} when the menu opens at the button. Consumed by the skin.
     *
     * @return the context anchor screen point, or {@code null}
     */
    public final Point2D getContextAnchor() {
        return contextAnchor;
    }

    /**
     * Installs a context-menu handler on {@code node}: a context-menu request
     * (right-click or the context-menu key) opens this button's menu at the cursor
     * via {@link #showAt}. The button's popup is reused, so the button must be in a
     * realized window when the request fires.
     *
     * <p>This <b>replaces</b> the node's native context menu — the request is always
     * consumed, so a disabled button (whose {@link #showAt} is a no-op) shows no menu
     * at all rather than falling back to a platform menu.
     *
     * <p>The returned handler can be passed to
     * {@code node.removeEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, handler)}
     * to uninstall it.
     *
     * @param node the node to attach the context-menu trigger to
     * @return the installed handler, for later removal
     */
    public EventHandler<ContextMenuEvent> installContextMenu(Node node) {
        EventHandler<ContextMenuEvent> handler = event -> {
            showAt(event.getScreenX(), event.getScreenY());
            event.consume();
        };
        node.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, handler);
        return handler;
    }

    // ==================== Placement ====================

    private final ObjectProperty<RXPlacement> placement =
            new SimpleObjectProperty<>(this, "placement", RXPlacement.BOTTOM_START);

    /**
     * Preferred placement of the popup relative to the button. Default
     * {@code BOTTOM_START}. {@code null} is tolerated (the popup falls back to its
     * own default).
     *
     * @return the placement property
     */
    public final ObjectProperty<RXPlacement> placementProperty() {
        return placement;
    }

    /**
     * Returns the placement.
     *
     * @return the placement, or {@code null}
     */
    public final RXPlacement getPlacement() {
        return placement.get();
    }

    /**
     * Sets the placement.
     *
     * @param value the placement, or {@code null}
     */
    public final void setPlacement(RXPlacement value) {
        placement.set(value);
    }

    // ==================== Fire ====================

    /**
     * Toggles the menu (opens if closed, closes if open) instead of dispatching an
     * {@code ActionEvent} — the {@link ButtonBase} activation contract for a menu
     * trigger. A no-op when disabled.
     */
    @Override
    public void fire() {
        if (isDisabled()) {
            return;
        }
        if (isShowing()) {
            hide();
        } else {
            show();
        }
    }

    /**
     * Routes the accessibility FIRE action through {@link #fire()}.
     *
     * @param action     the accessible action
     * @param parameters the action parameters
     */
    @Override
    public void executeAccessibleAction(AccessibleAction action, Object... parameters) {
        if (action == AccessibleAction.FIRE) {
            fire();
        } else {
            super.executeAccessibleAction(action, parameters);
        }
    }

    // ==================== Ripple Fill ====================

    private final ObjectProperty<Paint> rippleFill =
            new StyleableObjectProperty<>(RXRipplePane.DEFAULT_RIPPLE_FILL) {
                @Override
                public CssMetaData<? extends Styleable, Paint> getCssMetaData() {
                    return StyleableProperties.RIPPLE_FILL;
                }

                @Override
                public Object getBean() {
                    return RXMenuButton.this;
                }

                @Override
                public String getName() {
                    return "rippleFill";
                }
            };

    /**
     * Fill of the press ripple and hover state overlay. Initial value is
     * {@link RXRipplePane#DEFAULT_RIPPLE_FILL}; {@code null} renders no fill.
     *
     * @return the ripple-fill property
     */
    public final ObjectProperty<Paint> rippleFillProperty() {
        return rippleFill;
    }

    /**
     * Returns the ripple fill.
     *
     * @return the ripple fill
     */
    public final Paint getRippleFill() {
        return rippleFill.get();
    }

    /**
     * Sets the ripple fill.
     *
     * @param value the ripple fill
     */
    public final void setRippleFill(Paint value) {
        rippleFill.set(value);
    }

    // ==================== Ripple Opacity ====================

    private final DoubleProperty rippleOpacity =
            new StyleableDoubleProperty(RXRipplePane.DEFAULT_RIPPLE_OPACITY) {
                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.RIPPLE_OPACITY;
                }

                @Override
                public Object getBean() {
                    return RXMenuButton.this;
                }

                @Override
                public String getName() {
                    return "rippleOpacity";
                }
            };

    /**
     * Peak ripple / overlay opacity. Initial value is
     * {@link RXRipplePane#DEFAULT_RIPPLE_OPACITY}.
     *
     * @return the ripple-opacity property
     */
    public final DoubleProperty rippleOpacityProperty() {
        return rippleOpacity;
    }

    /**
     * Returns the ripple opacity.
     *
     * @return the ripple opacity
     */
    public final double getRippleOpacity() {
        return rippleOpacity.get();
    }

    /**
     * Sets the ripple opacity.
     *
     * @param value the ripple opacity
     */
    public final void setRippleOpacity(double value) {
        rippleOpacity.set(value);
    }

    // ==================== Ripple Enabled ====================

    private final BooleanProperty rippleEnabled =
            new StyleableBooleanProperty(RXRipplePane.DEFAULT_RIPPLE_ENABLED) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.RIPPLE_ENABLED;
                }

                @Override
                public Object getBean() {
                    return RXMenuButton.this;
                }

                @Override
                public String getName() {
                    return "rippleEnabled";
                }
            };

    /**
     * Whether pressing the button creates a ripple. Initial value is
     * {@link RXRipplePane#DEFAULT_RIPPLE_ENABLED}.
     *
     * @return the ripple-enabled property
     */
    public final BooleanProperty rippleEnabledProperty() {
        return rippleEnabled;
    }

    /**
     * Returns whether the ripple is enabled.
     *
     * @return {@code true} if the ripple is enabled
     */
    public final boolean isRippleEnabled() {
        return rippleEnabled.get();
    }

    /**
     * Sets whether the ripple is enabled.
     *
     * @param value {@code true} to enable the ripple
     */
    public final void setRippleEnabled(boolean value) {
        rippleEnabled.set(value);
    }

    // ==================== State Overlay Enabled ====================

    private final BooleanProperty stateOverlayEnabled =
            new StyleableBooleanProperty(RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.RIPPLE_STATE_OVERLAY_ENABLED;
                }

                @Override
                public Object getBean() {
                    return RXMenuButton.this;
                }

                @Override
                public String getName() {
                    return "stateOverlayEnabled";
                }
            };

    /**
     * Whether the hover state overlay tints the button while the pointer is inside.
     * Initial value is {@link RXRipplePane#DEFAULT_STATE_OVERLAY_ENABLED}.
     *
     * @return the state-overlay-enabled property
     */
    public final BooleanProperty stateOverlayEnabledProperty() {
        return stateOverlayEnabled;
    }

    /**
     * Returns whether the state overlay is enabled.
     *
     * @return {@code true} if the state overlay is enabled
     */
    public final boolean isStateOverlayEnabled() {
        return stateOverlayEnabled.get();
    }

    /**
     * Sets whether the state overlay is enabled.
     *
     * @param value {@code true} to enable the state overlay
     */
    public final void setStateOverlayEnabled(boolean value) {
        stateOverlayEnabled.set(value);
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXMenuButton, Paint> RIPPLE_FILL =
                new CssMetaData<>("-rx-ripple-fill",
                        PaintConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_FILL) {
                    @Override
                    public boolean isSettable(RXMenuButton control) {
                        return !control.rippleFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXMenuButton control) {
                        return (StyleableProperty<Paint>) control.rippleFillProperty();
                    }
                };

        private static final CssMetaData<RXMenuButton, Number> RIPPLE_OPACITY =
                new CssMetaData<>("-rx-ripple-opacity",
                        SizeConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_OPACITY) {
                    @Override
                    public boolean isSettable(RXMenuButton control) {
                        return !control.rippleOpacity.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMenuButton control) {
                        return (StyleableProperty<Number>) control.rippleOpacityProperty();
                    }
                };

        private static final CssMetaData<RXMenuButton, Boolean> RIPPLE_ENABLED =
                new CssMetaData<>("-rx-ripple-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_ENABLED) {
                    @Override
                    public boolean isSettable(RXMenuButton control) {
                        return !control.rippleEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXMenuButton control) {
                        return (StyleableProperty<Boolean>) control.rippleEnabledProperty();
                    }
                };

        private static final CssMetaData<RXMenuButton, Boolean> RIPPLE_STATE_OVERLAY_ENABLED =
                new CssMetaData<>("-rx-ripple-state-overlay-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED) {
                    @Override
                    public boolean isSettable(RXMenuButton control) {
                        return !control.stateOverlayEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXMenuButton control) {
                        return (StyleableProperty<Boolean>) control.stateOverlayEnabledProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(ButtonBase.getClassCssMetaData());
            styleables.add(RIPPLE_FILL);
            styleables.add(RIPPLE_OPACITY);
            styleables.add(RIPPLE_ENABLED);
            styleables.add(RIPPLE_STATE_OVERLAY_ENABLED);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata list
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * Returns the CSS metadata associated with this control.
     *
     * @return the CSS metadata list
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
