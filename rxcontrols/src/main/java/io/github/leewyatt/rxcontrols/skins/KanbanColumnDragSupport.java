package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXKanbanColumn;
import io.github.leewyatt.rxcontrols.RXKanbanView;
import io.github.leewyatt.rxcontrols.event.ColumnMovedEvent;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Pointer drag-and-drop for reordering whole kanban columns, owned by
 * {@link RXKanbanViewSkin}. Dragging a column's header translates the real column box
 * to follow the pointer (columns are all realized — no virtualization), computes an
 * insertion index from the other columns' centers, and on release fires a vetoable
 * {@link ColumnMovedEvent} before reordering the columns list by index. Boxes glide
 * (FLIP) from their pre-reorder positions to the new order.
 *
 * @param <T> the card type
 */
final class KanbanColumnDragSupport<T> {

    private static final double DRAG_THRESHOLD = 6.0;
    private static final double AUTO_SCROLL_EDGE = 28.0;
    private static final double AUTO_SCROLL_MAX_STEP = 22.0;

    private final RXKanbanViewSkin<T> skin;
    private final RXKanbanView<T> control;

    private Timeline autoScroll;

    private boolean armed;
    private boolean started;
    private double pressSceneX;
    private double grabDx;
    private double lastPointerSceneX;

    private KanbanColumnBox<T> sourceBox;
    private RXKanbanColumn<T> sourceColumn;
    private int sourceIndex = -1;

    KanbanColumnDragSupport(RXKanbanViewSkin<T> skin, RXKanbanView<T> control) {
        this.skin = skin;
        this.control = control;
    }

    boolean isDragging() {
        return started;
    }

    void onMousePressed(MouseEvent event) {
        if (started) {
            // A drag is already in progress; ignore extra button presses so a second
            // button does not silently un-start it (which would strand the column).
            return;
        }
        if (event.getButton() != MouseButton.PRIMARY || !control.isColumnReorderEnabled()) {
            // Check the button BEFORE clearing `armed`: a non-primary press while a primary
            // gesture is armed-but-not-yet-dragging must not disarm it.
            return;
        }
        armed = false;
        KanbanColumnBox<T> box = skin.headerBoxAt(event.getTarget());
        if (box == null) {
            return;
        }
        sourceBox = box;
        sourceColumn = box.getColumn();
        sourceIndex = skin.columnBoxes().indexOf(box);
        pressSceneX = event.getSceneX();
        armed = sourceIndex >= 0;
    }

    void onMouseDragged(MouseEvent event) {
        if (!armed) {
            return;
        }
        if (!started) {
            if (Math.abs(event.getSceneX() - pressSceneX) < DRAG_THRESHOLD) {
                return;
            }
            startDrag(event.getSceneX());
        }
        updateDrag(event.getSceneX());
        event.consume();
    }

