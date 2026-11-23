package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.event.RXDashboardEvent;
import io.github.leewyatt.rxcontrols.internal.MasonryColumns;
import io.github.leewyatt.rxcontrols.internal.PixelGrid;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.internal.RenderScaleWatch;
import io.github.leewyatt.rxcontrols.layout.DashboardSolver.Item;
import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout.Placement;
import javafx.animation.Interpolator;
import javafx.beans.DefaultProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableIntegerProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleOrigin;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.SizeConverter;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * An editable dashboard: cards on a grid of a fixed number of columns and rows of a fixed height, which the user can
 * drag and resize and the application can save and restore.
 *
 * <p>Each card ({@link RXDashboardCard}) occupies a rectangle of cells, given by its column, row, column span and row
 * span. The pane owns those positions: they are read-only on the card and change through the pane's methods
 * ({@link #moveCard}, {@link #resizeCard}, {@link #compact()}, {@link #applyLayout}) or user gestures, all of which
 * resolve collisions by the same rules and are complete when they return. Under the default
 * {@link CollisionPolicy#PUSH PUSH} policy, the edited card takes the cells it asks for and every card in the way
 * moves straight down, as far as needed, possibly pushing others in turn. By default
 * ({@link #autoCompactProperty() autoCompact}) the cards are then packed upward, so no hole stays above a card; while
 * dragging, a card also exchanges places with a touching neighbour of a matching size in its row or column, and
 * dragged down onto cards that could take its place it goes below them. With {@code autoCompact} off, nothing moves sideways or up, and holes
 * left behind stay until the application calls {@link #compact()}. An edit whose area covers a
 * {@link RXDashboardCard#lockedProperty() locked} card is rejected; locked cards never move by themselves. Under
 * {@link CollisionPolicy#BLOCK BLOCK} an edit whose area covers any other card is rejected.</p>
 *
 * <pre>{@code
 * RXDashboardPane dashboard = new RXDashboardPane();
 * dashboard.setColumnCount(4);
 * dashboard.setRowHeight(180);
 * dashboard.getCards().addAll(new RXDashboardCard("trend", chart),
 *         new RXDashboardCard("customers", table), new RXDashboardCard("kpi", kpi));
 * dashboard.applyLayout(saved != null ? saved : defaults);
 * dashboard.layoutProperty().addListener((obs, old, layout) -> preferences.put("home", layout.encode()));
 * }</pre>
 *
 * <p><b>Geometry.</b> The columns share the content width equally, with {@link #hgapProperty() hgap} between them;
 * every row is {@link #rowHeightProperty() rowHeight} high, with {@link #vgapProperty() vgap} between rows, so a wider
 * window makes cards wider but never taller. Every edge lands on the device-pixel grid. The pane's preferred height
 * is that of its rows and its minimum height equals it: the pane is not squeezed, so put it in a {@code ScrollPane}
 * when it may be taller than the window. Its preferred width makes each cell square; its minimum width is
 * {@link #minColumnWidthProperty() minColumnWidth} per column.</p>
 *
 * <p><b>Gestures.</b> While the pane is {@link #editableProperty() editable}, the user drags a card by pressing
 * anywhere on it that its content does not take for itself (table rows, scroll bars and buttons do; charts and labels
 * do not), or on its {@link RXDashboardCard#dragHandleProperty() drag handle}, and resizes it from its right edge,
 * bottom edge or bottom-right corner. A press moves nothing until the pointer has travelled a few pixels. During the
 * gesture a placeholder marks the cells the card will take, the other cards make room as the collision policy says,
 * and nothing is committed: releasing commits the layout and fires {@link RXDashboardEvent#LAYOUT_CHANGED}, Esc or
 * switching to another window cancels it. Near the top or bottom of an enclosing {@code ScrollPane}'s viewport, the
 * scroll pane scrolls. Changing the cards, the column count or the layout from code during a gesture cancels it
 * first.</p>
 *
 * <p><b>Saving and restoring.</b> {@link #layoutProperty() layout} holds a snapshot of the committed layout,
 * keyed by {@link RXDashboardCard#cardIdProperty() card ID}, and changes after every change, from the user and from
 * code; save it from a listener on that property. {@link #applyLayout} restores a snapshot, correcting whatever does
 * not fit the current cards and columns. {@link RXDashboardEvent#LAYOUT_CHANGED} is fired only after the user
 * commits an edit.</p>
 *
 * <p><b>Changing the layout from callbacks.</b> A commit first records the new layout, then updates the cards'
 * placement properties, then the {@code layout} property, and fires the event last when the user made the change.
 * Calling {@link #moveCard}, {@link #resizeCard}, {@link #compact()} or {@link #applyLayout} from a listener on a
 * card's placement properties or on {@code layout} throws {@link IllegalStateException}; from a
 * {@link RXDashboardEvent} handler it is allowed and commits at once. Other changes made from those listeners
 * (adding or removing cards, the column count, a card's ID, span limits or lock) take effect once the notification
 * is over.</p>
 *
 * <p>Style class {@code rx-dashboard-pane}; pseudo-class {@code :editable}. The cards are the pane's children in
 * reading order, row first, then column, which is also the keyboard focus order; {@link #getCards()} keeps the order
 * the application gave.</p>
 */
@DefaultProperty("cards")
public class RXDashboardPane extends Region {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-dashboard-pane";
    private static final PseudoClass EDITABLE_PSEUDO_CLASS = PseudoClass.getPseudoClass("editable");

    private static final int DEFAULT_COLUMN_COUNT = 12;
    private static final double DEFAULT_ROW_HEIGHT = 80.0;
    private static final double DEFAULT_GAP = 10.0;
    private static final double DEFAULT_MIN_COLUMN_WIDTH = 0.0;
    private static final CollisionPolicy DEFAULT_COLLISION_POLICY = CollisionPolicy.PUSH;
    private static final boolean DEFAULT_AUTO_COMPACT = true;
    private static final boolean DEFAULT_ANIMATED = true;
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);
    private static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    // A listener that changes the cards on every notification would otherwise loop forever.
    private static final int MAX_RECONCILE_ROUNDS = 100;

    // Operations the pane keeps for a late column count or policy before its first layout; a pane changed this often
    // without ever being laid out stops keeping them, so a hidden pane does not hold on to cards it no longer has.
    private static final int HISTORY_LIMIT = 512;

    // The regular grid a drag is worked out on step by step, in virtual pixels: a cell, and the margin inside a cell a
    // move by code uses (the largest the drag leans by). A gesture uses half the gaps.
    private static final double VIRTUAL_CELL = 100.0;
    private static final double VIRTUAL_MARGIN = 10.0;

    private static final Logger LOGGER = Logger.getLogger(RXDashboardPane.class.getName());

    /**
     * How the pane resolves an edit whose area covers other cards.
     */
    public enum CollisionPolicy {

        /**
         * The edited card takes the area; every card in the way moves straight down until it is clear, pushing
         * others in turn. Locked cards are never pushed: an area covering one is rejected. With
         * {@link #autoCompactProperty() autoCompact}, a drag or {@link #moveCard} may also exchange the card with a
         * neighbour, and the cards then pack upward.
         */
        PUSH,

        /**
         * An area covering any other card is rejected; no card is pushed or exchanged. With
         * {@link #autoCompactProperty() autoCompact}, the other cards still pack upward after an edit.
         */
        BLOCK
    }

    // ==================== State ====================

    private final ObservableList<RXDashboardCard> cards = FXCollections.observableArrayList();
    // The distinct cards of the list, in its order, that the pane has taken in.
    private final List<RXDashboardCard> members = new ArrayList<>();
    // The base layout a column-count change converts from, so a round trip with no edit in between restores the
    // layout: per card, the last placement not formed by a column-count change and the column count it was formed at.
    // Adding a card that pushes nothing records only that card. A card the base does not hold (left out of an applied
    // snapshot) is placed as a card a snapshot lacks.
    private final Map<RXDashboardCard, BaseEntry> base = new IdentityHashMap<>();
    // Until the first layout pass, every operation that placed cards, in order. FXML sets a pane's attributes after
    // its children and a style sheet's column count arrives with the first CSS pass, so the column count or the
    // collision policy may change after cards were placed; the pane then places them all again from this history, as
    // if the new value had been set first. A column count that arrives after cards were placed since an earlier
    // replayed one converts instead, since they refer to that count. Null once the pane has been laid out, once a
    // column count was converted, or once the history outgrew its limit.
    private List<History> history = new ArrayList<>();
    private boolean replayPending;
    private boolean columnsReplayed;
    private boolean placedAfterColumns;
    private CollisionPolicy lastPolicy = DEFAULT_COLLISION_POLICY;
    private boolean lastAutoCompact = DEFAULT_AUTO_COMPACT;
    private int effectiveColumns = DEFAULT_COLUMN_COUNT;
    private int lastValidColumnCount = DEFAULT_COLUMN_COUNT;
    // Depth of the placement and layout notifications in progress; changes arriving meanwhile wait for the end.
    private int notifying;
    private boolean reconcilePending;
    private boolean reconciling;

    private final PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
    private final ResizeGate resizeGate = new ResizeGate();
    private final PassMembership membership = new PassMembership();
    private DashboardGrid grid;

    private final DashboardGesture gesture;
    // Where a card a gesture let go showed, for the next pass to glide it from.
    private final Map<RXDashboardCard, Point2D> pendingShown = new IdentityHashMap<>();
    // Marks the area a dragged or resized card lands on. Unmanaged, so showing it requests no layout.
    private final Region placeholder = new Region();

    private record BaseEntry(Placement placement, int columns) {
    }

    private enum Step {
        ADDED, REMOVED, APPLIED, MOVED, RESIZED, COMPACTED
    }

    // One operation of the history: the card and what was asked for (the request it was added with, the position it
    // was moved to, the spans it was resized to, where it was when removed), or, for an applied snapshot, the cards it
    // matched with their snapshot placements, the snapshot's column count and the order of the cards then.
    private record History(Step step, RXDashboardCard card, Placement asked, Map<RXDashboardCard, Placement> targets,
                           int columns, List<RXDashboardCard> order) {

        static History of(Step step, RXDashboardCard card, Placement asked) {
            return new History(step, card, asked, null, 0, null);
        }
    }

    // ==================== Constructors ====================

    /**
     * Creates an empty dashboard with 12 columns.
     */
    public RXDashboardPane() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        pseudoClassStateChanged(EDITABLE_PSEUDO_CLASS, true);
        layout.set(RXDashboardLayout.builder(effectiveColumns).build());
        placeholder.getStyleClass().add("placeholder");
        placeholder.setManaged(false);
        placeholder.setMouseTransparent(true);
        placeholder.setVisible(false);
        placeholder.setViewOrder(1.0);
        getChildren().add(placeholder);
        gesture = new DashboardGesture(this);
        cards.addListener((ListChangeListener<RXDashboardCard>) change -> reconcile());
        // Region re-lays out nothing when the render scale or snapToPixel changes; the grid lines would keep the old
        // device grid.
        snapToPixelProperty().addListener((obs, oldValue, newValue) -> requestLayout());
        new RenderScaleWatch(this, this::requestLayout);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            gesture.cancel();
            pendingShown.clear();
            membership.sceneChanged(newScene);
            if (newScene == null) {
                animator.stopAll();
                resizeGate.reset();
            }
        });
    }

    /**
     * Creates a dashboard with 12 columns holding the given cards, each placed as it is added.
     *
     * @param cards the initial cards
     */
    public RXDashboardPane(RXDashboardCard... cards) {
        this();
        this.cards.addAll(cards);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the rxcontrols user-agent stylesheet so the pane's {@code -rx-*} properties and the cards' default
     * look apply without any application-level install.</p>
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Cards ====================

    /**
     * Returns the cards of the dashboard, in the order the application keeps them. Adding a card places it at once:
     * at the position it requests ({@link RXDashboardCard#RXDashboardCard(String, Node, int, int, int, int)}), solved
     * by the {@link #collisionPolicyProperty() collisionPolicy}, or at the first free area when it requests none or
     * the policy rejects its request; several cards added at once are placed in list order. Removing a card leaves
     * the others where they are. A card added to another dashboard leaves this one. {@code null} elements and a
     * card's second occurrence are ignored. The order decides which of several cards sharing an ID keeps it (the
     * first), and the order cards are placed in whenever the pane places several by the same rule: cards added
     * together, cards a snapshot or a column-count change leaves to find their own place, cards placed again by their
     * requests. The cards are shown and focused in reading order whatever it is.
     *
     * @return the live list of cards
     */
    public final ObservableList<RXDashboardCard> getCards() {
        return cards;
    }

    // ==================== Column Count ====================

    private final IntegerProperty columnCount = new StyleableIntegerProperty(DEFAULT_COLUMN_COUNT) {
        @Override
        protected void invalidated() {
            int value = get();
            if (value < 1) {
                if (!isBound()) {
                    set(lastValidColumnCount);
                    throw new IllegalArgumentException("columnCount must be at least 1: " + value);
                }
                return;
            }
            // A bound property is invalidated on every change of its source, even to the same value.
            if (value == lastValidColumnCount) {
                return;
            }
            lastValidColumnCount = value;
            reconcile();
        }

        // Throwing here would make the CSS engine reset the property to its initial value, 12, and the layout would
        // be converted to 12 columns.
        @Override
        public void applyStyle(StyleOrigin origin, Number value) {
            if (value == null || value.intValue() < 1) {
                LOGGER.warning("Ignored -rx-column-count below 1: " + value);
                return;
            }
            super.applyStyle(origin, value);
        }

        // A binding left at an invalid value would leave the property there for good.
        @Override
        public void unbind() {
            super.unbind();
            if (get() < 1) {
                set(lastValidColumnCount);
            }
        }

        @Override
        public CssMetaData<RXDashboardPane, Number> getCssMetaData() {
            return StyleableProperties.COLUMN_COUNT;
        }

        @Override
        public Object getBean() {
            return RXDashboardPane.this;
        }

        @Override
        public String getName() {
            return "columnCount";
        }
    };

    /**
     * The number of columns, 12 by default; settable from CSS as {@code -rx-column-count}. Changing it converts the
     * layout to the new count at once: each card's left and right edges scale and round, so cards that shared an edge
     * still share it, and overlaps and overflows are then corrected as by {@link #applyLayout}. The conversion always
     * starts from the last layout not produced by a column-count change, so changing the count and changing it back
     * with no edit in between restores the layout exactly, and a column count set by a style sheet after a snapshot
     * was applied still yields the snapshot. Until the pane is first laid out, a change instead places every card
     * again as if the count had been set before the first card was added, repeating in order the cards added and
     * removed, the snapshots applied and the edits made so far, with the cards' current locks and span limits: FXML
     * sets the count after adding the cards, and a style sheet sets it at the first CSS pass. A card taken out
     * meanwhile and not yet in another dashboard is given the cells it would have left, which it requests when added
     * again. A change that comes after the layout was changed following an earlier change converts instead, since
     * what came after a count was set refers to that count; so does every change once the pane has been laid out, or
     * once more than 512 cards were added, removed or edited, or snapshots applied, before it. Values above 4096
     * count as
     * 4096.
     *
     * <p>A value below 1 is rejected with {@link IllegalArgumentException} and the property goes back to its last
     * valid value. When the property is bound to an invalid value it cannot go back: the pane keeps laying out with
     * the last valid count until a valid value arrives, and unbinding it then restores the last valid value. A style
     * sheet value below 1 is ignored with a logged warning, as if the style sheet set no column count.</p>
     *
     * @return the column-count property
     */
    public final IntegerProperty columnCountProperty() {
        return columnCount;
    }

    /**
     * Returns the number of columns.
     *
     * @return the column count
     */
    public final int getColumnCount() {
        return columnCount.get();
    }

    /**
     * Sets the number of columns.
     *
     * @param value the column count, at least 1
     * @throws IllegalArgumentException if {@code value} is less than 1
     */
    public final void setColumnCount(int value) {
        columnCount.set(value);
    }

    // ==================== Row Height ====================

    private final DoubleProperty rowHeight = new StyleableDoubleProperty(DEFAULT_ROW_HEIGHT) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public CssMetaData<RXDashboardPane, Number> getCssMetaData() {
            return StyleableProperties.ROW_HEIGHT;
        }

        @Override
        public Object getBean() {
            return RXDashboardPane.this;
        }

        @Override
        public String getName() {
            return "rowHeight";
        }
    };

    /**
     * The height of one row in pixels, 80 by default; settable from CSS as {@code -rx-row-height}. A card spanning
     * {@code n} rows is {@code n} rows plus {@code n - 1} gaps high, whatever the width. A value that is not finite or
     * not positive is accepted and lays out as 80.
     *
     * @return the row-height property
     */
    public final DoubleProperty rowHeightProperty() {
        return rowHeight;
    }

    /**
     * Returns the row height.
     *
     * @return the row height
     */
    public final double getRowHeight() {
        return rowHeight.get();
    }

    /**
     * Sets the row height.
     *
     * @param value the row height in pixels
     */
    public final void setRowHeight(double value) {
        rowHeight.set(value);
    }

    // ==================== Hgap ====================

    private final DoubleProperty hgap = gapProperty("hgap", true);

    /**
     * The horizontal gap between columns, 10 by default; settable from CSS as {@code -rx-hgap}. A value that is not
     * finite or is negative is accepted and lays out as 0. Spare pixels left by rounding the columns to the device
     * grid widen the gaps slightly.
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
     * @param value the horizontal gap in pixels
     */
    public final void setHgap(double value) {
        hgap.set(value);
    }

    // ==================== Vgap ====================

    private final DoubleProperty vgap = gapProperty("vgap", false);

    /**
     * The vertical gap between rows, 10 by default; settable from CSS as {@code -rx-vgap}. A value that is not finite
     * or is negative is accepted and lays out as 0.
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
     * @param value the vertical gap in pixels
     */
    public final void setVgap(double value) {
        vgap.set(value);
    }

    private DoubleProperty gapProperty(String name, boolean horizontal) {
        return new StyleableDoubleProperty(DEFAULT_GAP) {
            @Override
            protected void invalidated() {
                requestLayout();
                // The resize handles reach into the gap.
                for (RXDashboardCard card : members) {
                    card.requestLayout();
                }
            }

            @Override
            public CssMetaData<RXDashboardPane, Number> getCssMetaData() {
                return horizontal ? StyleableProperties.HGAP : StyleableProperties.VGAP;
            }

            @Override
            public Object getBean() {
                return RXDashboardPane.this;
            }

            @Override
            public String getName() {
                return name;
            }
        };
    }

    // ==================== Min Column Width ====================

    private final DoubleProperty minColumnWidth = new SimpleDoubleProperty(this, "minColumnWidth",
            DEFAULT_MIN_COLUMN_WIDTH) {
        @Override
        protected void invalidated() {
            requestLayout();
        }
    };

    /**
     * The narrowest a column may get, which sets the pane's minimum width: the column count times this value, plus
     * the gaps and insets. 0 by default, so columns narrow without limit; inside a {@code ScrollPane} that fits its
     * width to the viewport, a positive value makes a horizontal scroll bar appear instead once the viewport is
     * narrower. A value that is not finite or is negative is accepted and counts as 0.
     *
     * @return the minimum-column-width property
     */
    public final DoubleProperty minColumnWidthProperty() {
        return minColumnWidth;
    }

    /**
     * Returns the narrowest column width.
     *
     * @return the minimum column width
     */
    public final double getMinColumnWidth() {
        return minColumnWidth.get();
    }

    /**
     * Sets the narrowest column width.
     *
     * @param value the minimum column width in pixels
     */
    public final void setMinColumnWidth(double value) {
        minColumnWidth.set(value);
    }

    // ==================== Collision Policy ====================

    private final ObjectProperty<CollisionPolicy> collisionPolicy =
            new SimpleObjectProperty<>(this, "collisionPolicy", DEFAULT_COLLISION_POLICY) {
                @Override
                protected void invalidated() {
                    CollisionPolicy policy = get() == null ? CollisionPolicy.PUSH : get();
                    if (policy == lastPolicy) {
                        return;
                    }
                    lastPolicy = policy;
                    gesture.cancel();
                    if (history != null) {
                        replayPending = true;
                        reconcile();
                    }
                }
            };

    /**
     * How an edit whose area covers other cards is resolved, for user gestures, code and cards added with a
     * requested position alike. {@link CollisionPolicy#PUSH PUSH} by default; {@code null} counts as
     * {@code PUSH}. Until the pane is first laid out, a change places every card again as if the policy had been set
     * before the first card was added, with the cards' current locks and span limits: FXML sets the policy after
     * adding the cards. That stops once a column-count change had to convert the layout instead, or after more than
     * 512 such operations (see {@link #columnCountProperty()}); a change then applies to later edits only.
     *
     * @return the collision-policy property
     */
    public final ObjectProperty<CollisionPolicy> collisionPolicyProperty() {
        return collisionPolicy;
    }

    /**
     * Returns the collision policy.
     *
     * @return the collision policy, possibly {@code null}
     */
    public final CollisionPolicy getCollisionPolicy() {
        return collisionPolicy.get();
    }

    /**
     * Sets the collision policy.
     *
     * @param value the collision policy, or {@code null} for {@code PUSH}
     */
    public final void setCollisionPolicy(CollisionPolicy value) {
        collisionPolicy.set(value);
    }

    // ==================== Auto Compact ====================

    private final BooleanProperty autoCompact = new SimpleBooleanProperty(this, "autoCompact", DEFAULT_AUTO_COMPACT) {
        @Override
        protected void invalidated() {
            if (get() == lastAutoCompact) {
                return;
            }
            lastAutoCompact = get();
            gesture.cancel();
            if (history != null) {
                replayPending = true;
            }
            reconcile();
        }
    };

    /**
     * Whether the cards are kept packed upward, on by default. After every change, from the user or from code, each
     * card that is not locked rises as far as nothing is in its way, so the layout never keeps a hole above a card:
     * dragging a card into empty space below drops it at the highest row it can reach, and removing a card or making
     * one smaller lets the cards below it move up. While dragging, a card that covers more than half of a touching
     * neighbour exchanges places with it when both have the same size and share a row or column, or share a row and
     * have the same height, or share a column and have the same width; a card dragged down onto cards that could
     * take its place goes below them. Locked cards never move and hold up the cards below them.
     * {@link #applyLayout} packs the snapshot it applies, so {@link #getLayout()} may then differ from it. Turning it
     * on packs the layout at once; turning it off moves nothing. Until the pane is first laid out, a change places
     * every card again as if it had been set before the first card was added, as for the collision policy: FXML sets
     * it after adding the cards. Not settable from CSS: a style sheet's value flips back to the initial value
     * whenever the pane's styles are rebuilt, which would pack away holes the user left on purpose.
     *
     * @return the auto-compact property
     */
    public final BooleanProperty autoCompactProperty() {
        return autoCompact;
    }

    /**
     * Returns whether the cards are kept packed upward.
     *
     * @return whether the layout is kept compact
     */
    public final boolean isAutoCompact() {
        return autoCompact.get();
    }

    /**
     * Sets whether the cards are kept packed upward.
     *
     * @param value whether to keep the layout compact
     */
    public final void setAutoCompact(boolean value) {
        autoCompact.set(value);
    }

    // ==================== Editable ====================

    private final BooleanProperty editable = new SimpleBooleanProperty(this, "editable", true) {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(EDITABLE_PSEUDO_CLASS, get());
            if (!get()) {
                gesture.cancel();
            }
            for (RXDashboardCard card : members) {
                card.requestLayout();
            }
        }
    };

    /**
     * Whether the user can drag and resize cards. On by default; an application usually binds it to an "edit
     * layout" switch, so a slight move while pressing a chart does not drag the card. Off, the cards show no resize
     * handles and ignore drags; code can still change the layout. Sets the {@code :editable} pseudo-class.
     *
     * @return the editable property
     */
    public final BooleanProperty editableProperty() {
        return editable;
    }

    /**
     * Returns whether the user can drag and resize cards.
     *
     * @return whether the dashboard is editable
     */
    public final boolean isEditable() {
        return editable.get();
    }

    /**
     * Sets whether the user can drag and resize cards.
     *
     * @param value whether the dashboard is editable
     */
    public final void setEditable(boolean value) {
        editable.set(value);
    }

    // ==================== Live Resize ====================

    private final BooleanProperty liveResize = new SimpleBooleanProperty(this, "liveResize", true) {
        @Override
        protected void invalidated() {
            if (gesture.isActive()) {
                requestLayout();
            }
        }
    };

    /**
     * Whether a card the user resizes changes size with the pointer, laying its content out at every step, or keeps
     * its size until the pointer is released while only the placeholder follows. On by default; turn it off for
     * content too heavy to lay out at every frame.
     *
     * @return the live-resize property
     */
    public final BooleanProperty liveResizeProperty() {
        return liveResize;
    }

    /**
     * Returns whether resized cards change size with the pointer.
     *
     * @return whether live resize is on
     */
    public final boolean isLiveResize() {
        return liveResize.get();
    }

    /**
     * Sets whether resized cards change size with the pointer.
     *
     * @param value whether live resize is on
     */
    public final void setLiveResize(boolean value) {
        liveResize.set(value);
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
        public CssMetaData<RXDashboardPane, Boolean> getCssMetaData() {
            return StyleableProperties.ANIMATED;
        }

        @Override
        public Object getBean() {
            return RXDashboardPane.this;
        }

        @Override
        public String getName() {
            return "animated";
        }
    };

    /**
     * Whether cards glide to their new positions when the layout changes, and fade in, rising a few pixels, when
     * added while the pane shows. On by default; settable from CSS as {@code -rx-animated}. Turning it off mid-flight
     * snaps every card into place. A width change imposed by the parent (a window edge dragged) applies at once, so
     * cards never trail it. A removed card disappears at once.
     *
     * <p>While a card glides, the pane drives its {@code translateX} and {@code translateY}, and its {@code opacity}
     * while it fades in; values the card had before are restored when the motion ends.</p>
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether layout changes animate.
     *
     * @return whether animation is on
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether layout changes animate.
     *
     * @param value whether animation is on
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
                public CssMetaData<RXDashboardPane, Duration> getCssMetaData() {
                    return StyleableProperties.ANIMATION_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXDashboardPane.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * The duration of a glide, 200 milliseconds by default; settable from CSS as {@code -rx-animation-duration}. A
     * {@code null}, non-positive, unknown or indefinite value is accepted and turns animation off, like
     * {@code animated=false}.
     *
     * @return the animation-duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the glide duration.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the glide duration.
     *
     * @param value the duration; {@code null} or a non-positive value turns animation off
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Animation Interpolator ====================

    private final ObjectProperty<Interpolator> animationInterpolator =
            new SimpleObjectProperty<>(this, "animationInterpolator", DEFAULT_ANIMATION_INTERPOLATOR);

    /**
     * The interpolator of a glide, {@link Interpolator#EASE_BOTH} by default; {@code null} counts as
     * {@code EASE_BOTH}. Not settable from CSS.
     *
     * @return the animation-interpolator property
     */
    public final ObjectProperty<Interpolator> animationInterpolatorProperty() {
        return animationInterpolator;
    }

    /**
     * Returns the glide interpolator.
     *
     * @return the animation interpolator, possibly {@code null}
     */
    public final Interpolator getAnimationInterpolator() {
        return animationInterpolator.get();
    }

    /**
     * Sets the glide interpolator.
     *
     * @param value the interpolator, or {@code null} for {@code EASE_BOTH}
     */
    public final void setAnimationInterpolator(Interpolator value) {
        animationInterpolator.set(value);
    }

    // ==================== Layout Snapshot ====================

    private final ReadOnlyObjectWrapper<RXDashboardLayout> layout = new ReadOnlyObjectWrapper<>(this, "layout");

    /**
     * A snapshot of the committed layout: the column count and, for every card with an ID, its placement. It
     * changes after every change of the layout, made by the user or by code, a restored snapshot and a column-count
     * change included, and never during a gesture, whose preview stays private until the user releases. Save the
     * layout from a listener on this property; a listener registered before the layout is restored also saves the
     * layout the pane actually adopted. Cards without an ID take no part in it.
     *
     * @return the layout property
     */
    public final ReadOnlyObjectProperty<RXDashboardLayout> layoutProperty() {
        return layout.getReadOnlyProperty();
    }

    /**
     * Returns the snapshot of the committed layout.
     *
     * @return the current layout, never {@code null}
     */
    public final RXDashboardLayout getLayout() {
        return layout.get();
    }

    // ==================== On Layout Changed ====================

    private final ObjectProperty<EventHandler<RXDashboardEvent>> onLayoutChanged = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            setEventHandler(RXDashboardEvent.LAYOUT_CHANGED, get());
        }

        @Override
        public Object getBean() {
            return RXDashboardPane.this;
        }

        @Override
        public String getName() {
            return "onLayoutChanged";
        }
    };

    /**
     * The handler of {@link RXDashboardEvent#LAYOUT_CHANGED}, fired after the user commits a drag or resize that
     * changed the layout.
     *
     * @return the on-layout-changed property
     */
    public final ObjectProperty<EventHandler<RXDashboardEvent>> onLayoutChangedProperty() {
        return onLayoutChanged;
    }

    /**
     * Returns the layout-changed handler.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<RXDashboardEvent> getOnLayoutChanged() {
        return onLayoutChanged.get();
    }

    /**
     * Sets the layout-changed handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnLayoutChanged(EventHandler<RXDashboardEvent> value) {
        onLayoutChanged.set(value);
    }

    // ==================== Editing by code ====================

    /**
     * Moves a card toward the given column and row for its top-left cell, keeping its span. The position is first
     * clamped into the grid. Other cards make room by the {@link #collisionPolicyProperty() collisionPolicy}; the
     * card itself may be locked. With {@link #autoCompactProperty() autoCompact} under {@code PUSH}, the card is moved
     * as a user dragging it there in a straight line would move it, cell by cell, exchanging places and passing
     * cards on the way, and then everything packs upward; where it ends up depends on where it started and may differ
     * from the requested cell, as may the result of a hand-drawn drag to the same cell.
     *
     * @param card   a card of this dashboard
     * @param column the new column
     * @param row    the new row
     * @return {@code true} if the layout changed; {@code false} if the move was rejected (it covers another locked
     *         card, or any card under {@code BLOCK}) or left every card where it was
     * @throws NullPointerException     if {@code card} is {@code null}
     * @throws IllegalArgumentException if {@code card} is not one of this dashboard's cards
     * @throws IllegalStateException    if called from a listener on a card's placement or on {@code layout}
     */
    public boolean moveCard(RXDashboardCard card, int column, int row) {
        Objects.requireNonNull(card, "card cannot be null");
        checkNotNotifying();
        Placement current = placementOf(card);
        record(History.of(Step.MOVED, card, new Placement(column, row, 1, 1)));
        if (dragsByCode(card)) {
            gesture.cancel();
            Placement target = DashboardSolver.clamp(item(card, current), current.withPosition(column, row),
                    effectiveColumns);
            Map<RXDashboardCard, Placement> result = dragAlong(committedPlacements(), card, target, effectiveColumns);
            boolean changed = changesPlacements(result);
            commitEdit(result);
            return changed;
        }
        Map<RXDashboardCard, Placement> before = committedPlacements();
        return edit(card, current.withPosition(column, row)) && !before.equals(committedPlacements());
    }

    /**
     * Resizes a card to the given column and row span, keeping its top-left cell where the grid allows. The spans are
     * first clamped into the card's span limits and the grid; a card that cannot keep its column at its new width
     * moves left. Other cards make room by the {@link #collisionPolicyProperty() collisionPolicy}; the card itself
     * may be locked.
     *
     * @param card       a card of this dashboard
     * @param columnSpan the new column span
     * @param rowSpan    the new row span
     * @return {@code true} if the card has the clamped span afterwards, {@code false} if the resize was rejected (it
     *         covers another locked card, or any card under {@code BLOCK}) and nothing changed
     * @throws NullPointerException     if {@code card} is {@code null}
     * @throws IllegalArgumentException if {@code card} is not one of this dashboard's cards
     * @throws IllegalStateException    if called from a listener on a card's placement or on {@code layout}
     */
    public boolean resizeCard(RXDashboardCard card, int columnSpan, int rowSpan) {
        Objects.requireNonNull(card, "card cannot be null");
        checkNotNotifying();
        Placement current = placementOf(card);
        record(History.of(Step.RESIZED, card, new Placement(0, 0, columnSpan, rowSpan)));
        return edit(card, resizeRequest(current, columnSpan, rowSpan, effectiveColumns));
    }

    /**
     * Moves every card that is not locked straight up as far as it goes, top to bottom, left to right, closing the
     * holes pushes leave behind. Locked cards stay; no card changes column. Compacting twice changes nothing more.
     * With {@link #autoCompactProperty() autoCompact} the layout is always compact, so this changes nothing and does
     * not interrupt a gesture.
     *
     * @throws IllegalStateException if called from a listener on a card's placement or on {@code layout}
     */
    public void compact() {
        checkNotNotifying();
        if (lastAutoCompact) {
            return;
        }
        gesture.cancel();
        record(History.of(Step.COMPACTED, null, null));
        commitEdit(DashboardSolver.compact(items()));
    }

    /**
     * Applies a layout snapshot. Never throws for a snapshot it is given; it corrects whatever does not fit, by fixed
     * rules, and {@link #getLayout()} then shows what the pane adopted:
     * <ul>
     *     <li>a snapshot entry with no card of that ID is ignored;</li>
     *     <li>a snapshot taken at another column count is converted to this one: each card's left and right edges
     *     scale and round;</li>
     *     <li>placements outside the grid are clamped: negative positions become 0, spans at least 1 and within the
     *     card's span limits and the column count, and cards move left until they fit;</li>
     *     <li>overlaps are removed: locked cards keep their area first, then the others top to bottom, left to right,
     *     each moving straight down until clear;</li>
     *     <li>cards the snapshot does not mention (new ones, or ones without an ID) follow in list order: each keeps
     *     its place if it is free, otherwise goes to the first free area with its span.</li>
     * </ul>
     * Locked cards are moved like any other. With {@link #autoCompactProperty() autoCompact} the result then packs
     * upward, so a snapshot with holes is not kept as it was. The snapshot also becomes the layout later column-count
     * changes convert from; they handle the cards it does not mention by the last rule.
     *
     * @param layout the snapshot to apply
     * @throws NullPointerException  if {@code layout} is {@code null}
     * @throws IllegalStateException if called from a listener on a card's placement or on {@code layout}
     */
    public void applyLayout(RXDashboardLayout layout) {
        Objects.requireNonNull(layout, "layout cannot be null");
        checkNotNotifying();
        gesture.cancel();
        Map<RXDashboardCard, Placement> target = targetsOf(layout, members);
        record(new History(Step.APPLIED, null, null, Map.copyOf(target), layout.getColumnCount(),
                List.copyOf(members)));
        Map<RXDashboardCard, Placement> result = packed(DashboardSolver.arrange(items(), target,
                layout.getColumnCount(), effectiveColumns, effectiveColumns));
        base.clear();
        target.forEach((card, placement) -> base.put(card, new BaseEntry(placement, layout.getColumnCount())));
        commit(result);
    }

    // The snapshot placement of each card the snapshot names, by the cards' IDs: the first card with an ID uses it.
    private static Map<RXDashboardCard, Placement> targetsOf(RXDashboardLayout layout,
                                                           Iterable<RXDashboardCard> cards) {
        Map<String, RXDashboardCard> byId = new LinkedHashMap<>();
        for (RXDashboardCard card : cards) {
            if (RXDashboardLayout.isUsableId(card.getCardId())) {
                byId.putIfAbsent(card.getCardId(), card);
            }
        }
        Map<RXDashboardCard, Placement> target = new LinkedHashMap<>();
        layout.getPlacements().forEach((id, placement) -> {
            RXDashboardCard card = byId.get(id);
            if (card != null) {
                target.put(card, placement);
            }
        });
        return target;
    }

    // A resize keeps the top-left cell where the grid allows: the column span is first limited to the columns left.
    private static Placement resizeRequest(Placement current, int columnSpan, int rowSpan, int columns) {
        return current.withSpan(Math.max(1, Math.min(columnSpan, columns - current.column())), rowSpan);
    }

    private void record(History step) {
        if (history == null) {
            return;
        }
        if (columnsReplayed) {
            placedAfterColumns = true;
        }
        history.add(step);
        if (history.size() > HISTORY_LIMIT) {
            history = null;
        }
    }

    private boolean edit(RXDashboardCard card, Placement requested) {
        gesture.cancel();
        Placement candidate = DashboardSolver.clamp(item(card, requested), requested, effectiveColumns);
        Map<RXDashboardCard, Placement> result = DashboardSolver.solve(items(), card, candidate, isBlocking(),
                effectiveColumns);
        if (result == null) {
            return false;
        }
        commitEdit(packed(result));
        return true;
    }

    // Whether a move by code is a straight drag: when the cards pack under PUSH and the card is not locked (a locked
    // card would not pack, so it is placed where asked).
    private boolean dragsByCode(RXDashboardCard card) {
        return lastAutoCompact && !isBlocking() && !card.isLocked();
    }

    // A move by code with packing: the card is dragged in a straight line from its cell to the target, one cell per
    // step, on a regular grid of virtual pixels, as a user dragging it there would. Rows below every card are all
    // alike, so the walk stops a card's height below the lowest one.
    private Map<RXDashboardCard, Placement> dragAlong(Map<RXDashboardCard, Placement> work, RXDashboardCard card,
                                                     Placement target, int columns) {
        Placement from = work.get(card);
        int bottom = from.row();
        for (Map.Entry<RXDashboardCard, Placement> entry : work.entrySet()) {
            if (entry.getKey() != card) {
                bottom = Math.max(bottom, entry.getValue().row() + entry.getValue().rowSpan());
            }
        }
        int toColumn = target.column();
        int toRow = Math.min(target.row(), bottom + from.rowSpan());
        DashboardDrag<RXDashboardCard> drag = new DashboardDrag<>(items(work), card, columns, VIRTUAL_CELL,
                VIRTUAL_CELL, VIRTUAL_MARGIN, VIRTUAL_MARGIN);
        // The cells of a straight line, each next to the previous one (sideways, up or down, or diagonally).
        int x = from.column();
        int y = from.row();
        int dx = Math.abs(toColumn - x);
        int dy = -Math.abs(toRow - y);
        int sx = x < toColumn ? 1 : -1;
        int sy = y < toRow ? 1 : -1;
        int error = dx + dy;
        while (x != toColumn || y != toRow) {
            int doubled = 2 * error;
            if (doubled >= dy) {
                error += dy;
                x += sx;
            }
            if (doubled <= dx) {
                error += dx;
                y += sy;
            }
            drag.step(x * VIRTUAL_CELL, y * VIRTUAL_CELL);
        }
        return drag.layout();
    }

    // The layout packed upward when the pane keeps it compact.
    private Map<RXDashboardCard, Placement> packed(Map<RXDashboardCard, Placement> work) {
        return lastAutoCompact ? DashboardSolver.compact(items(work)) : work;
    }

    private Placement placementOf(RXDashboardCard card) {
        Objects.requireNonNull(card, "card cannot be null");
        if (card.owner != this || card.placement == null) {
            throw new IllegalArgumentException("Not a card of this dashboard: " + card);
        }
        return card.placement;
    }

    // Also while the pane reconciles: a listener of another dashboard reached from taking a card over must not
    // commit a change this reconciliation would then overwrite.
    private void checkNotNotifying() {
        if (notifying > 0 || reconciling) {
            throw new IllegalStateException("Cannot change the dashboard layout from a listener on a card's "
                    + "placement or on the layout property; change it from an event handler or after the "
                    + "notification");
        }
    }

    // ==================== Committing ====================

    // A commit made by an edit: when any card moved or changed span, the result becomes the base of later
    // column-count conversions.
    private void commitEdit(Map<RXDashboardCard, Placement> result) {
        if (changesPlacements(result)) {
            rebase(result);
        }
        commit(result);
    }

    private boolean changesPlacements(Map<RXDashboardCard, Placement> result) {
        for (RXDashboardCard card : members) {
            if (!Objects.equals(card.placement, result.get(card))) {
                return true;
            }
        }
        return false;
    }

    private void rebase(Map<RXDashboardCard, Placement> result) {
        base.clear();
        for (RXDashboardCard card : members) {
            base.put(card, new BaseEntry(result.get(card), effectiveColumns));
        }
    }

    // Records the result, then notifies: the cards' placement properties first, then the layout property. Changes
    // the listeners make to the cards or the columns are applied once the notification is over.
    private RXDashboardLayout commit(Map<RXDashboardCard, Placement> result) {
        boolean moved = changesPlacements(result);
        for (RXDashboardCard card : members) {
            card.placement = result.get(card);
        }
        RXDashboardLayout next = snapshot();
        if (moved) {
            sortChildren();
            requestLayout();
        }
        notifying++;
        try {
            for (RXDashboardCard card : new ArrayList<>(members)) {
                if (card.owner == this && card.placement != null) {
                    card.publish(card.placement);
                }
            }
            if (!next.equals(layout.get())) {
                layout.set(next);
            }
        } finally {
            notifying--;
        }
        if (reconcilePending && notifying == 0) {
            reconcile();
        }
        return next;
    }

    private RXDashboardLayout snapshot() {
        RXDashboardLayout.Builder builder = RXDashboardLayout.builder(effectiveColumns);
        cardsById().forEach((id, card) -> builder.card(id, card.placement));
        return builder.build();
    }

    // The cards that use their ID: the first card of the list with each non-empty ID.
    private Map<String, RXDashboardCard> cardsById() {
        Map<String, RXDashboardCard> byId = new LinkedHashMap<>();
        for (RXDashboardCard card : members) {
            String id = card.getCardId();
            if (RXDashboardLayout.isUsableId(id) && card.placement != null) {
                byId.putIfAbsent(id, card);
            }
        }
        return byId;
    }

    // The children are the placeholder, then the cards in reading order, which is also the focus traversal order. One
    // setAll keeps every card in the scene, so a focused node inside keeps the focus.
    private void sortChildren() {
        List<RXDashboardCard> sorted = new ArrayList<>(members);
        sorted.sort((a, b) -> {
            int byRow = Integer.compare(a.placement.row(), b.placement.row());
            return byRow != 0 ? byRow : Integer.compare(a.placement.column(), b.placement.column());
        });
        List<Node> children = new ArrayList<>(sorted.size() + 1);
        children.add(placeholder);
        children.addAll(sorted);
        if (!getChildren().equals(children)) {
            getChildren().setAll(children);
        }
    }

    // ==================== Reconciling ====================

    // Brings the committed layout in line with the card list, the column count and the cards' IDs, span limits and
    // locks. Called on every such change; during a notification it only marks itself pending, and the commit that
    // notifies runs it once the notification is over.
    private void reconcile() {
        if (notifying > 0 || reconciling) {
            reconcilePending = true;
            return;
        }
        // A preview in progress is stale only when reconciling will move a card or change the grid.
        if (changesLayout()) {
            gesture.cancel();
        }
        reconciling = true;
        try {
            int rounds = 0;
            do {
                reconcilePending = false;
                reconcileOnce();
                if (++rounds > MAX_RECONCILE_ROUNDS) {
                    throw new IllegalStateException("Listeners keep changing the dashboard on every notification");
                }
            } while (reconcilePending);
        } finally {
            reconciling = false;
        }
    }

    private boolean changesLayout() {
        if (replayPending || resolvedColumnCount() != effectiveColumns || !distinctCards().equals(members)) {
            return true;
        }
        for (RXDashboardCard card : members) {
            Placement placement = card.placement;
            if (card.owner != this || placement == null
                    || !DashboardSolver.clamp(item(card, placement), placement, effectiveColumns).equals(placement)) {
                return true;
            }
        }
        return false;
    }

    private void reconcileOnce() {
        List<RXDashboardCard> current = distinctCards();
        Set<RXDashboardCard> listed = identitySet(current);
        // A member another dashboard took over (during a notification, say) is taken in again like a new card.
        Set<RXDashboardCard> known = Collections.newSetFromMap(new IdentityHashMap<>());
        for (RXDashboardCard card : members) {
            if (card.owner == this) {
                known.add(card);
            }
        }
        for (RXDashboardCard card : new ArrayList<>(members)) {
            if (!listed.contains(card) || !known.contains(card)) {
                record(History.of(Step.REMOVED, card, card.owner == this ? card.placement : null));
                detach(card);
            }
        }
        members.clear();
        Map<RXDashboardCard, Placement> work = new LinkedHashMap<>();
        for (RXDashboardCard card : current) {
            if (known.contains(card)) {
                members.add(card);
                work.put(card, card.placement);
            }
        }
        boolean rebaseAll = false;

        int columns = resolvedColumnCount();
        boolean converted = columns != effectiveColumns;
        if (converted) {
            int oldColumns = effectiveColumns;
            effectiveColumns = columns;
            if (history != null && !placedAfterColumns) {
                work = replay(work, oldColumns, columns);
                columnsReplayed = true;
            } else {
                history = null;
                work = convert(work, oldColumns, columns);
            }
            // The grid changes even when no card's cells do.
            requestLayout();
        } else if (replayPending && history != null) {
            work = replay(work, columns, columns);
        }
        replayPending = false;

        for (RXDashboardCard card : new ArrayList<>(work.keySet())) {
            Placement now = work.get(card);
            Placement clamped = DashboardSolver.clamp(item(card, now), now, columns);
            if (!clamped.equals(now)) {
                work = placeEdited(work, card, clamped);
                rebaseAll = true;
            }
        }

        for (RXDashboardCard card : current) {
            if (known.contains(card)) {
                continue;
            }
            Placement request = card.requestedPlacement();
            attach(card);
            record(History.of(Step.ADDED, card, request));
            Map<RXDashboardCard, Placement> result = place(work, card, request, columns);
            // Pushing a card makes the result the base, as an edit does; otherwise only the new card joins it.
            if (movesAny(work, result)) {
                rebaseAll = true;
            }
            work = result;
            base.put(card, new BaseEntry(result.get(card), columns));
        }
        // Packing what a removal, an unlock, a span limit or an addition left: an edit, so the base follows; a
        // column-count conversion packs too but keeps converting from the base, or a round trip would not restore.
        Map<RXDashboardCard, Placement> packedWork = packed(work);
        if (!converted && movesAny(work, packedWork)) {
            rebaseAll = true;
        }
        work = packedWork;
        members.clear();
        members.addAll(current);
        Map<RXDashboardCard, Placement> ordered = new LinkedHashMap<>();
        for (RXDashboardCard card : members) {
            ordered.put(card, work.get(card));
        }
        if (rebaseAll) {
            for (RXDashboardCard card : members) {
                base.put(card, new BaseEntry(ordered.get(card), columns));
            }
        }
        commit(ordered);
    }

    // A column-count change applies the base layout again at the new count, as applyLayout would: the cards it holds
    // are converted and corrected first, the others then try their own place, converted, or take the first free area.
    private Map<RXDashboardCard, Placement> convert(Map<RXDashboardCard, Placement> work, int oldColumns,
                                                    int columns) {
        Map<RXDashboardCard, Placement> target = new LinkedHashMap<>();
        work.forEach((card, placement) -> {
            BaseEntry entry = base.get(card);
            if (entry != null) {
                target.put(card, DashboardSolver.rescale(entry.placement(), entry.columns(), columns));
            }
        });
        return DashboardSolver.arrange(items(work), target, columns, oldColumns, columns);
    }

    // Before the first layout: every card placed again from the history, at the given column count and under the
    // current policy, the base layout rebuilt by the same rules on the way; what setting both before the first card
    // would have produced. Should the history not account for a card, the base layout is converted instead.
    private Map<RXDashboardCard, Placement> replay(Map<RXDashboardCard, Placement> work, int oldColumns,
                                                   int columns) {
        Map<RXDashboardCard, Placement> state = new LinkedHashMap<>();
        Map<RXDashboardCard, BaseEntry> rebuilt = new IdentityHashMap<>();
        // Where each removed card was, live and in the replay: a card put back asks for the position it left, which
        // it published at the count of the time.
        Map<RXDashboardCard, Placement[]> removedAt = new IdentityHashMap<>();
        Map<RXDashboardCard, Integer> removedStep = new IdentityHashMap<>();
        for (int index = 0; index < history.size(); index++) {
            History step = history.get(index);
            RXDashboardCard card = step.card();
            Map<RXDashboardCard, Placement> result = state;
            switch (step.step()) {
                case ADDED -> {
                    Placement request = step.asked();
                    Placement[] left = removedAt.get(card);
                    if (left != null && request.equals(left[0]) && left[1] != null) {
                        request = left[1];
                    }
                    result = packed(place(state, card, request, columns));
                    if (movesAny(state, result)) {
                        rebuild(rebuilt, result, columns);
                    }
                    rebuilt.put(card, new BaseEntry(result.get(card), columns));
                }
                case REMOVED -> {
                    removedAt.put(card, new Placement[] {step.asked(), state.get(card)});
                    removedStep.put(card, index);
                    Map<RXDashboardCard, Placement> rest = new LinkedHashMap<>(state);
                    rest.remove(card);
                    rebuilt.remove(card);
                    result = packed(rest);
                    if (movesAny(rest, result)) {
                        rebuild(rebuilt, result, columns);
                    }
                }
                case APPLIED -> {
                    // In the order and with the matching of the time it was applied.
                    Map<RXDashboardCard, Placement> ordered = new LinkedHashMap<>();
                    for (RXDashboardCard member : step.order()) {
                        if (state.containsKey(member)) {
                            ordered.put(member, state.get(member));
                        }
                    }
                    state.forEach(ordered::putIfAbsent);
                    Map<RXDashboardCard, Placement> target = new LinkedHashMap<>();
                    step.targets().forEach((c, p) -> {
                        if (ordered.containsKey(c)) {
                            target.put(c, p);
                        }
                    });
                    result = packed(DashboardSolver.arrange(items(ordered), target, step.columns(), columns,
                            columns));
                    rebuilt.clear();
                    target.forEach((c, p) -> rebuilt.put(c, new BaseEntry(p, step.columns())));
                }
                case MOVED, RESIZED -> {
                    Placement now = state.get(card);
                    if (now != null) {
                        Placement asked = step.step() == Step.MOVED
                                ? now.withPosition(step.asked().column(), step.asked().row())
                                : resizeRequest(now, step.asked().columnSpan(), step.asked().rowSpan(), columns);
                        Placement clamped = DashboardSolver.clamp(item(card, asked), asked, columns);
                        if (step.step() == Step.MOVED && dragsByCode(card)) {
                            result = dragAlong(state, card, clamped, columns);
                        } else {
                            Map<RXDashboardCard, Placement> solved = DashboardSolver.solve(items(state), card,
                                    clamped, isBlocking(), columns);
                            if (solved != null) {
                                result = packed(solved);
                            }
                        }
                    }
                    if (movesAny(state, result)) {
                        rebuild(rebuilt, result, columns);
                    }
                }
                case COMPACTED -> {
                    result = DashboardSolver.compact(items(state));
                    if (movesAny(state, result)) {
                        rebuild(rebuilt, result, columns);
                    }
                }
            }
            state = result;
        }
        if (!state.keySet().equals(work.keySet())) {
            history = null;
            return convert(work, oldColumns, columns);
        }
        base.clear();
        base.putAll(rebuilt);
        // A card still out shows the cells it left at the old count; it would request them when added again.
        for (Map.Entry<RXDashboardCard, Integer> removed : removedStep.entrySet()) {
            RXDashboardCard card = removed.getKey();
            Placement[] left = removedAt.get(card);
            if (!state.containsKey(card) && card.owner == null && left[0] != null && left[1] != null
                    && card.requestedPlacement().equals(left[0])) {
                history.set(removed.getValue(), History.of(Step.REMOVED, card, left[1]));
                card.publish(left[1]);
            }
        }
        Map<RXDashboardCard, Placement> ordered = new LinkedHashMap<>();
        for (RXDashboardCard card : work.keySet()) {
            ordered.put(card, state.get(card));
        }
        return ordered;
    }

    private static boolean movesAny(Map<RXDashboardCard, Placement> before, Map<RXDashboardCard, Placement> after) {
        for (Map.Entry<RXDashboardCard, Placement> entry : before.entrySet()) {
            if (!entry.getValue().equals(after.get(entry.getKey()))) {
                return true;
            }
        }
        return false;
    }

    private static void rebuild(Map<RXDashboardCard, BaseEntry> rebuilt, Map<RXDashboardCard, Placement> placements,
                                int columns) {
        rebuilt.clear();
        placements.forEach((card, placement) -> rebuilt.put(card, new BaseEntry(placement, columns)));
    }

    // Places a card by its request: a requested position solved by the policy (or the first free area when the policy
    // rejects it), no position the first free area; the spans come from the request.
    private Map<RXDashboardCard, Placement> place(Map<RXDashboardCard, Placement> work, RXDashboardCard card,
                                                  Placement request, int columns) {
        Map<RXDashboardCard, Placement> others = new LinkedHashMap<>(work);
        others.remove(card);
        Item<RXDashboardCard> added = item(card, request);
        return request.column() >= 0 && request.row() >= 0
                ? DashboardSolver.add(items(others), added, request, isBlocking(), columns)
                : DashboardSolver.addAtFirstFit(items(others), added, columns);
    }

    // An edit by the pane itself (a span limit raised): solved by the policy, or the card to the first free area.
    private Map<RXDashboardCard, Placement> placeEdited(Map<RXDashboardCard, Placement> work, RXDashboardCard card,
                                                        Placement candidate) {
        List<Item<RXDashboardCard>> items = items(work);
        Map<RXDashboardCard, Placement> solved = DashboardSolver.solve(items, card, candidate, isBlocking(),
                effectiveColumns);
        if (solved != null) {
            return solved;
        }
        List<Placement> others = new ArrayList<>();
        work.forEach((other, placement) -> {
            if (other != card) {
                others.add(placement);
            }
        });
        Map<RXDashboardCard, Placement> result = new LinkedHashMap<>(work);
        result.put(card, DashboardSolver.firstFit(others, candidate.columnSpan(), candidate.rowSpan(),
                effectiveColumns));
        return result;
    }

    private void attach(RXDashboardCard card) {
        RXDashboardPane previous = card.owner;
        if (previous != null && previous != this) {
            previous.getCards().removeIf(other -> other == card);
        }
        card.owner = this;
        card.placement = null;
        // Its handles show by this pane's editable state, even if its size stays the same.
        card.requestLayout();
        if (card.getParent() != this) {
            getChildren().add(card);
        }
        membership.added(card, animationsActive());
    }

    private void detach(RXDashboardCard card) {
        base.remove(card);
        pendingShown.remove(card);
        resizeGate.reset();
        if (card.getParent() == this) {
            membership.removed(card, animator.isFading(card));
            getChildren().remove(card);
        }
        animator.forget(card);
        if (card.owner == this) {
            card.owner = null;
            card.placement = null;
            card.requestLayout();
        }
    }

    // Called by a card when its span limits or lock change. A lock changes what the preview of a gesture may push.
    void cardChanged(boolean lockChanged) {
        if (lockChanged) {
            gesture.cancel();
        }
        reconcile();
    }

    // Called by a card when its ID changes. A gesture reports the layout it started from, so one going on is
    // cancelled when the change shows in the snapshot.
    void cardIdChanged() {
        if (gesture.isActive() && !snapshot().equals(layout.get())) {
            gesture.cancel();
        }
        reconcile();
    }

    // Called by a card when its move or resize switch changes.
    void cardGestureFlagsChanged(RXDashboardCard card) {
        gesture.switchChanged(card);
        card.requestLayout();
    }

    // The room a card's resize handles may take beyond its right and bottom edges: the gap, which no other card's
    // handle uses, so a handle never lies over a neighbour.
    double handleRoomX() {
        return snapSpaceX(gapOrZero(getHgap()));
    }

    double handleRoomY() {
        return snapSpaceY(gapOrZero(getVgap()));
    }

    // ==================== Gesture hooks ====================

    Map<RXDashboardCard, Placement> committedPlacements() {
        Map<RXDashboardCard, Placement> placements = new LinkedHashMap<>();
        for (RXDashboardCard card : members) {
            placements.put(card, card.placement);
        }
        return placements;
    }

    DashboardGrid grid() {
        return grid;
    }

    // A card the user takes hold of is no longer the animator's: its translate follows the pointer.
    void releaseFromAnimation(RXDashboardCard card) {
        animator.forget(card);
    }

    // Whether a drag is worked out step by step: when the cards pack under PUSH.
    boolean dragsStepwise() {
        return lastAutoCompact && !isBlocking();
    }

    // A drag worked out step by step from the given layout, on the virtual grid the gesture converts the pointer to.
    DashboardDrag<RXDashboardCard> stepwiseDrag(Map<RXDashboardCard, Placement> start, RXDashboardCard card,
                                               DashboardGrid grid) {
        double marginX = VIRTUAL_CELL * gapOrZero(getHgap()) / 2.0 / grid.columnPitch();
        double marginY = VIRTUAL_CELL * gapOrZero(getVgap()) / 2.0 / grid.rowPitch();
        return new DashboardDrag<>(items(start), card, effectiveColumns, VIRTUAL_CELL, VIRTUAL_CELL, marginX,
                marginY);
    }

    // A pane x on the virtual grid of a stepwise drag: column c's left edge is c cells.
    static double virtualX(DashboardGrid grid, double x) {
        return (x - grid.columnX(0)) / grid.columnPitch() * VIRTUAL_CELL;
    }

    // A pane y on the virtual grid of a stepwise drag: row r's top edge is r cells.
    static double virtualY(DashboardGrid grid, double y) {
        return (y - grid.rowY(0)) / grid.rowPitch() * VIRTUAL_CELL;
    }

    Map<RXDashboardCard, Placement> solveFrom(Map<RXDashboardCard, Placement> start, RXDashboardCard card,
                                              Placement candidate) {
        Map<RXDashboardCard, Placement> solved = DashboardSolver.solve(items(start), card, candidate, isBlocking(),
                effectiveColumns);
        return solved == null ? null : packed(solved);
    }

    void glideFrom(RXDashboardCard card, double x, double y) {
        pendingShown.put(card, new Point2D(x, y));
    }

    // A committed gesture: the result becomes the base of later conversions, and the event comes last, once the
    // properties are notified and any change made from their listeners is applied.
    void commitUserEdit(Map<RXDashboardCard, Placement> result, RXDashboardCard edited, RXDashboardLayout before) {
        rebase(result);
        RXDashboardLayout committed = commit(result);
        fireEvent(new RXDashboardEvent(RXDashboardEvent.LAYOUT_CHANGED, this, committed, before, edited));
    }

    private List<RXDashboardCard> distinctCards() {
        List<RXDashboardCard> distinct = new ArrayList<>();
        Set<RXDashboardCard> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (RXDashboardCard card : cards) {
            if (card != null && seen.add(card)) {
                distinct.add(card);
            }
        }
        return distinct;
    }

    private static Set<RXDashboardCard> identitySet(List<RXDashboardCard> list) {
        Set<RXDashboardCard> set = Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(list);
        return set;
    }

    // ==================== Solver input ====================

    private List<Item<RXDashboardCard>> items() {
        List<Item<RXDashboardCard>> items = new ArrayList<>();
        for (RXDashboardCard card : members) {
            items.add(item(card, card.placement));
        }
        return items;
    }

    private List<Item<RXDashboardCard>> items(Map<RXDashboardCard, Placement> placements) {
        List<Item<RXDashboardCard>> items = new ArrayList<>();
        placements.forEach((card, placement) -> items.add(item(card, placement)));
        return items;
    }

    private static Item<RXDashboardCard> item(RXDashboardCard card, Placement placement) {
        return new Item<>(card, placement, card.isLocked(), card.getMinColumnSpan(), card.getMaxColumnSpan(),
                card.getMinRowSpan(), card.getMaxRowSpan());
    }

    private boolean isBlocking() {
        return getCollisionPolicy() == CollisionPolicy.BLOCK;
    }

    private int resolvedColumnCount() {
        return Math.min(lastValidColumnCount, MasonryColumns.MAX_RESOLVED_COLUMNS);
    }

    // ==================== Sizing ====================

    @Override
    protected double computeMinWidth(double height) {
        int columns = effectiveColumns;
        double content = columns * snapSizeX(minColumnWidthOrZero()) + (columns - 1) * snapSpaceX(gapOrZero(getHgap()));
        return PixelGrid.ceilX(this, snappedLeftInset() + content + snappedRightInset());
    }

    @Override
    protected double computePrefWidth(double height) {
        int columns = effectiveColumns;
        double content = columns * snapSizeX(rowHeightOrDefault()) + (columns - 1) * snapSpaceX(gapOrZero(getHgap()));
        return PixelGrid.ceilX(this, snappedLeftInset() + content + snappedRightInset());
    }

    @Override
    protected double computePrefHeight(double width) {
        int rows = rowCount();
        double row = snapSizeY(rowHeightOrDefault());
        double gap = snapSpaceY(gapOrZero(getVgap()));
        double content = rows == 0 ? 0.0 : rows * row + (rows - 1) * gap;
        return PixelGrid.ceilY(this, snappedTopInset() + content + snappedBottomInset());
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    // The rows the shown layout uses: the largest bottom row of any card, plus one during a gesture so a card can be
    // taken below the last row.
    private int rowCount() {
        int rows = 0;
        for (RXDashboardCard card : members) {
            Placement placement = gesture.displayedPlacement(card);
            if (placement != null) {
                rows = Math.max(rows, placement.row() + placement.rowSpan());
            }
        }
        return gesture.isActive() ? rows + 1 : rows;
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        // From the first layout on, a column-count or policy change converts the layout the user sees.
        history = null;
        // Sampled before anything below can reach a listener that takes the pane out of its scene.
        int sceneChangesAtStart = membership.sceneChanges();
        Scene scene = getScene();
        membership.beginPass();
        grid = computeGrid();
        boolean animate = isAnimated() && isAnimationDurationPositive() && membership.continuesIn(scene);
        boolean collectMoves = animate || animator.hasActiveState();
        List<RXDashboardCard> laidOut = new ArrayList<>(members);
        boolean resizeOnly = false;
        if (isAnimated()) {
            resizeOnly = resizeGate.isResizeOnly(getWidth(), ownPrefWidth(), structure(laidOut));
        } else {
            resizeGate.reset();
        }
        List<PaneRelayoutAnimator.Move> moves = collectMoves ? new ArrayList<>() : null;
        for (RXDashboardCard card : laidOut) {
            // A listener reached from laying out an earlier card may have taken this one away.
            Placement placement = gesture.displayedPlacement(card);
            if (card.getParent() != this || placement == null) {
                continue;
            }
            membership.laidOut(card);
            // Asked again for every card: a listener reached from laying out an earlier card may have ended it.
            if (card == gesture.card()) {
                gesture.layoutCard(grid);
                continue;
            }
            Point2D shownAfterGesture = pendingShown.remove(card);
            double oldVisualX = 0.0;
            double oldVisualY = 0.0;
            PassMembership.Shown shownWhenRemoved = null;
            if (collectMoves) {
                shownWhenRemoved = membership.shownWhenRemoved(card);
                if (shownAfterGesture != null) {
                    oldVisualX = shownAfterGesture.getX();
                    oldVisualY = shownAfterGesture.getY();
                } else {
                    oldVisualX = shownWhenRemoved != null
                            ? shownWhenRemoved.x() : card.getLayoutX() + card.getTranslateX();
                    oldVisualY = shownWhenRemoved != null
                            ? shownWhenRemoved.y() : card.getLayoutY() + card.getTranslateY();
                }
            }
            // Sized and placed directly from the grid lines: layoutInArea would snap the width a second time and can
            // push the card one device pixel into the gap.
            card.resize(grid.spanWidth(placement.column(), placement.columnSpan()),
                    grid.spanHeight(placement.row(), placement.rowSpan()));
            card.relocate(grid.columnX(placement.column()), grid.rowY(placement.row()));
            if (!collectMoves) {
                continue;
            }
            if (resizeOnly) {
                animator.keepInFlight(moves, card);
                continue;
            }
            if (shownWhenRemoved == null && !membership.wasLaidOut(card)) {
                if (membership.isEntering(card)) {
                    animator.addEnterMove(moves, card, 0.0,
                            PaneRelayoutAnimator.enterOffset(snapSpaceY(gapOrZero(getVgap()))), true);
                }
                continue;
            }
            if (shownWhenRemoved != null && shownWhenRemoved.fading()) {
                animator.addResumedMove(moves, card, oldVisualX, oldVisualY, shownWhenRemoved.opacity());
            } else {
                animator.addRelayoutMove(moves, card, oldVisualX, oldVisualY);
            }
        }
        if (collectMoves && membership.sceneChanges() == sceneChangesAtStart) {
            moves.removeIf(move -> move.node().getParent() != this || move.node() == gesture.card());
            animator.runRelayout(moves, animate && animationsActive(), getAnimationDuration(),
                    interpolatorOrDefault());
        }
        layoutPlaceholder();
        if (membership.sceneChanges() != sceneChangesAtStart) {
            animator.stopAll();
            membership.forget();
            return;
        }
        membership.endPass(scene);
    }

    private void layoutPlaceholder() {
        Placement target = gesture.landing();
        placeholder.setVisible(target != null);
        if (target != null) {
            placeholder.resizeRelocate(grid.columnX(target.column()), grid.rowY(target.row()),
                    grid.spanWidth(target.column(), target.columnSpan()),
                    grid.spanHeight(target.row(), target.rowSpan()));
        }
    }

    DashboardGrid computeGrid() {
        double left = snappedLeftInset();
        double top = snappedTopInset();
        double contentWidth = Math.max(0.0, getWidth() - left - snappedRightInset());
        MasonryColumns.Resolution columns = MasonryColumns.resolve(contentWidth, contentWidth, effectiveColumns,
                1.0, snapSpaceX(gapOrZero(getHgap())), 0, true, RXBreakpointProfile.BOOTSTRAP, null,
                snapScale(true));
        return new DashboardGrid(columns, effectiveColumns, left, top, snapSizeY(rowHeightOrDefault()),
                snapSpaceY(gapOrZero(getVgap())), snapScale(false));
    }

    // The render scale the pane's edges snap to, or 0 when it does not snap to pixels.
    private double snapScale(boolean horizontal) {
        if (!isSnapToPixel()) {
            return 0.0;
        }
        Scene scene = getScene();
        Window window = scene == null ? null : scene.getWindow();
        if (window == null) {
            return 1.0;
        }
        double scale = horizontal ? window.getRenderScaleX() : window.getRenderScaleY();
        return Double.isFinite(scale) && scale > 0.0 ? scale : 1.0;
    }

    // What goes where: a pass with the same structure and only a new width is a window resize, which starts no
    // glide. Records hold no state beyond the card and its placement, so equal lists mean the same layout.
    private List<Object> structure(List<RXDashboardCard> laidOut) {
        List<Object> structure = new ArrayList<>(laidOut.size() * 2);
        for (RXDashboardCard card : laidOut) {
            structure.add(card);
            structure.add(gesture.displayedPlacement(card));
        }
        return structure;
    }

    // The preferred width from the pane's own inputs; an explicit prefWidth, bound to the parent's width say, follows
    // a resize and must not count as the pane changing itself.
    private double ownPrefWidth() {
        return getPrefWidth() == USE_COMPUTED_SIZE ? prefWidth(-1) : computePrefWidth(-1);
    }

    // ==================== Helpers ====================

    private boolean animationsActive() {
        return isAnimated() && isAnimationDurationPositive();
    }

    private boolean isAnimationDurationPositive() {
        Duration duration = getAnimationDuration();
        return duration != null && !duration.isUnknown() && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO);
    }

    private Interpolator interpolatorOrDefault() {
        Interpolator value = getAnimationInterpolator();
        return value == null ? DEFAULT_ANIMATION_INTERPOLATOR : value;
    }

    private double rowHeightOrDefault() {
        double value = getRowHeight();
        return Double.isFinite(value) && value > 0.0 ? value : DEFAULT_ROW_HEIGHT;
    }

    private double minColumnWidthOrZero() {
        double value = getMinColumnWidth();
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }

    private static double gapOrZero(double gap) {
        return Double.isFinite(gap) && gap > 0.0 ? gap : 0.0;
    }

    // ==================== CSS ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXDashboardPane, Number> COLUMN_COUNT =
                new CssMetaData<>("-rx-column-count", SizeConverter.getInstance(), DEFAULT_COLUMN_COUNT) {
                    @Override
                    public boolean isSettable(RXDashboardPane node) {
                        return !node.columnCount.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXDashboardPane node) {
                        return (StyleableProperty<Number>) node.columnCountProperty();
                    }
                };

        private static final CssMetaData<RXDashboardPane, Number> ROW_HEIGHT =
                new CssMetaData<>("-rx-row-height", SizeConverter.getInstance(), DEFAULT_ROW_HEIGHT) {
                    @Override
                    public boolean isSettable(RXDashboardPane node) {
                        return !node.rowHeight.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXDashboardPane node) {
                        return (StyleableProperty<Number>) node.rowHeightProperty();
                    }
                };

        private static final CssMetaData<RXDashboardPane, Number> HGAP =
                new CssMetaData<>("-rx-hgap", SizeConverter.getInstance(), DEFAULT_GAP) {
                    @Override
                    public boolean isSettable(RXDashboardPane node) {
                        return !node.hgap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXDashboardPane node) {
                        return (StyleableProperty<Number>) node.hgapProperty();
                    }
                };

        private static final CssMetaData<RXDashboardPane, Number> VGAP =
                new CssMetaData<>("-rx-vgap", SizeConverter.getInstance(), DEFAULT_GAP) {
                    @Override
                    public boolean isSettable(RXDashboardPane node) {
                        return !node.vgap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXDashboardPane node) {
                        return (StyleableProperty<Number>) node.vgapProperty();
                    }
                };

        private static final CssMetaData<RXDashboardPane, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated", BooleanConverter.getInstance(), DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXDashboardPane node) {
                        return !node.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXDashboardPane node) {
                        return (StyleableProperty<Boolean>) node.animatedProperty();
                    }
                };

        private static final CssMetaData<RXDashboardPane, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration", DurationConverter.getInstance(),
                        DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXDashboardPane node) {
                        return !node.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXDashboardPane node) {
                        return (StyleableProperty<Duration>) node.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Region.getClassCssMetaData());
            styleables.add(COLUMN_COUNT);
            styleables.add(ROW_HEIGHT);
            styleables.add(HGAP);
            styleables.add(VGAP);
            styleables.add(ANIMATED);
            styleables.add(ANIMATION_DURATION);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata of this class and its superclasses.
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
