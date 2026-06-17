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
import javafx.css.converter.SizeConverter;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Skin;
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
 *   <li>{@code .track} — the unfilled region behind each segment</li>
 *   <li>{@code .segment-fill} — the filled region on top of each track</li>
 * </ul>
 *
 * <p>Control pseudo-classes (in addition to {@code :determinate} /
 * {@code :indeterminate} inherited from {@link ProgressIndicator}):
 * <ul>
 *   <li>{@code :completed} — set when {@code progress >= 1.0}</li>
 * </ul>
 *
 * <p>Child-region pseudo-classes:
 * <ul>
 *   <li>{@code :first} — set on the first {@code .track} and {@code .segment-fill}</li>
 *   <li>{@code :last} — set on the last {@code .track} and {@code .segment-fill}</li>
 * </ul>
 *
 * <p>In indeterminate mode a fixed-width highlight band sweeps across the row,
 * clipped by each segment as it passes. With one segment the control behaves
 * like a classic linear indeterminate progress bar. The animation auto-pauses
 * whenever the host window or any ancestor is hidden, so an off-screen bar does
 * not waste CPU.
 */
public class RXSegmentedProgressBar extends ProgressIndicator {

    private static final String DEFAULT_STYLE_CLASS = "rx-segmented-progress-bar";

    private static final PseudoClass PSEUDO_CLASS_COMPLETED = PseudoClass.getPseudoClass("completed");

    // ==================== Public Defaults ====================

    /**
     * Default number of segments.
     */
    private static final int DEFAULT_SEGMENT_COUNT = 5;

    /**
     * Minimum permitted {@link #segmentCountProperty() segmentCount} at render time.
     */
    public static final int MIN_SEGMENT_COUNT = 1;

    /**
     * Maximum permitted {@link #segmentCountProperty() segmentCount} at render time.
     * Beyond this the segments become visually indistinguishable from a normal
     * progress bar and the gap between them dominates each pill's width.
     */
    public static final int MAX_SEGMENT_COUNT = 20;

    /**
     * Default horizontal gap between adjacent segments, in pixels.
     */
    private static final double DEFAULT_SEGMENT_GAP = 4.0;

    /**
     * Default segment height (also drives {@code prefHeight}), in pixels.
     */
    private static final double DEFAULT_SEGMENT_HEIGHT = 8.0;

    /**
     * Default tween duration applied to determinate progress changes.
     */
    private static final Duration DEFAULT_PROGRESS_TRANSITION_DURATION = Duration.millis(250.0);

    /**
     * Default cycle duration for the indeterminate highlight sweep.
     */
    private static final Duration DEFAULT_INDETERMINATE_CYCLE_DURATION = Duration.millis(1600.0);

    /**
     * Default width of the indeterminate highlight band, expressed as a
     * fraction of the content width.
     */
    private static final double DEFAULT_INDETERMINATE_BAND_RATIO = 0.35;

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
     * Duration of one full pass of the indeterminate highlight band across the
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

    // ==================== Indeterminate Band Ratio ====================

    private final DoubleProperty indeterminateBandRatio =
            new StyleableDoubleProperty(DEFAULT_INDETERMINATE_BAND_RATIO) {
                @Override
                public Object getBean() {
                    return RXSegmentedProgressBar.this;
                }

                @Override
                public String getName() {
                    return "indeterminateBandRatio";
                }

                @Override
                public CssMetaData<RXSegmentedProgressBar, Number> getCssMetaData() {
                    return StyleableProperties.INDETERMINATE_BAND_RATIO;
                }
            };

    /**
     * Width of the indeterminate highlight band, expressed as a fraction of
     * the content width. Values outside {@code [0, 1]} are clamped at render
     * time; {@code 0} hides the band.
     *
     * @return the indeterminate-band-ratio property
     */
    public final DoubleProperty indeterminateBandRatioProperty() {
        return indeterminateBandRatio;
    }

    public final double getIndeterminateBandRatio() {
        return indeterminateBandRatio.get();
    }

    public final void setIndeterminateBandRatio(double value) {
        indeterminateBandRatio.set(value);
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

        private static final CssMetaData<RXSegmentedProgressBar, Number> INDETERMINATE_BAND_RATIO =
                new CssMetaData<>("-rx-indeterminate-band-ratio",
                        SizeConverter.getInstance(),
                        DEFAULT_INDETERMINATE_BAND_RATIO) {
                    @Override
                    public boolean isSettable(RXSegmentedProgressBar n) {
                        return !n.indeterminateBandRatio.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSegmentedProgressBar n) {
                        return (StyleableProperty<Number>) n.indeterminateBandRatioProperty();
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
                    PROGRESS_TRANSITION_DURATION,
                    INDETERMINATE_CYCLE_DURATION,
                    INDETERMINATE_BAND_RATIO);
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
