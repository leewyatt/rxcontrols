package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXWaveProgressIndicatorSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.PaintConverter;
import javafx.css.converter.SizeConverter;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.util.Duration;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Circular wave-fill progress indicator. The control draws a round container
 * whose water-level mirrors {@link #getProgress() progress}; two layered sine
 * waves scroll horizontally over the water surface. Extends
 * {@link ProgressIndicator} so it can be bound to
 * {@code javafx.concurrent.Task#progressProperty()} interchangeably with the
 * native indicator.
 *
 * <p>Visual structure (driven by the default skin):
 * <ul>
 *   <li>circular container — drawn by {@link javafx.scene.layout.Region}'s standard
 *       background ({@code -fx-background-color} / {@code -fx-background-radius})</li>
 *   <li>{@code .back-wave} — rear wave layer (smaller amplitude, slower, phase-shifted)</li>
 *   <li>{@code .front-wave} — front wave layer (full amplitude)</li>
 *   <li>optional outer ring — use {@code -fx-border-color} / {@code -fx-border-width}
 *       / {@code -fx-border-radius} / {@code -fx-padding} via CSS or
 *       {@link javafx.scene.layout.Region#setBorder(javafx.scene.layout.Border) setBorder}</li>
 *   <li>{@code .progress-label} — renders the converted progress text;
 *       water-line dual-colour rendering uses a hidden mirror label clipped to
 *       the front-wave surface ({@code .progress-label.below-water})</li>
 * </ul>
 *
 * <p>This control deliberately does not own a {@code graphic} property: the
 * dual-label water-line trick cannot mirror an arbitrary graphic node (a node
 * has only one parent). To compose with an icon, wrap externally:
 * {@code new StackPane(indicator, iconNode)}.
 *
 * <p>Pseudo-classes (in addition to {@code :determinate} / {@code :indeterminate}
 * inherited from {@link ProgressIndicator}):
 * <ul>
 *   <li>{@code :completed} — set when {@code progress >= 1.0}</li>
 *   <li>{@code :empty} — set when {@code progress == 0.0}; CSS uses this to
 *       flatten the wave amplitude so the surface no longer fights the circle
 *       baseline visually</li>
 * </ul>
 *
 * <p>The wave-scroll and indeterminate animations auto-pause whenever the host
 * window or any ancestor of the control is hidden, so an off-screen indicator
 * does not waste CPU.
 */
public class RXWaveProgressIndicator extends ProgressIndicator {

    private static final String DEFAULT_STYLE_CLASS = "rx-wave-progress-indicator";

    private static final PseudoClass PSEUDO_CLASS_COMPLETED = PseudoClass.getPseudoClass("completed");
    private static final PseudoClass PSEUDO_CLASS_EMPTY = PseudoClass.getPseudoClass("empty");

    // ==================== Public Defaults ====================

    /**
     * Default resting crest height of the wave, in pixels.
     */
    public static final double DEFAULT_WAVE_AMPLITUDE = 6.0;

    /**
     * Default base wavelength, in pixels. A value of {@code 0} lets the skin
     * fall back to the container diameter.
     */
    public static final double DEFAULT_WAVE_LENGTH = 0.0;

    /**
     * Default cycle duration for the front wave's horizontal scroll.
     */
    public static final Duration DEFAULT_WAVE_CYCLE_DURATION = Duration.millis(2000.0);

    /**
     * Default ratio applied to the front cycle duration to derive the back
     * wave's cycle. {@code > 1} means the back wave moves slower than the
     * front, mimicking depth.
     */
    public static final double DEFAULT_BACK_WAVE_SPEED_RATIO = 1.4;

    /**
     * Default ratio applied to the front amplitude to derive the back
     * amplitude. {@code < 1} keeps the back wave subtler than the front.
     */
    public static final double DEFAULT_BACK_WAVE_AMPLITUDE_RATIO = 0.7;

    /**
     * Default front-wave fill (opaque blue).
     */
    public static final Paint DEFAULT_FRONT_WAVE_FILL = Color.web("#1E90FF");

    /**
     * Default back-wave fill (translucent blue).
     */
    public static final Paint DEFAULT_BACK_WAVE_FILL = Color.web("#1E90FF", 0.4);

    /**
     * Default cycle duration for the indeterminate breathing animation.
     */
    public static final Duration DEFAULT_INDETERMINATE_CYCLE_DURATION = Duration.millis(2500.0);

    /**
     * Default tween duration applied to determinate progress changes.
     */
    public static final Duration DEFAULT_PROGRESS_TRANSITION_DURATION = Duration.millis(250.0);

    /**
     * Default text factory used by the skin when {@link #textFactoryProperty()} is {@code null}.
     */
    public static final Callback<Double, String> DEFAULT_TEXT_FACTORY = progress -> {
        if (progress == null || progress < 0.0) {
            return "";
        }
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        return Math.round(clamped * 100.0) + "%";
    };

    // ==================== Constructors ====================

    /**
     * Creates an indeterminate indicator.
     */
    public RXWaveProgressIndicator() {
        this(INDETERMINATE_PROGRESS);
    }

    /**
     * Creates an indicator with the given initial progress.
     *
     * @param progress initial progress in {@code [0,1]}, or {@code -1} for indeterminate
     */
    public RXWaveProgressIndicator(@NamedArg("progress") double progress) {
        super(progress);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        progressProperty().addListener(obs -> updateProgressPseudoClasses());
        updateProgressPseudoClasses();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXWaveProgressIndicatorSkin(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    private void updateProgressPseudoClasses() {
        double p = getProgress();
        pseudoClassStateChanged(PSEUDO_CLASS_COMPLETED, p >= 1.0);
        pseudoClassStateChanged(PSEUDO_CLASS_EMPTY, p == 0.0);
    }

    // ==================== Text Factory ====================

    private final ObjectProperty<Callback<Double, String>> textFactory =
            new SimpleObjectProperty<>(this, "textFactory", DEFAULT_TEXT_FACTORY);

    /**
     * Factory that produces the centre progress text. Tolerates {@code null}
     * (skin falls back to {@link #DEFAULT_TEXT_FACTORY}).
     *
     * @return the textFactory property
     */
    public final ObjectProperty<Callback<Double, String>> textFactoryProperty() {
        return textFactory;
    }

    public final Callback<Double, String> getTextFactory() {
        return textFactory.get();
    }

    public final void setTextFactory(Callback<Double, String> value) {
        textFactory.set(value);
    }

    // ==================== Progress Transition Duration ====================

    private final ObjectProperty<Duration> progressTransitionDuration =
            new StyleableObjectProperty<>(DEFAULT_PROGRESS_TRANSITION_DURATION) {
                @Override
                public Object getBean() {
                    return RXWaveProgressIndicator.this;
                }

                @Override
                public String getName() {
                    return "progressTransitionDuration";
                }

                @Override
                public CssMetaData<RXWaveProgressIndicator, Duration> getCssMetaData() {
                    return StyleableProperties.PROGRESS_TRANSITION_DURATION;
                }
            };

    /**
     * Duration of the tween applied to determinate progress changes. A value
     * of {@code null} or any {@code Duration} less than or equal to
     * {@link Duration#ZERO} disables tweening — the water level jumps
     * directly to the new progress.
     *
     * @return the progress-transition-duration property
     */
    public final ObjectProperty<Duration> progressTransitionDurationProperty() {
        return progressTransitionDuration;
    }

    public final Duration getProgressTransitionDuration() {
        return progressTransitionDuration.get();
    }

    public final void setProgressTransitionDuration(Duration value) {
        progressTransitionDuration.set(value);
    }

    // ==================== Indeterminate Cycle Duration ====================

    private final ObjectProperty<Duration> indeterminateCycleDuration =
            new StyleableObjectProperty<>(DEFAULT_INDETERMINATE_CYCLE_DURATION) {
                @Override
                public Object getBean() {
                    return RXWaveProgressIndicator.this;
                }

                @Override
                public String getName() {
                    return "indeterminateCycleDuration";
                }

                @Override
                public CssMetaData<RXWaveProgressIndicator, Duration> getCssMetaData() {
                    return StyleableProperties.INDETERMINATE_CYCLE_DURATION;
                }
            };

    /**
     * Duration of one full breathing cycle of the indeterminate animation. A
     * value of {@code null} or any {@code Duration} less than or equal to
     * {@link Duration#ZERO} suppresses the breathing — the water level stays
     * at its mid-range pose.
     *
     * @return the indeterminate-cycle-duration property
     */
    public final ObjectProperty<Duration> indeterminateCycleDurationProperty() {
        return indeterminateCycleDuration;
    }

    public final Duration getIndeterminateCycleDuration() {
        return indeterminateCycleDuration.get();
    }

    public final void setIndeterminateCycleDuration(Duration value) {
        indeterminateCycleDuration.set(value);
    }

    // ==================== Wave Amplitude ====================

    private final DoubleProperty waveAmplitude = new StyleableDoubleProperty(DEFAULT_WAVE_AMPLITUDE) {
        @Override
        public Object getBean() {
            return RXWaveProgressIndicator.this;
        }

        @Override
        public String getName() {
            return "waveAmplitude";
        }

        @Override
        public CssMetaData<RXWaveProgressIndicator, Number> getCssMetaData() {
            return StyleableProperties.WAVE_AMPLITUDE;
        }
    };

    /**
     * Resting crest height of the wave, in pixels — the calm amplitude shown
     * once the water level is stable. While {@code progress} is changing the
     * surface briefly swells above this value, then settles back. Negative
     * values and {@code NaN} are clamped to {@code 0} at render time (the
     * surface flattens to a straight line).
     *
     * @return the wave-amplitude property
     */
    public final DoubleProperty waveAmplitudeProperty() {
        return waveAmplitude;
    }

    public final double getWaveAmplitude() {
        return waveAmplitude.get();
    }

    public final void setWaveAmplitude(double value) {
        waveAmplitude.set(value);
    }

    // ==================== Wave Length ====================

    private final DoubleProperty waveLength = new StyleableDoubleProperty(DEFAULT_WAVE_LENGTH) {
        @Override
        public Object getBean() {
            return RXWaveProgressIndicator.this;
        }

        @Override
        public String getName() {
            return "waveLength";
        }

        @Override
        public CssMetaData<RXWaveProgressIndicator, Number> getCssMetaData() {
            return StyleableProperties.WAVE_LENGTH;
        }
    };

    /**
     * Base wavelength of the wave surface, in pixels. The surface is a sum of
     * several sine components derived from this base; values of {@code 0},
     * negative, or {@code NaN} fall back to the container diameter at render
     * time.
     *
     * @return the wave-length property
     */
    public final DoubleProperty waveLengthProperty() {
        return waveLength;
    }

    public final double getWaveLength() {
        return waveLength.get();
    }

    public final void setWaveLength(double value) {
        waveLength.set(value);
    }

    // ==================== Wave Cycle Duration ====================

    private final ObjectProperty<Duration> waveCycleDuration =
            new StyleableObjectProperty<>(DEFAULT_WAVE_CYCLE_DURATION) {
                @Override
                public Object getBean() {
                    return RXWaveProgressIndicator.this;
                }

                @Override
                public String getName() {
                    return "waveCycleDuration";
                }

                @Override
                public CssMetaData<RXWaveProgressIndicator, Duration> getCssMetaData() {
                    return StyleableProperties.WAVE_CYCLE_DURATION;
                }
            };

    /**
     * Time for one full horizontal scroll of the front wave (one wavelength).
     * A value of {@code null} or any {@code Duration} less than or equal to
     * {@link Duration#ZERO} suppresses the scroll — the waves stay still.
     *
     * @return the wave-cycle-duration property
     */
    public final ObjectProperty<Duration> waveCycleDurationProperty() {
        return waveCycleDuration;
    }

    public final Duration getWaveCycleDuration() {
        return waveCycleDuration.get();
    }

    public final void setWaveCycleDuration(Duration value) {
        waveCycleDuration.set(value);
    }

    // ==================== Back Wave Speed Ratio ====================

    private final DoubleProperty backWaveSpeedRatio =
            new StyleableDoubleProperty(DEFAULT_BACK_WAVE_SPEED_RATIO) {
                @Override
                public Object getBean() {
                    return RXWaveProgressIndicator.this;
                }

                @Override
                public String getName() {
                    return "backWaveSpeedRatio";
                }

                @Override
                public CssMetaData<RXWaveProgressIndicator, Number> getCssMetaData() {
                    return StyleableProperties.BACK_WAVE_SPEED_RATIO;
                }
            };

    /**
     * Multiplier applied to {@link #waveCycleDurationProperty() waveCycleDuration}
     * to derive the back wave's cycle. Values {@code <= 0} or {@code NaN}
     * collapse to {@code 1.0} at render time (avoids a tight loop on the back
     * timeline).
     *
     * @return the back-wave-speed-ratio property
     */
    public final DoubleProperty backWaveSpeedRatioProperty() {
        return backWaveSpeedRatio;
    }

    public final double getBackWaveSpeedRatio() {
        return backWaveSpeedRatio.get();
    }

    public final void setBackWaveSpeedRatio(double value) {
        backWaveSpeedRatio.set(value);
    }

    // ==================== Back Wave Amplitude Ratio ====================

    private final DoubleProperty backWaveAmplitudeRatio =
            new StyleableDoubleProperty(DEFAULT_BACK_WAVE_AMPLITUDE_RATIO) {
                @Override
                public Object getBean() {
                    return RXWaveProgressIndicator.this;
                }

                @Override
                public String getName() {
                    return "backWaveAmplitudeRatio";
                }

                @Override
                public CssMetaData<RXWaveProgressIndicator, Number> getCssMetaData() {
                    return StyleableProperties.BACK_WAVE_AMPLITUDE_RATIO;
                }
            };

    /**
     * Multiplier applied to {@link #waveAmplitudeProperty() waveAmplitude} to
     * derive the back wave's amplitude. {@code 0} or negative hides the back
     * wave (its crest flattens onto the baseline).
     *
     * @return the back-wave-amplitude-ratio property
     */
    public final DoubleProperty backWaveAmplitudeRatioProperty() {
        return backWaveAmplitudeRatio;
    }

    public final double getBackWaveAmplitudeRatio() {
        return backWaveAmplitudeRatio.get();
    }

    public final void setBackWaveAmplitudeRatio(double value) {
        backWaveAmplitudeRatio.set(value);
    }

    // ==================== Front Wave Fill ====================

    private final ObjectProperty<Paint> frontWaveFill = new StyleableObjectProperty<>(DEFAULT_FRONT_WAVE_FILL) {
        @Override
        public Object getBean() {
            return RXWaveProgressIndicator.this;
        }

        @Override
        public String getName() {
            return "frontWaveFill";
        }

        @Override
        public CssMetaData<RXWaveProgressIndicator, Paint> getCssMetaData() {
            return StyleableProperties.FRONT_WAVE_FILL;
        }
    };

    /**
     * Paint used to fill the front wave. Initial value is
     * {@link #DEFAULT_FRONT_WAVE_FILL}; setting {@code null} renders no fill
     * (transparent), per the JavaFX {@code Shape.setFill} convention.
     *
     * @return the front-wave-fill property
     */
    public final ObjectProperty<Paint> frontWaveFillProperty() {
        return frontWaveFill;
    }

    public final Paint getFrontWaveFill() {
        return frontWaveFill.get();
    }

    public final void setFrontWaveFill(Paint value) {
        frontWaveFill.set(value);
    }

    // ==================== Back Wave Fill ====================

    private final ObjectProperty<Paint> backWaveFill = new StyleableObjectProperty<>(DEFAULT_BACK_WAVE_FILL) {
        @Override
        public Object getBean() {
            return RXWaveProgressIndicator.this;
        }

        @Override
        public String getName() {
            return "backWaveFill";
        }

        @Override
        public CssMetaData<RXWaveProgressIndicator, Paint> getCssMetaData() {
            return StyleableProperties.BACK_WAVE_FILL;
        }
    };

    /**
     * Paint used to fill the back wave. Initial value is
     * {@link #DEFAULT_BACK_WAVE_FILL}; setting {@code null} renders no fill
     * (transparent), per the JavaFX {@code Shape.setFill} convention.
     *
     * @return the back-wave-fill property
     */
    public final ObjectProperty<Paint> backWaveFillProperty() {
        return backWaveFill;
    }

    public final Paint getBackWaveFill() {
        return backWaveFill.get();
    }

    public final void setBackWaveFill(Paint value) {
        backWaveFill.set(value);
    }

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXWaveProgressIndicator, Duration> PROGRESS_TRANSITION_DURATION =
                new CssMetaData<>("-rx-progress-transition-duration",
                        DurationConverter.getInstance(),
                        DEFAULT_PROGRESS_TRANSITION_DURATION) {
                    @Override
                    public boolean isSettable(RXWaveProgressIndicator n) {
                        return !n.progressTransitionDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXWaveProgressIndicator n) {
                        return (StyleableProperty<Duration>) n.progressTransitionDurationProperty();
                    }
                };

        private static final CssMetaData<RXWaveProgressIndicator, Duration> INDETERMINATE_CYCLE_DURATION =
                new CssMetaData<>("-rx-indeterminate-cycle-duration",
                        DurationConverter.getInstance(),
                        DEFAULT_INDETERMINATE_CYCLE_DURATION) {
                    @Override
                    public boolean isSettable(RXWaveProgressIndicator n) {
                        return !n.indeterminateCycleDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXWaveProgressIndicator n) {
                        return (StyleableProperty<Duration>) n.indeterminateCycleDurationProperty();
                    }
                };

        private static final CssMetaData<RXWaveProgressIndicator, Number> WAVE_AMPLITUDE =
                new CssMetaData<>("-rx-wave-amplitude", SizeConverter.getInstance(), DEFAULT_WAVE_AMPLITUDE) {
                    @Override
                    public boolean isSettable(RXWaveProgressIndicator n) {
                        return !n.waveAmplitude.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXWaveProgressIndicator n) {
                        return (StyleableProperty<Number>) n.waveAmplitudeProperty();
                    }
                };

        private static final CssMetaData<RXWaveProgressIndicator, Number> WAVE_LENGTH =
                new CssMetaData<>("-rx-wave-length", SizeConverter.getInstance(), DEFAULT_WAVE_LENGTH) {
                    @Override
                    public boolean isSettable(RXWaveProgressIndicator n) {
                        return !n.waveLength.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXWaveProgressIndicator n) {
                        return (StyleableProperty<Number>) n.waveLengthProperty();
                    }
                };

        private static final CssMetaData<RXWaveProgressIndicator, Duration> WAVE_CYCLE_DURATION =
                new CssMetaData<>("-rx-wave-cycle-duration",
                        DurationConverter.getInstance(),
                        DEFAULT_WAVE_CYCLE_DURATION) {
                    @Override
                    public boolean isSettable(RXWaveProgressIndicator n) {
                        return !n.waveCycleDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXWaveProgressIndicator n) {
                        return (StyleableProperty<Duration>) n.waveCycleDurationProperty();
                    }
                };

        private static final CssMetaData<RXWaveProgressIndicator, Number> BACK_WAVE_SPEED_RATIO =
                new CssMetaData<>("-rx-back-wave-speed-ratio",
                        SizeConverter.getInstance(),
                        DEFAULT_BACK_WAVE_SPEED_RATIO) {
                    @Override
                    public boolean isSettable(RXWaveProgressIndicator n) {
                        return !n.backWaveSpeedRatio.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXWaveProgressIndicator n) {
                        return (StyleableProperty<Number>) n.backWaveSpeedRatioProperty();
                    }
                };

        private static final CssMetaData<RXWaveProgressIndicator, Number> BACK_WAVE_AMPLITUDE_RATIO =
                new CssMetaData<>("-rx-back-wave-amplitude-ratio",
                        SizeConverter.getInstance(),
                        DEFAULT_BACK_WAVE_AMPLITUDE_RATIO) {
                    @Override
                    public boolean isSettable(RXWaveProgressIndicator n) {
                        return !n.backWaveAmplitudeRatio.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXWaveProgressIndicator n) {
                        return (StyleableProperty<Number>) n.backWaveAmplitudeRatioProperty();
                    }
                };

        private static final CssMetaData<RXWaveProgressIndicator, Paint> FRONT_WAVE_FILL =
                new CssMetaData<>("-rx-front-wave-fill", PaintConverter.getInstance(), DEFAULT_FRONT_WAVE_FILL) {
                    @Override
                    public boolean isSettable(RXWaveProgressIndicator n) {
                        return !n.frontWaveFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXWaveProgressIndicator n) {
                        return (StyleableProperty<Paint>) n.frontWaveFillProperty();
                    }
                };

        private static final CssMetaData<RXWaveProgressIndicator, Paint> BACK_WAVE_FILL =
                new CssMetaData<>("-rx-back-wave-fill", PaintConverter.getInstance(), DEFAULT_BACK_WAVE_FILL) {
                    @Override
                    public boolean isSettable(RXWaveProgressIndicator n) {
                        return !n.backWaveFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXWaveProgressIndicator n) {
                        return (StyleableProperty<Paint>) n.backWaveFillProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(ProgressIndicator.getClassCssMetaData());
            Collections.addAll(styleables,
                    PROGRESS_TRANSITION_DURATION,
                    INDETERMINATE_CYCLE_DURATION,
                    WAVE_AMPLITUDE,
                    WAVE_LENGTH,
                    WAVE_CYCLE_DURATION,
                    BACK_WAVE_SPEED_RATIO,
                    BACK_WAVE_AMPLITUDE_RATIO,
                    FRONT_WAVE_FILL,
                    BACK_WAVE_FILL);
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
