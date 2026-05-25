package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXDotPulseSkin;
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
import javafx.css.converter.SizeConverter;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Indeterminate loading indicator that animates a horizontal row of dots in a
 * staggered phase. Common use cases include "typing…" indicators in chat UIs,
 * inline placeholders next to text, and small loading badges inside buttons.
 *
 * <p>Three animation variants are exposed via {@link #pulseStyleProperty() pulseStyle}:
 * <ul>
 *   <li>{@link PulseStyle#BOUNCE} (default) — each dot translates upward and
 *       returns, mimicking a wave of bouncing dots</li>
 *   <li>{@link PulseStyle#PULSE} — each dot scales up briefly and contracts</li>
 *   <li>{@link PulseStyle#FADE} — each dot lifts its opacity from a resting
 *       value to fully opaque and back</li>
 * </ul>
 *
 * <p>Phase offsets are computed automatically as {@code i * cycle / dotCount},
 * so adding or removing dots redistributes the phases evenly without exposing
 * per-dot timing knobs.
 *
 * <p>Setting {@link #cycleDurationProperty() cycleDuration} to {@code null} or
 * any value {@code <= 0} stops the animation — the dots collapse to their
 * resting pose (centered row, no offset/scale/fade) so the static frame stays
 * predictable. The animation also auto-pauses whenever the host window or any
 * ancestor is hidden, so an off-screen indicator does not burn CPU.
 *
 * <p>This control is purely decorative — it does not extend
 * {@link javafx.scene.control.ProgressIndicator}, so it has no determinate
 * mode and is not bindable to {@code Task#progressProperty()}.
 */
public class RXDotPulse extends Control {

    /**
     * Animation variant for {@link RXDotPulse}.
     */
    public enum PulseStyle {
        /**
         * Each dot translates upward by an amount proportional to
         * {@link RXDotPulse#amplitudeProperty() amplitude} and {@link
         * RXDotPulse#dotSizeProperty() dotSize}, then returns to baseline.
         */
        BOUNCE,
        /**
         * Each dot scales up uniformly by an amount proportional to
         * {@link RXDotPulse#amplitudeProperty() amplitude}, then contracts.
         */
        PULSE,
        /**
         * Each dot's opacity rises from a resting value (held when at rest)
         * to fully opaque at the peak of its phase, then returns.
         */
        FADE
    }

    private static final String DEFAULT_STYLE_CLASS = "rx-dot-pulse";


    // ==================== Public Defaults ====================

    /**
     * Default {@link PulseStyle}.
     */
    public static final PulseStyle DEFAULT_PULSE_STYLE = PulseStyle.BOUNCE;

    /**
     * Default number of dots.
     */
    public static final int DEFAULT_DOT_COUNT = 3;

    /**
     * Minimum permitted {@link #dotCountProperty() dotCount} at render time.
     */
    public static final int MIN_DOT_COUNT = 2;

    /**
     * Maximum permitted {@link #dotCountProperty() dotCount} at render time.
     * Beyond this the indicator stops reading as a "few-dot" indicator and
     * starts to look like a progress bar.
     */
    public static final int MAX_DOT_COUNT = 8;

    /**
     * Default diameter of each dot, in pixels.
     */
    public static final double DEFAULT_DOT_SIZE = 8.0;

    /**
     * Default horizontal gap between adjacent dots, in pixels.
     */
    public static final double DEFAULT_DOT_GAP = 6.0;

    /**
     * Default cycle duration for one full staggered pass over all dots.
     */
    public static final Duration DEFAULT_CYCLE_DURATION = Duration.millis(1200.0);

    /**
     * Default amplitude multiplier. {@code 1.0} yields the visual defaults
     * tuned per {@link PulseStyle}; {@code 0} flattens the animation; values
     * above {@code 1.0} exaggerate the effect.
     */
    public static final double DEFAULT_AMPLITUDE = 1.0;

    // ==================== Constructors ====================

    /**
     * Creates an indicator with the {@linkplain #DEFAULT_PULSE_STYLE default style}.
     */
    public RXDotPulse() {
        this(DEFAULT_PULSE_STYLE);
    }

    /**
     * Creates an indicator with the given pulse style.
     *
     * @param pulseStyle the initial pulse style; {@code null} falls back to
     *                   {@link #DEFAULT_PULSE_STYLE}
     */
    public RXDotPulse(@NamedArg("pulseStyle") PulseStyle pulseStyle) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setPulseStyle(pulseStyle == null ? DEFAULT_PULSE_STYLE : pulseStyle);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXDotPulseSkin(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Pulse Style ====================

    private final ObjectProperty<PulseStyle> pulseStyle = new StyleableObjectProperty<>(DEFAULT_PULSE_STYLE) {
        private PulseStyle lastValid = DEFAULT_PULSE_STYLE;

        @Override
        protected void invalidated() {
            PulseStyle v = get();
            if (v == null) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new NullPointerException("pulseStyle cannot be null");
            }
            lastValid = v;
        }

        @Override
        public Object getBean() {
            return RXDotPulse.this;
        }

        @Override
        public String getName() {
            return "pulseStyle";
        }

        @Override
        public CssMetaData<RXDotPulse, PulseStyle> getCssMetaData() {
            return StyleableProperties.PULSE_STYLE;
        }
    };

    /**
     * Animation variant. Cannot be set to {@code null} — doing so throws
     * {@link NullPointerException} and rolls back to the previous value
     * (unless the property is currently bound).
     *
     * <p>The name is {@code pulseStyle} rather than {@code style} to avoid
     * shadowing {@link javafx.scene.Node#styleProperty()}, which is final and
     * typed as {@code StringProperty}.
     *
     * @return the pulse-style property
     */
    public final ObjectProperty<PulseStyle> pulseStyleProperty() {
        return pulseStyle;
    }

    public final PulseStyle getPulseStyle() {
        return pulseStyle.get();
    }

    public final void setPulseStyle(PulseStyle value) {
        pulseStyle.set(value);
    }

    // ==================== Dot Count ====================

    private final IntegerProperty dotCount = new StyleableIntegerProperty(DEFAULT_DOT_COUNT) {
        @Override
        public Object getBean() {
            return RXDotPulse.this;
        }

        @Override
        public String getName() {
            return "dotCount";
        }

        @Override
        public CssMetaData<RXDotPulse, Number> getCssMetaData() {
            return StyleableProperties.DOT_COUNT;
        }
    };

    /**
     * Number of dots in the row. Values outside
     * {@code [}{@link #MIN_DOT_COUNT}{@code , }{@link #MAX_DOT_COUNT}{@code ]}
     * are clamped at render time.
     *
     * @return the dot-count property
     */
    public final IntegerProperty dotCountProperty() {
        return dotCount;
    }

    public final int getDotCount() {
        return dotCount.get();
    }

    public final void setDotCount(int value) {
        dotCount.set(value);
    }

    // ==================== Dot Size ====================

    private final DoubleProperty dotSize = new StyleableDoubleProperty(DEFAULT_DOT_SIZE) {
        @Override
        public Object getBean() {
            return RXDotPulse.this;
        }

        @Override
        public String getName() {
            return "dotSize";
        }

        @Override
        public CssMetaData<RXDotPulse, Number> getCssMetaData() {
            return StyleableProperties.DOT_SIZE;
        }
    };

    /**
     * Diameter of each dot, in pixels. Negative values and {@code NaN} are
     * treated as {@code 0} at render time (the dot collapses to invisible).
     *
     * @return the dot-size property
     */
    public final DoubleProperty dotSizeProperty() {
        return dotSize;
    }

    public final double getDotSize() {
        return dotSize.get();
    }

    public final void setDotSize(double value) {
        dotSize.set(value);
    }

    // ==================== Dot Gap ====================

    private final DoubleProperty dotGap = new StyleableDoubleProperty(DEFAULT_DOT_GAP) {
        @Override
        public Object getBean() {
            return RXDotPulse.this;
        }

        @Override
        public String getName() {
            return "dotGap";
        }

        @Override
        public CssMetaData<RXDotPulse, Number> getCssMetaData() {
            return StyleableProperties.DOT_GAP;
        }
    };

    /**
     * Horizontal gap between adjacent dots, in pixels. Negative values and
     * {@code NaN} are treated as {@code 0} at render time.
     *
     * @return the dot-gap property
     */
    public final DoubleProperty dotGapProperty() {
        return dotGap;
    }

    public final double getDotGap() {
        return dotGap.get();
    }

    public final void setDotGap(double value) {
        dotGap.set(value);
    }

    // ==================== Cycle Duration ====================

    private final ObjectProperty<Duration> cycleDuration =
            new StyleableObjectProperty<>(DEFAULT_CYCLE_DURATION) {
                @Override
                public Object getBean() {
                    return RXDotPulse.this;
                }

                @Override
                public String getName() {
                    return "cycleDuration";
                }

                @Override
                public CssMetaData<RXDotPulse, Duration> getCssMetaData() {
                    return StyleableProperties.CYCLE_DURATION;
                }
            };

    /**
     * Duration of one full staggered pass over all dots. A value of
     * {@code null} or any {@code Duration} less than or equal to
     * {@link Duration#ZERO} suppresses the animation — the dots collapse to
     * their resting pose.
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

    // ==================== Amplitude ====================

    private final DoubleProperty amplitude = new StyleableDoubleProperty(DEFAULT_AMPLITUDE) {
        @Override
        public Object getBean() {
            return RXDotPulse.this;
        }

        @Override
        public String getName() {
            return "amplitude";
        }

        @Override
        public CssMetaData<RXDotPulse, Number> getCssMetaData() {
            return StyleableProperties.AMPLITUDE;
        }
    };

    /**
     * Multiplier applied to the per-style peak effect:
     * <ul>
     *   <li>{@link PulseStyle#BOUNCE}: peak upward translation in pixels =
     *       {@code amplitude * dotSize * 0.75}</li>
     *   <li>{@link PulseStyle#PULSE}: peak scale = {@code 1 + amplitude * 0.5}</li>
     *   <li>{@link PulseStyle#FADE}: opacity range = {@code [1 - 0.7 * amplitude, 1]},
     *       clamped to {@code [0, 1]}</li>
     * </ul>
     * Negative values and {@code NaN} are treated as {@code 0} at render time
     * (the animation flattens; the dots stay at their resting pose).
     *
     * @return the amplitude property
     */
    public final DoubleProperty amplitudeProperty() {
        return amplitude;
    }

    public final double getAmplitude() {
        return amplitude.get();
    }

    public final void setAmplitude(double value) {
        amplitude.set(value);
    }

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXDotPulse, PulseStyle> PULSE_STYLE =
                new CssMetaData<>("-rx-pulse-style",
                        new EnumConverter<>(PulseStyle.class),
                        DEFAULT_PULSE_STYLE) {
                    @Override
                    public boolean isSettable(RXDotPulse n) {
                        return !n.pulseStyle.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<PulseStyle> getStyleableProperty(RXDotPulse n) {
                        return (StyleableProperty<PulseStyle>) n.pulseStyleProperty();
                    }
                };

        private static final CssMetaData<RXDotPulse, Number> DOT_COUNT =
                new CssMetaData<>("-rx-dot-count",
                        SizeConverter.getInstance(),
                        DEFAULT_DOT_COUNT) {
                    @Override
                    public boolean isSettable(RXDotPulse n) {
                        return !n.dotCount.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXDotPulse n) {
                        return (StyleableProperty<Number>) n.dotCountProperty();
                    }
                };

        private static final CssMetaData<RXDotPulse, Number> DOT_SIZE =
                new CssMetaData<>("-rx-dot-size",
                        SizeConverter.getInstance(),
                        DEFAULT_DOT_SIZE) {
                    @Override
                    public boolean isSettable(RXDotPulse n) {
                        return !n.dotSize.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXDotPulse n) {
                        return (StyleableProperty<Number>) n.dotSizeProperty();
                    }
                };

        private static final CssMetaData<RXDotPulse, Number> DOT_GAP =
                new CssMetaData<>("-rx-dot-gap",
                        SizeConverter.getInstance(),
                        DEFAULT_DOT_GAP) {
                    @Override
                    public boolean isSettable(RXDotPulse n) {
                        return !n.dotGap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXDotPulse n) {
                        return (StyleableProperty<Number>) n.dotGapProperty();
                    }
                };

        private static final CssMetaData<RXDotPulse, Duration> CYCLE_DURATION =
                new CssMetaData<>("-rx-cycle-duration",
                        DurationConverter.getInstance(),
                        DEFAULT_CYCLE_DURATION) {
                    @Override
                    public boolean isSettable(RXDotPulse n) {
                        return !n.cycleDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXDotPulse n) {
                        return (StyleableProperty<Duration>) n.cycleDurationProperty();
                    }
                };

        private static final CssMetaData<RXDotPulse, Number> AMPLITUDE =
                new CssMetaData<>("-rx-amplitude",
                        SizeConverter.getInstance(),
                        DEFAULT_AMPLITUDE) {
                    @Override
                    public boolean isSettable(RXDotPulse n) {
                        return !n.amplitude.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXDotPulse n) {
                        return (StyleableProperty<Number>) n.amplitudeProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Region.getClassCssMetaData());
            Collections.addAll(styleables,
                    PULSE_STYLE,
                    DOT_COUNT,
                    DOT_SIZE,
                    DOT_GAP,
                    CYCLE_DURATION,
                    AMPLITUDE);
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
