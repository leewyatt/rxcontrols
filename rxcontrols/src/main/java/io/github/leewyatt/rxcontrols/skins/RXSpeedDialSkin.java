package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXFloatingActionButton;
import io.github.leewyatt.rxcontrols.RXSpeedDial;
import io.github.leewyatt.rxcontrols.RXSpeedDialAction;
import io.github.leewyatt.rxcontrols.event.RXSpeedDialEvent;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Default skin for {@link RXSpeedDial}.
 */
public class RXSpeedDialSkin extends RXSkinBase<RXSpeedDial> {

    private static final double HIDDEN_ACTION_SCALE = 0.6;
    private static final double VISIBLE_ACTION_SCALE = 1.0;
    private static final double HIDDEN_OPACITY = 0.0;
    private static final double VISIBLE_OPACITY = 1.0;
    private static final double CLOSED_ICON_ROTATION = 0.0;
    private static final double OPEN_ICON_ROTATION = 45.0;

    private final RXFloatingActionButton mainFab = new MainFab();
    private final Pane actionsLayer = new Pane();
    private final StackPane iconMorph = new StackPane();
    private final List<ActionCell> cells = new ArrayList<>();
    private final EventHandler<MouseEvent> sceneMousePressedFilter = this::handleSceneMousePressed;
    private final ChangeListener<Node> sceneFocusOwnerListener = (observable, oldValue, newValue) -> handleFocusChanged();

    private final ListChangeListener<RXSpeedDialAction> actionsListener = change -> rebuildCells();
    private final WeakListChangeListener<RXSpeedDialAction> weakActionsListener =
            new WeakListChangeListener<>(actionsListener);

    private Scene observedScene;
    private Timeline openCloseAnim;
    private boolean terminalEventPending;
    private RXSpeedDial.CloseReason pendingHiddenReason;
    private boolean disposed;
    private boolean hoverOpenSuppressed;

    // ==================== Constructors ====================

    /**
     * Creates a speed-dial skin.
     *
     * @param control the control this skin is attached to
     */
    public RXSpeedDialSkin(RXSpeedDial control) {
        super(control);
        initializeNodes();
        registerListeners(control);
        rebuildCells();
        snapTo(control.isShowing());
    }

    private void initializeNodes() {
        actionsLayer.getStyleClass().add("actions");
        actionsLayer.setManaged(false);
        iconMorph.getStyleClass().add("icon-morph");
        iconMorph.setMouseTransparent(true);
        mainFab.setGraphic(iconMorph);
        getChildren().setAll(actionsLayer, mainFab);
    }

