package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.internal.RXResources;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.DoublePropertyBase;
import javafx.beans.property.ObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An enhanced flow pane that lays managed children out in runs (rows), wrapping
 * at the available width, and — unlike {@link javafx.scene.layout.FlowPane} —
 * aligns the whole content block <em>once</em> ({@code contentAlignment}) and
 * then positions items inside each run by a separate {@code lineAlignment}.
 *
 * <p>This decoupling fixes FlowPane's "centered last row" behavior: with
 * {@code contentAlignment = Pos.TOP_CENTER} and {@code lineAlignment = HPos.LEFT},
 * a 7-card / 3-column flow renders
 * <pre>
 *   [1][2][3]
 *   [4][5][6]
 *   [7]          &lt;- stays LEFT inside a centered content block, not self-centered
 * </pre>
 * because FlowPane centers each run independently against the inside width,
 * whereas RXFlowPane centers the bounding block of all runs once and then lays
 * each run out left-aligned within that block. Selecting
 * {@code lineAlignment = HPos.CENTER} reproduces FlowPane's centered-last-row
 * look as an explicit, opt-in special case.</p>
 *
 * <p>Each child keeps its own preferred width (there are no uniform tiles), so
 * this is a true "enhanced FlowPane" rather than a grid. The pane is
 * height-for-width ({@link #getContentBias()} is always
 * {@link Orientation#HORIZONTAL}). Only managed children take part in layout.</p>
 *
 * <p>See also {@code RXWrapPane} (an earlier name considered for this class).</p>
 */
public class RXFlowPane extends Pane {

    // ==================== Constants ====================

    private static final double DEFAULT_HGAP = 0.0;
    private static final double DEFAULT_VGAP = 0.0;
    private static final Pos DEFAULT_CONTENT_ALIGNMENT = Pos.TOP_CENTER;
    private static final HPos DEFAULT_LINE_ALIGNMENT = HPos.LEFT;
    private static final VPos DEFAULT_ROW_ALIGNMENT = VPos.TOP;
    private static final double DEFAULT_PREF_WRAP_LENGTH = 400.0;

    private static final String DEFAULT_STYLE_CLASS = "rx-flow-pane";
    private static final String MARGIN_CONSTRAINT = "rxflowpane-margin";

    // ==================== Constraints ====================

    /**
     * Sets the margin around a child. Setting {@code null} removes the
     * constraint.
     *
     * @param child the child node
     * @param value the margin, or {@code null} to remove
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public static void setMargin(Node child, Insets value) {
        setConstraint(child, MARGIN_CONSTRAINT, value);
    }

    /**
     * Returns the margin around a child, or {@code null} if none is set.
     *
     * @param child the child node
     * @return the margin, or {@code null}
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public static Insets getMargin(Node child) {
        return (Insets) getConstraint(child, MARGIN_CONSTRAINT);
    }

    /**
     * Removes all RXFlowPane constraints from a child.
     *
     * @param child the child node
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public static void clearConstraints(Node child) {
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
     * Creates an empty RXFlowPane.
     */
    public RXFlowPane() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates an RXFlowPane with the given children.
     *
     * @param children the initial children
     */
    public RXFlowPane(Node... children) {
        this();
        getChildren().addAll(children);
    }

    /**
     * Creates an RXFlowPane with the given gaps.
     *
     * @param hgap the horizontal gap between items in a run
     * @param vgap the vertical gap between runs
     */
    public RXFlowPane(double hgap, double vgap) {
        this();
        setHgap(hgap);
        setVgap(vgap);
    }

    /**
     * Creates an RXFlowPane with the given gaps and children.
     *
     * @param hgap the horizontal gap between items in a run
     * @param vgap the vertical gap between runs
     * @param children the initial children
     */
    public RXFlowPane(double hgap, double vgap, Node... children) {
        this(hgap, vgap);
        getChildren().addAll(children);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
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
            return RXFlowPane.this;
        }

        @Override
        public String getName() {
            return "hgap";
        }
    };

    /**
     * Horizontal gap between adjacent items within a run. Negative values are
     * accepted (items overlap); non-finite values resolve to {@code 0} at the
     * use site.
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

    private double hgapOrDefault() {
        double value = getHgap();
        return Double.isFinite(value) ? value : DEFAULT_HGAP;
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
            return RXFlowPane.this;
        }

        @Override
        public String getName() {
            return "vgap";
        }
    };

    /**
     * Vertical gap between adjacent runs. Negative values are accepted (runs
     * overlap); non-finite values resolve to {@code 0} at the use site.
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

    private double vgapOrDefault() {
        double value = getVgap();
        return Double.isFinite(value) ? value : DEFAULT_VGAP;
    }

    // ==================== Content alignment ====================

    private final ObjectProperty<Pos> contentAlignment =
            new StyleableObjectProperty<>(DEFAULT_CONTENT_ALIGNMENT) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, Pos> getCssMetaData() {
                    return StyleableProperties.CONTENT_ALIGNMENT;
                }

                @Override
                public Object getBean() {
                    return RXFlowPane.this;
                }

                @Override
                public String getName() {
                    return "contentAlignment";
                }
            };

    /**
     * Alignment of the whole content block (the bounding box of all runs) within
     * the pane's inside area, applied once on both axes. With
     * {@link Pos#TOP_CENTER} (the default) the block is horizontally centered
     * while each run starts at the block's left edge (see
     * {@link #lineAlignmentProperty()}). The content block has no baseline, so a
     * vertical {@link VPos#BASELINE} component is treated as {@link VPos#TOP}
     * (e.g. {@code BASELINE_CENTER} behaves like {@code TOP_CENTER}); per-item
     * baseline alignment within a run is {@link #rowAlignmentProperty()}. A
     * {@code null} value is not rejected; it resolves to the default
     * ({@link Pos#TOP_CENTER}) at the use site.
     *
     * @return the content-alignment property
     */
    public final ObjectProperty<Pos> contentAlignmentProperty() {
        return contentAlignment;
    }

    /**
     * Returns the content alignment.
     *
     * @return the content alignment
     */
    public final Pos getContentAlignment() {
        return contentAlignment.get();
    }

    /**
     * Sets the content alignment.
     *
     * @param value the content alignment
     */
    public final void setContentAlignment(Pos value) {
        contentAlignment.set(value);
    }

    private Pos contentAlignmentOrDefault() {
        Pos value = getContentAlignment();
        return value != null ? value : DEFAULT_CONTENT_ALIGNMENT;
    }

    // ==================== Line alignment ====================

    private final ObjectProperty<HPos> lineAlignment =
            new StyleableObjectProperty<>(DEFAULT_LINE_ALIGNMENT) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, HPos> getCssMetaData() {
                    return StyleableProperties.LINE_ALIGNMENT;
                }

                @Override
                public Object getBean() {
                    return RXFlowPane.this;
                }

                @Override
                public String getName() {
                    return "lineAlignment";
                }
            };

    /**
     * Horizontal alignment of items <em>within each run</em>, relative to the
     * content-block width (not the pane's inside width). With {@link HPos#LEFT}
     * (the default) a short last row stays at the block's left edge instead of
     * being centered by itself. A {@code null} value is not rejected; it
     * resolves to the default ({@link HPos#LEFT}) at the use site.
     *
     * @return the line-alignment property
     */
    public final ObjectProperty<HPos> lineAlignmentProperty() {
        return lineAlignment;
    }

    /**
     * Returns the line alignment.
     *
     * @return the line alignment
     */
    public final HPos getLineAlignment() {
        return lineAlignment.get();
    }

    /**
     * Sets the line alignment.
     *
     * @param value the line alignment
     */
    public final void setLineAlignment(HPos value) {
        lineAlignment.set(value);
    }

    private HPos lineAlignmentOrDefault() {
        HPos value = getLineAlignment();
        return value != null ? value : DEFAULT_LINE_ALIGNMENT;
    }

    // ==================== Row alignment ====================

    private final ObjectProperty<VPos> rowAlignment =
            new StyleableObjectProperty<>(DEFAULT_ROW_ALIGNMENT) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, VPos> getCssMetaData() {
                    return StyleableProperties.ROW_ALIGNMENT;
                }

                @Override
                public Object getBean() {
                    return RXFlowPane.this;
                }

                @Override
                public String getName() {
                    return "rowAlignment";
                }
            };

    /**
     * Vertical alignment of each child within its run's height. {@link VPos#TOP}
     * (the default) lines the items up along the top of each run.
     * {@link VPos#BASELINE} aligns items by their text baseline. A child without a
     * real baseline (its {@code getBaselineOffset()} reports
     * {@link Node#BASELINE_OFFSET_SAME_AS_HEIGHT}, e.g. a plain container) is
     * aligned by its bottom edge, matching {@code FlowPane}; such a child's bottom
     * margin is not reserved in a baseline run. A {@code null} value is not
     * rejected; it resolves to the default ({@link VPos#TOP}) at the use site.
     *
     * @return the row-alignment property
     */
    public final ObjectProperty<VPos> rowAlignmentProperty() {
        return rowAlignment;
    }

    /**
     * Returns the row alignment.
     *
     * @return the row alignment
     */
    public final VPos getRowAlignment() {
        return rowAlignment.get();
    }

    /**
     * Sets the row alignment.
     *
     * @param value the row alignment
     */
    public final void setRowAlignment(VPos value) {
        rowAlignment.set(value);
    }

    private VPos rowAlignmentOrDefault() {
        VPos value = getRowAlignment();
        return value != null ? value : DEFAULT_ROW_ALIGNMENT;
    }

    // ==================== Preferred wrap length ====================

    private final DoubleProperty prefWrapLength = new DoublePropertyBase(DEFAULT_PREF_WRAP_LENGTH) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public Object getBean() {
            return RXFlowPane.this;
        }

        @Override
        public String getName() {
            return "prefWrapLength";
        }
    };

    /**
     * Preferred length used to wrap runs when computing the pane's preferred
     * size only. Like {@link javafx.scene.layout.FlowPane#prefWrapLengthProperty()},
     * it does <em>not</em> control the actual wrapping at layout time — the real
     * wrap boundary is the width the parent gives this pane. It exists so an
     * unconstrained pane reports a sane preferred width instead of one giant row.
     *
     * @return the preferred-wrap-length property
     */
    public final DoubleProperty prefWrapLengthProperty() {
        return prefWrapLength;
    }

    /**
     * Returns the preferred wrap length.
     *
     * @return the preferred wrap length
     */
    public final double getPrefWrapLength() {
        return prefWrapLength.get();
    }

    /**
     * Sets the preferred wrap length.
     *
     * @param value the preferred wrap length
     */
    public final void setPrefWrapLength(double value) {
        prefWrapLength.set(value);
    }

    // ==================== Run machine ====================

    private List<Run> runs;
    private double lastMaxRunLength = -1;
    private boolean computingRuns;

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestLayout() {
        if (!computingRuns) {
            runs = null;
        }
        super.requestLayout();
    }

    private List<Run> getRuns(double maxRunLength) {
        if (runs == null || maxRunLength != lastMaxRunLength) {
            computingRuns = true;
            try {
                double hgap = snapSpaceX(hgapOrDefault());
                VPos rowAlignment = rowAlignmentOrDefault();
                List<Run> built = new ArrayList<>();
                double runLength = 0;
                Run run = new Run();
                for (Node child : getManagedChildren()) {
                    LayoutRect nodeRect = new LayoutRect();
                    nodeRect.node = child;
                    nodeRect.width = prefAreaWidth(child);
                    nodeRect.height = prefAreaHeight(child);
                    if (runLength + nodeRect.width > maxRunLength && runLength > 0) {
                        // wrap to next run unless it is the only node in the run
                        normalizeRun(run, hgap, rowAlignment);
                        built.add(run);
                        runLength = 0;
                        run = new Run();
                    }
                    runLength += nodeRect.width + hgap;
                    run.rects.add(nodeRect);
                }
                normalizeRun(run, hgap, rowAlignment);
                built.add(run);
                // Publish the runs and their key together, only after a clean
                // build, so a child measurement that throws leaves no half-built
                // cache (and no stale key) to be reused on the next pass.
                runs = built;
                lastMaxRunLength = maxRunLength;
            } finally {
                computingRuns = false;
            }
        }
        return runs;
    }

    private void normalizeRun(Run run, double hgap, VPos rowAlignment) {
        int count = run.rects.size();
        double width = count > 1 ? (count - 1) * hgap : 0;
        double plainHeight = 0;
        for (LayoutRect lrect : run.rects) {
            width += lrect.width;
            plainHeight = Math.max(plainHeight, lrect.height);
        }
        run.width = width;
        if (rowAlignment != VPos.BASELINE) {
            run.height = plainHeight;
            run.baselineOffset = 0;
            return;
        }
        // Baseline rows size to maxAbove + maxBelow, exactly Region.getMaxAreaHeight's
        // BASELINE path (and FlowPane): this can exceed the tallest child's pref-area
        // height when a shallow-baseline child has a deep below-baseline part. No
        // plainHeight floor on purpose — a SAME_AS_HEIGHT child's bottom margin stays
        // below the implied baseline (as in FlowPane), and a floor would only wedge
        // empty space below it without un-compressing it. run.baselineOffset is the
        // shared baseline from the run top, later fed to layoutInArea.
        double maxAbove = 0;
        double maxBelow = 0;
        for (LayoutRect lrect : run.rects) {
            Node child = lrect.node;
            Insets margin = getMargin(child);
            double top = margin == null ? 0 : snapSpaceY(margin.getTop());
            double bottom = margin == null ? 0 : snapSpaceY(margin.getBottom());
            double childHeight = lrect.height - top - bottom;
            double baseline = child.getBaselineOffset();
            if (baseline == Node.BASELINE_OFFSET_SAME_AS_HEIGHT) {
                maxAbove = Math.max(maxAbove, childHeight + top);
            } else {
                maxAbove = Math.max(maxAbove, baseline + top);
                maxBelow = Math.max(maxBelow, childHeight - baseline + bottom);
            }
        }
        run.height = maxAbove + maxBelow;
        run.baselineOffset = maxAbove;
    }

    private double computeContentWidth(List<Run> lines) {
        double width = 0;
        for (Run run : lines) {
            width = Math.max(width, run.width);
        }
        return width;
    }

    private double computeContentHeight(List<Run> lines) {
        // getRuns always returns at least one run, so (size - 1) is never negative.
        double vgap = snapSpaceY(vgapOrDefault());
        double height = (lines.size() - 1) * vgap;
        for (Run run : lines) {
            height += run.height;
        }
        return height;
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
    protected double computeMinWidth(double height) {
        double maxPref = 0;
        for (Node child : getManagedChildren()) {
            maxPref = Math.max(maxPref, prefAreaWidth(child));
        }
        return snappedLeftInset() + maxPref + snappedRightInset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height) {
        double wrap = getPrefWrapLength();
        List<Run> lines = getRuns(wrap);
        double width = computeContentWidth(lines);
        width = Math.max(width, wrap);
        return snappedLeftInset() + snapSizeX(width) + snappedRightInset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double forWidth) {
        double wrap = forWidth == -1
                ? getPrefWrapLength()
                : forWidth - snappedLeftInset() - snappedRightInset();
        List<Run> lines = getRuns(wrap);
        return snappedTopInset() + snapSizeY(computeContentHeight(lines)) + snappedBottomInset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren() {
        double top = snappedTopInset();
        double left = snappedLeftInset();
        // Inside extents are kept unsnapped here so the run-cache key
        // (maxRunLength == insideWidth) matches computePrefHeight's wrap width,
        // mirroring FlowPane; the block/line offsets are snapped on output below.
        double insideWidth = getWidth() - left - snappedRightInset();
        double insideHeight = getHeight() - top - snappedBottomInset();

        List<Run> lines = getRuns(insideWidth);
        double blockWidth = computeContentWidth(lines);
        double blockHeight = computeContentHeight(lines);

        Pos ca = contentAlignmentOrDefault();
        HPos la = lineAlignmentOrDefault();
        VPos ra = rowAlignmentOrDefault();

        double blockX = snapPositionX(left + blockHOffset(insideWidth, blockWidth, ca.getHpos()));
        double blockY = snapPositionY(top + blockVOffset(insideHeight, blockHeight, ca.getVpos()));

        double hgap = snapSpaceX(hgapOrDefault());
        double vgap = snapSpaceY(vgapOrDefault());

        double y = blockY;
        for (Run run : lines) {
            double lineX = snapPositionX(blockX + blockHOffset(blockWidth, run.width, la));
            double baselineOffset = ra == VPos.BASELINE ? run.baselineOffset : -1.0;
            double x = lineX;
            for (LayoutRect lrect : run.rects) {
                Node child = lrect.node;
                layoutInArea(child, x, y, lrect.width, run.height,
                        baselineOffset, getMargin(child), false, false, HPos.LEFT, ra);
                x += lrect.width + hgap;
            }
            y += run.height + vgap;
        }
    }

    // ==================== Layout helpers ====================

    private double prefAreaWidth(Node child) {
        Insets margin = getMargin(child);
        double left = margin == null ? 0 : snapSpaceX(margin.getLeft());
        double right = margin == null ? 0 : snapSpaceX(margin.getRight());
        double width = boundedSize(child.minWidth(-1), child.prefWidth(-1), child.maxWidth(-1));
        return left + snapSizeX(width) + right;
    }

    private double prefAreaHeight(Node child) {
        Insets margin = getMargin(child);
        double top = margin == null ? 0 : snapSpaceY(margin.getTop());
        double bottom = margin == null ? 0 : snapSpaceY(margin.getBottom());
        double alt = -1;
        if (child.isResizable() && child.getContentBias() == Orientation.HORIZONTAL) {
            alt = snapSizeX(boundedSize(child.minWidth(-1), child.prefWidth(-1), child.maxWidth(-1)));
        }
        double height = boundedSize(child.minHeight(alt), child.prefHeight(alt), child.maxHeight(alt));
        return top + snapSizeY(height) + bottom;
    }

    private static double boundedSize(double min, double pref, double max) {
        return Math.min(Math.max(min, pref), Math.max(min, max));
    }

    private static double blockHOffset(double area, double content, HPos hpos) {
        switch (hpos) {
            case LEFT:
                return 0.0;
            case CENTER:
                return (area - content) / 2.0;
            case RIGHT:
                return area - content;
            default:
                throw new AssertionError("Unhandled HPos: " + hpos);
        }
    }

    private static double blockVOffset(double area, double content, VPos vpos) {
        switch (vpos) {
            case BASELINE:
            case TOP:
                return 0.0;
            case CENTER:
                return (area - content) / 2.0;
            case BOTTOM:
                return area - content;
            default:
                throw new AssertionError("Unhandled VPos: " + vpos);
        }
    }

    // ==================== CSS ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXFlowPane, Number> HGAP =
                new CssMetaData<>("-rx-hgap", SizeConverter.getInstance(), DEFAULT_HGAP) {
                    @Override
                    public boolean isSettable(RXFlowPane node) {
                        return !node.hgap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXFlowPane node) {
                        return (StyleableProperty<Number>) node.hgapProperty();
                    }
                };

        private static final CssMetaData<RXFlowPane, Number> VGAP =
                new CssMetaData<>("-rx-vgap", SizeConverter.getInstance(), DEFAULT_VGAP) {
                    @Override
                    public boolean isSettable(RXFlowPane node) {
                        return !node.vgap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXFlowPane node) {
                        return (StyleableProperty<Number>) node.vgapProperty();
                    }
                };

        private static final CssMetaData<RXFlowPane, Pos> CONTENT_ALIGNMENT =
                new CssMetaData<>("-rx-content-alignment",
                        new EnumConverter<>(Pos.class), DEFAULT_CONTENT_ALIGNMENT) {
                    @Override
                    public boolean isSettable(RXFlowPane node) {
                        return !node.contentAlignment.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Pos> getStyleableProperty(RXFlowPane node) {
                        return (StyleableProperty<Pos>) node.contentAlignmentProperty();
                    }
                };

        private static final CssMetaData<RXFlowPane, HPos> LINE_ALIGNMENT =
                new CssMetaData<>("-rx-line-alignment",
                        new EnumConverter<>(HPos.class), DEFAULT_LINE_ALIGNMENT) {
                    @Override
                    public boolean isSettable(RXFlowPane node) {
                        return !node.lineAlignment.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<HPos> getStyleableProperty(RXFlowPane node) {
                        return (StyleableProperty<HPos>) node.lineAlignmentProperty();
                    }
                };

        private static final CssMetaData<RXFlowPane, VPos> ROW_ALIGNMENT =
                new CssMetaData<>("-rx-row-alignment",
                        new EnumConverter<>(VPos.class), DEFAULT_ROW_ALIGNMENT) {
                    @Override
                    public boolean isSettable(RXFlowPane node) {
                        return !node.rowAlignment.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<VPos> getStyleableProperty(RXFlowPane node) {
                        return (StyleableProperty<VPos>) node.rowAlignmentProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Pane.getClassCssMetaData());
            Collections.addAll(styleables, HGAP, VGAP, CONTENT_ALIGNMENT, LINE_ALIGNMENT, ROW_ALIGNMENT);
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
     * {@inheritDoc}
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    // ==================== Run model ====================

    private static final class LayoutRect {
        private Node node;
        private double width;
        private double height;
    }

    private static final class Run {
        private final List<LayoutRect> rects = new ArrayList<>();
        private double width;
        private double height;
        private double baselineOffset;
    }
}
