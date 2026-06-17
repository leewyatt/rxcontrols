package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXAnimationEvent;
import io.github.leewyatt.rxcontrols.internal.CoercedStyleableProperty;
import io.github.leewyatt.rxcontrols.internal.CornerRadiiCoercion;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXToggleButtonSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.InsetsConverter;
import javafx.css.converter.PaintConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Standard toggle button with a built-in bounded ripple as pressed feedback.
 *
 * <p>Behaviour is the plain {@link ToggleButton} toggle semantics: when added
 * to a {@code ToggleGroup} the selected button can still be deselected by
 * re-clicking it. For the radio-like variant that refuses to deselect the
 * active button, use {@link RXRadioToggleButton}.</p>
 *
 * <p>The ripple lifecycle follows the button's {@code armed} state: a valid
 * primary press starts a ripple at the pointer location (or the center when
 * {@link #rippleCenteredProperty() rippleCentered} is true), keyboard
 * activation starts a centered ripple, and disarming fades the active ripple
 * out. Programmatic {@link #fire()} does not arm the button and therefore
 * shows no ripple.</p>
 *
 * <p>The ripple visual contract (full-bounds layer, shape or background
 * geometry clip) matches {@link RXRipplePane}; the ripple properties share
 * names, CSS properties and defaults with that container. A low-opacity hover
 * state overlay tints the button while the pointer is inside and
 * {@link #hoverOverlayEnabledProperty() hoverOverlayEnabled} is true.
 * {@link #rippleCornerRadiusProperty() rippleCornerRadius} overrides the
 * mirrored clip corners with explicit radii.</p>
 */
public class RXToggleButton extends ToggleButton {

    private static final String DEFAULT_STYLE_CLASS = "rx-toggle-button";

    // ==================== Constructors ====================

    /**
     * Creates a toggle button with an empty text caption.
     */
    public RXToggleButton() {
        initialize();
    }

    /**
     * Creates a toggle button with the given text caption.
     *
     * @param text the text caption, or {@code null}
     */
    public RXToggleButton(@NamedArg("text") String text) {
        super(text);
        initialize();
    }

    /**
     * Creates a toggle button with the given text caption and graphic.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the graphic node, or {@code null}
     */
    public RXToggleButton(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
        super(text, graphic);
        initialize();
    }

    private void initialize() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates the default skin with the built-in ripple layer.
     *
     * @return the default skin
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXToggleButtonSkin(this);
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

    // ==================== Programmatic Playback ====================

    /**
     * Plays one centered ripple (press and immediate release). No effect when
     * ripples are disabled or the host is disabled.
     */
    public final void playRipple() {
        fireEvent(new RXAnimationEvent(RXAnimationEvent.PLAY_RIPPLE));
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
                    return RXToggleButton.this;
                }

                @Override
                public String getName() {
                    return "rippleFill";
                }
            };

    /**
     * Fill used for newly created ripple circles. Initial value is
     * {@link RXRipplePane#DEFAULT_RIPPLE_FILL}; setting {@code null} renders
     * no fill (transparent) per the JavaFX {@code Shape.setFill} convention.
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
                    return RXToggleButton.this;
                }

                @Override
                public String getName() {
                    return "rippleOpacity";
                }
            };

    /**
     * Peak opacity for newly created ripple circles. Values outside
     * {@code [0, 1]} are stored as-is and clamped at render time. Initial
     * value is {@link RXRipplePane#DEFAULT_RIPPLE_OPACITY}.
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
                    return RXToggleButton.this;
                }

                @Override
                public String getName() {
                    return "rippleEnabled";
                }
            };

    /**
     * Whether user interaction creates press ripples. Turning this off
     * immediately clears existing ripple nodes and running ripple animations.
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

    // ==================== Hover Overlay Enabled ====================

    private final BooleanProperty hoverOverlayEnabled =
            new StyleableBooleanProperty(RXRipplePane.DEFAULT_HOVER_OVERLAY_ENABLED) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.RIPPLE_HOVER_OVERLAY_ENABLED;
                }

                @Override
                public Object getBean() {
                    return RXToggleButton.this;
                }

                @Override
                public String getName() {
                    return "hoverOverlayEnabled";
                }
            };

    /**
     * Whether the low-opacity hover state overlay may show while the pointer is
     * inside. The press ripple is unaffected (it stays gated only by
     * {@link #rippleEnabledProperty() rippleEnabled}). Initial value is
     * {@link RXRipplePane#DEFAULT_HOVER_OVERLAY_ENABLED}.
     *
     * @return the hover-overlay-enabled property
     */
    public final BooleanProperty hoverOverlayEnabledProperty() {
        return hoverOverlayEnabled;
    }

    /**
     * Returns whether the hover state overlay may show.
     *
     * @return whether the hover state overlay may show
     */
    public final boolean isHoverOverlayEnabled() {
        return hoverOverlayEnabled.get();
    }

    /**
     * Sets whether the hover state overlay may show.
     *
     * @param value {@code true} to allow the hover overlay
     */
    public final void setHoverOverlayEnabled(boolean value) {
        hoverOverlayEnabled.set(value);
    }

    // ==================== Ripple Centered ====================

    private final BooleanProperty rippleCentered =
            new StyleableBooleanProperty(RXRipplePane.DEFAULT_RIPPLE_CENTERED) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.RIPPLE_CENTERED;
                }

                @Override
                public Object getBean() {
                    return RXToggleButton.this;
                }

                @Override
                public String getName() {
                    return "rippleCentered";
                }
            };

    /**
     * Whether pointer-triggered ripples start from the button center instead
     * of the press location. Keyboard-triggered ripples always start from the
     * center.
     *
     * @return the ripple-centered property
     */
    public final BooleanProperty rippleCenteredProperty() {
        return rippleCentered;
    }

    /**
     * Returns whether pointer-triggered ripples start from the center.
     *
     * @return whether pointer-triggered ripples start from the center
     */
    public final boolean isRippleCentered() {
        return rippleCentered.get();
    }

    /**
     * Sets whether pointer-triggered ripples start from the center.
     *
     * @param value {@code true} to start pointer-triggered ripples from center
     */
    public final void setRippleCentered(boolean value) {
        rippleCentered.set(value);
    }

    // ==================== Ripple Corner Radius ====================

    private final ObjectProperty<CornerRadii> rippleCornerRadius =
            new SimpleObjectProperty<>(this, "rippleCornerRadius", null);

    /**
     * CSS facade for {@link #rippleCornerRadius}: the engine can only deliver
     * multi-value custom properties through the special-cased
     * {@code InsetsConverter} (RT-37727), so the CSS type is {@link Insets}
     * and gets coerced into {@link CornerRadii} here. CSS order follows the
     * {@code border-radius} convention: top-left, top-right, bottom-right,
     * bottom-left; any negative component means automatic mirroring.
     */
    private final CoercedStyleableProperty<Insets, CornerRadii> rippleCornerRadiusCss =
            new CoercedStyleableProperty<>(rippleCornerRadius, StyleableProperties.RIPPLE_CORNER_RADIUS,
                    CornerRadiiCoercion::fromInsets, CornerRadiiCoercion::toInsets);

    /**
     * Explicit corner radii for the ripple clip. When set, the ripple and hover
     * overlay are clipped to a single rounded rectangle with these radii,
     * ignoring the button background layers entirely — the escape hatch for
     * stateful multi-layer backgrounds such as focus rings. The default
     * {@code null} mirrors the button's painted background geometry. From CSS,
     * {@code -rx-ripple-corner-radius} accepts 1 to 4 sizes in
     * {@code border-radius} order (top-left, top-right, bottom-right,
     * bottom-left); a negative value selects automatic mirroring. Ignored when
     * the button uses a {@code shape}.
     *
     * @return the ripple corner radius property
     */
    public final ObjectProperty<CornerRadii> rippleCornerRadiusProperty() {
        return rippleCornerRadius;
    }

    /**
     * Returns the ripple corner radius.
     *
     * @return the ripple corner radius, or {@code null} for automatic mirroring
     */
    public final CornerRadii getRippleCornerRadius() {
        return rippleCornerRadius.get();
    }

    /**
     * Sets the ripple corner radius.
     *
     * @param value the ripple corner radius, or {@code null} for automatic
     *              mirroring
     */
    public final void setRippleCornerRadius(CornerRadii value) {
        rippleCornerRadius.set(value);
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXToggleButton, Paint> RIPPLE_FILL =
                new CssMetaData<>("-rx-ripple-fill",
                        PaintConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_FILL) {
                    @Override
                    public boolean isSettable(RXToggleButton button) {
                        return !button.rippleFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXToggleButton button) {
                        return (StyleableProperty<Paint>) button.rippleFillProperty();
                    }
                };

        private static final CssMetaData<RXToggleButton, Number> RIPPLE_OPACITY =
                new CssMetaData<>("-rx-ripple-opacity",
                        SizeConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_OPACITY) {
                    @Override
                    public boolean isSettable(RXToggleButton button) {
                        return !button.rippleOpacity.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXToggleButton button) {
                        return (StyleableProperty<Number>) button.rippleOpacityProperty();
                    }
                };

        private static final CssMetaData<RXToggleButton, Boolean> RIPPLE_ENABLED =
                new CssMetaData<>("-rx-ripple-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_ENABLED) {
                    @Override
                    public boolean isSettable(RXToggleButton button) {
                        return !button.rippleEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXToggleButton button) {
                        return (StyleableProperty<Boolean>) button.rippleEnabledProperty();
                    }
                };

        private static final CssMetaData<RXToggleButton, Boolean> RIPPLE_HOVER_OVERLAY_ENABLED =
                new CssMetaData<>("-rx-ripple-hover-overlay-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_HOVER_OVERLAY_ENABLED) {
                    @Override
                    public boolean isSettable(RXToggleButton button) {
                        return !button.hoverOverlayEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXToggleButton button) {
                        return (StyleableProperty<Boolean>) button.hoverOverlayEnabledProperty();
                    }
                };

        private static final CssMetaData<RXToggleButton, Boolean> RIPPLE_CENTERED =
                new CssMetaData<>("-rx-ripple-centered",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_CENTERED) {
                    @Override
                    public boolean isSettable(RXToggleButton button) {
                        return !button.rippleCentered.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXToggleButton button) {
                        return (StyleableProperty<Boolean>) button.rippleCenteredProperty();
                    }
                };

        private static final CssMetaData<RXToggleButton, Insets> RIPPLE_CORNER_RADIUS =
                new CssMetaData<>("-rx-ripple-corner-radius",
                        InsetsConverter.getInstance(), null) {
                    @Override
                    public boolean isSettable(RXToggleButton button) {
                        return !button.rippleCornerRadius.isBound();
                    }

                    @Override
                    public StyleableProperty<Insets> getStyleableProperty(RXToggleButton button) {
                        return button.rippleCornerRadiusCss;
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(ToggleButton.getClassCssMetaData());
            styleables.add(RIPPLE_FILL);
            styleables.add(RIPPLE_OPACITY);
            styleables.add(RIPPLE_ENABLED);
            styleables.add(RIPPLE_HOVER_OVERLAY_ENABLED);
            styleables.add(RIPPLE_CENTERED);
            styleables.add(RIPPLE_CORNER_RADIUS);
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
