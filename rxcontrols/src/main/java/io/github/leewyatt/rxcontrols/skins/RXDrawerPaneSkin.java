package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDrawerPane;
import io.github.leewyatt.rxcontrols.enums.CloseReason;
import io.github.leewyatt.rxcontrols.event.RXDrawerEvent;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.event.EventType;
import javafx.geometry.HPos;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Skin for {@link RXDrawerPane}. Stacks a fill-the-area content layer under an
 * edge-attached drawer panel and slides the panel with a single, superseded
 * {@link Timeline} that only tweens {@code translate} — layout always writes the
 * panel's open position, so reopening is always geometrically correct and a
 * mid-flight reversal resumes smoothly from the current value.
 *
 * <p>The animation paradigm mirrors {@code MasonryAnimator}: a single
 * {@link #animation} field is stopped and replaced rather than reused, every
 * termination path converges on {@link #finalizeOpen()} / {@link #finalizeClose()},
 * and because the field is rebuilt repeatedly it is stopped explicitly in
 * {@link #disposeSkin()} instead of being registered with the disposer. The
 * authoritative open/close intent is the control's
 * {@link RXDrawerPane#showingProperty() showing} property; the skin never invents
 * a parallel state.</p>
 */
public class RXDrawerPaneSkin extends RXSkinBase<RXDrawerPane> {

    /**
     * Default drawer thickness used when {@code prefDrawerWidth} /
     * {@code prefDrawerHeight} are not a finite positive value. Sits in the
     * Fluent-small / Naive / antd thickness band.
     */
    private static final double DEFAULT_DRAWER_THICKNESS = 320.0;

    private final StackPane contentPane = new StackPane();
    private final StackPane drawerPane = new StackPane();
    private final Rectangle clipRect = new Rectangle();

    private Timeline animation;
    private boolean initialized;
    // Guard lifecycle-event firing: OPENED/CLOSED fire only when a matching
    // OPENING/CLOSING transition is in flight, never on a redundant settle.
    private boolean openInFlight;
    private boolean closeInFlight;

    /**
     * Creates the skin, assembles the content and drawer layers, installs the
     * clip, and registers all listeners with the disposer.
     *
     * @param control the drawer pane this skin is attached to
     */
    public RXDrawerPaneSkin(RXDrawerPane control) {
        super(control);

        drawerPane.getStyleClass().add("drawer");
        getChildren().setAll(contentPane, drawerPane);
        updateContent();
        updateDrawerContent();

        control.setClip(clipRect);
        disposer.registerDisposeTask(() -> control.setClip(null));

        // A ChangeListener (not invalidation): a vetoed close that reverts
        // showing true→false→true reports old == new and is correctly skipped.
        disposer.registerListener(control.showingProperty(),
                (obs, wasShowing, isShowing) -> handleShowingChanged(isShowing));
        disposer.registerListener(control.sideProperty(), this::onSideChanged);
        disposer.registerListener(control.contentProperty(), this::updateContent);
        disposer.registerListener(control.drawerContentProperty(), this::updateDrawerContent);
        disposer.registerListener(control.animatedProperty(), this::onAnimatedChanged);
        disposer.registerListener(control.animationDurationProperty(), this::onAnimationDurationChanged);
        disposer.registerListener(control.prefDrawerWidthProperty(), this::onThicknessChanged);
        disposer.registerListener(control.prefDrawerHeightProperty(), this::onThicknessChanged);
        disposer.registerListener(control.sceneProperty(),
                (obs, oldScene, newScene) -> onSceneChanged(newScene));
    }

    // ==================== Slots ====================

    private void updateContent() {
        setSingleChild(contentPane, getSkinnable().getContent());
    }

    private void updateDrawerContent() {
        setSingleChild(drawerPane, getSkinnable().getDrawerContent());
    }

    private static void setSingleChild(StackPane host, Node child) {
        if (child == null) {
            host.getChildren().clear();
        } else {
            host.getChildren().setAll(child);
        }
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        boolean horizontal = isHorizontal();
        double thickness = thickness();
        double drawerW = horizontal ? thickness : contentWidth;
        double drawerH = horizontal ? contentHeight : thickness;

        layoutInArea(contentPane, contentX, contentY, contentWidth, contentHeight, 0, HPos.LEFT, VPos.TOP);

        double areaX = contentX;
        double areaY = contentY;
        switch (sideOrDefault()) {
            case RIGHT -> areaX = contentX + contentWidth - drawerW;
            case BOTTOM -> areaY = contentY + contentHeight - drawerH;
            default -> {
                // LEFT / TOP attach at the content origin.
            }
        }
        layoutInArea(drawerPane, areaX, areaY, drawerW, drawerH, 0, HPos.LEFT, VPos.TOP);

        if (!initialized && !isAnimationRunning()) {
            snapToShowing();
            initialized = true;
        }
        resetClip();
    }

    private void resetClip() {
        clipRect.setX(0);
        clipRect.setY(0);
        clipRect.setWidth(getSkinnable().getWidth());
        clipRect.setHeight(getSkinnable().getHeight());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        Node node = getSkinnable().getContent();
        double inner = (height == -1) ? -1 : Math.max(0, height - topInset - bottomInset);
        double cw = node == null ? 0 : node.prefWidth(inner);
        return leftInset + cw + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        Node node = getSkinnable().getContent();
        double inner = (width == -1) ? -1 : Math.max(0, width - leftInset - rightInset);
        double ch = node == null ? 0 : node.prefHeight(inner);
        return topInset + ch + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        Node node = getSkinnable().getContent();
        double inner = (height == -1) ? -1 : Math.max(0, height - topInset - bottomInset);
        double cw = node == null ? 0 : node.minWidth(inner);
        return leftInset + cw + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        Node node = getSkinnable().getContent();
        double inner = (width == -1) ? -1 : Math.max(0, width - leftInset - rightInset);
        double ch = node == null ? 0 : node.minHeight(inner);
        return topInset + ch + bottomInset;
    }

    // ==================== Open / Close animation ====================

    private void handleShowingChanged(boolean showing) {
        if (showing) {
            openInFlight = true;
            closeInFlight = false;
            fireLifecycle(RXDrawerEvent.OPENING, null);
            playOpen();
        } else {
            closeInFlight = true;
            openInFlight = false;
            fireLifecycle(RXDrawerEvent.CLOSING, getSkinnable().getActiveCloseReason());
            playClose();
        }
    }

    private void fireLifecycle(EventType<RXDrawerEvent> type, CloseReason reason) {
        getSkinnable().fireEvent(new RXDrawerEvent(type, getSkinnable(), reason));
    }

    private void playOpen() {
        stopAnimation();
        if (!animationsActive()) {
            finalizeOpen();
            return;
        }
        Interpolator interpolator = interpolatorOrDefault();
        Timeline timeline = new Timeline(new KeyFrame(getSkinnable().getAnimationDuration(),
                new KeyValue(axisTranslate(), 0.0, interpolator)));
        timeline.setOnFinished(event -> {
            if (animation == timeline) {
                animation = null;
            }
            finalizeOpen();
        });
        animation = timeline;
        timeline.play();
    }

    private void playClose() {
        stopAnimation();
        if (!animationsActive()) {
            finalizeClose();
            return;
        }
        Interpolator interpolator = interpolatorOrDefault();
        Timeline timeline = new Timeline(new KeyFrame(getSkinnable().getAnimationDuration(),
                new KeyValue(axisTranslate(), closedTranslate(), interpolator)));
        timeline.setOnFinished(event -> {
            if (animation == timeline) {
                animation = null;
            }
            finalizeClose();
        });
        animation = timeline;
        timeline.play();
    }

    private void finalizeOpen() {
        drawerPane.setTranslateX(0.0);
        drawerPane.setTranslateY(0.0);
        if (openInFlight) {
            openInFlight = false;
            fireLifecycle(RXDrawerEvent.OPENED, null);
        }
    }

    private void finalizeClose() {
        double closed = closedTranslate();
        if (isHorizontal()) {
            drawerPane.setTranslateX(closed);
            drawerPane.setTranslateY(0.0);
        } else {
            drawerPane.setTranslateY(closed);
            drawerPane.setTranslateX(0.0);
        }
        if (closeInFlight) {
            closeInFlight = false;
            fireLifecycle(RXDrawerEvent.CLOSED, getSkinnable().getActiveCloseReason());
        }
    }

    private void snapToShowing() {
        double target = getSkinnable().isShowing() ? 0.0 : closedTranslate();
        if (isHorizontal()) {
            drawerPane.setTranslateX(target);
            drawerPane.setTranslateY(0.0);
        } else {
            drawerPane.setTranslateY(target);
            drawerPane.setTranslateX(0.0);
        }
    }

    private void stopAnimation() {
        if (animation != null) {
            animation.stop();
            animation = null;
        }
    }

    /**
     * Stops the running animation and settles to the terminal pose of the current
     * {@code showing} intent, used when animation is disabled mid-flight.
     */
    private void snapRunningToTerminal() {
        if (animation == null) {
            return;
        }
        stopAnimation();
        if (getSkinnable().isShowing()) {
            finalizeOpen();
        } else {
            finalizeClose();
        }
    }

    // ==================== Property reactions ====================

    private void onSideChanged() {
        // A side change retargets the axis. Stop any slide and settle the in-flight
        // transition to its terminal — the inFlight guard fires the matching
        // OPENED/CLOSED at most once and is cleared, so a later detach cannot fire a
        // stale event. The listener runs after the side updates, so finalize* already
        // uses the new axis. Layout then re-snaps for good measure.
        stopAnimation();
        if (getSkinnable().isShowing()) {
            finalizeOpen();
        } else {
            finalizeClose();
        }
        initialized = false;
        getSkinnable().requestLayout();
    }

    private void onAnimatedChanged() {
        if (!getSkinnable().isAnimated()) {
            snapRunningToTerminal();
        }
    }

    private void onAnimationDurationChanged() {
        if (!isAnimationDurationPositive()) {
            snapRunningToTerminal();
        }
    }

    private void onThicknessChanged() {
        // The closed-state offset depends on thickness; re-snap on next layout
        // unless an animation currently owns the transform.
        if (!isAnimationRunning()) {
            initialized = false;
            getSkinnable().requestLayout();
        }
    }

    private void onSceneChanged(Scene newScene) {
        if (newScene != null) {
            return;
        }
        stopAnimation();
        if (getSkinnable().isShowing()) {
            finalizeOpen();
        } else {
            finalizeClose();
        }
    }

    // ==================== Geometry helpers ====================

    // A-party fallback: a bound side source can momentarily yield null (the
    // control rejects it but cannot revert a bound property), so geometry reads
    // the side through this helper, mirroring RXMasonryPane.alignmentOrDefault().
    private Side sideOrDefault() {
        Side current = getSkinnable().getSide();
        return current == null ? RXDrawerPane.DEFAULT_SIDE : current;
    }

    private boolean isHorizontal() {
        Side current = sideOrDefault();
        return current == Side.LEFT || current == Side.RIGHT;
    }

    private double thickness() {
        RXDrawerPane control = getSkinnable();
        double pref = isHorizontal() ? control.getPrefDrawerWidth() : control.getPrefDrawerHeight();
        return Double.isFinite(pref) && pref > 0 ? pref : DEFAULT_DRAWER_THICKNESS;
    }

    private double closedTranslate() {
        Side current = sideOrDefault();
        double sign = (current == Side.LEFT || current == Side.TOP) ? -1.0 : 1.0;
        return sign * thickness();
    }

    private DoubleProperty axisTranslate() {
        return isHorizontal() ? drawerPane.translateXProperty() : drawerPane.translateYProperty();
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
        return value == null ? RXDrawerPane.DEFAULT_ANIMATION_INTERPOLATOR : value;
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
    }
}
