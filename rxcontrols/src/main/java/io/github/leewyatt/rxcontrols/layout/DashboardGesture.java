package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.internal.PixelGrid;
import io.github.leewyatt.rxcontrols.layout.RXDashboardCard.Edge;
import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout.Placement;
import io.github.leewyatt.rxcontrols.utils.RXTreeShowingProperty;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The user gestures of an {@link RXDashboardPane}: dragging a card and resizing it. One instance per pane.
 *
 * <p>A press on a card that its content did not consume arms a drag; a press on one of the card's resize handles
 * arms a resize. Once the pointer has moved {@link #DRAG_THRESHOLD} pixels the gesture starts and the card is raised
 * above the others by its view order. A dragged card follows the pointer through its translate while its layout
 * position stays where the gesture started; a resized card keeps its top-left corner and, with live resize on,
 * takes the size the pointer gives it, within its span limits. Whenever the pointer reaches another cell, the edit
 * is solved from the layout the gesture started with; an accepted candidate becomes the preview the pane lays the
 * other cards out by, and the placeholder marks where the card is in it. When the cards pack upward under
 * {@code PUSH}, a drag is instead worked out step by step: each change of the pointer's place on the grid takes one
 * step from the previous step's layout, and the pointer coming back over the cell the card started from brings the
 * starting layout back. Releasing commits the preview as shown; Esc, the window losing focus, the pane leaving its
 * scene, the card disabled or no longer shown, and any change to the pane's cards or layout cancel it, and every card
 * glides back from where it shows.</p>
 *
 * <p>While the pointer is near the top or bottom of the viewport of a {@link ScrollPane} the pane sits in, that scroll
 * pane scrolls, faster the closer the pointer gets and building up over the first moments, and keeps scrolling while
 * the pointer rests there.</p>
 */
final class DashboardGesture {

    /**
     * Pointer travel, in pixels, before a press becomes a drag.
     */
    static final double DRAG_THRESHOLD = 6.0;

    // How close to the viewport's top or bottom the pointer starts scrolling, and the most one tick scrolls, in
    // pixels; the same as dragging on a kanban board.
    private static final double AUTO_SCROLL_EDGE = 28.0;
    private static final double AUTO_SCROLL_MAX_STEP = 22.0;
    private static final Duration AUTO_SCROLL_TICK = Duration.millis(16.0);
    private static final Duration AUTO_SCROLL_RAMP = Duration.millis(500.0);

    private final RXDashboardPane pane;

    private boolean armed;
    private boolean started;
    private RXDashboardCard card;
    // The edge a resize pulls, or null for a drag.
    private Edge edge;
    private double pressSceneX;
    private double pressSceneY;
    private double lastSceneX;
    private double lastSceneY;
    // In the pane's coordinates: for a drag, the pointer's offset from the card's top-left corner as it showed at the
    // press; for a resize, its offset from the card's right and bottom edges at its cells.
    private double grabX;
    private double grabY;
    private double viewOrderBefore;

    private Map<RXDashboardCard, Placement> startLayout;
    private Map<RXDashboardCard, Placement> preview;
    private Placement candidate;
    // A drag worked out step by step, in the pane's virtual grid; created at the first step, as it needs a grid.
    private DashboardDrag<RXDashboardCard> steps;
    // A preview that changes the pane's height can bring up an enclosing scroll pane's scroll bar and narrow a pane
    // that fits its width; near a column edge the pointer is then over another column, whose preview takes the bar
    // away again, and so on at every move. Once the pane's width has gone back to the one it had before (by less
    // than a column), the column under the pointer (the column span, for a resize) is also worked out at that other
    // width: where the two disagree, the column last aimed at stays. When both agree on another column than the one
    // aimed at as the width went back, the pointer has left that edge and the columns shown are followed again, as
    // they are while the width has not gone back. Rows are the same at both widths.
    private double trackedWidth = Double.NaN;
    private double formerWidth = Double.NaN;
    private boolean widthWentBack;
    private int lastKey = Integer.MIN_VALUE;
    private int bandKey = Integer.MIN_VALUE;
    // The cells a dragged card points at, while they cover another card: the pane grows to take them in.
    private Placement collidingTarget;
    // Where the card was on the virtual grid at the last step, and whether its target was the cell it started from.
    private double stepX;
    private double stepY;
    private boolean home;
    private RXDashboardLayout layoutBefore;

    private Scene watchedScene;
    private Window watchedWindow;
    private RXDashboardCard watchedCard;
    private RXTreeShowingProperty watchedCardShowing;
    private Timeline autoScroll;
    // When the current run of auto-scroll started, in System.nanoTime().
    private long autoScrollStart;
    private boolean paneMovedSinceLayout;
    private double paneWidthAtLayout;
    private double paneHeightAtLayout;
    // Where the pane's origin was in the scene when the cells were last worked out or the pane last laid out.
    private Point2D lastPaneOrigin;
    // The click JavaFX generates for the release of a press that started a gesture, committed or cancelled: the card
    // followed the pointer, so press and release land on the same node. It is not a click on the content. The next
    // press clears it.
    private boolean swallowClick;

    private final EventHandler<KeyEvent> escapeFilter = event -> {
        if (event.getCode() == KeyCode.ESCAPE && started) {
            cancel();
            event.consume();
        }
    };

    // The pane moving in the scene without the pointer moving (a scroll wheel, a parent re-centering it) leaves the
    // pointer over other cells.
    private final InvalidationListener sceneTransformListener = this::paneMoved;

    // Switching to another application can swallow the release; the card would then stick to the pointer.
    private final InvalidationListener focusListener = observable -> {
        if (watchedWindow != null && !watchedWindow.isFocused()) {
            cancel();
        }
    };

    // The window showing another scene sends its input there, and holding on would keep the old scene alive.
    private final InvalidationListener windowListener = observable -> cancel();

    // A card disabled or no longer shown (it, the pane or an ancestor hidden, the window closed) can no longer be
    // handled, and an edit made through it now would be one the user did not see.
    private final ChangeListener<Boolean> cardDisabledListener = (obs, wasDisabled, disabled) -> {
        if (disabled) {
            cancel();
        }
    };
    private final ChangeListener<Boolean> cardShowingListener = (obs, wasShowing, showing) -> {
        if (!showing) {
            cancel();
        }
    };

    DashboardGesture(RXDashboardPane pane) {
        this.pane = pane;
        // A press and the drags before the threshold bubble, so content that takes them for itself keeps them. Once a
        // gesture has started, filters take its drags first and see its release even when content consumes it.
        pane.addEventHandler(MouseEvent.MOUSE_PRESSED, this::pressed);
        pane.addEventFilter(MouseEvent.MOUSE_PRESSED, this::beforePress);
        pane.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::dragged);
        pane.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::draggedDuringGesture);
        pane.addEventFilter(MouseEvent.MOUSE_RELEASED, this::released);
        pane.addEventFilter(MouseEvent.MOUSE_CLICKED, this::clicked);
    }

    // ==================== Pane-facing ====================

    /**
     * Returns whether a gesture has started and not yet ended.
     *
     * @return whether a gesture is in progress
     */
    boolean isActive() {
        return started;
    }

    /**
     * Returns the card the gesture moves, or {@code null}.
     *
     * @return the edited card while a gesture is in progress
     */
    RXDashboardCard card() {
        return started ? card : null;
    }

    /**
     * Returns the cells a dragged card points at while they cover another card, or {@code null}.
     *
     * @return the colliding target of the drag in progress
     */
    Placement collidingTarget() {
        return started ? collidingTarget : null;
    }

    /**
     * Returns where a card is to be laid out: its preview placement during a gesture, its committed one otherwise.
     *
     * @param target the card
     * @return the placement to lay it out at
     */
    Placement displayedPlacement(RXDashboardCard target) {
        if (started) {
            Placement shown = preview.get(target);
            if (shown != null) {
                return shown;
            }
        }
        return target.placement;
    }

    /**
     * Returns the area the placeholder marks: where the edited card is in the preview.
     *
     * @return the card's area in the preview, or {@code null} when no gesture is in progress
     */
    Placement landing() {
        return started ? preview.get(card) : null;
    }

    /**
     * Lays the edited card out at the cells it had when the gesture started: a dragged card displaced by the
     * pointer, a resized one at the size the pointer gives it when resizing live.
     *
     * @param grid the grid of the pass
     */
    void layoutCard(DashboardGrid grid) {
        if (!started) {
            return;
        }
        RXDashboardCard card = this.card;
        Edge edge = this.edge;
        Placement start = startLayout.get(card);
        double x = grid.columnX(start.column());
        double y = grid.rowY(start.row());
        double width = grid.spanWidth(start.column(), start.columnSpan());
        double height = grid.spanHeight(start.row(), start.rowSpan());
        if (edge != null && pane.isLiveResize()) {
            Point2D local = pane.sceneToLocal(lastSceneX, lastSceneY);
            Placement smallest = smallestArea(start, grid.columnCount());
            Placement largest = largestArea(start, grid.columnCount());
            if (edge != Edge.BOTTOM) {
                width = clamp(local.getX() - grabX - x, grid.spanWidth(start.column(), smallest.columnSpan()),
                        grid.spanWidth(start.column(), largest.columnSpan()));
            }
            if (edge != Edge.RIGHT) {
                double maxHeight = largest.rowSpan() >= DashboardSolver.MAX_ROW_SPAN ? Double.MAX_VALUE
                        : grid.spanHeight(start.row(), largest.rowSpan());
                height = clamp(local.getY() - grabY - y, grid.spanHeight(start.row(), smallest.rowSpan()), maxHeight);
            }
            width = PixelGrid.ceilSizeX(card, width);
            height = PixelGrid.ceilSizeY(card, height);
        }
        // A listener reached from each write may have ended the gesture.
        card.resize(width, height);
        if (!started) {
            return;
        }
        card.relocate(x, y);
        if (started && edge == null) {
            follow();
        }
    }

    /**
     * Cancels the gesture when the card it moves turned off the switch the gesture needs.
     *
     * @param changed the card whose move or resize switch changed
     */
    void switchChanged(RXDashboardCard changed) {
        if (changed == card && (edge == null ? !changed.isMoveEnabled() : !changed.isResizeEnabled())) {
            cancel();
        }
    }

    /**
     * Cancels the gesture: drops the preview, puts the card back and lets every card glide back. Disarms a press
     * that has not started a gesture yet.
     */
    void cancel() {
        armed = false;
        if (started) {
            end();
            pane.requestLayout();
        } else {
            forgetPress();
        }
    }

    // ==================== Mouse ====================

    // Reading the transform validates it again, so the next move invalidates it again. The card stays under the
    // pointer at once; the cells it points at are worked out when the pane is laid out next, from where it ends up. A
    // scroll pane laying the pane out moves it through places in between, and working them out at each would change
    // the pane's height while it is being laid out, so the scroll pane would lay it out again on every pulse.
    private void paneMoved(Observable observable) {
        pane.getLocalToSceneTransform();
        if (!started) {
            return;
        }
        if (edge == null) {
            follow();
        }
        paneMovedSinceLayout = true;
        pane.requestLayout();
    }

    /**
     * Called by the pane as it starts laying out its cards, with its grid for this pass in place.
     */
    void layoutStarting() {
        if (!started) {
            paneMovedSinceLayout = false;
            return;
        }
        boolean resized = pane.getWidth() != paneWidthAtLayout || pane.getHeight() != paneHeightAtLayout;
        // Where the pane's origin was, in its coordinates now: how far the pane has moved since.
        Point2D formerOrigin = pane.sceneToLocal(lastPaneOrigin);
        paneWidthAtLayout = pane.getWidth();
        paneHeightAtLayout = pane.getHeight();
        lastPaneOrigin = pane.localToScene(0.0, 0.0);
        if (!paneMovedSinceLayout) {
            return;
        }
        paneMovedSinceLayout = false;
        // A move that comes with a new size is the parent placing the pane again after it grew or shrank, often by
        // the preview's rows. A parent that keeps the pane's bottom edge in place moves it up as it grows; taken as
        // the pointer moving over the grid, that would grow it again at every pass or pointer event. The held card
        // moves with the cells instead, and the pointer keeps its place on the grid. A pixel or less is rounding (a
        // scroll pane placing its content again after it grew), not taken in, or the card would creep away from the
        // pointer over a long auto-scroll.
        if (resized && (Math.abs(formerOrigin.getX()) > 1.0 || Math.abs(formerOrigin.getY()) > 1.0)) {
            grabX += formerOrigin.getX();
            grabY += formerOrigin.getY();
        }
        track();
    }

    // A primary press ends what the last one left behind: a gesture whose release never arrived, or a card armed by a
    // press whose release went elsewhere (to another window, say). Runs before content can consume the press.
    private void beforePress(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        cancel();
        swallowClick = false;
    }

    private void pressed(MouseEvent event) {
        if (started || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        armed = false;
        RXDashboardCard pressed = cardOf(event.getTarget());
        if (pressed == null || !pane.isEditable() || pressed.isLocked()) {
            return;
        }
        Edge pulled = edgeOf(event.getTarget(), pressed);
        if (pulled != null ? !pressed.isResizeEnabled()
                : !pressed.isMoveEnabled() || !onHandle(event.getTarget(), pressed)) {
            return;
        }
        card = pressed;
        edge = pulled;
        pressSceneX = event.getSceneX();
        pressSceneY = event.getSceneY();
        lastSceneX = pressSceneX;
        lastSceneY = pressSceneY;
        Point2D local = pane.sceneToLocal(pressSceneX, pressSceneY);
        if (edge == null) {
            // A drag keeps the grabbed point under the pointer, from where the card shows now, even mid-glide.
            grabX = local.getX() - (card.getLayoutX() + card.getTranslateX());
            grabY = local.getY() - (card.getLayoutY() + card.getTranslateY());
        } else {
            // A resized card stops gliding and stands at its cells when the gesture starts; the pulled edge moves
            // from there by as much as the pointer moves.
            grabX = local.getX() - (card.getLayoutX() + card.getWidth());
            grabY = local.getY() - (card.getLayoutY() + card.getHeight());
        }
        armed = true;
        if (edge != null) {
            // The press is the handle's: nothing under it, and nothing above the pane, should act on it.
            event.consume();
        }
    }

    private void draggedDuringGesture(MouseEvent event) {
        if (!started) {
            return;
        }
        // The release of the primary button never arrived.
        if (!event.isPrimaryButtonDown()) {
            cancel();
            return;
        }
        lastSceneX = event.getSceneX();
        lastSceneY = event.getSceneY();
        update();
        event.consume();
    }

    private void dragged(MouseEvent event) {
        if (!armed || started) {
            return;
        }
        // The drags of a press go to the node pressed, which lies in the card it armed. A drag elsewhere, or with the
        // primary button up, means the release of that press went elsewhere and the drag belongs to a later press,
        // one the pane did not see because it stopped above the pane.
        if (!event.isPrimaryButtonDown() || cardOf(event.getTarget()) != card) {
            cancel();
            return;
        }
        lastSceneX = event.getSceneX();
        lastSceneY = event.getSceneY();
        if (!started) {
            double dx = lastSceneX - pressSceneX;
            double dy = lastSceneY - pressSceneY;
            if (dx * dx + dy * dy < DRAG_THRESHOLD * DRAG_THRESHOLD) {
                return;
            }
            if (!start()) {
                armed = false;
                forgetPress();
                return;
            }
        }
        update();
        event.consume();
    }

    // Not consumed: content that saw the press gets its release.
    private void released(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        armed = false;
        if (started) {
            boolean pointerMoved = event.getSceneX() != lastSceneX || event.getSceneY() != lastSceneY;
            lastSceneX = event.getSceneX();
            lastSceneY = event.getSceneY();
            // The preview as shown is committed unless the pointer moved since it was worked out. A stepwise drag
            // commits it as shown regardless: a last step would land somewhere never shown.
            if (pointerMoved && (edge != null || !pane.dragsStepwise())) {
                track();
            }
            if (started) {
                commit();
            }
        } else {
            forgetPress();
        }
    }

    private void clicked(MouseEvent event) {
        if (swallowClick && event.getButton() == MouseButton.PRIMARY) {
            swallowClick = false;
            event.consume();
        }
    }

    // A press that never became a gesture holds on to no card.
    private void forgetPress() {
        card = null;
        edge = null;
    }

    // ==================== Lifecycle ====================

    private boolean start() {
        // The card may have left the pane, the pane its editable state, or the card (with the pane or an ancestor)
        // been disabled or hidden between the press and now.
        boolean allowed = edge != null ? card.isResizeEnabled() : card.isMoveEnabled();
        if (card.owner != pane || card.placement == null || !pane.isEditable() || card.isLocked() || !allowed
                || pane.getScene() == null || card.isDisabled() || !isVisibleToTheRoot(card)) {
            return false;
        }
        started = true;
        swallowClick = true;
        RXDashboardCard held = card;
        boolean drag = edge == null;
        viewOrderBefore = held.getViewOrder();
        startLayout = pane.committedPlacements();
        preview = new LinkedHashMap<>(startLayout);
        candidate = held.placement;
        collidingTarget = null;
        trackedWidth = pane.getWidth();
        formerWidth = Double.NaN;
        widthWentBack = false;
        lastKey = Integer.MIN_VALUE;
        bandKey = Integer.MIN_VALUE;
        steps = null;
        stepX = Double.NaN;
        stepY = Double.NaN;
        home = true;
        layoutBefore = pane.getLayout();
        paneWidthAtLayout = pane.getWidth();
        paneHeightAtLayout = pane.getHeight();
        lastPaneOrigin = pane.localToScene(0.0, 0.0);
        // A listener reached from each change to the card below may end the gesture at once.
        pane.releaseFromAnimation(held);
        if (started && !held.viewOrderProperty().isBound()) {
            held.setViewOrder(viewOrderBefore - 1.0);
        }
        if (started) {
            held.pseudoClassStateChanged(drag ? RXDashboardCard.DRAGGING_PSEUDO_CLASS
                    : RXDashboardCard.RESIZING_PSEUDO_CLASS, true);
        }
        if (!started) {
            return false;
        }
        watch(pane.getScene());
        if (drag) {
            follow();
        }
        pane.requestLayout();
        return started;
    }

    // Follows the pointer, re-solves when it reaches another cell, and scrolls when it nears a viewport edge.
    private void update() {
        updateAutoScroll();
        track();
    }

    private void track() {
        // The cells are worked out from where the pane is now, so a growth later compensates only the moves after
        // this: an auto-scroll tick's move is the pointer travelling over the grid, not to be undone.
        lastPaneOrigin = pane.localToScene(0.0, 0.0);
        if (edge == null) {
            follow();
            // A listener reached from moving the card may have ended the gesture.
            if (!started) {
                return;
            }
        } else if (pane.isLiveResize()) {
            pane.requestLayout();
        }
        DashboardGrid shown = pane.grid();
        if (shown == null) {
            return;
        }
        DashboardGrid former = formerGrid(shown);
        Point2D local = pane.sceneToLocal(lastSceneX, lastSceneY);
        Placement start = startLayout.get(card);
        if (edge == null && pane.dragsStepwise()) {
            step(shown, former, start, local);
            return;
        }
        Placement next = cellsUnder(shown, start, local);
        if (former != null) {
            Placement there = cellsUnder(former, start, local);
            if (key(there) == key(next)) {
                leaveEdge(key(next));
            } else if (key(there) == lastKey) {
                next = there;
            }
        }
        lastKey = key(next);
        if (edge == null) {
            aimAt(next);
        }
        if (next.equals(candidate)) {
            return;
        }
        Map<RXDashboardCard, Placement> solved = pane.solveFrom(startLayout, card, next);
        if (solved == null) {
            return;
        }
        candidate = next;
        preview = solved;
        collidingTarget = null;
        pane.requestLayout();
    }

    // One step of a stepwise drag, taken only when the card's place on the grid changed since the last one: a pane
    // that moves or scrolls under a resting pointer does not repeat it.
    private void step(DashboardGrid grid, DashboardGrid former, Placement start, Point2D local) {
        double x = RXDashboardPane.virtualX(grid, local.getX() - grabX);
        double y = RXDashboardPane.virtualY(grid, local.getY() - grabY);
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return;
        }
        if (steps == null) {
            steps = pane.stepwiseDrag(startLayout, card, grid);
        }
        if (former != null) {
            double formerX = RXDashboardPane.virtualX(former, local.getX() - grabX);
            int there = steps.target(formerX, y)[0];
            int here = steps.target(x, y)[0];
            if (there == here) {
                leaveEdge(here);
            } else if (there == lastKey) {
                x = formerX;
            }
        }
        lastKey = steps.target(x, y)[0];
        if (x == stepX && y == stepY) {
            return;
        }
        stepX = x;
        stepY = y;
        int[] target = steps.target(x, y);
        int column = Math.max(0, Math.min(target[0], grid.columnCount() - start.columnSpan()));
        int row = Math.max(0, target[1]);
        aimAt(start.withPosition(column, row));
        if (column == start.column() && row == start.row()) {
            // Coming back over the starting cell brings the starting layout back; the lean starts from here.
            if (!home) {
                steps = pane.stepwiseDrag(startLayout, card, grid);
                steps.anchor(x, y);
                landed();
                show(startLayout);
            }
            home = true;
            return;
        }
        home = false;
        if (steps.step(x, y)) {
            landed();
        }
        show(steps.layout());
    }

    // Cells pointed at that cover another card in the preview shown so far: the pane takes them in, so the pointer can
    // go on down to place the card below the others, until a move is accepted. Over free cells it does not; the
    // preview alone says how tall the pane is.
    private void aimAt(Placement target) {
        Placement colliding = null;
        for (Map.Entry<RXDashboardCard, Placement> entry : preview.entrySet()) {
            if (entry.getKey() != card && DashboardSolver.overlaps(target, entry.getValue())) {
                colliding = target;
                break;
            }
        }
        if (!Objects.equals(colliding, collidingTarget)) {
            collidingTarget = colliding;
            pane.requestLayout();
        }
    }

    // A move was accepted: the preview says how tall the pane is again.
    private void landed() {
        if (collidingTarget != null) {
            collidingTarget = null;
            pane.requestLayout();
        }
    }

    private void show(Map<RXDashboardCard, Placement> layout) {
        if (!layout.equals(preview)) {
            preview = new LinkedHashMap<>(layout);
            candidate = preview.get(card);
            pane.requestLayout();
        }
    }

    private static boolean isVisibleToTheRoot(Node node) {
        for (Node shown = node; shown != null; shown = shown.getParent()) {
            if (!shown.isVisible()) {
                return false;
            }
        }
        return true;
    }

    // The grid at the other width the pane goes back and forth between, by less than a column: a scroll bar coming
    // and going. A larger change (the window resized, say) is simply taken.
    private DashboardGrid formerGrid(DashboardGrid shown) {
        double width = pane.getWidth();
        if (width != trackedWidth) {
            if (!widthWentBack && width == formerWidth) {
                widthWentBack = true;
                bandKey = lastKey;
            }
            formerWidth = trackedWidth;
            trackedWidth = width;
        }
        if (!widthWentBack || !(Math.abs(width - formerWidth) < shown.columnPitch())) {
            return null;
        }
        return pane.computeGrid(formerWidth);
    }

    // Both widths agree on a column: past the edge the width went back at, there is nothing to hold any more.
    private void leaveEdge(int key) {
        if (key != bandKey) {
            widthWentBack = false;
        }
    }

    private int key(Placement cells) {
        return edge == null ? cells.column() : cells.columnSpan();
    }

    private Placement cellsUnder(DashboardGrid grid, Placement start, Point2D local) {
        return edge == null ? dragCandidate(grid, start, local) : resizeCandidate(grid, start, local);
    }

    // The cell nearest to the card's top-left corner as it follows the pointer.
    private Placement dragCandidate(DashboardGrid grid, Placement start, Point2D local) {
        return start.withPosition(grid.nearestColumn(local.getX() - grabX, start.columnSpan()),
                grid.nearestRow(local.getY() - grabY));
    }

    // The spans whose edges are nearest to the pulled edges, within the card's span limits and the grid.
    private Placement resizeCandidate(DashboardGrid grid, Placement start, Point2D local) {
        int columnSpan = start.columnSpan();
        int rowSpan = start.rowSpan();
        if (edge != Edge.BOTTOM) {
            columnSpan = grid.nearestColumnSpan(start.column(), local.getX() - grabX);
        }
        if (edge != Edge.RIGHT) {
            rowSpan = grid.nearestRowSpan(start.row(), local.getY() - grabY);
        }
        Placement smallest = smallestArea(start, grid.columnCount());
        Placement largest = largestArea(start, grid.columnCount());
        return start.withSpan(Math.max(smallest.columnSpan(), Math.min(largest.columnSpan(), columnSpan)),
                Math.max(smallest.rowSpan(), Math.min(largest.rowSpan(), rowSpan)));
    }

    // The card at its smallest and largest spans from its top-left cell, within its limits and the grid.
    private Placement smallestArea(Placement start, int columns) {
        Placement clamped = DashboardSolver.clamp(item(), start.withSpan(1, 1), columns);
        return start.withSpan(Math.min(clamped.columnSpan(), columns - start.column()), clamped.rowSpan());
    }

    private Placement largestArea(Placement start, int columns) {
        Placement clamped = DashboardSolver.clamp(item(), start.withSpan(Integer.MAX_VALUE, Integer.MAX_VALUE),
                columns);
        return start.withSpan(Math.max(1, Math.min(clamped.columnSpan(), columns - start.column())),
                clamped.rowSpan());
    }

    private DashboardSolver.Item<RXDashboardCard> item() {
        return new DashboardSolver.Item<>(card, card.placement, card.isLocked(), card.getMinColumnSpan(),
                card.getMaxColumnSpan(), card.getMinRowSpan(), card.getMaxRowSpan());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(Math.max(min, max), value));
    }

    // The translate that keeps the grabbed point under the pointer, from the card's current layout position.
    private void follow() {
        RXDashboardCard held = card;
        if (!started || held.translateXProperty().isBound() || held.translateYProperty().isBound()) {
            return;
        }
        Point2D local = pane.sceneToLocal(lastSceneX, lastSceneY);
        held.setTranslateX(local.getX() - grabX - held.getLayoutX());
        // A listener reached from the first write may have ended the gesture and given the translate back.
        if (started) {
            held.setTranslateY(local.getY() - grabY - held.getLayoutY());
        }
    }

    private void commit() {
        RXDashboardCard edited = card;
        Map<RXDashboardCard, Placement> result = preview.equals(startLayout) ? null : preview;
        pane.commitUserEdit(this::end, result, edited, layoutBefore);
        pane.requestLayout();
    }

    // Puts the card back in the pane's hands. It glides from where it shows to its cells: the pane picks up the
    // shown position at its next pass, with the translate given back first.
    private void end() {
        RXDashboardCard ended = card;
        started = false;
        armed = false;
        stopAutoScroll();
        unwatch();
        if (ended.getParent() == pane) {
            pane.glideFrom(ended, ended.getLayoutX() + ended.getTranslateX(),
                    ended.getLayoutY() + ended.getTranslateY());
        }
        if (!ended.translateXProperty().isBound()) {
            ended.setTranslateX(0.0);
        }
        if (!ended.translateYProperty().isBound()) {
            ended.setTranslateY(0.0);
        }
        if (!ended.viewOrderProperty().isBound()) {
            ended.setViewOrder(viewOrderBefore);
        }
        ended.pseudoClassStateChanged(RXDashboardCard.DRAGGING_PSEUDO_CLASS, false);
        ended.pseudoClassStateChanged(RXDashboardCard.RESIZING_PSEUDO_CLASS, false);
        card = null;
        edge = null;
        startLayout = null;
        preview = null;
        steps = null;
        candidate = null;
        collidingTarget = null;
        trackedWidth = Double.NaN;
        formerWidth = Double.NaN;
        widthWentBack = false;
        lastKey = Integer.MIN_VALUE;
        bandKey = Integer.MIN_VALUE;
        layoutBefore = null;
    }

    // ==================== Abnormal endings ====================

    private void watch(Scene scene) {
        watchedScene = scene;
        scene.addEventFilter(KeyEvent.KEY_PRESSED, escapeFilter);
        scene.windowProperty().addListener(windowListener);
        pane.localToSceneTransformProperty().addListener(sceneTransformListener);
        pane.getLocalToSceneTransform();
        watchedWindow = scene.getWindow();
        if (watchedWindow != null) {
            watchedWindow.focusedProperty().addListener(focusListener);
        }
        watchedCard = card;
        watchedCard.disabledProperty().addListener(cardDisabledListener);
        watchedCardShowing = new RXTreeShowingProperty(watchedCard);
        watchedCardShowing.addListener(cardShowingListener);
    }

    private void unwatch() {
        pane.localToSceneTransformProperty().removeListener(sceneTransformListener);
        if (watchedScene != null) {
            watchedScene.removeEventFilter(KeyEvent.KEY_PRESSED, escapeFilter);
            watchedScene.windowProperty().removeListener(windowListener);
            watchedScene = null;
        }
        if (watchedWindow != null) {
            watchedWindow.focusedProperty().removeListener(focusListener);
            watchedWindow = null;
        }
        if (watchedCard != null) {
            watchedCard.disabledProperty().removeListener(cardDisabledListener);
            watchedCardShowing.removeListener(cardShowingListener);
            watchedCardShowing.dispose();
            watchedCard = null;
            watchedCardShowing = null;
        }
    }

    // ==================== Auto-scroll ====================

    private void updateAutoScroll() {
        if (scrollStep() != 0.0) {
            if (autoScroll == null) {
                autoScroll = new Timeline(new KeyFrame(AUTO_SCROLL_TICK, event -> autoScrollTick()));
                autoScroll.setCycleCount(Animation.INDEFINITE);
            }
            if (!isAutoScrolling()) {
                autoScrollStart = System.nanoTime();
                autoScroll.play();
            }
        } else {
            stopAutoScroll();
        }
    }

    private void stopAutoScroll() {
        if (autoScroll != null) {
            autoScroll.stop();
        }
    }

    private void autoScrollTick() {
        ScrollPane scroller = enclosingScrollPane();
        double step = scrollStep();
        if (!started || scroller == null || step == 0.0) {
            stopAutoScroll();
            return;
        }
        Node content = scroller.getContent();
        double scrollable = content == null ? 0.0
                : content.getLayoutBounds().getHeight() - scroller.getViewportBounds().getHeight();
        double range = scroller.getVmax() - scroller.getVmin();
        if (!(scrollable > 0.0) || !(range > 0.0)) {
            stopAutoScroll();
            return;
        }
        double value = scroller.getVvalue() + step * range / scrollable;
        double clamped = Math.max(scroller.getVmin(), Math.min(scroller.getVmax(), value));
        if (clamped == scroller.getVvalue()) {
            return;
        }
        if (!scroller.vvalueProperty().isBound()) {
            scroller.setVvalue(clamped);
        }
        // The pane moved under a pointer that did not: follow it again (the scene-transform listener may not see the
        // move until the scroll pane lays its content out).
        track();
    }

    // Pixels to scroll this tick: negative toward the top, positive toward the bottom, 0 outside the edge bands.
    private double scrollStep() {
        ScrollPane scroller = enclosingScrollPane();
        if (!started || scroller == null) {
            return 0.0;
        }
        // The viewport as drawn: a scaled ancestor scales it too.
        Bounds viewport = scroller.localToScene(new BoundingBox(scroller.snappedLeftInset(),
                scroller.snappedTopInset(), scroller.getViewportBounds().getWidth(),
                scroller.getViewportBounds().getHeight()));
        double top = viewport.getMinY();
        double bottom = viewport.getMaxY();
        if (lastSceneY < top + AUTO_SCROLL_EDGE) {
            return -scrollSpeed((top + AUTO_SCROLL_EDGE - lastSceneY) / AUTO_SCROLL_EDGE);
        }
        if (lastSceneY > bottom - AUTO_SCROLL_EDGE) {
            return scrollSpeed((lastSceneY - (bottom - AUTO_SCROLL_EDGE)) / AUTO_SCROLL_EDGE);
        }
        return 0.0;
    }

    // Faster the deeper the pointer is in the band, and building up over the first moments of a run: a gesture that
    // starts in the band, pulling the bottom edge of a card at the bottom of the viewport say, does not set off at
    // full speed.
    private double scrollSpeed(double depth) {
        double ramp = 0.0;
        if (isAutoScrolling()) {
            double elapsedMillis = (System.nanoTime() - autoScrollStart) / 1.0e6;
            ramp = Math.min(1.0, elapsedMillis / AUTO_SCROLL_RAMP.toMillis());
        }
        return Math.max(1.0, Math.min(1.0, depth) * ramp * AUTO_SCROLL_MAX_STEP);
    }

    private boolean isAutoScrolling() {
        return autoScroll != null && autoScroll.getStatus() == Animation.Status.RUNNING;
    }

    private ScrollPane enclosingScrollPane() {
        for (Parent parent = pane.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof ScrollPane scroller) {
                return scroller;
            }
        }
        return null;
    }

    // ==================== Hit testing ====================

    // The card of this pane the press landed on: the nearest card above the target, provided no other dashboard
    // sits in between (a dashboard nested in a card handles its own cards).
    private RXDashboardCard cardOf(EventTarget target) {
        for (Node node = target instanceof Node n ? n : null; node != null && node != pane; node = node.getParent()) {
            if (node instanceof RXDashboardPane) {
                return null;
            }
            if (node instanceof RXDashboardCard found) {
                return found.owner == pane && found.placement != null ? found : null;
            }
        }
        return null;
    }

    // The edge a press pulls: the resize handle of the pressed card the target is, or null.
    private static Edge edgeOf(EventTarget target, RXDashboardCard pressed) {
        Node node = target instanceof Node n ? n : null;
        return node != null && node.getParent() == pressed ? RXDashboardCard.edgeOf(node) : null;
    }

    private static boolean onHandle(EventTarget target, RXDashboardCard pressed) {
        Node handle = pressed.getDragHandle();
        if (handle == null) {
            return true;
        }
        for (Node node = target instanceof Node n ? n : null; node != null; node = node.getParent()) {
            if (node == handle) {
                return true;
            }
            if (node == pressed) {
                return false;
            }
        }
        return false;
    }
}
