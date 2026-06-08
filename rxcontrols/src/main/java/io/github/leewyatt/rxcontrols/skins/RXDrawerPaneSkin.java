package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDrawerPane;
import io.github.leewyatt.rxcontrols.enums.CloseReason;
import io.github.leewyatt.rxcontrols.enums.RXDrawerMode;
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
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
    private final Region overlayPane = new Region();
    private final BorderPane drawerPane = new BorderPane();
    private final HBox header = new HBox();
    private final Label titleLabel = new Label();
    private final StackPane closeButton = new StackPane();
    private final Region closeGraphic = new Region();
    private final StackPane body = new StackPane();
    private final ScrollPane scrollPane = new ScrollPane();
    private final StackPane footerContainer = new StackPane();
    private final Rectangle clipRect = new Rectangle();

    private Timeline animation;
    private boolean initialized;
    // Guard lifecycle-event firing: OPENED/CLOSED fire only when a matching
    // OPENING/CLOSING transition is in flight, never on a redundant settle.
    private boolean openInFlight;
    private boolean closeInFlight;
    // The focus owner captured when a modal drawer opened, restored when it closes.
    private Node prevFocusOwner;

    // PUSH expand ratio in [0, 1]: 0 = collapsed, 1 = fully open. Only PUSH tweens
    // it; its change relayouts so the content makes room (the PUSH cost).
    private final DoubleProperty progress = new SimpleDoubleProperty(this, "progress", 0.0) {
        @Override
        protected void invalidated() {
            getSkinnable().requestLayout();
        }
    };

    /**
     * Creates the skin, assembles the content layer and the drawer panel chrome
     * (header / body / footer), installs the clip, and registers all listeners with
     * the disposer.
     *
     * @param control the drawer pane this skin is attached to
     */
    public RXDrawerPaneSkin(RXDrawerPane control) {
        super(control);

        drawerPane.getStyleClass().add("drawer");
        overlayPane.getStyleClass().add("overlay-pane");
        header.getStyleClass().add("header");
        body.getStyleClass().add("body");
        footerContainer.getStyleClass().add("footer");
        closeButton.getStyleClass().add("close-button");
        closeGraphic.getStyleClass().add("graphic");

        // Title takes the leading space so the close button rests at the trailing edge.
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        header.getChildren().add(titleLabel);

        // Icon: a -fx-shape Region, pinned to its pref size so it never stretches; the
        // transparent wrapper is what gets picked, the icon itself is click-through.
        closeGraphic.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        closeGraphic.setMouseTransparent(true);
        closeButton.getChildren().add(closeGraphic);
        closeButton.setAccessibleText("Close");
        closeButton.setFocusTraversable(true);

        scrollPane.setFitToWidth(true);
        // The drawer panel is the last-resort focus target when modal and nothing
        // inside is focusable.
        drawerPane.setFocusTraversable(true);
        drawerPane.setCenter(body);

        // z-order: content (bottom) → overlay pane (middle) → drawer (top).
        getChildren().setAll(contentPane, overlayPane, drawerPane);
        updateContent();
        updateBody();
        updateHeader();
        updateFooter();
        applyOverlayPaneRest(control.isShowing());

        control.setClip(clipRect);
        disposer.registerDisposeTask(() -> control.setClip(null));

        disposer.registerEventHandler(closeButton, MouseEvent.MOUSE_CLICKED, event -> {
            control.requestClose(CloseReason.CLOSE_BUTTON);
            event.consume();
        });

        // The close button is a non-Control focusable, so wire keyboard activation.
        disposer.registerEventHandler(closeButton, KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                control.requestClose(CloseReason.CLOSE_BUTTON);
                event.consume();
            }
        });

        // Focus trap: while modal and open, Tab / Shift+Tab cycle within the drawer
        // subtree and never escape (capturing filter takes over traversal).
        disposer.registerEventFilter(control, KeyEvent.KEY_PRESSED, this::handleTabTrap);

        // Clicking the overlay pane requests an OVERLAY_PANE_CLICK close (it is
        // pickable only while open and modal, so the guard is belt-and-suspenders).
        disposer.registerEventHandler(overlayPane, MouseEvent.MOUSE_CLICKED, event -> {
            if (control.isCloseOnOverlayPaneClick() && overlayPaneActive() && control.isShowing()) {
                control.requestClose(CloseReason.OVERLAY_PANE_CLICK);
                event.consume();
            }
        });

        // ESC anywhere in the drawer subtree requests an ESC close. A capturing filter
        // catches it before the focused descendant consumes it.
        disposer.registerEventFilter(control, KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && control.isCloseOnEsc() && control.isShowing()) {
                control.requestClose(CloseReason.ESC);
                event.consume();
            }
        });

        // A ChangeListener (not invalidation): a vetoed close that reverts
        // showing true→false→true reports old == new and is correctly skipped.
        disposer.registerListener(control.showingProperty(),
                (obs, wasShowing, isShowing) -> handleShowingChanged(isShowing));
        disposer.registerListener(control.sideProperty(), this::onSideChanged);
        disposer.registerListener(control.drawerModeProperty(), this::onModeChanged);
        disposer.registerListener(control.contentProperty(), this::updateContent);
        disposer.registerListener(control.drawerContentProperty(), this::updateBody);
        disposer.registerListener(control.scrollableProperty(), this::updateBody);
        disposer.registerListener(control.titleProperty(), this::updateHeader);
        disposer.registerListener(control.showCloseButtonProperty(), this::updateHeader);
        disposer.registerListener(control.footerProperty(), this::updateFooter);
        disposer.registerListener(control.overlayPaneVisibleProperty(), this::onOverlayPaneChanged);
        disposer.registerListener(control.animatedProperty(), this::onAnimatedChanged);
        disposer.registerListener(control.animationDurationProperty(), this::onAnimationDurationChanged);
        disposer.registerListener(control.prefDrawerWidthProperty(), this::onThicknessChanged);
        disposer.registerListener(control.prefDrawerHeightProperty(), this::onThicknessChanged);
        disposer.registerListener(control.sceneProperty(),
                (obs, oldScene, newScene) -> onSceneChanged(newScene));
    }

    // ==================== Slots & chrome ====================

    private void updateContent() {
        Node content = getSkinnable().getContent();
        if (content == null) {
            contentPane.getChildren().clear();
        } else {
            contentPane.getChildren().setAll(content);
        }
    }

    private void updateBody() {
        Node content = getSkinnable().getDrawerContent();
        if (content == null) {
            scrollPane.setContent(null);
            body.getChildren().clear();
        } else if (getSkinnable().isScrollable()) {
            scrollPane.setContent(content);
            body.getChildren().setAll(scrollPane);
        } else {
            scrollPane.setContent(null);
            body.getChildren().setAll(content);
        }
    }

    private void updateHeader() {
        String title = getSkinnable().getTitle();
        titleLabel.setText(title == null ? "" : title);
        boolean showClose = getSkinnable().isShowCloseButton();
        boolean closeInHeader = header.getChildren().contains(closeButton);
        if (showClose && !closeInHeader) {
            header.getChildren().add(closeButton);
        } else if (!showClose && closeInHeader) {
            header.getChildren().remove(closeButton);
        }
        // Render the header only when it carries something: a title or the close button.
        boolean needsHeader = showClose || !titleLabel.getText().isEmpty();
        drawerPane.setTop(needsHeader ? header : null);
    }

    private void updateFooter() {
        Node footer = getSkinnable().getFooter();
        if (footer == null) {
            footerContainer.getChildren().clear();
            drawerPane.setBottom(null);
        } else {
            footerContainer.getChildren().setAll(footer);
            drawerPane.setBottom(footerContainer);
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

        // The overlay pane always fills; it is only visible while modal (see applyOverlayPaneRest).
        layoutInArea(overlayPane, contentX, contentY, contentWidth, contentHeight, 0, HPos.LEFT, VPos.TOP);

        if (isPush()) {
            layoutPush(contentX, contentY, contentWidth, contentHeight, drawerW, drawerH);
        } else {
            layoutOverlay(contentX, contentY, contentWidth, contentHeight, drawerW, drawerH);
        }

        if (!initialized && !isAnimationRunning()) {
            snapToShowing();
            initialized = true;
        }
        resetClip();
    }

    // OVERLAY: content fills, the panel rests at its open (edge-attached) position;
    // the closed state and the slide are expressed purely by translate.
    private void layoutOverlay(double contentX, double contentY, double contentWidth,
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
        layoutInArea(drawerPane, areaX, areaY, drawerW, drawerH, 0, HPos.LEFT, VPos.TOP);
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
        layoutInArea(drawerPane, dx, dy, drawerW, drawerH, 0, HPos.LEFT, VPos.TOP);
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
            moveFocusIntoDrawer();
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
        List<KeyValue> keyValues = new ArrayList<>();
        if (isPush()) {
            keyValues.add(new KeyValue(progress, 1.0, interpolator));
        } else {
            keyValues.add(new KeyValue(axisTranslate(), 0.0, interpolator));
            if (overlayPaneActive()) {
                // Make the overlay pane pickable and fade it in along the same KeyFrame.
                overlayPane.setVisible(true);
                overlayPane.setMouseTransparent(false);
                keyValues.add(new KeyValue(overlayPane.opacityProperty(), 1.0, interpolator));
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
            if (overlayPaneActive()) {
                keyValues.add(new KeyValue(overlayPane.opacityProperty(), 0.0, interpolator));
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
            drawerPane.setTranslateX(0.0);
            drawerPane.setTranslateY(0.0);
            progress.set(1.0);
        } else {
            drawerPane.setTranslateX(0.0);
            drawerPane.setTranslateY(0.0);
        }
        applyOverlayPaneRest(true);
        if (openInFlight) {
            openInFlight = false;
            fireLifecycle(RXDrawerEvent.OPENED, null);
        }
    }

    private void finalizeClose() {
        if (isPush()) {
            drawerPane.setTranslateX(0.0);
            drawerPane.setTranslateY(0.0);
            progress.set(0.0);
        } else {
            double closed = closedTranslate();
            if (isHorizontal()) {
                drawerPane.setTranslateX(closed);
                drawerPane.setTranslateY(0.0);
            } else {
                drawerPane.setTranslateY(closed);
                drawerPane.setTranslateX(0.0);
            }
        }
        applyOverlayPaneRest(false);
        if (closeInFlight) {
            closeInFlight = false;
            restoreFocus();
            fireLifecycle(RXDrawerEvent.CLOSED, getSkinnable().getActiveCloseReason());
        }
    }

    // ==================== Focus (a11y) ====================

    /**
     * Captures the current focus owner and moves focus into the drawer, only when
     * modal (a non-modal drawer leaves the page interactive and does not steal focus).
     */
    private void moveFocusIntoDrawer() {
        if (!overlayPaneActive()) {
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
        // Discovery order: first focusable in drawerContent → close button → the panel.
        Node inContent = firstFocusableIn(getSkinnable().getDrawerContent());
        if (inContent != null) {
            return inContent;
        }
        if (header.getChildren().contains(closeButton)) {
            return closeButton;
        }
        return drawerPane;
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
        if (event.getCode() != KeyCode.TAB || !overlayPaneActive() || !getSkinnable().isShowing()) {
            return;
        }
        Scene scene = getSkinnable().getScene();
        if (scene == null) {
            return;
        }
        List<Node> focusables = new ArrayList<>();
        for (Node child : drawerPane.getChildrenUnmodifiable()) {
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
            drawerPane.setTranslateX(0.0);
            drawerPane.setTranslateY(0.0);
            progress.set(open ? 1.0 : 0.0);
        } else {
            double target = open ? 0.0 : closedTranslate();
            if (isHorizontal()) {
                drawerPane.setTranslateX(target);
                drawerPane.setTranslateY(0.0);
            } else {
                drawerPane.setTranslateY(target);
                drawerPane.setTranslateX(0.0);
            }
        }
        applyOverlayPaneRest(open);
    }

    private RXDrawerMode drawerModeOrDefault() {
        RXDrawerMode mode = getSkinnable().getDrawerMode();
        return mode == null ? RXDrawerPane.DEFAULT_DRAWER_MODE : mode;
    }

    private boolean isPush() {
        return drawerModeOrDefault() == RXDrawerMode.PUSH;
    }

    /**
     * Whether the overlay pane participates: only when overlaying with the pane
     * enabled. PUSH is never modal.
     */
    private boolean overlayPaneActive() {
        return drawerModeOrDefault() == RXDrawerMode.OVERLAY && getSkinnable().isOverlayPaneVisible();
    }

    /**
     * Settles the overlay pane to its resting pose: opaque (its dim comes from the
     * {@code .overlay-pane} CSS background colour) and pickable when open and active,
     * fully transparent and click-through otherwise.
     *
     * @param open whether the drawer rests open
     */
    private void applyOverlayPaneRest(boolean open) {
        if (open && overlayPaneActive()) {
            overlayPane.setVisible(true);
            overlayPane.setMouseTransparent(false);
            overlayPane.setOpacity(1.0);
        } else {
            overlayPane.setVisible(false);
            overlayPane.setMouseTransparent(true);
            overlayPane.setOpacity(0.0);
        }
    }

    private void onOverlayPaneChanged() {
        // An overlayPaneVisible change re-settles the resting pose unless an animation
        // currently owns the pane opacity.
        if (!isAnimationRunning()) {
            applyOverlayPaneRest(getSkinnable().isShowing());
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
