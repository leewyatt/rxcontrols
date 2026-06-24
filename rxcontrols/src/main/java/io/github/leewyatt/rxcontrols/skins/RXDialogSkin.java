package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXDialog;
import io.github.leewyatt.rxcontrols.enums.CloseReason;
import io.github.leewyatt.rxcontrols.enums.RXDialogActionsLayout;
import io.github.leewyatt.rxcontrols.enums.RXDialogTransition;
import io.github.leewyatt.rxcontrols.event.RXDialogEvent;
import io.github.leewyatt.rxcontrols.layout.RXBox;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventTarget;
import javafx.event.EventType;
import javafx.geometry.HPos;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Skin for {@link RXDialog}. Stacks a full-bleed scrim under a content-sized,
 * centered card; the dialog control itself fills its {@code RXDialogLayer}, so the
 * scrim covers the whole scene and the card floats at the centre.
 *
 * <p>A single scalar {@link #progress} in {@code [0, 1]} drives every transition
 * through {@link #applyPose(double)} — {@code CENTER} scales + fades, the four
 * {@code SLIDE_*} variants translate by the card's own size + fade. The animation
 * uses one superseded {@link Timeline} (stop-and-replace) like
 * {@code RXDrawerPaneSkin}, so a mid-flight reversal resumes smoothly from the
 * current progress. The card is never clipped (that would crop its elevation
 * shadow and trip the Effect-before-Clip pitfall); its rounded corners come from
 * the background radius alone.</p>
 *
 * <p>The control owns the close gate and lifecycle bookkeeping. This skin fires
 * {@code SHOWING} / {@code SHOWN} at the show transition's start / end and calls
 * {@link RXDialog#hideCompleted()} when the hide transition finishes; the control
 * fires {@code CLOSE_REQUEST} / {@code HIDING} / {@code HIDDEN} and delivers the
 * result.</p>
 */
public class RXDialogSkin extends RXSkinBase<RXDialog<?>> {

    // Card scale at progress 0 for the CENTER transition (scales up to 1.0 at open).
    private static final double CLOSED_SCALE = 0.8;

    // Height of the top "title band" within which a press starts a move drag (decision
    // 6.2; real-device tunable). Presses on interactive header nodes (close X, focusable
    // controls) inside the band are excluded so they keep working.
    private static final double DRAG_BAND_HEIGHT = 48.0;

    // Width (in card-local px) of the border band that begins an edge / corner resize.
    private static final double RESIZE_EDGE = 8.0;
    // Resize-edge bitmask (combined into corners).
    private static final int EDGE_WEST = 1;
    private static final int EDGE_EAST = 1 << 1;
    private static final int EDGE_NORTH = 1 << 2;
    private static final int EDGE_SOUTH = 1 << 3;

    private final Region overlay = new Region();
    // The visible card. A VBox so it lays out the content (vgrow) above the action bar directly,
    // with the background / shadow / size on the same node — no extra wrapper.
    private final VBox dialogCard = new VBox();
    private final StackPane contentWrapper = new StackPane();
    // The action bar (a ButtonBar for PLATFORM, else an RXBox), rebuilt when buttonTypes
    // or actionsLayout change; null when there are no buttons.
    private Region actionsNode;

    private Timeline animation;
    // Guard lifecycle firing: SHOWN fires only when a show transition is in flight,
    // hideCompleted only when a hide transition is in flight.
    private boolean openInFlight;
    private boolean closeInFlight;
    // Focus owner captured when a modal dialog opened, restored when it hides.
    private Node prevFocusOwner;

    // True while no other dialog is stacked above this one in the shared layer. Only the
    // top-most dialog paints its scrim, so N stacked modal dialogs show one merged scrim
    // rather than N composited ones.
    private boolean topMost = true;
    private Parent observedParent;
    private final ListChangeListener<Node> stackListener = change -> refreshTopMost();
    // True for a transition that opens / closes on top of an already-scrimmed modal stack: the
    // merged scrim is continuous, so this dialog's overlay holds full opacity instead of fading
    // with the card — otherwise the swap between the lower (hidden) and this (fading) overlay
    // flashes the scene through. Recomputed at each transition start.
    private boolean scrimSteady;

    // Single scalar [0,1]: 0 = fully closed pose, 1 = fully open pose. Its change
    // re-applies the pose, so the Timeline only has to tween this one value.
    private final DoubleProperty progress = new SimpleDoubleProperty(this, "progress", 0.0) {
        @Override
        protected void invalidated() {
            applyPose(get());
        }
    };

    // ==================== Drag / resize state ====================

    // Persistent, raw (un-clamped) layout offset added to the centered card every layout
    // pass, then clamped into the scene for that frame only (the raw value is kept so a
    // window that shrinks then grows restores the dragged position). Reset to 0 only when
    // the dialog has fully hidden (finalizeClose / resetCardGeometry).
    private double dragOffsetX;
    private double dragOffsetY;

    // User-chosen card size from a resize gesture; null = automatic (content-driven). Reset
    // to null only when the dialog has fully hidden (resetCardGeometry).
    private Double userWidth;
    private Double userHeight;

    // Available content area cached from the last layoutChildren pass, so a live resize can
    // clamp against the same bounds layoutChildren uses. MAX_VALUE until the first layout.
    private double availContentWidth = Double.MAX_VALUE;
    private double availContentHeight = Double.MAX_VALUE;

    // True while a press-drag-release move / resize gesture is active.
    private boolean dragActive;
    private boolean resizeActive;
    // Which edges the active resize is dragging (corners set two).
    private boolean resizeWest;
    private boolean resizeEast;
    private boolean resizeNorth;
    private boolean resizeSouth;

    // Gesture anchor captured on press (scene coords + the drag offsets and, for resize, the
    // card size at press time), shared by drag and resize since only one runs at a time.
    private double gestureStartSceneX;
    private double gestureStartSceneY;
    private double gestureStartOffsetX;
    private double gestureStartOffsetY;
    private double gestureStartW;
    private double gestureStartH;

    /**
     * Creates the skin, assembles the scrim and card layers, and registers all
     * listeners with the disposer.
     *
     * @param control the dialog this skin is attached to
     */
    public RXDialogSkin(RXDialog<?> control) {
        super(control);

        overlay.getStyleClass().add("overlay");
        dialogCard.getStyleClass().add("dialog-card");
        contentWrapper.getStyleClass().add("content-wrapper");

        // The card catches clicks across its whole bounds so a click on it never
        // falls through to the scrim (which would request a close).
        dialogCard.setPickOnBounds(true);
        // Last-resort focus target when modal and nothing inside is focusable.
        dialogCard.setFocusTraversable(true);

        // The card lays out the content (filling the free space) above the action bar; the
        // action bar is appended after contentWrapper by rebuildActions when there are buttons.
        VBox.setVgrow(contentWrapper, Priority.ALWAYS);
        dialogCard.getChildren().setAll(contentWrapper);

        getChildren().setAll(overlay, dialogCard);

        updateContent();
        rebuildActions();
        snapToShowing();

        // Scrim click -> close (only while modal + open; the scrim is pickable only then).
        disposer.registerEventHandler(overlay, MouseEvent.MOUSE_CLICKED, event -> {
            RXDialog<?> dialog = getSkinnable();
            if (dialog.isModal() && dialog.isCloseOnScrimClick() && dialog.isShowing()) {
                dialog.requestClose(null, CloseReason.SCRIM);
                event.consume();
            }
        });

        // ESC anywhere in the dialog subtree requests a close. A capturing filter
        // catches it before a focused descendant consumes it; with focus trapped in
        // the top-most modal dialog, only that dialog sees it.
        disposer.registerEventFilter(control, KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && getSkinnable().isCloseOnEsc()
                    && getSkinnable().isShowing()) {
                getSkinnable().requestClose(null, CloseReason.ESC);
                event.consume();
            }
        });

        // Focus trap: while modal + open, Tab / Shift+Tab cycle within the card.
        disposer.registerEventFilter(control, KeyEvent.KEY_PRESSED, this::handleTabTrap);

        disposer.registerListener(control.showingProperty(),
                (obs, wasShowing, isShowing) -> handleShowingChanged(isShowing));
        disposer.registerListener(control.contentProperty(), this::updateContent);
        disposer.registerListener(control.getButtonTypes(), this::rebuildActions);
        disposer.registerListener(control.actionsLayoutProperty(), this::rebuildActions);
        disposer.registerListener(control.modalProperty(), this::onModalChanged);
        // Track stacking position so only the top-most dialog shows its scrim. The dialog's
        // parent is the shared RXDialogLayer (a StackPane) whose last child is the top-most
        // dialog; observe the parent and its child list to follow that position.
        disposer.registerListener(control.parentProperty(),
                (obs, oldParent, newParent) -> onParentChanged(newParent));
        disposer.registerDisposeTask(this::detachStackListener);
        onParentChanged(control.getParent());
        // transition / animated / animationDuration / animationInterpolator are read
        // at play time, so a change applies to the next transition.
        disposer.registerListener(control.sceneProperty(), (obs, oldScene, newScene) -> onSceneChanged(newScene));

        // Move / resize gestures on the card. Handlers (not filters) so they run on the
        // bubbling phase after interactive children; a gesture only starts (and consumes)
        // over a non-interactive part of the card.
        disposer.registerEventHandler(dialogCard, MouseEvent.MOUSE_MOVED, this::onCardMouseMoved);
        disposer.registerEventHandler(dialogCard, MouseEvent.MOUSE_PRESSED, this::onCardMousePressed);
        disposer.registerEventHandler(dialogCard, MouseEvent.MOUSE_DRAGGED, this::onCardMouseDragged);
        disposer.registerEventHandler(dialogCard, MouseEvent.MOUSE_RELEASED, this::onCardMouseReleased);
        disposer.registerEventHandler(dialogCard, MouseEvent.MOUSE_EXITED, this::onCardMouseExited);
        disposer.registerListener(control.enableDraggableProperty(), this::onGestureEnablementChanged);
        disposer.registerListener(control.enableResizableProperty(), this::onGestureEnablementChanged);

        // Drive the card's own min / pref / max size from the control's card-bounds properties,
        // so a resize is clamped to them and the card opens at the pref — the existing
        // boundedSize clamp and layoutInArea both read these card size properties, so binding
        // keeps them consistent. (These are the card's bounds, not the control's Region min/max,
        // which must stay unbounded so the control fills the scene to back the scrim.)
        disposer.registerBinding(dialogCard.minWidthProperty(), control.cardMinWidthProperty());
        disposer.registerBinding(dialogCard.prefWidthProperty(), control.cardPrefWidthProperty());
        disposer.registerBinding(dialogCard.maxWidthProperty(), control.cardMaxWidthProperty());
        disposer.registerBinding(dialogCard.minHeightProperty(), control.cardMinHeightProperty());
        disposer.registerBinding(dialogCard.prefHeightProperty(), control.cardPrefHeightProperty());
        disposer.registerBinding(dialogCard.maxHeightProperty(), control.cardMaxHeightProperty());
    }

    // ==================== Slots ====================

    private void updateContent() {
        Node content = getSkinnable().getContent();
        if (content == null) {
            contentWrapper.getChildren().clear();
        } else {
            contentWrapper.getChildren().setAll(content);
        }
    }

    private void rebuildActions() {
        if (actionsNode != null) {
            dialogCard.getChildren().remove(actionsNode);
            actionsNode = null;
        }
        List<RXButton> buttons = new ArrayList<>();
        for (ButtonType buttonType : getSkinnable().getButtonTypes()) {
            if (buttonType != null) {
                buttons.add(createActionButton(buttonType));
            }
        }
        if (buttons.isEmpty()) {
            return;
        }
        actionsNode = buildActionsContainer(buttons);
        actionsNode.getStyleClass().add("actions");
        dialogCard.getChildren().add(actionsNode);
    }

    // PLATFORM keeps the native ButtonBar (OS order, trailing-aligned). BOX (default) is a
    // plain RXBox row in buttonTypes order, fully styled by CSS on .actions (alignment /
    // spacing / orientation); there is no per-layout geometry code on purpose.
    private Region buildActionsContainer(List<RXButton> buttons) {
        if (actionsLayoutOrDefault() == RXDialogActionsLayout.PLATFORM) {
            ButtonBar bar = new ButtonBar();
            bar.getButtons().setAll(buttons);
            return bar;
        }
        RXBox row = new RXBox();
        row.getChildren().setAll(buttons);
        return row;
    }

    private RXDialogActionsLayout actionsLayoutOrDefault() {
        RXDialogActionsLayout value = getSkinnable().getActionsLayout();
        return value == null ? RXDialog.DEFAULT_ACTIONS_LAYOUT : value;
    }

    private RXButton createActionButton(ButtonType buttonType) {
        RXButton button = new RXButton(buttonType.getText());
        ButtonData data = buttonType.getButtonData();
        if (data != null) {
            ButtonBar.setButtonData(button, data);
            // Default button drives ENTER; the cancel/ESC path is owned by the
            // dialog's closeOnEsc handler, so the cancel flag is intentionally not set
            // here (it would double-fire on ESC).
            button.setDefaultButton(data.isDefaultButton());
        }
        button.setOnAction((ActionEvent event) ->
                getSkinnable().requestClose(buttonType, CloseReason.ACTION_BUTTON));
        return button;
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        layoutInArea(overlay, contentX, contentY, contentWidth, contentHeight, 0, HPos.LEFT, VPos.TOP);

        // Remember the available area so a live resize clamps against the same bounds.
        availContentWidth = contentWidth;
        availContentHeight = contentHeight;

        // A user resize size wins over the content's preferred size; both are clamped the
        // same way (clampCardWidth/Height), so resize feeds the identical math layout uses.
        double targetW = userWidth != null ? userWidth : dialogCard.prefWidth(-1);
        double cardW = clampCardWidth(targetW, contentWidth);
        double targetH = userHeight != null ? userHeight : dialogCard.prefHeight(cardW);
        double cardH = clampCardHeight(targetH, cardW, contentHeight);
        double cardX = contentX + (contentWidth - cardW) / 2.0 + dragOffsetX;
        double cardY = contentY + (contentHeight - cardH) / 2.0 + dragOffsetY;
        // Clamp the (possibly dragged) card fully inside the content area for this frame
        // only; dragOffset itself stays raw. maxX >= contentX because cardW <= contentWidth,
        // so the two-step max/min never inverts (plain math, not RXMath.clamp, to stay
        // throw-free even if a future change makes the card wider than the content).
        double maxX = contentX + contentWidth - cardW;
        double maxY = contentY + contentHeight - cardH;
        cardX = Math.max(contentX, Math.min(maxX, cardX));
        cardY = Math.max(contentY, Math.min(maxY, cardY));
        layoutInArea(dialogCard, cardX, cardY, cardW, cardH, 0, HPos.CENTER, VPos.CENTER);
    }

    // Mirrors JFX Region.boundedSize exactly so the resize clamp and layoutInArea (which uses
    // Region's own boundedSize) can never diverge: min wins when min > max — including a
    // degenerate 0 max, which Region.maxWidth coerces a negative / NaN card bound down to.
    // min / pref / max here are the card's already-resolved (sentinel-free) sizes read via
    // minWidth(-1) / prefWidth(-1) / maxWidth(-1).
    private static double boundedSize(double min, double pref, double max) {
        double atLeastMin = Math.max(pref, min);
        double cap = Math.max(min, max);
        return Math.min(atLeastMin, cap);
    }

    // The card's width: clamp the target (user size or pref) into the card's [min, max] via
    // boundedSize, then cap at the available content width. boundedSize handles min > max, so
    // RXMath.clamp (which throws) is never used and a tiny scene can't break it (spec §3.4 #1).
    private double clampCardWidth(double targetWidth, double availWidth) {
        return Math.min(availWidth,
                boundedSize(dialogCard.minWidth(-1), targetWidth, dialogCard.maxWidth(-1)));
    }

    // The card's height for a given width (the card is height-for-width: a wrapped body
    // reflows as the width changes), capped at the available content height.
    private double clampCardHeight(double targetHeight, double width, double availHeight) {
        return Math.min(availHeight,
                boundedSize(dialogCard.minHeight(width), targetHeight, dialogCard.maxHeight(width)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + dialogCard.prefWidth(-1) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + dialogCard.prefHeight(-1) + bottomInset;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Unbounded so the dialog's {@code RXDialogLayer} (a {@code StackPane})
     * stretches it to fill the layer, letting the scrim cover the whole scene.</p>
     */
    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    // ==================== Show / hide transition ====================

    private void handleShowingChanged(boolean showing) {
        // A starting transition disables gestures (gesturesEnabled() is false while
        // animating); cancel any in-flight one so its drag events stop fighting the
        // animation. Geometry is preserved here and reset only in finalizeClose.
        cancelGestures();
        // A lower modal dialog already paints the scrim => keep this overlay steady (full) so
        // the merged scrim never flashes the scene through during the stacked open / close.
        scrimSteady = hasModalDialogBelow();
        if (showing) {
            openInFlight = true;
            closeInFlight = false;
            dialogCard.setVisible(true);
            dialogCard.setMouseTransparent(false);
            prepareScrimForOpen();
            moveFocusIntoDialog();
            fireLifecycle(RXDialogEvent.SHOWING);
            playOpen();
        } else {
            closeInFlight = true;
            openInFlight = false;
            playClose();
        }
    }

    private void fireLifecycle(EventType<RXDialogEvent> type) {
        getSkinnable().fireEvent(new RXDialogEvent(type, getSkinnable()));
    }

    private void playOpen() {
        stopAnimation();
        if (!animationsActive()) {
            finalizeOpen();
            return;
        }
        Timeline timeline = buildTimeline(1.0, this::finalizeOpen);
        animation = timeline;
        timeline.play();
    }

    private void playClose() {
        stopAnimation();
        if (!animationsActive()) {
            finalizeClose();
            return;
        }
        Timeline timeline = buildTimeline(0.0, this::finalizeClose);
        animation = timeline;
        timeline.play();
    }

    private Timeline buildTimeline(double target, Runnable onFinish) {
        Timeline timeline = new Timeline(new KeyFrame(getSkinnable().getAnimationDuration(),
                new KeyValue(progress, target, interpolatorOrDefault())));
        timeline.setOnFinished(event -> {
            if (animation == timeline) {
                animation = null;
            }
            onFinish.run();
        });
        return timeline;
    }

    private void finalizeOpen() {
        progress.set(1.0);
        applyScrimRest(true);
        if (openInFlight) {
            openInFlight = false;
            fireLifecycle(RXDialogEvent.SHOWN);
        }
    }

    private void finalizeClose() {
        progress.set(0.0);
        applyScrimRest(false);
        dialogCard.setVisible(false);
        dialogCard.setMouseTransparent(true);
        restoreFocus();
        // Reset drag / resize geometry now that the hide animation is done (decision 3.4
        // #5), and before hideCompleted so a re-show chained from a HIDDEN / onResult
        // handler starts centered and auto-sized rather than at the last dragged pose.
        resetCardGeometry();
        if (closeInFlight) {
            closeInFlight = false;
            // Last action: fires HIDDEN, delivers the result, and detaches from the
            // layer (which removes this control from the scene).
            getSkinnable().hideCompleted();
        }
    }

    private void stopAnimation() {
        if (animation != null) {
            animation.stop();
            animation = null;
        }
    }

    // The dialog left its scene by some path other than the normal close gate (the app
    // swapped the scene root, removed an ancestor, or a re-show pulled it off a still-running
    // close). Settle the in-flight transition so a close still fires HIDDEN / delivers the
    // result / restores focus instead of stranding; finalize* is idempotent via the inFlight
    // guards, so the normal detach (which also nulls the scene) is a harmless no-op here.
    private void onSceneChanged(Scene newScene) {
        if (newScene != null) {
            return;
        }
        stopAnimation();
        // Torn out of the scene: drop drag/resize geometry so a later re-attach starts
        // centered / auto-sized. finalizeClose resets too, but finalizeOpen does not, so
        // resetting here also covers the scene-yank-while-showing branch.
        resetCardGeometry();
        if (getSkinnable().isShowing()) {
            finalizeOpen();
        } else {
            finalizeClose();
        }
    }

    // ==================== Pose ====================

    private void applyPose(double rawProgress) {
        double p = clamp01(rawProgress);
        dialogCard.setOpacity(p);
        boolean scrim = scrimActive();
        // A continuous (stacked) scrim stays full; a solo scrim fades in / out with the card.
        overlay.setOpacity(scrim ? (scrimSteady ? 1.0 : p) : 0.0);
        // An inactive scrim (non-modal, or a lower stacked dialog) must not swallow clicks
        // even while a transition still has it visible: opacity alone does not block picking.
        overlay.setMouseTransparent(!scrim);

        RXDialogTransition transition = transitionOrDefault();
        if (transition == RXDialogTransition.CENTER) {
            double scale = CLOSED_SCALE + (1.0 - CLOSED_SCALE) * p;
            dialogCard.setScaleX(scale);
            dialogCard.setScaleY(scale);
            dialogCard.setTranslateX(0.0);
            dialogCard.setTranslateY(0.0);
        } else {
            dialogCard.setScaleX(1.0);
            dialogCard.setScaleY(1.0);
            double offset = (1.0 - p) * slideOffset(transition);
            double tx = 0.0;
            double ty = 0.0;
            switch (transition) {
                case SLIDE_LEFT -> tx = -offset;
                case SLIDE_RIGHT -> tx = offset;
                case SLIDE_TOP -> ty = -offset;
                case SLIDE_BOTTOM -> ty = offset;
                default -> {
                    // CENTER handled above.
                }
            }
            dialogCard.setTranslateX(tx);
            dialogCard.setTranslateY(ty);
        }
    }

    // The card's own size along the slide axis (decision 10: slide by the card, not
    // the whole scene). Uses the laid-out size when available, else the preferred size.
    private double slideOffset(RXDialogTransition transition) {
        boolean horizontal = transition == RXDialogTransition.SLIDE_LEFT
                || transition == RXDialogTransition.SLIDE_RIGHT;
        if (horizontal) {
            return dialogCard.getWidth() > 0 ? dialogCard.getWidth() : dialogCard.prefWidth(-1);
        }
        return dialogCard.getHeight() > 0 ? dialogCard.getHeight() : dialogCard.prefHeight(-1);
    }

    private void snapToShowing() {
        boolean open = getSkinnable().isShowing();
        progress.set(open ? 1.0 : 0.0);
        dialogCard.setVisible(open);
        dialogCard.setMouseTransparent(!open);
        applyScrimRest(open);
    }

    private void prepareScrimForOpen() {
        if (scrimActive()) {
            overlay.setVisible(true);
            overlay.setMouseTransparent(false);
            if (scrimSteady) {
                // Continuous scrim: start at full so the open animation's first frame doesn't
                // flash a gap between the just-hidden lower overlay and this one.
                overlay.setOpacity(1.0);
            }
        } else {
            applyScrimRest(false);
        }
    }

    private void applyScrimRest(boolean open) {
        if (open && scrimActive()) {
            overlay.setVisible(true);
            overlay.setMouseTransparent(false);
            overlay.setOpacity(1.0);
        } else {
            overlay.setVisible(false);
            overlay.setMouseTransparent(true);
            overlay.setOpacity(0.0);
        }
    }

    private void onModalChanged() {
        if (!isAnimationRunning()) {
            applyScrimRest(getSkinnable().isShowing());
        }
    }

    private boolean scrimActive() {
        return getSkinnable().isModal() && topMost;
    }

    // Whether a lower sibling in the shared layer is a modal dialog that is currently showing —
    // i.e. the merged scrim already exists below this dialog, so this one's overlay transitions
    // must be instant (steady) rather than fading, to keep the scrim continuous.
    private boolean hasModalDialogBelow() {
        Parent parent = getSkinnable().getParent();
        if (parent == null) {
            return false;
        }
        List<Node> siblings = parent.getChildrenUnmodifiable();
        int index = siblings.indexOf(getSkinnable());
        for (int i = 0; i < index; i++) {
            if (siblings.get(i) instanceof RXDialog<?> below && below.isModal() && below.isShowing()) {
                return true;
            }
        }
        return false;
    }

    // ==================== Stacking (top-most) ====================

    private void onParentChanged(Parent newParent) {
        detachStackListener();
        if (newParent != null) {
            observedParent = newParent;
            newParent.getChildrenUnmodifiable().addListener(stackListener);
        }
        refreshTopMost();
    }

    private void detachStackListener() {
        if (observedParent != null) {
            observedParent.getChildrenUnmodifiable().removeListener(stackListener);
            observedParent = null;
        }
    }

    private void refreshTopMost() {
        boolean nowTop = computeTopMost();
        if (nowTop == topMost) {
            return;
        }
        topMost = nowTop;
        // Re-settle the scrim for the new stacking position. While an animation owns the
        // scrim, applyPose already re-reads scrimActive() each frame.
        if (!isAnimationRunning()) {
            applyScrimRest(getSkinnable().isShowing());
        }
    }

    private boolean computeTopMost() {
        Parent parent = getSkinnable().getParent();
        if (parent == null) {
            return true;
        }
        List<Node> siblings = parent.getChildrenUnmodifiable();
        return siblings.isEmpty() || siblings.get(siblings.size() - 1) == getSkinnable();
    }

    // ==================== Focus (a11y) ====================

    private void moveFocusIntoDialog() {
        if (!scrimActive()) {
            return;
        }
        Scene scene = getSkinnable().getScene();
        if (scene == null) {
            return;
        }
        prevFocusOwner = scene.getFocusOwner();
        Node target = firstFocusTarget();
        if (target != null) {
            target.requestFocus();
        }
    }

    private void restoreFocus() {
        if (prevFocusOwner != null && prevFocusOwner.getScene() != null) {
            prevFocusOwner.requestFocus();
        }
        prevFocusOwner = null;
    }

    private Node firstFocusTarget() {
        // Discovery order: first action button -> first focusable in content -> the card.
        Node inActions = firstFocusableIn(actionsNode);
        if (inActions != null) {
            return inActions;
        }
        Node inContent = firstFocusableIn(getSkinnable().getContent());
        if (inContent != null) {
            return inContent;
        }
        return dialogCard;
    }

    private static Node firstFocusableIn(Node root) {
        if (root == null || !root.isVisible() || root.isDisabled()) {
            return null;
        }
        if (root.isFocusTraversable()) {
            return root;
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = firstFocusableIn(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void handleTabTrap(KeyEvent event) {
        if (event.getCode() != KeyCode.TAB || !scrimActive() || !getSkinnable().isShowing()) {
            return;
        }
        Scene scene = getSkinnable().getScene();
        if (scene == null) {
            return;
        }
        List<Node> focusables = new ArrayList<>();
        for (Node child : dialogCard.getChildrenUnmodifiable()) {
            collectFocusable(child, focusables);
        }
        // Take over traversal entirely so focus can never leave the modal dialog.
        event.consume();
        if (focusables.isEmpty()) {
            return;
        }
        int index = focusables.indexOf(scene.getFocusOwner());
        int next;
        if (event.isShiftDown()) {
            next = index <= 0 ? focusables.size() - 1 : index - 1;
        } else {
            next = (index < 0 || index >= focusables.size() - 1) ? 0 : index + 1;
        }
        focusables.get(next).requestFocus();
    }

    private static void collectFocusable(Node node, List<Node> out) {
        if (node == null || !node.isVisible() || node.isDisabled()) {
            return;
        }
        if (node.isFocusTraversable()) {
            out.add(node);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectFocusable(child, out);
            }
        }
    }

    // ==================== Animation gating ====================

    private boolean animationsActive() {
        return getSkinnable().isAnimated()
                && getSkinnable().getScene() != null
                && isAnimationDurationPositive();
    }

    private boolean isAnimationDurationPositive() {
        Duration duration = getSkinnable().getAnimationDuration();
        return duration != null && !duration.isUnknown() && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO);
    }

    private boolean isAnimationRunning() {
        return animation != null && animation.getStatus() == Animation.Status.RUNNING;
    }

    private Interpolator interpolatorOrDefault() {
        Interpolator value = getSkinnable().getAnimationInterpolator();
        return value == null ? RXDialog.DEFAULT_ANIMATION_INTERPOLATOR : value;
    }

    private RXDialogTransition transitionOrDefault() {
        RXDialogTransition value = getSkinnable().getTransition();
        return value == null ? RXDialog.DEFAULT_TRANSITION : value;
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        return Math.min(value, 1.0);
    }

    // ==================== Drag / resize gestures ====================

    // Gestures are live only while the dialog is shown and not animating.
    private boolean gesturesEnabled() {
        return getSkinnable().isShowing() && !isAnimationRunning();
    }

    private boolean gestureInProgress() {
        return dragActive || resizeActive;
    }

    private void onCardMouseMoved(MouseEvent event) {
        dialogCard.setCursor(resolveHoverCursor(event));
    }

    // The cursor for a hover at the event's position: one of the eight resize cursors over a
    // border zone (priority), else MOVE over the draggable title band (away from interactive
    // header nodes), else null so the node's own / inherited cursor shows.
    private Cursor resolveHoverCursor(MouseEvent event) {
        if (!gesturesEnabled()) {
            return null;
        }
        Point2D local = dialogCard.sceneToLocal(event.getSceneX(), event.getSceneY());
        if (getSkinnable().isEnableResizable()) {
            Cursor resize = resizeCursor(hitResizeEdges(local));
            if (resize != null) {
                return resize;
            }
        }
        if (getSkinnable().isEnableDraggable() && inDragBand(local) && !isInteractiveTarget(event.getTarget())) {
            return Cursor.MOVE;
        }
        return null;
    }

    private boolean inDragBand(Point2D local) {
        return local.getX() >= 0.0 && local.getX() <= dialogCard.getWidth()
                && local.getY() >= 0.0 && local.getY() <= DRAG_BAND_HEIGHT;
    }

    // Bitmask of the resize edges hit at a card-local point (0 = none / outside the card).
    // West/East and North/South are mutually exclusive (else-if), so a card narrower than
    // 2*RESIZE_EDGE never reports both sides and the resize math stays well-defined.
    private int hitResizeEdges(Point2D local) {
        double w = dialogCard.getWidth();
        double h = dialogCard.getHeight();
        if (local.getX() < 0.0 || local.getX() > w || local.getY() < 0.0 || local.getY() > h) {
            return 0;
        }
        int edges = 0;
        if (local.getX() <= RESIZE_EDGE) {
            edges |= EDGE_WEST;
        } else if (local.getX() >= w - RESIZE_EDGE) {
            edges |= EDGE_EAST;
        }
        if (local.getY() <= RESIZE_EDGE) {
            edges |= EDGE_NORTH;
        } else if (local.getY() >= h - RESIZE_EDGE) {
            edges |= EDGE_SOUTH;
        }
        return edges;
    }

    // Maps an edge bitmask to its resize cursor (corners take priority over single edges);
    // null when no edge is set.
    private static Cursor resizeCursor(int edges) {
        boolean west = (edges & EDGE_WEST) != 0;
        boolean east = (edges & EDGE_EAST) != 0;
        boolean north = (edges & EDGE_NORTH) != 0;
        boolean south = (edges & EDGE_SOUTH) != 0;
        if (north && west) {
            return Cursor.NW_RESIZE;
        }
        if (north && east) {
            return Cursor.NE_RESIZE;
        }
        if (south && west) {
            return Cursor.SW_RESIZE;
        }
        if (south && east) {
            return Cursor.SE_RESIZE;
        }
        if (west) {
            return Cursor.W_RESIZE;
        }
        if (east) {
            return Cursor.E_RESIZE;
        }
        if (north) {
            return Cursor.N_RESIZE;
        }
        if (south) {
            return Cursor.S_RESIZE;
        }
        return null;
    }

    private void onCardMousePressed(MouseEvent event) {
        // Only the primary button starts a gesture (decision 3.4 #9); never while
        // animating / hidden.
        if (event.getButton() != MouseButton.PRIMARY || !gesturesEnabled()) {
            return;
        }
        Point2D local = dialogCard.sceneToLocal(event.getSceneX(), event.getSceneY());
        // Resize wins over drag (so the top border band resizes north rather than dragging),
        // and corners win over edges inside resizeCursor / beginResize (spec §3.4 #3).
        if (getSkinnable().isEnableResizable()) {
            int edges = hitResizeEdges(local);
            if (edges != 0) {
                beginResize(edges, event);
                event.consume();
                return;
            }
        }
        if (getSkinnable().isEnableDraggable() && inDragBand(local) && !isInteractiveTarget(event.getTarget())) {
            beginDrag(event);
            event.consume();
        }
    }

    private void beginDrag(MouseEvent event) {
        // Start from a fully-cleared state so drag and resize are never both active.
        clearGestureFlags();
        dragActive = true;
        gestureStartSceneX = event.getSceneX();
        gestureStartSceneY = event.getSceneY();
        gestureStartOffsetX = dragOffsetX;
        gestureStartOffsetY = dragOffsetY;
        dialogCard.setCursor(Cursor.MOVE);
    }

    private void beginResize(int edges, MouseEvent event) {
        clearGestureFlags();
        resizeActive = true;
        resizeWest = (edges & EDGE_WEST) != 0;
        resizeEast = (edges & EDGE_EAST) != 0;
        resizeNorth = (edges & EDGE_NORTH) != 0;
        resizeSouth = (edges & EDGE_SOUTH) != 0;
        gestureStartSceneX = event.getSceneX();
        gestureStartSceneY = event.getSceneY();
        gestureStartOffsetX = dragOffsetX;
        gestureStartOffsetY = dragOffsetY;
        gestureStartW = dialogCard.getWidth();
        gestureStartH = dialogCard.getHeight();
        dialogCard.setCursor(resizeCursor(edges));
    }

    private void onCardMouseDragged(MouseEvent event) {
        if (!event.isPrimaryButtonDown()) {
            return;
        }
        if (resizeActive) {
            updateResize(event);
            event.consume();
        } else if (dragActive) {
            // Accumulate the raw offset; layoutChildren clamps it into the scene per frame.
            dragOffsetX = gestureStartOffsetX + (event.getSceneX() - gestureStartSceneX);
            dragOffsetY = gestureStartOffsetY + (event.getSceneY() - gestureStartSceneY);
            // The offset is consumed by the control's layoutChildren, so relayout the control.
            getSkinnable().requestLayout();
            event.consume();
        }
    }

    // Center-anchored resize: the grabbed edge follows the pointer while the opposite edge
    // mirrors it, so the card grows / shrinks symmetrically about its current centre, which
    // stays fixed (the centred card's centre = scene centre + dragOffset, independent of the
    // card size, and dragOffset is left untouched — so a centred dialog stays centred and a
    // dragged-off-centre card scales about wherever it is). The grabbed edge moves by the
    // pointer delta, so the size changes by twice it. The per-frame scene clamp in
    // layoutChildren still pins the card on-screen, so growing near an edge can't push it out
    // (no clip needed).
    private void updateResize(MouseEvent event) {
        double dx = event.getSceneX() - gestureStartSceneX;
        double dy = event.getSceneY() - gestureStartSceneY;
        if (resizeEast || resizeWest) {
            double edgeDelta = resizeEast ? dx : -dx;
            userWidth = clampCardWidth(gestureStartW + 2.0 * edgeDelta, availContentWidth);
        }
        if (resizeNorth || resizeSouth) {
            double edgeDelta = resizeSouth ? dy : -dy;
            // Height is for-width: use the (possibly just-updated) card width so the clamp
            // matches what layoutChildren will compute for this frame.
            double widthForHeight = userWidth != null
                    ? clampCardWidth(userWidth, availContentWidth) : dialogCard.getWidth();
            userHeight = clampCardHeight(gestureStartH + 2.0 * edgeDelta, widthForHeight, availContentHeight);
        }
        getSkinnable().requestLayout();
    }

    private void onCardMouseReleased(MouseEvent event) {
        if (gestureInProgress()) {
            // End whichever gesture was active (drag OR resize), then recompute the hover
            // cursor for where the pointer ended up. Geometry is left untouched.
            clearGestureFlags();
            dialogCard.setCursor(resolveHoverCursor(event));
            event.consume();
        }
    }

    private void onCardMouseExited(MouseEvent event) {
        // Don't clear the cursor mid-gesture: drag events keep targeting the card while the
        // pointer roams outside it, and the release handler refreshes the cursor.
        if (!gestureInProgress()) {
            dialogCard.setCursor(null);
        }
    }

    // Walks the pick chain from the event target up to (not including) the card; true if any
    // node is a button, a focus-traversable control, or the header close (X) button — those
    // keep their own click handling, so a press on them never starts a drag (decision 4 #4).
    private boolean isInteractiveTarget(EventTarget target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        while (node != null && node != dialogCard) {
            if (node instanceof ButtonBase || node.isFocusTraversable()
                    || node.getStyleClass().contains("close-button")) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    // Disabling enableDraggable / enableResizable cancels the matching in-flight gesture and stops
    // new ones, but keeps geometry (decision 3.4 #10); the cursor is cleared so the next hover
    // recomputes it.
    private void onGestureEnablementChanged() {
        if (!getSkinnable().isEnableDraggable()) {
            dragActive = false;
        }
        if (!getSkinnable().isEnableResizable()) {
            resizeActive = false;
        }
        dialogCard.setCursor(null);
    }

    // Clears every active-gesture flag (drag, resize, and the resize edge set) without
    // touching geometry or the cursor. The single place gesture state ends.
    private void clearGestureFlags() {
        dragActive = false;
        resizeActive = false;
        resizeWest = false;
        resizeEast = false;
        resizeNorth = false;
        resizeSouth = false;
    }

    // Ends any in-flight gesture without touching geometry (called when a transition starts).
    private void cancelGestures() {
        clearGestureFlags();
        dialogCard.setCursor(null);
    }

    // Recenters and drops the user size + drag offset after the dialog has fully hidden, so a
    // re-show starts centered and auto-sized.
    private void resetCardGeometry() {
        cancelGestures();
        dragOffsetX = 0.0;
        dragOffsetY = 0.0;
        userWidth = null;
        userHeight = null;
    }

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        // The repeatedly-rebuilt animation field is stopped here explicitly rather
        // than via the disposer, which would hold a stale Timeline reference.
        stopAnimation();
        prevFocusOwner = null;
    }
}
