package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.internal.MasonryColumns;
import io.github.leewyatt.rxcontrols.internal.MasonryLayoutEngine;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.utils.RXMath;

import javafx.animation.Interpolator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Responsive masonry (waterfall) layout pane. Children keep their own height and
 * are arranged into equal-width columns; each child is placed into the currently
 * shortest column, producing the staggered "Pinterest" look.
 *
 * <p>The number of columns is responsive. By default it is derived from
 * {@link #columnWidthProperty() columnWidth} and the available width, gaining a
 * column per {@code columnWidth + hgap} of extra width (the per-column step is
 * floored at a fraction of {@code columnWidth}, so a strongly negative
 * {@code hgap} overlaps columns without exploding the count).
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

    private static final String DEFAULT_STYLE_CLASS = "rx-masonry-pane";
    private static final String MARGIN_CONSTRAINT = "rx-masonry-margin";
    private static final String COLUMN_SPAN_CONSTRAINT = "rx-masonry-column-span";

    /**
     * Default target column width used to derive the responsive column count.
     */
    private static final double DEFAULT_COLUMN_WIDTH = 260.0;

    /**
     * Default horizontal gap between columns.
     */
    private static final double DEFAULT_HGAP = 8.0;

    /**
     * Default vertical gap between stacked children in a column.
     */
    private static final double DEFAULT_VGAP = 8.0;

    /**
     * Default forced column count. {@code 0} means the count is computed
     * automatically from {@link #columnWidthProperty() columnWidth}.
     */
    private static final int DEFAULT_COLUMN_COUNT = 0;

    /**
     * Default number of columns reported by {@link #computePrefWidth(double)} when
     * the pane is laid out without a width constraint.
     */
    private static final int DEFAULT_PREF_COLUMNS = 3;

    /**
     * Default maximum column count. {@code 0} means unbounded.
     */
    private static final int DEFAULT_MAX_COLUMNS = 0;

    /**
     * Default fill-width behavior.
     */
    private static final boolean DEFAULT_FILL_WIDTH = true;

    /**
     * Default content alignment.
     */
    private static final Pos DEFAULT_ALIGNMENT = Pos.TOP_LEFT;

    /**
     * Default breakpoint profile used to resolve the active breakpoint.
     */
    private static final RXBreakpointProfile DEFAULT_BREAKPOINT_PROFILE = RXBreakpointProfile.ANT_DESIGN;

    /**
     * Sentinel breakpoint column count that restores
     * {@link #columnWidthProperty() columnWidth} auto-calculation. Passing this
     * to {@link #setBreakpointColumns(RXBreakpoint, Integer)} or a typed setter sets
     * an explicit auto override that breaks the mobile-first cascade and resumes
     * auto column counting from that breakpoint up, in contrast to {@code null}
     * which simply clears the override so the breakpoint inherits.
     */
    public static final int AUTO_COLUMNS = 0;

    /**
     * Default column span constraint for a child.
     */
    private static final int DEFAULT_COLUMN_SPAN = 1;

    /**
     * Default layout-animation enabled state.
     */
    private static final boolean DEFAULT_ANIMATED = true;

    /**
     * Default layout-animation duration.
     */
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);

    /**
     * Default layout-animation interpolator.
     */
    private static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

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

    private final PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
    private final Set<Node> enteringNodes = new HashSet<>();
    // leaving node -> its managed state before the exit animation, restored on finish
    private final Map<Node, Boolean> leavingNodes = new HashMap<>();
    private boolean firstLayoutDone;

    private final ListChangeListener<Node> childrenListener = change -> {
        while (change.next()) {
            if (change.wasAdded() && firstLayoutDone) {
                enteringNodes.addAll(change.getAddedSubList());
            }
            if (change.wasRemoved()) {
                for (Node removed : change.getRemoved()) {
                    enteringNodes.remove(removed);
                    // A node removed externally mid-exit never reaches finishLeaving,
                    // so restore its original managed state here.
                    Boolean wasManaged = leavingNodes.remove(removed);
                    if (wasManaged != null) {
                        removed.setManaged(wasManaged);
                    }
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
        // Resolve the active breakpoint outside the layout pass (width/insets feed
        // the content width), mirroring RXRow rather than mutating CSS
        // pseudo-class state inside layoutChildren.
        widthProperty().addListener((obs, oldWidth, newWidth) -> updateActiveBreakpoint());
        insetsProperty().addListener((obs, oldInsets, newInsets) -> updateActiveBreakpoint());
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
        @Override
        protected void invalidated() {
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
     * Target column width that drives the responsive column count. A non-positive
     * or non-finite value is accepted but resolved to the default at layout time.
     *
     * <p>Column width is authoritative: a child wider than its resolved track
     * (for example a {@code minWidth} larger than the column) overflows into the
     * next column. Choose a {@code columnWidth} that accommodates the widest
     * child, or give that child a {@code columnSpan}.</p>
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
     */
    public final void setColumnWidth(double value) {
        columnWidth.set(value);
    }

    // ==================== Hgap ====================

    private final DoubleProperty hgap = new StyleableDoubleProperty(DEFAULT_HGAP) {
        @Override
        protected void invalidated() {
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
     * Horizontal gap between columns. May be negative to overlap columns; a
     * non-finite value resolves to the default when measured or laid out.
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
     */
    public final void setHgap(double value) {
        hgap.set(value);
    }

    // ==================== Vgap ====================

    private final DoubleProperty vgap = new StyleableDoubleProperty(DEFAULT_VGAP) {
        @Override
        protected void invalidated() {
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
     * Vertical gap between stacked children in a column. May be negative to
     * overlap items; a non-finite value resolves to the default when measured or
     * laid out.
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
     */
    public final void setVgap(double value) {
        vgap.set(value);
    }

    // ==================== Column Count ====================

    private final IntegerProperty columnCount = new StyleableIntegerProperty(DEFAULT_COLUMN_COUNT) {
        @Override
        protected void invalidated() {
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
     */
    public final void setColumnCount(int value) {
        columnCount.set(value);
    }

    // ==================== Pref Columns ====================

    private final IntegerProperty prefColumns = new StyleableIntegerProperty(DEFAULT_PREF_COLUMNS) {
        @Override
        protected void invalidated() {
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
     * used during layout, and is ignored while a positive
     * {@link #columnCountProperty() columnCount} is forced (the forced count then
     * drives the preferred width too).
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
     */
    public final void setPrefColumns(int value) {
        prefColumns.set(value);
    }

    // ==================== Max Columns ====================

    private final IntegerProperty maxColumns = new StyleableIntegerProperty(DEFAULT_MAX_COLUMNS) {
        @Override
        protected void invalidated() {
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
     * unbounded, subject only to an internal safety ceiling that guards against a
     * pathological {@code columnWidth} or {@code columnCount}.
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
        @Override
        protected void invalidated() {
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
     * Alignment of the content block within the pane. The horizontal component also
     * aligns a child within its column whenever the child cannot fill the track —
     * when {@link #fillWidthProperty() fillWidth} is {@code false}, or when the
     * child's own max width caps it below the track width; the vertical component
     * only takes effect when the pane is taller than its content. A {@code null}
     * value is not rejected; it resolves to the default at the use site.
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
     * @param value the alignment, or {@code null} to fall back to the default
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
     * insertion, and fade out via {@link #removeAnimated(Node)}. On by default;
     * turning this off while an animation is running snaps every child to its
     * final state. While enabled, the pane drives each child's {@code translateX}
     * / {@code translateY} (and {@code opacity} during fades) for the animation.
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
                @Override
                protected void invalidated() {
                    if (!isAnimationDurationPositive()) {
                        // Disabling animation mid-flight must snap, matching animated=false.
                        animator.stopAll();
                    }
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
     * Duration of a single relayout / insertion / removal animation. A {@code null},
     * non-positive, unknown, or indefinite value is not rejected; it disables
     * animation just like {@code animated=false} or {@link Duration#ZERO}.
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
     * @param value the animation duration; {@code null} or any non-positive value disables animation
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Animation Interpolator ====================

    private final ObjectProperty<Interpolator> animationInterpolator =
            new SimpleObjectProperty<>(this, "animationInterpolator", DEFAULT_ANIMATION_INTERPOLATOR);

    /**
     * Interpolator used for layout animations. Accepts {@code null}, which the
     * animator treats as {@link #DEFAULT_ANIMATION_INTERPOLATOR}. Not styleable:
     * there is no stable public CSS converter for an arbitrary {@link Interpolator}.
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
     * @param value the animation interpolator, or {@code null} for the default
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
        markLeaving(child);
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
            markLeaving(child);
        }
        requestLayout();
        for (Node child : snapshot) {
            animator.runExit(child, true, getAnimationDuration(), interpolatorOrDefault(),
                    -enterTranslateY(), () -> finishLeaving(child));
        }
    }

    private void markLeaving(Node child) {
        // Remember the original managed state and unmanage the node so survivors
        // reflow into the freed space; restored verbatim in finishLeaving.
        if (leavingNodes.putIfAbsent(child, child.isManaged()) == null) {
            child.setManaged(false);
        }
    }

    private void finishLeaving(Node child) {
        Boolean wasManaged = leavingNodes.remove(child);
        if (wasManaged != null) {
            child.setManaged(wasManaged);
        }
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

    private double columnWidthOrDefault() {
        double value = getColumnWidth();
        return Double.isFinite(value) && value > 0.0 ? value : DEFAULT_COLUMN_WIDTH;
    }

    private double sanitizedHgap() {
        double g = getHgap();
        return Double.isFinite(g) ? g : DEFAULT_HGAP;
    }

    private double sanitizedVgap() {
        double g = getVgap();
        return Double.isFinite(g) ? g : DEFAULT_VGAP;
    }

    private double enterTranslateY() {
        return RXMath.clamp(sanitizedVgap(), ENTER_TRANSLATE_MIN, ENTER_TRANSLATE_MAX);
    }

    // ==================== RXBreakpoint Profile ====================

    private final BreakpointSupport breakpointSupport = new BreakpointSupport();
    private final Map<RXBreakpoint, Integer> breakpointColumns = new EnumMap<>(RXBreakpoint.class);

    private final ObjectProperty<RXBreakpointProfile> breakpointProfile =
            new SimpleObjectProperty<>(this, "breakpointProfile", DEFAULT_BREAKPOINT_PROFILE) {
                @Override
                protected void invalidated() {
                    updateActiveBreakpoint();
                    requestLayout();
                }
            };

    /**
     * RXBreakpoint profile used to resolve the active breakpoint from the pane's
     * content width. Only the profile's breakpoint set and {@code resolve} are used;
     * its grid column count is ignored. A {@code null} value is not rejected; it
     * resolves to the default at the use site.
     *
     * @return the breakpoint profile property
     */
    public final ObjectProperty<RXBreakpointProfile> breakpointProfileProperty() {
        return breakpointProfile;
    }

    /**
     * Returns the breakpoint profile.
     *
     * @return the breakpoint profile
     */
    public final RXBreakpointProfile getBreakpointProfile() {
        return breakpointProfile.get();
    }

    /**
     * Sets the breakpoint profile.
     *
     * @param value the breakpoint profile, or {@code null} to fall back to the default
     */
    public final void setBreakpointProfile(RXBreakpointProfile value) {
        breakpointProfile.set(value);
    }

    private RXBreakpointProfile breakpointProfileOrDefault() {
        RXBreakpointProfile value = getBreakpointProfile();
        return value == null ? DEFAULT_BREAKPOINT_PROFILE : value;
    }

    // ==================== Active RXBreakpoint ====================

    private final ReadOnlyObjectWrapper<RXBreakpoint> activeBreakpoint =
            new ReadOnlyObjectWrapper<>(this, "activeBreakpoint");

    /**
     * RXBreakpoint resolved from the pane's current content width, or {@code null}
     * before the pane is given a width. Updated when the width, insets or profile
     * change. Drives the {@code :<name>} pseudo-class and can be observed for
     * breakpoint-dependent behavior.
     *
     * @return the active breakpoint property
     */
    public final ReadOnlyObjectProperty<RXBreakpoint> activeBreakpointProperty() {
        return activeBreakpoint.getReadOnlyProperty();
    }

    /**
     * Returns the active breakpoint.
     *
     * @return the active breakpoint, or {@code null} before the pane is given a width
     */
    public final RXBreakpoint getActiveBreakpoint() {
        return activeBreakpoint.get();
    }

    private void updateActiveBreakpoint() {
        double contentWidth = Math.max(0.0, getWidth() - snappedLeftInset() - snappedRightInset());
        RXBreakpoint breakpoint = breakpointSupport.update(breakpointProfileOrDefault(), contentWidth,
                this::pseudoClassStateChanged);
        if (!Objects.equals(activeBreakpoint.get(), breakpoint)) {
            activeBreakpoint.set(breakpoint);
        }
    }

    // ==================== RXBreakpoint Columns ====================

    /**
     * Sets the column count for a named breakpoint, overriding the
     * {@link #columnWidthProperty() columnWidth} auto-calculation. Overrides are
     * mobile-first: a value set at one breakpoint stays in effect for wider
     * breakpoints until another override replaces it. Passing
     * {@link #AUTO_COLUMNS} sets an explicit auto override that breaks the
     * cascade and restores columnWidth auto-calculation from that breakpoint up,
     * until a wider breakpoint sets a positive count again. Setting {@code null}
     * clears the override entirely so the breakpoint inherits.
     *
     * @param breakpoint the breakpoint tier (e.g. {@link RXBreakpoint#MD})
     * @param columns    a positive column count, {@link #AUTO_COLUMNS} for
     *                   explicit auto, or {@code null} to clear
     * @throws NullPointerException     if {@code breakpoint} is {@code null}
     * @throws IllegalArgumentException if {@code columns} is negative
     */
    public final void setBreakpointColumns(RXBreakpoint breakpoint, Integer columns) {
        Objects.requireNonNull(breakpoint, "breakpoint cannot be null");
        if (columns != null && columns < 0) {
            throw new IllegalArgumentException("columns cannot be negative");
        }
        if (columns == null) {
            breakpointColumns.remove(breakpoint);
        } else {
            breakpointColumns.put(breakpoint, columns);
        }
        requestLayout();
    }

    /**
     * Returns the column count override for a breakpoint tier.
     *
     * @param breakpoint the breakpoint tier
     * @return the column count, or {@code null} if none is set
     * @throws NullPointerException if {@code breakpoint} is {@code null}
     */
    public final Integer getBreakpointColumns(RXBreakpoint breakpoint) {
        Objects.requireNonNull(breakpoint, "breakpoint cannot be null");
        return breakpointColumns.get(breakpoint);
    }

    /**
     * Sets the column count for the {@code xs} breakpoint.
     *
     * @param columns a positive column count, {@link #AUTO_COLUMNS} for explicit
     *                auto, or {@code null} to clear the override
     * @throws IllegalArgumentException if {@code columns} is negative
     */
    public final void setXs(Integer columns) {
        setBreakpointColumns(RXBreakpoint.XS, columns);
    }

    /**
     * Returns the {@code xs} breakpoint column count.
     *
     * @return the column count, or {@code null} if none is set
     */
    public final Integer getXs() {
        return getBreakpointColumns(RXBreakpoint.XS);
    }

    /**
     * Sets the column count for the {@code sm} breakpoint.
     *
     * @param columns a positive column count, {@link #AUTO_COLUMNS} for explicit
     *                auto, or {@code null} to clear the override
     * @throws IllegalArgumentException if {@code columns} is negative
     */
    public final void setSm(Integer columns) {
        setBreakpointColumns(RXBreakpoint.SM, columns);
    }

    /**
     * Returns the {@code sm} breakpoint column count.
     *
     * @return the column count, or {@code null} if none is set
     */
    public final Integer getSm() {
        return getBreakpointColumns(RXBreakpoint.SM);
    }

    /**
     * Sets the column count for the {@code md} breakpoint.
     *
     * @param columns a positive column count, {@link #AUTO_COLUMNS} for explicit
     *                auto, or {@code null} to clear the override
     * @throws IllegalArgumentException if {@code columns} is negative
     */
    public final void setMd(Integer columns) {
        setBreakpointColumns(RXBreakpoint.MD, columns);
    }

    /**
     * Returns the {@code md} breakpoint column count.
     *
     * @return the column count, or {@code null} if none is set
     */
    public final Integer getMd() {
        return getBreakpointColumns(RXBreakpoint.MD);
    }

    /**
     * Sets the column count for the {@code lg} breakpoint.
     *
     * @param columns a positive column count, {@link #AUTO_COLUMNS} for explicit
     *                auto, or {@code null} to clear the override
     * @throws IllegalArgumentException if {@code columns} is negative
     */
    public final void setLg(Integer columns) {
        setBreakpointColumns(RXBreakpoint.LG, columns);
    }

    /**
     * Returns the {@code lg} breakpoint column count.
     *
     * @return the column count, or {@code null} if none is set
     */
    public final Integer getLg() {
        return getBreakpointColumns(RXBreakpoint.LG);
    }

    /**
     * Sets the column count for the {@code xl} breakpoint.
     *
     * @param columns a positive column count, {@link #AUTO_COLUMNS} for explicit
     *                auto, or {@code null} to clear the override
     * @throws IllegalArgumentException if {@code columns} is negative
     */
    public final void setXl(Integer columns) {
        setBreakpointColumns(RXBreakpoint.XL, columns);
    }

    /**
     * Returns the {@code xl} breakpoint column count.
     *
     * @return the column count, or {@code null} if none is set
     */
    public final Integer getXl() {
        return getBreakpointColumns(RXBreakpoint.XL);
    }

    /**
     * Sets the column count for the {@code xxl} breakpoint.
     *
     * @param columns a positive column count, {@link #AUTO_COLUMNS} for explicit
     *                auto, or {@code null} to clear the override
     * @throws IllegalArgumentException if {@code columns} is negative
     */
    public final void setXxl(Integer columns) {
        setBreakpointColumns(RXBreakpoint.XXL, columns);
    }

    /**
     * Returns the {@code xxl} breakpoint column count.
     *
     * @return the column count, or {@code null} if none is set
     */
    public final Integer getXxl() {
        return getBreakpointColumns(RXBreakpoint.XXL);
    }

    /**
     * Sets the column count for the {@code xxxl} breakpoint.
     *
     * @param columns a positive column count, {@link #AUTO_COLUMNS} for explicit
     *                auto, or {@code null} to clear the override
     * @throws IllegalArgumentException if {@code columns} is negative
     */
    public final void setXxxl(Integer columns) {
        setBreakpointColumns(RXBreakpoint.XXXL, columns);
    }

    /**
     * Returns the {@code xxxl} breakpoint column count.
     *
     * @return the column count, or {@code null} if none is set
     */
    public final Integer getXxxl() {
        return getBreakpointColumns(RXBreakpoint.XXXL);
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
        return snappedLeftInset() + snapSizeX(columnWidthOrDefault()) + snappedRightInset();
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected double computePrefWidth(double height) {
        // A forced column count wins over prefColumns, mirroring the layout-time
        // priority, so the pane never reports a pref width its own layout would
        // then split into a different number of columns.
        int forced = getColumnCount();
        int columns = forced >= 1 ? forced : Math.max(1, getPrefColumns());
        int max = getMaxColumns();
        if (max > 0 && columns > max) {
            columns = max;
        }
        columns = Math.min(columns, MasonryColumns.MAX_RESOLVED_COLUMNS);
        double content = columns * snapSizeX(columnWidthOrDefault()) + (columns - 1) * snapSpaceX(sanitizedHgap());
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
        // With animation off and nothing in flight the animator pass would be a
        // no-op; skip the move bookkeeping entirely on this hot path.
        boolean collectMoves = animate || animator.hasActiveState();
        double enterOffset = enterTranslateY();
        List<Node> managed = metrics.managed();
        int[] startColumns = metrics.result().startColumns();
        double[] tops = metrics.result().tops();
        List<PaneRelayoutAnimator.Move> moves = collectMoves ? new ArrayList<>() : null;
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
            double oldVisualX = 0.0;
            double oldVisualY = 0.0;
            if (collectMoves) {
                oldVisualX = child.getLayoutX() + child.getTranslateX();
                oldVisualY = child.getLayoutY() + child.getTranslateY();
            }
            layoutInArea(child, x, y, areaWidth, areaHeight, -1.0, margin, fill, false, hpos, VPos.TOP);
            if (!collectMoves) {
                continue;
            }
            if (enteringNodes.contains(child)) {
                moves.add(new PaneRelayoutAnimator.Move(child, 0.0, enterOffset, true));
            } else {
                double fromDx = oldVisualX - child.getLayoutX();
                double fromDy = oldVisualY - child.getLayoutY();
                // Static nodes are pre-filtered like the sibling panes, but a node
                // the animator still tracks must stay submitted — dropping it from
                // the pass would finalize its in-flight tween (fade or glide).
                if (Math.abs(fromDx) >= PaneRelayoutAnimator.MOVE_EPSILON
                        || Math.abs(fromDy) >= PaneRelayoutAnimator.MOVE_EPSILON
                        || animator.isTracked(child)) {
                    moves.add(new PaneRelayoutAnimator.Move(child, fromDx, fromDy, false));
                }
            }
        }
        if (collectMoves) {
            animator.runRelayout(moves, animate, getAnimationDuration(), interpolatorOrDefault());
        }
        enteringNodes.clear();
        firstLayoutDone = true;
    }

    private LayoutMetrics computeMetrics(double contentWidth) {
        if (cachedMetrics != null && contentWidth == cachedWidth) {
            return cachedMetrics;
        }

        int columns = computeColumns(contentWidth);
        double gap = snapSpaceX(sanitizedHgap());
        double trackWidth;
        if (isFillWidth()) {
            trackWidth = Math.max(0.0, (contentWidth - (columns - 1) * gap) / columns);
        } else {
            trackWidth = snapSizeX(columnWidthOrDefault());
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
                MasonryLayoutEngine.place(columns, snapSpaceY(sanitizedVgap()), spans, blockHeights);
        double usedWidth = columns * trackWidth + (columns - 1) * gap;

        LayoutMetrics metrics = new LayoutMetrics(columns, trackWidth, usedWidth, gap,
                managed, spans, blockHeights, result);
        cachedWidth = contentWidth;
        cachedMetrics = metrics;
        return metrics;
    }

    // A node-based pane has no scrollbar, so the breakpoint-driving width and the
    // track-driving width are the same content width.
    private int computeColumns(double contentWidth) {
        return MasonryColumns.resolve(contentWidth, contentWidth, getColumnCount(),
                snapSizeX(columnWidthOrDefault()), snapSpaceX(sanitizedHgap()),
                getMaxColumns(), isFillWidth(), breakpointProfileOrDefault(),
                breakpointColumns).columns();
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
            // Only a horizontal content bias makes height depend on width; for a
            // vertical or null bias, measure at -1 so the result matches what
            // layoutInArea will give the child (height is the independent axis).
            double heightHint = child.getContentBias() == Orientation.HORIZONTAL
                    ? boundedChildWidth(child, contentWidth)
                    : -1.0;
            childHeight = RXMath.clamp(child.prefHeight(heightHint), child.minHeight(heightHint),
                    child.maxHeight(heightHint));
        } else {
            childHeight = child.getLayoutBounds().getHeight();
        }
        if (!Double.isFinite(childHeight) || childHeight < 0.0) {
            childHeight = 0.0;
        }
        // Negative margins are valid in JavaFX, so the block (margins + height) can
        // go negative; clamp it so the engine never receives a negative extent.
        return Math.max(0.0, marginTop + snapSizeY(childHeight) + marginBottom);
    }

    private double boundedChildWidth(Node child, double contentWidth) {
        double pref = isFillWidth() ? contentWidth : Math.min(contentWidth, child.prefWidth(-1));
        return RXMath.clamp(pref, child.minWidth(-1), child.maxWidth(-1));
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
