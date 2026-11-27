package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDualPane;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import io.github.leewyatt.rxcontrols.internal.transition.TransitionPages;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Skin for {@link RXDualPane}.
 *
 * <p>Two page wrappers host the two persistent faces with fixed addressing:
 * the first face is always page A, the second always page B. A flip of the
 * control's {@code showingSecond} state plays a transition towards the target
 * face through the shared {@link TransitionPages} surface; the direction is
 * derived (forward to the second face, backward to the first). Unlike a
 * value-follow pane, neither face is ever detached after a flip — both nodes
 * stay parented to their own page. Replacing a face's content updates that
 * page in place without playing a transition.</p>
 *
 * <p>Every flip is processed to completion before the next one starts: a flip
 * made by a {@code transitioning} or focus listener is picked up once the
 * current step has finished. Focus inside the face being left moves to the
 * shown face, so keyboard input never reaches a hidden face.</p>
 *
 * <p>The skin takes the faces over when it is installed on the control, not
 * when it is constructed. Mouse events on the faces reach the pane's parent,
 * as with other content containers.</p>
 */
public class RXDualPaneSkin extends RXSkinBase<RXDualPane> {

    private final TransitionPages pages = new TransitionPages("page", true);

    // A step of the loop in sync() is in progress.
    private boolean busy;
    // The playing transition has ended and is still to be completed.
    private boolean settlePending;
    // Tags the playing transition, so callbacks of an ended one are ignored.
    private int transitionId;
    // The pane holds the focus taken from a face it left before the shown
    // face became visible.
    private boolean focusHandOff;
    private boolean activated;
    private boolean disposed;

    private final InvalidationListener skinListener = observable -> activate();

    /**
     * Creates the skin for the given control. The skin starts showing the
     * faces once it is installed as the control's skin.
     *
     * @param control the control
     */
    public RXDualPaneSkin(RXDualPane control) {
        super(control);
        // A container passes mouse events on its content through to the
        // parent, as ScrollPane does. Done here rather than on activation:
        // the SkinBase constructor of an ignored skin re-adds the consumer.
        consumeMouseEvents(false);

        // Weak: setSkin drops a skin of the installed class without disposing
        // it, and nothing else may keep such a skin reachable from the pane.
        WeakInvalidationListener weakSkinListener = new WeakInvalidationListener(skinListener);
        control.skinProperty().addListener(weakSkinListener);
        disposer.registerDisposeTask(() -> control.skinProperty().removeListener(weakSkinListener));
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        pages.resizeRelocate(x, y, w, h);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double h = (height < 0) ? -1 : Math.max(0.0, height - topInset - bottomInset);
        return leftInset + maxFaceWidth(false, h) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double w = (width < 0) ? -1 : Math.max(0.0, width - leftInset - rightInset);
        return topInset + maxFaceHeight(false, w) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        double h = (height < 0) ? -1 : Math.max(0.0, height - topInset - bottomInset);
        return leftInset + maxFaceWidth(true, h) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        double w = (width < 0) ? -1 : Math.max(0.0, width - leftInset - rightInset);
        return topInset + maxFaceHeight(true, w) + bottomInset;
    }

    /**
     * {@inheritDoc}
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

    /**
     * Returns the baseline of the shown face. Both pages stay managed, so the
     * default (the first managed child) would always report the first face.
     *
     * @param topInset    the pixel snapped top inset
     * @param rightInset  the pixel snapped right inset
     * @param bottomInset the pixel snapped bottom inset
     * @param leftInset   the pixel snapped left inset
     * @return the baseline offset of the shown face
     */
    @Override
    protected double computeBaselineOffset(double topInset, double rightInset,
                                           double bottomInset, double leftInset) {
        StackPane page = pages.getCurrentPage();
        double offset = page.getBaselineOffset();
        if (offset == Node.BASELINE_OFFSET_SAME_AS_HEIGHT) {
            return Node.BASELINE_OFFSET_SAME_AS_HEIGHT;
        }
        return pages.getLayoutBounds().getMinY() + pages.getLayoutY()
                + page.getLayoutBounds().getMinY() + page.getLayoutY() + offset;
    }

