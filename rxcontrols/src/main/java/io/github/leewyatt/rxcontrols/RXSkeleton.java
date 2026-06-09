package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXSkeletonSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableIntegerProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.PaintConverter;
import javafx.css.converter.SizeConverter;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Single-unit skeleton placeholder with a horizontal shimmer animation,
 * intended for "content is loading" UI. The skeleton draws one of three
 * variants selected via {@link #variantProperty()} and overlays a shimmer band
 * that scrolls left-to-right to suggest activity.
 *
 * <p>Unlike spinner-type indicators in this library, the skeleton is
 * <b>stretchable</b>: its skin reports {@code maxWidth} / {@code maxHeight} as
 * {@link Double#MAX_VALUE}, so combined with {@code HBox.setHgrow(...,
 * Priority.ALWAYS)} / {@code VBox.setVgrow(..., Priority.ALWAYS)} the
 * placeholder grows to mirror real content as its container resizes.
 *
 * <p>Variants:
 * <ul>
 *   <li>{@link Variant#ROUNDED_RECTANGLE} (default) — a rectangle whose corners use
 *       {@link #cornerRadiusProperty() cornerRadius}; setting the radius to
 *       {@code 0} yields a plain rectangle</li>
 *   <li>{@link Variant#CIRCULAR} — a circle inscribed in
 *       {@code min(width, height)}; intended for avatar placeholders</li>
 *   <li>{@link Variant#TEXT} — N stacked lines that simulate a paragraph;
 *       lines {@code 1 .. N-1} fill the full width, the last line is shortened
 *       by {@link #lastLineFillPercentProperty() lastLineFillPercent}; a single
 *       shimmer band sweeps across the union of all lines</li>
 * </ul>
 *
 * <p>The shimmer animation auto-pauses whenever the host window or any
 * ancestor of the skeleton is hidden, so off-screen placeholders do not waste
 * CPU. Setting {@link #cycleDurationProperty() cycleDuration} to
 * {@code Duration.ZERO} or any non-positive value suppresses the animation
 * entirely — the skeleton degrades to a static gray block.
 *
 * @see RXSkeletonPane
 */
public class RXSkeleton extends Control {

    /**
     * Geometric form of a {@link RXSkeleton}.
     */
    public enum Variant {
        /**
         * A rounded rectangle whose corner radius is driven by
         * {@link RXSkeleton#cornerRadiusProperty() cornerRadius}.
         */
        ROUNDED_RECTANGLE,
        /**
         * A circle inscribed in {@code min(width, height)}.
         */
        CIRCULAR,
        /**
         * A vertical stack of {@link RXSkeleton#lineCountProperty()
         * lineCount} horizontal lines simulating a paragraph.
         */
        TEXT
    }

    private static final String DEFAULT_STYLE_CLASS = "rx-skeleton";

    // ==================== Public Defaults ====================

    /**
     * Default {@link Variant}.
     */
    public static final Variant DEFAULT_VARIANT = Variant.ROUNDED_RECTANGLE;

    /**
     * Default corner radius for {@link Variant#ROUNDED_RECTANGLE}, in pixels.
     */
    public static final double DEFAULT_CORNER_RADIUS = 4.0;

    /**
     * Default cycle duration for one full shimmer sweep across the placeholder.
     */
    public static final Duration DEFAULT_CYCLE_DURATION = Duration.millis(1500.0);

    /**
     * Default shimmer band width, in pixels.
     */
    public static final double DEFAULT_SHIMMER_WIDTH = 56.0;

    /**
     * Default number of lines for {@link Variant#TEXT}.
     */
    public static final int DEFAULT_LINE_COUNT = 1;

    /**
     * Default per-line height for {@link Variant#TEXT}, in pixels.
     */
    public static final double DEFAULT_LINE_HEIGHT = 14.0;

    /**
     * Default spacing between lines for {@link Variant#TEXT}, in pixels.
     */
    public static final double DEFAULT_LINE_SPACING = 8.0;

    /**
     * Default fill percent for the last line of {@link Variant#TEXT},
     * expressed as {@code [0, 100]}. The classic value of {@code 70} mimics
     * the way real paragraphs rarely fill the last line edge-to-edge.
     */
    public static final double DEFAULT_LAST_LINE_FILL_PERCENT = 70.0;

    /**
     * Default base color painted under the shimmer band.
     */
    public static final Paint DEFAULT_BASE_COLOR = Color.web("#e0e0e0");

    /**
     * Default shimmer band fill. It is a standard left-to-right shimmer
     * gradient with transparent edges and a translucent white highlight center.
     */
    public static final Paint DEFAULT_SHIMMER_FILL =
            createShimmerGradient(Color.web("#ffffff", 0.6));

    /**
     * Creates a standard shimmer gradient from a single highlight color.
     *
     * <p>The resulting gradient is transparent at both edges and uses the
     * supplied color as the center stop.
     *
     * @param highlightColor the center highlight color
     * @return a linear gradient suitable for {@link #shimmerFillProperty()}
     * @throws NullPointerException if {@code highlightColor} is {@code null}
     */
    public static LinearGradient createShimmerGradient(Color highlightColor) {
        if (highlightColor == null) {
            throw new NullPointerException("highlightColor cannot be null");
        }
        Color edge = new Color(highlightColor.getRed(), highlightColor.getGreen(),
                highlightColor.getBlue(), 0.0);
        return new LinearGradient(0.0, 0.0, 1.0, 0.0, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, edge),
                new Stop(0.5, highlightColor),
                new Stop(1.0, edge));
    }

    // ==================== Constructors ====================

    /**
     * Creates a skeleton with the {@linkplain #DEFAULT_VARIANT default variant}.
     */
    public RXSkeleton() {
        this(DEFAULT_VARIANT);
    }

    /**
     * Creates a skeleton with the given variant.
     *
     * @param variant the initial variant; {@code null} falls back to
     *                {@link #DEFAULT_VARIANT}
     */
    public RXSkeleton(@NamedArg("variant") Variant variant) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setVariant(variant == null ? DEFAULT_VARIANT : variant);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXSkeletonSkin(this);
    }

    /**
     * Returns the user-agent stylesheet for RXControls.
     *
     * @return the user-agent stylesheet URL
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Variant ====================
    //
    // Named "variant" rather than "shape" because Region already defines a
    // final shapeProperty() (the -fx-shape SVG path used to clip a region
    // into an arbitrary outline). The "variant" name aligns with Material UI
    // / Ant Design conventions for the same concept.

    private final ObjectProperty<Variant> variant = new StyleableObjectProperty<>(DEFAULT_VARIANT) {
        @Override
        public Object getBean() {
            return RXSkeleton.this;
        }

        @Override
        public String getName() {
            return "variant";
        }

        @Override
        public CssMetaData<RXSkeleton, Variant> getCssMetaData() {
            return StyleableProperties.VARIANT;
        }
    };

    /**
     * Geometric form of the placeholder. A {@code null} value is not rejected;
     * it resolves to the default {@link #DEFAULT_VARIANT} at the use site.
     *
     * @return the variant property
     */
    public final ObjectProperty<Variant> variantProperty() {
        return variant;
    }

    /**
     * Gets the geometric variant.
     *
     * @return the current variant
     */
    public final Variant getVariant() {
        return variant.get();
    }

    /**
     * Sets the geometric variant.
     *
     * @param value the variant, or {@code null} to fall back to the default
     */
    public final void setVariant(Variant value) {
        variant.set(value);
    }

    // ==================== Corner Radius ====================

    private final DoubleProperty cornerRadius = new StyleableDoubleProperty(DEFAULT_CORNER_RADIUS) {
        @Override
        public Object getBean() {
            return RXSkeleton.this;
        }

        @Override
        public String getName() {
            return "cornerRadius";
        }

        @Override
        public CssMetaData<RXSkeleton, Number> getCssMetaData() {
            return StyleableProperties.CORNER_RADIUS;
        }
    };

    /**
     * Corner radius applied when {@link #variantProperty()} is
     * {@link Variant#ROUNDED_RECTANGLE}. Ignored for {@link Variant#CIRCULAR} and
     * {@link Variant#TEXT} (lines use their own height-derived radius).
     * Negative values and {@code NaN} are treated as {@code 0} at render time.
     *
     * @return the corner-radius property
     */
    public final DoubleProperty cornerRadiusProperty() {
        return cornerRadius;
    }

    /**
     * Gets the corner radius.
     *
     * @return the corner radius in pixels
     */
    public final double getCornerRadius() {
        return cornerRadius.get();
    }

    /**
     * Sets the corner radius.
     *
     * @param value the corner radius in pixels
     */
    public final void setCornerRadius(double value) {
        cornerRadius.set(value);
    }

    // ==================== Base Color ====================

    private final ObjectProperty<Paint> baseColor = new StyleableObjectProperty<>(DEFAULT_BASE_COLOR) {
        @Override
        public Object getBean() {
            return RXSkeleton.this;
        }

        @Override
        public String getName() {
            return "baseColor";
        }

        @Override
        public CssMetaData<RXSkeleton, Paint> getCssMetaData() {
            return StyleableProperties.BASE_COLOR;
        }
    };

    /**
     * Paint used for the base block under the shimmer band. Initial value is
     * {@link #DEFAULT_BASE_COLOR}; setting {@code null} renders no base fill
     * per the JavaFX {@code Shape.setFill} convention.
     *
     * @return the base-color property
     */
    public final ObjectProperty<Paint> baseColorProperty() {
        return baseColor;
    }

    /**
     * Gets the base fill paint.
     *
     * @return the base fill paint, or {@code null} for no base fill
     */
    public final Paint getBaseColor() {
        return baseColor.get();
    }

    /**
     * Sets the base fill paint.
     *
     * @param value the base fill paint, or {@code null} for no base fill
     */
    public final void setBaseColor(Paint value) {
        baseColor.set(value);
    }

    // ==================== Shimmer Fill ====================

    private final ObjectProperty<Paint> shimmerFill = new StyleableObjectProperty<>(DEFAULT_SHIMMER_FILL) {
        @Override
        public Object getBean() {
            return RXSkeleton.this;
        }

        @Override
        public String getName() {
            return "shimmerFill";
        }

        @Override
        public CssMetaData<RXSkeleton, Paint> getCssMetaData() {
            return StyleableProperties.SHIMMER_FILL;
        }
    };

    /**
     * Fill paint used for the moving shimmer band. Initial value is
     * {@link #DEFAULT_SHIMMER_FILL}; setting {@code null} renders no shimmer
     * fill per the JavaFX {@code Shape.setFill} convention. A solid paint
     * renders a solid, hard-edged moving band; use
     * {@link #createShimmerGradient(Color)} for the standard soft shimmer.
     *
     * @return the shimmer-fill property
     */
    public final ObjectProperty<Paint> shimmerFillProperty() {
        return shimmerFill;
    }

    /**
     * Gets the shimmer band fill paint.
     *
     * @return the shimmer band fill paint, or {@code null} for no shimmer fill
     */
    public final Paint getShimmerFill() {
        return shimmerFill.get();
    }

    /**
     * Sets the shimmer band fill paint.
     *
     * @param value the shimmer band fill paint, or {@code null} for no shimmer
     *              fill
     */
    public final void setShimmerFill(Paint value) {
        shimmerFill.set(value);
    }

    // ==================== Cycle Duration ====================

    private final ObjectProperty<Duration> cycleDuration =
            new StyleableObjectProperty<>(DEFAULT_CYCLE_DURATION) {
                @Override
                public Object getBean() {
                    return RXSkeleton.this;
                }

                @Override
                public String getName() {
                    return "cycleDuration";
                }

                @Override
                public CssMetaData<RXSkeleton, Duration> getCssMetaData() {
                    return StyleableProperties.CYCLE_DURATION;
                }
            };

    /**
     * Duration of one full left-to-right shimmer sweep. A value of {@code null}
     * or any {@code Duration} less than or equal to {@link Duration#ZERO}
     * suppresses the animation — the placeholder stays a static gray block.
     *
     * @return the cycle-duration property
     */
    public final ObjectProperty<Duration> cycleDurationProperty() {
        return cycleDuration;
    }

    /**
     * Gets the shimmer cycle duration.
     *
     * @return the cycle duration, or {@code null} to disable animation
     */
    public final Duration getCycleDuration() {
        return cycleDuration.get();
    }

    /**
     * Sets the shimmer cycle duration.
     *
     * @param value the cycle duration, or {@code null} to disable animation
     */
    public final void setCycleDuration(Duration value) {
        cycleDuration.set(value);
    }

    // ==================== Shimmer Width ====================

    private final DoubleProperty shimmerWidth =
            new StyleableDoubleProperty(DEFAULT_SHIMMER_WIDTH) {
                @Override
                public Object getBean() {
                    return RXSkeleton.this;
                }

                @Override
                public String getName() {
                    return "shimmerWidth";
                }

                @Override
                public CssMetaData<RXSkeleton, Number> getCssMetaData() {
                    return StyleableProperties.SHIMMER_WIDTH;
                }
            };

    /**
     * Width of the shimmer band in pixels. Values that are negative,
     * {@code NaN}, or infinite are treated as {@code 0} at render time. The
     * width is not clamped to the placeholder width.
     *
     * @return the shimmer-width property
     */
    public final DoubleProperty shimmerWidthProperty() {
        return shimmerWidth;
    }

    /**
     * Gets the shimmer band width.
     *
     * @return the shimmer band width in pixels
     */
    public final double getShimmerWidth() {
        return shimmerWidth.get();
    }

    /**
     * Sets the shimmer band width.
     *
     * @param value the shimmer band width in pixels
     */
    public final void setShimmerWidth(double value) {
        shimmerWidth.set(value);
    }

    // ==================== Line Count (TEXT only) ====================

    private final IntegerProperty lineCount = new StyleableIntegerProperty(DEFAULT_LINE_COUNT) {
        @Override
        public Object getBean() {
            return RXSkeleton.this;
        }

        @Override
        public String getName() {
            return "lineCount";
        }

        @Override
        public CssMetaData<RXSkeleton, Number> getCssMetaData() {
            return StyleableProperties.LINE_COUNT;
        }
    };

    /**
     * Number of stacked lines drawn for {@link Variant#TEXT}. Ignored for
     * the other shapes. Values less than {@code 1} are treated as {@code 1}.
     *
     * @return the line-count property
     */
    public final IntegerProperty lineCountProperty() {
        return lineCount;
    }

    /**
     * Gets the number of text lines.
     *
     * @return the configured line count
     */
    public final int getLineCount() {
        return lineCount.get();
    }

    /**
     * Sets the number of text lines.
     *
     * @param value the configured line count
     */
    public final void setLineCount(int value) {
        lineCount.set(value);
    }

    // ==================== Line Height (TEXT only) ====================

    private final DoubleProperty lineHeight = new StyleableDoubleProperty(DEFAULT_LINE_HEIGHT) {
        @Override
        public Object getBean() {
            return RXSkeleton.this;
        }

        @Override
        public String getName() {
            return "lineHeight";
        }

        @Override
        public CssMetaData<RXSkeleton, Number> getCssMetaData() {
            return StyleableProperties.LINE_HEIGHT;
        }
    };

    /**
     * Per-line height for {@link Variant#TEXT}, in pixels. Ignored for the
     * other shapes. Negative values and {@code NaN} are treated as {@code 0}
     * at render time.
     *
     * @return the line-height property
     */
    public final DoubleProperty lineHeightProperty() {
        return lineHeight;
    }

    /**
     * Gets the text line height.
     *
     * @return the line height in pixels
     */
    public final double getLineHeight() {
        return lineHeight.get();
    }

    /**
     * Sets the text line height.
     *
     * @param value the line height in pixels
     */
    public final void setLineHeight(double value) {
        lineHeight.set(value);
    }

    // ==================== Line Spacing (TEXT only) ====================

    private final DoubleProperty lineSpacing = new StyleableDoubleProperty(DEFAULT_LINE_SPACING) {
        @Override
        public Object getBean() {
            return RXSkeleton.this;
        }

        @Override
        public String getName() {
            return "lineSpacing";
        }

        @Override
        public CssMetaData<RXSkeleton, Number> getCssMetaData() {
            return StyleableProperties.LINE_SPACING;
        }
    };

    /**
     * Vertical gap between adjacent lines for {@link Variant#TEXT}, in
     * pixels. Ignored for the other shapes. Negative values and {@code NaN}
     * are treated as {@code 0} at render time.
     *
     * @return the line-spacing property
     */
    public final DoubleProperty lineSpacingProperty() {
        return lineSpacing;
    }

    /**
     * Gets the spacing between text lines.
     *
     * @return the line spacing in pixels
     */
    public final double getLineSpacing() {
        return lineSpacing.get();
    }

    /**
     * Sets the spacing between text lines.
     *
     * @param value the line spacing in pixels
     */
    public final void setLineSpacing(double value) {
        lineSpacing.set(value);
    }

    // ==================== Last Line Fill Percent (TEXT only) ====================

    private final DoubleProperty lastLineFillPercent =
            new StyleableDoubleProperty(DEFAULT_LAST_LINE_FILL_PERCENT) {
                @Override
                public Object getBean() {
                    return RXSkeleton.this;
                }

                @Override
                public String getName() {
                    return "lastLineFillPercent";
                }

                @Override
                public CssMetaData<RXSkeleton, Number> getCssMetaData() {
                    return StyleableProperties.LAST_LINE_FILL_PERCENT;
                }
            };

    /**
     * Width of the last line for {@link Variant#TEXT}, expressed as a
     * percent of the placeholder width. Mimics real paragraphs whose last
     * line rarely fills the entire row. Ignored for the other shapes. Values
     * are clamped to {@code [0, 100]} at render time.
     *
     * @return the last-line-fill-percent property
     */
    public final DoubleProperty lastLineFillPercentProperty() {
        return lastLineFillPercent;
    }

    /**
     * Gets the fill percent used for the final text line.
     *
     * @return the final line fill percent
     */
    public final double getLastLineFillPercent() {
        return lastLineFillPercent.get();
    }

    /**
     * Sets the fill percent used for the final text line.
     *
     * @param value the final line fill percent
     */
    public final void setLastLineFillPercent(double value) {
        lastLineFillPercent.set(value);
    }

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXSkeleton, Variant> VARIANT =
                new CssMetaData<>("-rx-variant",
                        new EnumConverter<>(Variant.class),
                        DEFAULT_VARIANT) {
                    @Override
                    public boolean isSettable(RXSkeleton n) {
                        return !n.variant.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Variant> getStyleableProperty(RXSkeleton n) {
                        return (StyleableProperty<Variant>) n.variantProperty();
                    }
                };

        private static final CssMetaData<RXSkeleton, Number> CORNER_RADIUS =
                new CssMetaData<>("-rx-corner-radius",
                        SizeConverter.getInstance(),
                        DEFAULT_CORNER_RADIUS) {
                    @Override
                    public boolean isSettable(RXSkeleton n) {
                        return !n.cornerRadius.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSkeleton n) {
                        return (StyleableProperty<Number>) n.cornerRadiusProperty();
                    }
                };

        private static final CssMetaData<RXSkeleton, Paint> BASE_COLOR =
                new CssMetaData<>("-rx-base-color",
                        PaintConverter.getInstance(),
                        DEFAULT_BASE_COLOR) {
                    @Override
                    public boolean isSettable(RXSkeleton n) {
                        return !n.baseColor.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXSkeleton n) {
                        return (StyleableProperty<Paint>) n.baseColorProperty();
                    }
                };

        private static final CssMetaData<RXSkeleton, Paint> SHIMMER_FILL =
                new CssMetaData<>("-rx-shimmer-fill",
                        PaintConverter.getInstance(),
                        DEFAULT_SHIMMER_FILL) {
                    @Override
                    public boolean isSettable(RXSkeleton n) {
                        return !n.shimmerFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXSkeleton n) {
                        return (StyleableProperty<Paint>) n.shimmerFillProperty();
                    }
                };

        private static final CssMetaData<RXSkeleton, Duration> CYCLE_DURATION =
                new CssMetaData<>("-rx-cycle-duration",
                        DurationConverter.getInstance(),
                        DEFAULT_CYCLE_DURATION) {
                    @Override
                    public boolean isSettable(RXSkeleton n) {
                        return !n.cycleDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXSkeleton n) {
                        return (StyleableProperty<Duration>) n.cycleDurationProperty();
                    }
                };

        private static final CssMetaData<RXSkeleton, Number> SHIMMER_WIDTH =
                new CssMetaData<>("-rx-shimmer-width",
                        SizeConverter.getInstance(),
                        DEFAULT_SHIMMER_WIDTH) {
                    @Override
                    public boolean isSettable(RXSkeleton n) {
                        return !n.shimmerWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSkeleton n) {
                        return (StyleableProperty<Number>) n.shimmerWidthProperty();
                    }
                };

        private static final CssMetaData<RXSkeleton, Number> LINE_COUNT =
                new CssMetaData<>("-rx-line-count",
                        SizeConverter.getInstance(),
                        DEFAULT_LINE_COUNT) {
                    @Override
                    public boolean isSettable(RXSkeleton n) {
                        return !n.lineCount.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSkeleton n) {
                        return (StyleableProperty<Number>) n.lineCountProperty();
                    }
                };

        private static final CssMetaData<RXSkeleton, Number> LINE_HEIGHT =
                new CssMetaData<>("-rx-line-height",
                        SizeConverter.getInstance(),
                        DEFAULT_LINE_HEIGHT) {
                    @Override
                    public boolean isSettable(RXSkeleton n) {
                        return !n.lineHeight.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSkeleton n) {
                        return (StyleableProperty<Number>) n.lineHeightProperty();
                    }
                };

        private static final CssMetaData<RXSkeleton, Number> LINE_SPACING =
                new CssMetaData<>("-rx-line-spacing",
                        SizeConverter.getInstance(),
                        DEFAULT_LINE_SPACING) {
                    @Override
                    public boolean isSettable(RXSkeleton n) {
                        return !n.lineSpacing.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSkeleton n) {
                        return (StyleableProperty<Number>) n.lineSpacingProperty();
                    }
                };

        private static final CssMetaData<RXSkeleton, Number> LAST_LINE_FILL_PERCENT =
                new CssMetaData<>("-rx-last-line-fill-percent",
                        SizeConverter.getInstance(),
                        DEFAULT_LAST_LINE_FILL_PERCENT) {
                    @Override
                    public boolean isSettable(RXSkeleton n) {
                        return !n.lastLineFillPercent.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSkeleton n) {
                        return (StyleableProperty<Number>) n.lastLineFillPercentProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables,
                    VARIANT,
                    CORNER_RADIUS,
                    BASE_COLOR,
                    SHIMMER_FILL,
                    CYCLE_DURATION,
                    SHIMMER_WIDTH,
                    LINE_COUNT,
                    LINE_HEIGHT,
                    LINE_SPACING,
                    LAST_LINE_FILL_PERCENT);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * Returns the CSS metadata associated with this control instance.
     *
     * @return the CSS metadata
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
