package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDualPane;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import io.github.leewyatt.rxcontrols.internal.transition.TransitionPages;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.scene.Node;
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
 * <p>The skin takes the faces over when it is installed on the control, not
 * when it is constructed. Mouse events on the faces reach the pane's parent,
 * as with other content containers.</p>
 */
public class RXDualPaneSkin extends RXSkinBase<RXDualPane> {

    private final TransitionPages pages = new TransitionPages("page", true);

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
        pages.layout(x, y, w, h);
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
        StackPane contentPane = pages.getContentPane();
        return contentPane.getLayoutBounds().getMinY() + contentPane.getLayoutY()
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
        getChildren().add(pages.getContentPane());
        setPageContent(pages.getPageA(), control.getFirstContent());
        setPageContent(pages.getPageB(), control.getSecondContent());
        if (control.isShowingSecond()) {
            pages.directCutTo(pages.getPageB());
        }

        disposer.registerListener(control.showingSecondProperty(),
                (observable, oldValue, newValue) -> showFace(newValue));
        disposer.registerListener(control.firstContentProperty(),
                () -> setPageContent(pages.getPageA(), control.getFirstContent()));
        disposer.registerListener(control.secondContentProperty(),
                () -> setPageContent(pages.getPageB(), control.getSecondContent()));
    }

    // ==================== Transitions ====================

    private void showFace(boolean second) {
        pages.interrupt();

        RXDualPane control = getSkinnable();
        PageAnimation animation = control.getAnimation();
        TransitionDirection direction =
                second ? TransitionDirection.FORWARD : TransitionDirection.BACKWARD;
        Duration duration = control.getAnimationDuration();

        pages.clearEffectsIfChanged(animation, direction, duration);

        StackPane target = second ? pages.getPageB() : pages.getPageA();
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
            control.setTransitioning(false);
            return;
        }

        pages.transitionTo(target, animation, direction, duration,
                () -> control.setTransitioning(true),
                () -> control.setTransitioning(false),
                () -> control.setTransitioning(false));
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
        getChildren().remove(pages.getContentPane());
    }
}
