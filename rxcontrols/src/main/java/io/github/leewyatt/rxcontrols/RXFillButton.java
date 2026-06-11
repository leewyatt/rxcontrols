package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import io.github.leewyatt.rxcontrols.enums.RXAnimationTrigger;
import io.github.leewyatt.rxcontrols.internal.CoercedStyleableProperty;
import io.github.leewyatt.rxcontrols.internal.KeywordConverter;
import io.github.leewyatt.rxcontrols.skins.RXFillButtonSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.InsetsConverter;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.layout.CornerRadii;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Button that fills its background with an animated sweep while hovered or
 * pressed.
 *
 * <p>The fill is a decoration layer between the button background and the
 * label, clipped to the button's painted geometry; the sweep geometry is
 * selected by {@link #fillAnimationProperty() fillAnimation} (built-in
 * presets via the {@code -rx-fill-animation} CSS keyword or the
 * {@link FillAnimation} constants, custom variants by constructing your own
 * instance) and the trigger state by
 * {@link #animationTriggerProperty() animationTrigger}. Turning the trigger
 * state off reverses the sweep from its current progress, with duration
 * proportional to the remaining distance. The ripple feedback inherited from
 * {@link RXButton} stays available and composes with the fill.</p>
 *
 * <p>While any fill is visible the {@code :filling} pseudo-class is active,
 * letting stylesheets restyle the filled state — for example
 * {@code .rx-fill-button:filling { -fx-text-fill: white; }} or recoloring a
 * shape-based graphic. It activates as soon as the fill starts sweeping in
 * and deactivates when the reverse sweep finishes.</p>
 */
public class RXFillButton extends RXButton {

    // ==================== Constants ====================

    /**
     * Default fill animation.
     */
    public static final FillAnimation DEFAULT_FILL_ANIMATION = FillAnimation.LEFT_TO_RIGHT;

    /**
     * Default animation trigger.
     */
    public static final RXAnimationTrigger DEFAULT_ANIMATION_TRIGGER = RXAnimationTrigger.HOVER;

    /**
     * Default animation duration.
     */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);

    private static final String DEFAULT_STYLE_CLASS = "rx-fill-button";

    // ==================== Constructors ====================

    /**
     * Creates a fill button with an empty text caption.
     */
    public RXFillButton() {
        initialize();
    }

    /**
     * Creates a fill button with the given text caption.
     *
     * @param text the text caption, or {@code null}
     */
    public RXFillButton(@NamedArg("text") String text) {
        super(text);
        initialize();
    }

    /**
     * Creates a fill button with the given text caption and graphic.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the graphic node, or {@code null}
     */
    public RXFillButton(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
        super(text, graphic);
        initialize();
    }

    private void initialize() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates the default skin with the fill decoration layer.
     *
     * @return the default skin
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXFillButtonSkin(this);
    }

    // ==================== Fill Animation ====================

    private final ObjectProperty<FillAnimation> fillAnimation =
            new StyleableObjectProperty<>(DEFAULT_FILL_ANIMATION) {
                @Override
                public CssMetaData<? extends Styleable, FillAnimation> getCssMetaData() {
                    return StyleableProperties.FILL_ANIMATION;
                }

                @Override
                public Object getBean() {
                    return RXFillButton.this;
                }

                @Override
                public String getName() {
                    return "fillAnimation";
                }
            };

    /**
     * Sweep geometry of the fill animation: a {@link FillAnimation} preset
     * constant, a parameterized instance such as
     * {@code new FillAnimZigzag(6)}, or a custom implementation. From CSS,
     * {@code -rx-fill-animation} selects presets by keyword. A {@code null}
     * or unknown-keyword value falls back to
     * {@link #DEFAULT_FILL_ANIMATION} at render time.
     *
     * @return the fill animation property
     */
    public final ObjectProperty<FillAnimation> fillAnimationProperty() {
        return fillAnimation;
    }

    /**
     * Returns the fill animation.
     *
     * @return the fill animation, or {@code null}
     */
    public final FillAnimation getFillAnimation() {
        return fillAnimation.get();
    }

    /**
     * Sets the fill animation.
     *
     * @param value the fill animation, or {@code null} for the default
     */
    public final void setFillAnimation(FillAnimation value) {
        fillAnimation.set(value);
    }

    // ==================== Animation Trigger ====================

    private final ObjectProperty<RXAnimationTrigger> animationTrigger =
            new StyleableObjectProperty<>(DEFAULT_ANIMATION_TRIGGER) {
                @Override
                public CssMetaData<? extends Styleable, RXAnimationTrigger> getCssMetaData() {
                    return StyleableProperties.ANIMATION_TRIGGER;
                }

                @Override
                public Object getBean() {
                    return RXFillButton.this;
                }

                @Override
                public String getName() {
                    return "animationTrigger";
                }
            };

    /**
     * State source driving the fill animation. A {@code null} value falls back
     * to {@link #DEFAULT_ANIMATION_TRIGGER} at render time.
     *
     * @return the animation trigger property
     */
    public final ObjectProperty<RXAnimationTrigger> animationTriggerProperty() {
        return animationTrigger;
    }

    /**
     * Returns the animation trigger.
     *
     * @return the animation trigger
     */
    public final RXAnimationTrigger getAnimationTrigger() {
        return animationTrigger.get();
    }

    /**
     * Sets the animation trigger.
     *
     * @param value the animation trigger
     */
    public final void setAnimationTrigger(RXAnimationTrigger value) {
        animationTrigger.set(value);
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
                    return RXFillButton.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of a full fill sweep. {@code Duration.ZERO} disables the
     * animation (the fill snaps to the trigger state); {@code null}, negative
     * or otherwise unusable values fall back to
     * {@link #DEFAULT_ANIMATION_DURATION} at render time.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the animation duration.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the animation duration.
     *
     * @param value the animation duration
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Fill Insets ====================

    private final ObjectProperty<Insets> fillInsets =
            new StyleableObjectProperty<>(null) {
                @Override
                public CssMetaData<? extends Styleable, Insets> getCssMetaData() {
                    return StyleableProperties.FILL_INSETS;
                }

                @Override
                public Object getBean() {
                    return RXFillButton.this;
                }

                @Override
                public String getName() {
                    return "fillInsets";
                }
            };

    /**
     * Insets of the fill area measured from the button bounds, mirroring the
     * {@code -fx-background-insets} convention: zero fills the full bounds
     * (covering a border), positive values shrink the fill inward, negative
     * values let it bleed outside the bounds as a pure visual effect that
     * never affects the button's size. The default {@code null} follows the
     * inner edge of the button's real border automatically. Faux borders
     * painted as layered background fills and shape-based buttons cannot be
     * inset.
     *
     * @return the fill insets property
     */
    public final ObjectProperty<Insets> fillInsetsProperty() {
        return fillInsets;
    }

    /**
     * Returns the fill insets.
     *
     * @return the fill insets, or {@code null} for automatic border following
     */
    public final Insets getFillInsets() {
        return fillInsets.get();
    }

    /**
     * Sets the fill insets.
     *
     * @param value the fill insets, or {@code null} for automatic border
     *              following
     */
    public final void setFillInsets(Insets value) {
        fillInsets.set(value);
    }

    // ==================== Fill Radius ====================

    private final ObjectProperty<CornerRadii> fillRadius =
            new SimpleObjectProperty<>(this, "fillRadius", null);

    /**
     * CSS facade for {@link #fillRadius}: the engine can only deliver
     * multi-value custom properties through the special-cased
     * {@code InsetsConverter} (RT-37727), so the CSS type is {@link Insets}
     * and gets coerced into {@link CornerRadii} here. CSS order follows the
     * {@code border-radius} convention: top-left, top-right, bottom-right,
     * bottom-left; any negative component means automatic mirroring.
     */
    private final CoercedStyleableProperty<Insets, CornerRadii> fillRadiusCss =
            new CoercedStyleableProperty<>(fillRadius, StyleableProperties.FILL_RADIUS,
                    RXFillButton::radiiFromInsets, RXFillButton::insetsFromRadii);

    /**
     * Explicit corner radii for the fill area. When set, the fill is clipped
     * to a single rounded rectangle with these radii (and the
     * {@link #fillInsetsProperty() fillInsets} box), ignoring the host
     * background layers entirely — the escape hatch for stateful multi-layer
     * backgrounds such as focus rings. The default {@code null} mirrors the
     * button's painted background geometry. From CSS, {@code -rx-fill-radius}
     * accepts 1 to 4 sizes in {@code border-radius} order (top-left,
     * top-right, bottom-right, bottom-left); a negative value selects
     * automatic mirroring. Ignored when the button uses a {@code shape}.
     *
     * @return the fill radius property
     */
    public final ObjectProperty<CornerRadii> fillRadiusProperty() {
        return fillRadius;
    }

    /**
     * Returns the fill radius.
     *
     * @return the fill radius, or {@code null} for automatic mirroring
     */
    public final CornerRadii getFillRadius() {
        return fillRadius.get();
    }

    /**
     * Sets the fill radius.
     *
     * @param value the fill radius, or {@code null} for automatic mirroring
     */
    public final void setFillRadius(CornerRadii value) {
        fillRadius.set(value);
    }

    private static CornerRadii radiiFromInsets(Insets value) {
        if (value == null
                || value.getTop() < 0.0 || value.getRight() < 0.0
                || value.getBottom() < 0.0 || value.getLeft() < 0.0) {
            return null;
        }
        if (value.getTop() == 0.0 && value.getRight() == 0.0
                && value.getBottom() == 0.0 && value.getLeft() == 0.0) {
            return CornerRadii.EMPTY;
        }
        return new CornerRadii(value.getTop(), value.getRight(),
                value.getBottom(), value.getLeft(), false);
    }

    private static Insets insetsFromRadii(CornerRadii value) {
        if (value == null) {
            return null;
        }
        return new Insets(value.getTopLeftHorizontalRadius(),
                value.getTopRightHorizontalRadius(),
                value.getBottomRightHorizontalRadius(),
                value.getBottomLeftHorizontalRadius());
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXFillButton, FillAnimation> FILL_ANIMATION =
                new CssMetaData<>("-rx-fill-animation",
                        new KeywordConverter<>(FillAnimation::valueOf), DEFAULT_FILL_ANIMATION) {
                    @Override
                    public boolean isSettable(RXFillButton button) {
                        return !button.fillAnimation.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<FillAnimation> getStyleableProperty(RXFillButton button) {
                        return (StyleableProperty<FillAnimation>) button.fillAnimationProperty();
                    }
                };

        private static final CssMetaData<RXFillButton, RXAnimationTrigger> ANIMATION_TRIGGER =
                new CssMetaData<>("-rx-animation-trigger",
                        new EnumConverter<>(RXAnimationTrigger.class), DEFAULT_ANIMATION_TRIGGER) {
                    @Override
                    public boolean isSettable(RXFillButton button) {
                        return !button.animationTrigger.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<RXAnimationTrigger> getStyleableProperty(RXFillButton button) {
                        return (StyleableProperty<RXAnimationTrigger>) button.animationTriggerProperty();
                    }
                };

        private static final CssMetaData<RXFillButton, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXFillButton button) {
                        return !button.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXFillButton button) {
                        return (StyleableProperty<Duration>) button.animationDurationProperty();
                    }
                };

        private static final CssMetaData<RXFillButton, Insets> FILL_INSETS =
                new CssMetaData<>("-rx-fill-insets",
                        InsetsConverter.getInstance(), null) {
                    @Override
                    public boolean isSettable(RXFillButton button) {
                        return !button.fillInsets.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Insets> getStyleableProperty(RXFillButton button) {
                        return (StyleableProperty<Insets>) button.fillInsetsProperty();
                    }
                };

        private static final CssMetaData<RXFillButton, Insets> FILL_RADIUS =
                new CssMetaData<>("-rx-fill-radius",
                        InsetsConverter.getInstance(), null) {
                    @Override
                    public boolean isSettable(RXFillButton button) {
                        return !button.fillRadius.isBound();
                    }

                    @Override
                    public StyleableProperty<Insets> getStyleableProperty(RXFillButton button) {
                        return button.fillRadiusCss;
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(RXButton.getClassCssMetaData());
            styleables.add(FILL_ANIMATION);
            styleables.add(ANIMATION_TRIGGER);
            styleables.add(ANIMATION_DURATION);
            styleables.add(FILL_INSETS);
            styleables.add(FILL_RADIUS);
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
