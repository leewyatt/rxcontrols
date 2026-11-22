package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.layout.RXDashboardCard.Edge;
import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout.Placement;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
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

/**
 * The user gestures of an {@link RXDashboardPane}: dragging a card and resizing it. One instance per pane.
 *
 * <p>A press on a card that its content did not consume arms a drag; a press on one of the card's resize handles
 * arms a resize. Once the pointer has moved {@link #DRAG_THRESHOLD} pixels the gesture starts and the card is raised
 * above the others by its view order. A dragged card follows the pointer through its translate while its layout
 * position stays where the gesture started; a resized card keeps its top-left corner and, with live resize on,
 * takes the size the pointer gives it, within its span limits. Whenever the pointer reaches another cell, the edit
 * is solved from the layout the gesture started with; an accepted candidate becomes the preview the pane lays the
 * other cards out by, and the placeholder marks it. Releasing commits the preview; Esc, the window losing focus, the
 * pane leaving its scene and any change to the pane's cards or layout cancel it, and every card glides back from
 * where it shows.</p>
 *
 * <p>While the pointer is near the top or bottom of the viewport of a {@link ScrollPane} the pane sits in, that scroll
 * pane scrolls, faster the closer the pointer gets, and keeps scrolling while the pointer rests there.</p>
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
    private RXDashboardLayout layoutBefore;

    private Scene watchedScene;
    private Window watchedWindow;
    private Timeline autoScroll;
    // The click JavaFX generates for the release that ends a gesture: the card followed the pointer, so press and
    // release land on the same node. It is not a click on the content.
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

    DashboardGesture(RXDashboardPane pane) {
        this.pane = pane;
        // A press and the drags before the threshold bubble, so content that takes them for itself keeps them. Once a
        // gesture has started, filters take its drags first and see its release even when content consumes it.
        pane.addEventHandler(MouseEvent.MOUSE_PRESSED, this::pressed);
        pane.addEventFilter(MouseEvent.MOUSE_PRESSED, this::pressedDuringGesture);
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
     * Returns the area the placeholder marks.
     *
     * @return the candidate area, or {@code null} when no gesture is in progress
     */
    Placement candidate() {
        return started ? candidate : null;
    }

    /**
     * Lays the edited card out at the cells it had when the gesture started: a dragged card displaced by the
     * pointer, a resized one at the size the pointer gives it when resizing live.
     *
     * @param grid the grid of the pass
     */
    void layoutCard(DashboardGrid grid) {
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
            width = card.snapSizeX(width);
            height = card.snapSizeY(height);
        }
        card.resize(width, height);
        card.relocate(x, y);
        if (edge == null) {
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

    // Reading the transform validates it again, so the next move invalidates it again.
    private void paneMoved(Observable observable) {
        pane.getLocalToSceneTransform();
        if (started) {
            track();
        }
    }

    // A primary press while a gesture is in progress means its release never arrived.
    private void pressedDuringGesture(MouseEvent event) {
        swallowClick = false;
        if (started && event.getButton() == MouseButton.PRIMARY) {
            cancel();
        }
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
        lastSceneX = event.getSceneX();
        lastSceneY = event.getSceneY();
        update();
        event.consume();
    }

    private void dragged(MouseEvent event) {
        if (!armed || started) {
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
            lastSceneX = event.getSceneX();
            lastSceneY = event.getSceneY();
            track();
            swallowClick = true;
            commit();
        } else {
            forgetPress();
        }
    }

    private void clicked(MouseEvent event) {
        if (swallowClick) {
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
        // The card may have left the pane, or the pane its editable state, between the press and now.
        boolean allowed = edge != null ? card.isResizeEnabled() : card.isMoveEnabled();
        if (card.owner != pane || card.placement == null || !pane.isEditable() || card.isLocked() || !allowed
                || pane.getScene() == null) {
            return false;
        }
        started = true;
        startLayout = pane.committedPlacements();
        preview = new LinkedHashMap<>(startLayout);
        candidate = card.placement;
        layoutBefore = pane.getLayout();
        pane.releaseFromAnimation(card);
        viewOrderBefore = card.getViewOrder();
        if (!card.viewOrderProperty().isBound()) {
            card.setViewOrder(viewOrderBefore - 1.0);
        }
        card.pseudoClassStateChanged(edge == null ? RXDashboardCard.DRAGGING_PSEUDO_CLASS
                : RXDashboardCard.RESIZING_PSEUDO_CLASS, true);
        watch(pane.getScene());
        if (edge == null) {
            follow();
        }
        pane.requestLayout();
        return true;
    }

    // Follows the pointer, re-solves when it reaches another cell, and scrolls when it nears a viewport edge.
    private void update() {
        updateAutoScroll();
        track();
    }

    private void track() {
        if (edge == null) {
            follow();
        } else if (pane.isLiveResize()) {
            pane.requestLayout();
        }
        DashboardGrid grid = pane.grid();
        if (grid == null) {
            return;
        }
        Point2D local = pane.sceneToLocal(lastSceneX, lastSceneY);
        Placement start = startLayout.get(card);
        Placement next = edge == null ? dragCandidate(grid, start, local) : resizeCandidate(grid, start, local);
        if (next.equals(candidate)) {
            return;
        }
        Map<RXDashboardCard, Placement> solved = pane.solveFrom(startLayout, card, next);
        if (solved == null) {
            return;
        }
        candidate = next;
        preview = solved;
        pane.requestLayout();
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
        if (card.translateXProperty().isBound() || card.translateYProperty().isBound()) {
            return;
        }
        Point2D local = pane.sceneToLocal(lastSceneX, lastSceneY);
        card.setTranslateX(local.getX() - grabX - card.getLayoutX());
        card.setTranslateY(local.getY() - grabY - card.getLayoutY());
    }

    private void commit() {
        RXDashboardCard edited = card;
        Map<RXDashboardCard, Placement> result = preview;
        boolean changed = !result.equals(startLayout);
        RXDashboardLayout before = layoutBefore;
        end();
        if (changed) {
            pane.commitUserEdit(result, edited, before);
        }
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
        candidate = null;
        layoutBefore = null;
    }

    // ==================== Abnormal endings ====================

    private void watch(Scene scene) {
        watchedScene = scene;
        scene.addEventFilter(KeyEvent.KEY_PRESSED, escapeFilter);
        pane.localToSceneTransformProperty().addListener(sceneTransformListener);
        pane.getLocalToSceneTransform();
        watchedWindow = scene.getWindow();
        if (watchedWindow != null) {
            watchedWindow.focusedProperty().addListener(focusListener);
        }
    }

    private void unwatch() {
        pane.localToSceneTransformProperty().removeListener(sceneTransformListener);
        if (watchedScene != null) {
            watchedScene.removeEventFilter(KeyEvent.KEY_PRESSED, escapeFilter);
            watchedScene = null;
        }
        if (watchedWindow != null) {
            watchedWindow.focusedProperty().removeListener(focusListener);
            watchedWindow = null;
        }
    }

    // ==================== Auto-scroll ====================

    private void updateAutoScroll() {
        if (scrollStep() != 0.0) {
            if (autoScroll == null) {
                autoScroll = new Timeline(new KeyFrame(AUTO_SCROLL_TICK, event -> autoScrollTick()));
                autoScroll.setCycleCount(Animation.INDEFINITE);
            }
            if (autoScroll.getStatus() != Animation.Status.RUNNING) {
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
        Point2D origin = scroller.localToScene(scroller.snappedLeftInset(), scroller.snappedTopInset());
        double top = origin.getY();
        double bottom = top + scroller.getViewportBounds().getHeight();
        if (lastSceneY < top + AUTO_SCROLL_EDGE) {
            double depth = Math.min(1.0, (top + AUTO_SCROLL_EDGE - lastSceneY) / AUTO_SCROLL_EDGE);
            return -Math.max(1.0, depth * AUTO_SCROLL_MAX_STEP);
        }
        if (lastSceneY > bottom - AUTO_SCROLL_EDGE) {
            double depth = Math.min(1.0, (lastSceneY - (bottom - AUTO_SCROLL_EDGE)) / AUTO_SCROLL_EDGE);
            return Math.max(1.0, depth * AUTO_SCROLL_MAX_STEP);
        }
        return 0.0;
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
