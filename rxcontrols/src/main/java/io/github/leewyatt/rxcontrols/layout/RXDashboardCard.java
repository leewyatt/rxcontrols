package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout.Placement;
import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.Region;

import java.util.Objects;

/**
 * A card on an {@link RXDashboardPane}: a container for one content node that the pane places on its grid and the
 * user can drag and resize.
 *
 * <p>The pane owns the card's position. {@link #columnProperty() column}, {@link #rowProperty() row},
 * {@link #columnSpanProperty() columnSpan} and {@link #rowSpanProperty() rowSpan} are read-only and change only
 * through the pane: user gestures, {@link RXDashboardPane#moveCard}, {@link RXDashboardPane#resizeCard},
 * {@link RXDashboardPane#compact()} and {@link RXDashboardPane#applyLayout}. A position given to the constructor is a
 * request the pane honours, by its collision rules, when the card is added; without one the card goes to the first
 * free area.</p>
 *
 * <pre>{@code
 * RXDashboardCard trend = new RXDashboardCard("trend", salesChart);
 * trend.setMinColumnSpan(2);
 * dashboard.getCards().add(trend);
 * }</pre>
 *
 * <p>The card lays its content out in its content area (its size less its insets). Resizable content fills the area
 * within its own minimum and maximum size: smaller than the area it is centered, larger it keeps its minimum size,
 * aligned to the top left. Content that is not resizable keeps its size and is centered. The card does not clip:
 * whatever does not fit overflows the card, and overflow takes no part in placement, collision or the pane's size.
 * To keep content inside, give the card a minimum span, put the content in a {@code ScrollPane}, or clip the content
 * node.</p>
 *
 * <p>The pane drives the card's {@code translateX}, {@code translateY} and {@code viewOrder} while it animates or
 * drags the card, and sets the {@code :dragging} and {@code :resizing} pseudo-classes; applications should not
 * change these. The card needs a background fill, transparent or not, for presses on its empty parts to reach it;
 * the default style gives it one. Style class {@code rx-dashboard-card}; pseudo-classes {@code :dragging},
 * {@code :resizing}, {@code :locked}.</p>
 */
public class RXDashboardCard extends Region {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-dashboard-card";
    private static final PseudoClass LOCKED_PSEUDO_CLASS = PseudoClass.getPseudoClass("locked");
    static final PseudoClass DRAGGING_PSEUDO_CLASS = PseudoClass.getPseudoClass("dragging");
    static final PseudoClass RESIZING_PSEUDO_CLASS = PseudoClass.getPseudoClass("resizing");

    // A column or row of -1: the card has no position yet and goes to the first free area when added.
    private static final int NO_POSITION = -1;

    /**
     * The edge a resize handle pulls.
     */
    enum Edge {
        RIGHT, BOTTOM, CORNER
    }

    // ==================== Pane bookkeeping ====================

    // The pane whose card list holds this card, and the placement it committed for it. Package-private: the pane
    // writes them, the card only reads them.
    RXDashboardPane owner;
    Placement placement;

    private final ResizeHandle rightHandle = new ResizeHandle(Edge.RIGHT, "right");
    private final ResizeHandle bottomHandle = new ResizeHandle(Edge.BOTTOM, "bottom");
    private final ResizeHandle cornerHandle = new ResizeHandle(Edge.CORNER, "corner");

    // ==================== Constructors ====================

    /**
     * Creates an empty card with no ID that goes to the first free area when added.
     */
    public RXDashboardCard() {
        this(null, null);
    }

    /**
     * Creates a card spanning one column and one row that goes to the first free area when added.
     *
     * @param cardId  the stable ID saved in layout snapshots, or {@code null} for none
     * @param content the content node, or {@code null} for an empty card
     */
    public RXDashboardCard(String cardId, Node content) {
        this(cardId, content, NO_POSITION, NO_POSITION, 1, 1);
    }

