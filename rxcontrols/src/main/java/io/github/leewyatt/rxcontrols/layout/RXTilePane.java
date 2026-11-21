package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.internal.PixelGrid;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.utils.RXMath;
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
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A responsive, animated tile layout pane — the node-based sibling of
 * {@code RXTileView}. It lays out its managed children in a uniform grid whose
 * column count is derived from the resolved nominal tile width and the available
 * width. Unlike {@code RXTileView}, it is a plain layout {@link Pane}: not
 * data-driven, not virtualized, with no sections or selection.
 * It is to a tile grid what {@code RXFlowPane} is to a wrapping flow. The grid
 * fills horizontal rows only (there is no {@code orientation} axis, unlike
 * {@code TilePane}); the pane grows vertically as rows wrap.
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
    private static final String DEFAULT_STYLE_CLASS = "rx-tile-pane";
    private static final String MARGIN_CONSTRAINT = "rx-tile-pane-margin";
    private static final String ALIGNMENT_CONSTRAINT = "rx-tile-pane-alignment";

    private static final double DEFAULT_PREF_TILE_WIDTH = USE_COMPUTED_SIZE;
    private static final double DEFAULT_PREF_TILE_HEIGHT = USE_COMPUTED_SIZE;
    private static final double DEFAULT_HGAP = 10.0;
    private static final double DEFAULT_VGAP = 10.0;
    private static final double DEFAULT_MAX_TILE_WIDTH = 0.0;
    private static final int DEFAULT_MAX_COLUMNS = 0;
    private static final ItemsJustify DEFAULT_ITEMS_JUSTIFY = ItemsJustify.START;
    private static final VPos DEFAULT_CONTENT_V_ALIGNMENT = VPos.TOP;
    private static final Pos DEFAULT_TILE_ALIGNMENT = Pos.CENTER;
    private static final boolean DEFAULT_ANIMATED = true;
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);
    private static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    // Columns used by computePrefWidth when laid out without a width constraint.
    private static final int DEFAULT_PREF_COLUMNS = 3;
    // Defensive ceiling so a tiny prefTileWidth cannot explode the column count.
    // The tile family does not depend on MasonryColumns, so this is an independent
    // constant kept deliberately equal to MasonryColumns.MAX_RESOLVED_COLUMNS.
    private static final int MAX_RESOLVED_COLUMNS = 4096;

    // ==================== Child Constraints ====================

    /**
     * Sets the tile alignment constraint for a child. A non-null value overrides
     * this pane's {@link #tileAlignmentProperty() tileAlignment} for that child.
     * Passing {@code null} removes the constraint.
     *
     * @param child the child node
     * @param value the child tile alignment, or {@code null} to remove it
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public static void setAlignment(Node child, Pos value) {
        setConstraint(child, ALIGNMENT_CONSTRAINT, value);
    }

    /**
     * Returns the tile alignment constraint for a child.
     *
     * @param child the child node
     * @return the child tile alignment, or {@code null} if none is set
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public static Pos getAlignment(Node child) {
        return (Pos) getConstraint(child, ALIGNMENT_CONSTRAINT);
    }

    /**
     * Sets the margin constraint for a child. Passing {@code null} removes the
     * constraint. Margins participate in computed tile size and are subtracted
     * from the child layout area, matching JavaFX {@code TilePane}.
     *
     * @param child the child node
     * @param value the margin around the child, or {@code null} to remove it
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public static void setMargin(Node child, Insets value) {
        setConstraint(child, MARGIN_CONSTRAINT, value);
    }

    /**
     * Returns the margin constraint for a child.
     *
     * @param child the child node
     * @return the child margin, or {@code null} if none is set
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public static Insets getMargin(Node child) {
        return (Insets) getConstraint(child, MARGIN_CONSTRAINT);
    }

    /**
     * Removes all RXTilePane constraints from the child node.
     *
     * @param child the child node
     * @throws NullPointerException if {@code child} is {@code null}
     */
    public static void clearConstraints(Node child) {
        setAlignment(child, null);
        setMargin(child, null);
    }

    private static void setConstraint(Node child, String key, Object value) {
        Objects.requireNonNull(child, "child cannot be null");
        if (value == null) {
            child.getProperties().remove(key);
        } else {
            child.getProperties().put(key, value);
        }
        if (child.getParent() != null) {
            child.getParent().requestLayout();
        }
    }

    private static Object getConstraint(Node child, String key) {
        Objects.requireNonNull(child, "child cannot be null");
        if (child.hasProperties()) {
            Object value = child.getProperties().get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    // ==================== Animation state ====================

    // Reuses the same FLIP relayout animator as RXMasonryPane (same package). All
    // children are real and persistent, so no recycler pin-set is needed.
    private final PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
    private final ResizeGate resizeGate = new ResizeGate();
    // Who the previous pass laid out: only those children glide.
    private final PassMembership membership = new PassMembership();
    // A leaving child drifts up by the entrance offset while it fades.
    private final ChildExits exits = new ChildExits(this, animator, this::animationsActive,
            this::getAnimationDuration, this::interpolatorOrDefault,
            () -> -PaneRelayoutAnimator.enterOffset(snapSpaceY(gapOrZero(getVgap()))));

    // A child the change keeps in the list (setAll keeping it) never left: its glide goes
    // on untouched.
    private final ListChangeListener<Node> childrenListener = change -> {
        ChildrenChange children = ChildrenChange.of(change);
        if (!children.removed().isEmpty()) {
            // The recorded structure would otherwise keep removed children alive.
            resizeGate.reset();
        }
        // Forgotten in one batch: clearing many gliding children reworks the glide of
        // the rest once.
        animator.inBatch(() -> {
            for (Node removed : children.removed()) {
                membership.removed(removed, animator.isFading(removed));
                exits.removed(removed);
                animator.forget(removed);
            }
        });
        for (Node added : children.added()) {
            membership.added(added, animationsActive());
        }
    };

    // ==================== Constructors ====================

    /**
     * Creates an empty tile pane.
     */
    public RXTilePane() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        getChildren().addListener(childrenListener);
        // Region only re-lays out the parent on a snapToPixel change, and the tile size is
        // cached snapped, so without this the tiles would keep the old snapped size.
        snapToPixelProperty().addListener((obs, oldValue, newValue) -> requestLayout());
        // Neither is Region re-laid out when the render scale changes; the snapped tile size
        // and Region's cached preferred size would keep the old scale.
        new RenderScaleWatch(this, this::requestLayout);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            membership.sceneChanged(newScene);
            if (newScene == null) {
                animator.stopAll();
                resizeGate.reset();
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
     * preferred area of the managed children; a child whose height depends on its
     * width is measured at the width its tile is laid out with, so the tile height can
     * change with that width (a squeezed single column or {@link ItemsJustify#STRETCH}).
     * A positive value fixes the nominal
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

    // ==================== Pref Columns ====================

    private final IntegerProperty prefColumns = new StyleableIntegerProperty(DEFAULT_PREF_COLUMNS) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<RXTilePane, Number> getCssMetaData() {
            return StyleableProperties.PREF_COLUMNS;
        }

        @Override
        public Object getBean() {
            return RXTilePane.this;
        }

        @Override
        public String getName() {
            return "prefColumns";
        }
    };

    /**
     * Number of columns used by {@link #computePrefWidth(double)} when the pane is
     * measured without a width constraint. Does not affect the actual column count
     * used during layout, which is derived from the available width.
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
     * Whether children animate on relayout: existing children glide to their new
     * positions, and a child added while the pane shows fades in, rising a few pixels into
     * place with the same duration and interpolator; {@link #removeAnimated(Node)} and
     * {@link #clearAnimated()} fade a child out, drifting up, while the others reflow. On
     * by default; turning it off mid-flight snaps every child to its final position. A
     * size change imposed by the parent that keeps the column count applies immediately,
     * in width or in height (a pane squeezed below its content included), so dragging a
     * window edge never leaves children trailing; a glide already in flight keeps its
     * clock while its target follows the resize. An explicit {@code prefWidth}, even one
     * bound to the parent's width, counts as imposed by the parent; a width that follows
     * the pane's own computed preferred width still glides. Only a child the previous
     * layout placed glides, and only a child added to the list since, with animation on,
     * enters; one that is newly managed or back after missing a layout snaps into place,
     * and so does every child on the first layout after the pane enters a scene or is laid
     * out outside one. A child removed and put back before the next layout goes on as it
     * stood when it was removed: it glides on from where it showed, even mid-glide, or
     * still enters if it was added since; one that {@code setAll} keeps in the list goes
     * on as it was.
     *
     * <p>While a child animates, the pane drives its {@code translateX} /
     * {@code translateY}, and its {@code opacity} while it fades in or out. Values the
     * child had before are kept: a glide ends at the earlier translate, an entrance starts
     * offset from it, and a fade returns to the earlier opacity. Writing these from
     * elsewhere while the child animates, by one assignment or a running animation, is not
     * supported: the pane may overwrite them, and when the animation settles it puts back
     * the values from before, which a still-running animation may then change again. Keep
     * independent effects on a node inside the child. A child with {@code translateX} or
     * {@code translateY} bound snaps instead, whether moving or entering, and one with
     * {@code opacity} bound enters without fading; either binding, or a bound
     * {@code managed}, makes {@link #removeAnimated(Node)} and {@link #clearAnimated()}
     * remove the child at once. Binding these while the child is animating is not
     * supported. Neither is turning animation off from a listener on the children list
     * while children are fading out: finishing their exits removes children, which JavaFX
     * forbids during that list's change notification.</p>
     *
     * <p>A listener the pane's own layout reaches (one on a child's position or size, say)
     * may change the children or turn animation off while that layout runs. The pane then
     * throws nothing, keeps no reference to a child it no longer holds, does not lay out a
     * child that left it or was unmanaged before its turn, stops driving a child that
     * moved to another parent, and writes no animation once animation is off. Where the
     * transitions of that layout start from, and how smoothly they go on, is best effort,
     * and a child that leaves while its own layout is under way may still be placed by
     * that layout.</p>
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

    // ==================== Animated removal ====================

    /**
     * Removes a child with a fade-out: the child leaves the layout at once, so the other
     * children reflow into the space it frees while it fades out drifting up, and it
     * leaves the children list when the fade ends. If animation is off or the pane is not
     * in a scene, or the child's {@code managed}, {@code translateX}, {@code translateY} or
     * {@code opacity} is bound, the child is removed at once. Removing it from the
     * children list directly always removes it at once.
     *
     * @param child the child to remove
     * @return whether the child was a child of this pane
     */
    public boolean removeAnimated(Node child) {
        return exits.removeAnimated(child);
    }

    /**
     * Removes every child with a fade-out, as {@link #removeAnimated(Node)} does with one.
     * If animation is off or the pane is not in a scene, the children are cleared at once.
     */
    public void clearAnimated() {
        exits.clearAnimated();
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
    // The computed tile height for one tile width, when it depends on the width; a
    // different width measures again (see tileHeightAt).
    private boolean tileHeightAtWidthValid;
    private double tileHeightAtWidthKey;
    private double cachedTileHeightAtWidth;
    // Whether any managed child's height depends on its width; a parent asks for the
    // preferred height at many widths, and this saves a scan of the children each time.
    private boolean widthDependenceValid;
    private boolean cachedWidthDependence;

    private void invalidateTileSizeCache() {
        nominalTileWidthValid = false;
        nominalTileHeightValid = false;
        tileHeightAtWidthValid = false;
        widthDependenceValid = false;
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
        return PixelGrid.ceilX(this, snappedLeftInset() + nominalTileWidth() + snappedRightInset());
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
        // Slots and gaps are on the grid already; snapping their sum again can add a
        // device pixel at a fractional render scale.
        return PixelGrid.ceilX(this, snappedLeftInset() + content + snappedRightInset());
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
        double vgapValue = snapSpaceY(gapOrZero(getVgap()));
        if (rows == 0) {
            return heightOfRows(0, 0.0, vgapValue);
        }
        // The tiles as layoutChildren sizes them at this width: the tile height can depend
        // on their width.
        int justifyColumns = rows == 1 ? Math.min(columns, count) : columns;
        RowLayout tileRow = rowLayout(PixelGrid.floorX(this, contentWidth), justifyColumns,
                justifyOrDefault(getItemsJustify()), tileWidth, snapSpaceX(gapOrZero(getHgap())));
        return heightOfRows(rows, tileHeightAt(tileRow.tileWidth()), vgapValue);
    }

    @Override
    protected void layoutChildren() {
        // Sampled before anything below can reach a listener: publishing the column count
        // notifies its listeners, and one of them may take the pane out of its scene.
        int sceneChangesAtStart = membership.sceneChanges();
        Scene scene = getScene();
        membership.beginPass();
        double left = snappedLeftInset();
        double top = snappedTopInset();
        double bottom = snappedBottomInset();
        double contentWidth = Math.max(0.0, getWidth() - left - snappedRightInset());
        double contentHeight = Math.max(0.0, getHeight() - top - bottom);
        double nominalTileWidth = nominalTileWidth();

        int columns = computeColumns(contentWidth, nominalTileWidth);
        if (actualColumnCount.get() != columns) {
            actualColumnCount.set(columns);
        }

        List<Node> managed = getManagedChildren();
        double hgapValue = snapSpaceX(gapOrZero(getHgap()));
        double vgapValue = snapSpaceY(gapOrZero(getVgap()));
        int rows = managed.isEmpty() ? 0 : (managed.size() + columns - 1) / columns;
        Pos tileAlignmentValue = tileAlignmentOrDefault(getTileAlignment());

        // A single partial row has no cross-row column alignment to preserve, so
        // the justify block spans the actual tiles rather than the resolvable
        // column count — CENTER/END/SPACE_*/STRETCH then behave like CSS
        // justify-content on that row. With multiple rows the short final row
        // keeps the full-row metrics so columns stay aligned across rows.
        int justifyColumns = rows == 1 ? Math.min(columns, managed.size()) : columns;
        // Justify within the content width floored to the pixel grid: tile widths and
        // gaps are on the grid too, so every rounded tile x stays monotonic, keeps at
        // least hgap to its neighbour and leaves the last tile inside the content area.
        // A content width off the grid would let rounding push the last tile out.
        RowLayout tileRow = rowLayout(PixelGrid.floorX(this, contentWidth), justifyColumns,
                justifyOrDefault(getItemsJustify()), nominalTileWidth, hgapValue);
        double tileWidth = tileRow.tileWidth();
        double effectiveHgap = tileRow.hgap();
        double startX = tileRow.startX();
        // Measured at the width the tiles get in this pass: a child whose height depends on
        // its width is as high as it is laid out.
        double fullTileHeight = tileHeightAt(tileWidth);
        double tileHeight = fullTileHeight > contentHeight ? contentHeight : fullTileHeight;
        double rowBlockHeight = rows == 0 ? 0.0 : rows * tileHeight + (rows - 1) * vgapValue;
        double startY = verticalContentOffset(
                contentHeight, rowBlockHeight, contentVAlignmentOrDefault(getContentVAlignment()));

        boolean animate = isAnimated() && membership.continuesIn(scene) && isAnimationDurationPositive();
        // With animation off and nothing in flight the animator pass would be a
        // no-op; skip the move bookkeeping entirely on this hot path.
        boolean collectMoves = animate || animator.hasActiveState();
        double baselineOffset = computeBaselineOffset(managed, tileAlignmentValue, tileWidth, tileHeight);
        List<PaneRelayoutAnimator.Move> moves = collectMoves ? new ArrayList<>() : null;
        // Recorded on every animated-pane pass, the first layout included, so the
        // next pass always has a baseline to compare against.
        boolean resizeOnly = false;
        if (isAnimated()) {
            // The cross preferred extent uses the full tile height: the tile height a short
            // pane squeezes its rows to follows every drag of the window edge, while
            // padding added in a parent that sizes the pane to its pref changes this value,
            // so that growth still animates.
            resizeOnly = resizeGate.isResizeOnly(getWidth(), ownPrefWidth(), getHeight(),
                    heightOfRows(rows, fullTileHeight, vgapValue), layoutStructure(columns, managed));
        } else {
            resizeGate.reset();
        }
        for (int i = 0; i < managed.size(); i++) {
            Node child = managed.get(i);
            // The children were taken when the pass began; a listener reached from laying
            // an earlier one out may have moved this one away or unmanaged it since.
            if (child.getParent() != this || !child.isManaged()) {
                continue;
            }
            int column = i % columns;
            int row = i / columns;
            double x = left + snapPositionX(startX + column * (tileWidth + effectiveHgap));
            double y = top + snapPositionY(startY + row * (tileHeight + vgapValue));
            // FLIP: capture the current on-screen position before relocating so the
            // animator can invert the move and tween translate back to its resting value.
            double oldVisualX = 0.0;
            double oldVisualY = 0.0;
            // A child removed and put back since the last pass has had its translate given
            // back; where it showed when removed is where its glide goes on from.
            PassMembership.Shown shownWhenRemoved = null;
            if (collectMoves) {
                shownWhenRemoved = membership.shownWhenRemoved(child);
                oldVisualX = shownWhenRemoved != null
                        ? shownWhenRemoved.x() : child.getLayoutX() + child.getTranslateX();
                oldVisualY = shownWhenRemoved != null
                        ? shownWhenRemoved.y() : child.getLayoutY() + child.getTranslateY();
            }
            Pos childAlignment = childTileAlignment(child, tileAlignmentValue);
            // Recorded before any skip: every pass in a scene, the first and the
            // resize-only ones included, sets who the next pass may glide.
            membership.laidOut(child);
            layoutInTile(child, x, y, tileWidth, tileHeight, baselineOffset, getMargin(child),
                    childAlignment.getHpos(), childAlignment.getVpos());
            if (!collectMoves) {
                continue;
            }
            if (resizeOnly) {
                animator.keepInFlight(moves, child);
                continue;
            }
            // Checked before any motion: a child with no shown position to glide from has an
            // old layout position that belongs to wherever it was before. A new child rises
            // into its slot from its own translate; any other snaps, its translate untouched.
            if (shownWhenRemoved == null && !membership.wasLaidOut(child)) {
                if (membership.isEntering(child)) {
                    animator.addEnterMove(moves, child, 0.0, PaneRelayoutAnimator.enterOffset(vgapValue), true);
                }
                continue;
            }
            if (shownWhenRemoved != null && shownWhenRemoved.fading()) {
                // Removed mid-fade and put back: the removal gave the opacity back, so the
                // fade goes on from where it showed.
                animator.addResumedMove(moves, child, oldVisualX, oldVisualY, shownWhenRemoved.opacity());
            } else {
                animator.addRelayoutMove(moves, child, oldVisualX, oldVisualY);
            }
        }
        if (collectMoves && membership.sceneChanges() == sceneChangesAtStart) {
            // Laying a child out can reach a listener that moves another child away or
            // turns animation off while this pass runs: only children still laid out here
            // move, and only while the pane still animates.
            moves.removeIf(move -> move.node().getParent() != this || !move.node().isManaged());
            animator.runRelayout(moves, animate && animationsActive(), getAnimationDuration(),
                    interpolatorOrDefault());
        }
        // Publishing the column count, laying a child out, or starting a glide (which writes
        // translate) can reach a listener that takes the pane out of its scene while this
        // pass runs. The scene listener stops only what it can see then: stop what this
        // pass started after it, and let nothing from the pass count as laid out there.
        if (membership.sceneChanges() != sceneChangesAtStart) {
            animator.stopAll();
            membership.forget();
            return;
        }
        membership.endPass(scene);
    }

    // The preferred width the pane computes from its own inputs. An explicit prefWidth
    // is left out: one bound to the parent's width follows a resize and must not
    // count as the pane changing itself. Without one, prefWidth(-1) is the computed
    // value and comes from the layout cache.
    private double ownPrefWidth() {
        return getPrefWidth() == USE_COMPUTED_SIZE ? prefWidth(-1.0) : computePrefWidth(-1.0);
    }

    // The width of the tiles in a row, the gap between them and where the row starts.
    private record RowLayout(double tileWidth, double hgap, double startX) {
    }

    // Lays out a row of justifyColumns tiles within rowWidth, a width on the pixel grid.
    // Shared by layoutChildren and computePrefHeight, so a preferred height is measured
    // with the tiles the layout makes at that width.
    private RowLayout rowLayout(double rowWidth, int justifyColumns, ItemsJustify mode,
                                double nominalTileWidth, double hgapValue) {
        double preferredRowWidth = justifyColumns * nominalTileWidth + (justifyColumns - 1) * hgapValue;
        if (justifyColumns == 1 && preferredRowWidth > rowWidth) {
            return new RowLayout(Math.max(0.0, rowWidth), hgapValue, 0.0);
        }
        if (mode == ItemsJustify.STRETCH) {
            double ideal = (rowWidth - (justifyColumns - 1) * hgapValue) / justifyColumns;
            double cap = maxTileWidthOrUnbounded();
            double effectiveCap = cap > 0.0 ? Math.max(snapSizeX(cap), nominalTileWidth) : 0.0;
            if (effectiveCap > 0.0 && ideal > effectiveCap) {
                double used = justifyColumns * effectiveCap + (justifyColumns - 1) * hgapValue;
                return new RowLayout(effectiveCap, hgapValue, Math.max(0.0, (rowWidth - used) / 2.0));
            }
            // Floored, not ceiled (snapSizeX): ceiling each tile overruns the content area
            // by up to a pixel per column, into the right padding and past the pane. The
            // few spare pixels go into the gaps, so the tiles stay equal and the row still
            // fills edge to edge.
            double tileWidth = PixelGrid.floorX(this, Math.max(0.0, ideal));
            double spare = Math.max(0.0,
                    rowWidth - (justifyColumns * tileWidth + (justifyColumns - 1) * hgapValue));
            return new RowLayout(tileWidth,
                    hgapValue + (justifyColumns > 1 ? spare / (justifyColumns - 1) : 0.0), 0.0);
        }
        double slack = Math.max(0.0, rowWidth - preferredRowWidth);
        return switch (mode) {
            case CENTER -> new RowLayout(nominalTileWidth, hgapValue, slack / 2.0);
            case END -> new RowLayout(nominalTileWidth, hgapValue, slack);
            case SPACE_BETWEEN -> new RowLayout(nominalTileWidth,
                    hgapValue + (justifyColumns > 1 ? slack / (justifyColumns - 1) : 0.0), 0.0);
            case SPACE_AROUND -> new RowLayout(nominalTileWidth,
                    hgapValue + slack / justifyColumns, slack / (2.0 * justifyColumns));
            case SPACE_EVENLY -> new RowLayout(nominalTileWidth,
                    hgapValue + slack / (justifyColumns + 1), slack / (justifyColumns + 1));
            // START: the block hugs the leading edge.
            default -> new RowLayout(nominalTileWidth, hgapValue, 0.0);
        };
    }

    // The preferred height of the given rows plus the vertical insets, ceiled as
    // computePrefWidth explains.
    private double heightOfRows(int rows, double tileHeight, double vgap) {
        double content = rows == 0 ? 0.0 : rows * tileHeight + (rows - 1) * vgap;
        return PixelGrid.ceilY(this, snappedTopInset() + content + snappedBottomInset());
    }

    // Column count plus the child sequence fix every child's row and column; tile size,
    // gaps and justify offsets are left out, so a pure resize leaves it equal.
    private static List<Object> layoutStructure(int columns, List<Node> managed) {
        List<Object> structure = new ArrayList<>(managed.size() + 1);
        structure.add(columns);
        structure.addAll(managed);
        return structure;
    }

    // ==================== Helpers ====================

    // tileWidth is the snapped slot width the tiles are laid out with (nominalTileWidth()).
    // Snapping it again can add a device pixel at a fractional render scale (175% turns
    // 128.571 into 129.143), so the count would disagree with the slots it lays out.
    private int computeColumns(double availableWidth, double tileWidth) {
        double gap = snapSpaceX(gapOrZero(getHgap()));
        int max = getMaxColumns();
        int limit = max > 0 ? Math.min(max, MAX_RESOLVED_COLUMNS) : MAX_RESOLVED_COLUMNS;
        int columns = 1;
        if (availableWidth > 0.0 && tileWidth > 0.0) {
            // Capped while still a double: the ratio of a tiny tile can exceed any int.
            double fit = Math.floor((availableWidth + gap) / (tileWidth + gap));
            columns = (int) Math.max(1.0, Math.min(fit, limit));
            // The division carries floating-point noise at a fractional render scale: a row
            // that exactly fits must keep its last column, and one a fraction of a pixel too
            // wide must still lose it. The candidate rows are summed like computePrefWidth.
            if (columns < limit && !PixelGrid.exceedsX(this, rowWidth(columns + 1, tileWidth, gap), availableWidth)) {
                columns++;
            } else if (columns > 1 && PixelGrid.exceedsX(this, rowWidth(columns, tileWidth, gap), availableWidth)) {
                columns--;
            }
        }
        return columns;
    }

    private int prefWidthColumns() {
        return capColumns(getPrefColumns());
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

    // Lays a child out in its tile as layoutInArea does with both fills, except for the
    // final snap of a resizable child's size. A size worked out from the tile, or a child
    // minimum summed from snapped sizes, can compute a hair above its grid line, and
    // JavaFX 17 ceils that to a whole device pixel more: the child would overlap its
    // neighbour or run past the content edge. Measuring and placing go by the tile itself,
    // as layoutInArea does; a child whose height depends on its width is asked at the
    // width it then gets.
    private void layoutInTile(Node child, double areaX, double areaY, double areaWidth, double areaHeight,
                              double areaBaselineOffset, Insets margin, HPos hpos, VPos vpos) {
        if (child.isResizable()) {
            double top = margin == null ? 0.0 : snapSpaceY(margin.getTop());
            double bottom = margin == null ? 0.0 : snapSpaceY(margin.getBottom());
            double left = margin == null ? 0.0 : snapSpaceX(margin.getLeft());
            double right = margin == null ? 0.0 : snapSpaceX(margin.getRight());
            // A baseline-aligned child is sized from the tile less its baseline shift.
            if (vpos == VPos.BASELINE) {
                double baseline = child.getBaselineOffset();
                if (baseline == BASELINE_OFFSET_SAME_AS_HEIGHT) {
                    bottom += snapSpaceY(areaHeight - areaBaselineOffset);
                } else {
                    top = snapSpaceY(areaBaselineOffset - baseline);
                }
            }
            double width = areaWidth - left - right;
            double height = areaHeight - top - bottom;
            double childWidth;
            double childHeight;
            if (child.getContentBias() == Orientation.VERTICAL) {
                childHeight = fitY(height, child.minHeight(-1.0), child.maxHeight(-1.0));
                childWidth = fitX(width, child.minWidth(childHeight), child.maxWidth(childHeight));
            } else {
                childWidth = fittedWidth(child, width);
                double heightHint = child.getContentBias() == Orientation.HORIZONTAL ? childWidth : -1.0;
                childHeight = fitY(height, child.minHeight(heightHint), child.maxHeight(heightHint));
            }
            child.resize(childWidth, childHeight);
        }
        positionInArea(child, areaX, areaY, areaWidth, areaHeight, areaBaselineOffset, margin, hpos, vpos,
                isSnapToPixel());
    }

    // The width layoutInTile gives a resizable child in content this wide. A tile height
    // worked out from children whose height depends on their width measures them at
    // exactly this width, so the rows are as high as the children laid out in them.
    private double fittedWidth(Node child, double contentWidth) {
        return fitX(contentWidth, child.minWidth(-1.0), child.maxWidth(-1.0));
    }

    // A size worked out from the tile, clamped to a child's own bounds on the pixel grid.
    // Ceiling then clamping equals clamping then ceiling, but each side ceils its own way:
    // what margins filling the tile leave is noise and comes out as zero, while a positive
    // bound of the child's own takes at least a device pixel.
    private double fitX(double fromTile, double min, double max) {
        return RXMath.clamp(PixelGrid.ceilX(this, fromTile), PixelGrid.ceilSizeX(this, min),
                PixelGrid.ceilSizeX(this, max));
    }

    private double fitY(double fromTile, double min, double max) {
        return RXMath.clamp(PixelGrid.ceilY(this, fromTile), PixelGrid.ceilSizeY(this, min),
                PixelGrid.ceilSizeY(this, max));
    }

    private double nominalTileWidth() {
        if (!nominalTileWidthValid) {
            double value = getPrefTileWidth();
            // A size computed from the children is on the grid already; snapping it again
            // could add a device pixel. An explicit size is snapped as Region snaps any.
            cachedNominalTileWidth = isExplicitTileSize(value)
                    ? snapSizeX(value) : PixelGrid.ceilX(this, computeTileWidthFromChildren());
            nominalTileWidthValid = true;
        }
        return cachedNominalTileWidth;
    }

    // A set tile height, or one computed from children whose heights do not depend on
    // their width: the same at every tile width.
    private double nominalTileHeight() {
        if (!nominalTileHeightValid) {
            double value = getPrefTileHeight();
            cachedNominalTileHeight = isExplicitTileSize(value)
                    ? snapSizeY(value) : PixelGrid.ceilY(this, computeTileHeightFromChildren(-1.0));
            nominalTileHeightValid = true;
        }
        return cachedNominalTileHeight;
    }

    // The tile height for tiles this wide. A computed height measures a child whose height
    // depends on its width at the width it gets in a tile this wide, the set or computed
    // tile width or what a squeezed or stretched row makes of it, so it is worked out for
    // the width asked; the last one is kept.
    private double tileHeightAt(double tileWidth) {
        if (isExplicitTileSize(getPrefTileHeight()) || !hasWidthDependentChild()) {
            return nominalTileHeight();
        }
        if (!tileHeightAtWidthValid || tileWidth != tileHeightAtWidthKey) {
            cachedTileHeightAtWidth = PixelGrid.ceilY(this, computeTileHeightFromChildren(tileWidth));
            tileHeightAtWidthKey = tileWidth;
            tileHeightAtWidthValid = true;
        }
        return cachedTileHeightAtWidth;
    }

    private boolean hasWidthDependentChild() {
        if (!widthDependenceValid) {
            cachedWidthDependence = false;
            for (Node child : getManagedChildren()) {
                if (child.getContentBias() == Orientation.HORIZONTAL) {
                    cachedWidthDependence = true;
                    break;
                }
            }
            widthDependenceValid = true;
        }
        return cachedWidthDependence;
    }

    private static boolean isExplicitTileSize(double value) {
        return value != USE_COMPUTED_SIZE && Double.isFinite(value) && value > 0.0;
    }

    private double computeTileWidthFromChildren() {
        List<Node> managed = getManagedChildren();
        double height = -1.0;
        for (Node child : managed) {
            if (child.getContentBias() == Orientation.VERTICAL) {
                // The tile width is being worked out here, so width-dependent children are
                // measured at their own preferred widths.
                height = computeTileHeightFromChildren(
                        hasWidthDependentChild() ? computeMaxChildPrefAreaWidth() : -1.0);
                break;
            }
        }
        double max = 0.0;
        for (Node child : managed) {
            max = Math.max(max, computeChildPrefAreaWidth(child, height, true));
        }
        return max;
    }

    // tileWidth is the width of the tile a child whose height depends on its width is
    // measured in, or -1 when no such child is measured.
    private double computeTileHeightFromChildren(double tileWidth) {
        List<Node> managed = getManagedChildren();
        double width = tileWidth;
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
                        : fittedWidth(child, Math.max(0.0, width - snapSpaceX(margin.getLeft()) - snapSpaceX(margin.getRight())));
                double childHeight = PixelGrid.ceilSizeY(this, child.prefHeight(childWidth));
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
                alt = fitY(childHeight, child.minHeight(-1.0), child.maxHeight(-1.0));
            } else {
                alt = PixelGrid.ceilSizeY(this, RXMath.clamp(
                        child.prefHeight(-1.0),
                        child.minHeight(-1.0),
                        Math.min(child.maxHeight(-1.0), childHeight)));
            }
        }
        double childWidth = RXMath.clamp(child.prefWidth(alt), child.minWidth(alt), child.maxWidth(alt));
        // A container child reports a sum of snapped sizes; Region's snapping of that sum
        // can add a device pixel, and the tile would then be one wider than the child.
        return PixelGrid.ceilSizeX(this, childWidth) + marginWidth;
    }

    private double computeChildPrefAreaHeight(Node child, double width) {
        Insets margin = marginOrEmpty(child);
        double marginWidth = snapSpaceX(margin.getLeft()) + snapSpaceX(margin.getRight());
        double marginHeight = snapSpaceY(margin.getTop()) + snapSpaceY(margin.getBottom());
        double alt = -1.0;
        if (child.isResizable() && child.getContentBias() == Orientation.HORIZONTAL) {
            alt = width == -1.0
                    ? PixelGrid.ceilSizeX(this,
                            RXMath.clamp(child.prefWidth(-1.0), child.minWidth(-1.0), child.maxWidth(-1.0)))
                    : fittedWidth(child, Math.max(0.0, width - marginWidth));
        }
        double childHeight = RXMath.clamp(child.prefHeight(alt), child.minHeight(alt), child.maxHeight(alt));
        return PixelGrid.ceilSizeY(this, childHeight) + marginHeight;
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
                // Measured at the width layoutInTile gives it: a child narrower than its tile
                // (a max width) can be taller there than at the tile width.
                double alt = child.getContentBias() == Orientation.HORIZONTAL
                        ? fittedWidth(child, Math.max(0.0,
                                tileWidth - snapSpaceX(margin.getLeft()) - snapSpaceX(margin.getRight())))
                        : -1.0;
                double availableHeight = Math.max(0.0, tileHeight - minComplement - top - bottom);
                double childHeight = RXMath.clamp(child.prefHeight(alt), child.minHeight(alt),
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

    private double maxTileWidthOrUnbounded() {
        double value = getMaxTileWidth();
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
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

        private static final CssMetaData<RXTilePane, Number> PREF_COLUMNS =
                new CssMetaData<>("-rx-pref-columns", SizeConverter.getInstance(), DEFAULT_PREF_COLUMNS) {
                    @Override
                    public boolean isSettable(RXTilePane node) {
                        return !node.prefColumns.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXTilePane node) {
                        return (StyleableProperty<Number>) node.prefColumnsProperty();
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
            Collections.addAll(styleables, PREF_TILE_WIDTH, PREF_TILE_HEIGHT, MAX_TILE_WIDTH, PREF_COLUMNS, MAX_COLUMNS, HGAP, VGAP,
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
