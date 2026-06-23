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
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A responsive, animated tile layout pane — the node-based sibling of
 * {@code RXTileView}. It lays out its managed children in a uniform grid whose
 * column count is derived from the resolved nominal tile width and the available
 * width. Unlike {@code RXTileView}, it is a plain layout {@link Pane}: not
 * data-driven, not virtualized, with no sections or selection.
 * It is to a tile grid what {@code RXFlowPane} is to a wrapping flow.
 *
 * <p>Children are placed left-to-right, top-to-bottom into tiles whose nominal
 * size is {@link #prefTileWidthProperty() prefTileWidth} ×
 * {@link #prefTileHeightProperty() prefTileHeight}. When either preferred tile
 * dimension is {@link Region#USE_COMPUTED_SIZE} or not a finite positive
 * value, that dimension is resolved from the largest preferred area of the
 * managed children. Tiles are separated by {@link #hgapProperty() hgap} /
 * {@link #vgapProperty() vgap};
 * spare row width is distributed per {@link #itemsJustifyProperty()
 * itemsJustify}; the row block is positioned vertically by
 * {@link #contentVAlignmentProperty() contentVAlignment}. Non-stretch rows keep
 * their nominal tile width while space permits, while
 * {@link ItemsJustify#STRETCH} grows tiles to fill spare row width. If a single
 * nominal-width tile is wider than the available row width, all modes shrink the
 * tile width for that pass so the row remains horizontally bounded. During
 * actual layout the tile height is likewise limited to the available content
 * height, mirroring JavaFX {@code TilePane}'s defensive fallback. A resizable
 * child fills its tile (bounded by its own max size); when a child cannot fill
 * its tile, {@link #tileAlignmentProperty() tileAlignment} positions it inside
 * the slot. Per-child {@link #setAlignment(Node, Pos) alignment} and
 * {@link #setMargin(Node, Insets) margin} constraints override the pane default
 * in the same style as JavaFX {@code TilePane}. Baseline tile alignments share
 * one baseline offset across the aligned children. When
 * {@link #animatedProperty() animated} is on, existing children glide to their
 * new positions after relayout.
 */
public class RXTilePane extends Pane {

    // ==================== Constants ====================

    private static final double DEFAULT_PREF_TILE_WIDTH = USE_COMPUTED_SIZE;
    private static final double DEFAULT_PREF_TILE_HEIGHT = USE_COMPUTED_SIZE;
    private static final double DEFAULT_HGAP = 10.0;
    private static final double DEFAULT_VGAP = 10.0;
    private static final double DEFAULT_MAX_TILE_WIDTH = 0.0;
    private static final int DEFAULT_MAX_COLUMNS = 0;
    private static final ItemsJustify DEFAULT_ITEMS_JUSTIFY = ItemsJustify.START;
    private static final VPos DEFAULT_CONTENT_V_ALIGNMENT = VPos.TOP;
    private static final Pos DEFAULT_TILE_ALIGNMENT = Pos.CENTER;
    private static final boolean DEFAULT_ANIMATED = false;
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);
    private static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    // Columns used by computePrefWidth when laid out without a width constraint.
    private static final int DEFAULT_PREF_COLUMNS = 3;
    // Defensive ceiling so a tiny prefTileWidth cannot explode the column count.
    private static final int MAX_RESOLVED_COLUMNS = 4096;

    private static final String DEFAULT_STYLE_CLASS = "rx-tile-pane";
    private static final String MARGIN_CONSTRAINT = "rx-tile-pane-margin";
    private static final String ALIGNMENT_CONSTRAINT = "rx-tile-pane-alignment";

    // ==================== Child Constraints ====================

    /**
     * Sets the tile alignment constraint for a child. A non-null value overrides
     * this pane's {@link #tileAlignmentProperty() tileAlignment} for that child.
     * Passing {@code null} removes the constraint.
     *
     * @param node  the child node
     * @param value the child tile alignment, or {@code null} to remove it
     */
    public static void setAlignment(Node node, Pos value) {
        setConstraint(node, ALIGNMENT_CONSTRAINT, value);
    }

    /**
     * Returns the tile alignment constraint for a child.
     *
     * @param node the child node
     * @return the child tile alignment, or {@code null} if none is set
     */
    public static Pos getAlignment(Node node) {
        return (Pos) getConstraint(node, ALIGNMENT_CONSTRAINT);
    }

    /**
     * Sets the margin constraint for a child. Passing {@code null} removes the
     * constraint. Margins participate in computed tile size and are subtracted
     * from the child layout area, matching JavaFX {@code TilePane}.
     *
     * @param node  the child node
     * @param value the margin around the child, or {@code null} to remove it
     */
    public static void setMargin(Node node, Insets value) {
        setConstraint(node, MARGIN_CONSTRAINT, value);
    }

    /**
     * Returns the margin constraint for a child.
     *
     * @param node the child node
     * @return the child margin, or {@code null} if none is set
     */
    public static Insets getMargin(Node node) {
        return (Insets) getConstraint(node, MARGIN_CONSTRAINT);
    }

    /**
     * Removes all RXTilePane constraints from the child node.
     *
     * @param child the child node
     */
    public static void clearConstraints(Node child) {
        setAlignment(child, null);
        setMargin(child, null);
    }

    private static void setConstraint(Node node, String key, Object value) {
        if (value == null) {
            node.getProperties().remove(key);
        } else {
            node.getProperties().put(key, value);
        }
        if (node.getParent() != null) {
            node.getParent().requestLayout();
        }
    }

    private static Object getConstraint(Node node, String key) {
        if (node.hasProperties()) {
            Object value = node.getProperties().get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    // ==================== Animation state ====================

    // Reuses the same FLIP relayout animator as RXMasonryPane (same package). All
    // children are real and persistent, so no recycler pin-set is needed.
    private final RelayoutAnimator animator = new RelayoutAnimator();
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

    // ==================== Preferred Tile Width ====================

    private final DoubleProperty prefTileWidth = new StyleableDoubleProperty(DEFAULT_PREF_TILE_WIDTH) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<RXTilePane, Number> getCssMetaData() {
            return StyleableProperties.PREF_TILE_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXTilePane.this;
        }

        @Override
        public String getName() {
            return "prefTileWidth";
        }
    };

    /**
     * Preferred width of each tile. The default
     * {@link Region#USE_COMPUTED_SIZE} resolves the width from the largest
     * preferred area of the managed children. A positive value fixes the nominal
     * tile width and does not let child min / pref widths expand the tile. A
     * non-positive or non-finite value is accepted and resolved from children at
     * layout time.
     *
     * @return the preferred-tile-width property
     */
    public final DoubleProperty prefTileWidthProperty() {
        return prefTileWidth;
    }

    /**
     * Returns the preferred tile width.
     *
     * @return the preferred tile width, or {@link Region#USE_COMPUTED_SIZE}
     */
    public final double getPrefTileWidth() {
        return prefTileWidth.get();
    }

    /**
     * Sets the preferred tile width.
     *
     * @param value the preferred tile width
     */
    public final void setPrefTileWidth(double value) {
        prefTileWidth.set(value);
    }

    // ==================== Preferred Tile Height ====================

    private final DoubleProperty prefTileHeight = new StyleableDoubleProperty(DEFAULT_PREF_TILE_HEIGHT) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<RXTilePane, Number> getCssMetaData() {
            return StyleableProperties.PREF_TILE_HEIGHT;
        }

        @Override
        public Object getBean() {
            return RXTilePane.this;
        }

        @Override
        public String getName() {
            return "prefTileHeight";
        }
    };

    /**
     * Preferred height of each tile. The default
     * {@link Region#USE_COMPUTED_SIZE} resolves the height from the largest
     * preferred area of the managed children. A positive value fixes the nominal
     * tile height and does not let child min / pref heights expand the tile. A
     * non-positive or non-finite value is accepted and resolved from children at
     * layout time.
     *
     * @return the preferred-tile-height property
     */
    public final DoubleProperty prefTileHeightProperty() {
        return prefTileHeight;
    }

    /**
     * Returns the preferred tile height.
     *
     * @return the preferred tile height, or {@link Region#USE_COMPUTED_SIZE}
     */
    public final double getPrefTileHeight() {
        return prefTileHeight.get();
    }

    /**
     * Sets the preferred tile height.
     *
     * @param value the preferred tile height
     */
    public final void setPrefTileHeight(double value) {
        prefTileHeight.set(value);
    }

    // ==================== Max Tile Width ====================

    private final DoubleProperty maxTileWidth = new StyleableDoubleProperty(DEFAULT_MAX_TILE_WIDTH) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<RXTilePane, Number> getCssMetaData() {
            return StyleableProperties.MAX_TILE_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXTilePane.this;
        }

        @Override
        public String getName() {
            return "maxTileWidth";
        }
    };

    /**
     * Upper bound for the tile slot width used only when
     * {@link #itemsJustifyProperty() itemsJustify} is
     * {@link ItemsJustify#STRETCH}. It is useful for wide containers where stretch
     * should fill spare width up to a readable track size, then center the capped
     * row block instead of making every tile extremely wide.
     *
     * <p>This property does not constrain child nodes directly and has no effect
     * in non-stretch justification modes. A value of {@code 0} (the default), any
     * non-positive value, or a non-finite value means unbounded. A cap less than
     * or equal to the resolved preferred tile width is treated as the preferred
     * tile width, so this property never shrinks tiles below their nominal width.</p>
     *
     * @return the max-tile-width property
     */
    public final DoubleProperty maxTileWidthProperty() {
        return maxTileWidth;
    }

    /**
     * Returns the maximum tile width used in {@link ItemsJustify#STRETCH} mode.
     *
     * @return the maximum tile width, or {@code 0} for unbounded
     */
    public final double getMaxTileWidth() {
        return maxTileWidth.get();
    }

    /**
     * Sets the maximum tile width used in {@link ItemsJustify#STRETCH} mode.
     *
     * @param value a positive cap, or {@code 0} (or any non-positive value) for
     *              unbounded
     */
    public final void setMaxTileWidth(double value) {
        maxTileWidth.set(value);
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
     * Horizontal gap between tiles. A negative or non-finite value is treated as
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

    private final IntegerProperty maxColumns = new StyleableIntegerProperty(DEFAULT_MAX_COLUMNS) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<RXTilePane, Number> getCssMetaData() {
            return StyleableProperties.MAX_COLUMNS;
        }

        @Override
        public Object getBean() {
            return RXTilePane.this;
        }

        @Override
        public String getName() {
            return "maxColumns";
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
     * How a row uses its spare horizontal width: position the nominal-width block
     * ({@code START} / {@code CENTER} / {@code END}), grow the gaps
     * ({@code SPACE_BETWEEN} / {@code SPACE_AROUND} / {@code SPACE_EVENLY}) or
     * grow the tiles ({@link ItemsJustify#STRETCH}, capped by
     * {@link #maxTileWidthProperty() maxTileWidth}). A {@code null} value is
     * treated as {@link ItemsJustify#START}. When the row has spare width, only
     * {@code STRETCH} grows tiles.
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

    // ==================== Content Vertical Alignment ====================

    private final ObjectProperty<VPos> contentVAlignment =
            new StyleableObjectProperty<>(DEFAULT_CONTENT_V_ALIGNMENT) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<RXTilePane, VPos> getCssMetaData() {
                    return StyleableProperties.CONTENT_V_ALIGNMENT;
                }

                @Override
                public Object getBean() {
                    return RXTilePane.this;
                }

                @Override
                public String getName() {
                    return "contentVAlignment";
                }
            };

    /**
     * Vertical alignment of the whole row block inside this pane's available
     * content height. {@link VPos#TOP}, {@link VPos#CENTER} and
     * {@link VPos#BOTTOM} are supported. {@code null} and {@link VPos#BASELINE}
     * are treated as {@link VPos#TOP}.
     *
     * @return the content-vertical-alignment property
     */
    public final ObjectProperty<VPos> contentVAlignmentProperty() {
        return contentVAlignment;
    }

    /**
     * Returns the content vertical alignment.
     *
     * @return the content vertical alignment
     */
    public final VPos getContentVAlignment() {
        return contentVAlignment.get();
    }

    /**
     * Sets the content vertical alignment.
     *
     * @param value the vertical alignment, or {@code null} for the default
     */
    public final void setContentVAlignment(VPos value) {
        contentVAlignment.set(value);
    }

    // ==================== Tile Alignment ====================

    private final ObjectProperty<Pos> tileAlignment =
            new StyleableObjectProperty<>(DEFAULT_TILE_ALIGNMENT) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<RXTilePane, Pos> getCssMetaData() {
                    return StyleableProperties.TILE_ALIGNMENT;
                }

                @Override
                public Object getBean() {
                    return RXTilePane.this;
                }

                @Override
                public String getName() {
                    return "tileAlignment";
                }
            };

    /**
     * Default alignment for a child inside its tile when the child cannot fill
     * the whole tile because it is not resizable or is bounded by its max size.
     * Resizable children still fill their tile area subject to their own min /
     * max constraints, matching JavaFX {@code TilePane}. {@code null} is treated
     * as {@link Pos#CENTER}. Baseline positions share a baseline offset across
     * children whose effective tile alignment is baseline.
     *
     * @return the tile-alignment property
     */
    public final ObjectProperty<Pos> tileAlignmentProperty() {
        return tileAlignment;
    }

    /**
     * Returns the tile alignment.
     *
     * @return the tile alignment
     */
    public final Pos getTileAlignment() {
        return tileAlignment.get();
    }

    /**
     * Sets the tile alignment.
     *
     * @param value the tile alignment, or {@code null} for the default
     */
    public final void setTileAlignment(Pos value) {
        tileAlignment.set(value);
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
     * Whether existing children glide to their new positions after relayout. Off
     * by default; turning it off mid-flight snaps every child to its final
     * position.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether relayout animation is enabled.
     *
     * @return whether relayout animation is enabled
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether relayout animation is enabled.
     *
     * @param value whether relayout animation is enabled
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
     * Duration of a single relayout glide. A {@code null}, non-positive, unknown or
     * indefinite value is accepted and disables animation, like {@code animated=false}.
     *
     * @return the animation-duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the relayout-animation duration.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the relayout-animation duration.
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
     * Interpolator for the relayout glide. {@code null} falls back to
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

    // ==================== Tile Size Cache ====================

    private boolean nominalTileWidthValid;
    private double cachedNominalTileWidth;
    private boolean nominalTileHeightValid;
    private double cachedNominalTileHeight;

    private void invalidateTileSizeCache() {
        nominalTileWidthValid = false;
        nominalTileHeightValid = false;
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestLayout() {
        invalidateTileSizeCache();
        super.requestLayout();
    }

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
        return snappedLeftInset() + nominalTileWidth() + snappedRightInset();
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected double computePrefWidth(double height) {
        int columns = prefWidthColumns();
        double tileWidth = nominalTileWidth();
        double gap = snapSpaceX(gapOrZero(getHgap()));
        double content = rowWidth(columns, tileWidth, gap);
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
        double tileWidth = nominalTileWidth();
        int columns = computeColumns(contentWidth, tileWidth);
        int count = getManagedChildren().size();
        int rows = count == 0 ? 0 : (count + columns - 1) / columns;
        double tileHeight = nominalTileHeight();
        double vgapValue = snapSpaceY(gapOrZero(getVgap()));
        double content = rows == 0 ? 0.0 : rows * tileHeight + (rows - 1) * vgapValue;
        return snappedTopInset() + snapSizeY(content) + snappedBottomInset();
    }

    @Override
    protected void layoutChildren() {
        double left = snappedLeftInset();
        double top = snappedTopInset();
        double bottom = snappedBottomInset();
        double contentWidth = Math.max(0.0, getWidth() - left - snappedRightInset());
        double contentHeight = Math.max(0.0, getHeight() - top - bottom);
        double nominalTileWidth = nominalTileWidth();
        double nominalTileHeight = nominalTileHeight();

        int columns = computeColumns(contentWidth, nominalTileWidth);
        if (actualColumnCount.get() != columns) {
            actualColumnCount.set(columns);
        }

        List<Node> managed = getManagedChildren();
        double hgapValue = snapSpaceX(gapOrZero(getHgap()));
        double vgapValue = snapSpaceY(gapOrZero(getVgap()));
        double tileHeight = nominalTileHeight > contentHeight ? contentHeight : nominalTileHeight;
        int rows = managed.isEmpty() ? 0 : (managed.size() + columns - 1) / columns;
        double rowBlockHeight = rows == 0 ? 0.0 : rows * tileHeight + (rows - 1) * vgapValue;
        double startY = verticalContentOffset(
                contentHeight, rowBlockHeight, contentVAlignmentOrDefault(getContentVAlignment()));
        Pos tileAlignmentValue = tileAlignmentOrDefault(getTileAlignment());

        double tileWidth;
        double effectiveHgap;
        double startX;
        ItemsJustify mode = justifyOrDefault(getItemsJustify());
        double preferredRowWidth = columns * nominalTileWidth + (columns - 1) * hgapValue;
        if (columns == 1 && preferredRowWidth > contentWidth) {
            tileWidth = Math.max(0.0, contentWidth);
            effectiveHgap = hgapValue;
            startX = 0.0;
        } else if (mode == ItemsJustify.STRETCH) {
            double ideal = (contentWidth - (columns - 1) * hgapValue) / columns;
            double cap = maxTileWidthOrUnbounded();
            double effectiveCap = cap > 0.0 ? Math.max(snapSizeX(cap), nominalTileWidth) : 0.0;
            effectiveHgap = hgapValue;
            if (effectiveCap > 0.0 && ideal > effectiveCap) {
                tileWidth = effectiveCap;
                double used = columns * tileWidth + (columns - 1) * hgapValue;
                startX = Math.max(0.0, (contentWidth - used) / 2.0);
            } else {
                tileWidth = snapSizeX(Math.max(0.0, ideal));
                startX = 0.0;
            }
        } else {
            effectiveHgap = hgapValue;
            startX = 0.0;
            tileWidth = nominalTileWidth;
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
        double baselineOffset = computeBaselineOffset(managed, tileAlignmentValue, tileWidth, tileHeight);
        List<RelayoutAnimator.Move> moves = null;
        for (int i = 0; i < managed.size(); i++) {
            Node child = managed.get(i);
            int column = i % columns;
            int row = i / columns;
            double x = left + snapPositionX(startX + column * (tileWidth + effectiveHgap));
            double y = top + snapPositionY(startY + row * (tileHeight + vgapValue));
            // FLIP: capture the current on-screen position before relocating so the
            // animator can invert the move and tween translate back to zero. An
            // entering child has no meaningful previous position, so it snaps in.
            double oldVisualX = child.getLayoutX() + child.getTranslateX();
            double oldVisualY = child.getLayoutY() + child.getTranslateY();
            Pos childAlignment = childTileAlignment(child, tileAlignmentValue);
            layoutInArea(child, x, y, tileWidth, tileHeight, baselineOffset, getMargin(child),
                    childAlignment.getHpos(), childAlignment.getVpos());
            double fromDx = enteringNodes.contains(child) ? 0.0 : oldVisualX - child.getLayoutX();
            double fromDy = enteringNodes.contains(child) ? 0.0 : oldVisualY - child.getLayoutY();
            if (Math.abs(fromDx) >= RelayoutAnimator.MOVE_EPSILON
                    || Math.abs(fromDy) >= RelayoutAnimator.MOVE_EPSILON) {
                if (moves == null) {
                    moves = new ArrayList<>();
                }
                moves.add(new RelayoutAnimator.Move(child, fromDx, fromDy, false));
            }
        }
        animator.runRelayout(moves == null ? List.of() : moves, animate, getAnimationDuration(), interpolatorOrDefault());
        enteringNodes.clear();
        firstLayoutDone = true;
    }

    // ==================== Helpers ====================

    private int computeColumns(double availableWidth, double tileWidth) {
        double track = snapSizeX(tileWidth);
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

    private static double rowWidth(int columns, double tileWidth, double hgap) {
        return columns * tileWidth + (columns - 1) * hgap;
    }

    private double nominalTileWidth() {
        if (!nominalTileWidthValid) {
            cachedNominalTileWidth = snapSizeX(resolveNominalTileWidth());
            nominalTileWidthValid = true;
        }
        return cachedNominalTileWidth;
    }

    private double nominalTileHeight() {
        if (!nominalTileHeightValid) {
            cachedNominalTileHeight = snapSizeY(resolveNominalTileHeight());
            nominalTileHeightValid = true;
        }
        return cachedNominalTileHeight;
    }

    private double resolveNominalTileWidth() {
        double value = getPrefTileWidth();
        if (value == USE_COMPUTED_SIZE || !Double.isFinite(value) || value <= 0.0) {
            return computeTileWidthFromChildren();
        }
        return value;
    }

    private double resolveNominalTileHeight() {
        double value = getPrefTileHeight();
        if (value == USE_COMPUTED_SIZE || !Double.isFinite(value) || value <= 0.0) {
            return computeTileHeightFromChildren();
        }
        return value;
    }

    private double computeTileWidthFromChildren() {
        List<Node> managed = getManagedChildren();
        double height = -1.0;
        for (Node child : managed) {
            if (child.getContentBias() == Orientation.VERTICAL) {
                height = computeTileHeightFromChildren();
                break;
            }
        }
        double max = 0.0;
        for (Node child : managed) {
            max = Math.max(max, computeChildPrefAreaWidth(child, height, true));
        }
        return max;
    }

    private double computeTileHeightFromChildren() {
        List<Node> managed = getManagedChildren();
        double width = -1.0;
        for (Node child : managed) {
            if (child.getContentBias() == Orientation.HORIZONTAL) {
                width = computeMaxChildPrefAreaWidth();
                break;
            }
        }
        double max = 0.0;
        double baselineMaxAbove = 0.0;
        double baselineMaxBelow = 0.0;
        boolean hasBaseline = false;
        Pos defaultAlignment = tileAlignmentOrDefault(getTileAlignment());
        for (Node child : managed) {
            Pos childAlignment = childTileAlignment(child, defaultAlignment);
            if (childAlignment.getVpos() == VPos.BASELINE) {
                hasBaseline = true;
                Insets margin = marginOrEmpty(child);
                double childWidth = width == -1.0
                        ? -1.0
                        : Math.max(0.0, width - snapSpaceX(margin.getLeft()) - snapSpaceX(margin.getRight()));
                double childHeight = snapSizeY(child.prefHeight(childWidth));
                double baseline = child.getBaselineOffset();
                if (baseline == BASELINE_OFFSET_SAME_AS_HEIGHT) {
                    baselineMaxAbove = Math.max(baselineMaxAbove, childHeight + snapSpaceY(margin.getTop()));
                } else {
                    baselineMaxAbove = Math.max(baselineMaxAbove, baseline + snapSpaceY(margin.getTop()));
                    baselineMaxBelow = Math.max(baselineMaxBelow,
                            childHeight - baseline + snapSpaceY(margin.getBottom()));
                }
            } else {
                max = Math.max(max, computeChildPrefAreaHeight(child, width));
            }
        }
        double baselineMax = hasBaseline ? baselineMaxAbove + baselineMaxBelow : 0.0;
        return Math.max(max, baselineMax);
    }

    private double computeMaxChildPrefAreaWidth() {
        double max = 0.0;
        for (Node child : getManagedChildren()) {
            max = Math.max(max, computeChildPrefAreaWidth(child, -1.0, false));
        }
        return max;
    }

    private double computeChildPrefAreaWidth(Node child, double height, boolean fillHeight) {
        Insets margin = marginOrEmpty(child);
        double marginWidth = snapSpaceX(margin.getLeft()) + snapSpaceX(margin.getRight());
        double marginHeight = snapSpaceY(margin.getTop()) + snapSpaceY(margin.getBottom());
        double alt = -1.0;
        if (height != -1.0 && child.isResizable() && child.getContentBias() == Orientation.VERTICAL) {
            double childHeight = Math.max(0.0, height - marginHeight);
            if (fillHeight) {
                alt = snapSizeY(boundedSize(child.minHeight(-1.0), childHeight, child.maxHeight(-1.0)));
            } else {
                alt = snapSizeY(boundedSize(
                        child.minHeight(-1.0),
                        child.prefHeight(-1.0),
                        Math.min(child.maxHeight(-1.0), childHeight)));
            }
        }
        double childWidth = boundedSize(child.minWidth(alt), child.prefWidth(alt), child.maxWidth(alt));
        return snapSizeX(childWidth) + marginWidth;
    }

    private double computeChildPrefAreaHeight(Node child, double width) {
        Insets margin = marginOrEmpty(child);
        double marginWidth = snapSpaceX(margin.getLeft()) + snapSpaceX(margin.getRight());
        double marginHeight = snapSpaceY(margin.getTop()) + snapSpaceY(margin.getBottom());
        double alt = -1.0;
        if (child.isResizable() && child.getContentBias() == Orientation.HORIZONTAL) {
            double targetWidth = width == -1.0
                    ? child.prefWidth(-1.0)
                    : Math.max(0.0, width - marginWidth);
            alt = snapSizeX(boundedSize(child.minWidth(-1.0), targetWidth, child.maxWidth(-1.0)));
        }
        double childHeight = boundedSize(child.minHeight(alt), child.prefHeight(alt), child.maxHeight(alt));
        return snapSizeY(childHeight) + marginHeight;
    }

    private double computeBaselineOffset(List<Node> managed, Pos defaultAlignment, double tileWidth, double tileHeight) {
        boolean hasBaseline = false;
        double minComplement = computeMinBaselineComplement(managed, defaultAlignment);
        double offset = 0.0;
        for (Node child : managed) {
            if (childTileAlignment(child, defaultAlignment).getVpos() != VPos.BASELINE) {
                continue;
            }
            hasBaseline = true;
            Insets margin = marginOrEmpty(child);
            double top = snapSpaceY(margin.getTop());
            double bottom = snapSpaceY(margin.getBottom());
            double baseline = child.getBaselineOffset();
            if (baseline == BASELINE_OFFSET_SAME_AS_HEIGHT) {
                double alt = child.getContentBias() == Orientation.HORIZONTAL
                        ? Math.max(0.0, tileWidth - snapSpaceX(margin.getLeft()) - snapSpaceX(margin.getRight()))
                        : -1.0;
                double availableHeight = Math.max(0.0, tileHeight - minComplement - top - bottom);
                double childHeight = boundedSize(child.minHeight(alt), child.prefHeight(alt),
                        Math.min(child.maxHeight(alt), availableHeight));
                offset = Math.max(offset, top + childHeight);
            } else {
                offset = Math.max(offset, top + baseline);
            }
        }
        return hasBaseline ? offset : -1.0;
    }

    private double computeMinBaselineComplement(List<Node> managed, Pos defaultAlignment) {
        double complement = 0.0;
        for (Node child : managed) {
            if (childTileAlignment(child, defaultAlignment).getVpos() != VPos.BASELINE) {
                continue;
            }
            double baseline = child.getBaselineOffset();
            if (baseline == BASELINE_OFFSET_SAME_AS_HEIGHT) {
                continue;
            }
            double height = child.isResizable()
                    ? child.minHeight(-1.0)
                    : child.getLayoutBounds().getHeight();
            complement = Math.max(complement, height - baseline);
        }
        return complement;
    }

    private static Insets marginOrEmpty(Node child) {
        Insets margin = getMargin(child);
        return margin == null ? Insets.EMPTY : margin;
    }

    private static double boundedSize(double min, double value, double max) {
        return Math.min(Math.max(value, min), Math.max(min, max));
    }

    private double maxTileWidthOrUnbounded() {
        double value = getMaxTileWidth();
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

    private static VPos contentVAlignmentOrDefault(VPos value) {
        return value == null || value == VPos.BASELINE ? DEFAULT_CONTENT_V_ALIGNMENT : value;
    }

    private static double verticalContentOffset(double availableHeight, double contentHeight, VPos alignment) {
        double slack = Math.max(0.0, availableHeight - contentHeight);
        return switch (alignment) {
            case CENTER -> slack / 2.0;
            case BOTTOM -> slack;
            case BASELINE, TOP -> 0.0;
        };
    }

    private static Pos tileAlignmentOrDefault(Pos value) {
        if (value == null) {
            return DEFAULT_TILE_ALIGNMENT;
        }
        return value;
    }

    private static Pos childTileAlignment(Node child, Pos defaultAlignment) {
        Pos childAlignment = getAlignment(child);
        return childAlignment == null ? defaultAlignment : tileAlignmentOrDefault(childAlignment);
    }

    // ==================== CSS ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXTilePane, Number> PREF_TILE_WIDTH =
                new CssMetaData<>("-rx-pref-tile-width", SizeConverter.getInstance(), DEFAULT_PREF_TILE_WIDTH) {
                    @Override
                    public boolean isSettable(RXTilePane node) {
                        return !node.prefTileWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTilePane node) {
                        return (StyleableProperty<Number>) node.prefTileWidthProperty();
                    }
                };

        private static final CssMetaData<RXTilePane, Number> PREF_TILE_HEIGHT =
                new CssMetaData<>("-rx-pref-tile-height", SizeConverter.getInstance(), DEFAULT_PREF_TILE_HEIGHT) {
                    @Override
                    public boolean isSettable(RXTilePane node) {
                        return !node.prefTileHeight.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTilePane node) {
                        return (StyleableProperty<Number>) node.prefTileHeightProperty();
                    }
                };

        private static final CssMetaData<RXTilePane, Number> MAX_TILE_WIDTH =
                new CssMetaData<>("-rx-max-tile-width", SizeConverter.getInstance(), DEFAULT_MAX_TILE_WIDTH) {
                    @Override
                    public boolean isSettable(RXTilePane node) {
                        return !node.maxTileWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTilePane node) {
                        return (StyleableProperty<Number>) node.maxTileWidthProperty();
                    }
                };

        private static final CssMetaData<RXTilePane, Number> MAX_COLUMNS =
                new CssMetaData<>("-rx-max-columns", SizeConverter.getInstance(), DEFAULT_MAX_COLUMNS) {
                    @Override
                    public boolean isSettable(RXTilePane node) {
                        return !node.maxColumns.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTilePane node) {
                        return (StyleableProperty<Number>) node.maxColumnsProperty();
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

        private static final CssMetaData<RXTilePane, VPos> CONTENT_V_ALIGNMENT =
                new CssMetaData<>("-rx-content-v-alignment",
                        new EnumConverter<>(VPos.class), DEFAULT_CONTENT_V_ALIGNMENT) {
                    @Override
                    public boolean isSettable(RXTilePane node) {
                        return !node.contentVAlignment.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<VPos> getStyleableProperty(RXTilePane node) {
                        return (StyleableProperty<VPos>) node.contentVAlignmentProperty();
                    }
                };

        private static final CssMetaData<RXTilePane, Pos> TILE_ALIGNMENT =
                new CssMetaData<>("-rx-tile-alignment",
                        new EnumConverter<>(Pos.class), DEFAULT_TILE_ALIGNMENT) {
                    @Override
                    public boolean isSettable(RXTilePane node) {
                        return !node.tileAlignment.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Pos> getStyleableProperty(RXTilePane node) {
                        return (StyleableProperty<Pos>) node.tileAlignmentProperty();
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
            Collections.addAll(styleables, PREF_TILE_WIDTH, PREF_TILE_HEIGHT, MAX_TILE_WIDTH, MAX_COLUMNS, HGAP, VGAP,
                    ITEMS_JUSTIFY, CONTENT_V_ALIGNMENT, TILE_ALIGNMENT, ANIMATED, ANIMATION_DURATION);
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