    /**
     * Creates a card with a requested position and span. When the card is added, the pane treats the position as a
     * request: it is clamped into the grid and solved by the pane's collision policy, and when that rejects it the
     * card goes to the first free area with the same span. A negative column or row means no requested position.
     *
     * @param cardId     the stable ID saved in layout snapshots, or {@code null} for none
     * @param content    the content node, or {@code null} for an empty card
     * @param column     the requested column, or a negative value for none
     * @param row        the requested row, or a negative value for none
     * @param columnSpan the column span; values below 1 count as 1
     * @param rowSpan    the row span; values below 1 count as 1
     */
    public RXDashboardCard(@NamedArg("cardId") String cardId,
                           @NamedArg("content") Node content,
                           @NamedArg(value = "column", defaultValue = "-1") int column,
                           @NamedArg(value = "row", defaultValue = "-1") int row,
                           @NamedArg(value = "columnSpan", defaultValue = "1") int columnSpan,
                           @NamedArg(value = "rowSpan", defaultValue = "1") int rowSpan) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        getChildren().addAll(rightHandle, bottomHandle, cornerHandle);
        boolean positioned = column >= 0 && row >= 0;
        this.column.set(positioned ? column : NO_POSITION);
        this.row.set(positioned ? row : NO_POSITION);
        this.columnSpan.set(Math.max(1, columnSpan));
        this.rowSpan.set(Math.max(1, rowSpan));
        setCardId(cardId);
        setContent(content);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the rxcontrols user-agent stylesheet, so the card's default look applies without any
     * application-level install.</p>
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Card ID ====================

    // The listeners below act only on a real change: a bound property is invalidated on every change of its source,
    // even when the value it recomputes is the same, and each change reaches the pane.
    private final StringProperty cardId = new SimpleStringProperty(this, "cardId") {
        private String last;

        @Override
        protected void invalidated() {
            if (Objects.equals(last, get())) {
                return;
            }
            last = get();
            if (owner != null) {
                owner.cardChanged(false);
            }
        }
    };

    /**
     * The stable ID that identifies this card in {@link RXDashboardLayout} snapshots, so a saved layout can be
     * restored onto new card instances in a later run. It is separate from {@link Node#idProperty() id}. A
     * {@code null} or empty ID means the card has none: it is left out of snapshots, and restoring a snapshot keeps
     * it where it is when that place is free. When several cards of a pane share an ID, the first of them in
     * {@link RXDashboardPane#getCards()} uses it and the others count as having none. An ID with an unpaired
     * surrogate character, which the text encoding cannot carry, also counts as none.
     *
     * @return the card-id property
     */
    public final StringProperty cardIdProperty() {
        return cardId;
    }

    /**
     * Returns the card's stable ID.
     *
     * @return the ID, or {@code null}
     */
    public final String getCardId() {
        return cardId.get();
    }

    /**
     * Sets the card's stable ID.
     *
     * @param value the ID, or {@code null} for none
     */
    public final void setCardId(String value) {
        cardId.set(value);
    }

    // ==================== Content ====================

    private final ObjectProperty<Node> content = new SimpleObjectProperty<>(this, "content") {
        private Node shown;

        @Override
        protected void invalidated() {
            if (shown != null && shown.getParent() == RXDashboardCard.this) {
                getChildren().remove(shown);
            }
            shown = get();
            if (shown != null) {
                getChildren().add(0, shown);
            }
        }
    };

    /**
     * The node the card shows. Its parent is the card; applications should not rely on that, since a later version
     * may put a node in between. Replacing it keeps the card's position and span.
     *
     * @return the content property
     */
    public final ObjectProperty<Node> contentProperty() {
        return content;
    }

    /**
     * Returns the content node.
     *
     * @return the content, or {@code null}
     */
    public final Node getContent() {
        return content.get();
    }

    /**
     * Sets the content node.
     *
     * @param value the content, or {@code null} for an empty card
     */
    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== Column ====================

