package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.internal.RXResources;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
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
import javafx.scene.layout.Priority;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One-dimensional layout pane that can switch between horizontal and vertical
 * orientation without reparenting its children.
 *
 * <p>When {@link #orientationProperty() orientation} is
 * {@link Orientation#HORIZONTAL}, the pane behaves like an {@code HBox}-style
 * row. When it is {@link Orientation#VERTICAL}, the pane behaves like a
 * {@code VBox}-style column. Children are added directly to
 * {@link #getChildren()} and stay in that single list when the orientation
 * changes.</p>
 *
 * <p>Only managed children take part in layout; invisible but managed children
 * still consume space, matching JavaFX pane semantics. Baseline alignment is
 * not special-cased in this first implementation; {@link VPos#BASELINE} is
 * treated as {@link VPos#CENTER}.</p>
 */
public class RXBox extends Pane {

    // ==================== Constants ====================

    /**
     * Default orientation.
     */
    public static final Orientation DEFAULT_ORIENTATION = Orientation.HORIZONTAL;

    /**
     * Default child spacing.
     */
    public static final double DEFAULT_SPACING = 0.0;

    /**
     * Default child alignment.
     */
    public static final Pos DEFAULT_ALIGNMENT = Pos.TOP_LEFT;

    /**
     * Default cross-axis fill behavior.
     */
    public static final boolean DEFAULT_FILL_CROSS_AXIS = true;

    private static final String DEFAULT_STYLE_CLASS = "rx-box";
    private static final String GROW_CONSTRAINT = "rxbox-grow";
    private static final String MARGIN_CONSTRAINT = "rxbox-margin";
    private static final double EPSILON = 1.0e-6;

    // ==================== Constraints ====================

    /**
     * Sets the main-axis grow priority for a child.
     *
     * <p>In horizontal orientation this is equivalent to an HBox horizontal
     * grow constraint. In vertical orientation it is equivalent to a VBox
     * vertical grow constraint. Setting {@code null} removes the constraint.</p>
     *
     * @param child the child node
     * @param value the grow priority, or {@code null}
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public static void setGrow(Node child, Priority value) {
        setConstraint(child, GROW_CONSTRAINT, value);
    }

    /**
     * Returns the main-axis grow priority for a child.
     *
     * @param child the child node
     * @return the grow priority, or {@code null} if none is set
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public static Priority getGrow(Node child) {
        return (Priority) getConstraint(child, GROW_CONSTRAINT);
    }

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
     * Removes all RXBox constraints from a child.
     *
     * @param child the child node
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public static void clearConstraints(Node child) {
        setGrow(child, null);
        setMargin(child, null);
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

    // ==================== Constructors ====================

    /**
     * Creates an empty horizontal RXBox.
     */
    public RXBox() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a horizontal RXBox with the given children.
     *
     * @param children the initial children
     */
    public RXBox(Node... children) {
        this();
        getChildren().addAll(children);
    }

    /**
     * Creates an RXBox with the given orientation.
     *
     * @param orientation the orientation
     * @throws NullPointerException if {@code orientation} is {@code null}
     */
    public RXBox(Orientation orientation) {
        this();
        setOrientation(orientation);
    }

    /**
     * Creates an RXBox with the given orientation and children.
     *
     * @param orientation the orientation
     * @param children the initial children
     * @throws NullPointerException if {@code orientation} is {@code null}
     */
    public RXBox(Orientation orientation, Node... children) {
        this(orientation);
        getChildren().addAll(children);
    }

    /**
     * Creates a horizontal RXBox with the given spacing and children.
     *
     * @param spacing the child spacing
     * @param children the initial children
     * @throws IllegalArgumentException if {@code spacing} is not finite
     */
    public RXBox(double spacing, Node... children) {
        this(children);
        setSpacing(spacing);
    }

    /**
     * Creates an RXBox with the given orientation, spacing and children.
     *
     * @param orientation the orientation
     * @param spacing the child spacing
     * @param children the initial children
     * @throws NullPointerException if {@code orientation} is {@code null}
     * @throws IllegalArgumentException if {@code spacing} is not finite
     */
    public RXBox(Orientation orientation, double spacing, Node... children) {
        this(orientation, children);
        setSpacing(spacing);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Orientation ====================

    private final ObjectProperty<Orientation> orientation =
            new StyleableObjectProperty<>(DEFAULT_ORIENTATION) {
                private Orientation lastValid = DEFAULT_ORIENTATION;

                @Override
                protected void invalidated() {
                    Orientation value = get();
                    if (value == null) {
                        if (!isBound()) {
                            set(lastValid);
                        }
                        throw new NullPointerException("orientation cannot be null");
                    }
                    lastValid = value;
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, Orientation> getCssMetaData() {
                    return StyleableProperties.ORIENTATION;
                }

                @Override
                public Object getBean() {
                    return RXBox.this;
                }

                @Override
                public String getName() {
                    return "orientation";
                }
            };

    /**
     * Orientation of the main layout axis. Cannot be set to {@code null}.
     *
     * @return the orientation property
     */
    public final ObjectProperty<Orientation> orientationProperty() {
        return orientation;
    }

    /**
     * Returns the orientation.
     *
     * @return the orientation
     */
    public final Orientation getOrientation() {
        return orientation.get();
    }

    /**
     * Sets the orientation.
     *
     * @param value the orientation
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public final void setOrientation(Orientation value) {
        orientation.set(value);
    }

    // ==================== Spacing ====================

    private final DoubleProperty spacing = new StyleableDoubleProperty(DEFAULT_SPACING) {
        private double lastValid = DEFAULT_SPACING;

        @Override
        protected void invalidated() {
            double value = get();
            if (!Double.isFinite(value)) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new IllegalArgumentException("spacing must be finite");
            }
            lastValid = value;
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.SPACING;
        }

        @Override
        public Object getBean() {
            return RXBox.this;
        }

        @Override
        public String getName() {
            return "spacing";
        }
    };

    /**
     * Amount of space between adjacent managed children on the main axis.
     *
     * @return the spacing property
     */
    public final DoubleProperty spacingProperty() {
        return spacing;
    }

    /**
     * Returns the spacing.
     *
     * @return the spacing
     */
    public final double getSpacing() {
        return spacing.get();
    }

    /**
     * Sets the spacing.
     *
     * @param value the spacing
     * @throws IllegalArgumentException if {@code value} is not finite
     */
    public final void setSpacing(double value) {
        spacing.set(value);
    }

    // ==================== Alignment ====================

    private final ObjectProperty<Pos> alignment =
            new StyleableObjectProperty<>(DEFAULT_ALIGNMENT) {
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
                    return RXBox.this;
                }

                @Override
                public String getName() {
                    return "alignment";
                }
            };

    /**
     * Overall alignment of children within this pane. Cannot be set to
     * {@code null}. {@link VPos#BASELINE} is treated as {@link VPos#CENTER}.
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

    // ==================== Fill Cross Axis ====================

    private final BooleanProperty fillCrossAxis =
            new StyleableBooleanProperty(DEFAULT_FILL_CROSS_AXIS) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.FILL_CROSS_AXIS;
                }

                @Override
                public Object getBean() {
                    return RXBox.this;
                }

                @Override
                public String getName() {
                    return "fillCrossAxis";
                }
            };

    /**
     * Whether resizable children fill the cross axis. In horizontal
     * orientation this maps to HBox fill-height behavior; in vertical
     * orientation it maps to VBox fill-width behavior.
     *
     * @return the fill cross axis property
     */
    public final BooleanProperty fillCrossAxisProperty() {
        return fillCrossAxis;
    }

    /**
     * Returns whether children fill the cross axis.
     *
     * @return whether children fill the cross axis
     */
    public final boolean isFillCrossAxis() {
        return fillCrossAxis.get();
    }

    /**
     * Sets whether children fill the cross axis.
     *
     * @param value whether children fill the cross axis
     */
    public final void setFillCrossAxis(boolean value) {
        fillCrossAxis.set(value);
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public Orientation getContentBias() {
        if (biasDirty) {
            bias = null;
            for (Node child : getManagedChildren()) {
                Orientation childBias = child.getContentBias();
                if (childBias == Orientation.HORIZONTAL) {
                    bias = Orientation.HORIZONTAL;
                    break;
                }
                if (childBias == Orientation.VERTICAL) {
                    bias = Orientation.VERTICAL;
                }
            }
            biasDirty = false;
        }
        return bias;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestLayout() {
        biasDirty = true;
        bias = null;
        super.requestLayout();
    }

    @Override
    protected double computeMinWidth(double height) {
        return computeWidth(height, true);
    }

    @Override
    protected double computeMinHeight(double width) {
        return computeHeight(width, true);
    }

    @Override
    protected double computePrefWidth(double height) {
        return computeWidth(height, false);
    }

    @Override
    protected double computePrefHeight(double width) {
        return computeHeight(width, false);
    }

    @Override
    protected void layoutChildren() {
        List<Node> managed = getManagedChildren();
        double top = snappedTopInset();
        double right = snappedRightInset();
        double bottom = snappedBottomInset();
        double left = snappedLeftInset();
        double width = getWidth();
        double height = getHeight();
        double contentWidth = Math.max(0.0, width - left - right);
        double contentHeight = Math.max(0.0, height - top - bottom);
        Pos align = alignmentOrDefault();
        HPos hpos = align.getHpos();
        VPos vpos = effectiveVPos(align.getVpos());

        if (isHorizontal()) {
            double[] areaWidths = computeAreaWidths(managed, height, false);
            double contentMain = adjustAreaSizes(managed, areaWidths, contentWidth, contentHeight, true);
            double x = left + computeXOffset(contentWidth, contentMain, hpos);
            double space = snapSpaceX(getSpacing());
            for (int i = 0, size = managed.size(); i < size; i++) {
                Node child = managed.get(i);
                Insets margin = getMargin(child);
                layoutInArea(child, x, top, areaWidths[i], contentHeight,
                        -1, margin, true, isFillCrossAxis(), hpos, vpos);
                x += areaWidths[i] + space;
            }
        } else {
            double[] areaHeights = computeAreaHeights(managed, width, false);
            double contentMain = adjustAreaSizes(managed, areaHeights, contentHeight, contentWidth, false);
            double y = top + computeYOffset(contentHeight, contentMain, vpos);
            double space = snapSpaceY(getSpacing());
            for (int i = 0, size = managed.size(); i < size; i++) {
                Node child = managed.get(i);
                Insets margin = getMargin(child);
                layoutInArea(child, left, y, contentWidth, areaHeights[i],
                        -1, margin, isFillCrossAxis(), true, hpos, vpos);
                y += areaHeights[i] + space;
            }
        }
    }

    private boolean biasDirty = true;
    private Orientation bias;

    private boolean isHorizontal() {
        return getOrientation() == Orientation.HORIZONTAL;
    }

    private double computeWidth(double height, boolean minimum) {
        List<Node> managed = getManagedChildren();
        double left = snappedLeftInset();
        double right = snappedRightInset();
        double contentWidth;
        if (isHorizontal()) {
            contentWidth = sum(computeAreaWidths(managed, height, minimum))
                    + spacingTotal(managed.size(), true);
        } else {
            if (height != -1 && getContentBias() != null) {
                double[] areaHeights = computeAreaHeights(managed, -1, false);
                double availableHeight = Math.max(0.0,
                        height - snappedTopInset() - snappedBottomInset());
                adjustAreaSizes(managed, areaHeights, availableHeight, -1, false);
                contentWidth = computeMaxAreaWidth(managed, areaHeights, minimum);
            } else {
                contentWidth = computeMaxAreaWidth(managed, null, minimum);
            }
        }
        return left + contentWidth + right;
    }

    private double computeHeight(double width, boolean minimum) {
        List<Node> managed = getManagedChildren();
        double top = snappedTopInset();
        double bottom = snappedBottomInset();
        double contentHeight;
        if (isHorizontal()) {
            if (width != -1 && getContentBias() != null) {
                double[] areaWidths = computeAreaWidths(managed, -1, false);
                double availableWidth = Math.max(0.0,
                        width - snappedLeftInset() - snappedRightInset());
                adjustAreaSizes(managed, areaWidths, availableWidth, -1, true);
                contentHeight = computeMaxAreaHeight(managed, areaWidths, minimum);
            } else {
                contentHeight = computeMaxAreaHeight(managed, null, minimum);
            }
        } else {
            contentHeight = sum(computeAreaHeights(managed, width, minimum))
                    + spacingTotal(managed.size(), false);
        }
        return top + contentHeight + bottom;
    }

    private double[] computeAreaWidths(List<Node> managed, double height, boolean minimum) {
        double availableHeight = height == -1 ? -1 :
                Math.max(0.0, height - snappedTopInset() - snappedBottomInset());
        double[] widths = new double[managed.size()];
        for (int i = 0, size = managed.size(); i < size; i++) {
            Node child = managed.get(i);
            Insets margin = getMargin(child);
            widths[i] = minimum
                    ? computeChildMinAreaWidth(child, margin, availableHeight, isFillCrossAxis())
                    : computeChildPrefAreaWidth(child, margin, availableHeight, isFillCrossAxis());
        }
        return widths;
    }

    private double[] computeAreaHeights(List<Node> managed, double width, boolean minimum) {
        double availableWidth = width == -1 ? -1 :
                Math.max(0.0, width - snappedLeftInset() - snappedRightInset());
        double[] heights = new double[managed.size()];
        for (int i = 0, size = managed.size(); i < size; i++) {
            Node child = managed.get(i);
            Insets margin = getMargin(child);
            heights[i] = minimum
                    ? computeChildMinAreaHeight(child, margin, availableWidth, isFillCrossAxis())
                    : computeChildPrefAreaHeight(child, margin, availableWidth, isFillCrossAxis());
        }
        return heights;
    }

    private double adjustAreaSizes(List<Node> managed, double[] sizes, double availableMain,
                                   double availableCross, boolean horizontal) {
        double contentMain = sum(sizes) + spacingTotal(managed.size(), horizontal);
        double extra = availableMain - contentMain;
        if (Math.abs(extra) < EPSILON) {
            return contentMain;
        }
        double remaining = distribute(managed, sizes, extra, availableCross,
                horizontal, Priority.ALWAYS);
        remaining = distribute(managed, sizes, remaining, availableCross,
                horizontal, Priority.SOMETIMES);
        contentMain += extra - remaining;
        return contentMain;
    }

    private double distribute(List<Node> managed, double[] sizes, double extra,
                              double availableCross, boolean horizontal,
                              Priority priority) {
        if (Math.abs(extra) < EPSILON || managed.isEmpty()) {
            return extra;
        }

        double[] limits = new double[managed.size()];
        int adjustable = 0;
        boolean shrinking = extra < 0.0;
        for (int i = 0, size = managed.size(); i < size; i++) {
            Node child = managed.get(i);
            if (shrinking || getGrow(child) == priority) {
                limits[i] = shrinking
                        ? computeChildMinMainArea(child, availableCross, horizontal)
                        : computeChildMaxMainArea(child, availableCross, horizontal);
                if ((shrinking && limits[i] < sizes[i] - EPSILON)
                        || (!shrinking && limits[i] > sizes[i] + EPSILON)) {
                    adjustable++;
                } else {
                    limits[i] = Double.NaN;
                }
            } else {
                limits[i] = Double.NaN;
            }
        }

        double available = extra;
        while (Math.abs(available) >= EPSILON && adjustable > 0) {
            double portion = available / adjustable;
            boolean changed = false;
            for (int i = 0, size = managed.size(); i < size; i++) {
                if (Double.isNaN(limits[i])) {
                    continue;
                }
                double limit = limits[i] - sizes[i];
                double change = Math.abs(limit) <= Math.abs(portion) ? limit : portion;
                if (Math.abs(change) < EPSILON) {
                    limits[i] = Double.NaN;
                    adjustable--;
                    continue;
                }
                sizes[i] += change;
                available -= change;
                changed = true;
                if (Math.abs(limit) <= Math.abs(portion) + EPSILON) {
                    limits[i] = Double.NaN;
                    adjustable--;
                }
                if (Math.abs(available) < EPSILON) {
                    break;
                }
            }
            if (!changed) {
                break;
            }
        }
        return available;
    }

    private double computeChildMinMainArea(Node child, double availableCross,
                                           boolean horizontal) {
        Insets margin = getMargin(child);
        return horizontal
                ? computeChildMinAreaWidth(child, margin, availableCross, isFillCrossAxis())
                : computeChildMinAreaHeight(child, margin, availableCross, isFillCrossAxis());
    }

    private double computeChildMaxMainArea(Node child, double availableCross,
                                           boolean horizontal) {
        Insets margin = getMargin(child);
        return horizontal
                ? computeChildMaxAreaWidth(child, margin, availableCross, isFillCrossAxis())
                : computeChildMaxAreaHeight(child, margin, availableCross, isFillCrossAxis());
    }

    private double computeMaxAreaWidth(List<Node> managed, double[] childHeights,
                                       boolean minimum) {
        double max = 0.0;
        for (int i = 0, size = managed.size(); i < size; i++) {
            Node child = managed.get(i);
            Insets margin = getMargin(child);
            double childHeight = childHeights == null ? -1 : childHeights[i];
            double areaWidth = minimum
                    ? computeChildMinAreaWidth(child, margin, childHeight, false)
                    : computeChildPrefAreaWidth(child, margin, childHeight, false);
            max = Math.max(max, areaWidth);
        }
        return max;
    }

    private double computeMaxAreaHeight(List<Node> managed, double[] childWidths,
                                        boolean minimum) {
        double max = 0.0;
        for (int i = 0, size = managed.size(); i < size; i++) {
            Node child = managed.get(i);
            Insets margin = getMargin(child);
            double childWidth = childWidths == null ? -1 : childWidths[i];
            double areaHeight = minimum
                    ? computeChildMinAreaHeight(child, margin, childWidth, false)
                    : computeChildPrefAreaHeight(child, margin, childWidth, false);
            max = Math.max(max, areaHeight);
        }
        return max;
    }

    private double computeChildMinAreaWidth(Node child, Insets margin,
                                            double availableHeight,
                                            boolean fillHeight) {
        double left = left(margin);
        double right = right(margin);
        double alt = heightAlt(child, margin, availableHeight, fillHeight);
        return left + snapSizeX(child.minWidth(alt)) + right;
    }

    private double computeChildPrefAreaWidth(Node child, Insets margin,
                                             double availableHeight,
                                             boolean fillHeight) {
        double left = left(margin);
        double right = right(margin);
        double alt = heightAlt(child, margin, availableHeight, fillHeight);
        return left + snapSizeX(boundedSize(child.minWidth(alt),
                child.prefWidth(alt), child.maxWidth(alt))) + right;
    }

    private double computeChildMaxAreaWidth(Node child, Insets margin,
                                            double availableHeight,
                                            boolean fillHeight) {
        double alt = heightAlt(child, margin, availableHeight, fillHeight);
        double max = child.maxWidth(alt);
        if (max == Double.MAX_VALUE) {
            return max;
        }
        double left = left(margin);
        double right = right(margin);
        return left + snapSizeX(boundedSize(child.minWidth(alt), max,
                Double.MAX_VALUE)) + right;
    }

    private double computeChildMinAreaHeight(Node child, Insets margin,
                                             double availableWidth,
                                             boolean fillWidth) {
        double top = top(margin);
        double bottom = bottom(margin);
        double alt = widthAlt(child, margin, availableWidth, fillWidth);
        return top + snapSizeY(child.minHeight(alt)) + bottom;
    }

    private double computeChildPrefAreaHeight(Node child, Insets margin,
                                              double availableWidth,
                                              boolean fillWidth) {
        double top = top(margin);
        double bottom = bottom(margin);
        double alt = widthAlt(child, margin, availableWidth, fillWidth);
        return top + snapSizeY(boundedSize(child.minHeight(alt),
                child.prefHeight(alt), child.maxHeight(alt))) + bottom;
    }

    private double computeChildMaxAreaHeight(Node child, Insets margin,
                                             double availableWidth,
                                             boolean fillWidth) {
        double alt = widthAlt(child, margin, availableWidth, fillWidth);
        double max = child.maxHeight(alt);
        if (max == Double.MAX_VALUE) {
            return max;
        }
        double top = top(margin);
        double bottom = bottom(margin);
        return top + snapSizeY(boundedSize(child.minHeight(alt), max,
                Double.MAX_VALUE)) + bottom;
    }

    private double widthAlt(Node child, Insets margin, double availableWidth,
                            boolean fillWidth) {
        if (availableWidth == -1 || !child.isResizable()
                || child.getContentBias() != Orientation.HORIZONTAL) {
            return -1;
        }
        double contentWidth = Math.max(0.0, availableWidth - left(margin) - right(margin));
        return computeBoundedWidth(child, fillWidth, contentWidth);
    }

    private double heightAlt(Node child, Insets margin, double availableHeight,
                             boolean fillHeight) {
        if (availableHeight == -1 || !child.isResizable()
                || child.getContentBias() != Orientation.VERTICAL) {
            return -1;
        }
        double contentHeight = Math.max(0.0, availableHeight - top(margin) - bottom(margin));
        return computeBoundedHeight(child, fillHeight, contentHeight);
    }

    private double computeBoundedWidth(Node child, boolean fill, double contentWidth) {
        double min = child.minWidth(-1);
        double pref = fill ? contentWidth : Math.min(contentWidth, child.prefWidth(-1));
        return snapSizeX(boundedSize(min, pref, child.maxWidth(-1)));
    }

    private double computeBoundedHeight(Node child, boolean fill, double contentHeight) {
        double min = child.minHeight(-1);
        double pref = fill ? contentHeight : Math.min(contentHeight, child.prefHeight(-1));
        return snapSizeY(boundedSize(min, pref, child.maxHeight(-1)));
    }

    private double spacingTotal(int childCount, boolean horizontal) {
        if (childCount <= 1) {
            return 0.0;
        }
        double spacingSize = horizontal ? snapSpaceX(getSpacing()) : snapSpaceY(getSpacing());
        return (childCount - 1) * spacingSize;
    }

    private double sum(double[] values) {
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total;
    }

    private double left(Insets margin) {
        return margin == null ? 0.0 : snapSpaceX(margin.getLeft());
    }

    private double right(Insets margin) {
        return margin == null ? 0.0 : snapSpaceX(margin.getRight());
    }

    private double top(Insets margin) {
        return margin == null ? 0.0 : snapSpaceY(margin.getTop());
    }

    private double bottom(Insets margin) {
        return margin == null ? 0.0 : snapSpaceY(margin.getBottom());
    }

    private double boundedSize(double min, double pref, double max) {
        double lowerBounded = Math.max(min, pref);
        double upper = Math.max(min, max);
        return Math.min(lowerBounded, upper);
    }

    private double computeXOffset(double width, double contentWidth, HPos hpos) {
        if (hpos == HPos.CENTER) {
            return (width - contentWidth) / 2.0;
        }
        if (hpos == HPos.RIGHT) {
            return width - contentWidth;
        }
        return 0.0;
    }

    private double computeYOffset(double height, double contentHeight, VPos vpos) {
        if (vpos == VPos.CENTER || vpos == VPos.BASELINE) {
            return (height - contentHeight) / 2.0;
        }
        if (vpos == VPos.BOTTOM) {
            return height - contentHeight;
        }
        return 0.0;
    }

    private VPos effectiveVPos(VPos vpos) {
        return vpos == VPos.BASELINE ? VPos.CENTER : vpos;
    }

    // ==================== CSS ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXBox, Orientation> ORIENTATION =
                new CssMetaData<>("-rx-orientation",
                        new EnumConverter<>(Orientation.class), DEFAULT_ORIENTATION) {
                    @Override
                    public Orientation getInitialValue(RXBox node) {
                        return node.getOrientation();
                    }

                    @Override
                    public boolean isSettable(RXBox node) {
                        return !node.orientation.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Orientation> getStyleableProperty(RXBox node) {
                        return (StyleableProperty<Orientation>) node.orientationProperty();
                    }
                };

        private static final CssMetaData<RXBox, Number> SPACING =
                new CssMetaData<>("-rx-spacing",
                        SizeConverter.getInstance(), DEFAULT_SPACING) {
                    @Override
                    public boolean isSettable(RXBox node) {
                        return !node.spacing.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXBox node) {
                        return (StyleableProperty<Number>) node.spacingProperty();
                    }
                };

        private static final CssMetaData<RXBox, Pos> ALIGNMENT =
                new CssMetaData<>("-rx-alignment",
                        new EnumConverter<>(Pos.class), DEFAULT_ALIGNMENT) {
                    @Override
                    public boolean isSettable(RXBox node) {
                        return !node.alignment.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Pos> getStyleableProperty(RXBox node) {
                        return (StyleableProperty<Pos>) node.alignmentProperty();
                    }
                };

        private static final CssMetaData<RXBox, Boolean> FILL_CROSS_AXIS =
                new CssMetaData<>("-rx-fill-cross-axis",
                        BooleanConverter.getInstance(), DEFAULT_FILL_CROSS_AXIS) {
                    @Override
                    public boolean isSettable(RXBox node) {
                        return !node.fillCrossAxis.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXBox node) {
                        return (StyleableProperty<Boolean>) node.fillCrossAxisProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Pane.getClassCssMetaData());
            styleables.add(ORIENTATION);
            styleables.add(SPACING);
            styleables.add(ALIGNMENT);
            styleables.add(FILL_CROSS_AXIS);
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
