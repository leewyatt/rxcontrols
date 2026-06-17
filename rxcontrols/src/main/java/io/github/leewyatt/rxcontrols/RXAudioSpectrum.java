package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXAudioSpectrumSkin;
import io.github.leewyatt.rxcontrols.spectrum.BandLayout;
import io.github.leewyatt.rxcontrols.spectrum.SpectrumVisualization;
import io.github.leewyatt.rxcontrols.spectrum.VisBars;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Audio spectrum visualizer. Renders per-band magnitudes fed through
 * {@link #updateSpectrum(float[])} on a single canvas, with the visual style
 * supplied by a pluggable {@link SpectrumVisualization} strategy.
 *
 * <p>Typical wiring with {@code javafx.media} (this library never depends on
 * the media module itself):
 * <pre>{@code
 * player.setAudioSpectrumListener((t, d, mags, phases) -> spectrum.updateSpectrum(mags));
 * }</pre>
 *
 * <p>Pseudo-classes: {@code :active} while data is flowing, {@code :silent}
 * once the display has settled to silence (also the initial state).
 */
public class RXAudioSpectrum extends Control {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-audio-spectrum";

    private static final PseudoClass PSEUDO_CLASS_ACTIVE = PseudoClass.getPseudoClass("active");

    private static final PseudoClass PSEUDO_CLASS_SILENT = PseudoClass.getPseudoClass("silent");

    /**
     * Default number of display bands.
     */
    private static final int DEFAULT_BAND_COUNT = 128;

    /**
     * Minimum allowed number of display bands.
     */
    public static final int MIN_BAND_COUNT = 2;

    /**
     * Default lower bound of the decibel window mapped to zero amplitude.
     */
    private static final double DEFAULT_MIN_DECIBELS = -60.0;

    /**
     * Default arrangement of source bands across the display slots.
     */
    public static final BandLayout DEFAULT_BAND_LAYOUT = BandLayout.LINEAR;

    /**
     * Default bar paint: a vertical amplitude-keyed gradient running from cool
     * colors at low amplitude to warm colors at full amplitude.
     */
    public static final Paint DEFAULT_BAR_FILL = new LinearGradient(0.0, 1.0, 0.0, 0.0, true,
            CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.web("#2e9fff")),
            new Stop(0.45, Color.web("#29e6a7")),
            new Stop(0.75, Color.web("#ffd166")),
            new Stop(1.0, Color.web("#ff5e5e")));

    /**
     * Default peak-cap paint: a brighter solid color.
     */
    public static final Paint DEFAULT_PEAK_FILL = Color.web("#ffffff", 0.85);

    /**
     * Default fraction of each band slot left empty between adjacent bars.
     */
    public static final double DEFAULT_BAR_GAP_RATIO = 0.2;

    /**
     * Upper bound the skin clamps {@link #barGapRatioProperty()} to.
     */
    public static final double MAX_BAR_GAP_RATIO = 0.9;

    /**
     * Default release smoothing factor.
     */
    public static final double DEFAULT_SMOOTHING = 0.8;

    /**
     * Upper bound the skin clamps {@link #smoothingProperty()} to.
     */
    public static final double MAX_SMOOTHING = 0.98;

    // ==================== Constructors ====================

    /**
     * Creates an audio spectrum visualizer in the silent state.
     */
    public RXAudioSpectrum() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setFocusTraversable(false);
        pseudoClassStateChanged(PSEUDO_CLASS_SILENT, true);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXAudioSpectrumSkin(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Visualization ====================

    private final ObjectProperty<SpectrumVisualization> visualization =
            new SimpleObjectProperty<>(this, "visualization", new VisBars());

    /**
     * The visual effect strategy used to render the spectrum. Initial value is
     * a {@link VisBars} instance. Tolerates {@code null} (the skin falls back
     * to an internal default {@link VisBars}). A visualization instance must
     * not be attached to more than one control at a time.
     *
     * @return the visualization property
     */
    public final ObjectProperty<SpectrumVisualization> visualizationProperty() {
        return visualization;
    }

    public final SpectrumVisualization getVisualization() {
        return visualization.get();
    }

    public final void setVisualization(SpectrumVisualization value) {
        visualization.set(value);
    }

    // ==================== Band Count ====================

    private final IntegerProperty bandCount = new SimpleIntegerProperty(this, "bandCount", DEFAULT_BAND_COUNT) {
        @Override
        protected void invalidated() {
            if (get() < MIN_BAND_COUNT) {
                if (!isBound()) {
                    set(MIN_BAND_COUNT);
                }
                throw new IllegalArgumentException("bandCount must be >= " + MIN_BAND_COUNT);
            }
        }
    };

    /**
     * The number of display bands. Incoming magnitude frames of any length are
     * re-bucketed onto this many bands (max-per-bucket).
     *
     * @return the band count property
     */
    public final IntegerProperty bandCountProperty() {
        return bandCount;
    }

    public final int getBandCount() {
        return bandCount.get();
    }

    /**
     * Sets the number of display bands.
     *
     * @param value the band count
     * @throws IllegalArgumentException if {@code value < }{@link #MIN_BAND_COUNT}
     *                                  (the property is coerced to the minimum)
     */
    public final void setBandCount(int value) {
        bandCount.set(value);
    }

    // ==================== Min Decibels ====================

    private final DoubleProperty minDecibels = new SimpleDoubleProperty(this, "minDecibels", DEFAULT_MIN_DECIBELS) {
        @Override
        protected void invalidated() {
            double value = get();
            if (Double.isNaN(value) || value >= 0.0) {
                if (!isBound()) {
                    set(DEFAULT_MIN_DECIBELS);
                }
                throw new IllegalArgumentException(
                        "minDecibels must be a negative decibel value, but was " + value);
            }
        }
    };

    /**
     * The lower bound of the decibel window, mapped to zero amplitude;
     * {@code 0 dB} maps to full amplitude. Matches the
     * {@code MediaPlayer.audioSpectrumThreshold} convention of non-positive
     * decibel magnitudes.
     *
     * @return the min-decibels property
     */
    public final DoubleProperty minDecibelsProperty() {
        return minDecibels;
    }

    public final double getMinDecibels() {
        return minDecibels.get();
    }

    /**
     * Sets the lower bound of the decibel window.
     *
     * @param value a strictly negative decibel value
     * @throws IllegalArgumentException if {@code value} is {@code NaN} or
     *                                  {@code >= 0} (the property is coerced
     *                                  to {@link #DEFAULT_MIN_DECIBELS})
     */
    public final void setMinDecibels(double value) {
        minDecibels.set(value);
    }

    // ==================== Band Layout ====================

    private final ObjectProperty<BandLayout> bandLayout = new StyleableObjectProperty<>(DEFAULT_BAND_LAYOUT) {
        @Override
        public Object getBean() {
            return RXAudioSpectrum.this;
        }

        @Override
        public String getName() {
            return "bandLayout";
        }

        @Override
        public CssMetaData<RXAudioSpectrum, BandLayout> getCssMetaData() {
            return StyleableProperties.BAND_LAYOUT;
        }
    };

    /**
     * How source bands are arranged across the display slots. Tolerates
     * {@code null} (the skin falls back to {@link BandLayout#LINEAR}).
     * Individual visualizations may ignore the layout if it has no meaning for
     * their shape.
     *
     * @return the band-layout property
     */
    public final ObjectProperty<BandLayout> bandLayoutProperty() {
        return bandLayout;
    }

    public final BandLayout getBandLayout() {
        return bandLayout.get();
    }

    public final void setBandLayout(BandLayout value) {
        bandLayout.set(value);
    }

    // ==================== Bar Fill ====================

    private final ObjectProperty<Paint> barFill = new StyleableObjectProperty<>(DEFAULT_BAR_FILL) {
        @Override
        public Object getBean() {
            return RXAudioSpectrum.this;
        }

        @Override
        public String getName() {
            return "barFill";
        }

        @Override
        public CssMetaData<RXAudioSpectrum, Paint> getCssMetaData() {
            return StyleableProperties.BAR_FILL;
        }
    };

    /**
     * Paint used for the spectrum bars. Proportional linear gradients are
     * remapped onto each visualization's amplitude axis, so the gradient is
     * keyed by amplitude (a low bar only reveals the low end of the gradient),
     * not by frequency index. Tolerates {@code null} as "use the default"
     * ({@link #DEFAULT_BAR_FILL}).
     *
     * @return the bar-fill property
     */
    public final ObjectProperty<Paint> barFillProperty() {
        return barFill;
    }

    public final Paint getBarFill() {
        return barFill.get();
    }

    public final void setBarFill(Paint value) {
        barFill.set(value);
    }

    // ==================== Peak Fill ====================

    private final ObjectProperty<Paint> peakFill = new StyleableObjectProperty<>(DEFAULT_PEAK_FILL) {
        @Override
        public Object getBean() {
            return RXAudioSpectrum.this;
        }

        @Override
        public String getName() {
            return "peakFill";
        }

        @Override
        public CssMetaData<RXAudioSpectrum, Paint> getCssMetaData() {
            return StyleableProperties.PEAK_FILL;
        }
    };

    /**
     * Paint used for the peak caps. Tolerates {@code null} as "use the
     * default" ({@link #DEFAULT_PEAK_FILL}).
     *
     * @return the peak-fill property
     */
    public final ObjectProperty<Paint> peakFillProperty() {
        return peakFill;
    }

    public final Paint getPeakFill() {
        return peakFill.get();
    }

    public final void setPeakFill(Paint value) {
        peakFill.set(value);
    }

    // ==================== Bar Gap Ratio ====================

    private final DoubleProperty barGapRatio = new StyleableDoubleProperty(DEFAULT_BAR_GAP_RATIO) {
        @Override
        public Object getBean() {
            return RXAudioSpectrum.this;
        }

        @Override
        public String getName() {
            return "barGapRatio";
        }

        @Override
        public CssMetaData<RXAudioSpectrum, Number> getCssMetaData() {
            return StyleableProperties.BAR_GAP_RATIO;
        }
    };

    /**
     * Fraction of each band slot left empty between adjacent bars. Clamped at
     * render time to {@code [0, }{@link #MAX_BAR_GAP_RATIO}{@code ]}.
     *
     * @return the bar-gap-ratio property
     */
    public final DoubleProperty barGapRatioProperty() {
        return barGapRatio;
    }

    public final double getBarGapRatio() {
        return barGapRatio.get();
    }

    public final void setBarGapRatio(double value) {
        barGapRatio.set(value);
    }

    // ==================== Smoothing ====================

    private final DoubleProperty smoothing = new StyleableDoubleProperty(DEFAULT_SMOOTHING) {
        @Override
        public Object getBean() {
            return RXAudioSpectrum.this;
        }

        @Override
        public String getName() {
            return "smoothing";
        }

        @Override
        public CssMetaData<RXAudioSpectrum, Number> getCssMetaData() {
            return StyleableProperties.SMOOTHING;
        }
    };

    /**
     * Release smoothing factor controlling how slowly bars fall:
     * {@code 0} releases instantly, values toward {@code 1} fall slower.
     * Clamped at render time to {@code [0, }{@link #MAX_SMOOTHING}{@code ]}.
     * The attack (rise) speed is fixed and fast.
     *
     * @return the smoothing property
     */
    public final DoubleProperty smoothingProperty() {
        return smoothing;
    }

    public final double getSmoothing() {
        return smoothing.get();
    }

    public final void setSmoothing(double value) {
        smoothing.set(value);
    }

    // ==================== Show Peaks ====================

    private final BooleanProperty showPeaks = new StyleableBooleanProperty(true) {
        @Override
        public Object getBean() {
            return RXAudioSpectrum.this;
        }

        @Override
        public String getName() {
            return "showPeaks";
        }

        @Override
        public CssMetaData<RXAudioSpectrum, Boolean> getCssMetaData() {
            return StyleableProperties.SHOW_PEAKS;
        }
    };

    /**
     * Whether hold-then-fall peak caps are drawn. Individual visualizations
     * may ignore this if peak caps have no meaning for their shape (e.g. a
     * line visualization).
     *
     * @return the show-peaks property
     */
    public final BooleanProperty showPeaksProperty() {
        return showPeaks;
    }

    public final boolean isShowPeaks() {
        return showPeaks.get();
    }

    public final void setShowPeaks(boolean value) {
        showPeaks.set(value);
    }

    // ==================== Glow ====================

    private final BooleanProperty glow = new StyleableBooleanProperty(false) {
        @Override
        public Object getBean() {
            return RXAudioSpectrum.this;
        }

        @Override
        public String getName() {
            return "glow";
        }

        @Override
        public CssMetaData<RXAudioSpectrum, Boolean> getCssMetaData() {
            return StyleableProperties.GLOW;
        }
    };

    /**
     * Whether a glow effect is composited over the rendered spectrum.
     *
     * @return the glow property
     */
    public final BooleanProperty glowProperty() {
        return glow;
    }

    public final boolean isGlow() {
        return glow.get();
    }

    public final void setGlow(boolean value) {
        glow.set(value);
    }

    // ==================== Active ====================

    private final ReadOnlyBooleanWrapper active = new ReadOnlyBooleanWrapper(this, "active", false) {
        @Override
        protected void invalidated() {
            boolean isActive = get();
            pseudoClassStateChanged(PSEUDO_CLASS_ACTIVE, isActive);
            pseudoClassStateChanged(PSEUDO_CLASS_SILENT, !isActive);
        }
    };

    /**
     * Whether the spectrum is currently animating incoming data (read-only).
     * Drives the {@code :active} / {@code :silent} pseudo-classes.
     *
     * @return the active property
     */
    public final ReadOnlyBooleanProperty activeProperty() {
        return active.getReadOnlyProperty();
    }

    public final boolean isActive() {
        return active.get();
    }

    /**
     * Sets the active state. This method is intended to be used by experts,
     * primarily by those implementing new Skins or Behaviors. It is not common
     * for developers to call this method directly.
     *
     * @param value the new active state
     */
    public final void setActive(boolean value) {
        active.set(value);
    }

    // ==================== Spectrum Data ====================

    private float[] rawMagnitudes = new float[0];

    private int magnitudeCount;

    private final ReadOnlyIntegerWrapper updateSequence =
            new ReadOnlyIntegerWrapper(this, "updateSequence", 0);

    /**
     * Feeds one frame of per-band magnitudes (non-positive dB,
     * {@code MediaPlayer} convention). Copies the array into an internal
     * reused buffer immediately and never retains the reference. Must be
     * called on the JavaFX Application Thread; callers on a worker thread must
     * wrap with {@code Platform.runLater}. An empty array is treated as one
     * frame of silence.
     *
     * @param magnitudes the magnitude frame; must not be {@code null}
     * @throws NullPointerException if {@code magnitudes} is {@code null}
     */
    public void updateSpectrum(float[] magnitudes) {
        Objects.requireNonNull(magnitudes, "magnitudes");
        if (magnitudes.length > rawMagnitudes.length) {
            rawMagnitudes = new float[magnitudes.length];
        }
        System.arraycopy(magnitudes, 0, rawMagnitudes, 0, magnitudes.length);
        magnitudeCount = magnitudes.length;
        updateSequence.set(updateSequence.get() + 1);
    }

    /**
     * Increments each time {@link #updateSpectrum(float[])} is called. This
     * property is intended to be used by experts, primarily by those
     * implementing new Skins.
     *
     * @return the update-sequence property
     */
    public final ReadOnlyIntegerProperty updateSequenceProperty() {
        return updateSequence.getReadOnlyProperty();
    }

    /**
     * Returns the length of the most recent raw magnitude frame. This method
     * is intended to be used by experts, primarily by those implementing new
     * Skins.
     *
     * @return the raw magnitude count of the latest frame
     */
    public final int getMagnitudeCount() {
        return magnitudeCount;
    }

    /**
     * Copies the most recent raw magnitude frame into {@code dest} without
     * allocating. This method is intended to be used by experts, primarily by
     * those implementing new Skins.
     *
     * @param dest the destination buffer
     * @return the number of values copied:
     *         {@code min(dest.length, getMagnitudeCount())}
     */
    public final int copyMagnitudesTo(float[] dest) {
        int count = Math.min(dest.length, magnitudeCount);
        System.arraycopy(rawMagnitudes, 0, dest, 0, count);
        return count;
    }

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXAudioSpectrum, BandLayout> BAND_LAYOUT =
                new CssMetaData<>("-rx-band-layout", new EnumConverter<>(BandLayout.class), DEFAULT_BAND_LAYOUT) {
                    @Override
                    public boolean isSettable(RXAudioSpectrum n) {
                        return !n.bandLayout.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<BandLayout> getStyleableProperty(RXAudioSpectrum n) {
                        return (StyleableProperty<BandLayout>) n.bandLayoutProperty();
                    }
                };

        private static final CssMetaData<RXAudioSpectrum, Paint> BAR_FILL =
                new CssMetaData<>("-rx-bar-fill", PaintConverter.getInstance(), DEFAULT_BAR_FILL) {
                    @Override
                    public boolean isSettable(RXAudioSpectrum n) {
                        return !n.barFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXAudioSpectrum n) {
                        return (StyleableProperty<Paint>) n.barFillProperty();
                    }
                };

        private static final CssMetaData<RXAudioSpectrum, Paint> PEAK_FILL =
                new CssMetaData<>("-rx-peak-fill", PaintConverter.getInstance(), DEFAULT_PEAK_FILL) {
                    @Override
                    public boolean isSettable(RXAudioSpectrum n) {
                        return !n.peakFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXAudioSpectrum n) {
                        return (StyleableProperty<Paint>) n.peakFillProperty();
                    }
                };

        private static final CssMetaData<RXAudioSpectrum, Number> BAR_GAP_RATIO =
                new CssMetaData<>("-rx-bar-gap-ratio", SizeConverter.getInstance(), DEFAULT_BAR_GAP_RATIO) {
                    @Override
                    public boolean isSettable(RXAudioSpectrum n) {
                        return !n.barGapRatio.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXAudioSpectrum n) {
                        return (StyleableProperty<Number>) n.barGapRatioProperty();
                    }
                };

        private static final CssMetaData<RXAudioSpectrum, Number> SMOOTHING =
                new CssMetaData<>("-rx-smoothing", SizeConverter.getInstance(), DEFAULT_SMOOTHING) {
                    @Override
                    public boolean isSettable(RXAudioSpectrum n) {
                        return !n.smoothing.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXAudioSpectrum n) {
                        return (StyleableProperty<Number>) n.smoothingProperty();
                    }
                };

        private static final CssMetaData<RXAudioSpectrum, Boolean> SHOW_PEAKS =
                new CssMetaData<>("-rx-show-peaks", BooleanConverter.getInstance(), Boolean.TRUE) {
                    @Override
                    public boolean isSettable(RXAudioSpectrum n) {
                        return !n.showPeaks.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXAudioSpectrum n) {
                        return (StyleableProperty<Boolean>) n.showPeaksProperty();
                    }
                };

        private static final CssMetaData<RXAudioSpectrum, Boolean> GLOW =
                new CssMetaData<>("-rx-glow", BooleanConverter.getInstance(), Boolean.FALSE) {
                    @Override
                    public boolean isSettable(RXAudioSpectrum n) {
                        return !n.glow.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXAudioSpectrum n) {
                        return (StyleableProperty<Boolean>) n.glowProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables,
                    BAND_LAYOUT,
                    BAR_FILL,
                    PEAK_FILL,
                    BAR_GAP_RATIO,
                    SMOOTHING,
                    SHOW_PEAKS,
                    GLOW);
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
