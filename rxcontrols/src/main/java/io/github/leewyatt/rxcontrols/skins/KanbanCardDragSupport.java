package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXKanbanCardCell;
import io.github.leewyatt.rxcontrols.RXKanbanCardDropContext;
import io.github.leewyatt.rxcontrols.RXKanbanColumn;
import io.github.leewyatt.rxcontrols.RXKanbanView;
import io.github.leewyatt.rxcontrols.event.RXCardMovedEvent;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Callback;
import javafx.util.Duration;

/**
 * Pointer drag-and-drop for kanban cards, owned by {@link RXKanbanViewSkin}. It
 * runs a three-phase mouse state machine (press / drag / release), renders a
 * snapshot ghost on a board-top overlay (above every column clip), collapses the
 * source card's slot ({@link KanbanColumnViewport#setLiftedIndex(int)}), opens a
 * make-way gap at the drop index ({@link KanbanColumnViewport#setDropGap(int)}),
 * auto-scrolls near the edges and, on release, fires a vetoable
 * {@link RXCardMovedEvent} before mutating the model by index.
 *
 * @param <T> the card type
 */
final class KanbanCardDragSupport<T> {

    // Pointer travel before a press becomes a drag.
    private static final double DRAG_THRESHOLD = 6.0;
    // Distance from an edge at which auto-scroll starts, and its per-tick pixel cap.
    private static final double AUTO_SCROLL_EDGE = 28.0;
    private static final double AUTO_SCROLL_MAX_STEP = 22.0;
    private static final double GHOST_OPACITY = 0.9;
    private static final double GHOST_INVALID_OPACITY = 0.45;

    private final RXKanbanViewSkin<T> skin;
    private final RXKanbanView<T> control;
    private final Pane overlay = new Pane();

    private Timeline autoScroll;

    // Armed on a press over a card; started once the pointer passes the threshold.
    private boolean armed;
    private boolean started;
    private double pressSceneX;
    private double pressSceneY;
    private double grabDx;
    private double grabDy;

    private RXKanbanColumn<T> sourceColumn;
    private int sourceIndex = -1;
    private T card;
    private ImageView ghost;

    private KanbanColumnBox<T> targetBox;
    private int dropIndex = -1;
    private boolean dropValid;

    private double lastPointerSceneX;
    private double lastPointerSceneY;

    KanbanCardDragSupport(RXKanbanViewSkin<T> skin, RXKanbanView<T> control) {
        this.skin = skin;
        this.control = control;
        overlay.getStyleClass().add("drag-overlay");
        overlay.setManaged(false);
        overlay.setMouseTransparent(true);
        overlay.setVisible(false);
    }

    // ==================== Skin-facing ====================

    Pane getOverlay() {
        return overlay;
    }

    boolean isDragging() {
        return started;
    }

    void layoutOverlay(double x, double y, double width, double height) {
        overlay.resizeRelocate(x, y, width, height);
    }

    void onMousePressed(MouseEvent event) {
        if (started) {
            // A drag is already in progress; ignore extra button presses so a second
            // button does not silently un-start it (which would strand the ghost).
            return;
        }
        if (event.getButton() != MouseButton.PRIMARY || !control.isCardDragEnabled()) {
            // Check the button BEFORE clearing `armed`: a non-primary press while a primary
            // gesture is armed-but-not-yet-dragging must not disarm it.
            return;
        }
        armed = false;
        RXKanbanCardCell<T> cell = skin.cardCellAt(event.getTarget());
        if (cell == null) {
            return;
        }
        sourceColumn = cell.getColumn();
        sourceIndex = cell.getIndex();
        if (sourceColumn == null || sourceIndex < 0 || sourceIndex >= sourceColumn.getCards().size()) {
            return;
        }
        Bounds cellScene = cell.localToScene(cell.getBoundsInLocal());
        pressSceneX = event.getSceneX();
        pressSceneY = event.getSceneY();
        grabDx = pressSceneX - cellScene.getMinX();
        grabDy = pressSceneY - cellScene.getMinY();
        armed = true;
    }