    private final ReadOnlyIntegerWrapper column = new ReadOnlyIntegerWrapper(this, "column", NO_POSITION);

    /**
     * The column of the card's left edge, counted from 0. Set by the pane. Before the card is first placed it holds
     * the requested column, or -1 for none; after the card leaves a pane it keeps the last value, which becomes the
     * requested position if the card is added again.
     *
     * @return the column property
     */
    public final ReadOnlyIntegerProperty columnProperty() {
        return column.getReadOnlyProperty();
    }

    /**
     * Returns the column of the card's left edge.
     *
     * @return the column, or -1 if the card was never placed and has no requested position
     */
    public final int getColumn() {
        return column.get();
    }

    // ==================== Row ====================

    private final ReadOnlyIntegerWrapper row = new ReadOnlyIntegerWrapper(this, "row", NO_POSITION);

    /**
     * The row of the card's top edge, counted from 0. Set by the pane, like {@link #columnProperty() column}.
     *
     * @return the row property
     */
    public final ReadOnlyIntegerProperty rowProperty() {
        return row.getReadOnlyProperty();
    }

    /**
     * Returns the row of the card's top edge.
     *
     * @return the row, or -1 if the card was never placed and has no requested position
     */
    public final int getRow() {
        return row.get();
    }

    // ==================== Column Span ====================

    private final ReadOnlyIntegerWrapper columnSpan = new ReadOnlyIntegerWrapper(this, "columnSpan", 1);

    /**
     * The number of columns the card spans, at least 1. Set by the pane.
     *
     * @return the column-span property
     */
    public final ReadOnlyIntegerProperty columnSpanProperty() {
        return columnSpan.getReadOnlyProperty();
    }

    /**
     * Returns the number of columns the card spans.
     *
     * @return the column span
     */
    public final int getColumnSpan() {
        return columnSpan.get();
    }

    // ==================== Row Span ====================

    private final ReadOnlyIntegerWrapper rowSpan = new ReadOnlyIntegerWrapper(this, "rowSpan", 1);

    /**
     * The number of rows the card spans, at least 1. Set by the pane.
     *
     * @return the row-span property
     */
    public final ReadOnlyIntegerProperty rowSpanProperty() {
        return rowSpan.getReadOnlyProperty();
    }

    /**
     * Returns the number of rows the card spans.
     *
     * @return the row span
     */
    public final int getRowSpan() {
        return rowSpan.get();
    }

    // ==================== Locked ====================

    private final BooleanProperty locked = new SimpleBooleanProperty(this, "locked") {
        private boolean last;

        @Override
        protected void invalidated() {
            if (last == get()) {
                return;
            }
            last = get();
            pseudoClassStateChanged(LOCKED_PSEUDO_CLASS, last);
            requestLayout();
            if (owner != null) {
                owner.cardChanged(true);
            }
        }
    };

    /**
     * Whether the card is locked. The user can neither drag nor resize a locked card, and no other card pushes it or
     * lands on it: an edit whose area covers a locked card is rejected. Code can still move and resize a locked card
     * ({@link RXDashboardPane#moveCard}, {@link RXDashboardPane#resizeCard}); {@link RXDashboardPane#compact()}
     * leaves it where it is. Off by default; sets the {@code :locked} pseudo-class.
     *
     * @return the locked property
     */
    public final BooleanProperty lockedProperty() {
        return locked;
    }

    /**
     * Returns whether the card is locked.
     *
     * @return whether the card is locked
     */
    public final boolean isLocked() {
        return locked.get();
    }

    /**
     * Sets whether the card is locked.
     *
     * @param value whether the card is locked
     */
    public final void setLocked(boolean value) {
        locked.set(value);
    }

    // ==================== Move Enabled ====================

    private final BooleanProperty moveEnabled = gestureSwitch("moveEnabled");

