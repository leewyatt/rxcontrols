package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXMenuListSkin;
import javafx.animation.Interpolator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.PaintConverter;
import javafx.css.converter.SizeConverter;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.ToggleGroup;
import javafx.scene.paint.Paint;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A vertical list of command-menu items with real per-item roving focus,
 * full keyboard navigation, type-ahead, ripple feedback, and command-menu
 * accessibility roles. It is the reusable content of a popup menu but can also
 * be embedded inline in a layout as a standing command list.
 *
 * <p>Items are {@link RXMenuItem} value objects (not nodes); the skin renders
 * each into a focusable cell. An item belongs to at most one list at a time —
 * adding an item that already has a parent moves it here. Unlike a
 * {@code ListView} this list is <b>not</b> virtualized: menus hold a handful of
 * items, every one gets a stable cell, and assistive technologies see a static
 * {@code CONTEXT_MENU} / {@code MENU_ITEM} tree rather than a virtualized list.
 *
 * <p>Keyboard navigation moves real focus between cells (Down/Up wrap and skip
 * separators, headers, and — by default — disabled items; Home/End jump to the
 * ends; a printable key does type-ahead). Enter/Space activate the focused
 * item. Dismissal keys (Escape/Tab) are the hosting popup's concern; used
 * inline they fall through to the platform default.
 */
public class RXMenuList extends Control {

    private static final String DEFAULT_STYLE_CLASS = "rx-menu-list";
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(120.0);
    private static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_OUT;
    private static final PseudoClass DENSE_PSEUDO_CLASS = PseudoClass.getPseudoClass("dense");

    /**
     * Which item receives focus when the menu opens.
     */
    public enum InitialFocus {
        /**
         * Focus the first focusable item.
         */
        FIRST,
        /**
         * Focus the currently selected item (falls back to {@code FIRST} when no
         * item is selected).
         */
        SELECTED
    }

    // ==================== Constructors ====================

