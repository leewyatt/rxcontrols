package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXSegmentInteractionEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXSegmentedStepIndicatorSkin;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.beans.NamedArg;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.SizeConverter;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Horizontal segmented step indicator. The control renders a row of equal-width
 * segments, marks all segments before {@link #selectedIndexProperty()} as
 * complete, and partially fills the selected segment using
 * {@link #segmentProgressProperty()}. A {@link #titleFactoryProperty() title
 * factory} adds a title to each segment, above or below the row.
 *
 * <p>Every step counts as an equal share of the row, so the filled part is
 * {@code (selectedIndex + segmentProgress) / stepCount} of the whole, with
 * {@code selectedIndex} kept within the steps and {@code segmentProgress}
 * within {@code [0, 1]}. When that reaches the whole row, that is when the last
 * step is selected and its progress is {@code 1}, the indicator is
 * {@link #completedProperty() completed}. The control has the {@code :empty}
 * pseudo-class when it renders no segments and {@code :completed} while it is
 * completed.</p>
 *
 * <p>The indicator is intentionally passive: clicking a segment fires
 * {@link RXSegmentInteractionEvent#CLICKED}, entering a segment fires
 * {@link RXSegmentInteractionEvent#ENTERED}, and neither event updates
 * {@code selectedIndex}. Applications decide how segment interaction maps to
 * their own state.</p>
 */
public class RXSegmentedStepIndicator extends Control {

    private static final String DEFAULT_STYLE_CLASS = "rx-segmented-step-indicator";

    private static final PseudoClass PSEUDO_CLASS_EMPTY = PseudoClass.getPseudoClass("empty");

    private static final PseudoClass PSEUDO_CLASS_COMPLETED = PseudoClass.getPseudoClass("completed");

    // ==================== Public Defaults ====================

    /**
     * Default number of segments.
     */
    private static final int DEFAULT_STEP_COUNT = 5;

    /**
     * Maximum rendered segment count.
     */
    public static final int MAX_STEP_COUNT = 20;

    /**
     * Default horizontal gap between adjacent segments, in pixels.
     */
    private static final double DEFAULT_SEGMENT_GAP = 4.0;

    /**
     * Default segment height, in pixels.
     */
    private static final double DEFAULT_SEGMENT_HEIGHT = 8.0;

    /**
     * Default side of the segment titles, also used when {@link #titleSideProperty()}
     * is {@code null}.
     */
    public static final TitleSide DEFAULT_TITLE_SIDE = TitleSide.BOTTOM;

    /**
     * Default gap between the titles and the segments, in pixels.
     */
    private static final double DEFAULT_TITLE_GAP = 6.0;

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
        updateCompleted();
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

    // Compared with stepCount, not the rendered segments: with more steps than
    // MAX_STEP_COUNT, the last rendered segment is not the last step.
    private void updateCompleted() {
        int steps = getStepCount();
        setCompleted(steps > 0 && getSelectedIndex() >= steps - 1
                && RXMath.clamp0To1(getSegmentProgress()) >= 1.0);
    }

    private int renderedStepCount() {
        return RXMath.clamp(getStepCount(), 0, MAX_STEP_COUNT);
    }

    // ==================== Step Count ====================

    private final IntegerProperty stepCount = new SimpleIntegerProperty(this, "stepCount", DEFAULT_STEP_COUNT) {
        @Override
        protected void invalidated() {
            updateEmptyPseudoClass();
            updateCompleted();
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

    private final IntegerProperty selectedIndex = new SimpleIntegerProperty(this, "selectedIndex", 0) {
        @Override
        protected void invalidated() {
            updateCompleted();
        }
    };

    /**
     * Index of the current step. Segments before it are completed, its own
     * segment fills by {@link #segmentProgressProperty()}, and the segments
     * after it are upcoming. When at least one segment is rendered, values
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

    private final DoubleProperty segmentProgress = new SimpleDoubleProperty(this, "segmentProgress", 0.0) {
        @Override
        protected void invalidated() {
            updateCompleted();
        }
    };

    /**
     * Fill ratio of the selected segment. Values outside {@code [0, 1]} are
     * clamped for rendering only. Reaching {@code 1} on the last step completes
     * the indicator; see {@link #completedProperty()}.
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

    // ==================== Completed ====================

    private final ReadOnlyBooleanWrapper completed = new ReadOnlyBooleanWrapper(this, "completed") {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(PSEUDO_CLASS_COMPLETED, get());
        }
    };

    /**
     * Whether every step is completed: the last step is selected, counting an
     * index past it as the last step, and its
     * {@link #segmentProgressProperty() segmentProgress} is {@code 1} or more.
     * Read-only; to complete the indicator, select the last step and set its
     * progress to {@code 1}. While completed, every segment has the
     * {@code :completed} pseudo-class, none is {@code :selected}, and the
     * control has the {@code :completed} pseudo-class.
     *
     * @return the completed property
     */
    public final ReadOnlyBooleanProperty completedProperty() {
        return completed.getReadOnlyProperty();
    }

    /**
     * Returns whether every step is completed.
     *
     * @return {@code true} if every step is completed
     */
    public final boolean isCompleted() {
        return completed.get();
    }

    private void setCompleted(boolean value) {
        completed.set(value);
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

    // ==================== Title Factory ====================

    private final ObjectProperty<Callback<Integer, Node>> titleFactory =
            new SimpleObjectProperty<>(this, "titleFactory");

    /**
     * Factory for the segment titles, usually returning a {@code Label} with or
     * without a graphic. The default skin calls it with each rendered segment's
     * index when the skin is installed and whenever {@link #stepCountProperty()}
     * or this factory changes; selection, progress, and size changes do not call
     * it again. To change a title later, update the node the factory returned
     * or set a new factory.
     *
     * <p>Each title sits in a {@code .title} box exactly as wide as its segment,
     * at the side given by {@link #titleSideProperty()}. The box carries the
     * segment's {@code :completed}, {@code :selected}, {@code :upcoming},
     * {@code :first}, and {@code :last} pseudo-classes and clips its content to
     * the segment's width: a label keeps its preferred width when that fits and
     * is truncated with an ellipsis when it does not. Titles are measured on one
     * line. Clicking or entering a title fires the same
     * {@link RXSegmentInteractionEvent} as its segment.</p>
     *
     * <p>The factory must return a distinct node for every index, or
     * {@code null} to leave that segment without a title. The default
     * {@code null} factory shows no titles and adds no height.</p>
     *
     * @return the title-factory property
     */
    public final ObjectProperty<Callback<Integer, Node>> titleFactoryProperty() {
        return titleFactory;
    }

    /**
     * Returns the title factory.
     *
     * @return the title factory, or {@code null}
     */
    public final Callback<Integer, Node> getTitleFactory() {
        return titleFactory.get();
    }

    /**
     * Sets the title factory.
     *
     * @param value the title factory, or {@code null} for no titles
     */
    public final void setTitleFactory(Callback<Integer, Node> value) {
        titleFactory.set(value);
    }

    // ==================== Title Side ====================

    private final ObjectProperty<TitleSide> titleSide = new StyleableObjectProperty<>(DEFAULT_TITLE_SIDE) {
        @Override
        public Object getBean() {
            return RXSegmentedStepIndicator.this;
        }

        @Override
        public String getName() {
            return "titleSide";
        }

        @Override
        public CssMetaData<RXSegmentedStepIndicator, TitleSide> getCssMetaData() {
            return StyleableProperties.TITLE_SIDE;
        }
    };

    /**
     * Side of the segments on which the titles are shown, styleable through
     * {@code -rx-title-side} ({@code top} or {@code bottom}). Initial value is
     * {@link #DEFAULT_TITLE_SIDE}; {@code null} is rendered as the default.
     *
     * @return the title-side property
     */
    public final ObjectProperty<TitleSide> titleSideProperty() {
        return titleSide;
    }

    /**
     * Returns the title side.
     *
     * @return the title side, or {@code null}
     */
    public final TitleSide getTitleSide() {
        return titleSide.get();
    }

    /**
     * Sets the title side.
     *
     * @param value the title side, or {@code null} for the default
     */
    public final void setTitleSide(TitleSide value) {
        titleSide.set(value);
    }

    // ==================== Title Gap ====================

    private final DoubleProperty titleGap = new StyleableDoubleProperty(DEFAULT_TITLE_GAP) {
        @Override
        public Object getBean() {
            return RXSegmentedStepIndicator.this;
        }

        @Override
        public String getName() {
            return "titleGap";
        }

        @Override
        public CssMetaData<RXSegmentedStepIndicator, Number> getCssMetaData() {
            return StyleableProperties.TITLE_GAP;
        }
    };

    /**
     * Gap between the titles and the segments, in pixels, styleable through
     * {@code -rx-title-gap}. Negative, infinite, and {@code NaN} values are
     * treated as {@code 0} at render time. Has no effect without titles.
     *
     * @return the title-gap property
     */
    public final DoubleProperty titleGapProperty() {
        return titleGap;
    }

    /**
     * Returns the configured title gap.
     *
     * @return the title gap
     */
    public final double getTitleGap() {
        return titleGap.get();
    }

    /**
     * Sets the title gap.
     *
     * @param value the title gap
     */
    public final void setTitleGap(double value) {
        titleGap.set(value);
    }

    // ==================== Segment Clicked ====================

    private ObjectProperty<EventHandler<RXSegmentInteractionEvent>> onSegmentClicked;

    /**
     * Called when a segment is clicked. This convenience property is equivalent
     * to {@code addEventHandler(RXSegmentInteractionEvent.CLICKED, handler)},
     * except that it allows only a single handler.
     *
     * @return the onSegmentClicked handler property
     * @see RXSegmentInteractionEvent#CLICKED
     */
    public final ObjectProperty<EventHandler<RXSegmentInteractionEvent>> onSegmentClickedProperty() {
        if (onSegmentClicked == null) {
            onSegmentClicked = new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(RXSegmentInteractionEvent.CLICKED, get());
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
    public final void setOnSegmentClicked(EventHandler<RXSegmentInteractionEvent> handler) {
        onSegmentClickedProperty().set(handler);
    }

    /**
     * Returns the handler for segment clicked events.
     *
     * @return the event handler
     */
    public final EventHandler<RXSegmentInteractionEvent> getOnSegmentClicked() {
        return onSegmentClicked == null ? null : onSegmentClicked.get();
    }

    // ==================== Segment Entered ====================

    private ObjectProperty<EventHandler<RXSegmentInteractionEvent>> onSegmentEntered;

    /**
     * Called when the pointer enters a segment. This convenience property is
     * equivalent to {@code addEventHandler(RXSegmentInteractionEvent.ENTERED, handler)},
     * except that it allows only a single handler.
     *
     * @return the onSegmentEntered handler property
     * @see RXSegmentInteractionEvent#ENTERED
     */
    public final ObjectProperty<EventHandler<RXSegmentInteractionEvent>> onSegmentEnteredProperty() {
        if (onSegmentEntered == null) {
            onSegmentEntered = new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(RXSegmentInteractionEvent.ENTERED, get());
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
    public final void setOnSegmentEntered(EventHandler<RXSegmentInteractionEvent> handler) {
        onSegmentEnteredProperty().set(handler);
    }

    /**
     * Returns the handler for segment entered events.
     *
     * @return the event handler
     */
    public final EventHandler<RXSegmentInteractionEvent> getOnSegmentEntered() {
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

        private static final CssMetaData<RXSegmentedStepIndicator, TitleSide> TITLE_SIDE =
                new CssMetaData<>("-rx-title-side",
                        new EnumConverter<>(TitleSide.class),
                        DEFAULT_TITLE_SIDE) {
                    @Override
                    public boolean isSettable(RXSegmentedStepIndicator n) {
                        return !n.titleSide.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<TitleSide> getStyleableProperty(RXSegmentedStepIndicator n) {
                        return (StyleableProperty<TitleSide>) n.titleSideProperty();
                    }
                };

        private static final CssMetaData<RXSegmentedStepIndicator, Number> TITLE_GAP =
                new CssMetaData<>("-rx-title-gap",
                        SizeConverter.getInstance(),
                        DEFAULT_TITLE_GAP) {
                    @Override
                    public boolean isSettable(RXSegmentedStepIndicator n) {
                        return !n.titleGap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXSegmentedStepIndicator n) {
                        return (StyleableProperty<Number>) n.titleGapProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables,
                    SEGMENT_GAP,
                    SEGMENT_HEIGHT,
                    TITLE_SIDE,
                    TITLE_GAP);
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

    /**
     * Side of the segments on which the segment titles are shown.
     */
    public enum TitleSide {

        /**
         * Above the segments.
         */
        TOP,

        /**
         * Below the segments.
         */
        BOTTOM
    }
}
