package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.utils.RXMath;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableIntegerProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * JavaFX-native responsive row layout driven by the row's own width.
 *
 * <p><b>Despite the name, an {@code RXRow} is not a single horizontal
 * row.</b> It is a column-based responsive grid container (24 columns by the
 * default {@link RXBreakpointProfile#ANT_DESIGN} profile, 12 by
 * {@link RXBreakpointProfile#BOOTSTRAP}) that automatically wraps its child
 * {@link RXCol} instances onto multiple visual rows whenever their
 * combined spans exceed {@link #columnsProperty() columns}. The {@code Row}
 * naming follows the web convention shared by Element UI, Element Plus,
 * Bootstrap, Ant Design and similar frameworks, where a single {@code <el-row>}
 * / {@code <div class="row">} can wrap into many visual rows. Add columns
 * directly to {@link #getChildren()}; there is no separate "add a new row" step.</p>
 *
 * <p>Children are expected to be {@link RXCol} wrappers. Plain
 * {@link Node} children are accepted as full-width columns for convenience, but
 * only {@code RXCol} supports breakpoint-specific specs.</p>
 *
 * <p><b>{@code RXRow}/{@code RXCol} vs {@link RXBox}:</b> use {@code RXBox} for
 * a simple one-dimensional linear layout (a single horizontal or vertical run of
 * nodes, like {@code HBox}/{@code VBox}). Use {@code RXRow}/{@code RXCol} when
 * you need a breakpoint-driven 12/24-column responsive grid with span, offset,
 * order, hidden and automatic wrapping.</p>
 *
 * <p>The row's minimum width is measured with the profile's narrowest
 * breakpoint so shrink-wrapping parents can still allocate a width that lets
 * the row enter its narrow responsive layout. Wider breakpoint specs still
 * participate in preferred width calculation and in layout once the row is
 * allocated a width in those breakpoint ranges. If an intermediate breakpoint
 * assigns columns that are too narrow for their content minimum widths, that
 * content may still clip in that breakpoint range; adjust the column specs or
 * content minimum sizes to fit.</p>
 *
 * <p>Minimal example:</p>
 * <pre>{@code
 * RXRow row = new RXRow();
 * row.setGutter(16);
 * row.setRowGap(16);
 * for (int i = 0; i < 5; i++) {
 *     RXCol col = new RXCol();
 *     col.setXs(RXColSpec.of(24));   // narrow: 1 column per visual row
 *     col.setMd(RXColSpec.of(12));   // medium: 2 columns per visual row
 *     col.setLg(RXColSpec.of(6));    // wide:   4 columns per visual row
 *     col.getChildren().add(new Label("Card " + i));
 *     row.getChildren().add(col);
 * }
 * }</pre>
 */
public class RXRow extends Pane {

    // ==================== Constants ====================
    private static final String DEFAULT_STYLE_CLASS = "rx-row";

    /**
     * Default column count from the default Ant Design-style profile.
     */
    private static final int DEFAULT_COLUMNS = RXBreakpointProfile.ANT_DESIGN.getColumns();

    /**
     * Default horizontal gutter.
     */
    public static final double DEFAULT_GUTTER = 0.0;

    /**
     * Default vertical row gap.
     */
    private static final double DEFAULT_ROW_GAP = 0.0;

    private static final Logger LOGGER = Logger.getLogger(RXRow.class.getName());

    private final BreakpointSupport breakpointSupport = new BreakpointSupport();
    private final Map<Node, SpecWarningKey> coercedSpecWarnings = new IdentityHashMap<>();
    private final Map<RXCol, ResponsiveHiddenState> responsiveHiddenStates = new IdentityHashMap<>();
    private boolean columnsExplicitlySet;
    private boolean updatingColumnsFromProfile;

    // ==================== Constructors ====================

    /**
     * Creates an empty responsive row.
     */
    public RXRow() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        widthProperty().addListener(obs -> updateActiveBreakpoint(getWidth()));
        updateActiveBreakpoint(getWidth());
    }

    /**
     * Creates a responsive row with the given children.
     *
     * @param children the initial children
     */
    public RXRow(Node... children) {
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

    // ==================== Nested Types ====================

    /**
     * Horizontal distribution of remaining space for each responsive row line.
     *
     * <p>Column offsets are treated as occupied space, equivalent to fixed
     * flexbox margins. Justification distributes only the remaining free space
     * after spans and offsets are accounted for.</p>
     */
    public enum Justify {
        /**
         * Keep remaining space after the last column.
         */
        START,
        /**
         * Split remaining space before and after the line.
         */
        CENTER,
        /**
         * Place remaining space before the first column.
         */
        END,
        /**
         * Distribute remaining space between columns.
         */
        SPACE_BETWEEN,
        /**
         * Distribute remaining space around columns with half-size edge gaps.
         */
        SPACE_AROUND,
        /**
         * Distribute remaining space evenly between columns and both edges.
         */
        SPACE_EVENLY
    }

    /**
     * Vertical alignment of columns within a responsive row line.
     */
    public enum Align {
        /**
         * Align columns to the top of the line.
         */
        TOP,
        /**
         * Center columns vertically within the line.
         */
        CENTER,
        /**
         * Align columns to the bottom of the line.
         */
        BOTTOM,
        /**
         * Resize columns to the full line height.
         */
        STRETCH
    }

    // ==================== Columns ====================

    private final IntegerProperty columns = new StyleableIntegerProperty(DEFAULT_COLUMNS) {
        @Override
        protected void invalidated() {
            int value = get();
            if (value <= 0) {
                if (!isBound()) {
                    set(DEFAULT_COLUMNS);
                }
                throw new IllegalArgumentException("columns must be greater than zero");
            }
            if (!updatingColumnsFromProfile) {
                columnsExplicitlySet = true;
            }
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.COLUMNS;
        }

        @Override
        public Object getBean() {
            return RXRow.this;
        }

        @Override
        public String getName() {
            return "columns";
        }
    };

    /**
     * Total number of layout columns in the row. Until this property is
     * explicitly set or bound, it follows {@link #breakpointProfileProperty()}
     * when the profile changes.
     *
     * @return the columns property
     */
    public final IntegerProperty columnsProperty() {
        return columns;
    }

    /**
     * Returns the column count.
     *
     * @return the column count
     */
    public final int getColumns() {
        return columns.get();
    }

    /**
     * Sets the column count.
     *
     * @param value the column count
     * @throws IllegalArgumentException if {@code value <= 0}
     */
    public final void setColumns(int value) {
        columns.set(value);
    }

    // ==================== Gutter ====================

    private final DoubleProperty gutter = new StyleableDoubleProperty(DEFAULT_GUTTER) {
        @Override
        protected void invalidated() {
            requestLayout();
            requestColumnLayouts();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.GUTTER;
        }

        @Override
        public Object getBean() {
            return RXRow.this;
        }

        @Override
        public String getName() {
            return "gutter";
        }
    };

    /**
     * Horizontal space between adjacent column content areas. Each
     * {@link RXCol} receives half of this value on each side.
     *
     * @return the gutter property
     */
    public final DoubleProperty gutterProperty() {
        return gutter;
    }

    /**
     * Returns the horizontal gutter.
     *
     * @return the gutter
     */
    public final double getGutter() {
        return gutter.get();
    }

    /**
     * Sets the horizontal gutter.
     *
     * @param value the gutter
     */
    public final void setGutter(double value) {
        gutter.set(value);
    }

    // ==================== Row Gap ====================

    private final DoubleProperty rowGap = new StyleableDoubleProperty(DEFAULT_ROW_GAP) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.ROW_GAP;
        }

        @Override
        public Object getBean() {
            return RXRow.this;
        }

        @Override
        public String getName() {
            return "rowGap";
        }
    };

    /**
     * Vertical gap between wrapped lines.
     *
     * @return the row gap property
     */
    public final DoubleProperty rowGapProperty() {
        return rowGap;
    }

    /**
     * Returns the row gap.
     *
     * @return the row gap
     */
    public final double getRowGap() {
        return rowGap.get();
    }

    /**
     * Sets the row gap.
     *
     * @param value the row gap
     */
    public final void setRowGap(double value) {
        rowGap.set(value);
    }

    // ==================== Justify ====================

    private final ObjectProperty<Justify> justify =
            new StyleableObjectProperty<>(Justify.START) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, Justify> getCssMetaData() {
                    return StyleableProperties.JUSTIFY;
                }

                @Override
                public Object getBean() {
                    return RXRow.this;
                }

                @Override
                public String getName() {
                    return "justify";
                }
            };

    /**
     * Horizontal distribution of remaining space for each line. Column offsets
     * participate in the occupied line width, matching flexbox margin semantics;
     * justify does not cancel or redistribute an explicit offset. A {@code null}
     * value is not rejected; it resolves to the default at the use site.
     *
     * @return the justify property
     */
    public final ObjectProperty<Justify> justifyProperty() {
        return justify;
    }

    /**
     * Returns the row justification.
     *
     * @return the justification
     */
    public final Justify getJustify() {
        return justify.get();
    }

    /**
     * Sets the row justification.
     *
     * @param value the justification
     */
    public final void setJustify(Justify value) {
        justify.set(value);
    }

    // ==================== Align ====================

    private final ObjectProperty<Align> align =
            new StyleableObjectProperty<>(Align.TOP) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, Align> getCssMetaData() {
                    return StyleableProperties.ALIGN;
                }

                @Override
                public Object getBean() {
                    return RXRow.this;
                }

                @Override
                public String getName() {
                    return "align";
                }
            };

    /**
     * Vertical alignment of columns inside each wrapped line. A {@code null}
     * value is not rejected; it resolves to the default at the use site.
     *
     * @return the align property
     */
    public final ObjectProperty<Align> alignProperty() {
        return align;
    }

    /**
     * Returns the row alignment.
     *
     * @return the alignment
     */
    public final Align getAlign() {
        return align.get();
    }

    /**
     * Sets the row alignment.
     *
     * @param value the alignment
     */
    public final void setAlign(Align value) {
        align.set(value);
    }

    // ==================== RXBreakpoint Profile ====================

    private final ObjectProperty<RXBreakpointProfile> breakpointProfile =
            new SimpleObjectProperty<>(this, "breakpointProfile", RXBreakpointProfile.ANT_DESIGN) {
                @Override
                protected void invalidated() {
                    syncColumnsWithProfile(breakpointProfileOrDefault());
                    updateActiveBreakpoint(getWidth());
                    requestLayout();
                }
            };

    /**
     * RXBreakpoint profile used to resolve active breakpoint and mobile-first
     * column specs. Changing the profile also updates {@link #columnsProperty()}
     * while columns is still using the profile default. A {@code null} value is
     * not rejected; it resolves to the default at the use site.
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
     * @param value the breakpoint profile
     */
    public final void setBreakpointProfile(RXBreakpointProfile value) {
        breakpointProfile.set(value);
    }

    // ==================== Active RXBreakpoint ====================

    private final ReadOnlyObjectWrapper<RXBreakpoint> activeBreakpoint =
            new ReadOnlyObjectWrapper<>(this, "activeBreakpoint");

    /**
     * Current breakpoint resolved from the row's outer width.
     *
     * @return the read-only active breakpoint property
     */
    public final ReadOnlyObjectProperty<RXBreakpoint> activeBreakpointProperty() {
        return activeBreakpoint.getReadOnlyProperty();
    }

    /**
     * Returns the current active breakpoint.
     *
     * @return the active breakpoint
     */
    public final RXBreakpoint getActiveBreakpoint() {
        return activeBreakpoint.get();
    }

    private void setActiveBreakpoint(RXBreakpoint value) {
        activeBreakpoint.set(value);
    }

    // ==================== Layout ====================

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected double computeMinWidth(double height) {
        return computeMinimumWidth(height);
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected double computePrefWidth(double height) {
        return computePreferredWidth(height, false);
    }

    @Override
    protected double computePrefHeight(double width) {
        double measuredWidth = normalizeMeasurementWidth(width);
        Measurement measurement = measure(measuredWidth, resolveBreakpoint(measuredWidth), false);
        return getInsets().getTop() + snapSizeY(measurement.totalHeight()) + getInsets().getBottom();
    }

    @Override
    protected void layoutChildren() {
        double rowWidth = getWidth();
        RXBreakpoint breakpoint = getActiveBreakpoint();
        if (breakpoint == null) {
            breakpoint = resolveBreakpoint(rowWidth);
        }
        Measurement measurement = measure(rowWidth, breakpoint, true);
        Insets insets = getInsets();
        double contentX = snapPositionX(insets.getLeft());
        double contentY = snapPositionY(insets.getTop());
        double contentWidth = contentWidth(rowWidth);
        double y = contentY;
        double gap = snapSpaceY(rowGapOrDefault());
        Align rowAlign = alignOrDefault();
        Justify rowJustify = justifyOrDefault();
        int columnsCount = columnsOrDefault();

        for (int lineIndex = 0; lineIndex < measurement.lines().size(); lineIndex++) {
            Line line = measurement.lines().get(lineIndex);
            layoutLine(line, contentX, y, contentWidth, columnsCount,
                    rowJustify, rowAlign);
            y += line.height();
            if (lineIndex < measurement.lines().size() - 1) {
                y += gap;
            }
        }
    }

    private void layoutLine(Line line, double contentX, double y, double contentWidth,
                            int columnsCount, Justify rowJustify,
                            Align rowAlign) {
        JustifyMetrics justifyMetrics = justifyMetrics(rowJustify,
                Math.max(0.0, contentWidth - line.usedWidth()), line.items().size());
        VPos vpos = switch (rowAlign) {
            case TOP, STRETCH -> VPos.TOP;
            case CENTER -> VPos.CENTER;
            case BOTTOM -> VPos.BOTTOM;
        };
        boolean fillHeight = rowAlign == Align.STRETCH;

        for (int i = 0; i < line.items().size(); i++) {
            LineItem item = line.items().get(i);
            Node child = item.child();
            double itemOffset = justifyMetrics.edgeOffset() + i * justifyMetrics.itemGap();
            double rawX = contentX + justifyMetrics.lineOffset() + itemOffset
                    + contentWidth * item.startColumn() / columnsCount;
            double rawWidth = contentWidth * item.span() / columnsCount;
            double x = snapPositionX(rawX);
            double right = snapPositionX(rawX + rawWidth);
            double width = Math.max(0.0, right - x);

            if (item.span() == 0) {
                child.resizeRelocate(x, snapPositionY(y), 0.0, 0.0);
            } else {
                layoutInArea(child, x, y, width, line.height(), 0.0, null,
                        true, fillHeight, HPos.LEFT, vpos);
            }
        }
    }

    private Measurement measure(double rowWidth, RXBreakpoint breakpoint,
                                boolean applyHiddenState) {
        int columnsCount = columnsOrDefault();
        double contentWidth = contentWidth(rowWidth);
        double gutterValue = gutterOrDefault();
        List<Line> lines = new ArrayList<>();
        List<LineItem> items = new ArrayList<>();
        int cursor = 0;
        double lineHeight = 0.0;
        int maxEndColumn = 0;
        List<LayoutCandidate> candidates =
                collectLayoutCandidates(breakpoint, columnsCount, applyHiddenState);

        for (LayoutCandidate candidate : candidates) {
            Node child = candidate.child();
            EffectiveSpec spec = candidate.spec();
            int required = spec.offset() + spec.span();
            if (!items.isEmpty() && cursor + required > columnsCount) {
                lines.add(createLine(items, lineHeight, maxEndColumn, contentWidth, columnsCount));
                items = new ArrayList<>();
                cursor = 0;
                lineHeight = 0.0;
                maxEndColumn = 0;
            }

            int start = cursor + spec.offset();
            int end = start + spec.span();
            double outerWidth = contentWidth * spec.span() / columnsCount;
            double childHeight = spec.span() == 0
                    ? 0.0
                    : computeChildPrefHeight(child, outerWidth, gutterValue);
            items.add(new LineItem(child, start, spec.span()));
            cursor = end;
            maxEndColumn = Math.max(maxEndColumn, end);
            lineHeight = Math.max(lineHeight, childHeight);
        }

        if (!items.isEmpty()) {
            lines.add(createLine(items, lineHeight, maxEndColumn, contentWidth, columnsCount));
        }

        return new Measurement(lines, snapSpaceY(rowGapOrDefault()));
    }

    private List<LayoutCandidate> collectLayoutCandidates(RXBreakpoint breakpoint,
                                                          int columnsCount,
                                                          boolean applyHiddenState) {
        List<LayoutCandidate> candidates = new ArrayList<>();
        List<Node> children = getChildren();
        for (int i = 0; i < children.size(); i++) {
            Node child = children.get(i);
            EffectiveSpec spec = resolveSpec(child, breakpoint, columnsCount);
            if (child instanceof RXCol col) {
                if (applyHiddenState) {
                    updateResponsiveHidden(col, spec.hidden());
                }
                if (spec.hidden()) {
                    continue;
                }
            }
            if (!isEffectivelyManaged(child)) {
                continue;
            }
            candidates.add(new LayoutCandidate(child, spec, i));
        }
        if (applyHiddenState) {
            cleanupRemovedResponsiveHiddenStates(children);
        }
        candidates.sort(Comparator
                .comparingInt((LayoutCandidate candidate) -> candidate.spec().order())
                .thenComparingInt(LayoutCandidate::index));
        return candidates;
    }

    private boolean isEffectivelyManaged(Node child) {
        if (child instanceof RXCol col) {
            ResponsiveHiddenState hiddenState = responsiveHiddenStates.get(col);
            if (hiddenState != null) {
                return hiddenState.managedBefore();
            }
        }
        return child.isManaged();
    }

    private void updateResponsiveHidden(RXCol col, boolean hidden) {
        if (hidden) {
            ResponsiveHiddenState hiddenState = responsiveHiddenStates.get(col);
            if (hiddenState == null) {
                hiddenState = new ResponsiveHiddenState(col.isVisible(), col.isManaged());
                responsiveHiddenStates.put(col, hiddenState);
            }
            applyResponsiveHidden(col, hiddenState);
        } else {
            restoreResponsiveHidden(col);
        }
    }

    private void applyResponsiveHidden(RXCol col, ResponsiveHiddenState hiddenState) {
        if (col.visibleProperty().isBound()) {
            warnResponsiveHiddenBinding(col, "visible", hiddenState);
        } else if (col.isVisible()) {
            col.setVisible(false);
        }
        if (col.managedProperty().isBound()) {
            warnResponsiveHiddenBinding(col, "managed", hiddenState);
        } else if (col.isManaged()) {
            col.setManaged(false);
        }
        col.resizeRelocate(0.0, 0.0, 0.0, 0.0);
    }

    private void restoreResponsiveHidden(RXCol col) {
        ResponsiveHiddenState hiddenState = responsiveHiddenStates.remove(col);
        if (hiddenState == null) {
            return;
        }
        if (col.visibleProperty().isBound()) {
            warnResponsiveHiddenBinding(col, "visible", hiddenState);
        } else {
            col.setVisible(hiddenState.visibleBefore());
        }
        if (col.managedProperty().isBound()) {
            warnResponsiveHiddenBinding(col, "managed", hiddenState);
        } else {
            col.setManaged(hiddenState.managedBefore());
        }
    }

    private void cleanupRemovedResponsiveHiddenStates(List<Node> children) {
        if (responsiveHiddenStates.isEmpty()) {
            return;
        }
        List<RXCol> removed = new ArrayList<>();
        for (RXCol col : responsiveHiddenStates.keySet()) {
            if (!children.contains(col)) {
                removed.add(col);
            }
        }
        for (RXCol col : removed) {
            restoreResponsiveHidden(col);
        }
    }

    private void warnResponsiveHiddenBinding(RXCol col, String propertyName,
                                             ResponsiveHiddenState hiddenState) {
        if ("visible".equals(propertyName)) {
            if (hiddenState.visibleWarningLogged()) {
                return;
            }
            hiddenState.setVisibleWarningLogged(true);
        } else {
            if (hiddenState.managedWarningLogged()) {
                return;
            }
            hiddenState.setManagedWarningLogged(true);
        }
        LOGGER.warning("Responsive hidden could not update bound "
                + propertyName + "Property on " + col
                + "; layout still skips the column.");
    }

    private Line createLine(List<LineItem> items, double lineHeight, int maxEndColumn,
                            double contentWidth, int columnsCount) {
        double usedWidth = contentWidth * maxEndColumn / columnsCount;
        return new Line(List.copyOf(items), snapSizeY(lineHeight), usedWidth);
    }

    private EffectiveSpec resolveSpec(Node child, RXBreakpoint breakpoint, int columnsCount) {
        int span = child instanceof RXCol col ? col.getSpan() : columnsCount;
        int offset = child instanceof RXCol col ? col.getOffset() : 0;
        int order = child instanceof RXCol col
                ? col.getOrder()
                : RXCol.DEFAULT_ORDER;
        boolean hidden = child instanceof RXCol col && col.isHidden();
        if (child instanceof RXCol col) {
            RXBreakpointProfile profile = breakpointProfileOrDefault();
            double activeMinWidth = breakpoint == null ? 0.0 : profile.minWidthOf(breakpoint);
            for (RXBreakpoint profileBreakpoint : profile.getBreakpoints()) {
                if (profile.minWidthOf(profileBreakpoint) > activeMinWidth) {
                    break;
                }
                RXColSpec spec = col.getSpec(profileBreakpoint);
                if (spec != null) {
                    if (spec.getSpan() != null) {
                        span = spec.getSpan();
                    }
                    if (spec.getOffset() != null) {
                        offset = spec.getOffset();
                    }
                    if (spec.getOrder() != null) {
                        order = spec.getOrder();
                    }
                    if (spec.getHidden() != null) {
                        hidden = spec.getHidden();
                    }
                }
            }
        }
        return coerceSpec(child, span, offset, order, hidden, columnsCount);
    }

    private EffectiveSpec coerceSpec(Node child, int span, int offset, int order,
                                     boolean hidden, int columnsCount) {
        int coercedOffset = RXMath.clamp(offset, 0, columnsCount);
        int maxSpan = columnsCount - coercedOffset;
        int coercedSpan = RXMath.clamp(span, 0, maxSpan);
        if (shouldWarnSpecCoercion(span, offset, coercedSpan, coercedOffset, columnsCount)) {
            SpecWarningKey warningKey = new SpecWarningKey(span, offset, columnsCount);
            if (!warningKey.equals(coercedSpecWarnings.put(child, warningKey))) {
                LOGGER.warning("Responsive column spec exceeds row columns and was clamped: span="
                        + span + ", offset=" + offset + ", columns=" + columnsCount);
            }
        } else {
            coercedSpecWarnings.remove(child);
        }
        return new EffectiveSpec(coercedSpan, coercedOffset, order, hidden);
    }

    private boolean shouldWarnSpecCoercion(int span, int offset, int coercedSpan,
                                           int coercedOffset, int columnsCount) {
        if (coercedSpan == span && coercedOffset == offset) {
            return false;
        }
        return span != RXCol.DEFAULT_SPAN || offset != 0 || coercedSpan != columnsCount;
    }

    private double computeChildPrefHeight(Node child, double width, double gutterValue) {
        double prefHeight = child instanceof RXCol col
                ? col.computeResponsivePrefHeight(width, gutterValue)
                : child.prefHeight(width);
        if (!child.isResizable()) {
            return snapSizeY(prefHeight);
        }
        return snapSizeY(RXMath.clamp(prefHeight, child.minHeight(width), child.maxHeight(width)));
    }

    private double computeChildPrefWidth(Node child, double height, double gutterValue) {
        double prefWidth = child instanceof RXCol col
                ? col.computeResponsivePrefWidth(height, gutterValue)
                : child.prefWidth(height);
        if (!child.isResizable()) {
            return snapSizeX(prefWidth);
        }
        return snapSizeX(RXMath.clamp(prefWidth, child.minWidth(height), child.maxWidth(height)));
    }

    private double computePreferredWidth(double height, boolean min) {
        return computePreferredWidth(height, min, breakpointProfileOrDefault().getBreakpoints());
    }

    private double computeMinimumWidth(double height) {
        RXBreakpointProfile profile = breakpointProfileOrDefault();
        return computePreferredWidth(height, true,
                Collections.singletonList(profile.getBreakpoints().get(0)));
    }

    private double computePreferredWidth(double height, boolean min,
                                         List<RXBreakpoint> breakpoints) {
        Insets insets = getInsets();
        int columnsCount = columnsOrDefault();
        double gutterValue = gutterOrDefault();
        double contentWidth = 0.0;

        for (RXBreakpoint breakpoint : breakpoints) {
            List<LayoutCandidate> candidates =
                    collectLayoutCandidates(breakpoint, columnsCount, false);
            for (LayoutCandidate candidate : candidates) {
                Node child = candidate.child();
                EffectiveSpec spec = candidate.spec();
                if (spec.span() == 0) {
                    continue;
                }
                double childWidth = min ? computeChildMinWidth(child, height, gutterValue)
                        : computeChildPrefWidth(child, height, gutterValue);
                contentWidth = Math.max(contentWidth, childWidth * columnsCount / spec.span());
            }
        }
        return insets.getLeft() + snapSizeX(contentWidth) + insets.getRight();
    }

    private double computeChildMinWidth(Node child, double height, double gutterValue) {
        double minWidth = child instanceof RXCol col
                ? col.computeResponsiveMinWidth(height, gutterValue)
                : child.minWidth(height);
        return snapSizeX(minWidth);
    }

    private void updateActiveBreakpoint(double width) {
        RXBreakpoint next = breakpointSupport.update(breakpointProfileOrDefault(), width,
                this::pseudoClassStateChanged);
        if (Objects.equals(next, getActiveBreakpoint())) {
            return;
        }
        setActiveBreakpoint(next);
        requestLayout();
    }

    private RXBreakpoint resolveBreakpoint(double width) {
        return breakpointProfileOrDefault().resolve(width);
    }

    private double normalizeMeasurementWidth(double width) {
        if (width >= 0.0) {
            return width;
        }
        double currentWidth = getWidth();
        if (currentWidth > 0.0) {
            return currentWidth;
        }
        return computePrefWidth(-1.0);
    }

    private double contentWidth(double rowWidth) {
        Insets insets = getInsets();
        return Math.max(0.0, rowWidth - insets.getLeft() - insets.getRight());
    }

    private int columnsOrDefault() {
        int value = getColumns();
        return value > 0 ? value : breakpointProfileOrDefault().getColumns();
    }

    private double gutterOrDefault() {
        double value = getGutter();
        return Double.isFinite(value) ? value : DEFAULT_GUTTER;
    }

    private void requestColumnLayouts() {
        for (Node child : getChildren()) {
            if (child instanceof RXCol col) {
                col.requestLayout();
            }
        }
    }

    private double rowGapOrDefault() {
        double value = getRowGap();
        return Double.isFinite(value) ? value : DEFAULT_ROW_GAP;
    }

    private Justify justifyOrDefault() {
        Justify value = getJustify();
        return value == null ? Justify.START : value;
    }

    private Align alignOrDefault() {
        Align value = getAlign();
        return value == null ? Align.TOP : value;
    }

    private RXBreakpointProfile breakpointProfileOrDefault() {
        RXBreakpointProfile value = getBreakpointProfile();
        return value == null ? RXBreakpointProfile.ANT_DESIGN : value;
    }

    private void syncColumnsWithProfile(RXBreakpointProfile profile) {
        if (columnsExplicitlySet || columns.isBound()) {
            return;
        }
        int profileColumns = profile.getColumns();
        if (getColumns() == profileColumns) {
            return;
        }
        updatingColumnsFromProfile = true;
        try {
            columns.set(profileColumns);
        } finally {
            updatingColumnsFromProfile = false;
        }
    }

    private JustifyMetrics justifyMetrics(Justify rowJustify, double remaining, int itemCount) {
        if (itemCount <= 0 || remaining <= 0.0) {
            return new JustifyMetrics(0.0, 0.0, 0.0);
        }
        return switch (rowJustify) {
            case START -> new JustifyMetrics(0.0, 0.0, 0.0);
            case CENTER -> new JustifyMetrics(remaining / 2.0, 0.0, 0.0);
            case END -> new JustifyMetrics(remaining, 0.0, 0.0);
            case SPACE_BETWEEN -> itemCount == 1
                    ? new JustifyMetrics(0.0, 0.0, 0.0)
                    : new JustifyMetrics(0.0, 0.0, remaining / (itemCount - 1));
            case SPACE_AROUND -> {
                double gap = remaining / itemCount;
                yield new JustifyMetrics(0.0, gap / 2.0, gap);
            }
            case SPACE_EVENLY -> {
                double gap = remaining / (itemCount + 1);
                yield new JustifyMetrics(0.0, gap, gap);
            }
        };
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {
        private static final CssMetaData<RXRow, Number> COLUMNS =
                new CssMetaData<>("-rx-columns", SizeConverter.getInstance(), DEFAULT_COLUMNS) {
                    @Override
                    public boolean isSettable(RXRow row) {
                        return !row.columns.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXRow row) {
                        return (StyleableProperty<Number>) row.columnsProperty();
                    }
                };

        private static final CssMetaData<RXRow, Number> GUTTER =
                new CssMetaData<>("-rx-gutter", SizeConverter.getInstance(), DEFAULT_GUTTER) {
                    @Override
                    public boolean isSettable(RXRow row) {
                        return !row.gutter.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXRow row) {
                        return (StyleableProperty<Number>) row.gutterProperty();
                    }
                };

        private static final CssMetaData<RXRow, Number> ROW_GAP =
                new CssMetaData<>("-rx-row-gap", SizeConverter.getInstance(), DEFAULT_ROW_GAP) {
                    @Override
                    public boolean isSettable(RXRow row) {
                        return !row.rowGap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXRow row) {
                        return (StyleableProperty<Number>) row.rowGapProperty();
                    }
                };

        private static final CssMetaData<RXRow, Justify> JUSTIFY =
                new CssMetaData<>("-rx-justify",
                        new EnumConverter<>(Justify.class), Justify.START) {
                    @Override
                    public boolean isSettable(RXRow row) {
                        return !row.justify.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Justify> getStyleableProperty(RXRow row) {
                        return (StyleableProperty<Justify>) row.justifyProperty();
                    }
                };

        private static final CssMetaData<RXRow, Align> ALIGN =
                new CssMetaData<>("-rx-align",
                        new EnumConverter<>(Align.class), Align.TOP) {
                    @Override
                    public boolean isSettable(RXRow row) {
                        return !row.align.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Align> getStyleableProperty(RXRow row) {
                        return (StyleableProperty<Align>) row.alignProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Pane.getClassCssMetaData());
            styleables.add(COLUMNS);
            styleables.add(GUTTER);
            styleables.add(ROW_GAP);
            styleables.add(JUSTIFY);
            styleables.add(ALIGN);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata list
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

    private record EffectiveSpec(int span, int offset, int order, boolean hidden) {
    }

    private record SpecWarningKey(int span, int offset, int columns) {
    }

    private record LayoutCandidate(Node child, EffectiveSpec spec, int index) {
    }

    private record LineItem(Node child, int startColumn, int span) {
    }

    private record Line(List<LineItem> items, double height, double usedWidth) {
    }

    private record Measurement(List<Line> lines, double rowGap) {
        private double totalHeight() {
            if (lines.isEmpty()) {
                return 0.0;
            }
            double total = 0.0;
            for (int i = 0; i < lines.size(); i++) {
                total += lines.get(i).height();
                if (i < lines.size() - 1) {
                    total += rowGap;
                }
            }
            return total;
        }
    }

    private record JustifyMetrics(double lineOffset, double edgeOffset, double itemGap) {
    }

    private static final class ResponsiveHiddenState {
        private final boolean visibleBefore;
        private final boolean managedBefore;
        private boolean visibleWarningLogged;
        private boolean managedWarningLogged;

        private ResponsiveHiddenState(boolean visibleBefore, boolean managedBefore) {
            this.visibleBefore = visibleBefore;
            this.managedBefore = managedBefore;
        }

        private boolean visibleBefore() {
            return visibleBefore;
        }

        private boolean managedBefore() {
            return managedBefore;
        }

        private boolean visibleWarningLogged() {
            return visibleWarningLogged;
        }

        private void setVisibleWarningLogged(boolean value) {
            visibleWarningLogged = value;
        }

        private boolean managedWarningLogged() {
            return managedWarningLogged;
        }

        private void setManagedWarningLogged(boolean value) {
            managedWarningLogged = value;
        }
    }
}
