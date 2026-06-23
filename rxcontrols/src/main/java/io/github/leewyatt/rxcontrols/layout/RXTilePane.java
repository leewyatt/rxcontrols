package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import javafx.animation.Interpolator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A responsive, animated tile layout pane — the node-based sibling of
 * {@code RXTileView}. It lays out its (managed) children in a uniform grid whose
 * column count is derived from {@link #cellWidthProperty() cellWidth} and the
 * available width, exactly like {@code RXTileView}, but it is a plain layout
 * {@link Pane}: not data-driven, not virtualized, with no sections or selection.
 * It is to a tile grid what {@code RXFlowPane} is to a wrapping flow.
 *
 * <p>Children are placed left-to-right, top-to-bottom into cells whose normal
 * target size is {@code cellWidth} × {@link #cellHeightProperty() cellHeight},
 * separated by {@link #hgapProperty() hgap} / {@link #vgapProperty() vgap};
 * spare row width is distributed per {@link #itemsJustifyProperty()
 * itemsJustify}. Non-stretch rows keep their target cell width while space
 * permits, while {@link ItemsJustify#STRETCH} grows cells to fill spare row
 * width. If the available row width is narrower than the target row width, all
 * modes shrink cells and gaps for that pass so the row remains horizontally
 * bounded. Applications that require one full target-width column to remain
 * visible can set an explicit minimum width on the pane. A resizable child fills
 * its cell (bounded by its own max size); a non-resizable child is centered in it.
 * When {@link #animatedProperty() animated} is on, children glide to their new
 * positions as the column count changes.
 */
public class RXTilePane extends Pane {

    // ==================== Constants ====================

    private static final double DEFAULT_CELL_WIDTH = 100.0;
    private static final double DEFAULT_CELL_HEIGHT = 100.0;
    private static final double DEFAULT_HGAP = 10.0;
    private static final double DEFAULT_VGAP = 10.0;
    private static final double DEFAULT_MAX_CELL_WIDTH = 0.0;
    private static final int DEFAULT_MAX_COLUMNS = 0;
    private static final ItemsJustify DEFAULT_ITEMS_JUSTIFY = ItemsJustify.START;
    private static final boolean DEFAULT_ANIMATED = false;
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);
    private static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    // Columns used by computePrefWidth when laid out without a width constraint.
    private static final int DEFAULT_PREF_COLUMNS = 3;
    // Defensive ceiling so a tiny cellWidth cannot explode the column count.
    private static final int MAX_RESOLVED_COLUMNS = 4096;

    private static final String DEFAULT_STYLE_CLASS = "rx-tile-pane";

    // ==================== Animation state ====================

    // Reuses the same FLIP relayout animator as RXMasonryPane (same package). All
    // children are real and persistent, so no recycler pin-set is needed.
    private final MasonryAnimator animator = new MasonryAnimator();
    private boolean firstLayoutDone;
    // Children added after the first layout: they snap into their slot rather than
    // gliding in from the pane origin (no enter animation in V1).
    private final Set<Node> enteringNodes = new HashSet<>();

    private final ListChangeListener<Node> childrenListener = change -> {
        while (change.next()) {
            if (change.wasAdded() && firstLayoutDone) {
                enteringNodes.addAll(change.getAddedSubList());
            }
            if (change.wasRemoved()) {
                for (Node removed : change.getRemoved()) {
                    enteringNodes.remove(removed);
                    animator.forget(removed);
                }
            }
        }
    };

    // ==================== Constructors ====================

    /**
     * Creates an empty tile pane.
     */
    public RXTilePane() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        getChildren().addListener(childrenListener);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                animator.stopAll();
            }
        });
    }

    /**
     * Creates a tile pane with the given children.
     *
     * @param children the initial children
     */
    public RXTilePane(Node... children) {
        this();
        getChildren().addAll(children);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the rxcontrols user-agent stylesheet so this pane's
     * {@code -rx-*} tokens resolve without any application-level install.
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Cell Width ====================

    private final DoubleProperty cellWidth = new StyleableDoubleProperty(DEFAULT_CELL_WIDTH) {
        @Override
        protected void invalidated() {
            double value = get();
            if (!Double.isFinite(value) || value <= 0.0) {
                if (!isBound()) {
                    set(DEFAULT_CELL_WIDTH);
                }
                throw new IllegalArgumentException("cellWidth must be a finite positive number");
            }
            requestLayout();
        }

        @Override
        public CssMetaData<RXTilePane, Number> getCssMetaData() {
            return StyleableProperties.CELL_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXTilePane.this;
        }

        @Override
        public String getName() {
            return "cellWidth";
        }
    };

    /**
     * Target width of each cell, in pixels; drives the automatic column count and
     * preferred width, but is not the pane's default minimum width. Must be a
     * finite positive number — an illegal value is rejected with
     * {@link IllegalArgumentException} and coerced back to the default (unless bound).
     *
     * @return the cell-width property
     */
    public final DoubleProperty cellWidthProperty() {
        return cellWidth;
    }

    /**
     * Returns the cell width.
     *
     * @return the cell width
     */
    public final double getCellWidth() {
        return cellWidth.get();
    }

    /**
     * Sets the cell width.
     *
     * @param value a finite positive width
     */
    public final void setCellWidth(double value) {
        cellWidth.set(value);
    }

    // ==================== Cell Height ====================

    private final DoubleProperty cellHeight = new StyleableDoubleProperty(DEFAULT_CELL_HEIGHT) {
        @Override
        protected void invalidated() {
            double value = get();
            if (!Double.isFinite(value) || value <= 0.0) {
                if (!isBound()) {
                    set(DEFAULT_CELL_HEIGHT);
                }
                throw new IllegalArgumentException("cellHeight must be a finite positive number");
            }
            requestLayout();
        }

        @Override
        public CssMetaData<RXTilePane, Number> getCssMetaData() {
            return StyleableProperties.CELL_HEIGHT;
        }

        @Override
        public Object getBean() {
            return RXTilePane.this;
        }

        @Override
        public String getName() {
            return "cellHeight";
        }
    };

    /**
     * Height of each cell, in pixels. Must be a finite positive number — an illegal
     * value is rejected and coerced back to the default (unless bound).
     *
     * @return the cell-height property
     */
    public final DoubleProperty cellHeightProperty() {
        return cellHeight;
    }

    /**
     * Returns the cell height.
     *
     * @return the cell height
     */
    public final double getCellHeight() {
        return cellHeight.get();
    }

    /**
     * Sets the cell height.
     *
     * @param value a finite positive height
     */
    public final void setCellHeight(double value) {
        cellHeight.set(value);
    }

    // ==================== Max Cell Width ====================

    private final DoubleProperty maxCellWidth = new StyleableDoubleProperty(DEFAULT_MAX_CELL_WIDTH) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<RXTilePane, Number> getCssMetaData() {
            return StyleableProperties.MAX_CELL_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXTilePane.this;
        }

        @Override
        public String getName() {
            return "maxCellWidth";
        }
    };

    /**
     * Upper bound on how wide a cell may grow when
     * {@link #itemsJustifyProperty() itemsJustify} is
     * {@link ItemsJustify#STRETCH}. {@code 0} (the default) or any non-positive
     * value means unbounded. Has no effect in the other justification modes,
     * where cells normally keep the target {@link #cellWidthProperty() cellWidth}
     * while space permits.
     *
     * <p>A cap smaller than {@code cellWidth} is degenerate
     * ({@code max < min}) and is treated as {@code cellWidth}; the cap itself
     * never shrinks cells below their target width. Any justification mode may
     * still shrink cells when the available row width is narrower than the target
     * row width.
     *
     * @return the max-cell-width property
     */
    public final DoubleProperty maxCellWidthProperty() {
        return maxCellWidth;
    }

    /**
     * Returns the maximum cell width used in {@link ItemsJustify#STRETCH} mode.
     *
     * @return the maximum cell width, or {@code 0} for unbounded
     */
    public final double getMaxCellWidth() {
        return maxCellWidth.get();
    }

    /**
     * Sets the maximum cell width used in {@link ItemsJustify#STRETCH} mode.
     *
     * @param value a positive cap, or {@code 0} (or any non-positive value) for
     *              unbounded
     */
    public final void setMaxCellWidth(double value) {
        maxCellWidth.set(value);
    }

    // ==================== Hgap ====================

    private final DoubleProperty hgap = new StyleableDoubleProperty(DEFAULT_HGAP) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<RXTilePane, Number> getCssMetaData() {
            return StyleableProperties.HGAP;
        }

        @Override
        public Object getBean() {
            return RXTilePane.this;
        }

        @Override
        public String getName() {
            return "hgap";
        }
    };

    /**
     * Horizontal gap between cells. A negative or non-finite value is treated as
     * zero at layout time rather than rejected.
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
     * @param value the gap
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
        public CssMetaData<RXTilePane, Number> getCssMetaData() {
            return StyleableProperties.VGAP;
        }

        @Override
        public Object getBean() {
            return RXTilePane.this;
        }

        @Override
        public String getName() {
            return "vgap";
        }
    };

    /**
     * Vertical gap between rows. A negative or non-finite value is treated as zero
     * at layout time rather than rejected.
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
     * @param value the gap
     */
    public final void setVgap(double value) {
        vgap.set(value);
    }

    // ==================== Max Columns ====================

    private final IntegerProperty maxColumns = new SimpleIntegerProperty(this, "maxColumns", DEFAULT_MAX_COLUMNS) {
        @Override
        protected void invalidated() {
            requestLayout();
        }
    };

    /**
     * Upper bound on the resolved column count. {@code 0} (or any non-positive
     * value) means unbounded.
     *
     * @return the max-columns property
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
     * @param value a positive bound, or {@code 0} for unbounded
     */
    public final void setMaxColumns(int value) {
        maxColumns.set(value);
    }

    // ==================== Items Justify ====================

    private final ObjectProperty<ItemsJustify> itemsJustify =
            new StyleableObjectProperty<>(DEFAULT_ITEMS_JUSTIFY) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<RXTilePane, ItemsJustify> getCssMetaData() {
                    return StyleableProperties.ITEMS_JUSTIFY;
                }

                @Override
                public Object getBean() {
                    return RXTilePane.this;
                }

                @Override
                public String getName() {
                    return "itemsJustify";
                }
            };

    /**
     * How a row uses its spare horizontal width: position the target-width block
     * ({@code START} / {@code CENTER} / {@code END}), grow the gaps
     * ({@code SPACE_BETWEEN} / {@code SPACE_AROUND} / {@code SPACE_EVENLY}) or
     * grow the cells ({@link ItemsJustify#STRETCH}, capped by
     * {@link #maxCellWidthProperty() maxCellWidth}). A {@code null} value is
     * treated as {@link ItemsJustify#START}. {@code cellWidth} is the target
     * track width used for deriving columns and preferred size. When the row is
     * narrower than its target width, all modes shrink cells for that layout
     * pass; when the row has spare width, only {@code STRETCH} grows cells.
     *
     * @return the items-justify property
     */
    public final ObjectProperty<ItemsJustify> itemsJustifyProperty() {
        return itemsJustify;
    }

    /**
     * Returns the items justification.
     *
     * @return the items justification
     */
    public final ItemsJustify getItemsJustify() {
        return itemsJustify.get();
    }

    /**
     * Sets the items justification.
     *
     * @param value the justification, or {@code null} for the default
     */
    public final void setItemsJustify(ItemsJustify value) {
        itemsJustify.set(value);
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
        public CssMetaData<RXTilePane, Boolean> getCssMetaData() {
            return StyleableProperties.ANIMATED;
        }

        @Override
        public Object getBean() {
            return RXTilePane.this;
        }

        @Override
        public String getName() {
            return "animated";
        }
    };

    /**
     * Whether children glide to their new positions when a change in the column
     * count reflows the grid. Off by default; turning it off mid-flight snaps every
     * child to its final position.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether reorder animation is enabled.
     *
     * @return whether reorder animation is enabled
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether reorder animation is enabled.
     *
     * @param value whether reorder animation is enabled
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
                        animator.stopAll();
                    }
                }

                @Override
                public CssMetaData<RXTilePane, Duration> getCssMetaData() {
                    return StyleableProperties.ANIMATION_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXTilePane.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of a single reorder glide. A {@code null}, non-positive, unknown or
     * indefinite value is accepted and disables animation, like {@code animated=false}.
     *
     * @return the animation-duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the reorder-animation duration.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the reorder-animation duration.
     *
     * @param value the duration; {@code null} or any non-positive value disables animation
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Animation Interpolator ====================

    private final ObjectProperty<Interpolator> animationInterpolator =
            new SimpleObjectProperty<>(this, "animationInterpolator", DEFAULT_ANIMATION_INTERPOLATOR);

    /**
     * Interpolator for the reorder glide. {@code null} falls back to
     * {@link Interpolator#EASE_BOTH}. Not styleable (no stable CSS converter).
     *
     * @return the animation-interpolator property
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
     * @param value the interpolator, or {@code null} for the default
     */
    public final void setAnimationInterpolator(Interpolator value) {
        animationInterpolator.set(value);
    }

    // ==================== Actual Column Count (read-only) ====================

    private final ReadOnlyIntegerWrapper actualColumnCount =
            new ReadOnlyIntegerWrapper(this, "actualColumnCount", 0);

    /**
     * The column count resolved on the last layout pass.
     *
     * @return the read-only actual-column-count property
     */
    public final ReadOnlyIntegerProperty actualColumnCountProperty() {
        return actualColumnCount.getReadOnlyProperty();
    }

    /**
     * Returns the resolved column count from the last layout pass.
     *
     * @return the actual column count
     */
    public final int getActualColumnCount() {
        return actualColumnCount.get();
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@link Orientation#HORIZONTAL}: the pane's height depends on its
     * width (more width yields more columns and fewer rows).
     */
    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected double computeMinWidth(double height) {
        return snappedLeftInset() + snappedRightInset();
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected double computePrefWidth(double height) {
        int columns = prefWidthColumns();
        double cell = snapSizeX(cellWidthOrDefault());
        double gap = snapSpaceX(gapOrZero(getHgap()));
        double content = rowWidth(columns, cell, gap);
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
        int columns = computeColumns(contentWidth);
        int count = getManagedChildren().size();
        int rows = count == 0 ? 0 : (count + columns - 1) / columns;
        double cellH = snapSizeY(cellHeightOrDefault());
        double vgapValue = snapSpaceY(gapOrZero(getVgap()));
        double content = rows == 0 ? 0.0 : rows * cellH + (rows - 1) * vgapValue;
        return snappedTopInset() + snapSizeY(content) + snappedBottomInset();
    }

    @Override
    protected void layoutChildren() {
        double left = snappedLeftInset();
        double top = snappedTopInset();
        double contentWidth = Math.max(0.0, getWidth() - left - snappedRightInset());

        int columns = computeColumns(contentWidth);
        if (actualColumnCount.get() != columns) {
            actualColumnCount.set(columns);
        }

        List<Node> managed = getManagedChildren();
        double hgapValue = snapSpaceX(gapOrZero(getHgap()));
        double vgapValue = snapSpaceY(gapOrZero(getVgap()));
        double cellH = snapSizeY(cellHeightOrDefault());

        double cellW;
        double effectiveHgap;
        double startX;
        ItemsJustify mode = justifyOrDefault(getItemsJustify());
        double baseWidth = snapSizeX(cellWidthOrDefault());
        double preferredRowWidth = columns * baseWidth + (columns - 1) * hgapValue;
        if (preferredRowWidth > contentWidth) {
            double scale = preferredRowWidth <= 0.0 ? 0.0 : Math.max(0.0, contentWidth) / preferredRowWidth;
            cellW = baseWidth * scale;
            effectiveHgap = hgapValue * scale;
            startX = 0.0;
        } else if (mode == ItemsJustify.STRETCH) {
            double ideal = (contentWidth - (columns - 1) * hgapValue) / columns;
            double cap = maxCellWidthOrUnbounded();
            double effectiveCap = cap > 0.0 ? Math.max(snapSizeX(cap), baseWidth) : 0.0;
            effectiveHgap = hgapValue;
            if (effectiveCap > 0.0 && ideal > effectiveCap) {
                cellW = effectiveCap;
                double used = columns * cellW + (columns - 1) * hgapValue;
                startX = Math.max(0.0, (contentWidth - used) / 2.0);
            } else {
                cellW = snapSizeX(Math.max(0.0, ideal));
                startX = 0.0;
            }
        } else {
            effectiveHgap = hgapValue;
            startX = 0.0;
            cellW = baseWidth;
            double slack = Math.max(0.0, contentWidth - preferredRowWidth);
            switch (mode) {
                case CENTER -> startX = slack / 2.0;
                case END -> startX = slack;
                case SPACE_BETWEEN -> effectiveHgap = hgapValue + (columns > 1 ? slack / (columns - 1) : 0.0);
                case SPACE_AROUND -> {
                    effectiveHgap = hgapValue + slack / columns;
                    startX = slack / (2.0 * columns);
                }
                case SPACE_EVENLY -> {
                    effectiveHgap = hgapValue + slack / (columns + 1);
                    startX = slack / (columns + 1);
                }
                default -> {
                    // START: the block hugs the leading edge (defaults stand).
                }
            }
        }

        boolean animate = isAnimated() && firstLayoutDone && getScene() != null && isAnimationDurationPositive();
        List<MasonryAnimator.Move> moves = new ArrayList<>(managed.size());
        for (int i = 0; i < managed.size(); i++) {
            Node child = managed.get(i);
            int column = i % columns;
            int row = i / columns;
            double x = left + snapPositionX(startX + column * (cellW + effectiveHgap));
            double y = top + snapPositionY(row * (cellH + vgapValue));
            // FLIP: capture the current on-screen position before relocating so the
            // animator can invert the move and tween translate back to zero. An
            // entering child has no meaningful previous position, so it snaps in.
            double oldVisualX = child.getLayoutX() + child.getTranslateX();
            double oldVisualY = child.getLayoutY() + child.getTranslateY();
            layoutInArea(child, x, y, cellW, cellH, -1.0, HPos.CENTER, VPos.CENTER);
            double fromDx = enteringNodes.contains(child) ? 0.0 : oldVisualX - child.getLayoutX();
            double fromDy = enteringNodes.contains(child) ? 0.0 : oldVisualY - child.getLayoutY();
            moves.add(new MasonryAnimator.Move(child, fromDx, fromDy, false));
        }
        animator.runRelayout(moves, animate, getAnimationDuration(), interpolatorOrDefault());
        enteringNodes.clear();
        firstLayoutDone = true;
    }

    // ==================== Helpers ====================

    private int computeColumns(double availableWidth) {
        double track = snapSizeX(cellWidthOrDefault());
        double gap = snapSpaceX(gapOrZero(getHgap()));
        int columns = (availableWidth <= 0.0 || track <= 0.0)
                ? 1
                : (int) Math.floor((availableWidth + gap) / (track + gap));
        columns = Math.max(1, columns);
        int max = getMaxColumns();
        if (max > 0 && columns > max) {
            columns = max;
        }
        return Math.min(columns, MAX_RESOLVED_COLUMNS);
    }

    private int prefWidthColumns() {
        return capColumns(DEFAULT_PREF_COLUMNS);
    }

    private int capColumns(int columns) {
        int capped = Math.max(1, columns);
        int max = getMaxColumns();
        if (max > 0 && capped > max) {
            capped = max;
        }
        return Math.min(capped, MAX_RESOLVED_COLUMNS);
    }

    private static double rowWidth(int columns, double cellWidth, double hgap) {
        return columns * cellWidth + (columns - 1) * hgap;
    }

    // Falls back to the default for a bound illegal value (coerce+throw cannot reset
    // a bound property), so layout math always sees a finite positive cell size.
    private double cellWidthOrDefault() {
        double value = getCellWidth();
        return Double.isFinite(value) && value > 0.0 ? value : DEFAULT_CELL_WIDTH;
    }

    private double cellHeightOrDefault() {
        double value = getCellHeight();
        return Double.isFinite(value) && value > 0.0 ? value : DEFAULT_CELL_HEIGHT;
    }

    private double maxCellWidthOrUnbounded() {
        double value = getMaxCellWidth();
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
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

    private static double gapOrZero(double gap) {
        return Double.isFinite(gap) ? Math.max(0.0, gap) : 0.0;
    }

    private static ItemsJustify justifyOrDefault(ItemsJustify value) {
        return value == null ? ItemsJustify.START : value;
    }

    // ==================== CSS ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXTilePane, Number> CELL_WIDTH =
                new CssMetaData<>("-rx-cell-width", SizeConverter.getInstance(), DEFAULT_CELL_WIDTH) {
                    @Override
                    public boolean isSettable(RXTilePane node) {
                        return !node.cellWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTilePane node) {
                        return (StyleableProperty<Number>) node.cellWidthProperty();
                    }
                };

        private static final CssMetaData<RXTilePane, Number> CELL_HEIGHT =
                new CssMetaData<>("-rx-cell-height", SizeConverter.getInstance(), DEFAULT_CELL_HEIGHT) {
                    @Override
                    public boolean isSettable(RXTilePane node) {
                        return !node.cellHeight.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTilePane node) {
                        return (StyleableProperty<Number>) node.cellHeightProperty();
                    }
                };

        private static final CssMetaData<RXTilePane, Number> MAX_CELL_WIDTH =
                new CssMetaData<>("-rx-max-cell-width", SizeConverter.getInstance(), DEFAULT_MAX_CELL_WIDTH) {
                    @Override
                    public boolean isSettable(RXTilePane node) {
                        return !node.maxCellWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTilePane node) {
                        return (StyleableProperty<Number>) node.maxCellWidthProperty();
                    }
                };

        private static final CssMetaData<RXTilePane, Number> HGAP =
                new CssMetaData<>("-rx-hgap", SizeConverter.getInstance(), DEFAULT_HGAP) {
                    @Override
                    public boolean isSettable(RXTilePane node) {
                        return !node.hgap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTilePane node) {
                        return (StyleableProperty<Number>) node.hgapProperty();
                    }
                };

        private static final CssMetaData<RXTilePane, Number> VGAP =
                new CssMetaData<>("-rx-vgap", SizeConverter.getInstance(), DEFAULT_VGAP) {
                    @Override
                    public boolean isSettable(RXTilePane node) {
                        return !node.vgap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTilePane node) {
                        return (StyleableProperty<Number>) node.vgapProperty();
                    }
                };

        private static final CssMetaData<RXTilePane, ItemsJustify> ITEMS_JUSTIFY =
                new CssMetaData<>("-rx-items-justify",
                        new EnumConverter<>(ItemsJustify.class), DEFAULT_ITEMS_JUSTIFY) {
                    @Override
                    public boolean isSettable(RXTilePane node) {
                        return !node.itemsJustify.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<ItemsJustify> getStyleableProperty(RXTilePane node) {
                        return (StyleableProperty<ItemsJustify>) node.itemsJustifyProperty();
                    }
                };

        private static final CssMetaData<RXTilePane, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated", BooleanConverter.getInstance(), DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXTilePane node) {
                        return !node.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXTilePane node) {
                        return (StyleableProperty<Boolean>) node.animatedProperty();
                    }
                };

        private static final CssMetaData<RXTilePane, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration", DurationConverter.getInstance(),
                        DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXTilePane node) {
                        return !node.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXTilePane node) {
                        return (StyleableProperty<Duration>) node.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Pane.getClassCssMetaData());
            Collections.addAll(styleables, CELL_WIDTH, CELL_HEIGHT, MAX_CELL_WIDTH, HGAP, VGAP,
                    ITEMS_JUSTIFY, ANIMATED, ANIMATION_DURATION);
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
}