    /**
     * Whether the user can drag the card. When off, the card still makes room when another card is pushed onto it,
     * and code can still move it. On by default.
     *
     * @return the move-enabled property
     */
    public final BooleanProperty moveEnabledProperty() {
        return moveEnabled;
    }

    /**
     * Returns whether the user can drag the card.
     *
     * @return whether dragging is enabled
     */
    public final boolean isMoveEnabled() {
        return moveEnabled.get();
    }

    /**
     * Sets whether the user can drag the card.
     *
     * @param value whether dragging is enabled
     */
    public final void setMoveEnabled(boolean value) {
        moveEnabled.set(value);
    }

    // ==================== Resize Enabled ====================

    private final BooleanProperty resizeEnabled = gestureSwitch("resizeEnabled");

    /**
     * Whether the user can resize the card from its right edge, bottom edge or bottom-right corner. When off, the card
     * shows no resize handles; it still makes room when another card is pushed onto it, and code can still resize
     * it. On by default.
     *
     * @return the resize-enabled property
     */
    public final BooleanProperty resizeEnabledProperty() {
        return resizeEnabled;
    }

    /**
     * Returns whether the user can resize the card.
     *
     * @return whether resizing is enabled
     */
    public final boolean isResizeEnabled() {
        return resizeEnabled.get();
    }

    /**
     * Sets whether the user can resize the card.
     *
     * @param value whether resizing is enabled
     */
    public final void setResizeEnabled(boolean value) {
        resizeEnabled.set(value);
    }

    // ==================== Span Limits ====================

    private final IntegerProperty minColumnSpan = spanLimit("minColumnSpan", 1);

    /**
     * The smallest number of columns the card spans. Every placement the pane gives the card respects it: user
     * resizes stop there, and code requests and restored snapshots are clamped. Values below 1 count as 1; when it
     * exceeds {@link #maxColumnSpanProperty() maxColumnSpan} the minimum wins, and the grid's column count caps both.
     * Raising it above the card's current span widens the card at once, pushing other cards as the pane's collision
     * policy allows, or moving the card to the first free area when the policy rejects the wider area. Default 1.
     *
     * @return the minimum-column-span property
     */
    public final IntegerProperty minColumnSpanProperty() {
        return minColumnSpan;
    }

    /**
     * Returns the smallest column span.
     *
     * @return the minimum column span
     */
    public final int getMinColumnSpan() {
        return minColumnSpan.get();
    }

    /**
     * Sets the smallest column span.
     *
     * @param value the minimum column span
     */
    public final void setMinColumnSpan(int value) {
        minColumnSpan.set(value);
    }

    private final IntegerProperty maxColumnSpan = spanLimit("maxColumnSpan", Integer.MAX_VALUE);

    /**
     * The largest number of columns the card spans; see {@link #minColumnSpanProperty() minColumnSpan} for how the
     * limits apply. Unbounded by default.
     *
     * @return the maximum-column-span property
     */
    public final IntegerProperty maxColumnSpanProperty() {
        return maxColumnSpan;
    }

    /**
     * Returns the largest column span.
     *
     * @return the maximum column span
     */
    public final int getMaxColumnSpan() {
        return maxColumnSpan.get();
    }

    /**
     * Sets the largest column span.
     *
     * @param value the maximum column span
     */
    public final void setMaxColumnSpan(int value) {
        maxColumnSpan.set(value);
    }

    private final IntegerProperty minRowSpan = spanLimit("minRowSpan", 1);

    /**
     * The smallest number of rows the card spans; see {@link #minColumnSpanProperty() minColumnSpan} for how the
     * limits apply. Default 1.
     *
     * @return the minimum-row-span property
     */
    public final IntegerProperty minRowSpanProperty() {
        return minRowSpan;
    }

    /**
     * Returns the smallest row span.
     *
     * @return the minimum row span
     */
    public final int getMinRowSpan() {
        return minRowSpan.get();
    }

