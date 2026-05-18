package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXCircularProgressIndicatorSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.PaintConverter;
import javafx.css.converter.SizeConverter;
import javafx.scene.Node;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Circular progress indicator with a determinate sweep and a Material-style
 * indeterminate animation. Extends {@link ProgressIndicator} so it can be used
 * wherever the JavaFX-native indicator is accepted (binding to
 * {@code javafx.concurrent.Task#progressProperty()}, etc.).
 *
 * <p>Visual structure (driven by the default skin):
 * <ul>
 *   <li>{@code .track-arc} — full background ring</li>
 *   <li>{@code .progress-arc} — foreground sweep</li>
 *   <li>{@code .center-slot} — hosts {@link #graphicProperty()} or the default
 *       {@code .progress-label}</li>
 * </ul>
 *
 * <p>Pseudo-classes (in addition to {@code :determinate} / {@code :indeterminate}
 * inherited from {@link ProgressIndicator}):
 * <ul>
 *   <li>{@code :completed} — set when {@code progress >= 1.0}</li>
 * </ul>
 *
 * <p>The indeterminate animation auto-pauses whenever the host window or any
 * ancestor of the control is hidden, so an off-screen spinner does not waste
 * CPU.
 */
public class RXCircularProgressIndicator extends ProgressIndicator {

    private static final String DEFAULT_STYLE_CLASS = "rx-circular-progress-indicator";
    private static final String USER_AGENT_STYLESHEET =
            RXCircularProgressIndicator.class.getResource("/rx-controls.css").toExternalForm();
    private static final PseudoClass PSEUDO_CLASS_COMPLETED = PseudoClass.getPseudoClass("completed");

    // ==================== Public Defaults ====================

    /** Default ring start angle in degrees (12 o'clock). */
    public static final double DEFAULT_START_ANGLE = 90.0;

    /** Default sweep direction. */
    public static final boolean DEFAULT_CLOCKWISE = true;

    /** Default visibility of the auto-generated progress text. */
    public static final boolean DEFAULT_SHOW_PROGRESS_TEXT = true;

    /** Default tweening of programmatic progress changes. */
    public static final boolean DEFAULT_ANIMATED = true;

    /** Default track stroke paint (used when CSS does not resolve {@code -rx-track-stroke}). */
    public static final Paint DEFAULT_TRACK_STROKE = Color.rgb(0, 0, 0, 0.12);

    /** Default progress stroke paint (used when CSS does not resolve {@code -rx-progress-stroke}). */
    public static final Paint DEFAULT_PROGRESS_STROKE = Color.web("#616dfe");

    /** Default stroke width for the track ring, in pixels. */
    public static final double DEFAULT_TRACK_STROKE_WIDTH = 4.0;

    /** Default stroke width for the progress arc, in pixels. */
    public static final double DEFAULT_PROGRESS_STROKE_WIDTH = 4.0;

    /** Default stroke line cap. */
    public static final StrokeLineCap DEFAULT_STROKE_LINE_CAP = StrokeLineCap.ROUND;

    /** Default cycle duration for the indeterminate animation. */
    public static final Duration DEFAULT_INDETERMINATE_CYCLE_DURATION = Duration.millis(1500.0);

    /** Default tween duration applied to determinate progress changes. */
    public static final Duration DEFAULT_PROGRESS_TRANSITION_DURATION = Duration.millis(250.0);

    /** Default converter used by the skin when {@link #converterProperty()} is {@code null}. */
    public static final StringConverter<Double> DEFAULT_CONVERTER = new StringConverter<>() {
        @Override
        public String toString(Double progress) {
            if (progress == null || progress < 0.0) {
                return "";
            }
            double clamped = Math.max(0.0, Math.min(1.0, progress));
            return Math.round(clamped * 100.0) + "%";
        }

        @Override
        public Double fromString(String value) {
            return null;
        }
    };

    // ==================== Constructors ====================

    /**
     * Creates an indeterminate indicator.
     */
    public RXCircularProgressIndicator() {
        this(INDETERMINATE_PROGRESS);
    }

    /**
     * Creates an indicator with the given initial progress.
     *
     * @param progress initial progress in {@code [0,1]}, or {@code -1} for indeterminate
     */
    public RXCircularProgressIndicator(@NamedArg("progress") double progress) {
        super(progress);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        progressProperty().addListener(obs -> updateCompletedPseudoClass());
        updateCompletedPseudoClass();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXCircularProgressIndicatorSkin(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return USER_AGENT_STYLESHEET;
    }

    private void updateCompletedPseudoClass() {
        pseudoClassStateChanged(PSEUDO_CLASS_COMPLETED, getProgress() >= 1.0);
    }

    // ==================== Graphic ====================

    private final ObjectProperty<Node> graphic = new SimpleObjectProperty<>(this, "graphic");

    /**
     * Node shown in the centre of the ring. When non-null it replaces the
     * generated progress text.
     *
     * @return the graphic property
     */
    public final ObjectProperty<Node> graphicProperty() {
        return graphic;
    }

    public final Node getGraphic() {
        return graphic.get();
    }

    public final void setGraphic(Node value) {
        graphic.set(value);
    }

    // ==================== Converter ====================

    private final ObjectProperty<StringConverter<Double>> converter =
            new SimpleObjectProperty<>(this, "converter", DEFAULT_CONVERTER);

    /**
     * Converter used to render the progress as text when no
     * {@link #graphicProperty() graphic} is set. Tolerates {@code null}
     * (skin falls back to {@link #DEFAULT_CONVERTER}).
     *
     * @return the converter property
     */
    public final ObjectProperty<StringConverter<Double>> converterProperty() {
        return converter;
    }

    public final StringConverter<Double> getConverter() {
        return converter.get();
    }

    public final void setConverter(StringConverter<Double> value) {
        converter.set(value);
    }

    // ==================== Start Angle ====================

    private final DoubleProperty startAngle = new StyleableDoubleProperty(DEFAULT_START_ANGLE) {
        @Override
        public Object getBean() {
            return RXCircularProgressIndicator.this;
        }

        @Override
        public String getName() {
            return "startAngle";
        }

        @Override
        public CssMetaData<RXCircularProgressIndicator, Number> getCssMetaData() {
            return StyleableProperties.START_ANGLE;
        }
    };

    /**
     * Angle (in degrees) at which the progress arc begins; default {@code 90}
     * (12 o'clock).
     *
     * @return the start-angle property
     */
    public final DoubleProperty startAngleProperty() {
        return startAngle;
    }

    public final double getStartAngle() {
        return startAngle.get();
    }

    public final void setStartAngle(double value) {
        startAngle.set(value);
    }

    // ==================== Clockwise ====================

    private final BooleanProperty clockwise = new StyleableBooleanProperty(DEFAULT_CLOCKWISE) {
        @Override
        public Object getBean() {
            return RXCircularProgressIndicator.this;
        }

        @Override
        public String getName() {
            return "clockwise";
        }

        @Override
        public CssMetaData<RXCircularProgressIndicator, Boolean> getCssMetaData() {
            return StyleableProperties.CLOCKWISE;
        }
    };

    /**
     * If {@code true} (default), progress sweeps clockwise from the start angle.
     * The indeterminate animation respects this flag too.
     *
     * @return the clockwise property
     */
    public final BooleanProperty clockwiseProperty() {
        return clockwise;
    }

    public final boolean isClockwise() {
        return clockwise.get();
    }

    public final void setClockwise(boolean value) {
        clockwise.set(value);
    }

    // ==================== Show Progress Text ====================

    private final BooleanProperty showProgressText =
            new SimpleBooleanProperty(this, "showProgressText", DEFAULT_SHOW_PROGRESS_TEXT);

    /**
     * Whether the auto-generated progress label is shown when no
     * {@link #graphicProperty() graphic} is set.
     *
     * @return the show-progress-text property
     */
    public final BooleanProperty showProgressTextProperty() {
        return showProgressText;
    }

    public final boolean isShowProgressText() {
        return showProgressText.get();
    }

    public final void setShowProgressText(boolean value) {
        showProgressText.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated =
            new SimpleBooleanProperty(this, "animated", DEFAULT_ANIMATED);

    /**
     * If {@code true} (default), determinate progress changes are tweened over
     * {@link #progressTransitionDurationProperty()} instead of jumping. Set to
     * {@code false} for very high-frequency updates.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    public final boolean isAnimated() {
        return animated.get();
    }

    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== Progress Transition Duration ====================

    private final ObjectProperty<Duration> progressTransitionDuration =
            new SimpleObjectProperty<>(this, "progressTransitionDuration", DEFAULT_PROGRESS_TRANSITION_DURATION);

    /**
     * Duration of the progress tween when {@link #animatedProperty()} is
     * {@code true}. Tolerates {@code null} or non-positive (skin falls back to
     * {@link #DEFAULT_PROGRESS_TRANSITION_DURATION}).
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
                private Duration lastValid = DEFAULT_INDETERMINATE_CYCLE_DURATION;

                @Override
                protected void invalidated() {
                    Duration v = get();
                    if (v != null && v.lessThanOrEqualTo(Duration.ZERO)) {
                        if (!isBound()) {
                            set(lastValid);
                        }
                        throw new IllegalArgumentException(
                                "indeterminateCycleDuration must be positive");
                    }
                    if (v != null) {
                        lastValid = v;
                    }
                }

                @Override
                public Object getBean() {
                    return RXCircularProgressIndicator.this;
                }

                @Override
                public String getName() {
                    return "indeterminateCycleDuration";
                }

                @Override
                public CssMetaData<RXCircularProgressIndicator, Duration> getCssMetaData() {
                    return StyleableProperties.INDETERMINATE_CYCLE_DURATION;
                }
            };

    /**
     * Duration of one full cycle of the indeterminate animation.
     *
     * @return the cycle-duration property
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

    // ==================== Track Stroke ====================

    private final ObjectProperty<Paint> trackStroke = new StyleableObjectProperty<>(DEFAULT_TRACK_STROKE) {
        @Override
        public Object getBean() {
            return RXCircularProgressIndicator.this;
        }

        @Override
        public String getName() {
            return "trackStroke";
        }

        @Override
        public CssMetaData<RXCircularProgressIndicator, Paint> getCssMetaData() {
            return StyleableProperties.TRACK_STROKE;
        }
    };

    /**
     * Paint used for the background track ring. Tolerates {@code null} (skin
     * falls back to {@link #DEFAULT_TRACK_STROKE}).
     *
     * @return the track-stroke property
     */
    public final ObjectProperty<Paint> trackStrokeProperty() {
        return trackStroke;
    }

    public final Paint getTrackStroke() {
        return trackStroke.get();
    }

    public final void setTrackStroke(Paint value) {
        trackStroke.set(value);
    }

    // ==================== Progress Stroke ====================

    private final ObjectProperty<Paint> progressStroke = new StyleableObjectProperty<>(DEFAULT_PROGRESS_STROKE) {
        @Override
        public Object getBean() {
            return RXCircularProgressIndicator.this;
        }

        @Override
        public String getName() {
            return "progressStroke";
        }

        @Override
        public CssMetaData<RXCircularProgressIndicator, Paint> getCssMetaData() {
            return StyleableProperties.PROGRESS_STROKE;
        }
    };

    /**
     * Paint used for the progress arc. Tolerates {@code null} (skin falls back
     * to {@link #DEFAULT_PROGRESS_STROKE}).
     *
     * @return the progress-stroke property
     */
    public final ObjectProperty<Paint> progressStrokeProperty() {
        return progressStroke;
    }

    public final Paint getProgressStroke() {
        return progressStroke.get();
    }

    public final void setProgressStroke(Paint value) {
        progressStroke.set(value);
    }

    // ==================== Track Stroke Width ====================

    private final DoubleProperty trackStrokeWidth = new StyleableDoubleProperty(DEFAULT_TRACK_STROKE_WIDTH) {
        private double lastValid = DEFAULT_TRACK_STROKE_WIDTH;

        @Override
        protected void invalidated() {
            double v = get();
            if (v < 0.0 || Double.isNaN(v)) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new IllegalArgumentException("trackStrokeWidth must be non-negative");
            }
            lastValid = v;
        }

        @Override
        public Object getBean() {
            return RXCircularProgressIndicator.this;
        }

        @Override
        public String getName() {
            return "trackStrokeWidth";
        }

        @Override
        public CssMetaData<RXCircularProgressIndicator, Number> getCssMetaData() {
            return StyleableProperties.TRACK_STROKE_WIDTH;
        }
    };

    /**
     * Stroke width of the track ring; must be non-negative.
     *
     * @return the track-stroke-width property
     */
    public final DoubleProperty trackStrokeWidthProperty() {
        return trackStrokeWidth;
    }

    public final double getTrackStrokeWidth() {
        return trackStrokeWidth.get();
    }

    public final void setTrackStrokeWidth(double value) {
        trackStrokeWidth.set(value);
    }

    // ==================== Progress Stroke Width ====================

    private final DoubleProperty progressStrokeWidth = new StyleableDoubleProperty(DEFAULT_PROGRESS_STROKE_WIDTH) {
        private double lastValid = DEFAULT_PROGRESS_STROKE_WIDTH;

        @Override
        protected void invalidated() {
            double v = get();
            if (v < 0.0 || Double.isNaN(v)) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new IllegalArgumentException("progressStrokeWidth must be non-negative");
            }
            lastValid = v;
        }

        @Override
        public Object getBean() {
            return RXCircularProgressIndicator.this;
        }

        @Override
        public String getName() {
            return "progressStrokeWidth";
        }

        @Override
        public CssMetaData<RXCircularProgressIndicator, Number> getCssMetaData() {
            return StyleableProperties.PROGRESS_STROKE_WIDTH;
        }
    };

    /**
     * Stroke width of the progress arc; must be non-negative.
     *
     * @return the progress-stroke-width property
     */
    public final DoubleProperty progressStrokeWidthProperty() {
        return progressStrokeWidth;
    }

    public final double getProgressStrokeWidth() {
        return progressStrokeWidth.get();
    }

    public final void setProgressStrokeWidth(double value) {
        progressStrokeWidth.set(value);
    }

    // ==================== Stroke Line Cap ====================

    private final ObjectProperty<StrokeLineCap> strokeLineCap =
            new StyleableObjectProperty<>(DEFAULT_STROKE_LINE_CAP) {
                @Override
                public Object getBean() {
                    return RXCircularProgressIndicator.this;
                }

                @Override
                public String getName() {
                    return "strokeLineCap";
                }

                @Override
                public CssMetaData<RXCircularProgressIndicator, StrokeLineCap> getCssMetaData() {
                    return StyleableProperties.STROKE_LINE_CAP;
                }
            };

    /**
     * Stroke line cap shared by both rings. Tolerates {@code null} (skin falls
     * back to {@link #DEFAULT_STROKE_LINE_CAP}).
     *
     * @return the stroke-line-cap property
     */
    public final ObjectProperty<StrokeLineCap> strokeLineCapProperty() {
        return strokeLineCap;
    }

    public final StrokeLineCap getStrokeLineCap() {
        return strokeLineCap.get();
    }

    public final void setStrokeLineCap(StrokeLineCap value) {
        strokeLineCap.set(value);
    }

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXCircularProgressIndicator, Number> START_ANGLE =
                new CssMetaData<>("-rx-start-angle", SizeConverter.getInstance(), DEFAULT_START_ANGLE) {
                    @Override
                    public boolean isSettable(RXCircularProgressIndicator n) {
                        return !n.startAngle.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXCircularProgressIndicator n) {
                        return (StyleableProperty<Number>) n.startAngleProperty();
                    }
                };

        private static final CssMetaData<RXCircularProgressIndicator, Boolean> CLOCKWISE =
                new CssMetaData<>("-rx-clockwise", BooleanConverter.getInstance(), DEFAULT_CLOCKWISE) {
                    @Override
                    public boolean isSettable(RXCircularProgressIndicator n) {
                        return !n.clockwise.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXCircularProgressIndicator n) {
                        return (StyleableProperty<Boolean>) n.clockwiseProperty();
                    }
                };

        private static final CssMetaData<RXCircularProgressIndicator, Duration> INDETERMINATE_CYCLE_DURATION =
                new CssMetaData<>("-rx-indeterminate-cycle-duration",
                        DurationConverter.getInstance(),
                        DEFAULT_INDETERMINATE_CYCLE_DURATION) {
                    @Override
                    public boolean isSettable(RXCircularProgressIndicator n) {
                        return !n.indeterminateCycleDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXCircularProgressIndicator n) {
                        return (StyleableProperty<Duration>) n.indeterminateCycleDurationProperty();
                    }
                };

        private static final CssMetaData<RXCircularProgressIndicator, Paint> TRACK_STROKE =
                new CssMetaData<>("-rx-track-stroke", PaintConverter.getInstance(), DEFAULT_TRACK_STROKE) {
                    @Override
                    public boolean isSettable(RXCircularProgressIndicator n) {
                        return !n.trackStroke.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXCircularProgressIndicator n) {
                        return (StyleableProperty<Paint>) n.trackStrokeProperty();
                    }
                };

        private static final CssMetaData<RXCircularProgressIndicator, Paint> PROGRESS_STROKE =
                new CssMetaData<>("-rx-progress-stroke", PaintConverter.getInstance(), DEFAULT_PROGRESS_STROKE) {
                    @Override
                    public boolean isSettable(RXCircularProgressIndicator n) {
                        return !n.progressStroke.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXCircularProgressIndicator n) {
                        return (StyleableProperty<Paint>) n.progressStrokeProperty();
                    }
                };

        private static final CssMetaData<RXCircularProgressIndicator, Number> TRACK_STROKE_WIDTH =
                new CssMetaData<>("-rx-track-stroke-width", SizeConverter.getInstance(), DEFAULT_TRACK_STROKE_WIDTH) {
                    @Override
                    public boolean isSettable(RXCircularProgressIndicator n) {
                        return !n.trackStrokeWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXCircularProgressIndicator n) {
                        return (StyleableProperty<Number>) n.trackStrokeWidthProperty();
                    }
                };

        private static final CssMetaData<RXCircularProgressIndicator, Number> PROGRESS_STROKE_WIDTH =
                new CssMetaData<>("-rx-progress-stroke-width", SizeConverter.getInstance(), DEFAULT_PROGRESS_STROKE_WIDTH) {
                    @Override
                    public boolean isSettable(RXCircularProgressIndicator n) {
                        return !n.progressStrokeWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXCircularProgressIndicator n) {
                        return (StyleableProperty<Number>) n.progressStrokeWidthProperty();
                    }
                };

        private static final CssMetaData<RXCircularProgressIndicator, StrokeLineCap> STROKE_LINE_CAP =
                new CssMetaData<>("-rx-stroke-line-cap",
                        new EnumConverter<>(StrokeLineCap.class),
                        DEFAULT_STROKE_LINE_CAP) {
                    @Override
                    public boolean isSettable(RXCircularProgressIndicator n) {
                        return !n.strokeLineCap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<StrokeLineCap> getStyleableProperty(RXCircularProgressIndicator n) {
                        return (StyleableProperty<StrokeLineCap>) n.strokeLineCapProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(ProgressIndicator.getClassCssMetaData());
            Collections.addAll(styleables,
                    START_ANGLE,
                    CLOCKWISE,
                    INDETERMINATE_CYCLE_DURATION,
                    TRACK_STROKE,
                    PROGRESS_STROKE,
                    TRACK_STROKE_WIDTH,
                    PROGRESS_STROKE_WIDTH,
                    STROKE_LINE_CAP);
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