    void onMouseDragged(MouseEvent event) {
        if (!armed) {
            return;
        }
        if (!started) {
            double dx = event.getSceneX() - pressSceneX;
            double dy = event.getSceneY() - pressSceneY;
            if (dx * dx + dy * dy < DRAG_THRESHOLD * DRAG_THRESHOLD) {
                return;
            }
            startDrag(event);
        }
        updateDrag(event.getSceneX(), event.getSceneY());
        event.consume();
    }

    void onMouseReleased(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            // Only the primary button (the one that started the drag) ends it; releasing
            // some other button mid-drag must not commit or drop the gesture.
            return;
        }
        if (!started) {
            armed = false;
            return;
        }
        finishDrag();
        event.consume();
    }

    void cancel() {
        if (started) {
            stopAutoScroll();
            cleanup();
        }
        armed = false;
    }

    void dispose() {
        stopAutoScroll();
        cleanup();
    }

    // ==================== Drag lifecycle ====================

    private void startDrag(MouseEvent event) {
        card = sourceColumn.getCards().get(sourceIndex);
        RXKanbanCardCell<T> cell = skin.cardCellAt(event.getTarget());
        ghost = buildGhost(cell != null ? cell : sourceCellFallback());
        if (ghost != null) {
            overlay.getChildren().add(ghost);
        }
        overlay.setVisible(true);
        started = true;
        KanbanColumnBox<T> sourceBox = skin.boxFor(sourceColumn);
        if (sourceBox != null) {
            sourceBox.getViewport().setLiftedIndex(sourceIndex);
        }
    }

    private ImageView buildGhost(RXKanbanCardCell<T> cell) {
        if (cell == null) {
            return null;
        }
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        WritableImage image = cell.snapshot(params, null);
        ImageView view = new ImageView(image);
        view.setManaged(false);
        view.setMouseTransparent(true);
        view.setOpacity(GHOST_OPACITY);
        return view;
    }

    // The pressed cell is the drag origin; recovering it here keeps startDrag robust
    // if the event target resolved to a child node of the cell.
    private RXKanbanCardCell<T> sourceCellFallback() {
        KanbanColumnBox<T> box = skin.boxFor(sourceColumn);
        if (box == null) {
            return null;
        }
        for (Object node : box.getViewport().lookupAll(".rx-kanban-card-cell")) {
            if (node instanceof RXKanbanCardCell<?> candidate
                    && candidate.getColumn() == sourceColumn && candidate.getIndex() == sourceIndex) {
                @SuppressWarnings("unchecked")
                RXKanbanCardCell<T> typed = (RXKanbanCardCell<T>) candidate;
                return typed;
            }
        }
        return null;
    }

    private void updateDrag(double sceneX, double sceneY) {
        lastPointerSceneX = sceneX;
        lastPointerSceneY = sceneY;
        moveGhost(sceneX, sceneY);
        updateDropTarget(sceneX, sceneY);
        updateAutoScroll(sceneX, sceneY);
    }

    private void moveGhost(double sceneX, double sceneY) {
        if (ghost == null) {
            return;
        }
        Point2D local = overlay.sceneToLocal(sceneX - grabDx, sceneY - grabDy);
        ghost.setTranslateX(local.getX());
        ghost.setTranslateY(local.getY());
    }

    private void updateDropTarget(double sceneX, double sceneY) {
        KanbanColumnBox<T> box = columnAt(sceneX, sceneY);
        if (box == null) {
            clearDropTarget();
            dropValid = false;
            setGhostValid(false);
            return;
        }
        KanbanColumnViewport<T> viewport = box.getViewport();
        Point2D localInViewport = viewport.sceneToLocal(sceneX, sceneY);
        double contentY = localInViewport.getY() + viewport.scrollOffset();
        int index = viewport.dropIndexAt(contentY);
        boolean valid = validateDrop(box.getColumn(), index);

        if (box != targetBox) {
            clearDropTarget();
        }
        targetBox = box;
        dropIndex = index;
        dropValid = valid;
        if (valid) {
            viewport.setDropGap(index);
            box.setDropTarget(true);
        } else {
            viewport.setDropGap(-1);
            box.setDropTarget(false);
        }
        setGhostValid(valid);
    }

    private boolean validateDrop(RXKanbanColumn<T> targetColumn, int index) {
        Callback<RXKanbanCardDropContext<T>, Boolean> validator = control.getDropValidator();
        if (validator == null) {
            return true;
        }
        RXKanbanCardDropContext<T> context =
                new RXKanbanCardDropContext<>(card, sourceColumn, sourceIndex, targetColumn, index);
        return Boolean.TRUE.equals(validator.call(context));
    }

    private void setGhostValid(boolean valid) {
        if (ghost != null) {
            ghost.setOpacity(valid ? GHOST_OPACITY : GHOST_INVALID_OPACITY);
        }
    }

    private void finishDrag() {
        stopAutoScroll();
        boolean commit = dropValid && targetBox != null;
        RXKanbanColumn<T> target = targetBox != null ? targetBox.getColumn() : null;
        int toIndex = dropIndex;
        RXKanbanColumn<T> from = sourceColumn;
        int fromIndex = sourceIndex;
        T moved = card;
        cleanup();
        if (commit) {
            commitMove(from, fromIndex, target, toIndex, moved);
        }
    }

    private void commitMove(RXKanbanColumn<T> from, int fromIndex, RXKanbanColumn<T> to, int toIndex, T moved) {
        RXCardMovedEvent<T> event = new RXCardMovedEvent<>(control, from, fromIndex, to, toIndex, moved);
        control.fireEvent(event);
        if (event.isConsumed()) {
            // Controlled / immutable data: the handler applies the change itself.
            return;
        }
        // Guard by identity, not just bounds: if a listener mutated the source list during
        // the gesture, fromIndex may now point at a different card — never remove the wrong one.
        if (fromIndex < 0 || fromIndex >= from.getCards().size() || from.getCards().get(fromIndex) != moved) {
            return;
        }
        from.getCards().remove(fromIndex);
        int clampedTo = RXMath.clamp(toIndex, 0, to.getCards().size());
        to.getCards().add(clampedTo, moved);
        control.updateSelection(to, clampedTo);
        control.updateFocus(to, clampedTo);
        skin.refreshCellStates();
    }

    private void cleanup() {
        if (sourceColumn != null) {
            KanbanColumnBox<T> sourceBox = skin.boxFor(sourceColumn);
            if (sourceBox != null) {
                sourceBox.getViewport().setLiftedIndex(-1);
            }
        }
        clearDropTarget();
        if (ghost != null) {
            overlay.getChildren().remove(ghost);
            ghost = null;
        }
        overlay.setVisible(false);
        started = false;
        armed = false;
        sourceColumn = null;
        sourceIndex = -1;
        card = null;
        dropIndex = -1;
        dropValid = false;
    }

    private void clearDropTarget() {
        if (targetBox != null) {
            targetBox.getViewport().setDropGap(-1);
            targetBox.setDropTarget(false);
            targetBox = null;
        }
    }

    // ==================== Hit testing ====================

    private KanbanColumnBox<T> columnAt(double sceneX, double sceneY) {
        // Test against the columns AREA (excludes the horizontal scroll bar strip below it),
        // not the full-height overlay: a release on the scroll bar — or otherwise off the card
        // area — is a no-op rather than committing into the nearest column. The columns-area
        // rectangle is also ghost-independent (the ghost lives in the unclipped overlay).
        Bounds boardScene = skin.getColumnsAreaBounds();
        if (sceneX < boardScene.getMinX() || sceneX > boardScene.getMaxX()
                || sceneY < boardScene.getMinY() || sceneY > boardScene.getMaxY()) {
            return null;
        }
        KanbanColumnBox<T> best = null;
        double bestDistance = Double.MAX_VALUE;
        for (KanbanColumnBox<T> box : skin.columnBoxes()) {
            if (!box.isVisible()) {
                // A fully collapsed (hidden) column is not a drop target.
                continue;
            }
            Bounds b = box.localToScene(box.getBoundsInLocal());
            if (sceneX >= b.getMinX() && sceneX <= b.getMaxX()) {
                return box;
            }
            double center = (b.getMinX() + b.getMaxX()) / 2.0;
            double distance = Math.abs(sceneX - center);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = box;
            }
        }
        return best;
    }

    // ==================== Auto-scroll ====================

    private void updateAutoScroll(double sceneX, double sceneY) {
        if (needsAutoScroll(sceneX, sceneY)) {
            startAutoScroll();
        } else {
            stopAutoScroll();
        }
    }

    private boolean needsAutoScroll(double sceneX, double sceneY) {
        Bounds board = skin.getColumnsAreaBounds();
        boolean horizontal = sceneX < board.getMinX() + AUTO_SCROLL_EDGE || sceneX > board.getMaxX() - AUTO_SCROLL_EDGE;
        KanbanColumnBox<T> box = columnAt(sceneX, sceneY);
        boolean vertical = false;
        if (box != null) {
            Bounds vp = box.getViewport().localToScene(box.getViewport().getBoundsInLocal());
            vertical = sceneY < vp.getMinY() + AUTO_SCROLL_EDGE || sceneY > vp.getMaxY() - AUTO_SCROLL_EDGE;
        }
        return horizontal || vertical;
    }

    private void startAutoScroll() {
        if (autoScroll == null) {
            autoScroll = new Timeline(new KeyFrame(Duration.millis(16.0), e -> autoScrollTick()));
            autoScroll.setCycleCount(Animation.INDEFINITE);
        }
        if (autoScroll.getStatus() != Animation.Status.RUNNING) {
            autoScroll.play();
        }
    }

    private void stopAutoScroll() {
        if (autoScroll != null) {
            autoScroll.stop();
        }
    }

    private void autoScrollTick() {
        if (!started) {
            stopAutoScroll();
            return;
        }
        Bounds board = skin.getColumnsAreaBounds();
        double horizontalStep = edgeStep(lastPointerSceneX, board.getMinX(), board.getMaxX());
        if (horizontalStep != 0.0) {
            skin.scrollBoardBy(horizontalStep);
        }
        KanbanColumnBox<T> box = columnAt(lastPointerSceneX, lastPointerSceneY);
        if (box != null) {
            Bounds vp = box.getViewport().localToScene(box.getViewport().getBoundsInLocal());
            double verticalStep = edgeStep(lastPointerSceneY, vp.getMinY(), vp.getMaxY());
            if (verticalStep != 0.0) {
                box.getViewport().scrollByPixels(verticalStep);
                box.getViewport().layout();
            }
        }
        control.layout();
        updateDropTarget(lastPointerSceneX, lastPointerSceneY);
        moveGhost(lastPointerSceneX, lastPointerSceneY);
    }

    // Signed pixel step: negative near the low edge, positive near the high edge,
    // ramped by how deep into the edge band the pointer is.
    private double edgeStep(double value, double min, double max) {
        if (value < min + AUTO_SCROLL_EDGE) {
            double ratio = Math.min(1.0, (min + AUTO_SCROLL_EDGE - value) / AUTO_SCROLL_EDGE);
            return -Math.max(1.0, ratio * AUTO_SCROLL_MAX_STEP);
        }
        if (value > max - AUTO_SCROLL_EDGE) {
            double ratio = Math.min(1.0, (value - (max - AUTO_SCROLL_EDGE)) / AUTO_SCROLL_EDGE);
            return Math.max(1.0, ratio * AUTO_SCROLL_MAX_STEP);
        }
        return 0.0;
    }
}