    /**
     * Sets the smallest row span.
     *
     * @param value the minimum row span
     */
    public final void setMinRowSpan(int value) {
        minRowSpan.set(value);
    }

    private final IntegerProperty maxRowSpan = spanLimit("maxRowSpan", Integer.MAX_VALUE);

    /**
     * The largest number of rows the card spans; see {@link #minColumnSpanProperty() minColumnSpan} for how the
     * limits apply. Unbounded by default.
     *
     * @return the maximum-row-span property
     */
    public final IntegerProperty maxRowSpanProperty() {
        return maxRowSpan;
    }

    /**
     * Returns the largest row span.
     *
     * @return the maximum row span
     */
    public final int getMaxRowSpan() {
        return maxRowSpan.get();
    }

    /**
     * Sets the largest row span.
     *
     * @param value the maximum row span
     */
    public final void setMaxRowSpan(int value) {
        maxRowSpan.set(value);
    }

    private BooleanProperty gestureSwitch(String name) {
        return new SimpleBooleanProperty(this, name, true) {
            private boolean last = true;

            @Override
            protected void invalidated() {
                if (last == get()) {
                    return;
                }
                last = get();
                if (owner != null) {
                    owner.cardGestureFlagsChanged(RXDashboardCard.this);
                }
            }
        };
    }

    private IntegerProperty spanLimit(String name, int initial) {
        return new SimpleIntegerProperty(this, name, initial) {
            private int last = initial;

            @Override
            protected void invalidated() {
                if (last == get()) {
                    return;
                }
                last = get();
                if (owner != null) {
                    owner.cardChanged(false);
                }
            }
        };
    }

    // ==================== Drag Handle ====================

    private final ObjectProperty<Node> dragHandle = new SimpleObjectProperty<>(this, "dragHandle");

    /**
     * The node a drag must start on, for content that has its own drag gestures. {@code null}, the default, lets a
     * drag start anywhere on the card where the content does not consume the press. The handle is meant to be the
     * content or a node inside it, a title bar say; a press on the handle or on any node inside it starts a drag.
     *
     * @return the drag-handle property
     */
    public final ObjectProperty<Node> dragHandleProperty() {
        return dragHandle;
    }

    /**
     * Returns the node a drag must start on.
     *
     * @return the drag handle, or {@code null} for the whole card
     */
    public final Node getDragHandle() {
        return dragHandle.get();
    }

    /**
     * Sets the node a drag must start on.
     *
     * @param value the drag handle, or {@code null} for the whole card
     */
    public final void setDragHandle(Node value) {
        dragHandle.set(value);
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        layoutHandles();
        Node node = getContent();
        if (node == null || node.getParent() != this) {
            return;
        }
        double left = snappedLeftInset();
        double top = snappedTopInset();
        double areaWidth = Math.max(0.0, getWidth() - left - snappedRightInset());
        double areaHeight = Math.max(0.0, getHeight() - top - snappedBottomInset());
        if (node.isResizable()) {
            double width = fit(areaWidth, node.minWidth(-1), node.maxWidth(-1));
            double height = fit(areaHeight, node.minHeight(width), node.maxHeight(width));
            width = snapSizeX(width);
            height = snapSizeY(height);
            // Smaller than the area (a maximum below it) centers; larger (a minimum above it) starts at the top left
            // and overflows to the right and bottom.
            double x = width < areaWidth ? left + (areaWidth - width) / 2.0 : left;
            double y = height < areaHeight ? top + (areaHeight - height) / 2.0 : top;
            node.resize(width, height);
            node.relocate(snapPositionX(x), snapPositionY(y));
        } else {
            Bounds bounds = node.getLayoutBounds();
            double x = left + (areaWidth - bounds.getWidth()) / 2.0 - bounds.getMinX();
            double y = top + (areaHeight - bounds.getHeight()) / 2.0 - bounds.getMinY();
            node.setLayoutX(snapPositionX(x));
            node.setLayoutY(snapPositionY(y));
        }
    }

