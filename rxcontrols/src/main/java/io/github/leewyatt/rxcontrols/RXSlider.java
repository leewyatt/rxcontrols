package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXSliderSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.PaintConverter;
import javafx.scene.control.Skin;
import javafx.scene.control.Slider;
import javafx.scene.paint.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Material single-value slider built on {@link Slider}: it inherits the
 * battle-tested {@code value} / {@code min} / {@code max} / {@code snapToTicks}
 * / tick / {@code labelFormatter} model and replaces only the skin, adding a
 * Material thumb state-layer, an in-skin value indicator bubble, and a
 * self-rendered tick scale (no {@code NumberAxis}).
 *
 * <p>The control keeps the inherited interaction contract: {@code valueChanging}
 * is {@code true} only while the thumb is dragged with the pointer and
 * {@code false} on release (track clicks and the keyboard commit discretely and
 * do not flip it), so a consumer can mirror external state while not changing
 * and commit on the {@code true -> false} transition.</p>
 *
 * <p>The default style class is {@code rx-slider}; the inherited {@code slider}
 * class is intentionally dropped so the modena {@code .slider} visuals do not
 * leak into the self-built skin.</p>
 */
public class RXSlider extends Slider {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-slider";

    /**
     * Default value indicator display policy.
     */
    public static final RXSliderIndicatorDisplay DEFAULT_INDICATOR_DISPLAY =
            RXSliderIndicatorDisplay.DRAGGING;

    /**
     * Default value indicator position relative to the track.
     */
    public static final RXSliderIndicatorPosition DEFAULT_INDICATOR_POSITION =
            RXSliderIndicatorPosition.ABOVE;

    /**
     * Default for whether interaction feedback and indicator transitions are
     * animated.
     */
    public static final boolean DEFAULT_ANIMATED = true;

    // ==================== Constructors ====================

    /**
     * Creates a slider over the default {@code 0..100} range with value
     * {@code 0}.
     */
    public RXSlider() {
        super();
        getStyleClass().setAll(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a slider over the given range with the given initial value.
     *
     * @param min   the range minimum
     * @param max   the range maximum
     * @param value the initial value
     */
    public RXSlider(@NamedArg("min") double min,
                    @NamedArg("max") double max,
                    @NamedArg("value") double value) {
        super(min, max, value);
        getStyleClass().setAll(DEFAULT_STYLE_CLASS);
    }

    /** {@inheritDoc} */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXSliderSkin(this);
    }

    /** {@inheritDoc} */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Indicator Display ====================

    private final ObjectProperty<RXSliderIndicatorDisplay> indicatorDisplay =
            new StyleableObjectProperty<>(DEFAULT_INDICATOR_DISPLAY) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, RXSliderIndicatorDisplay> getCssMetaData() {
                    return StyleableProperties.INDICATOR_DISPLAY;
                }

                @Override
                public Object getBean() {
                    return RXSlider.this;
                }

                @Override
                public String getName() {
                    return "indicatorDisplay";
                }
            };

    /**
     * Value indicator display policy. Initial value is
     * {@link #DEFAULT_INDICATOR_DISPLAY}; the skin reads {@code null} as the
     * default.
     *
     * @return the indicator display property
     */
    public final ObjectProperty<RXSliderIndicatorDisplay> indicatorDisplayProperty() {
        return indicatorDisplay;
    }

    /**
     * Returns the value indicator display policy.
     *
     * @return the indicator display policy, or {@code null}
     */
    public final RXSliderIndicatorDisplay getIndicatorDisplay() {
        return indicatorDisplay.get();
    }

    /**
     * Sets the value indicator display policy.
     *
     * @param value the indicator display policy, or {@code null} for the default
     */
    public final void setIndicatorDisplay(RXSliderIndicatorDisplay value) {
        indicatorDisplay.set(value);
    }

    // ==================== Indicator Position ====================