    /**
     * Creates an empty menu list.
     */
    public RXMenuList() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.CONTEXT_MENU);
        items.addListener((ListChangeListener<RXMenuItem>) change -> {
            while (change.next()) {
                for (RXMenuItem removed : change.getRemoved()) {
                    // Guard against a move that already re-pointed the item at
                    // another list before this remove callback ran.
                    if (removed.getParentList() == this) {
                        removed.setParentListInternal(null);
                    }
                }
                for (RXMenuItem added : change.getAddedSubList()) {
                    RXMenuList previous = added.getParentList();
                    if (previous != null && previous != this) {
                        previous.getItems().remove(added);
                    }
                    added.setParentListInternal(this);
                }
            }
        });
    }

    /**
     * Creates the default skin.
     *
     * @return the default skin
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXMenuListSkin(this);
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

    // ==================== Accessibility ====================

    private BooleanSupplier a11yShowing;
    private Supplier<Node> a11yOwner;

    // Wired by RXPopupMenu (same package) so the CONTEXT_MENU surface can report its
    // popup showing state and owner to assistive technology. Left null when the list
    // is used inline, where the Node defaults apply.
    void setPopupAccessibility(BooleanSupplier showing, Supplier<Node> owner) {
        this.a11yShowing = showing;
        this.a11yOwner = owner;
    }

    /**
     * Answers the command-menu surface's accessibility attributes: {@code VISIBLE}
     * reflects the hosting popup's showing state and {@code PARENT_MENU} returns its
     * owner (the triggering node), mirroring the platform {@code CONTEXT_MENU}
     * surface. Both fall back to the {@link Control} defaults when the list is
     * embedded inline rather than shown in a popup.
     *
     * @param attribute  the requested attribute
     * @param parameters optional attribute parameters
     * @return the attribute value
     */
    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        return switch (attribute) {
            case VISIBLE -> a11yShowing != null
                    ? a11yShowing.getAsBoolean()
                    : super.queryAccessibleAttribute(attribute, parameters);
            case PARENT_MENU -> a11yOwner != null
                    ? a11yOwner.get()
                    : super.queryAccessibleAttribute(attribute, parameters);
            default -> super.queryAccessibleAttribute(attribute, parameters);
        };
    }

    // ==================== Items ====================

    private final ObservableList<RXMenuItem> items = FXCollections.observableArrayList();

    /**
     * The items shown in this list. Separators and headers are ordinary
     * {@link RXMenuItem} subtypes and sit inline in this same list.
     *
     * @return the modifiable item list
     */
    public final ObservableList<RXMenuItem> getItems() {
        return items;
    }

    // ==================== Converter ====================

    private final ObjectProperty<StringConverter<RXMenuItem>> converter =
            new SimpleObjectProperty<>(this, "converter");

    /**
     * Optional converter that extracts an item's display / type-ahead text.
     * When {@code null}, the item's {@link RXMenuItem#getText() text} is used.
     * Only {@link StringConverter#toString(Object) toString} is used; menus
     * never parse text back, so {@code fromString} is never called.
     *
     * @return the converter property
     */
    public final ObjectProperty<StringConverter<RXMenuItem>> converterProperty() {
        return converter;
    }

    /**
     * Returns the text converter.
     *
     * @return the converter, or {@code null}
     */
    public final StringConverter<RXMenuItem> getConverter() {
        return converter.get();
    }

    /**
     * Sets the text converter.
     *
     * @param value the converter, or {@code null}
     */
    public final void setConverter(StringConverter<RXMenuItem> value) {
        converter.set(value);
    }

    // ==================== Wrap Around ====================

    private final BooleanProperty wrapAround = new SimpleBooleanProperty(this, "wrapAround", true);

    /**
     * Whether Down/Up wrap around at the first and last focusable items.
     * Defaults to {@code true}, matching native context menus and Material.
     *
     * @return the wrap-around property
     */
    public final BooleanProperty wrapAroundProperty() {
        return wrapAround;
    }

    /**
     * Returns whether arrow navigation wraps around.
     *
     * @return {@code true} if navigation wraps
     */
    public final boolean isWrapAround() {
        return wrapAround.get();
    }

    /**
     * Sets whether arrow navigation wraps around.
     *
     * @param value {@code true} to wrap
     */
    public final void setWrapAround(boolean value) {
        wrapAround.set(value);
    }

    // ==================== Disabled Items Focusable ====================

    private final BooleanProperty disabledItemsFocusable =
            new SimpleBooleanProperty(this, "disabledItemsFocusable", false);

    /**
     * Whether disabled items participate in roving keyboard navigation
     * (focusable but not activatable), versus being skipped. Defaults to
     * {@code false} (skip), matching Material / MUI / the repository's
     * {@code RXListView}. Set {@code true} for the WAI-ARIA APG behavior where a
     * disabled item can be focused but not activated — a deliberate departure
     * from the Material default.
     *
     * @return the disabled-items-focusable property
     */
    public final BooleanProperty disabledItemsFocusableProperty() {
        return disabledItemsFocusable;
    }

    /**
     * Returns whether disabled items are focusable.
     *
     * @return {@code true} if disabled items can be focused
     */
    public final boolean isDisabledItemsFocusable() {
        return disabledItemsFocusable.get();
    }

    /**
     * Sets whether disabled items are focusable.
     *
     * @param value {@code true} to let disabled items receive focus
     */
    public final void setDisabledItemsFocusable(boolean value) {
        disabledItemsFocusable.set(value);
    }

    // ==================== Initial Focus ====================

    private final ObjectProperty<InitialFocus> initialFocus =
            new SimpleObjectProperty<>(this, "initialFocus", InitialFocus.FIRST);

    /**
     * Which item receives focus when the menu opens. Defaults to
     * {@link InitialFocus#FIRST}. {@code null} is tolerated and treated as
     * {@code FIRST} by the skin.
     *
     * @return the initial-focus property
     */
    public final ObjectProperty<InitialFocus> initialFocusProperty() {
        return initialFocus;
    }

    /**
     * Returns the initial-focus mode.
     *
     * @return the initial-focus mode, or {@code null}
     */
    public final InitialFocus getInitialFocus() {
        return initialFocus.get();
    }

    /**
     * Sets the initial-focus mode.
     *
     * @param value the initial-focus mode, or {@code null}
     */
    public final void setInitialFocus(InitialFocus value) {
        initialFocus.set(value);
    }

    // ==================== On Action ====================

    private final ObjectProperty<EventHandler<ActionEvent>> onAction =
            new SimpleObjectProperty<>(this, "onAction");

    /**
     * A unified activation hook fired whenever any item is activated, in
     * addition to the item's own {@link RXMenuItem#onActionProperty() onAction}.
     * The {@link ActionEvent}'s source is the activated item.
     *
     * @return the on-action property
     */
    public final ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        return onAction;
    }

    /**
     * Returns the unified activation handler.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<ActionEvent> getOnAction() {
        return onAction.get();
    }

    /**
     * Sets the unified activation handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnAction(EventHandler<ActionEvent> value) {
        onAction.set(value);
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
                    return RXMenuList.this;
                }

                @Override
                public String getName() {
                    return "rippleFill";
                }
            };

    /**
     * The fill of the item ripple and hover state overlay. Initial value is
     * {@link RXRipplePane#DEFAULT_RIPPLE_FILL}; setting {@code null} renders no
     * fill (transparent) per the JavaFX {@code Shape.setFill} convention.
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
                    return RXMenuList.this;
                }

                @Override
                public String getName() {
                    return "rippleOpacity";
                }
            };

    /**
     * The peak ripple / overlay opacity. Initial value is
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
                    return RXMenuList.this;
                }

                @Override
                public String getName() {
                    return "rippleEnabled";
                }
            };

    /**
     * Whether pressing an item creates a ripple. Initial value is
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
                    return RXMenuList.this;
                }

                @Override
                public String getName() {
                    return "stateOverlayEnabled";
                }
            };

    /**
     * Whether the hover state overlay tints an item while the pointer is inside
     * (independent of {@link #rippleEnabledProperty() rippleEnabled}). Initial
     * value is {@link RXRipplePane#DEFAULT_STATE_OVERLAY_ENABLED}.
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

    // ==================== Animated ====================

    private final BooleanProperty animated = new SimpleBooleanProperty(this, "animated", true);

    /**
     * Master switch for the entrance animation played when this list is shown in a
     * popup menu. Defaults to {@code true}. Has no effect on an inline list (which
     * is never "shown"). Not styleable.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether the entrance animation is enabled.
     *
     * @return {@code true} if animated
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether the entrance animation is enabled.
     *
     * @param value {@code true} to animate
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== Animation Duration ====================

    private final ObjectProperty<Duration> animationDuration =
            new StyleableObjectProperty<>(DEFAULT_ANIMATION_DURATION) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.ANIMATION_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXMenuList.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of the entrance animation played when the list is shown in a popup
     * menu (V1 animates the entrance only). Initial value {@code 120ms}. A
     * {@code null}, non-positive, unknown or indefinite value is accepted and
     * disables animation, exactly like {@code animated=false}.
     *
     * @return the animation-duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the entrance-animation duration.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the entrance-animation duration.
     *
     * @param value the duration; {@code null} or any non-positive value disables animation
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Animation Interpolator ====================

    private final ObjectProperty<Interpolator> animationInterpolator =
            new SimpleObjectProperty<>(this, "animationInterpolator", DEFAULT_ANIMATION_INTERPOLATOR);

    /**
     * Interpolator (easing) for the entrance animation. {@code null} falls back to
     * {@link Interpolator#EASE_OUT}. Not styleable ({@link Interpolator} has no
     * stable CSS converter).
     *
     * @return the animation-interpolator property
     */
    public final ObjectProperty<Interpolator> animationInterpolatorProperty() {
        return animationInterpolator;
    }

    /**
     * Returns the animation interpolator.
     *
     * @return the animation interpolator
     */
    public final Interpolator getAnimationInterpolator() {
        return animationInterpolator.get();
    }

    /**
     * Sets the animation interpolator.
     *
     * @param value the interpolator, or {@code null} for the default
     */
    public final void setAnimationInterpolator(Interpolator value) {
        animationInterpolator.set(value);
    }

    // ==================== Dense ====================

    private final BooleanProperty dense =
            new StyleableBooleanProperty(false) {
                @Override
                protected void invalidated() {
                    pseudoClassStateChanged(DENSE_PSEUDO_CLASS, get());
                }

                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.DENSE;
                }

                @Override
                public Object getBean() {
                    return RXMenuList.this;
                }

                @Override
                public String getName() {
                    return "dense";
                }
            };

    /**
     * Whether the list uses a compact (dense) vertical rhythm. Toggling it flips
     * the container-level {@code :dense} pseudo-class, which the theme reacts to
     * by tightening item padding — density is a container state, not a per-item
     * numeric token. Styleable via {@code -rx-dense}. Defaults to {@code false}.
     *
     * @return the dense property
     */
    public final BooleanProperty denseProperty() {
        return dense;
    }

    /**
     * Returns whether the list is dense.
     *
     * @return {@code true} if dense
     */
    public final boolean isDense() {
        return dense.get();
    }

    /**
     * Sets whether the list is dense.
     *
     * @param value {@code true} for a compact vertical rhythm
     */
    public final void setDense(boolean value) {
        dense.set(value);
    }

    // ==================== Activation ====================

    private Consumer<RXMenuItem> commandActivator;

    /**
     * Activates the given item as if the user selected it: it fires (with the
     * hosting popup's close-then-fire ordering when hosted, or directly when
     * inline), then the unified {@link #onActionProperty() onAction} hook fires
     * with the item as source. Disabled or non-focusable items are ignored.
     *
     * @param item the item to activate
     */
    public void activate(RXMenuItem item) {
        if (item == null || item.isDisable() || !item.isFocusable()) {
            return;
        }
        // A checkbox / radio item toggles its checked state (radio exclusion is
        // handled by the toggle group); it also keeps the menu open (keepOpen), so
        // the commandActivator below does not close it. Re-activating the already
        // selected radio must not clear the group (mirrors ToggleButton.fire()).
        if (item.isSelectable()) {
            ToggleGroup group = item.getToggleGroup();
            boolean selectedRadio = group != null && item.isSelected() && group.getSelectedToggle() != null;
            if (!selectedRadio) {
                item.setSelected(!item.isSelected());
            }
        }
        if (commandActivator != null) {
            commandActivator.accept(item);
        } else {
            item.fire();
        }
        EventHandler<ActionEvent> handler = getOnAction();
        if (handler != null) {
            handler.handle(new ActionEvent(item, null));
        }
    }

    // Package-private: the hosting RXPopupMenu installs close-then-fire here; a
    // null activator (inline use) fires the item directly.
    final void setCommandActivator(Consumer<RXMenuItem> value) {
        commandActivator = value;
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXMenuList, Paint> RIPPLE_FILL =
                new CssMetaData<>("-rx-ripple-fill",
                        PaintConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_FILL) {
                    @Override
                    public boolean isSettable(RXMenuList control) {
                        return !control.rippleFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXMenuList control) {
                        return (StyleableProperty<Paint>) control.rippleFillProperty();
                    }
                };

        private static final CssMetaData<RXMenuList, Number> RIPPLE_OPACITY =
                new CssMetaData<>("-rx-ripple-opacity",
                        SizeConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_OPACITY) {
                    @Override
                    public boolean isSettable(RXMenuList control) {
                        return !control.rippleOpacity.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMenuList control) {
                        return (StyleableProperty<Number>) control.rippleOpacityProperty();
                    }
                };

        private static final CssMetaData<RXMenuList, Boolean> RIPPLE_ENABLED =
                new CssMetaData<>("-rx-ripple-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_ENABLED) {
                    @Override
                    public boolean isSettable(RXMenuList control) {
                        return !control.rippleEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXMenuList control) {
                        return (StyleableProperty<Boolean>) control.rippleEnabledProperty();
                    }
                };

        private static final CssMetaData<RXMenuList, Boolean> RIPPLE_STATE_OVERLAY_ENABLED =
                new CssMetaData<>("-rx-ripple-state-overlay-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED) {
                    @Override
                    public boolean isSettable(RXMenuList control) {
                        return !control.stateOverlayEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXMenuList control) {
                        return (StyleableProperty<Boolean>) control.stateOverlayEnabledProperty();
                    }
                };

        private static final CssMetaData<RXMenuList, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXMenuList control) {
                        return !control.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXMenuList control) {
                        return (StyleableProperty<Duration>) control.animationDurationProperty();
                    }
                };

        private static final CssMetaData<RXMenuList, Boolean> DENSE =
                new CssMetaData<>("-rx-dense", BooleanConverter.getInstance(), Boolean.FALSE) {
                    @Override
                    public boolean isSettable(RXMenuList control) {
                        return !control.dense.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXMenuList control) {
                        return (StyleableProperty<Boolean>) control.denseProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(RIPPLE_FILL);
            styleables.add(RIPPLE_OPACITY);
            styleables.add(RIPPLE_ENABLED);
            styleables.add(RIPPLE_STATE_OVERLAY_ENABLED);
            styleables.add(ANIMATION_DURATION);
            styleables.add(DENSE);
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
