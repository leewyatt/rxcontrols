package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.SegmentInteractionEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXSegmentedStepIndicatorSkin;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.beans.NamedArg;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.SizeConverter;
import javafx.event.EventHandler;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Horizontal segmented step indicator. The control renders a row of equal-width
 * segments, marks all segments before {@link #selectedIndexProperty()} as
 * complete, and partially fills the selected segment using
 * {@link #segmentProgressProperty()}.
 *
 * <p>The indicator is intentionally passive: clicking a segment fires
 * {@link SegmentInteractionEvent#CLICKED}, entering a segment fires
 * {@link SegmentInteractionEvent#ENTERED}, and neither event updates
 * {@code selectedIndex}. Applications decide how segment interaction maps to
 * their own state.</p>
 */
public class RXSegmentedStepIndicator extends Control {

    private static final String DEFAULT_STYLE_CLASS = "rx-segmented-step-indicator";

    private static final PseudoClass PSEUDO_CLASS_EMPTY = PseudoClass.getPseudoClass("empty");

    // ==================== Public Defaults ====================

    /**
     * Default number of segments.
     */
    public static final int DEFAULT_STEP_COUNT = 5;

    /**
     * Maximum rendered segment count.
     */
    public static final int MAX_STEP_COUNT = 20;

    /**
     * Default horizontal gap between adjacent segments, in pixels.
     */
    public static final double DEFAULT_SEGMENT_GAP = 4.0;

    /**
     * Default segment height, in pixels.
     */
    public static final double DEFAULT_SEGMENT_HEIGHT = 8.0;

    // ==================== Constructors ====================

    /**
     * Creates a segmented step indicator with the default step count.
     */
    public RXSegmentedStepIndicator() {
        this(DEFAULT_STEP_COUNT);
    }

    /**
     * Creates a segmented step indicator with the given step count.
     *
     * @param stepCount initial step count
     */
    public RXSegmentedStepIndicator(@NamedArg("stepCount") int stepCount) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setStepCount(stepCount);
        updateEmptyPseudoClass();
    }

    /**
     * Creates the default skin.
     *
     * @return the default skin
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXSegmentedStepIndicatorSkin(this);
    }

    /**
     * Returns the user-agent stylesheet.
     *
     * @return the user-agent stylesheet URL
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    private void updateEmptyPseudoClass() {
        pseudoClassStateChanged(PSEUDO_CLASS_EMPTY, renderedStepCount() == 0);
    }

    private int renderedStepCount() {
        return RXMath.clamp(getStepCount(), 0, MAX_STEP_COUNT);
    }

    // ==================== Step Count ====================

    private final IntegerProperty stepCount = new SimpleIntegerProperty(this, "stepCount", DEFAULT_STEP_COUNT) {
        @Override
        protected void invalidated() {
            updateEmptyPseudoClass();
        }
    };

    /**
     * Number of segments. Values outside {@code [0, }{@link #MAX_STEP_COUNT}{@code ]}
     * are clamped at render time; {@code 0} renders the empty state.
     *
     * @return the step-count property
     */
    public final IntegerProperty stepCountProperty() {
        return stepCount;
    }

    /**
     * Returns the configured step count.
     *
     * @return the step count
     */
    public final int getStepCount() {
        return stepCount.get();
    }

    /**
     * Sets the step count.
     *
     * @param value the step count
     */
    public final void setStepCount(int value) {
        stepCount.set(value);
    }

    // ==================== Selected Index ====================

    private final IntegerProperty selectedIndex = new SimpleIntegerProperty(this, "selectedIndex", 0);

    /**
     * Selected segment index. When at least one segment is rendered, values
     * outside the rendered range are clamped for rendering only.
     *
     * @return the selected-index property
     */
    public final IntegerProperty selectedIndexProperty() {
        return selectedIndex;
    }

    /**
     * Returns the configured selected index.
     *
     * @return the selected index
     */
    public final int getSelectedIndex() {
        return selectedIndex.get();
    }

    /**
     * Sets the selected index.
     *
     * @param value the selected index
     */
    public final void setSelectedIndex(int value) {
        selectedIndex.set(value);
    }

    // ==================== Segment Progress ====================

    private final DoubleProperty segmentProgress = new SimpleDoubleProperty(this, "segmentProgress", 0.0);

    /**
     * Fill ratio of the selected segment. Values outside {@code [0, 1]} are
     * clamped for rendering only.
     *
     * @return the segment-progress property
     */
    public final DoubleProperty segmentProgressProperty() {
        return segmentProgress;
    }

    /**
     * Returns the configured selected-segment progress.
     *
     * @return the segment progress
     */
    public final double getSegmentProgress() {
        return segmentProgress.get();
    }

    /**
     * Sets the selected-segment progress.
     *
     * @param value the segment progress
     */
    public final void setSegmentProgress(double value) {
        segmentProgress.set(value);
    }

    // ==================== Segment Gap ====================

    private final DoubleProperty segmentGap = new StyleableDoubleProperty(DEFAULT_SEGMENT_GAP) {
        @Override
        public Object getBean() {
            return RXSegmentedStepIndicator.this;
        }

        @Override
        public String getName() {
            return "segmentGap";
        }

        @Override
        public CssMetaData<RXSegmentedStepIndicator, Number> getCssMetaData() {
            return StyleableProperties.SEGMENT_GAP;
        }
    };

    /**
     * Horizontal gap between adjacent segments, in pixels. Negative, infinite,
     * and {@code NaN} values are treated as {@code 0} at render time.
     *
     * @return the segment-gap property
     */
    public final DoubleProperty segmentGapProperty() {
        return segmentGap;
    }

    /**
     * Returns the configured segment gap.
     *
     * @return the segment gap
     */
    public final double getSegmentGap() {
        return segmentGap.get();
    }

    /**
     * Sets the segment gap.
     *
     * @param value the segment gap
     */
    public final void setSegmentGap(double value) {
        segmentGap.set(value);
    }

    // ==================== Segment Height ====================

    private final DoubleProperty segmentHeight = new StyleableDoubleProperty(DEFAULT_SEGMENT_HEIGHT) {
        @Override
        public Object getBean() {
            return RXSegmentedStepIndicator.this;
        }

        @Override
        public String getName() {
            return "segmentHeight";
        }

        @Override
        public CssMetaData<RXSegmentedStepIndicator, Number> getCssMetaData() {
            return StyleableProperties.SEGMENT_HEIGHT;
        }
    };

    /**
     * Height of each segment, in pixels. Negative, infinite, and {@code NaN}
     * values are treated as {@code 0} at render time.
     *
     * @return the segment-height property
     */
    public final DoubleProperty segmentHeightProperty() {
        return segmentHeight;
    }

    /**
     * Returns the configured segment height.
     *
     * @return the segment height
     */
    public final double getSegmentHeight() {
        return segmentHeight.get();
    }

    /**
     * Sets the segment height.
     *
     * @param value the segment height
     */
    public final void setSegmentHeight(double value) {
        segmentHeight.set(value);
    }

    // ==================== Segment Clicked ====================

    private ObjectProperty<EventHandler<SegmentInteractionEvent>> onSegmentClicked;

    /**
     * Called when a segment is clicked. This convenience property is equivalent
     * to {@code addEventHandler(SegmentInteractionEvent.CLICKED, handler)},
     * except that it allows only a single handler.
     *
     * @return the onSegmentClicked handler property
     * @see SegmentInteractionEvent#CLICKED
     */
    public final ObjectProperty<EventHandler<SegmentInteractionEvent>> onSegmentClickedProperty() {
        if (onSegmentClicked == null) {
            onSegmentClicked = new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(SegmentInteractionEvent.CLICKED, get());
                }

                @Override
                public Object getBean() {
                    return RXSegmentedStepIndicator.this;
                }

                @Override
                public String getName() {
                    return "onSegmentClicked";
                }
            };
        }
        return onSegmentClicked;
    }

    /**
     * Sets the handler for segment clicked events.
     *
     * @param handler the event handler
     */
    public final void setOnSegmentClicked(EventHandler<SegmentInteractionEvent> handler) {
        onSegmentClickedProperty().set(handler);
    }

    /**
     * Returns the handler for segment clicked events.
     *
     * @return the event handler
     */
    public final EventHandler<SegmentInteractionEvent> getOnSegmentClicked() {
        return onSegmentClicked == null ? null : onSegmentClicked.get();
    }

    // ==================== Segment Entered ====================

    private ObjectProperty<EventHandler<SegmentInteractionEvent>> onSegmentEntered;

    /**
     * Called when the pointer enters a segment. This convenience property is
     * equivalent to {@code addEventHandler(SegmentInteractionEvent.ENTERED, handler)},
     * except that it allows only a single handler.
     *
     * @return the onSegmentEntered handler property
     * @see SegmentInteractionEvent#ENTERED
     */
    public final ObjectProperty<EventHandler<SegmentInteractionEvent>> onSegmentEnteredProperty() {
        if (onSegmentEntered == null) {
            onSegmentEntered = new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(SegmentInteractionEvent.ENTERED, get());
                }

                @Override
                public Object getBean() {
                    return RXSegmentedStepIndicator.this;
                }

                @Override
                public String getName() {
                    return "onSegmentEntered";
                }
            };
        }
        return onSegmentEntered;
    }

    /**
     * Sets the handler for segment entered events.
     *
     * @param handler the event handler
     */
    public final void setOnSegmentEntered(EventHandler<SegmentInteractionEvent> handler) {
        onSegmentEnteredProperty().set(handler);
    }

    /**
     * Returns the handler for segment entered events.
     *
     * @return the event handler
     */
    public final EventHandler<SegmentInteractionEvent> getOnSegmentEntered() {
        return onSegmentEntered == null ? null : onSegmentEntered.get();
    }

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXSegmentedStepIndicator, Number> SEGMENT_GAP =
                new CssMetaData<>("-rx-segment-gap",
                        SizeConverter.getInstance(),
                        DEFAULT_SEGMENT_GAP) {
                    @Override
                    public boolean isSettable(RXSegmentedStepIndicator n) {
                        return !n.segmentGap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSegmentedStepIndicator n) {
                        return (StyleableProperty<Number>) n.segmentGapProperty();
                    }
                };

        private static final CssMetaData<RXSegmentedStepIndicator, Number> SEGMENT_HEIGHT =
                new CssMetaData<>("-rx-segment-height",
                        SizeConverter.getInstance(),
                        DEFAULT_SEGMENT_HEIGHT) {
                    @Override
                    public boolean isSettable(RXSegmentedStepIndicator n) {
                        return !n.segmentHeight.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSegmentedStepIndicator n) {
                        return (StyleableProperty<Number>) n.segmentHeightProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables,
                    SEGMENT_GAP,
                    SEGMENT_HEIGHT);
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
     * Returns the CSS metadata associated with this control.
     *
     * @return the CSS metadata
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
