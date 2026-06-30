package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.utils.RXMath;

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
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

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
 * still consume space, matching JavaFX pane semantics. In horizontal
 * orientation, {@link VPos#BASELINE} follows HBox-like text baseline
 * alignment. In vertical orientation, {@link VPos#BASELINE} falls back to
 * top-axis behavior and the pane itself reports no baseline. Per-child
 * {@link #setAlignment(Node, Pos) alignment} constraints can override this
 * pane's alignment within an individual child's assigned layout area.</p>
 */
public class RXBox extends Pane {

    // ==================== Constants ====================

    /**
     * Default orientation.
     */
    private static final Orientation DEFAULT_ORIENTATION = Orientation.HORIZONTAL;

    /**
     * Default child spacing.
     */
    private static final double DEFAULT_SPACING = 0.0;

    /**
     * Default child alignment.
     */
    private static final Pos DEFAULT_ALIGNMENT = Pos.TOP_LEFT;

    /**
     * Default cross-axis fill behavior.
     */
    private static final boolean DEFAULT_FILL_CROSS_AXIS = true;

    private static final String DEFAULT_STYLE_CLASS = "rx-box";
    private static final String GROW_CONSTRAINT = "rxbox-grow";
    private static final String MARGIN_CONSTRAINT = "rxbox-margin";
    private static final String ALIGNMENT_CONSTRAINT = "rxbox-alignment";
    private static final double EPSILON = 1.0e-6;

    // ==================== Types ====================

    private enum Axis {
        X,
        Y
    }

    private enum SizeKind {
        MIN,
        PREF,
        MAX
    }

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
     * Sets the alignment constraint for a child. A non-null value overrides
     * this pane's {@link #alignmentProperty() alignment} for that child within
     * the child's assigned layout area. Setting {@code null} removes the
     * constraint.
     *
     * <p>This constraint positions the child; it does not set the child's
     * min, preferred, or max size. In a one-dimensional box, the visible effect
     * is normally on the cross axis: horizontal boxes use the vertical part of
     * {@code value}, and vertical boxes use the horizontal part. If the child
     * fills the cross axis, the alignment may not be visible. Constrain the
     * child's cross-axis maximum size or disable
     * {@link #fillCrossAxisProperty() fillCrossAxis} when the child should
     * remain smaller than its assigned area.</p>
     *
     * <p>{@code BASELINE_*} values participate in baseline alignment only for a
     * horizontal RXBox whose own alignment is also baseline. In other cases,
     * the baseline part is treated as top alignment. In a horizontal baseline
     * RXBox, a child with non-baseline alignment does not participate in the
     * baseline group and follows normal cross-axis fill behavior.</p>
     *
     * @param child the child node
     * @param value the child alignment, or {@code null}
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public static void setAlignment(Node child, Pos value) {
        setConstraint(child, ALIGNMENT_CONSTRAINT, value);
    }

    /**
     * Returns the alignment constraint for a child.
     *
     * @param child the child node
     * @return the child alignment, or {@code null} if none is set
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public static Pos getAlignment(Node child) {
        return (Pos) getConstraint(child, ALIGNMENT_CONSTRAINT);
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
        setAlignment(child, null);
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
                @Override
                protected void invalidated() {
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
     * Orientation of the main layout axis. A {@code null} value is not rejected;
     * it resolves to the default at the use site.
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
     */
    public final void setOrientation(Orientation value) {
        orientation.set(value);
    }

    // ==================== Spacing ====================

    private final DoubleProperty spacing = new StyleableDoubleProperty(DEFAULT_SPACING) {
        @Override
        protected void invalidated() {
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
     */
    public final void setSpacing(double value) {
        spacing.set(value);
    }

    private double spacingOrDefault() {
        double value = getSpacing();
        return Double.isFinite(value) ? value : DEFAULT_SPACING;
    }

    // ==================== Alignment ====================

    private final ObjectProperty<Pos> alignment =
            new StyleableObjectProperty<>(DEFAULT_ALIGNMENT) {
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
                    return RXBox.this;
                }

                @Override
                public String getName() {
                    return "alignment";
                }
            };

    /**
     * Overall alignment of children within this pane. {@link VPos#BASELINE} is
     * honored in horizontal orientation and treated as top alignment in vertical
     * orientation. A {@code null} value is not rejected; it resolves to the
     * default at the use site.
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

    // ==================== Bias Cache ====================

    private boolean biasDirty = true;
    private Orientation bias;
    private double baselineOffset = Double.NaN;

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
        baselineOffset = Double.NaN;
        super.requestLayout();
    }

    /**
     * Returns this pane's text baseline offset.
     *
     * <p>Only horizontal baseline alignment exposes a pane baseline. In
     * vertical orientation or non-baseline alignment, this method returns
     * {@link Node#BASELINE_OFFSET_SAME_AS_HEIGHT}.</p>
     *
     * @return the baseline offset
     */
    @Override
    public double getBaselineOffset() {
        if (Double.isNaN(baselineOffset)) {
            baselineOffset = computeBaselineOffset();
        }
        return baselineOffset;
    }

    private double computeBaselineOffset() {
        if (!isHorizontalBaseline()) {
            return Node.BASELINE_OFFSET_SAME_AS_HEIGHT;
        }
        List<Node> managed = getManagedChildren();
        if (managed.isEmpty()) {
            return Node.BASELINE_OFFSET_SAME_AS_HEIGHT;
        }
        double max = 0.0;
        boolean hasBaseline = false;
        for (Node child : managed) {
            if (!isBaselineParticipant(child)) {
                continue;
            }
            hasBaseline = true;
            double offset = child.getBaselineOffset();
            if (offset == Node.BASELINE_OFFSET_SAME_AS_HEIGHT) {
                return Node.BASELINE_OFFSET_SAME_AS_HEIGHT;
            }
            Insets margin = getMargin(child);
            max = Math.max(max, rawTop(margin) + child.getLayoutBounds().getMinY() + offset);
        }
        return hasBaseline ? max + snappedTopInset() : Node.BASELINE_OFFSET_SAME_AS_HEIGHT;
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
        boolean horizontal = isHorizontal();
        VPos vpos = effectiveVPos(align.getVpos(), horizontal);

        if (horizontal) {
            double[] areaWidths = computeAreaWidths(managed, height, false);
            double contentMain = adjustAreaSizes(managed, areaWidths, contentWidth, contentHeight, true);
            double x = left + computeXOffset(contentWidth, contentMain, hpos);
            double space = snapSpaceX(spacingOrDefault());
            double baselineOffset = vpos == VPos.BASELINE
                    ? computeAreaBaselineOffset(managed, areaWidths, contentHeight)
                    : -1.0;
            for (int i = 0, size = managed.size(); i < size; i++) {
                Node child = managed.get(i);
                Insets margin = getMargin(child);
                Pos childAlignment = childAlignmentOrDefault(child, align);
                layoutInArea(child, x, top, areaWidths[i], contentHeight,
                        baselineOffset, margin, true, shouldFillCrossAxis(child),
                        childAlignment.getHpos(), effectiveChildVPos(childAlignment, vpos, true));
                x += areaWidths[i] + space;
            }
        } else {
            double[] areaHeights = computeAreaHeights(managed, width, false);
            double contentMain = adjustAreaSizes(managed, areaHeights, contentHeight, contentWidth, false);
            double y = top + computeYOffset(contentHeight, contentMain, vpos);
            double space = snapSpaceY(spacingOrDefault());
            for (int i = 0, size = managed.size(); i < size; i++) {
                Node child = managed.get(i);
                Insets margin = getMargin(child);
                Pos childAlignment = childAlignmentOrDefault(child, align);
                layoutInArea(child, left, y, contentWidth, areaHeights[i],
                        -1, margin, shouldFillCrossAxis(child), true,
                        childAlignment.getHpos(), effectiveChildVPos(childAlignment, vpos, false));
                y += areaHeights[i] + space;
            }
        }
    }

    private static Pos childAlignmentOrDefault(Node child, Pos defaultAlignment) {
        Pos childAlignment = getAlignment(child);
        return childAlignment == null ? defaultAlignment : childAlignment;
    }

    private static VPos effectiveChildVPos(Pos childAlignment, VPos boxVPos, boolean horizontal) {
        VPos childVPos = childAlignment.getVpos();
        if (childVPos != VPos.BASELINE) {
            return childVPos;
        }
        return horizontal && boxVPos == VPos.BASELINE ? VPos.BASELINE : VPos.TOP;
    }

    private boolean isBaselineParticipant(Node child) {
        if (!isHorizontalBaseline()) {
            return false;
        }
        Pos childAlignment = getAlignment(child);
        return childAlignment == null || childAlignment.getVpos() == VPos.BASELINE;
    }

    private boolean isHorizontal() {
        return orientationOrDefault() == Orientation.HORIZONTAL;
    }

    private Orientation orientationOrDefault() {
        Orientation o = getOrientation();
        return o == null ? DEFAULT_ORIENTATION : o;
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
        double baselineComplement = isHorizontalBaseline()
                ? (minimum ? computeMinBaselineComplement(managed) : computePrefBaselineComplement(managed))
                : -1.0;
        double[] widths = new double[managed.size()];
        for (int i = 0, size = managed.size(); i < size; i++) {
            Node child = managed.get(i);
            Insets margin = getMargin(child);
            boolean baselineParticipant = isBaselineParticipant(child);
            double childBaselineComplement = baselineParticipant ? baselineComplement : -1.0;
            widths[i] = minimum
                    ? computeChildArea(child, margin, Axis.X, SizeKind.MIN,
                            availableHeight, shouldFillCrossAxis(child), childBaselineComplement)
                    : computeChildArea(child, margin, Axis.X, SizeKind.PREF,
                            availableHeight, shouldFillCrossAxis(child), childBaselineComplement);
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
                    ? computeChildArea(child, margin, Axis.Y, SizeKind.MIN,
                            availableWidth, shouldFillCrossAxis(child))
                    : computeChildArea(child, margin, Axis.Y, SizeKind.PREF,
                            availableWidth, shouldFillCrossAxis(child));
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
        double baselineComplement = horizontal && isHorizontalBaseline()
                ? computeMinBaselineComplement(managed)
                : -1.0;
        double remaining = distribute(managed, sizes, extra, availableCross,
                horizontal, baselineComplement, Priority.ALWAYS);
        remaining = distribute(managed, sizes, remaining, availableCross,
                horizontal, baselineComplement, Priority.SOMETIMES);
        contentMain += extra - remaining;
        return contentMain;
    }

    private double distribute(List<Node> managed, double[] sizes, double extra,
                              double availableCross, boolean horizontal,
                              double baselineComplement, Priority priority) {
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
                        ? computeChildMinMainArea(child, availableCross, horizontal, baselineComplement)
                        : computeChildMaxMainArea(child, availableCross, horizontal, baselineComplement);
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

        double pixelSize = pixelSize(horizontal);
        double available = extra;
        while (Math.abs(available) > 1.0 && adjustable > 0) {
            double portion = snapPortion(available / adjustable, horizontal);
            if (portion == 0.0) {
                if (pixelSize == 0.0) {
                    break;
                }
                portion = pixelSize * Math.signum(available);
            }

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
                if (Math.abs(change) < Math.abs(portion)) {
                    limits[i] = Double.NaN;
                    adjustable--;
                }
                if (Math.abs(available) < 1.0) {
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
                                           boolean horizontal, double baselineComplement) {
        Insets margin = getMargin(child);
        double childBaselineComplement = isBaselineParticipant(child) ? baselineComplement : -1.0;
        return computeChildArea(child, margin, axisOf(horizontal), SizeKind.MIN,
                availableCross, shouldFillCrossAxis(child), childBaselineComplement);
    }

    private double computeChildMaxMainArea(Node child, double availableCross,
                                           boolean horizontal, double baselineComplement) {
        Insets margin = getMargin(child);
        double childBaselineComplement = isBaselineParticipant(child) ? baselineComplement : -1.0;
        return computeChildArea(child, margin, axisOf(horizontal), SizeKind.MAX,
                availableCross, shouldFillCrossAxis(child), childBaselineComplement);
    }

    private double computeMaxAreaWidth(List<Node> managed, double[] childHeights,
                                       boolean minimum) {
        double max = 0.0;
        for (int i = 0, size = managed.size(); i < size; i++) {
            Node child = managed.get(i);
            Insets margin = getMargin(child);
            double childHeight = childHeights == null ? -1 : childHeights[i];
            // fill=false on purpose: the cross-axis (height) is capped at the child's
            // pref, mirroring VBox.computePrefWidth, which calls Region.getMaxAreaWidth
            // with fillHeight=false. Do NOT "symmetrize" this to true to match
            // computeMaxAreaHeight below — JavaFX's getMaxAreaHeight (no cap) and
            // getMaxAreaWidth (capped) are intentionally asymmetric, and RXBox matches
            // each. Changing it would diverge from VBox for a vertical vgrow +
            // content-bias child whose pref height is small (see RXBoxTest).
            double areaWidth = minimum
                    ? computeChildArea(child, margin, Axis.X, SizeKind.MIN,
                            childHeight, false)
                    : computeChildArea(child, margin, Axis.X, SizeKind.PREF,
                            childHeight, false);
            max = Math.max(max, areaWidth);
        }
        return max;
    }

    private double computeMaxAreaHeight(List<Node> managed, double[] childWidths,
                                        boolean minimum) {
        if (isHorizontalBaseline()) {
            return computeBaselineAreaHeight(managed, childWidths, minimum);
        }
        double max = 0.0;
        for (int i = 0, size = managed.size(); i < size; i++) {
            Node child = managed.get(i);
            Insets margin = getMargin(child);
            double childWidth = childWidths == null ? -1 : childWidths[i];
            // fill=true: measure each child's height at its allocated (grown) main-axis
            // width, NOT a pref-capped width. This mirrors HBox.computePrefHeight, which
            // feeds the adjusted widths to Region.getMaxAreaHeight — a method that has no
            // fill flag and uses the width as-is. Passing false would mismeasure a grow +
            // content-bias child whose pref width is small (e.g. 0), inflating the box.
            // Deliberately asymmetric with computeMaxAreaWidth above (see note there).
            double areaHeight = minimum
                    ? computeChildArea(child, margin, Axis.Y, SizeKind.MIN,
                            childWidth, true)
                    : computeChildArea(child, margin, Axis.Y, SizeKind.PREF,
                            childWidth, true);
            max = Math.max(max, areaHeight);
        }
        return max;
    }

    private double computeChildArea(Node child, Insets margin, Axis axis,
                                    SizeKind kind, double availableCross,
                                    boolean fillCross) {
        return computeChildArea(child, margin, axis, kind, availableCross, fillCross, -1.0);
    }

    private double computeChildArea(Node child, Insets margin, Axis axis,
                                    SizeKind kind, double availableCross,
                                    boolean fillCross, double baselineComplement) {
        double alt = computeAlt(child, margin, axis, availableCross, fillCross,
                baselineComplement);
        double value;
        switch (kind) {
            case MIN:
                value = snapSize(axis, childMin(child, axis, alt));
                break;
            case PREF:
                value = snapSize(axis, RXMath.clamp(childPref(child, axis, alt),
                        childMin(child, axis, alt), childMax(child, axis, alt)));
                break;
            case MAX:
                double max = childMax(child, axis, alt);
                if (max == Double.MAX_VALUE) {
                    return max;
                }
                value = snapSize(axis, RXMath.clamp(max, childMin(child, axis, alt),
                        Double.MAX_VALUE));
                break;
            default:
                throw new AssertionError("Unhandled SizeKind: " + kind);
        }
        return leading(axis, margin) + value + trailing(axis, margin);
    }

    private double computeAlt(Node child, Insets margin, Axis axis,
                              double availableCross, boolean fillCross,
                              double baselineComplement) {
        if (availableCross == -1 || !child.isResizable()
                || child.getContentBias() != crossOrientation(axis)) {
            return -1;
        }
        Axis crossAxis = crossAxis(axis);
        double contentCross = Math.max(0.0,
                availableCross - leading(crossAxis, margin) - trailing(crossAxis, margin));
        if (axis == Axis.X
                && child.getBaselineOffset() == Node.BASELINE_OFFSET_SAME_AS_HEIGHT
                && baselineComplement != -1.0) {
            contentCross -= baselineComplement;
        }
        return computeBounded(child, crossAxis, fillCross, contentCross);
    }

    private double computeBounded(Node child, Axis axis, boolean fill, double contentSize) {
        double min = childMin(child, axis, -1);
        double pref = fill ? contentSize : Math.min(contentSize, childPref(child, axis, -1));
        return snapSize(axis, RXMath.clamp(pref, min, childMax(child, axis, -1)));
    }

    private Axis axisOf(boolean horizontal) {
        return horizontal ? Axis.X : Axis.Y;
    }

    private Axis crossAxis(Axis axis) {
        switch (axis) {
            case X:
                return Axis.Y;
            case Y:
                return Axis.X;
            default:
                throw new AssertionError("Unhandled Axis: " + axis);
        }
    }

    private Orientation crossOrientation(Axis axis) {
        switch (axis) {
            case X:
                return Orientation.VERTICAL;
            case Y:
                return Orientation.HORIZONTAL;
            default:
                throw new AssertionError("Unhandled Axis: " + axis);
        }
    }

    private double childMin(Node child, Axis axis, double alt) {
        switch (axis) {
            case X:
                return child.minWidth(alt);
            case Y:
                return child.minHeight(alt);
            default:
                throw new AssertionError("Unhandled Axis: " + axis);
        }
    }

    private double childPref(Node child, Axis axis, double alt) {
        switch (axis) {
            case X:
                return child.prefWidth(alt);
            case Y:
                return child.prefHeight(alt);
            default:
                throw new AssertionError("Unhandled Axis: " + axis);
        }
    }

    private double childMax(Node child, Axis axis, double alt) {
        switch (axis) {
            case X:
                return child.maxWidth(alt);
            case Y:
                return child.maxHeight(alt);
            default:
                throw new AssertionError("Unhandled Axis: " + axis);
        }
    }

    private double snapSize(Axis axis, double value) {
        switch (axis) {
            case X:
                return snapSizeX(value);
            case Y:
                return snapSizeY(value);
            default:
                throw new AssertionError("Unhandled Axis: " + axis);
        }
    }

    private double leading(Axis axis, Insets margin) {
        switch (axis) {
            case X:
                return left(margin);
            case Y:
                return top(margin);
            default:
                throw new AssertionError("Unhandled Axis: " + axis);
        }
    }

    private double trailing(Axis axis, Insets margin) {
        switch (axis) {
            case X:
                return right(margin);
            case Y:
                return bottom(margin);
            default:
                throw new AssertionError("Unhandled Axis: " + axis);
        }
    }

    private double spacingTotal(int childCount, boolean horizontal) {
        if (childCount <= 1) {
            return 0.0;
        }
        double spacingSize = horizontal ? snapSpaceX(spacingOrDefault()) : snapSpaceY(spacingOrDefault());
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

    private double snapPortion(double value, boolean horizontal) {
        if (!isSnapToPixel() || value == 0.0) {
            return value;
        }
        double scale = snapScale(horizontal);
        return value > 0.0 ? scaledFloor(value, scale) : scaledCeil(value, scale);
    }

    private double pixelSize(boolean horizontal) {
        return isSnapToPixel() ? 1.0 / snapScale(horizontal) : 0.0;
    }

    private double snapScale(boolean horizontal) {
        Scene scene = getScene();
        if (scene == null) {
            return 1.0;
        }
        Window window = scene.getWindow();
        if (window == null) {
            return 1.0;
        }
        double scale = horizontal ? window.getRenderScaleX() : window.getRenderScaleY();
        return Double.isFinite(scale) && scale > 0.0 ? scale : 1.0;
    }

    private double scaledFloor(double value, double scale) {
        double scaledValue = value * scale;
        if (Double.isInfinite(scaledValue)) {
            return value;
        }
        return Math.floor(scaledValue + Math.ulp(scaledValue)) / scale;
    }

    private double scaledCeil(double value, double scale) {
        double scaledValue = value * scale;
        if (Double.isInfinite(scaledValue)) {
            return value;
        }
        return Math.ceil(scaledValue - Math.ulp(scaledValue)) / scale;
    }

    private double rawTop(Insets margin) {
        return margin == null ? 0.0 : margin.getTop();
    }

    private double computeXOffset(double width, double contentWidth, HPos hpos) {
        switch (hpos) {
            case CENTER:
                return (width - contentWidth) / 2.0;
            case RIGHT:
                return width - contentWidth;
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
                return (height - contentHeight) / 2.0;
            case BOTTOM:
                return height - contentHeight;
            default:
                throw new AssertionError("Unhandled VPos: " + vpos);
        }
    }

    private VPos effectiveVPos(VPos vpos, boolean horizontal) {
        if (vpos != VPos.BASELINE) {
            return vpos;
        }
        return horizontal ? VPos.BASELINE : VPos.TOP;
    }

    private boolean shouldFillCrossAxis(Node child) {
        return isFillCrossAxis() && !isBaselineParticipant(child);
    }

    private boolean isHorizontalBaseline() {
        return isHorizontal() && alignmentOrDefault().getVpos() == VPos.BASELINE;
    }

    private double computeBaselineAreaHeight(List<Node> managed, double[] childWidths,
                                             boolean minimum) {
        double maxAbove = 0.0;
        double maxBelow = 0.0;
        double maxNonBaseline = 0.0;
        boolean hasBaseline = false;
        for (int i = 0, size = managed.size(); i < size; i++) {
            Node child = managed.get(i);
            Insets margin = getMargin(child);
            double childWidth = childWidths == null ? -1.0 : childWidths[i];
            if (!isBaselineParticipant(child)) {
                // Horizontal baseline layout still allocates the child's main-axis width
                // before measuring height, matching the non-baseline horizontal path.
                double areaHeight = minimum
                        ? computeChildArea(child, margin, Axis.Y, SizeKind.MIN,
                                childWidth, true)
                        : computeChildArea(child, margin, Axis.Y, SizeKind.PREF,
                                childWidth, true);
                maxNonBaseline = Math.max(maxNonBaseline, areaHeight);
                continue;
            }
            hasBaseline = true;
            double baseline = child.getBaselineOffset();
            double childHeight = minimum
                    ? snapSizeY(child.minHeight(childWidth))
                    : snapSizeY(child.prefHeight(childWidth));
            if (baseline == Node.BASELINE_OFFSET_SAME_AS_HEIGHT) {
                maxAbove = Math.max(maxAbove, childHeight + top(margin));
            } else {
                maxAbove = Math.max(maxAbove, baseline + top(margin));
                maxBelow = Math.max(maxBelow, childHeight - baseline + bottom(margin));
            }
        }
        return Math.max(hasBaseline ? maxAbove + maxBelow : 0.0, maxNonBaseline);
    }

    private double computeMinBaselineComplement(List<Node> managed) {
        return computeBaselineComplement(managed, true);
    }

    private double computePrefBaselineComplement(List<Node> managed) {
        return computeBaselineComplement(managed, false);
    }

    private double computeBaselineComplement(List<Node> managed, boolean minimum) {
        double complement = 0.0;
        for (Node child : managed) {
            if (!isBaselineParticipant(child)) {
                continue;
            }
            double baseline = child.getBaselineOffset();
            if (baseline == Node.BASELINE_OFFSET_SAME_AS_HEIGHT) {
                continue;
            }
            double height = child.isResizable()
                    ? (minimum ? child.minHeight(-1) : child.prefHeight(-1))
                    : child.getLayoutBounds().getHeight();
            complement = Math.max(complement, height - baseline);
        }
        return complement;
    }

    private double computeAreaBaselineOffset(List<Node> managed, double[] areaWidths,
                                             double areaHeight) {
        double minComplement = computeMinBaselineComplement(managed);
        double offset = 0.0;
        boolean hasBaseline = false;
        for (int i = 0, size = managed.size(); i < size; i++) {
            Node child = managed.get(i);
            if (!isBaselineParticipant(child)) {
                continue;
            }
            hasBaseline = true;
            Insets margin = getMargin(child);
            double top = top(margin);
            double bottom = bottom(margin);
            double baseline = child.getBaselineOffset();
            if (baseline == Node.BASELINE_OFFSET_SAME_AS_HEIGHT) {
                double alt = child.getContentBias() == Orientation.HORIZONTAL ? areaWidths[i] : -1.0;
                double availableHeight = areaHeight - minComplement - top - bottom;
                double childHeight = RXMath.clamp(child.prefHeight(alt), child.minHeight(alt),
                        Math.min(child.maxHeight(alt), availableHeight));
                offset = Math.max(offset, top + childHeight);
            } else {
                offset = Math.max(offset, top + baseline);
            }
        }
        return hasBaseline ? offset : -1.0;
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
