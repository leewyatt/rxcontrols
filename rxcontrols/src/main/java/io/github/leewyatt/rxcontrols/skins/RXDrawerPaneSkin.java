package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDrawerPane;
import io.github.leewyatt.rxcontrols.RXDrawerPane.DrawerMode;
import io.github.leewyatt.rxcontrols.event.RXDrawerEvent;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.EventType;
import javafx.geometry.HPos;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Skin for {@link RXDrawerPane}. Stacks a fill-the-area content layer under an
 * edge-attached drawer panel and slides the panel with a single, superseded
 * {@link Timeline} that only tweens {@code translate} — layout always writes the
 * panel's open position, so reopening is always geometrically correct and a
 * mid-flight reversal resumes smoothly from the current value.
 *
 * <p>The animation paradigm mirrors {@code RelayoutAnimator}: a single
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
    private final Region scrim = new Region();
    private final StackPane drawerWrapper = new StackPane();
    private final Rectangle clipRect = new Rectangle();

    private Timeline animation;
    private boolean initialized;
    // Guard lifecycle-event firing: OPENED/CLOSED fire only when a matching
    // OPENING/CLOSING transition is in flight, never on a redundant settle.
    private boolean openInFlight;
    private boolean closeInFlight;
    // The focus owner captured when a modal drawer opened, restored when it closes.
    private Node prevFocusOwner;
    // The main-axis drawer thickness from the last layout pass; the panel layout and
    // the closed-state translate both read it so off-screen parking always matches the
    // rendered panel size.
    private double drawerThickness = DEFAULT_DRAWER_THICKNESS;

    // PUSH expand ratio in [0, 1]: 0 = collapsed, 1 = fully open. Only PUSH tweens
    // it; its change relayouts so the content makes room (the PUSH cost).
    private final DoubleProperty progress = new SimpleDoubleProperty(this, "progress", 0.0) {
        @Override
        protected void invalidated() {
            getSkinnable().requestLayout();
        }
    };

    /**
     * Creates the skin, assembles the content and drawer layers, installs the clip,
     * and registers all listeners with the disposer.
     *
     * @param control the drawer pane this skin is attached to
     */
    public RXDrawerPaneSkin(RXDrawerPane control) {
        super(control);

        drawerWrapper.getStyleClass().add("drawer-wrapper");
        scrim.getStyleClass().add("scrim");

        // The drawer wrapper is the last-resort focus target when modal and nothing
        // inside is focusable.
        drawerWrapper.setFocusTraversable(true);

        // z-order: content (bottom) → scrim (middle) → drawer (top).
        getChildren().setAll(contentPane, scrim, drawerWrapper);
        updateContent();
        updateDrawerContent();
        applyDrawerWrapperRest(control.isShowing());
        applyScrimRest(control.isShowing());

        control.setClip(clipRect);
        disposer.registerDisposeTask(() -> control.setClip(null));

        // Focus trap: while modal and open, Tab / Shift+Tab cycle within the drawer
        // subtree and never escape (capturing filter takes over traversal).
        disposer.registerEventFilter(control, KeyEvent.KEY_PRESSED, this::handleTabTrap);

        // Clicking the scrim requests a close (it is pickable only while
        // open and modal, so the guard is belt-and-suspenders).
        disposer.registerEventHandler(scrim, MouseEvent.MOUSE_CLICKED, event -> {
            if (control.isCloseOnScrimClick() && scrimActive() && control.isShowing()) {
                control.close();
                event.consume();
            }
        });

        // ESC anywhere in the drawer subtree requests a close. A capturing filter
        // catches it before the focused descendant consumes it.
        disposer.registerEventFilter(control, KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && control.isCloseOnEsc() && control.isShowing()) {
                control.close();
                event.consume();
            }
        });

        // A ChangeListener observes only committed value changes; vetoed or
        // redundant close requests leave showing unchanged and do not restart transitions.
        disposer.registerListener(control.showingProperty(),
                (obs, wasShowing, isShowing) -> handleShowingChanged(isShowing));
        disposer.registerListener(control.sideProperty(), this::onSideChanged);
        disposer.registerListener(control.drawerModeProperty(), this::onModeChanged);
        disposer.registerListener(control.contentProperty(), this::updateContent);
        disposer.registerListener(control.drawerContentProperty(), this::updateDrawerContent);
        disposer.registerListener(control.scrimVisibleProperty(), this::onScrimChanged);
        // animated / animationDuration / animationInterpolator are intentionally NOT
        // observed: they are read at play time, so a change applies to the next
        // transition and never disturbs an in-flight slide.
        disposer.registerListener(control.prefDrawerWidthProperty(), this::onThicknessChanged);
        disposer.registerListener(control.prefDrawerHeightProperty(), this::onThicknessChanged);
        disposer.registerListener(control.sceneProperty(),
                (obs, oldScene, newScene) -> onSceneChanged(newScene));
    }

    // ==================== Slots ====================

    private void updateContent() {
        Node content = getSkinnable().getContent();
        if (content == null) {
            contentPane.getChildren().clear();
        } else {
            contentPane.getChildren().setAll(content);
        }
    }

    private void updateDrawerContent() {
        Node content = getSkinnable().getDrawerContent();
        if (content == null) {
            drawerWrapper.getChildren().clear();
        } else {
            drawerWrapper.getChildren().setAll(content);
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
        double newThickness = computeThickness(horizontal ? contentHeight : contentWidth);
        // A thickness change (drawerContent swapped or resized) must re-snap the resting
        // pose: otherwise a closed panel keeps its previous off-screen offset and shows a
        // sliver, and a later animated open starts half-exposed.
        boolean thicknessChanged = newThickness != drawerThickness;
        drawerThickness = newThickness;
        double drawerW = horizontal ? drawerThickness : contentWidth;
        double drawerH = horizontal ? contentHeight : drawerThickness;

        // The scrim always fills; it is only visible while modal (see applyScrimRest).
        layoutInArea(scrim, contentX, contentY, contentWidth, contentHeight, 0, HPos.LEFT, VPos.TOP);

        if (isPush()) {
            layoutPush(contentX, contentY, contentWidth, contentHeight, drawerW, drawerH);
        } else {
            layoutScrim(contentX, contentY, contentWidth, contentHeight, drawerW, drawerH);
        }

        if ((!initialized || thicknessChanged) && !isAnimationRunning()) {
            snapToShowing();
            initialized = true;
        }
        resetClip();
    }

    // OVERLAY: content fills, the panel rests at its open (edge-attached) position;
    // the closed state and the slide are expressed purely by translate.
    private void layoutScrim(double contentX, double contentY, double contentWidth,
                               double contentHeight, double drawerW, double drawerH) {
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
        layoutInArea(drawerWrapper, areaX, areaY, drawerW, drawerH, 0, HPos.LEFT, VPos.TOP);
    }

    // PUSH: the panel and the shrunken content are both positioned directly from
    // progress p — the panel does not translate.
    private void layoutPush(double contentX, double contentY, double contentWidth,
                            double contentHeight, double drawerW, double drawerH) {
        double p = progress.get();
        double cx = contentX;
        double cy = contentY;
        double cw = contentWidth;
        double ch = contentHeight;
        double dx = contentX;
        double dy = contentY;
        switch (sideOrDefault()) {
            case LEFT -> {
                cx = contentX + drawerW * p;
                cw = contentWidth - drawerW * p;
                dx = contentX + drawerW * (p - 1.0);
            }
            case RIGHT -> {
                cw = contentWidth - drawerW * p;
                dx = contentX + contentWidth - drawerW * p;
            }
            case TOP -> {
                cy = contentY + drawerH * p;
                ch = contentHeight - drawerH * p;
                dy = contentY + drawerH * (p - 1.0);
            }
            case BOTTOM -> {
                ch = contentHeight - drawerH * p;
                dy = contentY + contentHeight - drawerH * p;
            }
            default -> {
                // unreachable
            }
        }
        layoutInArea(contentPane, cx, cy, Math.max(0.0, cw), Math.max(0.0, ch), 0, HPos.LEFT, VPos.TOP);
        layoutInArea(drawerWrapper, dx, dy, drawerW, drawerH, 0, HPos.LEFT, VPos.TOP);
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
            applyDrawerWrapperRest(true);
            moveFocusIntoDrawer();
            fireLifecycle(RXDrawerEvent.OPENING);
            playOpen();
        } else {
            closeInFlight = true;
            openInFlight = false;
            fireLifecycle(RXDrawerEvent.CLOSING);
            playClose();
        }
    }

    private void fireLifecycle(EventType<RXDrawerEvent> type) {
        getSkinnable().fireEvent(new RXDrawerEvent(type, getSkinnable()));
    }

    private void playOpen() {
        stopAnimation();
        if (!animationsActive()) {
            finalizeOpen();
            return;
        }
        Interpolator interpolator = interpolatorOrDefault();
        List<KeyValue> keyValues = new ArrayList<>();
        if (isPush()) {
            keyValues.add(new KeyValue(progress, 1.0, interpolator));
        } else {
            keyValues.add(new KeyValue(axisTranslate(), 0.0, interpolator));
            if (scrimActive()) {
                // Make the scrim pickable and fade it in along the same KeyFrame.
                scrim.setVisible(true);
                scrim.setMouseTransparent(false);
                keyValues.add(new KeyValue(scrim.opacityProperty(), 1.0, interpolator));
            }
        }
        Timeline timeline = playTimeline(keyValues, this::finalizeOpen);
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
        List<KeyValue> keyValues = new ArrayList<>();
        if (isPush()) {
            keyValues.add(new KeyValue(progress, 0.0, interpolator));
        } else {
            keyValues.add(new KeyValue(axisTranslate(), closedTranslate(), interpolator));
            if (scrimActive()) {
                keyValues.add(new KeyValue(scrim.opacityProperty(), 0.0, interpolator));
            }
        }
        Timeline timeline = playTimeline(keyValues, this::finalizeClose);
        animation = timeline;
        timeline.play();
    }

    private Timeline playTimeline(List<KeyValue> keyValues, Runnable onFinish) {
        Timeline timeline = new Timeline(new KeyFrame(getSkinnable().getAnimationDuration(),
                keyValues.toArray(new KeyValue[0])));
        timeline.setOnFinished(event -> {
            if (animation == timeline) {
                animation = null;
            }
            onFinish.run();
        });
        return timeline;
    }

    private void finalizeOpen() {
        if (isPush()) {
            // PUSH positions the panel from progress; keep translate neutral.
            drawerWrapper.setTranslateX(0.0);
            drawerWrapper.setTranslateY(0.0);
            progress.set(1.0);
        } else {
            drawerWrapper.setTranslateX(0.0);
            drawerWrapper.setTranslateY(0.0);
        }
        applyDrawerWrapperRest(true);
        applyScrimRest(true);
        if (openInFlight) {
            openInFlight = false;
            fireLifecycle(RXDrawerEvent.OPENED);
        }
    }

    private void finalizeClose() {
        if (isPush()) {
            drawerWrapper.setTranslateX(0.0);
            drawerWrapper.setTranslateY(0.0);
            progress.set(0.0);
        } else {
            double closed = closedTranslate();
            if (isHorizontal()) {
                drawerWrapper.setTranslateX(closed);
                drawerWrapper.setTranslateY(0.0);
            } else {
                drawerWrapper.setTranslateY(closed);
                drawerWrapper.setTranslateX(0.0);
            }
        }
        applyScrimRest(false);
        boolean wasCloseInFlight = closeInFlight;
        if (wasCloseInFlight) {
            closeInFlight = false;
            restoreFocus();
        }
        applyDrawerWrapperRest(false);
        if (wasCloseInFlight) {
            fireLifecycle(RXDrawerEvent.CLOSED);
        }
    }

    // ==================== Focus (a11y) ====================

    /**
     * Captures the current focus owner and moves focus into the drawer, only when
     * modal (a non-modal drawer leaves the page interactive and does not steal focus).
     */
    private void moveFocusIntoDrawer() {
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
        // Discovery order: first focusable in drawerContent → the panel.
        Node inContent = firstFocusableIn(getSkinnable().getDrawerContent());
        if (inContent != null) {
            return inContent;
        }
        return drawerWrapper;
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
        for (Node child : drawerWrapper.getChildrenUnmodifiable()) {
            collectFocusable(child, focusables);
        }
        // Take over traversal entirely so focus can never leave the modal drawer.
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

    private void snapToShowing() {
        boolean open = getSkinnable().isShowing();
        if (isPush()) {
            drawerWrapper.setTranslateX(0.0);
            drawerWrapper.setTranslateY(0.0);
            progress.set(open ? 1.0 : 0.0);
        } else {
            double target = open ? 0.0 : closedTranslate();
            if (isHorizontal()) {
                drawerWrapper.setTranslateX(target);
                drawerWrapper.setTranslateY(0.0);
            } else {
                drawerWrapper.setTranslateY(target);
                drawerWrapper.setTranslateX(0.0);
            }
        }
        applyScrimRest(open);
        applyDrawerWrapperRest(open);
    }

    private DrawerMode drawerModeOrDefault() {
        DrawerMode mode = getSkinnable().getDrawerMode();
        return mode == null ? RXDrawerPane.DEFAULT_DRAWER_MODE : mode;
    }

    private boolean isPush() {
        return drawerModeOrDefault() == DrawerMode.PUSH;
    }

    /**
     * Whether the scrim participates: only when overlaying with the pane
     * enabled. PUSH is never modal.
     */
    private boolean scrimActive() {
        return drawerModeOrDefault() == DrawerMode.OVERLAY && getSkinnable().isScrimVisible();
    }

    /**
     * Settles the scrim to its resting pose: opaque (its dim comes from the
     * {@code .scrim} CSS background colour) and pickable when open and active,
     * fully transparent and click-through otherwise.
     *
     * @param open whether the drawer rests open
     */
    private void applyScrimRest(boolean open) {
        if (open && scrimActive()) {
            scrim.setVisible(true);
            scrim.setMouseTransparent(false);
            scrim.setOpacity(1.0);
        } else {
            scrim.setVisible(false);
            scrim.setMouseTransparent(true);
            scrim.setOpacity(0.0);
        }
    }

    private void applyDrawerWrapperRest(boolean open) {
        drawerWrapper.setVisible(open);
        drawerWrapper.setMouseTransparent(!open);
    }

    private void onScrimChanged() {
        // An scrimVisible change re-settles the resting pose unless an animation
        // currently owns the pane opacity.
        if (!isAnimationRunning()) {
            applyScrimRest(getSkinnable().isShowing());
        }
    }

    private void stopAnimation() {
        if (animation != null) {
            animation.stop();
            animation = null;
        }
    }

    // ==================== Property reactions ====================

    private void onSideChanged() {
        // A side change retargets the axis; settle for the new axis.
        settleAndRelayout();
    }

    private void onModeChanged() {
        // A mode change switches the geometry channel (translate vs progress);
        // settle the current transition for the new mode and re-snap.
        settleAndRelayout();
    }

    // Stop any slide and settle the in-flight transition to its terminal — the
    // inFlight guard fires the matching OPENED/CLOSED at most once and is cleared, so
    // a later detach cannot fire a stale event. The listener runs after the property
    // updates, so finalize* already uses the new side/mode. Layout then re-snaps.
    private void settleAndRelayout() {
        stopAnimation();
        if (getSkinnable().isShowing()) {
            finalizeOpen();
        } else {
            finalizeClose();
        }
        initialized = false;
        getSkinnable().requestLayout();
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

    private Side sideOrDefault() {
        Side current = getSkinnable().getSide();
        return current == null ? RXDrawerPane.DEFAULT_SIDE : current;
    }

    private boolean isHorizontal() {
        Side current = sideOrDefault();
        return current == Side.LEFT || current == Side.RIGHT;
    }

    // The main-axis panel thickness for the current side. An explicit
    // prefDrawer{Width,Height} wins; otherwise the drawerContent's preferred main-axis
    // size is used (DEFAULT_DRAWER_THICKNESS only when it has none). The result is then
    // bounded by the content's min/max exactly like JavaFX sizing, so the panel never
    // truncates below the content's min and never overshoots its max. crossExtent is the
    // panel's fixed cross-axis size (host height for LEFT/RIGHT, host width for TOP/BOTTOM).
    private double computeThickness(double crossExtent) {
        RXDrawerPane control = getSkinnable();
        boolean horizontal = isHorizontal();
        double appPref = horizontal ? control.getPrefDrawerWidth() : control.getPrefDrawerHeight();
        Node content = control.getDrawerContent();
        double contentMin = content == null ? 0.0
                : (horizontal ? content.minWidth(crossExtent) : content.minHeight(crossExtent));
        double contentPref = content == null ? 0.0
                : (horizontal ? content.prefWidth(crossExtent) : content.prefHeight(crossExtent));
        double contentMax = content == null ? 0.0
                : (horizontal ? content.maxWidth(crossExtent) : content.maxHeight(crossExtent));
        if (contentMax <= 0.0) {
            // 0 / unset / non-resizable content carries no meaningful upper bound.
            contentMax = Double.MAX_VALUE;
        }
        double pref = (Double.isFinite(appPref) && appPref > 0.0) ? appPref
                : (contentPref > 0.0 ? contentPref : DEFAULT_DRAWER_THICKNESS);
        return clampSize(contentMin, pref, contentMax);
    }

    // Mirrors the package-private Region.boundedSize: clamp pref into [min, max] with
    // min taking precedence when min > max, so the content min is never violated.
    private static double clampSize(double min, double pref, double max) {
        double atLeastMin = Math.max(pref, min);
        double cap = min >= max ? min : max;
        return Math.min(atLeastMin, cap);
    }

    private double closedTranslate() {
        Side current = sideOrDefault();
        double sign = (current == Side.LEFT || current == Side.TOP) ? -1.0 : 1.0;
        return sign * drawerThickness;
    }

    private DoubleProperty axisTranslate() {
        return isHorizontal() ? drawerWrapper.translateXProperty() : drawerWrapper.translateYProperty();
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
