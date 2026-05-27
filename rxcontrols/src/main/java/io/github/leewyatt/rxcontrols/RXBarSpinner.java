package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXBarSpinnerSkin;
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
 * Indeterminate loading indicator that animates a row of vertical bars whose
 * heights rise and fall in a staggered phase, evoking an audio equalizer. Use
 * cases: inline "loading" indicators next to media controls, decorative
 * activity badges, "now playing" markers. The control is purely decorative —
 * unlike {@link RXAudioSpectrum} it does not consume real spectrum data, and
 * unlike {@link RXCircularProgressIndicator} it has no determinate progress.
 *
 * <p>Four animation variants are exposed via {@link #animationModeProperty() animationMode}:
 * <ul>
 *   <li>{@link AnimationMode#WAVE} (default) — every bar is always oscillating on a
 *       smooth sine curve; per-bar phase offset of {@code i / barCount}
 *       produces one continuous travelling wave across the row</li>
 *   <li>{@link AnimationMode#BOUNCE} — each bar bounces up and back during half of
 *       its local cycle and rests at the minimum height for the other half;
 *       with the same phase offset only a subset of bars is bouncing at any
 *       instant, reading as a sequence of pings rather than a flowing wave</li>
 *   <li>{@link AnimationMode#PULSE} — all bars share the same phase and oscillate
 *       on the smooth sine curve in lock-step; the row breathes up and down
 *       as a single block, like a heartbeat</li>
 *   <li>{@link AnimationMode#RANDOM} — each bar runs at its own frequency and
 *       phase offset (deterministic, not actually random), so the row reads as
 *       uncorrelated jitter — a "fake spectrum analyser" effect</li>
 * </ul>
 *
 * <p>Bars never collapse fully — {@link #minBarHeightRatioProperty()
 * minBarHeightRatio} floors them at a fraction of {@link #barHeightProperty()
 * barHeight} so the row stays visually present at the trough of the cycle.
 *
 * <p>Setting {@link #cycleDurationProperty() cycleDuration} to {@code null}
 * or any value {@code <= 0} stops the animation — the bars snap to their
 * resting pose (every bar at the minimum height) so the static frame stays
 * predictable. The animation also auto-pauses whenever the host window or
 * any ancestor is hidden, so an off-screen indicator does not burn CPU.
 *
 * <p>This control is purely decorative — it does not extend
 * {@link javafx.scene.control.ProgressIndicator}, so it has no determinate
 * mode and is not bindable to {@code Task#progressProperty()}.
 */
public class RXBarSpinner extends Control {

    /**
     * Animation variant for {@link RXBarSpinner}.
     */
    public enum AnimationMode {
        /**
         * Smooth sine wave across the row. Every bar is always oscillating
         * along the curve, so adjacent bars rise and fall in phase continuity
         * and the row reads as one flowing wave.
         */
        WAVE,
        /**
         * Half-cycle bounce with a rest period. Each bar climbs to peak height
         * and falls back during the first half of its local cycle, then sits
         * at the minimum height for the second half. Combined with the per-bar
         * phase offset only a subset of bars is bouncing at any instant — the
         * row reads as a sequence of discrete pings.
         */
        BOUNCE,
        /**
         * Synchronized "heartbeat" — every bar shares the same phase and
         * oscillates in lock-step, so the row rises and falls as a single
         * block. There is no travelling wave; the row breathes.
         */
        PULSE,
        /**
         * Per-bar independent oscillation. Each bar runs at its own
         * (deterministic, not actually stochastic) frequency and phase offset,
         * so the heights look uncorrelated — a "fake spectrum analyser" feel.
         * The pattern repeats over a long period determined by the bundled
         * frequency table.
         */
        RANDOM
    }

    private static final String DEFAULT_STYLE_CLASS = "rx-bar-spinner";

    // ==================== Public Defaults ====================

    /**
     * Default {@link AnimationMode}.
     */
    public static final AnimationMode DEFAULT_ANIMATION_MODE = AnimationMode.WAVE;

    /**
     * Default number of bars.
     */
    public static final int DEFAULT_BAR_COUNT = 5;

    /**
     * Minimum permitted {@link #barCountProperty() barCount} at render time.
     */
    public static final int MIN_BAR_COUNT = 2;

    /**
     * Maximum permitted {@link #barCountProperty() barCount} at render time.
     * Beyond this the indicator stops reading as an equalizer and starts to
     * look like a generic progress bar.
     */
    public static final int MAX_BAR_COUNT = 12;

    /**
     * Default bar width, in pixels.
     */
    public static final double DEFAULT_BAR_WIDTH = 4.0;

    /**
     * Default peak bar height, in pixels.
     */
    public static final double DEFAULT_BAR_HEIGHT = 24.0;

    /**
     * Default horizontal gap between adjacent bars, in pixels.
     */
    public static final double DEFAULT_BAR_GAP = 4.0;

    /**
     * Default cycle duration for one full staggered pass over all bars.
     */
    public static final Duration DEFAULT_CYCLE_DURATION = Duration.millis(1000.0);

    /**
     * Default minimum-bar-height ratio. Bars never shrink below
     * {@code barHeight * DEFAULT_MIN_BAR_HEIGHT_RATIO} so the row stays
     * visually present at the trough of the cycle.
     */
    public static final double DEFAULT_MIN_BAR_HEIGHT_RATIO = 0.2;

    // ==================== Constructors ====================

    /**
     * Creates an indicator with the {@linkplain #DEFAULT_ANIMATION_MODE default animation mode}.
     */
    public RXBarSpinner() {
        this(DEFAULT_ANIMATION_MODE);
    }

    /**
     * Creates an indicator with the given animation mode.
     *
     * @param animationMode the initial animation mode; {@code null} falls back
     *                      to {@link #DEFAULT_ANIMATION_MODE}
     */
    public RXBarSpinner(@NamedArg("animationMode") AnimationMode animationMode) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAnimationMode(animationMode == null ? DEFAULT_ANIMATION_MODE : animationMode);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXBarSpinnerSkin(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Animation Mode ====================

    private final ObjectProperty<AnimationMode> animationMode = new StyleableObjectProperty<>(DEFAULT_ANIMATION_MODE) {
        private AnimationMode lastValid = DEFAULT_ANIMATION_MODE;

        @Override
        protected void invalidated() {
            AnimationMode v = get();
            if (v == null) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new NullPointerException("animationMode cannot be null");
            }
            lastValid = v;
        }

        @Override
        public Object getBean() {
            return RXBarSpinner.this;
        }

        @Override
        public String getName() {
            return "animationMode";
        }

        @Override
        public CssMetaData<RXBarSpinner, AnimationMode> getCssMetaData() {
            return StyleableProperties.ANIMATION_MODE;
        }
    };

    /**
     * Animation variant. Cannot be set to {@code null} — doing so throws
     * {@link NullPointerException} and rolls back to the previous value
     * (unless the property is currently bound).
     *
     * @return the animation-mode property
     */
    public final ObjectProperty<AnimationMode> animationModeProperty() {
        return animationMode;
    }

    /**
     * Returns the current animation mode.
     *
     * @return the animation mode
     */
    public final AnimationMode getAnimationMode() {
        return animationMode.get();
    }

    /**
     * Sets the animation mode.
     *
     * @param value the animation mode; cannot be {@code null}
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public final void setAnimationMode(AnimationMode value) {
        animationMode.set(value);
    }

    // ==================== Bar Count ====================

    private final IntegerProperty barCount = new StyleableIntegerProperty(DEFAULT_BAR_COUNT) {
        @Override
        public Object getBean() {
            return RXBarSpinner.this;
        }

        @Override
        public String getName() {
            return "barCount";
        }

        @Override
        public CssMetaData<RXBarSpinner, Number> getCssMetaData() {
            return StyleableProperties.BAR_COUNT;
        }
    };

    /**
     * Number of bars in the row. Values outside
     * {@code [}{@link #MIN_BAR_COUNT}{@code , }{@link #MAX_BAR_COUNT}{@code ]}
     * are clamped at render time.
     *
     * @return the bar-count property
     */
    public final IntegerProperty barCountProperty() {
        return barCount;
    }

    public final int getBarCount() {
        return barCount.get();
    }

    public final void setBarCount(int value) {
        barCount.set(value);
    }

    // ==================== Bar Width ====================

    private final DoubleProperty barWidth = new StyleableDoubleProperty(DEFAULT_BAR_WIDTH) {
        @Override
        public Object getBean() {
            return RXBarSpinner.this;
        }

        @Override
        public String getName() {
            return "barWidth";
        }

        @Override
        public CssMetaData<RXBarSpinner, Number> getCssMetaData() {
            return StyleableProperties.BAR_WIDTH;
        }
    };

    /**
     * Width of each bar, in pixels. Negative values and {@code NaN} are
     * treated as {@code 0} at render time (the bar collapses to invisible).
     *
     * @return the bar-width property
     */
    public final DoubleProperty barWidthProperty() {
        return barWidth;
    }

    public final double getBarWidth() {
        return barWidth.get();
    }

    public final void setBarWidth(double value) {
        barWidth.set(value);
    }

    // ==================== Bar Height ====================

    private final DoubleProperty barHeight = new StyleableDoubleProperty(DEFAULT_BAR_HEIGHT) {
        @Override
        public Object getBean() {
            return RXBarSpinner.this;
        }

        @Override
        public String getName() {
            return "barHeight";
        }

        @Override
        public CssMetaData<RXBarSpinner, Number> getCssMetaData() {
            return StyleableProperties.BAR_HEIGHT;
        }
    };

    /**
     * Peak height of each bar, in pixels. Drives the control's
     * {@code prefHeight}. Negative values and {@code NaN} are treated as
     * {@code 0} at render time.
     *
     * @return the bar-height property
     */
    public final DoubleProperty barHeightProperty() {
        return barHeight;
    }

    public final double getBarHeight() {
        return barHeight.get();
    }

    public final void setBarHeight(double value) {
        barHeight.set(value);
    }

    // ==================== Bar Gap ====================

    private final DoubleProperty barGap = new StyleableDoubleProperty(DEFAULT_BAR_GAP) {
        @Override
        public Object getBean() {
            return RXBarSpinner.this;
        }

        @Override
        public String getName() {
            return "barGap";
        }

        @Override
        public CssMetaData<RXBarSpinner, Number> getCssMetaData() {
            return StyleableProperties.BAR_GAP;
        }
    };

    /**
     * Horizontal gap between adjacent bars, in pixels. Negative values and
     * {@code NaN} are treated as {@code 0} at render time.
     *
     * @return the bar-gap property
     */
    public final DoubleProperty barGapProperty() {
        return barGap;
    }

    public final double getBarGap() {
        return barGap.get();
    }

    public final void setBarGap(double value) {
        barGap.set(value);
    }

    // ==================== Cycle Duration ====================

    private final ObjectProperty<Duration> cycleDuration =
            new StyleableObjectProperty<>(DEFAULT_CYCLE_DURATION) {
                @Override
                public Object getBean() {
                    return RXBarSpinner.this;
                }

                @Override
                public String getName() {
                    return "cycleDuration";
                }

                @Override
                public CssMetaData<RXBarSpinner, Duration> getCssMetaData() {
                    return StyleableProperties.CYCLE_DURATION;
                }
            };

    /**
     * Duration of one full staggered pass over all bars. A value of
     * {@code null} or any {@code Duration} less than or equal to
     * {@link Duration#ZERO} suppresses the animation — the bars collapse to
     * their resting pose (every bar at the minimum height).
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

    // ==================== Min Bar Height Ratio ====================

    private final DoubleProperty minBarHeightRatio =
            new StyleableDoubleProperty(DEFAULT_MIN_BAR_HEIGHT_RATIO) {
                @Override
                public Object getBean() {
                    return RXBarSpinner.this;
                }

                @Override
                public String getName() {
                    return "minBarHeightRatio";
                }

                @Override
                public CssMetaData<RXBarSpinner, Number> getCssMetaData() {
                    return StyleableProperties.MIN_BAR_HEIGHT_RATIO;
                }
            };

    /**
     * Lower-bound bar height, expressed as a fraction of
     * {@link #barHeightProperty() barHeight}. {@code 0} lets each bar
     * collapse fully at the trough of its phase; {@code 1} flattens the
     * animation (every bar always at peak height). Values outside
     * {@code [0, 1]} are clamped at render time.
     *
     * @return the min-bar-height-ratio property
     */
    public final DoubleProperty minBarHeightRatioProperty() {
        return minBarHeightRatio;
    }

    public final double getMinBarHeightRatio() {
        return minBarHeightRatio.get();
    }

    public final void setMinBarHeightRatio(double value) {
        minBarHeightRatio.set(value);
    }

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXBarSpinner, AnimationMode> ANIMATION_MODE =
                new CssMetaData<>("-rx-animation-mode",
                        new EnumConverter<>(AnimationMode.class),
                        DEFAULT_ANIMATION_MODE) {
                    @Override
                    public boolean isSettable(RXBarSpinner n) {
                        return !n.animationMode.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<AnimationMode> getStyleableProperty(RXBarSpinner n) {
                        return (StyleableProperty<AnimationMode>) n.animationModeProperty();
                    }
                };

        private static final CssMetaData<RXBarSpinner, Number> BAR_COUNT =
                new CssMetaData<>("-rx-bar-count",
                        SizeConverter.getInstance(),
                        DEFAULT_BAR_COUNT) {
                    @Override
                    public boolean isSettable(RXBarSpinner n) {
                        return !n.barCount.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXBarSpinner n) {
                        return (StyleableProperty<Number>) n.barCountProperty();
                    }
                };

        private static final CssMetaData<RXBarSpinner, Number> BAR_WIDTH =
                new CssMetaData<>("-rx-bar-width",
                        SizeConverter.getInstance(),
                        DEFAULT_BAR_WIDTH) {
                    @Override
                    public boolean isSettable(RXBarSpinner n) {
                        return !n.barWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXBarSpinner n) {
                        return (StyleableProperty<Number>) n.barWidthProperty();
                    }
                };

        private static final CssMetaData<RXBarSpinner, Number> BAR_HEIGHT =
                new CssMetaData<>("-rx-bar-height",
                        SizeConverter.getInstance(),
                        DEFAULT_BAR_HEIGHT) {
                    @Override
                    public boolean isSettable(RXBarSpinner n) {
                        return !n.barHeight.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXBarSpinner n) {
                        return (StyleableProperty<Number>) n.barHeightProperty();
                    }
                };

        private static final CssMetaData<RXBarSpinner, Number> BAR_GAP =
                new CssMetaData<>("-rx-bar-gap",
                        SizeConverter.getInstance(),
                        DEFAULT_BAR_GAP) {
                    @Override
                    public boolean isSettable(RXBarSpinner n) {
                        return !n.barGap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXBarSpinner n) {
                        return (StyleableProperty<Number>) n.barGapProperty();
                    }
                };

        private static final CssMetaData<RXBarSpinner, Duration> CYCLE_DURATION =
                new CssMetaData<>("-rx-cycle-duration",
                        DurationConverter.getInstance(),
                        DEFAULT_CYCLE_DURATION) {
                    @Override
                    public boolean isSettable(RXBarSpinner n) {
                        return !n.cycleDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXBarSpinner n) {
                        return (StyleableProperty<Duration>) n.cycleDurationProperty();
                    }
                };

        private static final CssMetaData<RXBarSpinner, Number> MIN_BAR_HEIGHT_RATIO =
                new CssMetaData<>("-rx-min-bar-height-ratio",
                        SizeConverter.getInstance(),
                        DEFAULT_MIN_BAR_HEIGHT_RATIO) {
                    @Override
                    public boolean isSettable(RXBarSpinner n) {
                        return !n.minBarHeightRatio.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXBarSpinner n) {
                        return (StyleableProperty<Number>) n.minBarHeightRatioProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Region.getClassCssMetaData());
            Collections.addAll(styleables,
                    ANIMATION_MODE,
                    BAR_COUNT,
                    BAR_WIDTH,
                    BAR_HEIGHT,
                    BAR_GAP,
                    CYCLE_DURATION,
                    MIN_BAR_HEIGHT_RATIO);
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