    // The pane keeps a stable size across the flip: both faces count. The hint
    // flows to both faces so a content-bias face (e.g. wrapText) is measured
    // against the real cross-axis extent; a non-content-bias face ignores the
    // hint and returns its natural size, so forwarding is safe.
    private double maxFaceWidth(boolean min, double height) {
        StackPane pageA = pages.getPageA();
        StackPane pageB = pages.getPageB();
        return min
                ? Math.max(pageA.minWidth(height), pageB.minWidth(height))
                : Math.max(pageA.prefWidth(height), pageB.prefWidth(height));
    }

    private double maxFaceHeight(boolean min, double width) {
        StackPane pageA = pages.getPageA();
        StackPane pageB = pages.getPageB();
        return min
                ? Math.max(pageA.minHeight(width), pageB.minHeight(width))
                : Math.max(pageA.prefHeight(width), pageB.prefHeight(width));
    }

    // ==================== Activation ====================

    // A skin is constructed before setSkin installs it, and setSkin ignores a
    // second skin of the installed class. Taking the faces over only once
    // installed keeps a skin that never gets installed from moving them into
    // its own pages or being kept reachable by the control's properties, and
    // runs after the replaced skin has been disposed.
    private void activate() {
        RXDualPane control = getSkinnable();
        if (activated || disposed || control == null || control.getSkin() != this) {
            return;
        }
        activated = true;
        getChildren().add(pages);
        setPageContent(pages.getPageA(), control.getFirstContent());
        setPageContent(pages.getPageB(), control.getSecondContent());
        if (control.isShowingSecond()) {
            pages.directCutTo(pages.getPageB());
        }

        disposer.registerListener(control.showingSecondProperty(),
                (observable, oldValue, newValue) -> sync());
        disposer.registerListener(control.firstContentProperty(),
                () -> setPageContent(pages.getPageA(), control.getFirstContent()));
        disposer.registerListener(control.secondContentProperty(),
                () -> setPageContent(pages.getPageB(), control.getSecondContent()));
    }

    // ==================== Flips ====================

    // Converges the pages onto showingSecond. Called for flips, natural
    // transition ends and deferred external stops. While a step is in
    // progress a further call returns at once: the loop re-reads
    // showingSecond after the step.
    private void sync() {
        if (busy || disposed) {
            return;
        }
        busy = true;
        try {
            while (!disposed && step()) {
                // A step notifies transitioning and focus listeners, which may flip again.
            }
        } finally {
            busy = false;
        }
    }

    // Returns false once nothing is left to do.
    private boolean step() {
        if (settlePending) {
            settle();
            return true;
        }
        StackPane target = targetPage();
        if (target == pages.getCurrentPage()) {
            return false;
        }
        if (pages.isTransitioning()) {
            // Latest wins: the stop runs the animation's finish action before
            // returning, so the transition can be completed right away. An
            // animation that ignores jumpToEnd is treated as ended as well.
            pages.interrupt();
            settlePending = true;
            return true;
        }
        show(target);
        return true;
    }

    private void show(StackPane target) {
        RXDualPane control = getSkinnable();
        PageAnimation animation = control.getAnimation();
        TransitionDirection direction = target == pages.getPageB()
                ? TransitionDirection.FORWARD : TransitionDirection.BACKWARD;
        Duration duration = control.getAnimationDuration();

        pages.clearEffectsIfChanged(animation, direction, duration);

        StackPane leaving = pages.getCurrentPage();
        // The baseline follows the shown face; a baseline-aligned parent
        // must lay the pane out again.
        control.requestLayout();
        // Edges (either face null) switch with a direct cut; the duration is
        // fed raw to the engine gate (null / non-positive / zero -> direct
        // cut, no default fallback).
        boolean animate = control.getFirstContent() != null
                && control.getSecondContent() != null
                && PageTransitionEngine.canAnimate(animation, control.isAnimated(), 2,
                        duration, false);
        if (!animate) {
            pages.directCutTo(target);
            moveFocusOutOf(leaving);
            return;
        }

        int id = ++transitionId;
        pages.transitionTo(target, animation, direction, duration,
                () -> onTransitionStarted(id, leaving),
                () -> onTransitionFinished(id),
                () -> onTransitionStopped(id));
    }

