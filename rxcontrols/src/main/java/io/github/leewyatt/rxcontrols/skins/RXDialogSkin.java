package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXDialog;
import io.github.leewyatt.rxcontrols.enums.CloseReason;
import io.github.leewyatt.rxcontrols.enums.RXDialogTransition;
import io.github.leewyatt.rxcontrols.event.RXDialogEvent;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.ActionEvent;
import javafx.event.EventType;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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

    private final Region overlay = new Region();
    private final StackPane dialogCard = new StackPane();
    private final StackPane contentHolder = new StackPane();
    private final VBox cardColumn = new VBox();
    private final ButtonBar actionsBar = new ButtonBar();
    private StackPane closeButton;

    private Timeline animation;
    // Guard lifecycle firing: SHOWN fires only when a show transition is in flight,
    // hideCompleted only when a hide transition is in flight.
    private boolean openInFlight;
    private boolean closeInFlight;
    // Focus owner captured when a modal dialog opened, restored when it hides.
    private Node prevFocusOwner;

    // Single scalar [0,1]: 0 = fully closed pose, 1 = fully open pose. Its change
    // re-applies the pose, so the Timeline only has to tween this one value.
    private final DoubleProperty progress = new SimpleDoubleProperty(this, "progress", 0.0) {
        @Override
        protected void invalidated() {
            applyPose(get());
        }
    };

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
        cardColumn.getStyleClass().add("card-body");
        contentHolder.getStyleClass().add("content");
        actionsBar.getStyleClass().add("actions");

        // The card catches clicks across its whole bounds so a click on it never
        // falls through to the scrim (which would request a close).
        dialogCard.setPickOnBounds(true);
        // Last-resort focus target when modal and nothing inside is focusable.
        dialogCard.setFocusTraversable(true);

        VBox.setVgrow(contentHolder, Priority.ALWAYS);
        cardColumn.getChildren().setAll(contentHolder, actionsBar);
        dialogCard.getChildren().setAll(cardColumn);

        getChildren().setAll(overlay, dialogCard);

        updateContent();
        rebuildActions();
        updateCloseButton();
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
        disposer.registerListener(control.showCloseButtonProperty(), this::updateCloseButton);
        disposer.registerListener(control.modalProperty(), this::onModalChanged);
        // transition / animated / animationDuration / animationInterpolator are read
        // at play time, so a change applies to the next transition.
        disposer.registerListener(control.sceneProperty(), (obs, oldScene, newScene) -> {
            if (newScene == null) {
                stopAnimation();
                // Drop the captured focus owner if the dialog left the scene without
                // going through the close gate (no finalizeClose -> restoreFocus).
                prevFocusOwner = null;
            }
        });
    }

    // ==================== Slots ====================

    private void updateContent() {
        Node content = getSkinnable().getContent();
        if (content == null) {
            contentHolder.getChildren().clear();
        } else {
            contentHolder.getChildren().setAll(content);
        }
    }

    private void rebuildActions() {
        actionsBar.getButtons().clear();
        for (ButtonType buttonType : getSkinnable().getButtonTypes()) {
            if (buttonType == null) {
                continue;
            }
            actionsBar.getButtons().add(createActionButton(buttonType));
        }
        boolean hasActions = !actionsBar.getButtons().isEmpty();
        actionsBar.setVisible(hasActions);
        actionsBar.setManaged(hasActions);
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

    private void updateCloseButton() {
        if (getSkinnable().isShowCloseButton()) {
            if (closeButton == null) {
                closeButton = createCloseButton();
            }
            if (!dialogCard.getChildren().contains(closeButton)) {
                dialogCard.getChildren().add(closeButton);
            }
        } else if (closeButton != null) {
            dialogCard.getChildren().remove(closeButton);
        }
    }

    private StackPane createCloseButton() {
        Region icon = new Region();
        icon.getStyleClass().add("icon");
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        icon.setMouseTransparent(true);
        StackPane button = new StackPane(icon);
        button.getStyleClass().add("close-button");
        StackPane.setAlignment(button, Pos.TOP_RIGHT);
        StackPane.setMargin(button, new Insets(8));
        // Created at most once (lazy), so registering with the disposer is safe.
        disposer.registerEventHandler(button, MouseEvent.MOUSE_CLICKED, event -> {
            getSkinnable().requestClose(null, CloseReason.CLOSE_BUTTON);
            event.consume();
        });
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

        double cardW = Math.min(contentWidth,
                boundedSize(dialogCard.minWidth(-1), dialogCard.prefWidth(-1), dialogCard.maxWidth(-1)));
        double cardH = Math.min(contentHeight,
                boundedSize(dialogCard.minHeight(cardW), dialogCard.prefHeight(cardW), dialogCard.maxHeight(cardW)));
        double cardX = contentX + (contentWidth - cardW) / 2.0;
        double cardY = contentY + (contentHeight - cardH) / 2.0;
        layoutInArea(dialogCard, cardX, cardY, cardW, cardH, 0, HPos.CENTER, VPos.CENTER);
    }

    // Clamp pref into [min, max] with min winning when min > max (mirrors Region.boundedSize);
    // a non-positive / non-finite max means "no upper bound".
    private static double boundedSize(double min, double pref, double max) {
        double effectiveMax = (max <= 0.0 || !Double.isFinite(max)) ? Double.MAX_VALUE : max;
        double atLeastMin = Math.max(pref, min);
        double cap = min >= effectiveMax ? min : effectiveMax;
        return Math.min(atLeastMin, cap);
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

    // ==================== Pose ====================

    private void applyPose(double rawProgress) {
        double p = clamp01(rawProgress);
        dialogCard.setOpacity(p);
        overlay.setOpacity(scrimActive() ? p : 0.0);

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
        return getSkinnable().isModal();
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
        for (Node button : actionsBar.getButtons()) {
            Node found = firstFocusableIn(button);
            if (found != null) {
                return found;
            }
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
