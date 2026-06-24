package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.internal.RXResources;

import javafx.animation.Interpolator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.DoublePropertyBase;
import javafx.beans.property.ObjectProperty;
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
 * An enhanced {@link javafx.scene.layout.FlowPane} that lays managed children out in runs
 * which wrap at the pane's boundary, and — unlike {@code FlowPane} — aligns the whole
 * content block <em>once</em> ({@link #alignmentProperty() alignment}) and then aligns each
 * run inside that block by a separate per-run alignment. A
 * {@linkplain Orientation#HORIZONTAL horizontal} flow (the default) wraps rows at the
 * width; a {@linkplain Orientation#VERTICAL vertical} flow wraps columns at the height.
 *
 * <p>This decoupling fixes FlowPane's "centered last run" behavior. Setting
 * {@code alignment = Pos.TOP_CENTER} with {@code rowHalignment = HPos.LEFT}, a horizontal
 * 7-card / 3-column flow renders
 * <pre>
 *   [1][2][3]
 *   [4][5][6]
 *   [7]          &lt;- stays LEFT inside a centered content block, not self-centered
 * </pre>
 * FlowPane centers each run independently against the inside size, so the lone last run
 * drifts to the middle; RXFlowPane centers the bounding block of all runs once and lays
 * each run out at the block's leading edge. A vertical flow mirrors this with
 * {@code columnValignment}.</p>
 *
 * <h2>Alignment model</h2>
 *
 * <p>Alignment is split into three orthogonal layers. {@code alignment} ({@link Pos})
 * positions the whole block within the pane on both axes. The other two layers are
 * direction-specific — only the pair matching the current orientation has any effect:</p>
 *
 * <table border="1">
 * <caption>Per-direction alignment</caption>
 * <tr><td></td><th scope="col">run within the block (main axis)</th>
 *     <th scope="col">item within its run (cross axis)</th></tr>
 * <tr><th scope="row">horizontal</th>
 *     <td>{@link #rowHalignmentProperty() rowHalignment} (HPos)</td>
 *     <td>{@link #rowValignmentProperty() rowValignment} (VPos)</td></tr>
 * <tr><th scope="row">vertical</th>
 *     <td>{@link #columnValignmentProperty() columnValignment} (VPos)</td>
 *     <td>{@link #columnHalignmentProperty() columnHalignment} (HPos)</td></tr>
 * </table>
 *
 * <p>The <em>run-within-block</em> layer (rowHalignment / columnValignment) is the fix:
 * FlowPane lacks it and folds the main axis into its {@code alignment} as a per-run offset.
 * The <em>item-within-run</em> layer (rowValignment / columnHalignment) matches FlowPane's
 * properties of the same name.</p>
 *
 * <h2>Relationship to {@code FlowPane.alignment}</h2>
 *
 * <p>{@code alignment} shares its name with
 * {@link javafx.scene.layout.FlowPane#alignmentProperty()} but differs on the
 * <em>main-axis</em> component: FlowPane applies it per run (the source of the bug),
 * RXFlowPane applies it to the whole block once. The cross-axis component is identical
 * (whole block in both). FlowPane's exact behavior is reproducible as an opt-in special
 * case: for a horizontal flow, {@code alignment = X} together with
 * {@code rowHalignment = X.getHpos()} lays out identically to
 * {@code FlowPane.alignment = X} — the block offset and the run-in-block offset compose to
 * the per-run offset (the block extent cancels) — and choosing {@code rowHalignment = LEFT}
 * instead is the fix. A vertical flow reproduces FlowPane the same way through
 * {@code columnValignment}.</p>
 *
 * <p>The default {@code alignment} is {@link Pos#TOP_LEFT}, matching {@code FlowPane} and
 * the wider JavaFX layout convention: at its defaults an RXFlowPane lays out exactly like a
 * {@code FlowPane} (the last-run fix is a no-op until the block leaves the leading edge). To
 * see the fix, center the block on the flow's main axis — {@code alignment = TOP_CENTER} for
 * a horizontal flow, {@code alignment = CENTER_LEFT} for a vertical one — while keeping the
 * run-within-block alignment at the leading edge ({@code rowHalignment = LEFT} /
 * {@code columnValignment = TOP}, the defaults).</p>
 *
 * <h2>Other behavior</h2>
 *
 * <ul>
 * <li>Each child keeps its own preferred size (no uniform tiles); this is an enhanced
 *     FlowPane, not a grid.</li>
 * <li>{@link #getContentBias()} follows the orientation: height-for-width when horizontal,
 *     width-for-height when vertical. Max width/height stay unbounded so the block has room
 *     to align.</li>
 * <li>{@link #rowValignmentProperty() rowValignment} may be {@link VPos#BASELINE} — a
 *     horizontal flow has a real per-item text baseline. A vertical flow has none, so its
 *     item alignment is {@code columnHalignment} (an {@link HPos}); a {@code BASELINE}
 *     value of any vertical positioning input degenerates to {@link VPos#TOP}.</li>
 * <li>{@link #prefWrapLengthProperty() prefWrapLength} is the preferred wrap length along
 *     the main axis (width when horizontal, height when vertical), used for preferred-size
 *     computation only; the live wrap tracks the pane's actual main-axis size.</li>
 * <li>Only managed children take part in layout.</li>
 * </ul>
 */
public class RXFlowPane extends Pane {

    // ==================== Constants ====================

    private static final Orientation DEFAULT_ORIENTATION = Orientation.HORIZONTAL;
    private static final double DEFAULT_HGAP = 0.0;
    private static final double DEFAULT_VGAP = 0.0;
    private static final Pos DEFAULT_ALIGNMENT = Pos.TOP_LEFT;
    private static final HPos DEFAULT_ROW_HALIGNMENT = HPos.LEFT;
    private static final VPos DEFAULT_ROW_VALIGNMENT = VPos.CENTER;
    private static final VPos DEFAULT_COLUMN_VALIGNMENT = VPos.TOP;
    private static final HPos DEFAULT_COLUMN_HALIGNMENT = HPos.LEFT;
    private static final double DEFAULT_PREF_WRAP_LENGTH = 400.0;
    private static final boolean DEFAULT_ANIMATED = false;
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);
    private static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

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

    // ==================== Animation state ====================

    // The same FLIP relayout animator RXTilePane / RXMasonryPane use (same package).
    // All children are real and persistent, so no recycler pin-set is needed.
    private final RelayoutAnimator animator = new RelayoutAnimator();
    private boolean firstLayoutDone;
    // Children added after the first layout snap into their slot rather than gliding
    // in from the pane origin (no enter animation, matching RXTilePane).
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
     * Creates an empty RXFlowPane.
     */
    public RXFlowPane() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        getChildren().addListener(childrenListener);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                animator.stopAll();
            }
        });
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
     * Creates an RXFlowPane with the given orientation.
     *
     * @param orientation the flow orientation
     */
    public RXFlowPane(Orientation orientation) {
        this();
        setOrientation(orientation);
    }

    /**
     * Creates an RXFlowPane with the given orientation and children.
     *
     * @param orientation the flow orientation
     * @param children the initial children
     */
    public RXFlowPane(Orientation orientation, Node... children) {
        this(orientation);
        getChildren().addAll(children);
    }

    /**
     * Creates an RXFlowPane with the given orientation and gaps.
     *
     * @param orientation the flow orientation
     * @param hgap the horizontal gap
     * @param vgap the vertical gap
     */
    public RXFlowPane(Orientation orientation, double hgap, double vgap) {
        this(orientation);
        setHgap(hgap);
        setVgap(vgap);
    }

    /**
     * Creates an RXFlowPane with the given orientation, gaps and children.
     *
     * @param orientation the flow orientation
     * @param hgap the horizontal gap
     * @param vgap the vertical gap
     * @param children the initial children
     */
    public RXFlowPane(Orientation orientation, double hgap, double vgap, Node... children) {
        this(orientation, hgap, vgap);
        getChildren().addAll(children);
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
                    return RXFlowPane.this;
                }

                @Override
                public String getName() {
                    return "orientation";
                }
            };

    /**
     * Flow orientation. {@link Orientation#HORIZONTAL} (the default) flows items into
     * rows that wrap at the pane's width; {@link Orientation#VERTICAL} flows them into
     * columns that wrap at the pane's height. Changing it flips
     * {@link #getContentBias()} and rebuilds the runs. A {@code null} value is not
     * rejected; it resolves to the default ({@link Orientation#HORIZONTAL}) at the use
     * site.
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

    private Orientation orientationOrDefault() {
        Orientation value = getOrientation();
        return value != null ? value : DEFAULT_ORIENTATION;
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
                    return RXFlowPane.this;
                }

                @Override
                public String getName() {
                    return "alignment";
                }
            };

    /**
     * Alignment of the whole content block (the bounding box of all runs) within
     * the pane's inside area, applied once on both axes. The default is
     * {@link Pos#TOP_LEFT}, matching {@link javafx.scene.layout.FlowPane}: at the
     * defaults the block is pinned to the top-left and the last-run fix is a no-op.
     * Centering the block on the flow's main axis is what makes the fix visible —
     * each run then stays at the block's leading edge (see
     * {@link #rowHalignmentProperty()} / {@link #columnValignmentProperty()}). The
     * content block has no baseline, so a vertical {@link VPos#BASELINE} component is
     * treated as {@link VPos#TOP} (e.g. {@code BASELINE_LEFT} behaves like
     * {@code TOP_LEFT}); per-item baseline alignment within a run is
     * {@link #rowValignmentProperty()}. A {@code null} value is not rejected; it
     * resolves to the default ({@link Pos#TOP_LEFT}) at the use site.
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
        return value != null ? value : DEFAULT_ALIGNMENT;
    }

    // ==================== Row halignment ====================

    private final ObjectProperty<HPos> rowHalignment =
            new StyleableObjectProperty<>(DEFAULT_ROW_HALIGNMENT) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, HPos> getCssMetaData() {
                    return StyleableProperties.ROW_HALIGNMENT;
                }

                @Override
                public Object getBean() {
                    return RXFlowPane.this;
                }

                @Override
                public String getName() {
                    return "rowHalignment";
                }
            };

    /**
     * Horizontal alignment of each run <em>within the content block</em>,
     * relative to the content-block width (not the pane's inside width). With
     * {@link HPos#LEFT} (the default) a short last row stays at the block's left
     * edge instead of being centered by itself. A {@code null} value is not
     * rejected; it resolves to the default ({@link HPos#LEFT}) at the use site.
     *
     * @return the row-halignment property
     */
    public final ObjectProperty<HPos> rowHalignmentProperty() {
        return rowHalignment;
    }

    /**
     * Returns the row halignment.
     *
     * @return the row halignment
     */
    public final HPos getRowHalignment() {
        return rowHalignment.get();
    }

    /**
     * Sets the row halignment.
     *
     * @param value the row halignment
     */
    public final void setRowHalignment(HPos value) {
        rowHalignment.set(value);
    }

    private HPos rowHalignmentOrDefault() {
        HPos value = getRowHalignment();
        return value != null ? value : DEFAULT_ROW_HALIGNMENT;
    }

    // ==================== Row valignment ====================

    private final ObjectProperty<VPos> rowValignment =
            new StyleableObjectProperty<>(DEFAULT_ROW_VALIGNMENT) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, VPos> getCssMetaData() {
                    return StyleableProperties.ROW_VALIGNMENT;
                }

                @Override
                public Object getBean() {
                    return RXFlowPane.this;
                }

                @Override
                public String getName() {
                    return "rowValignment";
                }
            };

    /**
     * Vertical alignment of each child within its run's height. {@link VPos#CENTER}
     * (the default, matching {@code FlowPane}) centers each item in its run.
     * {@link VPos#BASELINE} aligns items by their text baseline. A child without a
     * real baseline (its {@code getBaselineOffset()} reports
     * {@link Node#BASELINE_OFFSET_SAME_AS_HEIGHT}, e.g. a plain container) is placed
     * with its bottom edge on the shared baseline, and its bottom margin is not
     * reserved in a baseline run; unlike {@code FlowPane} such a child is not
     * stretched to fill the run height (it is capped at the run's above-baseline
     * extent). A {@code null} value is not rejected; it resolves to the default
     * ({@link VPos#CENTER}) at the use site.
     *
     * @return the row-valignment property
     */
    public final ObjectProperty<VPos> rowValignmentProperty() {
        return rowValignment;
    }

    /**
     * Returns the row valignment.
     *
     * @return the row valignment
     */
    public final VPos getRowValignment() {
        return rowValignment.get();
    }

    /**
     * Sets the row valignment.
     *
     * @param value the row valignment
     */
    public final void setRowValignment(VPos value) {
        rowValignment.set(value);
    }

    private VPos rowValignmentOrDefault() {
        VPos value = getRowValignment();
        return value != null ? value : DEFAULT_ROW_VALIGNMENT;
    }

    // ==================== Column valignment ====================

    private final ObjectProperty<VPos> columnValignment =
            new StyleableObjectProperty<>(DEFAULT_COLUMN_VALIGNMENT) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, VPos> getCssMetaData() {
                    return StyleableProperties.COLUMN_VALIGNMENT;
                }

                @Override
                public Object getBean() {
                    return RXFlowPane.this;
                }

                @Override
                public String getName() {
                    return "columnValignment";
                }
            };

    /**
     * Vertical alignment of each column <em>within the content block</em> in a vertical
     * flow, relative to the content-block height. With {@link VPos#TOP} (the default) a
     * short last column stays at the block's top edge instead of being centered by
     * itself — the vertical-flow mirror of {@link #rowHalignmentProperty()}. Ignored for
     * a horizontal flow. The content block has no baseline, so {@link VPos#BASELINE} is
     * treated as {@link VPos#TOP}. A {@code null} value is not rejected; it resolves to
     * the default ({@link VPos#TOP}) at the use site.
     *
     * @return the column-valignment property
     */
    public final ObjectProperty<VPos> columnValignmentProperty() {
        return columnValignment;
    }

    /**
     * Returns the column valignment.
     *
     * @return the column valignment
     */
    public final VPos getColumnValignment() {
        return columnValignment.get();
    }

    /**
     * Sets the column valignment.
     *
     * @param value the column valignment
     */
    public final void setColumnValignment(VPos value) {
        columnValignment.set(value);
    }

    private VPos columnValignmentOrDefault() {
        VPos value = getColumnValignment();
        return value != null ? value : DEFAULT_COLUMN_VALIGNMENT;
    }

    // ==================== Column halignment ====================

    private final ObjectProperty<HPos> columnHalignment =
            new StyleableObjectProperty<>(DEFAULT_COLUMN_HALIGNMENT) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, HPos> getCssMetaData() {
                    return StyleableProperties.COLUMN_HALIGNMENT;
                }

                @Override
                public Object getBean() {
                    return RXFlowPane.this;
                }

                @Override
                public String getName() {
                    return "columnHalignment";
                }
            };

    /**
     * Horizontal alignment of each child within its column's width in a vertical flow.
     * {@link HPos#LEFT} (the default) lines the items up along the left of each column —
     * the vertical-flow mirror of {@link #rowValignmentProperty()}. Ignored for a
     * horizontal flow. A {@code null} value is not rejected; it resolves to the default
     * ({@link HPos#LEFT}) at the use site.
     *
     * @return the column-halignment property
     */
    public final ObjectProperty<HPos> columnHalignmentProperty() {
        return columnHalignment;
    }

    /**
     * Returns the column halignment.
     *
     * @return the column halignment
     */
    public final HPos getColumnHalignment() {
        return columnHalignment.get();
    }

    /**
     * Sets the column halignment.
     *
     * @param value the column halignment
     */
    public final void setColumnHalignment(HPos value) {
        columnHalignment.set(value);
    }

    private HPos columnHalignmentOrDefault() {
        HPos value = getColumnHalignment();
        return value != null ? value : DEFAULT_COLUMN_HALIGNMENT;
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
     * Preferred wrap length along the flow's main axis — the width for a horizontal flow,
     * the height for a vertical one — used when computing the pane's preferred size only.
     * Like {@link javafx.scene.layout.FlowPane#prefWrapLengthProperty()}, it does
     * <em>not</em> control the actual wrapping at layout time; the real wrap boundary is
     * the main-axis size the parent gives this pane. It exists so an unconstrained pane
     * reports a sane preferred main-axis size instead of one giant run.
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
            return RXFlowPane.this;
        }

        @Override
        public String getName() {
            return "animated";
        }
    };

    /**
     * Whether existing children glide to their new positions when a relayout (a resize
     * that reflows the runs, a gap or alignment change) moves them. Off by default;
     * turning it off mid-flight snaps every child to its final position. Children added
     * after the first layout snap into place rather than gliding in.
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
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.ANIMATION_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXFlowPane.this;
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

    private boolean isAnimationDurationPositive() {
        Duration value = getAnimationDuration();
        return value != null && !value.isUnknown() && !value.isIndefinite()
                && value.greaterThan(Duration.ZERO);
    }

    private Interpolator interpolatorOrDefault() {
        Interpolator value = getAnimationInterpolator();
        return value == null ? DEFAULT_ANIMATION_INTERPOLATOR : value;
    }

    // ==================== Axis abstraction ====================

    private enum Axis {
        X,
        Y
    }

    // The flow runs along a "main" axis (items fill a run) and wraps onto a "cross" axis
    // (runs stack). A horizontal flow maps main -> X and cross -> Y; a vertical flow swaps
    // them. Every axis-generic helper reads orientationOrDefault() so both directions
    // share one implementation.
    private Axis mainAxis() {
        return orientationOrDefault() == Orientation.HORIZONTAL ? Axis.X : Axis.Y;
    }

    private Axis crossAxis() {
        return orientationOrDefault() == Orientation.HORIZONTAL ? Axis.Y : Axis.X;
    }

    private double mainGapOrDefault() {
        return orientationOrDefault() == Orientation.HORIZONTAL ? hgapOrDefault() : vgapOrDefault();
    }

    private double crossGapOrDefault() {
        return orientationOrDefault() == Orientation.HORIZONTAL ? vgapOrDefault() : hgapOrDefault();
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
                double mainGap = snapSpace(mainAxis(), mainGapOrDefault());
                VPos rowValignment = rowValignmentOrDefault();
                List<Run> built = new ArrayList<>();
                double runLength = 0;
                Run run = new Run();
                for (Node child : getManagedChildren()) {
                    LayoutRect nodeRect = new LayoutRect();
                    nodeRect.node = child;
                    nodeRect.main = prefAreaMain(child);
                    nodeRect.cross = prefAreaCross(child);
                    if (runLength + nodeRect.main > maxRunLength && runLength > 0) {
                        // wrap to next run unless it is the only node in the run
                        normalizeRun(run, mainGap, rowValignment);
                        built.add(run);
                        runLength = 0;
                        run = new Run();
                    }
                    runLength += nodeRect.main + mainGap;
                    run.rects.add(nodeRect);
                }
                normalizeRun(run, mainGap, rowValignment);
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

    private void normalizeRun(Run run, double mainGap, VPos rowValignment) {
        int count = run.rects.size();
        double main = count > 1 ? (count - 1) * mainGap : 0;
        double plainCross = 0;
        for (LayoutRect lrect : run.rects) {
            main += lrect.main;
            plainCross = Math.max(plainCross, lrect.cross);
        }
        run.main = main;
        // Baseline alignment exists only for a horizontal flow: its cross axis is vertical
        // and rowValignment can be BASELINE. A vertical flow stacks items along a
        // horizontal cross axis with no shared text baseline, so it always takes the
        // plain (max cross) path.
        if (orientationOrDefault() != Orientation.HORIZONTAL || rowValignment != VPos.BASELINE) {
            run.cross = plainCross;
            run.baselineOffset = 0;
            return;
        }
        // Baseline runs size to maxAbove + maxBelow, exactly Region.getMaxAreaHeight's
        // BASELINE path (and FlowPane): this can exceed the tallest child's pref-area
        // cross size when a shallow-baseline child has a deep below-baseline part. No
        // plainCross floor on purpose — a SAME_AS_HEIGHT child's trailing margin stays
        // below the implied baseline (as in FlowPane), and a floor would only wedge
        // empty space below it without un-compressing it. run.baselineOffset is the
        // shared baseline from the run's leading edge, later fed to layoutInArea.
        Axis cross = crossAxis();
        double maxAbove = 0;
        double maxBelow = 0;
        for (LayoutRect lrect : run.rects) {
            Node child = lrect.node;
            Insets margin = getMargin(child);
            double leading = marginLeading(cross, margin);
            double trailing = marginTrailing(cross, margin);
            double childCross = lrect.cross - leading - trailing;
            double baseline = child.getBaselineOffset();
            if (baseline == Node.BASELINE_OFFSET_SAME_AS_HEIGHT) {
                maxAbove = Math.max(maxAbove, childCross + leading);
            } else {
                maxAbove = Math.max(maxAbove, baseline + leading);
                maxBelow = Math.max(maxBelow, childCross - baseline + trailing);
            }
        }
        run.cross = maxAbove + maxBelow;
        run.baselineOffset = maxAbove;
    }

    private double computeContentMain(List<Run> lines) {
        double main = 0;
        for (Run run : lines) {
            main = Math.max(main, run.main);
        }
        return main;
    }

    private double computeContentCross(List<Run> lines) {
        // getRuns always returns at least one run, so (size - 1) is never negative.
        double crossGap = snapSpace(crossAxis(), crossGapOrDefault());
        double cross = (lines.size() - 1) * crossGap;
        for (Run run : lines) {
            cross += run.cross;
        }
        return cross;
    }

    // Resolve the main/cross run extents onto the X/Y axes for the width/height-oriented
    // size and block-alignment consumers.
    private double computeContentWidth(List<Run> lines) {
        return mainAxis() == Axis.X ? computeContentMain(lines) : computeContentCross(lines);
    }

    private double computeContentHeight(List<Run> lines) {
        return mainAxis() == Axis.Y ? computeContentMain(lines) : computeContentCross(lines);
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    public Orientation getContentBias() {
        return orientationOrDefault();
    }

    // Largest single child along the main (wrap) axis — the pane's minimum size on that
    // axis, so no child is ever clipped however narrow the wrap dimension gets.
    private double maxChildMain() {
        double max = 0;
        for (Node child : getManagedChildren()) {
            max = Math.max(max, prefAreaMain(child));
        }
        return max;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height) {
        if (orientationOrDefault() == Orientation.HORIZONTAL) {
            // width is the wrap (main) axis: the narrowest the pane gets is its widest child.
            return snappedLeftInset() + maxChildMain() + snappedRightInset();
        }
        // vertical: width is the cross axis -> min width equals pref width at this height.
        return computePrefWidth(height);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width) {
        if (orientationOrDefault() == Orientation.VERTICAL) {
            // height is the wrap (main) axis: the shortest the pane gets is its tallest child.
            return snappedTopInset() + maxChildMain() + snappedBottomInset();
        }
        // horizontal: height is the cross axis -> min height equals pref height at this width.
        return computePrefHeight(width);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height) {
        if (orientationOrDefault() == Orientation.HORIZONTAL) {
            // width is the wrap (main) axis, floored by prefWrapLength.
            List<Run> lines = getRuns(getPrefWrapLength());
            double width = Math.max(computeContentWidth(lines), getPrefWrapLength());
            return snappedLeftInset() + snapSizeX(width) + snappedRightInset();
        }
        // vertical: width is the cross axis, sized to the content wrapped at the given height.
        double wrap = height == -1
                ? getPrefWrapLength()
                : height - snappedTopInset() - snappedBottomInset();
        List<Run> lines = getRuns(wrap);
        return snappedLeftInset() + snapSizeX(computeContentWidth(lines)) + snappedRightInset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width) {
        if (orientationOrDefault() == Orientation.VERTICAL) {
            // height is the wrap (main) axis, floored by prefWrapLength.
            List<Run> lines = getRuns(getPrefWrapLength());
            double height = Math.max(computeContentHeight(lines), getPrefWrapLength());
            return snappedTopInset() + snapSizeY(height) + snappedBottomInset();
        }
        // horizontal: height is the cross axis, sized to the content wrapped at the given width.
        double wrap = width == -1
                ? getPrefWrapLength()
                : width - snappedLeftInset() - snappedRightInset();
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
        // (maxRunLength == inside main extent) matches computePref*'s wrap length,
        // mirroring FlowPane; the block/line offsets are snapped on output below.
        double insideWidth = getWidth() - left - snappedRightInset();
        double insideHeight = getHeight() - top - snappedBottomInset();

        boolean horizontal = orientationOrDefault() == Orientation.HORIZONTAL;
        List<Run> lines = getRuns(horizontal ? insideWidth : insideHeight);
        double blockWidth = computeContentWidth(lines);
        double blockHeight = computeContentHeight(lines);

        // The whole content block is aligned once on both axes (alignment), regardless of
        // orientation; only managed children take part.
        Pos align = alignmentOrDefault();
        double blockX = snapPositionX(left + blockHOffset(insideWidth, blockWidth, align.getHpos()));
        double blockY = snapPositionY(top + blockVOffset(insideHeight, blockHeight, align.getVpos()));

        double hgap = snapSpaceX(hgapOrDefault());
        double vgap = snapSpaceY(vgapOrDefault());

        boolean animate = isAnimated() && firstLayoutDone && getScene() != null
                && isAnimationDurationPositive();
        List<RelayoutAnimator.Move> moves = new ArrayList<>();

        if (horizontal) {
            // Runs stack down the cross (Y) axis. Each run is aligned along the main (X)
            // axis inside the block by rowHalignment; each item sits within the run's
            // cross extent (height) by rowValignment.
            HPos rowH = rowHalignmentOrDefault();
            VPos rowV = rowValignmentOrDefault();
            double y = blockY;
            for (Run run : lines) {
                double lineX = snapPositionX(blockX + blockHOffset(blockWidth, run.main, rowH));
                double baselineOffset = rowV == VPos.BASELINE ? run.baselineOffset : -1.0;
                double x = lineX;
                for (LayoutRect lrect : run.rects) {
                    layoutItem(lrect.node, x, y, lrect.main, run.cross, baselineOffset,
                            HPos.LEFT, rowV, moves);
                    x += lrect.main + hgap;
                }
                y += run.cross + vgap;
            }
        } else {
            // Columns stack across the cross (X) axis. Each column is aligned along the
            // main (Y) axis inside the block by columnValignment; each item sits within
            // the column's cross extent (width) by columnHalignment. Columns have no
            // shared baseline, so -1 is passed for the baseline offset.
            VPos colV = columnValignmentOrDefault();
            HPos colH = columnHalignmentOrDefault();
            double x = blockX;
            for (Run run : lines) {
                double lineY = snapPositionY(blockY + blockVOffset(blockHeight, run.main, colV));
                double y = lineY;
                for (LayoutRect lrect : run.rects) {
                    layoutItem(lrect.node, x, y, run.cross, lrect.main, -1.0,
                            colH, VPos.TOP, moves);
                    y += lrect.main + vgap;
                }
                x += run.cross + hgap;
            }
        }

        animator.runRelayout(moves, animate, getAnimationDuration(), interpolatorOrDefault());
        enteringNodes.clear();
        firstLayoutDone = true;
    }

    // Lays the node out (no fill, matching the static path) and records its FLIP delta
    // from the old on-screen position for the relayout animator. An entering child has
    // no meaningful previous position, so it snaps to its slot (fromD* = 0).
    private void layoutItem(Node node, double x, double y, double width, double height,
                            double baselineOffset, HPos hpos, VPos vpos,
                            List<RelayoutAnimator.Move> moves) {
        double oldVisualX = node.getLayoutX() + node.getTranslateX();
        double oldVisualY = node.getLayoutY() + node.getTranslateY();
        layoutInArea(node, x, y, width, height, baselineOffset, getMargin(node),
                false, false, hpos, vpos);
        double fromDx = enteringNodes.contains(node) ? 0.0 : oldVisualX - node.getLayoutX();
        double fromDy = enteringNodes.contains(node) ? 0.0 : oldVisualY - node.getLayoutY();
        if (Math.abs(fromDx) >= RelayoutAnimator.MOVE_EPSILON
                || Math.abs(fromDy) >= RelayoutAnimator.MOVE_EPSILON) {
            moves.add(new RelayoutAnimator.Move(node, fromDx, fromDy, false));
        }
    }

    // ==================== Layout helpers ====================

    private double prefAreaMain(Node child) {
        return prefAreaSize(child, mainAxis());
    }

    private double prefAreaCross(Node child) {
        return prefAreaSize(child, crossAxis());
    }

    /**
     * Preferred size of the child plus its margins along {@code axis}. When {@code axis}
     * is the child's <em>dependent</em> dimension — its content bias runs along the other
     * axis — the size is measured at the child's own preferred size along the bias
     * (driving) axis: height-for-width for a horizontally-biased child, width-for-height
     * for a vertically-biased one. This generalizes FlowPane's height-for-width to both
     * axes (FlowPane skips width-for-height); in a flow each item takes its preferred main
     * size, so the driving size is the child's own preference, not an allocated extent.
     */
    private double prefAreaSize(Node child, Axis axis) {
        Insets margin = getMargin(child);
        double alt = -1;
        Orientation bias = child.getContentBias();
        if (child.isResizable() && bias != null) {
            Axis biasAxis = bias == Orientation.HORIZONTAL ? Axis.X : Axis.Y;
            if (axis != biasAxis) {
                alt = snapSize(biasAxis, boundedSize(childMin(child, biasAxis, -1),
                        childPref(child, biasAxis, -1), childMax(child, biasAxis, -1)));
            }
        }
        double size = boundedSize(childMin(child, axis, alt), childPref(child, axis, alt),
                childMax(child, axis, alt));
        return marginLeading(axis, margin) + snapSize(axis, size) + marginTrailing(axis, margin);
    }

    private static double childMin(Node child, Axis axis, double alt) {
        return axis == Axis.X ? child.minWidth(alt) : child.minHeight(alt);
    }

    private static double childPref(Node child, Axis axis, double alt) {
        return axis == Axis.X ? child.prefWidth(alt) : child.prefHeight(alt);
    }

    private static double childMax(Node child, Axis axis, double alt) {
        return axis == Axis.X ? child.maxWidth(alt) : child.maxHeight(alt);
    }

    private double snapSize(Axis axis, double value) {
        return axis == Axis.X ? snapSizeX(value) : snapSizeY(value);
    }

    private double snapSpace(Axis axis, double value) {
        return axis == Axis.X ? snapSpaceX(value) : snapSpaceY(value);
    }

    private double marginLeading(Axis axis, Insets margin) {
        if (margin == null) {
            return 0.0;
        }
        return snapSpace(axis, axis == Axis.X ? margin.getLeft() : margin.getTop());
    }

    private double marginTrailing(Axis axis, Insets margin) {
        if (margin == null) {
            return 0.0;
        }
        return snapSpace(axis, axis == Axis.X ? margin.getRight() : margin.getBottom());
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

        private static final CssMetaData<RXFlowPane, Orientation> ORIENTATION =
                new CssMetaData<>("-rx-orientation",
                        new EnumConverter<>(Orientation.class), DEFAULT_ORIENTATION) {
                    @Override
                    public Orientation getInitialValue(RXFlowPane node) {
                        return node.getOrientation();
                    }

                    @Override
                    public boolean isSettable(RXFlowPane node) {
                        return !node.orientation.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Orientation> getStyleableProperty(RXFlowPane node) {
                        return (StyleableProperty<Orientation>) node.orientationProperty();
                    }
                };

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

        private static final CssMetaData<RXFlowPane, Pos> ALIGNMENT =
                new CssMetaData<>("-rx-alignment",
                        new EnumConverter<>(Pos.class), DEFAULT_ALIGNMENT) {
                    @Override
                    public boolean isSettable(RXFlowPane node) {
                        return !node.alignment.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Pos> getStyleableProperty(RXFlowPane node) {
                        return (StyleableProperty<Pos>) node.alignmentProperty();
                    }
                };

        private static final CssMetaData<RXFlowPane, HPos> ROW_HALIGNMENT =
                new CssMetaData<>("-rx-row-halignment",
                        new EnumConverter<>(HPos.class), DEFAULT_ROW_HALIGNMENT) {
                    @Override
                    public boolean isSettable(RXFlowPane node) {
                        return !node.rowHalignment.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<HPos> getStyleableProperty(RXFlowPane node) {
                        return (StyleableProperty<HPos>) node.rowHalignmentProperty();
                    }
                };

        private static final CssMetaData<RXFlowPane, VPos> ROW_VALIGNMENT =
                new CssMetaData<>("-rx-row-valignment",
                        new EnumConverter<>(VPos.class), DEFAULT_ROW_VALIGNMENT) {
                    @Override
                    public boolean isSettable(RXFlowPane node) {
                        return !node.rowValignment.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<VPos> getStyleableProperty(RXFlowPane node) {
                        return (StyleableProperty<VPos>) node.rowValignmentProperty();
                    }
                };

        private static final CssMetaData<RXFlowPane, VPos> COLUMN_VALIGNMENT =
                new CssMetaData<>("-rx-column-valignment",
                        new EnumConverter<>(VPos.class), DEFAULT_COLUMN_VALIGNMENT) {
                    @Override
                    public boolean isSettable(RXFlowPane node) {
                        return !node.columnValignment.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<VPos> getStyleableProperty(RXFlowPane node) {
                        return (StyleableProperty<VPos>) node.columnValignmentProperty();
                    }
                };

        private static final CssMetaData<RXFlowPane, HPos> COLUMN_HALIGNMENT =
                new CssMetaData<>("-rx-column-halignment",
                        new EnumConverter<>(HPos.class), DEFAULT_COLUMN_HALIGNMENT) {
                    @Override
                    public boolean isSettable(RXFlowPane node) {
                        return !node.columnHalignment.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<HPos> getStyleableProperty(RXFlowPane node) {
                        return (StyleableProperty<HPos>) node.columnHalignmentProperty();
                    }
                };

        private static final CssMetaData<RXFlowPane, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated", BooleanConverter.getInstance(), DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXFlowPane node) {
                        return !node.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXFlowPane node) {
                        return (StyleableProperty<Boolean>) node.animatedProperty();
                    }
                };

        private static final CssMetaData<RXFlowPane, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration", DurationConverter.getInstance(),
                        DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXFlowPane node) {
                        return !node.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXFlowPane node) {
                        return (StyleableProperty<Duration>) node.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Pane.getClassCssMetaData());
            Collections.addAll(styleables, ORIENTATION, HGAP, VGAP, ALIGNMENT,
                    ROW_HALIGNMENT, ROW_VALIGNMENT, COLUMN_VALIGNMENT, COLUMN_HALIGNMENT,
                    ANIMATED, ANIMATION_DURATION);
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
        private double main;
        private double cross;
    }

    private static final class Run {
        private final List<LayoutRect> rects = new ArrayList<>();
        private double main;
        private double cross;
        private double baselineOffset;
    }
}
