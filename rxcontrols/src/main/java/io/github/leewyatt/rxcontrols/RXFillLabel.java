package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import io.github.leewyatt.rxcontrols.enums.RXAnimationTrigger;
import io.github.leewyatt.rxcontrols.internal.CoercedStyleableProperty;
import io.github.leewyatt.rxcontrols.internal.KeywordConverter;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXFillLabelSkin;
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
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.layout.CornerRadii;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Label that fills its background with an animated sweep while hovered or
 * pressed — the non-interactive counterpart of {@link RXFillButton} for
 * decorative text such as tags, list entries or headings.
 *
 * <p>The fill is a decoration layer below the text, clipped to the label's
 * painted geometry; the sweep geometry is selected by
 * {@link #fillAnimationProperty() fillAnimation} and the trigger state by
 * {@link #animationTriggerProperty() animationTrigger}. Turning the trigger
 * state off reverses the sweep from its current progress, with duration
 * proportional to the remaining distance.</p>
 *
 * <p>While any fill is visible the {@code :filling} pseudo-class is active,
 * letting stylesheets restyle the filled state — for example
 * {@code .rx-fill-label:filling { -fx-text-fill: white; }}. Unlike
 * {@link RXFillButton} this control keeps the full {@link Label} semantics:
 * it is not focus-traversable, fires no action and reports the label
 * accessible role.</p>
 */
public class RXFillLabel extends Label {

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

    private static final String DEFAULT_STYLE_CLASS = "rx-fill-label";

    // ==================== Constructors ====================

    /**
     * Creates a fill label with an empty text caption.
     */
    public RXFillLabel() {
        initialize();
    }

    /**
     * Creates a fill label with the given text caption.
     *
     * @param text the text caption, or {@code null}
     */
    public RXFillLabel(@NamedArg("text") String text) {
        super(text);
        initialize();
    }

    /**
     * Creates a fill label with the given text caption and graphic.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the graphic node, or {@code null}
     */
    public RXFillLabel(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
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
        return new RXFillLabelSkin(this);
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

    // ==================== Fill Animation ====================

    private final ObjectProperty<FillAnimation> fillAnimation =
            new StyleableObjectProperty<>(DEFAULT_FILL_ANIMATION) {
                @Override
                public CssMetaData<? extends Styleable, FillAnimation> getCssMetaData() {
                    return StyleableProperties.FILL_ANIMATION;
                }

                @Override
                public Object getBean() {
                    return RXFillLabel.this;
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
                    return RXFillLabel.this;
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
                    return RXFillLabel.this;
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
                    return RXFillLabel.this;
                }

                @Override
                public String getName() {
                    return "fillInsets";
                }
            };

    /**
     * Insets of the fill area measured from the label bounds, mirroring the
     * {@code -fx-background-insets} convention: zero fills the full bounds
     * (covering a border), positive values shrink the fill inward, negative
     * values let it bleed outside the bounds as a pure visual effect that
     * never affects the label's size. The default {@code null} follows the
     * inner edge of the label's real border automatically.
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
     * and gets coerced into {@link CornerRadii} here.
     */
    private final CoercedStyleableProperty<Insets, CornerRadii> fillRadiusCss =
            new CoercedStyleableProperty<>(fillRadius, StyleableProperties.FILL_RADIUS,
                    RXFillLabel::radiiFromInsets, RXFillLabel::insetsFromRadii);

    /**
     * Explicit corner radii for the fill area. When set, the fill is clipped
     * to a single rounded rectangle with these radii (and the
     * {@link #fillInsetsProperty() fillInsets} box), ignoring the host
     * background layers entirely. The default {@code null} mirrors the
     * label's painted background geometry. From CSS, {@code -rx-fill-radius}
     * accepts 1 to 4 sizes in {@code border-radius} order (top-left,
     * top-right, bottom-right, bottom-left); a negative value selects
     * automatic mirroring. Ignored when the label uses a {@code shape}.
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

        private static final CssMetaData<RXFillLabel, FillAnimation> FILL_ANIMATION =
                new CssMetaData<>("-rx-fill-animation",
                        new KeywordConverter<>(FillAnimation::valueOf), DEFAULT_FILL_ANIMATION) {
                    @Override
                    public boolean isSettable(RXFillLabel label) {
                        return !label.fillAnimation.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<FillAnimation> getStyleableProperty(RXFillLabel label) {
                        return (StyleableProperty<FillAnimation>) label.fillAnimationProperty();
                    }
                };

        private static final CssMetaData<RXFillLabel, RXAnimationTrigger> ANIMATION_TRIGGER =
                new CssMetaData<>("-rx-animation-trigger",
                        new EnumConverter<>(RXAnimationTrigger.class), DEFAULT_ANIMATION_TRIGGER) {
                    @Override
                    public boolean isSettable(RXFillLabel label) {
                        return !label.animationTrigger.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<RXAnimationTrigger> getStyleableProperty(RXFillLabel label) {
                        return (StyleableProperty<RXAnimationTrigger>) label.animationTriggerProperty();
                    }
                };

        private static final CssMetaData<RXFillLabel, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXFillLabel label) {
                        return !label.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXFillLabel label) {
                        return (StyleableProperty<Duration>) label.animationDurationProperty();
                    }
                };

        private static final CssMetaData<RXFillLabel, Insets> FILL_INSETS =
                new CssMetaData<>("-rx-fill-insets",
                        InsetsConverter.getInstance(), null) {
                    @Override
                    public boolean isSettable(RXFillLabel label) {
                        return !label.fillInsets.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Insets> getStyleableProperty(RXFillLabel label) {
                        return (StyleableProperty<Insets>) label.fillInsetsProperty();
                    }
                };

        private static final CssMetaData<RXFillLabel, Insets> FILL_RADIUS =
                new CssMetaData<>("-rx-fill-radius",
                        InsetsConverter.getInstance(), null) {
                    @Override
                    public boolean isSettable(RXFillLabel label) {
                        return !label.fillRadius.isBound();
                    }

                    @Override
                    public StyleableProperty<Insets> getStyleableProperty(RXFillLabel label) {
                        return label.fillRadiusCss;
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Label.getClassCssMetaData());
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