    void onMouseReleased(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            // Only the primary button (the one that started the drag) ends it; releasing
            // some other button mid-drag must not commit or strand the gesture.
            return;
        }
        if (!started) {
            armed = false;
            return;
        }
        finishDrag(event.getSceneX());
        event.consume();
    }

    void cancel() {
        if (started) {
            stopAutoScroll();
            // Capture the preview positions, then drop back to normal layout so the
            // parted neighbours (and the dragged column) glide back to their home slots.
            skin.beginColumnFlip();
            skin.clearColumnReorderPreview();
            if (sourceBox != null) {
                sourceBox.setColumnDragging(false);
                sourceBox.setTranslateX(0.0);
            }
            control.requestLayout();
        }
        reset();
    }

    void dispose() {
        stopAutoScroll();
        reset();
    }

    // ==================== Lifecycle ====================

    private void startDrag(double sceneX) {
        started = true;
        sourceBox.setColumnDragging(true);
        sourceBox.toFront();
        Bounds boxScene = sourceBox.localToScene(sourceBox.getBoundsInLocal());
        grabDx = pressSceneX - boxScene.getMinX();
    }

    private void updateDrag(double sceneX) {
        lastPointerSceneX = sceneX;
        moveBox(sceneX);
        // Open (and glide neighbours to) a make-way gap at the current hover index.
        skin.setColumnReorderPreview(sourceIndex, computeTargetIndex(sceneX));
        updateAutoScroll(sceneX);
    }

    private void moveBox(double sceneX) {
        // Desired box left in the parent's local space, then expressed as a translate
        // over the layout x the skin assigned.
        Bounds parentScene = sourceBox.getParent().localToScene(sourceBox.getParent().getBoundsInLocal());
        double wantedLocalX = sceneX - grabDx - parentScene.getMinX();
        sourceBox.setTranslateX(wantedLocalX - sourceBox.getLayoutX());
    }

    private void finishDrag(double sceneX) {
        stopAutoScroll();
        int visibleTarget = computeTargetIndex(sceneX);
        int fromIndex = sourceIndex;
        RXKanbanColumn<T> column = sourceColumn;

        // Capture FLIP origins (every box's current visual x, including this box's drag
        // translate) BEFORE mutating, so the next layout glides them into the new order.
        skin.beginColumnFlip();
        skin.clearColumnReorderPreview();
        sourceBox.setColumnDragging(false);
        sourceBox.setTranslateX(0.0);

        ObservableList<RXKanbanColumn<T>> columns = control.getColumns();
        if (columns != null && fromIndex >= 0 && fromIndex < columns.size()
                && columns.get(fromIndex) == column) {
            List<RXKanbanColumn<T>> reordered = new ArrayList<>(columns);
            reordered.remove(fromIndex);
            // computeTargetIndex counts VISIBLE non-source columns; translate that into an
            // absolute index in the source-removed list so hidden columns keep their slots
            // and never shift the drop position.
            int toIndex = absoluteInsertionIndex(reordered, visibleTarget);
            if (toIndex != fromIndex) {
                ColumnMovedEvent<T> event = new ColumnMovedEvent<>(control, fromIndex, toIndex, column);
                control.fireEvent(event);
                if (!event.isConsumed()) {
                    // Commit as a single atomic setAll, NOT remove(fromIndex)+add(toIndex):
                    // the two-step form leaves the dragged column momentarily absent, which
                    // reconciles it away (disposing its viewport + clearing its selection).
                    reordered.add(toIndex, column);
                    columns.setAll(reordered);
                }
            }
        }
        reset();
        control.requestLayout();
    }

    // Map a visible-column insertion index (from computeTargetIndex) into an index in the
    // source-removed list such that exactly `visibleTarget` visible columns precede the drop.
    private int absoluteInsertionIndex(List<RXKanbanColumn<T>> withoutSource, int visibleTarget) {
        int visibleSeen = 0;
        for (int i = 0; i < withoutSource.size(); i++) {
            if (visibleSeen == visibleTarget) {
                return i;
            }
            if (withoutSource.get(i).isVisible()) {
                visibleSeen++;
            }
        }
        return withoutSource.size();
    }

    // Insertion index in the source-removed coordinate system: how many other columns'
    // centers sit left of the pointer.
    private int computeTargetIndex(double sceneX) {
        int index = 0;
        for (int i = 0; i < skin.columnBoxes().size(); i++) {
            if (i == sourceIndex) {
                continue;
            }
            KanbanColumnBox<T> box = skin.columnBoxes().get(i);
            if (!box.isVisible()) {
                // A hidden neighbor has no on-board position to compare against.
                continue;
            }
            Bounds b = box.localToScene(box.getBoundsInLocal());
            double center = (b.getMinX() + b.getMaxX()) / 2.0;
            if (center < sceneX) {
                index++;
            }
        }
        return index;
    }

    private void reset() {
        armed = false;
        started = false;
        sourceBox = null;
        sourceColumn = null;
        sourceIndex = -1;
    }

    // ==================== Auto-scroll ====================

    private void updateAutoScroll(double sceneX) {
        Bounds board = skin.columnBoxes().isEmpty() ? null : boardBounds();
        boolean near = board != null
                && (sceneX < board.getMinX() + AUTO_SCROLL_EDGE || sceneX > board.getMaxX() - AUTO_SCROLL_EDGE);
        if (near) {
            startAutoScroll();
        } else {
            stopAutoScroll();
        }
    }

    private Bounds boardBounds() {
        return skin.getOverlayBoardBounds();
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
        Bounds board = boardBounds();
        if (board == null) {
            return;
        }
        double step = 0.0;
        if (lastPointerSceneX < board.getMinX() + AUTO_SCROLL_EDGE) {
            double ratio = Math.min(1.0, (board.getMinX() + AUTO_SCROLL_EDGE - lastPointerSceneX) / AUTO_SCROLL_EDGE);
            step = -Math.max(1.0, ratio * AUTO_SCROLL_MAX_STEP);
        } else if (lastPointerSceneX > board.getMaxX() - AUTO_SCROLL_EDGE) {
            double ratio = Math.min(1.0, (lastPointerSceneX - (board.getMaxX() - AUTO_SCROLL_EDGE)) / AUTO_SCROLL_EDGE);
            step = Math.max(1.0, ratio * AUTO_SCROLL_MAX_STEP);
        }
        if (step != 0.0) {
            skin.scrollBoardBy(step);
            control.layout();
            moveBox(lastPointerSceneX);
        }
    }
}
