package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import io.github.leewyatt.rxcontrols.internal.CoercedStyleableProperty;
import io.github.leewyatt.rxcontrols.internal.CornerRadiiCoercion;
import io.github.leewyatt.rxcontrols.internal.KeywordConverter;
import io.github.leewyatt.rxcontrols.skins.RXFillButtonSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.InsetsConverter;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.layout.CornerRadii;

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
public class RXFillButton extends RXAnimatedButton {

    // ==================== Constants ====================

    /**
     * Default fill animation.
     */
    public static final FillAnimation DEFAULT_FILL_ANIMATION = FillAnimation.LEFT_TO_RIGHT;

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

    // ==================== Fill Corner Radius ====================

    private final ObjectProperty<CornerRadii> fillCornerRadius =
            new SimpleObjectProperty<>(this, "fillCornerRadius", null);

    /**
     * CSS facade for {@link #fillCornerRadius}: the engine can only deliver
     * multi-value custom properties through the special-cased
     * {@code InsetsConverter} (RT-37727), so the CSS type is {@link Insets}
     * and gets coerced into {@link CornerRadii} here. CSS order follows the
     * {@code border-radius} convention: top-left, top-right, bottom-right,
     * bottom-left; any negative component means automatic mirroring.
     */
    private final CoercedStyleableProperty<Insets, CornerRadii> fillCornerRadiusCss =
            new CoercedStyleableProperty<>(fillCornerRadius, StyleableProperties.FILL_CORNER_RADIUS,
                    CornerRadiiCoercion::fromInsets, CornerRadiiCoercion::toInsets);

    /**
     * Explicit corner radii for the fill area. When set, the fill is clipped
     * to a single rounded rectangle with these radii (and the
     * {@link #fillInsetsProperty() fillInsets} box), ignoring the host
     * background layers entirely — the escape hatch for stateful multi-layer
     * backgrounds such as focus rings. The default {@code null} mirrors the
     * button's painted background geometry. From CSS,
     * {@code -rx-fill-corner-radius} accepts 1 to 4 sizes in
     * {@code border-radius} order (top-left, top-right, bottom-right,
     * bottom-left); a negative value selects automatic mirroring. Ignored when
     * the button uses a {@code shape}.
     *
     * @return the fill corner radius property
     */
    public final ObjectProperty<CornerRadii> fillCornerRadiusProperty() {
        return fillCornerRadius;
    }

    /**
     * Returns the fill corner radius.
     *
     * @return the fill corner radius, or {@code null} for automatic mirroring
     */
    public final CornerRadii getFillCornerRadius() {
        return fillCornerRadius.get();
    }

    /**
     * Sets the fill corner radius.
     *
     * @param value the fill corner radius, or {@code null} for automatic
     *              mirroring
     */
    public final void setFillCornerRadius(CornerRadii value) {
        fillCornerRadius.set(value);
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

        private static final CssMetaData<RXFillButton, Insets> FILL_CORNER_RADIUS =
                new CssMetaData<>("-rx-fill-corner-radius",
                        InsetsConverter.getInstance(), null) {
                    @Override
                    public boolean isSettable(RXFillButton button) {
                        return !button.fillCornerRadius.isBound();
                    }

                    @Override
                    public StyleableProperty<Insets> getStyleableProperty(RXFillButton button) {
                        return button.fillCornerRadiusCss;
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(RXAnimatedButton.getClassCssMetaData());
            styleables.add(FILL_ANIMATION);
            styleables.add(FILL_INSETS);
            styleables.add(FILL_CORNER_RADIUS);
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
