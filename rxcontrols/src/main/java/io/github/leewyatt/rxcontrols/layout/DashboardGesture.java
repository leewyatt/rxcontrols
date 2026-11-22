package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout.Placement;
import javafx.beans.InvalidationListener;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The user gestures of an {@link RXDashboardPane}: dragging a card. One instance per pane.
 *
 * <p>A press on a card that its content did not consume arms the gesture; once the pointer has moved
 * {@link #DRAG_THRESHOLD} pixels it starts. The card then follows the pointer through its translate, raised above
 * the other cards by its view order, while its layout position stays where the gesture started. Whenever the
 * pointer reaches another cell, the edit is solved from the layout the gesture started with; an accepted candidate
 * becomes the preview the pane lays the other cards out by, and the placeholder marks it. Releasing commits the
 * preview; Esc, the window losing focus, the pane leaving its scene and any change to the pane's cards or layout
 * cancel it, and every card glides back from where it shows.</p>
 */
final class DashboardGesture {

    /**
     * Pointer travel, in pixels, before a press becomes a drag.
     */
    static final double DRAG_THRESHOLD = 6.0;

    private final RXDashboardPane pane;

    private boolean armed;
    private boolean started;
    private RXDashboardCard card;
    private double pressSceneX;
    private double pressSceneY;
    private double lastSceneX;
    private double lastSceneY;
    // The pointer's offset from the card's top-left corner as it showed at the press, in the pane's coordinates.
    private double grabX;
    private double grabY;
    private double viewOrderBefore;

    private Map<RXDashboardCard, Placement> startLayout;
    private Map<RXDashboardCard, Placement> preview;
    private Placement candidate;
    private RXDashboardLayout layoutBefore;

    private Scene watchedScene;
    private Window watchedWindow;

    private final EventHandler<KeyEvent> escapeFilter = event -> {
        if (event.getCode() == KeyCode.ESCAPE && started) {
            cancel();
            event.consume();
        }
    };

    // Switching to another application can swallow the release; the card would then stick to the pointer.
    private final InvalidationListener focusListener = observable -> {
        if (watchedWindow != null && !watchedWindow.isFocused()) {
            cancel();
        }
    };

    DashboardGesture(RXDashboardPane pane) {
        this.pane = pane;
        pane.addEventHandler(MouseEvent.MOUSE_PRESSED, this::pressed);
        pane.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::dragged);
        pane.addEventHandler(MouseEvent.MOUSE_RELEASED, this::released);
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
     * Lays the dragged card out: at the cells it had when the gesture started, displaced by the pointer.
     *
     * @param grid the grid of the pass
     */
    void layoutCard(DashboardGrid grid) {
        Placement start = startLayout.get(card);
        card.resize(grid.spanWidth(start.column(), start.columnSpan()), grid.spanHeight(start.row(), start.rowSpan()));
        card.relocate(grid.columnX(start.column()), grid.rowY(start.row()));
        follow();
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
        }
    }

    // ==================== Mouse ====================

    private void pressed(MouseEvent event) {
        if (started || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        armed = false;
        RXDashboardCard pressed = cardOf(event.getTarget());
        if (pressed == null || !pane.isEditable() || pressed.isLocked() || !pressed.isMoveEnabled()
                || !onHandle(event.getTarget(), pressed)) {
            return;
        }
        card = pressed;
        pressSceneX = event.getSceneX();
        pressSceneY = event.getSceneY();
        lastSceneX = pressSceneX;
        lastSceneY = pressSceneY;
        Point2D local = pane.sceneToLocal(pressSceneX, pressSceneY);
        grabX = local.getX() - (card.getLayoutX() + card.getTranslateX());
        grabY = local.getY() - (card.getLayoutY() + card.getTranslateY());
        armed = true;
    }

    private void dragged(MouseEvent event) {
        if (!armed) {
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
                return;
            }
        }
        update();
        event.consume();
    }

    private void released(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        armed = false;
        if (started) {
            commit();
            event.consume();
        }
    }

    // ==================== Lifecycle ====================

    private boolean start() {
        // The card may have left the pane, or the pane its editable state, between the press and now.
        if (card.owner != pane || card.placement == null || !pane.isEditable() || card.isLocked()
                || !card.isMoveEnabled() || pane.getScene() == null) {
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
        card.pseudoClassStateChanged(RXDashboardCard.DRAGGING_PSEUDO_CLASS, true);
        watch(pane.getScene());
        follow();
        pane.requestLayout();
        return true;
    }

    // Follows the pointer and re-solves when it reaches another cell.
    private void update() {
        follow();
        DashboardGrid grid = pane.grid();
        if (grid == null) {
            return;
        }
        Point2D local = pane.sceneToLocal(lastSceneX, lastSceneY);
        Placement start = startLayout.get(card);
        Placement next = start.withPosition(grid.nearestColumn(local.getX() - grabX, start.columnSpan()),
                grid.nearestRow(local.getY() - grabY));
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
        card = null;
        startLayout = null;
        preview = null;
        candidate = null;
        layoutBefore = null;
    }

    // ==================== Abnormal endings ====================

    private void watch(Scene scene) {
        watchedScene = scene;
        scene.addEventFilter(KeyEvent.KEY_PRESSED, escapeFilter);
        watchedWindow = scene.getWindow();
        if (watchedWindow != null) {
            watchedWindow.focusedProperty().addListener(focusListener);
        }
    }

    private void unwatch() {
        if (watchedScene != null) {
            watchedScene.removeEventFilter(KeyEvent.KEY_PRESSED, escapeFilter);
            watchedScene = null;
        }
        if (watchedWindow != null) {
            watchedWindow.focusedProperty().removeListener(focusListener);
            watchedWindow = null;
        }
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
