package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.line.LineAnimation;
import io.github.leewyatt.rxcontrols.internal.KeywordConverter;
import io.github.leewyatt.rxcontrols.skins.RXLineLabelSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.SizeConverter;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Label decorating its content with animated lines while hovered or
 * pressed — the non-interactive counterpart of {@link RXLineButton} for
 * decorative text such as links-in-text, list entries or headings.
 *
 * <p>The lines are a pure decoration overlay: they take no part in size
 * computation or content layout, follow the bounds of the text and graphic,
 * and may extend beyond the label bounds (they are never clipped by the
 * label). The line geometry is selected by
 * {@link #lineAnimationProperty() lineAnimation} and the trigger state by
 * {@link #animationTriggerProperty() animationTrigger}. Turning the trigger
 * state off reverses the animation from its current progress, with duration
 * proportional to the remaining distance.</p>
 *
 * <p>While any line is visible the {@code :line-showing} pseudo-class is
 * active, letting stylesheets restyle that state — for example
 * {@code .rx-line-label:line-showing { -fx-text-fill: -rx-line-color; }}.
 * Unlike {@link RXLineButton} this control keeps the full {@link Label}
 * semantics: it is not focus-traversable, fires no action and reports the
 * label accessible role.</p>
 */
public class RXLineLabel extends RXAnimatedLabel {

    // ==================== Constants ====================

    /**
     * Default line animation.
     */
    public static final LineAnimation DEFAULT_LINE_ANIMATION = LineAnimation.UNDERLINE_CENTER_OUT;

    /**
     * Default line thickness in pixels.
     */
    public static final double DEFAULT_LINE_THICKNESS = 2.0;

    /**
     * Default gap between a resting line and the content bounds, in pixels.
     */
    public static final double DEFAULT_LINE_GAP = 2.0;

    private static final String DEFAULT_STYLE_CLASS = "rx-line-label";

    // ==================== Constructors ====================

    /**
     * Creates a line label with an empty text caption.
     */
    public RXLineLabel() {
        initialize();
    }

    /**
     * Creates a line label with the given text caption.
     *
     * @param text the text caption, or {@code null}
     */
    public RXLineLabel(@NamedArg("text") String text) {
        super(text);
        initialize();
    }

    /**
     * Creates a line label with the given text caption and graphic.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the graphic node, or {@code null}
     */
    public RXLineLabel(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
        super(text, graphic);
        initialize();
    }

    private void initialize() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates the default skin with the line decoration layer.
     *
     * @return the default skin
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXLineLabelSkin(this);
    }

    // ==================== Line Animation ====================

    private final ObjectProperty<LineAnimation> lineAnimation =
            new StyleableObjectProperty<>(DEFAULT_LINE_ANIMATION) {
                @Override
                public CssMetaData<? extends Styleable, LineAnimation> getCssMetaData() {
                    return StyleableProperties.LINE_ANIMATION;
                }

                @Override
                public Object getBean() {
                    return RXLineLabel.this;
                }

                @Override
                public String getName() {
                    return "lineAnimation";
                }
            };

    /**
     * Geometry of the line animation: a {@link LineAnimation} preset
     * constant, a parameterized instance such as
     * {@code new LineAnimSlide(LineEdges.BOTTOM, 20.0)}, or a custom
     * implementation. From CSS, {@code -rx-line-animation} selects presets by
     * keyword. A {@code null} or unknown-keyword value falls back to
     * {@link #DEFAULT_LINE_ANIMATION} at render time.
     *
     * @return the line animation property
     */
    public final ObjectProperty<LineAnimation> lineAnimationProperty() {
        return lineAnimation;
    }

    /**
     * Returns the line animation.
     *
     * @return the line animation, or {@code null}
     */
    public final LineAnimation getLineAnimation() {
        return lineAnimation.get();
    }

    /**
     * Sets the line animation.
     *
     * @param value the line animation, or {@code null} for the default
     */
    public final void setLineAnimation(LineAnimation value) {
        lineAnimation.set(value);
    }

    // ==================== Line Thickness ====================

    private final DoubleProperty lineThickness =
            new StyleableDoubleProperty(DEFAULT_LINE_THICKNESS) {
                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.LINE_THICKNESS;
                }

                @Override
                public Object getBean() {
                    return RXLineLabel.this;
                }

                @Override
                public String getName() {
                    return "lineThickness";
                }
            };

    /**
     * Thickness of the line bars in pixels. Negative or non-finite values are
     * clamped at render time; from CSS, {@code -rx-line-thickness}.
     *
     * @return the line thickness property
     */
    public final DoubleProperty lineThicknessProperty() {
        return lineThickness;
    }

    /**
     * Returns the line thickness.
     *
     * @return the line thickness in pixels
     */
    public final double getLineThickness() {
        return lineThickness.get();
    }

    /**
     * Sets the line thickness.
     *
     * @param value the line thickness in pixels
     */
    public final void setLineThickness(double value) {
        lineThickness.set(value);
    }

    // ==================== Line Gap ====================

    private final DoubleProperty lineGap =
            new StyleableDoubleProperty(DEFAULT_LINE_GAP) {
                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.LINE_GAP;
                }

                @Override
                public Object getBean() {
                    return RXLineLabel.this;
                }

                @Override
                public String getName() {
                    return "lineGap";
                }
            };

    /**
     * Gap between a resting line and the content bounds, in pixels. Negative
     * or non-finite values are clamped at render time; from CSS,
     * {@code -rx-line-gap}.
     *
     * @return the line gap property
     */
    public final DoubleProperty lineGapProperty() {
        return lineGap;
    }

    /**
     * Returns the line gap.
     *
     * @return the line gap in pixels
     */
    public final double getLineGap() {
        return lineGap.get();
    }

    /**
     * Sets the line gap.
     *
     * @param value the line gap in pixels
     */
    public final void setLineGap(double value) {
        lineGap.set(value);
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXLineLabel, LineAnimation> LINE_ANIMATION =
                new CssMetaData<>("-rx-line-animation",
                        new KeywordConverter<>(LineAnimation::valueOf), DEFAULT_LINE_ANIMATION) {
                    @Override
                    public boolean isSettable(RXLineLabel label) {
                        return !label.lineAnimation.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<LineAnimation> getStyleableProperty(RXLineLabel label) {
                        return (StyleableProperty<LineAnimation>) label.lineAnimationProperty();
                    }
                };

        private static final CssMetaData<RXLineLabel, Number> LINE_THICKNESS =
                new CssMetaData<>("-rx-line-thickness",
                        SizeConverter.getInstance(), DEFAULT_LINE_THICKNESS) {
                    @Override
                    public boolean isSettable(RXLineLabel label) {
                        return !label.lineThickness.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXLineLabel label) {
                        return (StyleableProperty<Number>) label.lineThicknessProperty();
                    }
                };

        private static final CssMetaData<RXLineLabel, Number> LINE_GAP =
                new CssMetaData<>("-rx-line-gap",
                        SizeConverter.getInstance(), DEFAULT_LINE_GAP) {
                    @Override
                    public boolean isSettable(RXLineLabel label) {
                        return !label.lineGap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXLineLabel label) {
                        return (StyleableProperty<Number>) label.lineGapProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(RXAnimatedLabel.getClassCssMetaData());
            styleables.add(LINE_ANIMATION);
            styleables.add(LINE_THICKNESS);
            styleables.add(LINE_GAP);
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
