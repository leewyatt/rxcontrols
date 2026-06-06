package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.internal.MasonryLayoutEngine;
import io.github.leewyatt.rxcontrols.internal.RXResources;

import javafx.animation.Interpolator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableIntegerProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Responsive masonry (waterfall) layout pane. Children keep their own height and
 * are arranged into equal-width columns; each child is placed into the currently
 * shortest column, producing the staggered "Pinterest" look.
 *
 * <p>The number of columns is responsive. By default it is derived from
 * {@link #columnWidthProperty() columnWidth} and the available width, so the pane
 * gains a column whenever the width grows past another {@code columnWidth + hgap}.
 * A fixed count can be forced with {@link #columnCountProperty() columnCount}, and
 * {@link #maxColumnsProperty() maxColumns} caps the resolved count. When
 * {@link #fillWidthProperty() fillWidth} is {@code true} (the default) the columns
 * stretch to consume the whole width; otherwise they keep {@code columnWidth} and
 * the content block is positioned by {@link #alignmentProperty() alignment}.</p>
 *
 * <p>This pane reports its height through {@link #computePrefHeight(double)} with a
 * {@link Orientation#HORIZONTAL} content bias, so a width-fitting parent such as a
 * {@code ScrollPane} with {@code fitToWidth=true} sizes it correctly. Children are
 * never reordered, so scene-graph, focus and accessibility order are preserved.</p>
 */
public class RXMasonryPane extends Pane {

    // ==================== Constants ====================

    /**
     * Default target column width used to derive the responsive column count.
     */
    public static final double DEFAULT_COLUMN_WIDTH = 250.0;

    /**
     * Default horizontal gap between columns.
     */
    public static final double DEFAULT_HGAP = 8.0;

    /**
     * Default vertical gap between stacked children in a column.
     */
    public static final double DEFAULT_VGAP = 8.0;

    /**
     * Default forced column count. {@code 0} means the count is computed
     * automatically from {@link #columnWidthProperty() columnWidth}.
     */
    public static final int DEFAULT_COLUMN_COUNT = 0;

    /**
     * Default number of columns reported by {@link #computePrefWidth(double)} when
     * the pane is laid out without a width constraint.
     */
    public static final int DEFAULT_PREF_COLUMNS = 3;

    /**
     * Default maximum column count. {@code 0} means unbounded.
     */
    public static final int DEFAULT_MAX_COLUMNS = 0;

    /**
     * Default fill-width behavior.
     */
    public static final boolean DEFAULT_FILL_WIDTH = true;

    /**
     * Default content alignment.
     */
    public static final Pos DEFAULT_ALIGNMENT = Pos.TOP_LEFT;

    /**
     * Default column span constraint for a child.
     */
    public static final int DEFAULT_COLUMN_SPAN = 1;

    /**
     * Default layout-animation enabled state.
     */
    public static final boolean DEFAULT_ANIMATED = true;

    /**
     * Default layout-animation duration.
     */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(220.0);

    /**
     * Default layout-animation interpolator.
     */
    public static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    private static final String DEFAULT_STYLE_CLASS = "rx-masonry-pane";
    private static final String MARGIN_CONSTRAINT = "rx-masonry-margin";
    private static final String COLUMN_SPAN_CONSTRAINT = "rx-masonry-column-span";
    private static final double ENTER_TRANSLATE_MIN = 4.0;
    private static final double ENTER_TRANSLATE_MAX = 12.0;

    // ==================== Constraints ====================

    /**
     * Sets the margin around a child. Setting {@code null} removes the
     * constraint.
     *
     * @param child the child node
     * @param value the margin, or {@code null}
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public static void setMargin(Node child, Insets value) {
        setConstraint(child, MARGIN_CONSTRAINT, value);
    }

    /**
     * Returns the margin around a child.
     *
     * @param child the child node
     * @return the margin, or {@code null} if none is set
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public static Insets getMargin(Node child) {
        return (Insets) getConstraint(child, MARGIN_CONSTRAINT);
    }

    /**
     * Sets the number of columns a child spans. Setting {@code null} removes the
     * constraint and restores the default span of {@value #DEFAULT_COLUMN_SPAN}.
     * The effective span is clamped to the current column count during layout.
     *
     * @param child the child node
     * @param value the column span, or {@code null}
     * @throws NullPointerException     if {@code child} is {@code null}
     * @throws IllegalArgumentException if {@code value} is less than one
     */
    public static void setColumnSpan(Node child, Integer value) {
        if (value != null && value < 1) {
            throw new IllegalArgumentException("columnSpan must be at least 1");
        }
        setConstraint(child, COLUMN_SPAN_CONSTRAINT, value);
    }

    /**
     * Returns the number of columns a child spans.
     *
     * @param child the child node
     * @return the column span, or {@code null} if none is set
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public static Integer getColumnSpan(Node child) {
        return (Integer) getConstraint(child, COLUMN_SPAN_CONSTRAINT);
    }

    /**
     * Removes all RXMasonryPane constraints from a child.
     *
     * @param child the child node
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public static void clearConstraints(Node child) {
        setMargin(child, null);
        setColumnSpan(child, null);
    }

    private static void setConstraint(Node child, Object key, Object value) {
        Objects.requireNonNull(child, "child cannot be null");
        if (value == null) {
            child.getProperties().remove(key);
        } else {
            child.getProperties().put(key, value);
        }
        Parent parent = child.getParent();
        if (parent != null) {
            parent.requestLayout();
        }
    }

    private static Object getConstraint(Node child, Object key) {
        Objects.requireNonNull(child, "child cannot be null");
        if (!child.hasProperties()) {
            return null;
        }
        return child.getProperties().get(key);
    }

    // ==================== Animation state ====================

    private final MasonryAnimator animator = new MasonryAnimator();
    private final Set<Node> enteringNodes = new HashSet<>();
    private final Set<Node> leavingNodes = new HashSet<>();
    private boolean firstLayoutDone;

    private final ListChangeListener<Node> childrenListener = change -> {
        while (change.next()) {
            if (change.wasAdded() && firstLayoutDone) {
                enteringNodes.addAll(change.getAddedSubList());
            }
            if (change.wasRemoved()) {
                for (Node removed : change.getRemoved()) {
                    enteringNodes.remove(removed);
                    leavingNodes.remove(removed);
                    animator.forget(removed);
                }
            }
        }
    };

    // ==================== Constructors ====================

    /**
     * Creates an empty masonry pane.
     */
    public RXMasonryPane() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        getChildren().addListener(childrenListener);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                animator.stopAll();
            }
        });
    }

    /**
     * Creates a masonry pane with the given children.
     *
     * @param children the initial children
     */
    public RXMasonryPane(Node... children) {
        this();
        getChildren().addAll(children);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Column Width ====================

    private final DoubleProperty columnWidth = new StyleableDoubleProperty(DEFAULT_COLUMN_WIDTH) {
        private double lastValid = DEFAULT_COLUMN_WIDTH;

        @Override
        protected void invalidated() {
            double value = get();
            if (!Double.isFinite(value) || value <= 0.0) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new IllegalArgumentException("columnWidth must be a finite positive number");
            }
            lastValid = value;
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.COLUMN_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXMasonryPane.this;
        }

        @Override
        public String getName() {
            return "columnWidth";
        }
    };

    /**
     * Target column width that drives the responsive column count. Must be a
     * finite positive number.
     *
     * @return the column width property
     */
    public final DoubleProperty columnWidthProperty() {
        return columnWidth;
    }

    /**
     * Returns the column width.
     *
     * @return the column width
     */
    public final double getColumnWidth() {
        return columnWidth.get();
    }

    /**
     * Sets the column width.
     *
     * @param value the column width
     * @throws IllegalArgumentException if {@code value} is not a finite positive number
     */
    public final void setColumnWidth(double value) {
        columnWidth.set(value);
    }

    // ==================== Hgap ====================

    private final DoubleProperty hgap = new StyleableDoubleProperty(DEFAULT_HGAP) {
        private double lastValid = DEFAULT_HGAP;

        @Override
        protected void invalidated() {
            double value = get();
            if (!Double.isFinite(value) || value < 0.0) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new IllegalArgumentException("hgap must be a finite non-negative number");
            }
            lastValid = value;
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.HGAP;
        }

        @Override
        public Object getBean() {
            return RXMasonryPane.this;
        }

        @Override
        public String getName() {
            return "hgap";
        }
    };

    /**
     * Horizontal gap between columns. Must be a finite non-negative number.
     *
     * @return the hgap property
     */
    public final DoubleProperty hgapProperty() {
        return hgap;
    }

    /**
     * Returns the horizontal gap.
     *
     * @return the horizontal gap
     */
    public final double getHgap() {
        return hgap.get();
    }

    /**
     * Sets the horizontal gap.
     *
     * @param value the horizontal gap
     * @throws IllegalArgumentException if {@code value} is not a finite non-negative number
     */
    public final void setHgap(double value) {
        hgap.set(value);
    }

    // ==================== Vgap ====================

    private final DoubleProperty vgap = new StyleableDoubleProperty(DEFAULT_VGAP) {
        private double lastValid = DEFAULT_VGAP;

        @Override
        protected void invalidated() {
            double value = get();
            if (!Double.isFinite(value) || value < 0.0) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new IllegalArgumentException("vgap must be a finite non-negative number");
            }
            lastValid = value;
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.VGAP;
        }

        @Override
        public Object getBean() {
            return RXMasonryPane.this;
        }

        @Override
        public String getName() {
            return "vgap";
        }
    };

    /**
     * Vertical gap between stacked children in a column. Must be a finite
     * non-negative number.
     *
     * @return the vgap property
     */
    public final DoubleProperty vgapProperty() {
        return vgap;
    }

    /**
     * Returns the vertical gap.
     *
     * @return the vertical gap
     */
    public final double getVgap() {
        return vgap.get();
    }

    /**
     * Sets the vertical gap.
     *
     * @param value the vertical gap
     * @throws IllegalArgumentException if {@code value} is not a finite non-negative number
     */
    public final void setVgap(double value) {
        vgap.set(value);
    }

    // ==================== Column Count ====================

    private final IntegerProperty columnCount = new StyleableIntegerProperty(DEFAULT_COLUMN_COUNT) {
        private int lastValid = DEFAULT_COLUMN_COUNT;

        @Override
        protected void invalidated() {
            int value = get();
            if (value < 0) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new IllegalArgumentException("columnCount cannot be negative");
            }
            lastValid = value;
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.COLUMN_COUNT;
        }

        @Override
        public Object getBean() {
            return RXMasonryPane.this;
        }

        @Override
        public String getName() {
            return "columnCount";
        }
    };

    /**
     * Forces a fixed number of columns. {@code 0} (the default) computes the count
     * automatically from {@link #columnWidthProperty() columnWidth}; a positive
     * value pins the count, still subject to {@link #maxColumnsProperty()}.
     *
     * @return the column count property
     */
    public final IntegerProperty columnCountProperty() {
        return columnCount;
    }

    /**
     * Returns the forced column count.
     *
     * @return the forced column count, or {@code 0} for automatic
     */
    public final int getColumnCount() {
        return columnCount.get();
    }

    /**
     * Sets the forced column count.
     *
     * @param value the forced column count, or {@code 0} for automatic
     * @throws IllegalArgumentException if {@code value} is negative
     */
    public final void setColumnCount(int value) {
        columnCount.set(value);
    }

    // ==================== Pref Columns ====================

    private final IntegerProperty prefColumns = new StyleableIntegerProperty(DEFAULT_PREF_COLUMNS) {
        private int lastValid = DEFAULT_PREF_COLUMNS;

        @Override
        protected void invalidated() {
            int value = get();
            if (value < 1) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new IllegalArgumentException("prefColumns must be at least 1");
            }
            lastValid = value;
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.PREF_COLUMNS;
        }

        @Override
        public Object getBean() {
            return RXMasonryPane.this;
        }

        @Override
        public String getName() {
            return "prefColumns";
        }
    };

    /**
     * Number of columns used by {@link #computePrefWidth(double)} when the pane is
     * measured without a width constraint. Does not affect the actual column count
     * used during layout. Must be at least one.
     *
     * @return the pref columns property
     */
    public final IntegerProperty prefColumnsProperty() {
        return prefColumns;
    }

    /**
     * Returns the preferred column count.
     *
     * @return the preferred column count
     */
    public final int getPrefColumns() {
        return prefColumns.get();
    }

    /**
     * Sets the preferred column count.
     *
     * @param value the preferred column count
     * @throws IllegalArgumentException if {@code value} is less than one
     */
    public final void setPrefColumns(int value) {
        prefColumns.set(value);
    }

    // ==================== Max Columns ====================

    private final IntegerProperty maxColumns = new StyleableIntegerProperty(DEFAULT_MAX_COLUMNS) {
        private int lastValid = DEFAULT_MAX_COLUMNS;

        @Override
        protected void invalidated() {
            int value = get();
            if (value < 0) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new IllegalArgumentException("maxColumns cannot be negative");
            }
            lastValid = value;
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.MAX_COLUMNS;
        }

        @Override
        public Object getBean() {
            return RXMasonryPane.this;
        }

        @Override
        public String getName() {
            return "maxColumns";
        }
    };

    /**
     * Upper bound on the resolved column count. {@code 0} (the default) means
     * unbounded.
     *
     * @return the max columns property
     */
    public final IntegerProperty maxColumnsProperty() {
        return maxColumns;
    }

    /**
     * Returns the maximum column count.
     *
     * @return the maximum column count, or {@code 0} for unbounded
     */
    public final int getMaxColumns() {
        return maxColumns.get();
    }

    /**
     * Sets the maximum column count.
     *
     * @param value the maximum column count, or {@code 0} for unbounded
     * @throws IllegalArgumentException if {@code value} is negative
     */
    public final void setMaxColumns(int value) {
        maxColumns.set(value);
    }

    // ==================== Fill Width ====================

    private final BooleanProperty fillWidth = new StyleableBooleanProperty(DEFAULT_FILL_WIDTH) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.FILL_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXMasonryPane.this;
        }

        @Override
        public String getName() {
            return "fillWidth";
        }
    };

    /**
     * Whether columns stretch to fill the available width. When {@code true} (the
     * default) the column width is {@code (contentWidth - gaps) / columns}; when
     * {@code false} columns keep {@link #columnWidthProperty() columnWidth} and the
     * content block is positioned by {@link #alignmentProperty() alignment}.
     *
     * @return the fill width property
     */
    public final BooleanProperty fillWidthProperty() {
        return fillWidth;
    }

    /**
     * Returns whether columns fill the available width.
     *
     * @return whether columns fill the available width
     */
    public final boolean isFillWidth() {
        return fillWidth.get();
    }

    /**
     * Sets whether columns fill the available width.
     *
     * @param value whether columns fill the available width
     */
    public final void setFillWidth(boolean value) {
        fillWidth.set(value);
    }

    // ==================== Alignment ====================

    private final ObjectProperty<Pos> alignment = new StyleableObjectProperty<>(DEFAULT_ALIGNMENT) {
        private Pos lastValid = DEFAULT_ALIGNMENT;

        @Override
        protected void invalidated() {
            Pos value = get();
            if (value == null) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new NullPointerException("alignment cannot be null");
            }
            lastValid = value;
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Pos> getCssMetaData() {
            return StyleableProperties.ALIGNMENT;
        }

        @Override
        public Object getBean() {
            return RXMasonryPane.this;
        }

        @Override
        public String getName() {
            return "alignment";
        }
    };

    /**
     * Alignment of the content block within the pane. Cannot be set to
     * {@code null}. The horizontal component also aligns a child within its column
     * when {@link #fillWidthProperty() fillWidth} is {@code false}; the vertical
     * component only takes effect when the pane is taller than its content.
     *
     * @return the alignment property
     */
    public final ObjectProperty<Pos> alignmentProperty() {
        return alignment;
    }

    /**
     * Returns the alignment.
     *
     * @return the alignment
     */
    public final Pos getAlignment() {
        return alignment.get();
    }

    /**
     * Sets the alignment.
     *
     * @param value the alignment
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public final void setAlignment(Pos value) {
        alignment.set(value);
    }

    private Pos alignmentOrDefault() {
        Pos value = getAlignment();
        return value == null ? DEFAULT_ALIGNMENT : value;
    }

    // ==================== Animated ====================

    private final BooleanProperty animated = new StyleableBooleanProperty(DEFAULT_ANIMATED) {
        @Override
        protected void invalidated() {
            if (!get()) {
                animator.stopAll();
            }
        }

        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.ANIMATED;
        }

        @Override
        public Object getBean() {
            return RXMasonryPane.this;
        }

        @Override
        public String getName() {
            return "animated";
        }
    };

    /**
     * Whether children animate to their new positions on relayout, fade in on
     * insertion, and fade out via {@link #removeAnimated(Node)}. Turning this off
     * while an animation is running snaps every child to its final state.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether layout animation is enabled.
     *
     * @return whether layout animation is enabled
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether layout animation is enabled.
     *
     * @param value whether layout animation is enabled
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== Animation Duration ====================

    private final ObjectProperty<Duration> animationDuration =
            new StyleableObjectProperty<>(DEFAULT_ANIMATION_DURATION) {
                private Duration lastValid = DEFAULT_ANIMATION_DURATION;

                @Override
                protected void invalidated() {
                    Duration value = get();
                    if (value == null) {
                        if (!isBound()) {
                            set(lastValid);
                        }
                        throw new NullPointerException("animationDuration cannot be null");
                    }
                    if (value.isUnknown() || value.isIndefinite() || value.lessThan(Duration.ZERO)) {
                        if (!isBound()) {
                            set(lastValid);
                        }
                        throw new IllegalArgumentException(
                                "animationDuration must be a finite non-negative duration");
                    }
                    lastValid = value;
                }

                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.ANIMATION_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXMasonryPane.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of a single relayout / insertion / removal animation. Must be a
     * finite non-negative duration; a value of {@link Duration#ZERO} disables
     * animation just like {@code animated=false}.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the animation duration.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the animation duration.
     *
     * @param value the animation duration
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is indefinite, unknown, or negative
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Animation Interpolator ====================

    private final ObjectProperty<Interpolator> animationInterpolator =
            new SimpleObjectProperty<>(this, "animationInterpolator", DEFAULT_ANIMATION_INTERPOLATOR) {
                private Interpolator lastValid = DEFAULT_ANIMATION_INTERPOLATOR;

                @Override
                protected void invalidated() {
                    Interpolator value = get();
                    if (value == null) {
                        if (!isBound()) {
                            set(lastValid);
                        }
                        throw new NullPointerException("animationInterpolator cannot be null");
                    }
                    lastValid = value;
                }
            };

    /**
     * Interpolator used for layout animations. Cannot be set to {@code null}. This
     * property is not styleable because there is no stable public CSS converter for
     * an arbitrary {@link Interpolator}.
     *
     * @return the animation interpolator property
     */
    public final ObjectProperty<Interpolator> animationInterpolatorProperty() {
        return animationInterpolator;
    }

    /**
     * Returns the animation interpolator.
     *
     * @return the animation interpolator
     */
    public final Interpolator getAnimationInterpolator() {
        return animationInterpolator.get();
    }

    /**
     * Sets the animation interpolator.
     *
     * @param value the animation interpolator
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public final void setAnimationInterpolator(Interpolator value) {
        animationInterpolator.set(value);
    }

    // ==================== Animated removal ====================

    /**
     * Removes a child with a fade-out animation, letting the surviving children
     * reflow into the freed space at the same time. If animation is disabled (or
     * the pane is not showing) the child is removed immediately.
     *
     * @param child the child to remove
     * @return {@code true} if the child was a member of this pane
     */
    public boolean removeAnimated(Node child) {
        if (child == null || !getChildren().contains(child)) {
            return false;
        }
        if (!animationsActive()) {
            getChildren().remove(child);
            return true;
        }
        leavingNodes.add(child);
        child.setManaged(false);
        requestLayout();
        animator.runExit(child, true, getAnimationDuration(), interpolatorOrDefault(),
                -enterTranslateY(), () -> finishLeaving(child));
        return true;
    }

    /**
     * Removes every child with a fade-out animation. If animation is disabled (or
     * the pane is not showing) the children are cleared immediately.
     */
    public void clearAnimated() {
        if (!animationsActive()) {
            getChildren().clear();
            return;
        }
        List<Node> snapshot = new ArrayList<>(getChildren());
        for (Node child : snapshot) {
            if (leavingNodes.add(child)) {
                child.setManaged(false);
            }
        }
        requestLayout();
        for (Node child : snapshot) {
            animator.runExit(child, true, getAnimationDuration(), interpolatorOrDefault(),
                    -enterTranslateY(), () -> finishLeaving(child));
        }
    }

    private void finishLeaving(Node child) {
        leavingNodes.remove(child);
        child.setManaged(true);
        getChildren().remove(child);
    }

    private boolean animationsActive() {
        return isAnimated() && getScene() != null && isAnimationDurationPositive();
    }

    private boolean isAnimationDurationPositive() {
        Duration value = getAnimationDuration();
        return value != null && !value.isUnknown() && !value.isIndefinite()
                && value.greaterThan(Duration.ZERO);
    }

    private Interpolator interpolatorOrDefault() {
        Interpolator value = getAnimationInterpolator();
        return value == null ? DEFAULT_ANIMATION_INTERPOLATOR : value;
    }

    private double enterTranslateY() {
        return Math.min(ENTER_TRANSLATE_MAX, Math.max(ENTER_TRANSLATE_MIN, getVgap()));
    }

    // ==================== Actual Column Count ====================

    private final ReadOnlyIntegerWrapper actualColumnCount =
            new ReadOnlyIntegerWrapper(this, "actualColumnCount", 0);

    /**
     * Number of columns resolved during the most recent layout pass. This is a
     * read-only output, updated whenever the pane lays out its children.
     *
     * @return the actual column count property
     */
    public final ReadOnlyIntegerProperty actualColumnCountProperty() {
        return actualColumnCount.getReadOnlyProperty();
    }

    /**
     * Returns the column count resolved during the most recent layout pass.
     *
     * @return the actual column count, or {@code 0} before the first layout
     */
    public final int getActualColumnCount() {
        return actualColumnCount.get();
    }

    // ==================== Layout Cache ====================

    private double cachedWidth = -1.0;
    private LayoutMetrics cachedMetrics;

    private record LayoutMetrics(int columns, double trackWidth, double usedWidth, double hgap,
                                 List<Node> managed, int[] spans, double[] blockHeights,
                                 MasonryLayoutEngine.Result result) {
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestLayout() {
        cachedWidth = -1.0;
        cachedMetrics = null;
        super.requestLayout();
    }

    @Override
    protected double computeMinWidth(double height) {
        return snappedLeftInset() + snapSizeX(getColumnWidth()) + snappedRightInset();
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected double computePrefWidth(double height) {
        int columns = getPrefColumns();
        int max = getMaxColumns();
        if (max > 0 && columns > max) {
            columns = max;
        }
        double content = columns * snapSizeX(getColumnWidth()) + (columns - 1) * snapSpaceX(getHgap());
        return snappedLeftInset() + snapSizeX(content) + snappedRightInset();
    }

    @Override
    protected double computePrefHeight(double width) {
        double contentWidth;
        if (width == -1) {
            contentWidth = computePrefWidth(-1) - snappedLeftInset() - snappedRightInset();
        } else {
            contentWidth = Math.max(0.0, width - snappedLeftInset() - snappedRightInset());
        }
        LayoutMetrics metrics = computeMetrics(contentWidth);
        return snappedTopInset() + snapSizeY(metrics.result().contentHeight()) + snappedBottomInset();
    }

    @Override
    protected void layoutChildren() {
        double left = snappedLeftInset();
        double top = snappedTopInset();
        double right = snappedRightInset();
        double bottom = snappedBottomInset();
        double contentWidth = Math.max(0.0, getWidth() - left - right);
        double contentHeight = Math.max(0.0, getHeight() - top - bottom);

        LayoutMetrics metrics = computeMetrics(contentWidth);
        if (actualColumnCount.get() != metrics.columns()) {
            actualColumnCount.set(metrics.columns());
        }

        Pos align = alignmentOrDefault();
        HPos hpos = align.getHpos();
        VPos vpos = align.getVpos();
        double xOffset = computeXOffset(contentWidth, metrics.usedWidth(), hpos);
        double yOffset = computeYOffset(contentHeight, metrics.result().contentHeight(), vpos);

        double trackWidth = metrics.trackWidth();
        double gap = metrics.hgap();
        boolean fill = isFillWidth();
        boolean animate = isAnimated() && firstLayoutDone && getScene() != null
                && isAnimationDurationPositive();
        double enterOffset = enterTranslateY();
        List<Node> managed = metrics.managed();
        int[] startColumns = metrics.result().startColumns();
        double[] tops = metrics.result().tops();
        List<MasonryAnimator.Move> moves = new ArrayList<>(managed.size());
        for (int i = 0, size = managed.size(); i < size; i++) {
            Node child = managed.get(i);
            Insets margin = getMargin(child);
            int span = metrics.spans()[i];
            double areaWidth = span * trackWidth + (span - 1) * gap;
            double areaHeight = metrics.blockHeights()[i];
            double x = left + xOffset + startColumns[i] * (trackWidth + gap);
            double y = top + yOffset + tops[i];

            // FLIP: record the current on-screen position before relocating, so the
            // animator can invert the move and tween translate back to zero.
            double oldVisualX = child.getLayoutX() + child.getTranslateX();
            double oldVisualY = child.getLayoutY() + child.getTranslateY();
            layoutInArea(child, x, y, areaWidth, areaHeight, -1.0, margin, fill, false, hpos, VPos.TOP);
            if (enteringNodes.contains(child)) {
                moves.add(new MasonryAnimator.Move(child, 0.0, enterOffset, true));
            } else {
                moves.add(new MasonryAnimator.Move(child,
                        oldVisualX - child.getLayoutX(), oldVisualY - child.getLayoutY(), false));
            }
        }
        animator.runRelayout(moves, animate, getAnimationDuration(), interpolatorOrDefault());
        enteringNodes.clear();
        firstLayoutDone = true;
    }

    private LayoutMetrics computeMetrics(double contentWidth) {
        if (cachedMetrics != null && contentWidth == cachedWidth) {
            return cachedMetrics;
        }

        int columns = computeColumns(contentWidth);
        double gap = snapSpaceX(getHgap());
        double trackWidth;
        if (isFillWidth()) {
            trackWidth = Math.max(0.0, (contentWidth - (columns - 1) * gap) / columns);
        } else {
            trackWidth = snapSizeX(getColumnWidth());
        }

        List<Node> managed = getManagedChildren();
        int count = managed.size();
        int[] spans = new int[count];
        double[] blockHeights = new double[count];
        for (int i = 0; i < count; i++) {
            Node child = managed.get(i);
            Insets margin = getMargin(child);
            int span = clampColumnSpan(childColumnSpan(child), columns);
            double areaWidth = span * trackWidth + (span - 1) * gap;
            spans[i] = span;
            blockHeights[i] = computeChildBlockHeight(child, areaWidth, margin);
        }

        MasonryLayoutEngine.Result result =
                MasonryLayoutEngine.place(columns, snapSpaceY(getVgap()), spans, blockHeights);
        double usedWidth = columns * trackWidth + (columns - 1) * gap;

        LayoutMetrics metrics = new LayoutMetrics(columns, trackWidth, usedWidth, gap,
                managed, spans, blockHeights, result);
        cachedWidth = contentWidth;
        cachedMetrics = metrics;
        return metrics;
    }

    private int computeColumns(double contentWidth) {
        int columns;
        int forced = getColumnCount();
        if (forced >= 1) {
            columns = forced;
        } else {
            double track = snapSizeX(getColumnWidth());
            double gap = snapSpaceX(getHgap());
            columns = (int) Math.floor((contentWidth + gap) / (track + gap));
        }
        if (columns < 1) {
            columns = 1;
        }
        int max = getMaxColumns();
        if (max > 0 && columns > max) {
            columns = max;
        }
        return columns;
    }

    private int childColumnSpan(Node child) {
        Integer span = getColumnSpan(child);
        return span == null ? DEFAULT_COLUMN_SPAN : span;
    }

    private int clampColumnSpan(int span, int columns) {
        if (span < 1) {
            return 1;
        }
        if (span > columns) {
            return columns;
        }
        return span;
    }

    private double computeChildBlockHeight(Node child, double areaWidth, Insets margin) {
        double marginTop = topSpace(margin);
        double marginBottom = bottomSpace(margin);
        double contentWidth = Math.max(0.0, areaWidth - leftSpace(margin) - rightSpace(margin));
        double childHeight;
        if (child.isResizable()) {
            double widthHint;
            if (isFillWidth()) {
                widthHint = contentWidth;
            } else {
                widthHint = Math.min(contentWidth, snapSizeX(child.prefWidth(-1)));
            }
            childHeight = boundedSize(child.minHeight(widthHint),
                    child.prefHeight(widthHint), child.maxHeight(widthHint));
        } else {
            childHeight = child.getLayoutBounds().getHeight();
        }
        return marginTop + snapSizeY(childHeight) + marginBottom;
    }

    private double computeXOffset(double width, double contentWidth, HPos hpos) {
        switch (hpos) {
            case CENTER:
                return Math.max(0.0, (width - contentWidth) / 2.0);
            case RIGHT:
                return Math.max(0.0, width - contentWidth);
            case LEFT:
                return 0.0;
            default:
                throw new AssertionError("Unhandled HPos: " + hpos);
        }
    }

    private double computeYOffset(double height, double contentHeight, VPos vpos) {
        switch (vpos) {
            case BASELINE:
            case TOP:
                return 0.0;
            case CENTER:
                return Math.max(0.0, (height - contentHeight) / 2.0);
            case BOTTOM:
                return Math.max(0.0, height - contentHeight);
            default:
                throw new AssertionError("Unhandled VPos: " + vpos);
        }
    }

    private double boundedSize(double min, double pref, double max) {
        double lowerBounded = Math.max(min, pref);
        double upper = Math.max(min, max);
        return Math.min(lowerBounded, upper);
    }

    private double leftSpace(Insets margin) {
        return margin == null ? 0.0 : snapSpaceX(margin.getLeft());
    }

    private double rightSpace(Insets margin) {
        return margin == null ? 0.0 : snapSpaceX(margin.getRight());
    }

    private double topSpace(Insets margin) {
        return margin == null ? 0.0 : snapSpaceY(margin.getTop());
    }

    private double bottomSpace(Insets margin) {
        return margin == null ? 0.0 : snapSpaceY(margin.getBottom());
    }

    // ==================== CSS ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXMasonryPane, Number> COLUMN_WIDTH =
                new CssMetaData<>("-rx-column-width",
                        SizeConverter.getInstance(), DEFAULT_COLUMN_WIDTH) {
                    @Override
                    public boolean isSettable(RXMasonryPane node) {
                        return !node.columnWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMasonryPane node) {
                        return (StyleableProperty<Number>) node.columnWidthProperty();
                    }
                };

        private static final CssMetaData<RXMasonryPane, Number> HGAP =
                new CssMetaData<>("-rx-hgap",
                        SizeConverter.getInstance(), DEFAULT_HGAP) {
                    @Override
                    public boolean isSettable(RXMasonryPane node) {
                        return !node.hgap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMasonryPane node) {
                        return (StyleableProperty<Number>) node.hgapProperty();
                    }
                };

        private static final CssMetaData<RXMasonryPane, Number> VGAP =
                new CssMetaData<>("-rx-vgap",
                        SizeConverter.getInstance(), DEFAULT_VGAP) {
                    @Override
                    public boolean isSettable(RXMasonryPane node) {
                        return !node.vgap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMasonryPane node) {
                        return (StyleableProperty<Number>) node.vgapProperty();
                    }
                };

        private static final CssMetaData<RXMasonryPane, Number> COLUMN_COUNT =
                new CssMetaData<>("-rx-column-count",
                        SizeConverter.getInstance(), DEFAULT_COLUMN_COUNT) {
                    @Override
                    public boolean isSettable(RXMasonryPane node) {
                        return !node.columnCount.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMasonryPane node) {
                        return (StyleableProperty<Number>) node.columnCountProperty();
                    }
                };

        private static final CssMetaData<RXMasonryPane, Number> PREF_COLUMNS =
                new CssMetaData<>("-rx-pref-columns",
                        SizeConverter.getInstance(), DEFAULT_PREF_COLUMNS) {
                    @Override
                    public boolean isSettable(RXMasonryPane node) {
                        return !node.prefColumns.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMasonryPane node) {
                        return (StyleableProperty<Number>) node.prefColumnsProperty();
                    }
                };

        private static final CssMetaData<RXMasonryPane, Number> MAX_COLUMNS =
                new CssMetaData<>("-rx-max-columns",
                        SizeConverter.getInstance(), DEFAULT_MAX_COLUMNS) {
                    @Override
                    public boolean isSettable(RXMasonryPane node) {
                        return !node.maxColumns.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMasonryPane node) {
                        return (StyleableProperty<Number>) node.maxColumnsProperty();
                    }
                };

        private static final CssMetaData<RXMasonryPane, Boolean> FILL_WIDTH =
                new CssMetaData<>("-rx-fill-width",
                        BooleanConverter.getInstance(), DEFAULT_FILL_WIDTH) {
                    @Override
                    public boolean isSettable(RXMasonryPane node) {
                        return !node.fillWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXMasonryPane node) {
                        return (StyleableProperty<Boolean>) node.fillWidthProperty();
                    }
                };

        private static final CssMetaData<RXMasonryPane, Pos> ALIGNMENT =
                new CssMetaData<>("-rx-alignment",
                        new EnumConverter<>(Pos.class), DEFAULT_ALIGNMENT) {
                    @Override
                    public boolean isSettable(RXMasonryPane node) {
                        return !node.alignment.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Pos> getStyleableProperty(RXMasonryPane node) {
                        return (StyleableProperty<Pos>) node.alignmentProperty();
                    }
                };

        private static final CssMetaData<RXMasonryPane, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated",
                        BooleanConverter.getInstance(), DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXMasonryPane node) {
                        return !node.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXMasonryPane node) {
                        return (StyleableProperty<Boolean>) node.animatedProperty();
                    }
                };

        private static final CssMetaData<RXMasonryPane, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXMasonryPane node) {
                        return !node.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXMasonryPane node) {
                        return (StyleableProperty<Duration>) node.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Pane.getClassCssMetaData());
            styleables.add(COLUMN_WIDTH);
            styleables.add(HGAP);
            styleables.add(VGAP);
            styleables.add(COLUMN_COUNT);
            styleables.add(PREF_COLUMNS);
            styleables.add(MAX_COLUMNS);
            styleables.add(FILL_WIDTH);
            styleables.add(ALIGNMENT);
            styleables.add(ANIMATED);
            styleables.add(ANIMATION_DURATION);
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
     * Returns the CSS metadata associated with this instance.
     *
     * @return the CSS metadata
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }
}
