package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXSegmentedProgressBarSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableIntegerProperty;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Horizontal segmented progress bar. The row is split into
 * {@link #segmentCountProperty() segmentCount} equal-width pills; as
 * {@link #getProgress() progress} grows from {@code 0} to {@code 1} the
 * segments fill from left to right, and the boundary segment fills partially.
 * Common use cases include Stories-style progress, step / chapter completion,
 * battery-level meters, and any multi-stage activity that reads better as
 * discrete bars than as a single sweep.
 *
 * <p>Extends {@link ProgressIndicator} so the bar can be bound to
 * {@code javafx.concurrent.Task#progressProperty()} interchangeably with the
 * JavaFX-native progress controls.
 *
 * <p>Visual structure (driven by the default skin):
 * <ul>
 *   <li>{@code .track} — the unfilled pill behind each segment</li>
 *   <li>{@code .segment-fill} — the filled pill on top of each track</li>
 * </ul>
 *
 * <p>Pseudo-classes (in addition to {@code :determinate} / {@code :indeterminate}
 * inherited from {@link ProgressIndicator}):
 * <ul>
 *   <li>{@code :completed} — set when {@code progress >= 1.0}</li>
 * </ul>
 *
 * <p>In indeterminate mode a single highlight wave sweeps across the row: each
 * segment briefly fills and then drains as the wave passes, giving the bar a
 * direction-aware "still working" appearance distinct from the deterministic
 * left-to-right fill. The animation auto-pauses whenever the host window or
 * any ancestor is hidden, so an off-screen bar does not waste CPU.
 */
public class RXSegmentedProgressBar extends ProgressIndicator {

    private static final String DEFAULT_STYLE_CLASS = "rx-segmented-progress-bar";

    private static final PseudoClass PSEUDO_CLASS_COMPLETED = PseudoClass.getPseudoClass("completed");

    // ==================== Public Defaults ====================

    /**
     * Default number of segments.
     */
    public static final int DEFAULT_SEGMENT_COUNT = 5;

    /**
     * Minimum permitted {@link #segmentCountProperty() segmentCount} at render time.
     */
    public static final int MIN_SEGMENT_COUNT = 2;

    /**
     * Maximum permitted {@link #segmentCountProperty() segmentCount} at render time.
     * Beyond this the segments become visually indistinguishable from a normal
     * progress bar and the gap between them dominates each pill's width.
     */
    public static final int MAX_SEGMENT_COUNT = 20;

    /**
     * Default horizontal gap between adjacent segments, in pixels.
     */
    public static final double DEFAULT_SEGMENT_GAP = 4.0;

    /**
     * Default segment height (also drives {@code prefHeight}), in pixels.
     */
    public static final double DEFAULT_SEGMENT_HEIGHT = 8.0;

    /**
     * Default corner radius of each segment, in pixels. Equal to half the
     * default height so segments render as pills by default.
     */
    public static final double DEFAULT_SEGMENT_ARC = 4.0;

    /**
     * Default fill paint for the filled portion of each segment. Matches the
     * rest of the RX progress-indicator family so the bar reads as part of the
     * same control set.
     */
    public static final Paint DEFAULT_FILLED_COLOR = Color.web("#616dfe");

    /**
     * Default fill paint for the unfilled portion of each segment.
     */
    public static final Paint DEFAULT_UNFILLED_COLOR = Color.rgb(0, 0, 0, 0.12);

    /**
     * Default tween duration applied to determinate progress changes.
     */
    public static final Duration DEFAULT_PROGRESS_TRANSITION_DURATION = Duration.millis(250.0);

    /**
     * Default cycle duration for the indeterminate highlight sweep.
     */
    public static final Duration DEFAULT_INDETERMINATE_CYCLE_DURATION = Duration.millis(1600.0);

    // ==================== Constructors ====================

    /**
     * Creates an indeterminate bar.
     */
    public RXSegmentedProgressBar() {
        this(INDETERMINATE_PROGRESS);
    }

    /**
     * Creates a bar with the given initial progress.
     *
     * @param progress initial progress in {@code [0,1]}, or {@code -1} for indeterminate
     */
    public RXSegmentedProgressBar(@NamedArg("progress") double progress) {
        super(progress);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        progressProperty().addListener(obs -> updateCompletedPseudoClass());
        updateCompletedPseudoClass();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXSegmentedProgressBarSkin(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    private void updateCompletedPseudoClass() {
        pseudoClassStateChanged(PSEUDO_CLASS_COMPLETED, getProgress() >= 1.0);
    }

    // ==================== Segment Count ====================

    private final IntegerProperty segmentCount = new StyleableIntegerProperty(DEFAULT_SEGMENT_COUNT) {
        @Override
        public Object getBean() {
            return RXSegmentedProgressBar.this;
        }

        @Override
        public String getName() {
            return "segmentCount";
        }

        @Override
        public CssMetaData<RXSegmentedProgressBar, Number> getCssMetaData() {
            return StyleableProperties.SEGMENT_COUNT;
        }
    };

    /**
     * Number of segments in the row. Values outside
     * {@code [}{@link #MIN_SEGMENT_COUNT}{@code , }{@link #MAX_SEGMENT_COUNT}{@code ]}
     * are clamped at render time.
     *
     * @return the segment-count property
     */
    public final IntegerProperty segmentCountProperty() {
        return segmentCount;
    }

    public final int getSegmentCount() {
        return segmentCount.get();
    }

    public final void setSegmentCount(int value) {
        segmentCount.set(value);
    }

    // ==================== Segment Gap ====================

    private final DoubleProperty segmentGap = new StyleableDoubleProperty(DEFAULT_SEGMENT_GAP) {
        @Override
        public Object getBean() {
            return RXSegmentedProgressBar.this;
        }

        @Override
        public String getName() {
            return "segmentGap";
        }

        @Override
        public CssMetaData<RXSegmentedProgressBar, Number> getCssMetaData() {
            return StyleableProperties.SEGMENT_GAP;
        }
    };

    /**
     * Horizontal gap between adjacent segments, in pixels. Negative values and
     * {@code NaN} are treated as {@code 0} at render time.
     *
     * @return the segment-gap property
     */
    public final DoubleProperty segmentGapProperty() {
        return segmentGap;
    }

    public final double getSegmentGap() {
        return segmentGap.get();
    }

    public final void setSegmentGap(double value) {
        segmentGap.set(value);
    }

    // ==================== Segment Height ====================

    private final DoubleProperty segmentHeight = new StyleableDoubleProperty(DEFAULT_SEGMENT_HEIGHT) {
        @Override
        public Object getBean() {
            return RXSegmentedProgressBar.this;
        }

        @Override
        public String getName() {
            return "segmentHeight";
        }

        @Override
        public CssMetaData<RXSegmentedProgressBar, Number> getCssMetaData() {
            return StyleableProperties.SEGMENT_HEIGHT;
        }
    };

    /**
     * Height of each segment, in pixels. Drives the bar's {@code prefHeight}.
     * Negative values and {@code NaN} are treated as {@code 0} at render time.
     *
     * @return the segment-height property
     */
    public final DoubleProperty segmentHeightProperty() {
        return segmentHeight;
    }

    public final double getSegmentHeight() {
        return segmentHeight.get();
    }

    public final void setSegmentHeight(double value) {
        segmentHeight.set(value);
    }

    // ==================== Segment Arc ====================

    private final DoubleProperty segmentArc = new StyleableDoubleProperty(DEFAULT_SEGMENT_ARC) {
        @Override
        public Object getBean() {
            return RXSegmentedProgressBar.this;
        }

        @Override
        public String getName() {
            return "segmentArc";
        }

        @Override
        public CssMetaData<RXSegmentedProgressBar, Number> getCssMetaData() {
            return StyleableProperties.SEGMENT_ARC;
        }
    };

    /**
     * Corner radius of each segment, in pixels. {@code 0} yields plain
     * rectangles; values {@code >= segmentHeight / 2} round the ends into
     * pills. Negative values and {@code NaN} are treated as {@code 0} at
     * render time.
     *
     * @return the segment-arc property
     */
    public final DoubleProperty segmentArcProperty() {
        return segmentArc;
    }

    public final double getSegmentArc() {
        return segmentArc.get();
    }

    public final void setSegmentArc(double value) {
        segmentArc.set(value);
    }

    // ==================== Filled Color ====================

    private final ObjectProperty<Paint> filledColor = new StyleableObjectProperty<>(DEFAULT_FILLED_COLOR) {
        @Override
        public Object getBean() {
            return RXSegmentedProgressBar.this;
        }

        @Override
        public String getName() {
            return "filledColor";
        }

        @Override
        public CssMetaData<RXSegmentedProgressBar, Paint> getCssMetaData() {
            return StyleableProperties.FILLED_COLOR;
        }
    };

    /**
     * Paint used for the filled portion of each segment. Tolerates {@code null}
     * (skin falls back to {@link #DEFAULT_FILLED_COLOR}).
     *
     * @return the filled-color property
     */
    public final ObjectProperty<Paint> filledColorProperty() {
        return filledColor;
    }

    public final Paint getFilledColor() {
        return filledColor.get();
    }

    public final void setFilledColor(Paint value) {
        filledColor.set(value);
    }

    // ==================== Unfilled Color ====================

    private final ObjectProperty<Paint> unfilledColor = new StyleableObjectProperty<>(DEFAULT_UNFILLED_COLOR) {
        @Override
        public Object getBean() {
            return RXSegmentedProgressBar.this;
        }

        @Override
        public String getName() {
            return "unfilledColor";
        }

        @Override
        public CssMetaData<RXSegmentedProgressBar, Paint> getCssMetaData() {
            return StyleableProperties.UNFILLED_COLOR;
        }
    };

    /**
     * Paint used for the unfilled portion of each segment. Tolerates {@code null}
     * (skin falls back to {@link #DEFAULT_UNFILLED_COLOR}).
     *
     * @return the unfilled-color property
     */
    public final ObjectProperty<Paint> unfilledColorProperty() {
        return unfilledColor;
    }

    public final Paint getUnfilledColor() {
        return unfilledColor.get();
    }

    public final void setUnfilledColor(Paint value) {
        unfilledColor.set(value);
    }

    // ==================== Progress Transition Duration ====================

    private final ObjectProperty<Duration> progressTransitionDuration =
            new StyleableObjectProperty<>(DEFAULT_PROGRESS_TRANSITION_DURATION) {
                @Override
                public Object getBean() {
                    return RXSegmentedProgressBar.this;
                }

                @Override
                public String getName() {
                    return "progressTransitionDuration";
                }

                @Override
                public CssMetaData<RXSegmentedProgressBar, Duration> getCssMetaData() {
                    return StyleableProperties.PROGRESS_TRANSITION_DURATION;
                }
            };

    /**
     * Duration of the tween applied to determinate progress changes. A value
     * of {@code null} or any {@code Duration} less than or equal to
     * {@link Duration#ZERO} disables tweening — the fill jumps directly to
     * the new progress.
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
                    return RXSegmentedProgressBar.this;
                }

                @Override
                public String getName() {
                    return "indeterminateCycleDuration";
                }

                @Override
                public CssMetaData<RXSegmentedProgressBar, Duration> getCssMetaData() {
                    return StyleableProperties.INDETERMINATE_CYCLE_DURATION;
                }
            };

    /**
     * Duration of one full pass of the indeterminate highlight wave across the
     * row. A value of {@code null} or any {@code Duration} less than or equal
     * to {@link Duration#ZERO} suppresses the indeterminate animation — the
     * row collapses to its empty pose (all segments unfilled).
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

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXSegmentedProgressBar, Number> SEGMENT_COUNT =
                new CssMetaData<>("-rx-segment-count",
                        SizeConverter.getInstance(),
                        DEFAULT_SEGMENT_COUNT) {
                    @Override
                    public boolean isSettable(RXSegmentedProgressBar n) {
                        return !n.segmentCount.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSegmentedProgressBar n) {
                        return (StyleableProperty<Number>) n.segmentCountProperty();
                    }
                };

        private static final CssMetaData<RXSegmentedProgressBar, Number> SEGMENT_GAP =
                new CssMetaData<>("-rx-segment-gap",
                        SizeConverter.getInstance(),
                        DEFAULT_SEGMENT_GAP) {
                    @Override
                    public boolean isSettable(RXSegmentedProgressBar n) {
                        return !n.segmentGap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSegmentedProgressBar n) {
                        return (StyleableProperty<Number>) n.segmentGapProperty();
                    }
                };

        private static final CssMetaData<RXSegmentedProgressBar, Number> SEGMENT_HEIGHT =
                new CssMetaData<>("-rx-segment-height",
                        SizeConverter.getInstance(),
                        DEFAULT_SEGMENT_HEIGHT) {
                    @Override
                    public boolean isSettable(RXSegmentedProgressBar n) {
                        return !n.segmentHeight.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSegmentedProgressBar n) {
                        return (StyleableProperty<Number>) n.segmentHeightProperty();
                    }
                };

        private static final CssMetaData<RXSegmentedProgressBar, Number> SEGMENT_ARC =
                new CssMetaData<>("-rx-segment-arc",
                        SizeConverter.getInstance(),
                        DEFAULT_SEGMENT_ARC) {
                    @Override
                    public boolean isSettable(RXSegmentedProgressBar n) {
                        return !n.segmentArc.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSegmentedProgressBar n) {
                        return (StyleableProperty<Number>) n.segmentArcProperty();
                    }
                };

        private static final CssMetaData<RXSegmentedProgressBar, Paint> FILLED_COLOR =
                new CssMetaData<>("-rx-filled-color",
                        PaintConverter.getInstance(),
                        DEFAULT_FILLED_COLOR) {
                    @Override
                    public boolean isSettable(RXSegmentedProgressBar n) {
                        return !n.filledColor.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXSegmentedProgressBar n) {
                        return (StyleableProperty<Paint>) n.filledColorProperty();
                    }
                };

        private static final CssMetaData<RXSegmentedProgressBar, Paint> UNFILLED_COLOR =
                new CssMetaData<>("-rx-unfilled-color",
                        PaintConverter.getInstance(),
                        DEFAULT_UNFILLED_COLOR) {
                    @Override
                    public boolean isSettable(RXSegmentedProgressBar n) {
                        return !n.unfilledColor.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXSegmentedProgressBar n) {
                        return (StyleableProperty<Paint>) n.unfilledColorProperty();
                    }
                };

        private static final CssMetaData<RXSegmentedProgressBar, Duration> PROGRESS_TRANSITION_DURATION =
                new CssMetaData<>("-rx-progress-transition-duration",
                        DurationConverter.getInstance(),
                        DEFAULT_PROGRESS_TRANSITION_DURATION) {
                    @Override
                    public boolean isSettable(RXSegmentedProgressBar n) {
                        return !n.progressTransitionDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXSegmentedProgressBar n) {
                        return (StyleableProperty<Duration>) n.progressTransitionDurationProperty();
                    }
                };

        private static final CssMetaData<RXSegmentedProgressBar, Duration> INDETERMINATE_CYCLE_DURATION =
                new CssMetaData<>("-rx-indeterminate-cycle-duration",
                        DurationConverter.getInstance(),
                        DEFAULT_INDETERMINATE_CYCLE_DURATION) {
                    @Override
                    public boolean isSettable(RXSegmentedProgressBar n) {
                        return !n.indeterminateCycleDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXSegmentedProgressBar n) {
                        return (StyleableProperty<Duration>) n.indeterminateCycleDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(ProgressIndicator.getClassCssMetaData());
            Collections.addAll(styleables,
                    SEGMENT_COUNT,
                    SEGMENT_GAP,
                    SEGMENT_HEIGHT,
                    SEGMENT_ARC,
                    FILLED_COLOR,
                    UNFILLED_COLOR,
                    PROGRESS_TRANSITION_DURATION,
                    INDETERMINATE_CYCLE_DURATION);
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