    private void registerListeners(RXSpeedDial control) {
        control.getActions().addListener(weakActionsListener);
        disposer.registerDisposeTask(() -> control.getActions().removeListener(weakActionsListener));
        disposer.registerListener(control.iconProperty(), this::updateDisplayedIcon);
        disposer.registerListener(control.openIconProperty(), this::updateDisplayedIcon);
        disposer.registerListener(control.labelModeProperty(), this::onLabelModeChanged);
        disposer.registerListener(control.showingProperty(), this::onShowingChanged);
        disposer.registerListener(control.sceneProperty(), this::onSceneChanged);
        disposer.registerEventHandler(control, RXSpeedDialEvent.HIDING, this::onHiding);
        disposer.registerEventHandler(mainFab, ActionEvent.ACTION, event -> control.toggle());
        disposer.registerEventHandler(control, MouseEvent.MOUSE_ENTERED, this::handleMouseEntered);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_EXITED, this::handleMouseExited);
        disposer.registerEventFilter(control, KeyEvent.KEY_PRESSED, this::handleKeyPressed);
        disposer.registerEventFilter(control, KeyEvent.KEY_PRESSED, this::handleNavigationKeyPressed);
        disposer.registerDisposeTask(this::detachSceneObservers);
        updateDisplayedIcon();
        onSceneChanged();
    }

    private void onShowingChanged() {
        boolean opening = getSkinnable().isShowing();
        terminalEventPending = true;
        if (opening) {
            pendingHiddenReason = null;
        }
        stopOpenCloseAnimation();
        mainFab.notifyAccessibleAttributeChanged(AccessibleAttribute.EXPANDED);
        Duration duration = getSkinnable().getAnimationDuration();
        if (!getSkinnable().isAnimated() || !PageTransitionEngine.isPositiveFinite(duration)) {
            snapTo(opening);
            fireTerminalEvent(opening);
            return;
        }
        prepareIconMorphForAnimation(opening);
        playOpenCloseAnimation(opening, duration);
    }

    private void updateDisplayedIcon() {
        RXSpeedDial control = getSkinnable();
        boolean interrupted = openCloseAnim != null;
        if (interrupted) {
            stopOpenCloseAnimation();
        }
        iconMorph.getChildren().clear();
        Node closedIcon = control.getIcon();
        Node openIcon = control.getOpenIcon();
        if (closedIcon != null) {
            iconMorph.getChildren().add(closedIcon);
        }
        if (openIcon != null && openIcon != closedIcon) {
            iconMorph.getChildren().add(openIcon);
        }
        snapTo(control.isShowing());
        if (interrupted) {
            fireTerminalEvent(control.isShowing());
        }
    }

    private void onLabelModeChanged() {
        for (ActionCell cell : cells) {
            cell.updateLabelMode();
        }
        getSkinnable().requestLayout();
    }

    // ==================== Actions ====================

    private void rebuildCells() {
        if (openCloseAnim != null) {
            settleAnimationToCurrentState();
        }
        clearCells();
        for (RXSpeedDialAction action : getSkinnable().getActions()) {
            if (action == null) {
                continue;
            }
            ActionCell cell = new ActionCell(action);
            cells.add(cell);
            actionsLayer.getChildren().add(cell.root);
        }
        snapTo(getSkinnable().isShowing());
        getSkinnable().requestLayout();
    }

    private void clearCells() {
        for (ActionCell cell : cells) {
            cell.dispose();
        }
        cells.clear();
        actionsLayer.getChildren().clear();
    }

    // ==================== Layout ====================

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + mainFab.prefWidth(-1) + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + mainFab.prefHeight(-1) + bottomInset;
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return computePrefWidth(height, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return computePrefWidth(height, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected void layoutChildren(double x, double y, double width, double height) {
        actionsLayer.resizeRelocate(x, y, width, height);
        layoutInArea(mainFab, x, y, width, height, -1, HPos.CENTER, VPos.CENTER);

        RXSpeedDial.Direction direction = directionOrDefault();
        double centerX = snapPositionX(width / 2.0);
        double centerY = snapPositionY(height / 2.0);
        double offset = fabAxisExtent(mainFab, direction) / 2.0;
        double spacing = actionSpacing(direction);
        for (ActionCell cell : cells) {
            if (!cell.isVisible()) {
                continue;
            }
            double actionExtent = cell.axisExtent(direction);
            offset += spacing + actionExtent / 2.0;
            switch (direction) {
                case UP -> cell.layoutAt(centerX, centerY - offset, direction);
                case DOWN -> cell.layoutAt(centerX, centerY + offset, direction);
                case LEFT -> cell.layoutAt(centerX - offset, centerY, direction);
                case RIGHT -> cell.layoutAt(centerX + offset, centerY, direction);
            }
            offset += actionExtent / 2.0;
        }
    }

    private double fabAxisExtent(RXFloatingActionButton fab, RXSpeedDial.Direction direction) {
        if (direction == RXSpeedDial.Direction.LEFT || direction == RXSpeedDial.Direction.RIGHT) {
            return snapSizeX(fab.prefWidth(-1));
        }
        return snapSizeY(fab.prefHeight(-1));
    }

    private double actionSpacing(RXSpeedDial.Direction direction) {
        if (direction == RXSpeedDial.Direction.LEFT || direction == RXSpeedDial.Direction.RIGHT) {
            return snapSizeX(actionSpacing());
        }
        return snapSizeY(actionSpacing());
    }

    private RXSpeedDial.Direction directionOrDefault() {
        RXSpeedDial.Direction direction = getSkinnable().getDirection();
        return direction == null ? RXSpeedDial.Direction.UP : direction;
    }

    private RXSpeedDial.OpenTrigger openTriggerOrDefault() {
        RXSpeedDial.OpenTrigger trigger = getSkinnable().getOpenTrigger();
        return trigger == null ? RXSpeedDial.OpenTrigger.CLICK : trigger;
    }

    private RXSpeedDial.LabelMode labelModeOrDefault() {
        RXSpeedDial.LabelMode mode = getSkinnable().getLabelMode();
        return mode == null ? RXSpeedDial.LabelMode.HOVER : mode;
    }

    private RXSpeedDial.LabelPlacement labelPlacementOrDefault() {
        RXSpeedDial.LabelPlacement placement = getSkinnable().getLabelPlacement();
        return placement == null ? RXSpeedDial.LabelPlacement.AUTO : placement;
    }

    private RXSpeedDial.LabelPlacement effectiveLabelPlacement(RXSpeedDial.Direction direction) {
        RXSpeedDial.LabelPlacement placement = labelPlacementOrDefault();
        if (placement != RXSpeedDial.LabelPlacement.AUTO) {
            return placement;
        }
        return direction == RXSpeedDial.Direction.RIGHT
                ? RXSpeedDial.LabelPlacement.END
                : RXSpeedDial.LabelPlacement.START;
    }

    private double actionSpacing() {
        return RXMath.sanitizeFiniteNonNegative(getSkinnable().getActionSpacing());
    }

    private double labelGap() {
        return RXMath.sanitizeFiniteNonNegative(getSkinnable().getLabelGap());
    }

    private void snapTo(boolean showing) {
        snapIconMorph(showing);
        actionsLayer.setVisible(showing);
        actionsLayer.setMouseTransparent(!showing);
        for (ActionCell cell : cells) {
            cell.updateRootVisibility(showing);
            cell.snapTo(showing);
        }
    }

    private void snapIconMorph(boolean showing) {
        iconMorph.setRotate(showing ? OPEN_ICON_ROTATION : CLOSED_ICON_ROTATION);
        Node closedIcon = getSkinnable().getIcon();
        Node configuredOpenIcon = getSkinnable().getOpenIcon();
        Node openIcon = configuredOpenIcon == closedIcon ? null : configuredOpenIcon;
        for (Node child : iconMorph.getChildren()) {
            boolean openNode = openIcon != null && child == openIcon;
            child.setOpacity(openNode == showing || openIcon == null ? VISIBLE_OPACITY : HIDDEN_OPACITY);
        }
    }

    private void prepareIconMorphForAnimation(boolean opening) {
        iconMorph.setRotate(opening ? CLOSED_ICON_ROTATION : OPEN_ICON_ROTATION);
        Node closedIcon = getSkinnable().getIcon();
        Node configuredOpenIcon = getSkinnable().getOpenIcon();
        Node openIcon = configuredOpenIcon == closedIcon ? null : configuredOpenIcon;
        if (openIcon == null) {
            if (closedIcon != null) {
                closedIcon.setOpacity(VISIBLE_OPACITY);
            }
            return;
        }
        if (closedIcon != null) {
            closedIcon.setOpacity(opening ? VISIBLE_OPACITY : HIDDEN_OPACITY);
        }
        openIcon.setOpacity(opening ? HIDDEN_OPACITY : VISIBLE_OPACITY);
    }

    private void playOpenCloseAnimation(boolean opening, Duration duration) {
        if (opening) {
            showActionNodes();
        } else {
            actionsLayer.setMouseTransparent(true);
            for (ActionCell cell : cells) {
                cell.updateFocusTraversable(false);
            }
        }
        Timeline timeline = buildOpenCloseTimeline(opening, duration);
        openCloseAnim = timeline;
        timeline.setOnFinished(event -> {
            if (openCloseAnim != timeline) {
                return;
            }
            openCloseAnim = null;
            snapTo(opening);
            fireTerminalEvent(opening);
        });
        timeline.play();
    }

    private Timeline buildOpenCloseTimeline(boolean opening, Duration duration) {
        Timeline timeline = new Timeline();
        List<ActionCell> visibleCells = visibleCells();
        double staggerMillis = staggerMillisOrZero();
        int count = visibleCells.size();
        for (int i = 0; i < count; i++) {
            ActionCell cell = visibleCells.get(i);
            int order = opening ? i : count - i - 1;
            Duration delay = Duration.millis(order * staggerMillis);
            double startOpacity = cell.root.getOpacity();
            double startScaleX = cell.root.getScaleX();
            double startScaleY = cell.root.getScaleY();
            timeline.getKeyFrames().add(new KeyFrame(delay,
                    new KeyValue(cell.root.opacityProperty(), startOpacity, Interpolator.EASE_BOTH),
                    new KeyValue(cell.root.scaleXProperty(), startScaleX, Interpolator.EASE_BOTH),
                    new KeyValue(cell.root.scaleYProperty(), startScaleY, Interpolator.EASE_BOTH)));
            timeline.getKeyFrames().add(new KeyFrame(delay.add(duration),
                    new KeyValue(cell.root.opacityProperty(), opening ? VISIBLE_OPACITY : HIDDEN_OPACITY,
                            Interpolator.EASE_BOTH),
                    new KeyValue(cell.root.scaleXProperty(), opening ? VISIBLE_ACTION_SCALE : HIDDEN_ACTION_SCALE,
                            Interpolator.EASE_BOTH),
                    new KeyValue(cell.root.scaleYProperty(), opening ? VISIBLE_ACTION_SCALE : HIDDEN_ACTION_SCALE,
                            Interpolator.EASE_BOTH)));
        }
        addIconMorphKeyFrames(timeline, opening, duration);
        return timeline;
    }

    private void addIconMorphKeyFrames(Timeline timeline, boolean opening, Duration duration) {
        timeline.getKeyFrames().add(new KeyFrame(duration,
                new KeyValue(iconMorph.rotateProperty(), opening ? OPEN_ICON_ROTATION : CLOSED_ICON_ROTATION,
                        Interpolator.EASE_BOTH)));
        Node closedIcon = getSkinnable().getIcon();
        Node configuredOpenIcon = getSkinnable().getOpenIcon();
        Node openIcon = configuredOpenIcon == closedIcon ? null : configuredOpenIcon;
        if (openIcon == null) {
            if (closedIcon != null) {
                timeline.getKeyFrames().add(new KeyFrame(duration,
                        new KeyValue(closedIcon.opacityProperty(), VISIBLE_OPACITY, Interpolator.EASE_BOTH)));
            }
            return;
        }
        List<KeyValue> keyValues = new ArrayList<>();
        if (closedIcon != null) {
            keyValues.add(new KeyValue(closedIcon.opacityProperty(),
                    opening ? HIDDEN_OPACITY : VISIBLE_OPACITY, Interpolator.EASE_BOTH));
        }
        keyValues.add(new KeyValue(openIcon.opacityProperty(),
                opening ? VISIBLE_OPACITY : HIDDEN_OPACITY, Interpolator.EASE_BOTH));
        timeline.getKeyFrames().add(new KeyFrame(duration, keyValues.toArray(KeyValue[]::new)));
    }

    private List<ActionCell> visibleCells() {
        return cells.stream()
                .filter(ActionCell::isActionVisible)
                .toList();
    }

    private double staggerMillisOrZero() {
        Duration staggerDelay = getSkinnable().getStaggerDelay();
        if (!PageTransitionEngine.isPositiveFinite(staggerDelay)) {
            return 0.0;
        }
        return staggerDelay.toMillis();
    }

    private void showActionNodes() {
        RXSpeedDial control = getSkinnable();
        actionsLayer.setVisible(true);
        actionsLayer.setMouseTransparent(false);
        for (ActionCell cell : cells) {
            cell.updateRootVisibility(true);
            if (control.isShowing()) {
                cell.updateLabelVisibility();
            }
        }
    }

    private void fireTerminalEvent(boolean showing) {
        if (!terminalEventPending) {
            return;
        }
        terminalEventPending = false;
        RXSpeedDial control = getSkinnable();
        if (showing) {
            control.fireEvent(new RXSpeedDialEvent(control, control, RXSpeedDialEvent.SHOWN, null));
        } else {
            RXSpeedDial.CloseReason reason =
                    pendingHiddenReason == null ? RXSpeedDial.CloseReason.TOGGLE : pendingHiddenReason;
            pendingHiddenReason = null;
            control.fireEvent(new RXSpeedDialEvent(control, control, RXSpeedDialEvent.HIDDEN, reason));
        }
    }

    private void onHiding(RXSpeedDialEvent event) {
        pendingHiddenReason = event.getCloseReason();
        if (openTriggerOrDefault() == RXSpeedDial.OpenTrigger.HOVER
                && event.getCloseReason() != RXSpeedDial.CloseReason.MOUSE_EXIT) {
            hoverOpenSuppressed = true;
        }
    }

    private void stopOpenCloseAnimation() {
        if (openCloseAnim != null) {
            openCloseAnim.stop();
            openCloseAnim = null;
        }
    }

    private void settleAnimationToCurrentState() {
        stopOpenCloseAnimation();
        boolean showing = getSkinnable().isShowing();
        snapTo(showing);
        fireTerminalEvent(showing);
    }

    private void handleMouseEntered(MouseEvent event) {
        if (openTriggerOrDefault() != RXSpeedDial.OpenTrigger.HOVER) {
            return;
        }
        if (hoverOpenSuppressed) {
            clearHoverOpenSuppressionLater();
            return;
        }
        getSkinnable().open();
    }

    private void handleMouseExited(MouseEvent event) {
        if (openTriggerOrDefault() == RXSpeedDial.OpenTrigger.HOVER) {
            clearHoverOpenSuppressionLater();
            if (getSkinnable().isShowing() && !isFocusWithin()) {
                getSkinnable().close(RXSpeedDial.CloseReason.MOUSE_EXIT);
            }
        }
    }

    private void handleFocusChanged() {
        Platform.runLater(this::settleFocusChanged);
    }

    private void settleFocusChanged() {
        if (disposed) {
            return;
        }
        if (getSkinnable().isShowing()) {
            updateCellLabelVisibility();
        }
        RXSpeedDial control = getSkinnable();
        if (!isFocusWithin()) {
            hoverOpenSuppressed = false;
        }
        if (openTriggerOrDefault() == RXSpeedDial.OpenTrigger.HOVER
                && isFocusWithin()
                && !hoverOpenSuppressed) {
            control.open();
            return;
        }
        Scene scene = control.getScene();
        boolean hasExternalFocusOwner = scene != null && scene.getFocusOwner() != null;
        if (!isFocusWithin() && hasExternalFocusOwner && control.isShowing() && control.isCloseOnFocusLoss()) {
            control.close(RXSpeedDial.CloseReason.FOCUS_LOST);
        }
    }

    private void clearHoverOpenSuppressionLater() {
        if (!hoverOpenSuppressed) {
            return;
        }
        // Keep same-pulse focus and synthetic mouse events from reopening after an action close.
        Platform.runLater(() -> Platform.runLater(() -> {
            if (!disposed) {
                hoverOpenSuppressed = false;
            }
        }));
    }

    private void updateCellLabelVisibility() {
        for (ActionCell cell : cells) {
            cell.updateLabelVisibility();
        }
        getSkinnable().requestLayout();
    }

    private boolean isFocusWithin() {
        Scene scene = getSkinnable().getScene();
        Node focusOwner = scene == null ? null : scene.getFocusOwner();
        return focusOwner != null && isDescendantOf(focusOwner, getSkinnable());
    }

    private void handleKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE && getSkinnable().isShowing()) {
            getSkinnable().close(RXSpeedDial.CloseReason.ESCAPE);
            if (!getSkinnable().isShowing()) {
                mainFab.requestFocus();
            }
            event.consume();
        }
    }

    private void handleNavigationKeyPressed(KeyEvent event) {
        if (!getSkinnable().isShowing()) {
            return;
        }
        KeyCode code = event.getCode();
        if (code == KeyCode.HOME) {
            focusBoundaryAction(true, event);
            return;
        }
        if (code == KeyCode.END) {
            focusBoundaryAction(false, event);
            return;
        }
        if (isForwardKey(code)) {
            focusRelativeAction(1, event);
        } else if (isBackwardKey(code)) {
            focusRelativeAction(-1, event);
        }
    }

    private boolean isForwardKey(KeyCode code) {
        return switch (directionOrDefault()) {
            case UP -> code == KeyCode.UP;
            case DOWN -> code == KeyCode.DOWN;
            case LEFT -> code == KeyCode.LEFT;
            case RIGHT -> code == KeyCode.RIGHT;
        };
    }

    private boolean isBackwardKey(KeyCode code) {
        return switch (directionOrDefault()) {
            case UP -> code == KeyCode.DOWN;
            case DOWN -> code == KeyCode.UP;
            case LEFT -> code == KeyCode.RIGHT;
            case RIGHT -> code == KeyCode.LEFT;
        };
    }

    private void focusBoundaryAction(boolean first, KeyEvent event) {
        List<ActionCell> navigableCells = navigableCells();
        if (navigableCells.isEmpty() || !isEventFromDial(event)) {
            return;
        }
        ActionCell target = first ? navigableCells.get(0) : navigableCells.get(navigableCells.size() - 1);
        target.fab.requestFocus();
        event.consume();
    }

    private void focusRelativeAction(int delta, KeyEvent event) {
        List<ActionCell> navigableCells = navigableCells();
        if (navigableCells.isEmpty() || !isEventFromDial(event)) {
            return;
        }
        int currentIndex = focusedActionIndex(navigableCells, event);
        if (currentIndex < 0) {
            if (delta > 0 && isMainNavigationOrigin(event)) {
                navigableCells.get(0).fab.requestFocus();
                event.consume();
            }
            return;
        }
        int targetIndex = currentIndex + delta;
        if (targetIndex >= 0 && targetIndex < navigableCells.size()) {
            navigableCells.get(targetIndex).fab.requestFocus();
        } else if (targetIndex < 0) {
            mainFab.requestFocus();
        }
        event.consume();
    }

    private int focusedActionIndex(List<ActionCell> navigableCells, KeyEvent event) {
        Scene scene = getSkinnable().getScene();
        Node focusOwner = scene == null ? null : scene.getFocusOwner();
        Node eventTarget = event.getTarget() instanceof Node node ? node : null;
        for (int i = 0; i < navigableCells.size(); i++) {
            RXFloatingActionButton fab = navigableCells.get(i).fab;
            if (fab == focusOwner || eventTarget != null && isDescendantOf(eventTarget, fab)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isMainNavigationOrigin(KeyEvent event) {
        Scene scene = getSkinnable().getScene();
        Node focusOwner = scene == null ? null : scene.getFocusOwner();
        Node eventTarget = event.getTarget() instanceof Node node ? node : null;
        return focusOwner == mainFab
                || eventTarget != null && (eventTarget == getSkinnable() || isDescendantOf(eventTarget, mainFab));
    }

    private boolean isEventFromDial(KeyEvent event) {
        Object target = event.getTarget();
        if (!(target instanceof Node node)) {
            return false;
        }
        if (isDescendantOf(node, mainFab)) {
            return true;
        }
        for (ActionCell cell : cells) {
            if (isDescendantOf(node, cell.root)) {
                return true;
            }
        }
        return node == getSkinnable();
    }

    private List<ActionCell> navigableCells() {
        return cells.stream()
                .filter(ActionCell::isNavigable)
                .toList();
    }

    private void onSceneChanged() {
        detachSceneObservers();
        Scene scene = getSkinnable().getScene();
        if (scene != null) {
            scene.addEventFilter(MouseEvent.MOUSE_PRESSED, sceneMousePressedFilter);
            scene.focusOwnerProperty().addListener(sceneFocusOwnerListener);
            observedScene = scene;
            handleFocusChanged();
        } else {
            settleAnimationToCurrentState();
        }
    }

    private void detachSceneObservers() {
        if (observedScene != null) {
            observedScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, sceneMousePressedFilter);
            observedScene.focusOwnerProperty().removeListener(sceneFocusOwnerListener);
            observedScene = null;
        }
    }

    private void handleSceneMousePressed(MouseEvent event) {
        RXSpeedDial control = getSkinnable();
        if (control.isShowing() && control.isCloseOnClickOutside() && !isInsideDial(event)) {
            control.close(RXSpeedDial.CloseReason.CLICK_OUTSIDE);
        }
    }

    private boolean isInsideDial(MouseEvent event) {
        Object target = event.getTarget();
        if (target instanceof Node node && isDescendantOf(node, getSkinnable())) {
            return true;
        }
        Point2D local = getSkinnable().sceneToLocal(event.getSceneX(), event.getSceneY());
        return getSkinnable().contains(local);
    }

    private boolean isDescendantOf(Node node, Node ancestor) {
        Node current = node;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    @Override
    protected void disposeSkin() {
        disposed = true;
        settleAnimationToCurrentState();
        detachSceneObservers();
        clearCells();
        iconMorph.getChildren().clear();
        mainFab.setGraphic(null);
        getChildren().removeAll(actionsLayer, mainFab);
    }

    // ==================== Action Cell ====================

    private final class ActionCell {

        private final RXSpeedDialAction action;
        private final Pane root = new Pane();
        private final RXFloatingActionButton fab = new RXFloatingActionButton();
        private final Label label = new Label();
        private final SkinDisposer cellDisposer = new SkinDisposer();

        private ActionCell(RXSpeedDialAction action) {
            this.action = Objects.requireNonNull(action, "action");
            root.getStyleClass().add("action");
            root.setManaged(false);
            fab.setSize(RXFloatingActionButton.Size.SMALL);
            fab.setFocusTraversable(false);
            label.setMouseTransparent(true);

            cellDisposer.registerBinding(fab.graphicProperty(), action.graphicProperty());
            cellDisposer.registerBinding(fab.disableProperty(), action.disableProperty());
            cellDisposer.registerBinding(fab.accessibleTextProperty(), action.textProperty());
            cellDisposer.registerBinding(label.textProperty(), action.textProperty());
            cellDisposer.registerListener(action.visibleProperty(), this::onVisibleChanged);
            cellDisposer.registerListener(action.disableProperty(), this::onDisableChanged);
            cellDisposer.registerListener(action.textProperty(), this::onTextChanged);
            cellDisposer.registerListener(fab.disabledProperty(), this::onDisableChanged);
            cellDisposer.registerListener(fab.focusedProperty(), this::onLabelSignalChanged);
            cellDisposer.registerListener(root.hoverProperty(), this::onLabelSignalChanged);
            cellDisposer.registerEventHandler(fab, ActionEvent.ACTION, this::handleAction);

            root.getChildren().setAll(fab);
            updateLabelMode();
            updateRootVisibility(getSkinnable().isShowing());
            snapTo(getSkinnable().isShowing());
        }

        private boolean isVisible() {
            return root.isVisible();
        }

        private boolean isActionVisible() {
            return action.isVisible();
        }

        private boolean isNavigable() {
            return getSkinnable().isShowing() && action.isVisible() && !fab.isDisabled();
        }

        private double axisExtent(RXSpeedDial.Direction direction) {
            CellMetrics metrics = measure(direction);
            if (direction == RXSpeedDial.Direction.LEFT || direction == RXSpeedDial.Direction.RIGHT) {
                return metrics.width();
            }
            return metrics.height();
        }

        private void updateRootVisibility(boolean showing) {
            root.setVisible(showing && action.isVisible());
            updateFocusTraversable(showing);
        }

        private void updateFocusTraversable(boolean showing) {
            fab.setFocusTraversable(showing && action.isVisible() && !fab.isDisabled());
        }

        private void snapTo(boolean showing) {
            boolean visible = showing && action.isVisible();
            root.setOpacity(visible ? VISIBLE_OPACITY : HIDDEN_OPACITY);
            root.setScaleX(visible ? VISIBLE_ACTION_SCALE : HIDDEN_ACTION_SCALE);
            root.setScaleY(visible ? VISIBLE_ACTION_SCALE : HIDDEN_ACTION_SCALE);
            updateLabelVisibility();
        }

        private void layoutAt(double centerX, double centerY, RXSpeedDial.Direction direction) {
            CellMetrics metrics = measure(direction);
            double fabWidth = metrics.fabWidth();
            double fabHeight = metrics.fabHeight();
            double labelWidth = metrics.labelWidth();
            double labelHeight = metrics.labelHeight();
            boolean horizontal = direction == RXSpeedDial.Direction.LEFT || direction == RXSpeedDial.Direction.RIGHT;
            RXSpeedDial.LabelPlacement placement = effectiveLabelPlacement(direction);
            double gap = labelGap();
            boolean showLabel = label.isVisible();
            double rootWidth = metrics.width();
            double rootHeight = metrics.height();
            double fabX;
            double labelX;
            double fabY;
            double labelY;
            if (!showLabel) {
                fabX = 0.0;
                labelX = 0.0;
                fabY = 0.0;
                labelY = 0.0;
            } else if (horizontal) {
                fabX = snapPositionX((rootWidth - fabWidth) / 2.0);
                labelX = snapPositionX((rootWidth - labelWidth) / 2.0);
                if (placement == RXSpeedDial.LabelPlacement.START) {
                    labelY = 0.0;
                    fabY = labelHeight + gap;
                } else {
                    fabY = 0.0;
                    labelY = fabHeight + gap;
                }
            } else if (placement == RXSpeedDial.LabelPlacement.END) {
                fabX = 0.0;
                labelX = fabWidth + gap;
                fabY = snapPositionY((rootHeight - fabHeight) / 2.0);
                labelY = snapPositionY((rootHeight - labelHeight) / 2.0);
            } else {
                labelX = 0.0;
                fabX = labelWidth + gap;
                fabY = snapPositionY((rootHeight - fabHeight) / 2.0);
                labelY = snapPositionY((rootHeight - labelHeight) / 2.0);
            }

            root.resizeRelocate(snapPositionX(centerX - fabX - fabWidth / 2.0),
                    snapPositionY(centerY - fabY - fabHeight / 2.0),
                    rootWidth, rootHeight);
            fab.resizeRelocate(fabX, fabY, fabWidth, fabHeight);
            label.resizeRelocate(labelX, labelY, labelWidth, labelHeight);
        }

        private CellMetrics measure(RXSpeedDial.Direction direction) {
            double fabWidth = snapSizeX(fab.prefWidth(-1));
            double fabHeight = snapSizeY(fab.prefHeight(-1));
            boolean showLabel = label.isVisible();
            double labelWidth = showLabel ? snapSizeX(label.prefWidth(-1)) : 0.0;
            double labelHeight = showLabel ? snapSizeY(label.prefHeight(-1)) : 0.0;
            boolean horizontal = direction == RXSpeedDial.Direction.LEFT || direction == RXSpeedDial.Direction.RIGHT;
            double gap = showLabel ? labelGap() : 0.0;
            double width = horizontal ? Math.max(fabWidth, labelWidth) : fabWidth + gap + labelWidth;
            double height = horizontal ? fabHeight + gap + labelHeight : Math.max(fabHeight, labelHeight);
            return new CellMetrics(fabWidth, fabHeight, labelWidth, labelHeight, width, height);
        }

        private void onTextChanged() {
            updateLabelVisibility();
            getSkinnable().requestLayout();
        }

        private void onVisibleChanged() {
            updateRootVisibility(getSkinnable().isShowing());
            snapTo(getSkinnable().isShowing());
            getSkinnable().requestLayout();
        }

        private void onDisableChanged() {
            updateFocusTraversable(getSkinnable().isShowing());
        }

        private void onLabelSignalChanged() {
            updateLabelVisibility();
            getSkinnable().requestLayout();
        }

        private void updateLabelMode() {
            boolean includeLabel = labelModeOrDefault() != RXSpeedDial.LabelMode.NONE;
            if (includeLabel && !root.getChildren().contains(label)) {
                root.getChildren().add(0, label);
            } else if (!includeLabel) {
                root.getChildren().remove(label);
            }
            updateLabelVisibility();
        }

        private void updateLabelVisibility() {
            String text = action.getText();
            RXSpeedDial.LabelMode mode = labelModeOrDefault();
            boolean hasText = text != null && !text.isBlank();
            boolean active = mode == RXSpeedDial.LabelMode.PERSISTENT
                    || mode == RXSpeedDial.LabelMode.HOVER && (root.isHover() || isFocusOwnerWithinFab());
            label.setVisible(hasText && root.getChildren().contains(label)
                    && getSkinnable().isShowing() && action.isVisible() && active);
        }

        private boolean isFocusOwnerWithinFab() {
            Scene scene = getSkinnable().getScene();
            Node focusOwner = scene == null ? null : scene.getFocusOwner();
            return focusOwner != null && isDescendantOf(focusOwner, fab);
        }

        private void handleAction(ActionEvent event) {
            EventHandler<ActionEvent> handler = action.getOnAction();
            try {
                if (handler != null) {
                    handler.handle(event);
                }
            } finally {
                if (action.isCloseOnAction()) {
                    getSkinnable().close(RXSpeedDial.CloseReason.ACTION);
                }
            }
        }

        private void dispose() {
            cellDisposer.dispose();
            fab.setGraphic(null);
            fab.setAccessibleText(null);
            label.setText(null);
            root.getChildren().clear();
        }
    }

    private final class MainFab extends RXFloatingActionButton {

        @Override
        public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
            if (attribute == AccessibleAttribute.EXPANDED) {
                return getSkinnable().isShowing();
            }
            return super.queryAccessibleAttribute(attribute, parameters);
        }
    }

    private record CellMetrics(double fabWidth, double fabHeight, double labelWidth, double labelHeight,
                               double width, double height) {
    }
}