    private final ObjectProperty<RXSliderIndicatorPosition> indicatorPosition =
            new StyleableObjectProperty<>(DEFAULT_INDICATOR_POSITION) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, RXSliderIndicatorPosition> getCssMetaData() {
                    return StyleableProperties.INDICATOR_POSITION;
                }

                @Override
                public Object getBean() {
                    return RXSlider.this;
                }

                @Override
                public String getName() {
                    return "indicatorPosition";
                }
            };

    /**
     * Side of the track on which the value indicator bubble is placed. Initial
     * value is {@link #DEFAULT_INDICATOR_POSITION}; the skin reads {@code null}
     * as the default.
     *
     * @return the indicator position property
     */
    public final ObjectProperty<RXSliderIndicatorPosition> indicatorPositionProperty() {
        return indicatorPosition;
    }

    /**
     * Returns the value indicator position.
     *
     * @return the indicator position, or {@code null}
     */
    public final RXSliderIndicatorPosition getIndicatorPosition() {
        return indicatorPosition.get();
    }

    /**
     * Sets the value indicator position.
     *
     * @param value the indicator position, or {@code null} for the default
     */
    public final void setIndicatorPosition(RXSliderIndicatorPosition value) {
        indicatorPosition.set(value);
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
                    return RXSlider.this;
                }

                @Override
                public String getName() {
                    return "rippleEnabled";
                }
            };

    /**
     * Gates the optional bounded press ink on the thumb. Initial value is
     * {@link RXRipplePane#DEFAULT_RIPPLE_ENABLED}. The skin feeds this to the
     * thumb ink; turning it off does not affect the state-layer halo.
     *
     * @return the ripple-enabled property
     */
    public final BooleanProperty rippleEnabledProperty() {
        return rippleEnabled;
    }

    /**
     * Returns whether the press ink is enabled.
     *
     * @return whether the press ink is enabled
     */
    public final boolean isRippleEnabled() {
        return rippleEnabled.get();
    }

    /**
     * Sets whether the press ink is enabled.
     *
     * @param value {@code true} to enable the press ink
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
                    return RXSlider.this;
                }

                @Override
                public String getName() {
                    return "stateOverlayEnabled";
                }
            };

    /**
     * Gates the unbounded state-layer halo on the thumb (the hover / focus /
     * pressed / dragged tint). Initial value is
     * {@link RXRipplePane#DEFAULT_STATE_OVERLAY_ENABLED}. Turning it off only
     * hides the halo and does not affect the press ink.
     *
     * @return the state-overlay-enabled property
     */
    public final BooleanProperty stateOverlayEnabledProperty() {
        return stateOverlayEnabled;
    }

    /**
     * Returns whether the state-layer halo may show.
     *
     * @return whether the state-layer halo may show
     */
    public final boolean isStateOverlayEnabled() {
        return stateOverlayEnabled.get();
    }

    /**
     * Sets whether the state-layer halo may show.
     *
     * @param value {@code true} to allow the state-layer halo
     */
    public final void setStateOverlayEnabled(boolean value) {
        stateOverlayEnabled.set(value);
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
                    return RXSlider.this;
                }

                @Override
                public String getName() {
                    return "rippleFill";
                }
            };

    /**
     * Fill used for the thumb feedback (the state-layer halo and the optional
     * press ink). Initial value is {@link RXRipplePane#DEFAULT_RIPPLE_FILL};
     * setting {@code null} renders no fill (transparent) per the JavaFX
     * convention. The CSS key {@code -rx-ripple-fill} matches {@code RXButton}
     * and {@code RXRipplePane}, so ripple CSS is portable across controls.
     *
     * @return the ripple fill property
     */
    public final ObjectProperty<Paint> rippleFillProperty() {
        return rippleFill;
    }

    /**
     * Returns the thumb feedback fill.
     *
     * @return the thumb feedback fill, or {@code null}
     */
    public final Paint getRippleFill() {
        return rippleFill.get();
    }

    /**
     * Sets the thumb feedback fill.
     *
     * @param value the thumb feedback fill, or {@code null} for no fill
     */
    public final void setRippleFill(Paint value) {
        rippleFill.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated =
            new StyleableBooleanProperty(DEFAULT_ANIMATED) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.ANIMATED;
                }

                @Override
                public Object getBean() {
                    return RXSlider.this;
                }

                @Override
                public String getName() {
                    return "animated";
                }
            };

    /**
     * Whether the value indicator show / hide transition is animated. Initial
     * value is {@link #DEFAULT_ANIMATED}.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether interaction feedback is animated.
     *
     * @return whether interaction feedback is animated
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether interaction feedback is animated.
     *
     * @param value {@code true} to animate interaction feedback
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXSlider, RXSliderIndicatorDisplay> INDICATOR_DISPLAY =
                new CssMetaData<>("-rx-indicator-display",
                        new EnumConverter<>(RXSliderIndicatorDisplay.class), DEFAULT_INDICATOR_DISPLAY) {
                    @Override
                    public boolean isSettable(RXSlider slider) {
                        return !slider.indicatorDisplay.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<RXSliderIndicatorDisplay> getStyleableProperty(RXSlider slider) {
                        return (StyleableProperty<RXSliderIndicatorDisplay>) slider.indicatorDisplayProperty();
                    }
                };

        private static final CssMetaData<RXSlider, RXSliderIndicatorPosition> INDICATOR_POSITION =
                new CssMetaData<>("-rx-indicator-position",
                        new EnumConverter<>(RXSliderIndicatorPosition.class), DEFAULT_INDICATOR_POSITION) {
                    @Override
                    public boolean isSettable(RXSlider slider) {
                        return !slider.indicatorPosition.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<RXSliderIndicatorPosition> getStyleableProperty(RXSlider slider) {
                        return (StyleableProperty<RXSliderIndicatorPosition>) slider.indicatorPositionProperty();
                    }
                };

        private static final CssMetaData<RXSlider, Boolean> RIPPLE_ENABLED =
                new CssMetaData<>("-rx-ripple-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_ENABLED) {
                    @Override
                    public boolean isSettable(RXSlider slider) {
                        return !slider.rippleEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXSlider slider) {
                        return (StyleableProperty<Boolean>) slider.rippleEnabledProperty();
                    }
                };

        private static final CssMetaData<RXSlider, Boolean> RIPPLE_STATE_OVERLAY_ENABLED =
                new CssMetaData<>("-rx-ripple-state-overlay-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED) {
                    @Override
                    public boolean isSettable(RXSlider slider) {
                        return !slider.stateOverlayEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXSlider slider) {
                        return (StyleableProperty<Boolean>) slider.stateOverlayEnabledProperty();
                    }
                };

        private static final CssMetaData<RXSlider, Paint> RIPPLE_FILL =
                new CssMetaData<>("-rx-ripple-fill",
                        PaintConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_FILL) {
                    @Override
                    public boolean isSettable(RXSlider slider) {
                        return !slider.rippleFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXSlider slider) {
                        return (StyleableProperty<Paint>) slider.rippleFillProperty();
                    }
                };

        private static final CssMetaData<RXSlider, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated",
                        BooleanConverter.getInstance(), DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXSlider slider) {
                        return !slider.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXSlider slider) {
                        return (StyleableProperty<Boolean>) slider.animatedProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Slider.getClassCssMetaData());
            styleables.add(INDICATOR_DISPLAY);
            styleables.add(INDICATOR_POSITION);
            styleables.add(RIPPLE_ENABLED);
            styleables.add(RIPPLE_STATE_OVERLAY_ENABLED);
            styleables.add(RIPPLE_FILL);
            styleables.add(ANIMATED);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class, the inherited
     * {@link Slider} metadata plus the slider's own properties.
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
