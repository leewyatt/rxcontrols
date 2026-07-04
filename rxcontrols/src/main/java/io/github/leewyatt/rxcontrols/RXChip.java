package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXChipEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXChipSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.PaintConverter;
import javafx.css.converter.SizeConverter;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Skin;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A compact, interactive entity — a "chip" (a.k.a. tag / token): an optional
 * leading graphic or avatar, a text label, and an optional trailing remove
 * (close) affordance. A chip has a primary action (like a button) and, for the
 * {@link ChipType#FILTER} type, a persistent {@link #selectedProperty() selected}
 * state.
 *
 * <p>{@code RXChip} extends {@link ButtonBase}, inheriting {@code onAction} /
 * {@code armed} / {@code fire()} and (through {@code Labeled}) {@code text} /
 * {@code graphic} / {@code contentDisplay} / {@code graphicTextGap} /
 * {@code textOverrun}. It is a sibling of {@link RXButton} in the
 * {@code ButtonBase} family but shares no intermediate base class, so it carries
 * none of the {@code Button} default / cancel machinery and none of
 * {@code RXButton}'s {@code button} style classes; the two share ripple feedback
 * only at the skin layer.</p>
 *
 * <p>Keyboard activation (Space / Enter), the trailing-remove behavior and the
 * bounded ripple are supplied by {@link RXChipSkin}. The chip is
 * non-generic: it renders text / graphic / close only. A typed chip input
 * ({@code RXChipInput<T>}) adapts an item to a chip via its {@code chipFactory}.</p>
 */
public class RXChip extends ButtonBase {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-chip";

    /**
     * Default chip type. Public because the skin reads it to default a
     * {@code null} {@link #typeProperty() type}.
     */
    public static final ChipType DEFAULT_TYPE = ChipType.ASSIST;

    private static final PseudoClass ASSIST_PSEUDO_CLASS = PseudoClass.getPseudoClass("assist");
    private static final PseudoClass FILTER_PSEUDO_CLASS = PseudoClass.getPseudoClass("filter");
    private static final PseudoClass INPUT_PSEUDO_CLASS = PseudoClass.getPseudoClass("input");
    private static final PseudoClass SUGGESTION_PSEUDO_CLASS = PseudoClass.getPseudoClass("suggestion");
    private static final PseudoClass SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass REMOVABLE_PSEUDO_CLASS = PseudoClass.getPseudoClass("removable");

    // ==================== Chip Type ====================

    /**
     * The four Material chip types. The type is the primary switch: it drives the
     * {@code :assist} / {@code :filter} / {@code :input} / {@code :suggestion}
     * pseudo-classes and the accessible role, and seeds the default
     * {@link #removableProperty() removable} affordance at construction.
     */
    public enum ChipType {
        /** Momentary action (e.g. "Add to calendar"). Not selectable, not removable by default. Role BUTTON. */
        ASSIST,
        /** Toggle within a set (filter). Selectable ({@code selected}); removable optional. Role TOGGLE_BUTTON. */
        FILTER,
        /** A discrete data token entered by a user. Removable by default. Role BUTTON. */
        INPUT,
        /** A dynamically generated suggestion; clicking inserts it. Not selectable / removable. Role BUTTON. */
        SUGGESTION
    }

    // ==================== Constructors ====================

    /**
     * Creates an assist chip with an empty text caption.
     */
    public RXChip() {
        this("", ChipType.ASSIST);
    }

    /**
     * Creates an assist chip with the given text caption.
     *
     * @param text the text caption, or {@code null}
     */
    public RXChip(@NamedArg("text") String text) {
        this(text, ChipType.ASSIST);
    }

    /**
     * Creates a chip with the given text caption and type.
     *
     * @param text the text caption, or {@code null}
     * @param type the chip type, or {@code null} (treated as {@link ChipType#ASSIST})
     */
    public RXChip(@NamedArg("text") String text, @NamedArg("type") ChipType type) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setFocusTraversable(true);
        setText(text);
        setType(type);
        // Seed the type-driven removable default once, at construction: an input
        // chip is removable by default. Changing the type later does not silently
        // clobber an explicit removable set by the user.
        if (type == ChipType.INPUT) {
            setRemovable(true);
        }
        // invalidated() only fires on a change; sync the type-driven pseudo-classes
        // and accessible role explicitly for the initial (possibly unchanged) value.
        applyType(getType());
    }

    /**
     * Creates an assist chip with the given text caption and leading graphic.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the leading graphic node, or {@code null}
     */
    public RXChip(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
        this(text, ChipType.ASSIST);
        setGraphic(graphic);
    }

    // ==================== Type ====================

    private final ObjectProperty<ChipType> type = new ObjectPropertyBase<>(DEFAULT_TYPE) {
        @Override
        protected void invalidated() {
            applyType(get());
        }

        @Override
        public Object getBean() {
            return RXChip.this;
        }

        @Override
        public String getName() {
            return "type";
        }
    };

    /**
     * The chip type. Drives the {@code :assist} / {@code :filter} / {@code :input}
     * / {@code :suggestion} pseudo-classes and the accessible role
     * ({@link ChipType#FILTER} → {@code TOGGLE_BUTTON}, others → {@code BUTTON}).
     * {@code null} is treated as {@link #DEFAULT_TYPE}.
     *
     * @return the type property
     */
    public final ObjectProperty<ChipType> typeProperty() {
        return type;
    }

    /**
     * Returns the chip type.
     *
     * @return the chip type, or {@code null}
     */
    public final ChipType getType() {
        return type.get();
    }

    /**
     * Sets the chip type.
     *
     * @param value the chip type, or {@code null} for the default
     */
    public final void setType(ChipType value) {
        type.set(value);
    }

    private void applyType(ChipType value) {
        ChipType resolved = value == null ? DEFAULT_TYPE : value;
        pseudoClassStateChanged(ASSIST_PSEUDO_CLASS, resolved == ChipType.ASSIST);
        pseudoClassStateChanged(FILTER_PSEUDO_CLASS, resolved == ChipType.FILTER);
        pseudoClassStateChanged(INPUT_PSEUDO_CLASS, resolved == ChipType.INPUT);
        pseudoClassStateChanged(SUGGESTION_PSEUDO_CLASS, resolved == ChipType.SUGGESTION);
        setAccessibleRole(resolved == ChipType.FILTER
                ? AccessibleRole.TOGGLE_BUTTON : AccessibleRole.BUTTON);
    }

    // ==================== Selected ====================

    private final BooleanProperty selected = new BooleanPropertyBase(false) {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, get());
            notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTED);
        }

        @Override
        public Object getBean() {
            return RXChip.this;
        }

        @Override
        public String getName() {
            return "selected";
        }
    };

    /**
     * Whether this chip is selected. Meaningful for {@link ChipType#FILTER} chips
     * (a persistent single toggle); the property is writable for any type but only
     * a filter chip toggles it on activation. Drives the {@code :selected}
     * pseudo-class. This is a single on/off switch, unrelated to how many chips a
     * {@code RXChipSet} lets you select at once.
     *
     * @return the selected property
     */
    public final BooleanProperty selectedProperty() {
        return selected;
    }

    /**
     * Returns whether this chip is selected.
     *
     * @return whether this chip is selected
     */
    public final boolean isSelected() {
        return selected.get();
    }

    /**
     * Sets whether this chip is selected. A programmatic change does not fire an
     * {@link ActionEvent}; listen on {@link #selectedProperty()} to observe any
     * source.
     *
     * @param value {@code true} to select the chip
     */
    public final void setSelected(boolean value) {
        selected.set(value);
    }

    // ==================== Removable ====================

    private final BooleanProperty removable = new BooleanPropertyBase(false) {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(REMOVABLE_PSEUDO_CLASS, get());
            requestLayout();
        }

        @Override
        public Object getBean() {
            return RXChip.this;
        }

        @Override
        public String getName() {
            return "removable";
        }
    };

    /**
     * Whether the chip shows a trailing remove (close) affordance. Defaults to
     * {@code true} for a chip created as {@link ChipType#INPUT}, {@code false}
     * otherwise. Drives the {@code :removable} pseudo-class and the skin's close
     * button.
     *
     * @return the removable property
     */
    public final BooleanProperty removableProperty() {
        return removable;
    }

    /**
     * Returns whether the chip shows a remove affordance.
     *
     * @return whether the chip shows a remove affordance
     */
    public final boolean isRemovable() {
        return removable.get();
    }

    /**
     * Sets whether the chip shows a remove affordance.
     *
     * @param value {@code true} to show the remove affordance
     */
    public final void setRemovable(boolean value) {
        removable.set(value);
    }

    // ==================== On Remove ====================

    private final ObjectProperty<EventHandler<RXChipEvent>> onRemove =
            new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(RXChipEvent.REMOVE, get());
                }

                @Override
                public Object getBean() {
                    return RXChip.this;
                }

                @Override
                public String getName() {
                    return "onRemove";
                }
            };

    /**
     * The handler invoked when this chip is about to be removed (the
     * {@link RXChipEvent#REMOVE} event, which a handler may
     * {@link javafx.event.Event#consume() consume} to veto). Mirrors the
     * {@code ButtonBase.onAction} convention.
     *
     * @return the on-remove property
     */
    public final ObjectProperty<EventHandler<RXChipEvent>> onRemoveProperty() {
        return onRemove;
    }

    /**
     * Returns the on-remove handler.
     *
     * @return the on-remove handler, or {@code null}
     */
    public final EventHandler<RXChipEvent> getOnRemove() {
        return onRemove.get();
    }

    /**
     * Sets the on-remove handler.
     *
     * @param value the on-remove handler, or {@code null}
     */
    public final void setOnRemove(EventHandler<RXChipEvent> value) {
        onRemove.set(value);
    }

    // ==================== Behaviour ====================

    /**
     * Requests removal of this chip: fires a vetoable {@link RXChipEvent#REMOVE}.
     * The chip does not remove itself from any collection (it holds none) — it
     * only fires the event; the owner (a chip input's skin, or a caller's handler)
     * performs the actual removal unless a handler consumes the event.
     */
    public final void remove() {
        fireEvent(new RXChipEvent(RXChipEvent.REMOVE, this, null));
    }

    /**
     * Fires the chip's primary action. A {@link ChipType#FILTER} chip also toggles
     * {@link #selectedProperty() selected}. A disabled chip ignores it.
     */
    @Override
    public void fire() {
        if (isDisabled()) {
            return;
        }
        if (getType() == ChipType.FILTER) {
            setSelected(!isSelected());
        }
        fireEvent(new ActionEvent());
    }

    /**
     * Creates the default skin.
     *
     * @return the default skin
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXChipSkin(this);
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        if (attribute == AccessibleAttribute.SELECTED) {
            return isSelected();
        }
        return super.queryAccessibleAttribute(attribute, parameters);
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
                    return RXChip.this;
                }

                @Override
                public String getName() {
                    return "rippleFill";
                }
            };

    /**
     * Fill used for the press ripple and the state overlay. Initial value is
     * {@link RXRipplePane#DEFAULT_RIPPLE_FILL}; setting {@code null} renders no
     * fill (transparent) per the JavaFX {@code Shape.setFill} convention.
     *
     * @return the ripple fill property
     */
    public final ObjectProperty<Paint> rippleFillProperty() {
        return rippleFill;
    }

    /**
     * Returns the ripple fill.
     *
     * @return the ripple fill, or {@code null}
     */
    public final Paint getRippleFill() {
        return rippleFill.get();
    }

    /**
     * Sets the ripple fill.
     *
     * @param value the ripple fill, or {@code null} for no fill
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
                    return RXChip.this;
                }

                @Override
                public String getName() {
                    return "rippleOpacity";
                }
            };

    /**
     * Peak opacity for the press ripple. Values outside {@code [0, 1]} are stored
     * as-is and clamped at render time. Initial value is
     * {@link RXRipplePane#DEFAULT_RIPPLE_OPACITY}.
     *
     * @return the ripple opacity property
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
                    return RXChip.this;
                }

                @Override
                public String getName() {
                    return "rippleEnabled";
                }
            };

    /**
     * Whether user interaction creates press ripples. Turning this off clears
     * existing ripple nodes and running ripple animations.
     *
     * @return the ripple-enabled property
     */
    public final BooleanProperty rippleEnabledProperty() {
        return rippleEnabled;
    }

    /**
     * Returns whether ripple interaction is enabled.
     *
     * @return whether ripple interaction is enabled
     */
    public final boolean isRippleEnabled() {
        return rippleEnabled.get();
    }

    /**
     * Sets whether ripple interaction is enabled.
     *
     * @param value {@code true} to enable ripple interaction
     */
    public final void setRippleEnabled(boolean value) {
        rippleEnabled.set(value);
    }

    // ==================== State Overlay Enabled ====================

    private final BooleanProperty stateOverlayEnabled =
            new StyleableBooleanProperty(RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.STATE_OVERLAY_ENABLED;
                }

                @Override
                public Object getBean() {
                    return RXChip.this;
                }

                @Override
                public String getName() {
                    return "stateOverlayEnabled";
                }
            };

    /**
     * Whether the low-opacity state overlay may show while the pointer is inside.
     * The press ripple is unaffected (it stays gated only by
     * {@link #rippleEnabledProperty() rippleEnabled}). Initial value is
     * {@link RXRipplePane#DEFAULT_STATE_OVERLAY_ENABLED}.
     *
     * @return the state-overlay-enabled property
     */
    public final BooleanProperty stateOverlayEnabledProperty() {
        return stateOverlayEnabled;
    }

    /**
     * Returns whether the state overlay may show.
     *
     * @return whether the state overlay may show
     */
    public final boolean isStateOverlayEnabled() {
        return stateOverlayEnabled.get();
    }

    /**
     * Sets whether the state overlay may show.
     *
     * @param value {@code true} to allow the state overlay
     */
    public final void setStateOverlayEnabled(boolean value) {
        stateOverlayEnabled.set(value);
    }

    // ==================== Max Label Width ====================

    private final DoubleProperty maxLabelWidth =
            new StyleableDoubleProperty(Region.USE_COMPUTED_SIZE) {
                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.MAX_LABEL_WIDTH;
                }

                @Override
                public Object getBean() {
                    return RXChip.this;
                }

                @Override
                public String getName() {
                    return "maxLabelWidth";
                }
            };

    /**
     * Maximum width of the text label before it truncates with an ellipsis. The
     * default {@link Region#USE_COMPUTED_SIZE} lets the label grow to its content;
     * a positive value caps it (settable from CSS via
     * {@code -rx-chip-max-label-width}).
     *
     * @return the max label width property
     */
    public final DoubleProperty maxLabelWidthProperty() {
        return maxLabelWidth;
    }

    /**
     * Returns the maximum label width.
     *
     * @return the maximum label width, or {@link Region#USE_COMPUTED_SIZE}
     */
    public final double getMaxLabelWidth() {
        return maxLabelWidth.get();
    }

    /**
     * Sets the maximum label width.
     *
     * @param value the maximum label width, or {@link Region#USE_COMPUTED_SIZE}
     */
    public final void setMaxLabelWidth(double value) {
        maxLabelWidth.set(value);
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXChip, Paint> RIPPLE_FILL =
                new CssMetaData<>("-rx-ripple-fill",
                        PaintConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_FILL) {
                    @Override
                    public boolean isSettable(RXChip chip) {
                        return !chip.rippleFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXChip chip) {
                        return (StyleableProperty<Paint>) chip.rippleFillProperty();
                    }
                };

        private static final CssMetaData<RXChip, Number> RIPPLE_OPACITY =
                new CssMetaData<>("-rx-ripple-opacity",
                        SizeConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_OPACITY) {
                    @Override
                    public boolean isSettable(RXChip chip) {
                        return !chip.rippleOpacity.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXChip chip) {
                        return (StyleableProperty<Number>) chip.rippleOpacityProperty();
                    }
                };

        private static final CssMetaData<RXChip, Boolean> RIPPLE_ENABLED =
                new CssMetaData<>("-rx-ripple-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_ENABLED) {
                    @Override
                    public boolean isSettable(RXChip chip) {
                        return !chip.rippleEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXChip chip) {
                        return (StyleableProperty<Boolean>) chip.rippleEnabledProperty();
                    }
                };

        private static final CssMetaData<RXChip, Boolean> STATE_OVERLAY_ENABLED =
                new CssMetaData<>("-rx-ripple-state-overlay-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED) {
                    @Override
                    public boolean isSettable(RXChip chip) {
                        return !chip.stateOverlayEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXChip chip) {
                        return (StyleableProperty<Boolean>) chip.stateOverlayEnabledProperty();
                    }
                };

        private static final CssMetaData<RXChip, Number> MAX_LABEL_WIDTH =
                new CssMetaData<>("-rx-chip-max-label-width",
                        SizeConverter.getInstance(), Region.USE_COMPUTED_SIZE) {
                    @Override
                    public boolean isSettable(RXChip chip) {
                        return !chip.maxLabelWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXChip chip) {
                        return (StyleableProperty<Number>) chip.maxLabelWidthProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(ButtonBase.getClassCssMetaData());
            styleables.add(RIPPLE_FILL);
            styleables.add(RIPPLE_OPACITY);
            styleables.add(RIPPLE_ENABLED);
            styleables.add(STATE_OVERLAY_ENABLED);
            styleables.add(MAX_LABEL_WIDTH);
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