    // Runs once the animation plays, so the incoming page has usually been
    // made visible by then.
    private void onTransitionStarted(int id, StackPane leaving) {
        if (id != transitionId || disposed) {
            return;
        }
        getSkinnable().setTransitioning(true);
        if (!disposed) {
            moveFocusOutOf(leaving);
        }
    }

    // Natural end: in a pulse, or inside play() for a zero-length animation,
    // where the loop completes it after the current step.
    private void onTransitionFinished(int id) {
        if (id != transitionId || disposed) {
            return;
        }
        settlePending = true;
        sync();
    }

    // External stop: runs inside Animation.stop(), before the animation's
    // finish action restores the pages, so the completion must wait. A stop
    // made during a step is completed by the loop right after the step.
    private void onTransitionStopped(int id) {
        if (id != transitionId || disposed) {
            return;
        }
        settlePending = true;
        if (!busy) {
            Platform.runLater(this::sync);
        }
    }

    // Completes an ended transition: only the incoming page stays, then
    // transitioning turns false.
    private void settle() {
        settlePending = false;
        transitionId++;
        pages.directCutTo(pages.getCurrentPage());
        getSkinnable().setTransitioning(false);
        if (focusHandOff && !disposed && pages.getCurrentPage() == targetPage()) {
            focusHandOff = false;
            if (focusOwner() == getSkinnable()) {
                focusShownFace();
            }
        }
    }

    private StackPane targetPage() {
        return getSkinnable().isShowingSecond() ? pages.getPageB() : pages.getPageA();
    }

    // Both faces are persistent slots: replace the face's node in place, never
    // detach the spare like a value-follow pane would.
    private static void setPageContent(StackPane page, Node content) {
        if (content == null) {
            page.getChildren().clear();
        } else {
            page.getChildren().setAll(content);
        }
    }

    // ==================== Focus ====================

    // Both faces stay in the scene and a visibility change does not move the
    // focus, so typing would edit a face nobody sees. Focus inside the face
    // being left moves to the first focusable node of the shown face, or to
    // the pane while that face is not visible yet (some animations reveal it
    // late, a zero-length one only when it settles); the pane passes it on
    // once the face shows. Focus elsewhere is left alone.
    private void moveFocusOutOf(StackPane leaving) {
        RXDualPane control = getSkinnable();
        Node owner = focusOwner();
        boolean inLeavingFace = owner != null && isInside(owner, leaving);
        boolean heldForHandOff = focusHandOff && owner == control;
        focusHandOff = false;
        if (!inLeavingFace && !heldForHandOff) {
            return;
        }
        if (!pages.getCurrentPage().isVisible()) {
            focusHandOff = true;
            control.requestFocus();
            return;
        }
        focusShownFace();
    }

    private void focusShownFace() {
        RXDualPane control = getSkinnable();
        Node face = pages.getCurrentPage() == pages.getPageB()
                ? control.getSecondContent() : control.getFirstContent();
        Node target = firstFocusableIn(face);
        (target == null ? control : target).requestFocus();
    }

    private Node focusOwner() {
        Scene scene = getSkinnable().getScene();
        return scene == null ? null : scene.getFocusOwner();
    }

    private static boolean isInside(Node node, Node ancestor) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
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

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        disposed = true;
        RXDualPane control = getSkinnable();
        if (!activated || control == null) {
            // Never installed: the animation belongs to the installed skin.
            pages.dispose(null);
            return;
        }
        pages.dispose(control.getAnimation());
        // The faces belong to the control: release them, or they would keep
        // this skin's pages reachable until they are placed elsewhere. Nodes
        // already taken by another parent are no longer in these lists.
        pages.getPageA().getChildren().clear();
        pages.getPageB().getChildren().clear();
        control.setTransitioning(false);
        getChildren().remove(pages);
    }
}