    // The handles straddle the card's right and bottom edges, half in the gap and half in the padding, so with the
    // default padding they cover no content. A gap narrower than half a handle moves the rest of it inward: a handle
    // keeps its thickness but never lies over a neighbour, which may be drawn and picked above it. The corner square
    // takes the bottom-right end of both. They show only where the user may resize the card.
    private void layoutHandles() {
        boolean shown = owner != null && owner.isEditable() && isResizeEnabled() && !isLocked();
        rightHandle.setVisible(shown);
        bottomHandle.setVisible(shown);
        cornerHandle.setVisible(shown);
        if (!shown) {
            return;
        }
        double width = getWidth();
        double height = getHeight();
        double thicknessX = snapSizeX(rightHandle.prefWidth(-1));
        double thicknessY = snapSizeY(bottomHandle.prefHeight(-1));
        double outsideX = Math.min(thicknessX / 2.0, owner.handleRoomX());
        double outsideY = Math.min(thicknessY / 2.0, owner.handleRoomY());
        double rightStart = snapPositionX(width + outsideX - thicknessX);
        double rightEnd = rightStart + thicknessX;
        double bottomStart = snapPositionY(height + outsideY - thicknessY);
        double bottomEnd = bottomStart + thicknessY;
        double cornerX = Math.min(rightStart, rightEnd - snapSizeX(cornerHandle.prefWidth(-1)));
        double cornerY = Math.min(bottomStart, bottomEnd - snapSizeY(cornerHandle.prefHeight(-1)));
        cornerHandle.resizeRelocate(cornerX, cornerY, rightEnd - cornerX, bottomEnd - cornerY);
        rightHandle.resizeRelocate(rightStart, 0.0, thicknessX, Math.max(0.0, cornerY));
        bottomHandle.resizeRelocate(0.0, bottomStart, Math.max(0.0, cornerX), thicknessY);
    }

    // The area size within [min, max], the minimum winning over a smaller maximum.
    private static double fit(double area, double min, double max) {
        return Math.max(min, Math.min(area, Math.max(min, max)));
    }

    // ==================== Pane hooks ====================

    // The edge a press on this node pulls, or null when the node is not one of a card's resize handles.
    static Edge edgeOf(Node node) {
        return node instanceof ResizeHandle handle ? handle.edge : null;
    }

    // Publishes the placement the pane committed to the read-only properties.
    void publish(Placement value) {
        column.set(value.column());
        row.set(value.row());
        columnSpan.set(value.columnSpan());
        rowSpan.set(value.rowSpan());
    }

    // The placement the card asks for when added: its column and row when both are set, its spans always.
    Placement requestedPlacement() {
        return new Placement(column.get(), row.get(), columnSpan.get(), rowSpan.get());
    }


    // ==================== Resize handle ====================

    // A transparent strip over the card's edge that the user pulls to resize the card. It is picked on its bounds,
    // so it takes presses whatever the style sheet paints. The default thickness and corner size are its preferred
    // size; a style sheet may change them with -fx-pref-width and -fx-pref-height.
    private static final class ResizeHandle extends Region {

        private static final double DEFAULT_THICKNESS = 8.0;
        private static final double DEFAULT_CORNER_SIZE = 16.0;

        private final Edge edge;

        ResizeHandle(Edge edge, String styleClass) {
            this.edge = edge;
            getStyleClass().addAll("resize-handle", styleClass);
            setManaged(false);
            setPickOnBounds(true);
            setVisible(false);
        }

        @Override
        protected double computePrefWidth(double height) {
            return edge == Edge.CORNER ? DEFAULT_CORNER_SIZE : DEFAULT_THICKNESS;
        }

        @Override
        protected double computePrefHeight(double width) {
            return edge == Edge.CORNER ? DEFAULT_CORNER_SIZE : DEFAULT_THICKNESS;
        }
    }
}
