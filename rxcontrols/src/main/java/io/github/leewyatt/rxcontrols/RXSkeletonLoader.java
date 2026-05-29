package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXSkeletonLoaderSkin;
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
import javafx.scene.paint.Paint;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Single-unit skeleton placeholder with a horizontal shimmer animation,
 * intended for "content is loading" UI. The loader draws one of three
 * geometric forms — selected via {@link #shapeProperty() shape} — and overlays
 * a translucent gradient band that scrolls left-to-right to suggest activity.
 *
 * <p>Unlike spinner-type indicators in this library, the loader is
 * <b>stretchable</b>: its skin reports {@code maxWidth} / {@code maxHeight} as
 * {@link Double#MAX_VALUE}, so combined with {@code HBox.setHgrow(...,
 * Priority.ALWAYS)} / {@code VBox.setVgrow(..., Priority.ALWAYS)} the
 * placeholder grows to mirror real content as its container resizes.
 *
 * <p>Shapes:
 * <ul>
 *   <li>{@link Shape#ROUNDED_RECT} (default) — a rectangle whose corners use
 *       {@link #cornerRadiusProperty() cornerRadius}; setting the radius to
 *       {@code 0} yields a plain rectangle</li>
 *   <li>{@link Shape#CIRCLE} — a circle inscribed in
 *       {@code min(width, height)}; intended for avatar placeholders</li>
 *   <li>{@link Shape#TEXT_LINE} — N stacked lines that simulate a paragraph;
 *       lines {@code 1 .. N-1} fill the full width, the last line is shortened
 *       by {@link #lastLineFillPercentProperty() lastLineFillPercent}; a single
 *       shimmer band sweeps across the union of all lines</li>
 * </ul>
 *
 * <p>The shimmer animation auto-pauses whenever the host window or any
 * ancestor of the loader is hidden, so off-screen placeholders do not waste
 * CPU. Setting {@link #cycleDurationProperty() cycleDuration} to
 * {@code Duration.ZERO} or any non-positive value suppresses the animation
 * entirely — the loader degrades to a static grey block.
 *
 * @see RXSkeletonPane
 */
public class RXSkeletonLoader extends Control {

    /**
     * Geometric form of a {@link RXSkeletonLoader}.
     */
    public enum Shape {
        /**
         * A rounded rectangle whose corner radius is driven by
         * {@link RXSkeletonLoader#cornerRadiusProperty() cornerRadius}.
         */
        ROUNDED_RECT,
        /**
         * A circle inscribed in {@code min(width, height)}.
         */
        CIRCLE,
        /**
         * A vertical stack of {@link RXSkeletonLoader#lineCountProperty()
         * lineCount} horizontal lines simulating a paragraph.
         */
        TEXT_LINE
    }

    private static final String DEFAULT_STYLE_CLASS = "rx-skeleton-loader";


    // ==================== Public Defaults ====================

    /**
     * Default {@link Shape}.
     */
    public static final Shape DEFAULT_SHAPE = Shape.ROUNDED_RECT;

    /**
     * Default corner radius for {@link Shape#ROUNDED_RECT}, in pixels.
     */
    public static final double DEFAULT_CORNER_RADIUS = 4.0;

    /**
     * Default cycle duration for one full shimmer sweep across the placeholder.
     */
    public static final Duration DEFAULT_CYCLE_DURATION = Duration.millis(1500.0);

    /**
     * Default ratio of the shimmer band width to the placeholder width.
     */
    public static final double DEFAULT_SHIMMER_WIDTH_RATIO = 0.35;

    /**
     * Default number of lines for {@link Shape#TEXT_LINE}.
     */
    public static final int DEFAULT_LINE_COUNT = 1;

    /**
     * Default per-line height for {@link Shape#TEXT_LINE}, in pixels.
     */
    public static final double DEFAULT_LINE_HEIGHT = 14.0;

    /**
     * Default spacing between lines for {@link Shape#TEXT_LINE}, in pixels.
     */
    public static final double DEFAULT_LINE_SPACING = 8.0;

    /**
     * Default fill percent for the last line of {@link Shape#TEXT_LINE},
     * expressed as {@code [0, 100]}. The classic value of {@code 70} mimics
     * the way real paragraphs rarely fill the last line edge-to-edge.
     */
    public static final double DEFAULT_LAST_LINE_FILL_PERCENT = 70.0;

    /**
     * Default base colour painted under the shimmer band.
     */
    public static final Paint DEFAULT_BASE_COLOR = Color.web("#e0e0e0");

    /**
     * Default shimmer band colour. Translucent white so it composites cleanly
     * over any {@link #baseColorProperty() baseColor}; on dark base colours
     * the user should override this with a brighter / more opaque value.
     */
    public static final Paint DEFAULT_SHIMMER_COLOR = Color.web("#ffffff", 0.6);

    // ==================== Constructors ====================

    /**
     * Creates a loader with the {@linkplain #DEFAULT_SHAPE default shape}.
     */
    public RXSkeletonLoader() {
        this(DEFAULT_SHAPE);
    }

    /**
     * Creates a loader with the given shape.
     *
     * @param shape the initial shape; {@code null} falls back to
     *              {@link #DEFAULT_SHAPE}
     */
    public RXSkeletonLoader(@NamedArg("variant") Shape variant) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setVariant(variant == null ? DEFAULT_SHAPE : variant);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXSkeletonLoaderSkin(this);
    }

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

    private final ObjectProperty<Shape> variant = new StyleableObjectProperty<>(DEFAULT_SHAPE) {
        private Shape lastValid = DEFAULT_SHAPE;

        @Override
        protected void invalidated() {
            Shape v = get();
            if (v == null) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new NullPointerException("variant cannot be null");
            }
            lastValid = v;
        }

        @Override
        public Object getBean() {
            return RXSkeletonLoader.this;
        }

        @Override
        public String getName() {
            return "variant";
        }

        @Override
        public CssMetaData<RXSkeletonLoader, Shape> getCssMetaData() {
            return StyleableProperties.VARIANT;
        }
    };

    /**
     * Geometric form of the placeholder. Cannot be set to {@code null} — doing
     * so throws {@link NullPointerException} and rolls back to the previous
     * value (unless the property is currently bound).
     *
     * @return the variant property
     */
    public final ObjectProperty<Shape> variantProperty() {
        return variant;
    }

    public final Shape getVariant() {
        return variant.get();
    }

    public final void setVariant(Shape value) {
        variant.set(value);
    }

    // ==================== Corner Radius ====================

    private final DoubleProperty cornerRadius = new StyleableDoubleProperty(DEFAULT_CORNER_RADIUS) {
        @Override
        public Object getBean() {
            return RXSkeletonLoader.this;
        }

        @Override
        public String getName() {
            return "cornerRadius";
        }

        @Override
        public CssMetaData<RXSkeletonLoader, Number> getCssMetaData() {
            return StyleableProperties.CORNER_RADIUS;
        }
    };

    /**
     * Corner radius applied when {@link #shapeProperty() shape} is
     * {@link Shape#ROUNDED_RECT}. Ignored for {@link Shape#CIRCLE} and
     * {@link Shape#TEXT_LINE} (lines use their own height-derived radius).
     * Negative values and {@code NaN} are treated as {@code 0} at render time.
     *
     * @return the corner-radius property
     */
    public final DoubleProperty cornerRadiusProperty() {
        return cornerRadius;
    }

    public final double getCornerRadius() {
        return cornerRadius.get();
    }

    public final void setCornerRadius(double value) {
        cornerRadius.set(value);
    }

    // ==================== Base Color ====================

    private final ObjectProperty<Paint> baseColor = new StyleableObjectProperty<>(DEFAULT_BASE_COLOR) {
        @Override
        public Object getBean() {
            return RXSkeletonLoader.this;
        }

        @Override
        public String getName() {
            return "baseColor";
        }

        @Override
        public CssMetaData<RXSkeletonLoader, Paint> getCssMetaData() {
            return StyleableProperties.BASE_COLOR;
        }
    };

    /**
     * Paint used for the base block under the shimmer band. Tolerates
     * {@code null} (skin falls back to {@link #DEFAULT_BASE_COLOR}).
     *
     * @return the base-color property
     */
    public final ObjectProperty<Paint> baseColorProperty() {
        return baseColor;
    }

    public final Paint getBaseColor() {
        return baseColor.get();
    }

    public final void setBaseColor(Paint value) {
        baseColor.set(value);
    }

    // ==================== Shimmer Color ====================

    private final ObjectProperty<Paint> shimmerColor = new StyleableObjectProperty<>(DEFAULT_SHIMMER_COLOR) {
        @Override
        public Object getBean() {
            return RXSkeletonLoader.this;
        }

        @Override
        public String getName() {
            return "shimmerColor";
        }

        @Override
        public CssMetaData<RXSkeletonLoader, Paint> getCssMetaData() {
            return StyleableProperties.SHIMMER_COLOR;
        }
    };

    /**
     * Centre stop of the shimmer band's gradient. Should be translucent so
     * the band composites over the base. Tolerates {@code null} (skin falls
     * back to {@link #DEFAULT_SHIMMER_COLOR}).
     *
     * @return the shimmer-color property
     */
    public final ObjectProperty<Paint> shimmerColorProperty() {
        return shimmerColor;
    }

    public final Paint getShimmerColor() {
        return shimmerColor.get();
    }

    public final void setShimmerColor(Paint value) {
        shimmerColor.set(value);
    }

    // ==================== Cycle Duration ====================

    private final ObjectProperty<Duration> cycleDuration =
            new StyleableObjectProperty<>(DEFAULT_CYCLE_DURATION) {
                @Override
                public Object getBean() {
                    return RXSkeletonLoader.this;
                }

                @Override
                public String getName() {
                    return "cycleDuration";
                }

                @Override
                public CssMetaData<RXSkeletonLoader, Duration> getCssMetaData() {
                    return StyleableProperties.CYCLE_DURATION;
                }
            };

    /**
     * Duration of one full left-to-right shimmer sweep. A value of {@code null}
     * or any {@code Duration} less than or equal to {@link Duration#ZERO}
     * suppresses the animation — the placeholder stays a static grey block.
     *
     * @return the cycle-duration property
     */
    public final ObjectProperty<Duration> cycleDurationProperty() {
        return cycleDuration;
    }

    public final Duration getCycleDuration() {
        return cycleDuration.get();
    }

    public final void setCycleDuration(Duration value) {
        cycleDuration.set(value);
    }

    // ==================== Shimmer Width Ratio ====================

    private final DoubleProperty shimmerWidthRatio =
            new StyleableDoubleProperty(DEFAULT_SHIMMER_WIDTH_RATIO) {
                @Override
                public Object getBean() {
                    return RXSkeletonLoader.this;
                }

                @Override
                public String getName() {
                    return "shimmerWidthRatio";
                }

                @Override
                public CssMetaData<RXSkeletonLoader, Number> getCssMetaData() {
                    return StyleableProperties.SHIMMER_WIDTH_RATIO;
                }
            };

    /**
     * Width of the shimmer band, expressed as a fraction of the placeholder
     * width. Values outside {@code [0, 1]} are clamped at render time.
     *
     * @return the shimmer-width-ratio property
     */
    public final DoubleProperty shimmerWidthRatioProperty() {
        return shimmerWidthRatio;
    }

    public final double getShimmerWidthRatio() {
        return shimmerWidthRatio.get();
    }

    public final void setShimmerWidthRatio(double value) {
        shimmerWidthRatio.set(value);
    }

    // ==================== Line Count (TEXT_LINE only) ====================

    private final IntegerProperty lineCount = new StyleableIntegerProperty(DEFAULT_LINE_COUNT) {
        @Override
        public Object getBean() {
            return RXSkeletonLoader.this;
        }

        @Override
        public String getName() {
            return "lineCount";
        }

        @Override
        public CssMetaData<RXSkeletonLoader, Number> getCssMetaData() {
            return StyleableProperties.LINE_COUNT;
        }
    };

    /**
     * Number of stacked lines drawn for {@link Shape#TEXT_LINE}. Ignored for
     * the other shapes. Values less than {@code 1} are treated as {@code 1}.
     *
     * @return the line-count property
     */
    public final IntegerProperty lineCountProperty() {
        return lineCount;
    }

    public final int getLineCount() {
        return lineCount.get();
    }

    public final void setLineCount(int value) {
        lineCount.set(value);
    }

    // ==================== Line Height (TEXT_LINE only) ====================

    private final DoubleProperty lineHeight = new StyleableDoubleProperty(DEFAULT_LINE_HEIGHT) {
        @Override
        public Object getBean() {
            return RXSkeletonLoader.this;
        }

        @Override
        public String getName() {
            return "lineHeight";
        }

        @Override
        public CssMetaData<RXSkeletonLoader, Number> getCssMetaData() {
            return StyleableProperties.LINE_HEIGHT;
        }
    };

    /**
     * Per-line height for {@link Shape#TEXT_LINE}, in pixels. Ignored for the
     * other shapes. Negative values and {@code NaN} are treated as {@code 0}
     * at render time.
     *
     * @return the line-height property
     */
    public final DoubleProperty lineHeightProperty() {
        return lineHeight;
    }

    public final double getLineHeight() {
        return lineHeight.get();
    }

    public final void setLineHeight(double value) {
        lineHeight.set(value);
    }

    // ==================== Line Spacing (TEXT_LINE only) ====================

    private final DoubleProperty lineSpacing = new StyleableDoubleProperty(DEFAULT_LINE_SPACING) {
        @Override
        public Object getBean() {
            return RXSkeletonLoader.this;
        }

        @Override
        public String getName() {
            return "lineSpacing";
        }

        @Override
        public CssMetaData<RXSkeletonLoader, Number> getCssMetaData() {
            return StyleableProperties.LINE_SPACING;
        }
    };

    /**
     * Vertical gap between adjacent lines for {@link Shape#TEXT_LINE}, in
     * pixels. Ignored for the other shapes. Negative values and {@code NaN}
     * are treated as {@code 0} at render time.
     *
     * @return the line-spacing property
     */
    public final DoubleProperty lineSpacingProperty() {
        return lineSpacing;
    }

    public final double getLineSpacing() {
        return lineSpacing.get();
    }

    public final void setLineSpacing(double value) {
        lineSpacing.set(value);
    }

    // ==================== Last Line Fill Percent (TEXT_LINE only) ====================

    private final DoubleProperty lastLineFillPercent =
            new StyleableDoubleProperty(DEFAULT_LAST_LINE_FILL_PERCENT) {
                @Override
                public Object getBean() {
                    return RXSkeletonLoader.this;
                }

                @Override
                public String getName() {
                    return "lastLineFillPercent";
                }

                @Override
                public CssMetaData<RXSkeletonLoader, Number> getCssMetaData() {
                    return StyleableProperties.LAST_LINE_FILL_PERCENT;
                }
            };

    /**
     * Width of the last line for {@link Shape#TEXT_LINE}, expressed as a
     * percent of the placeholder width. Mimics real paragraphs whose last
     * line rarely fills the entire row. Ignored for the other shapes. Values
     * are clamped to {@code [0, 100]} at render time.
     *
     * @return the last-line-fill-percent property
     */
    public final DoubleProperty lastLineFillPercentProperty() {
        return lastLineFillPercent;
    }

    public final double getLastLineFillPercent() {
        return lastLineFillPercent.get();
    }

    public final void setLastLineFillPercent(double value) {
        lastLineFillPercent.set(value);
    }

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXSkeletonLoader, Shape> VARIANT =
                new CssMetaData<>("-rx-variant",
                        new EnumConverter<>(Shape.class),
                        DEFAULT_SHAPE) {
                    @Override
                    public boolean isSettable(RXSkeletonLoader n) {
                        return !n.variant.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Shape> getStyleableProperty(RXSkeletonLoader n) {
                        return (StyleableProperty<Shape>) n.variantProperty();
                    }
                };

        private static final CssMetaData<RXSkeletonLoader, Number> CORNER_RADIUS =
                new CssMetaData<>("-rx-corner-radius",
                        SizeConverter.getInstance(),
                        DEFAULT_CORNER_RADIUS) {
                    @Override
                    public boolean isSettable(RXSkeletonLoader n) {
                        return !n.cornerRadius.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSkeletonLoader n) {
                        return (StyleableProperty<Number>) n.cornerRadiusProperty();
                    }
                };

        private static final CssMetaData<RXSkeletonLoader, Paint> BASE_COLOR =
                new CssMetaData<>("-rx-base-color",
                        PaintConverter.getInstance(),
                        DEFAULT_BASE_COLOR) {
                    @Override
                    public boolean isSettable(RXSkeletonLoader n) {
                        return !n.baseColor.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXSkeletonLoader n) {
                        return (StyleableProperty<Paint>) n.baseColorProperty();
                    }
                };

        private static final CssMetaData<RXSkeletonLoader, Paint> SHIMMER_COLOR =
                new CssMetaData<>("-rx-shimmer-color",
                        PaintConverter.getInstance(),
                        DEFAULT_SHIMMER_COLOR) {
                    @Override
                    public boolean isSettable(RXSkeletonLoader n) {
                        return !n.shimmerColor.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXSkeletonLoader n) {
                        return (StyleableProperty<Paint>) n.shimmerColorProperty();
                    }
                };

        private static final CssMetaData<RXSkeletonLoader, Duration> CYCLE_DURATION =
                new CssMetaData<>("-rx-cycle-duration",
                        DurationConverter.getInstance(),
                        DEFAULT_CYCLE_DURATION) {
                    @Override
                    public boolean isSettable(RXSkeletonLoader n) {
                        return !n.cycleDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXSkeletonLoader n) {
                        return (StyleableProperty<Duration>) n.cycleDurationProperty();
                    }
                };

        private static final CssMetaData<RXSkeletonLoader, Number> SHIMMER_WIDTH_RATIO =
                new CssMetaData<>("-rx-shimmer-width-ratio",
                        SizeConverter.getInstance(),
                        DEFAULT_SHIMMER_WIDTH_RATIO) {
                    @Override
                    public boolean isSettable(RXSkeletonLoader n) {
                        return !n.shimmerWidthRatio.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSkeletonLoader n) {
                        return (StyleableProperty<Number>) n.shimmerWidthRatioProperty();
                    }
                };

        private static final CssMetaData<RXSkeletonLoader, Number> LINE_COUNT =
                new CssMetaData<>("-rx-line-count",
                        SizeConverter.getInstance(),
                        DEFAULT_LINE_COUNT) {
                    @Override
                    public boolean isSettable(RXSkeletonLoader n) {
                        return !n.lineCount.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSkeletonLoader n) {
                        return (StyleableProperty<Number>) n.lineCountProperty();
                    }
                };

        private static final CssMetaData<RXSkeletonLoader, Number> LINE_HEIGHT =
                new CssMetaData<>("-rx-line-height",
                        SizeConverter.getInstance(),
                        DEFAULT_LINE_HEIGHT) {
                    @Override
                    public boolean isSettable(RXSkeletonLoader n) {
                        return !n.lineHeight.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSkeletonLoader n) {
                        return (StyleableProperty<Number>) n.lineHeightProperty();
                    }
                };

        private static final CssMetaData<RXSkeletonLoader, Number> LINE_SPACING =
                new CssMetaData<>("-rx-line-spacing",
                        SizeConverter.getInstance(),
                        DEFAULT_LINE_SPACING) {
                    @Override
                    public boolean isSettable(RXSkeletonLoader n) {
                        return !n.lineSpacing.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSkeletonLoader n) {
                        return (StyleableProperty<Number>) n.lineSpacingProperty();
                    }
                };

        private static final CssMetaData<RXSkeletonLoader, Number> LAST_LINE_FILL_PERCENT =
                new CssMetaData<>("-rx-last-line-fill-percent",
                        SizeConverter.getInstance(),
                        DEFAULT_LAST_LINE_FILL_PERCENT) {
                    @Override
                    public boolean isSettable(RXSkeletonLoader n) {
                        return !n.lastLineFillPercent.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSkeletonLoader n) {
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
                    SHIMMER_COLOR,
                    CYCLE_DURATION,
                    SHIMMER_WIDTH_RATIO,
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

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
