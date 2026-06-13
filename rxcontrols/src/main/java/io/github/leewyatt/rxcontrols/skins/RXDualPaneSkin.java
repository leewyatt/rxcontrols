package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDualPane;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import io.github.leewyatt.rxcontrols.internal.transition.TransitionPages;
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
 */
public class RXDualPaneSkin extends RXSkinBase<RXDualPane> {

    private final TransitionPages pages = new TransitionPages("page", true);

    /**
     * Creates the skin for the given control.
     *
     * @param control the control
     */
    public RXDualPaneSkin(RXDualPane control) {
        super(control);

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
        return leftInset + maxFaceWidth(false) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + maxFaceHeight(false) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + maxFaceWidth(true) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + maxFaceHeight(true) + bottomInset;
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

    // The pane keeps a stable size across the flip: both faces count.
    private double maxFaceWidth(boolean min) {
        StackPane pageA = pages.getPageA();
        StackPane pageB = pages.getPageB();
        return min
                ? Math.max(pageA.minWidth(-1), pageB.minWidth(-1))
                : Math.max(pageA.prefWidth(-1), pageB.prefWidth(-1));
    }

    private double maxFaceHeight(boolean min) {
        StackPane pageA = pages.getPageA();
        StackPane pageB = pages.getPageB();
        return min
                ? Math.max(pageA.minHeight(-1), pageB.minHeight(-1))
                : Math.max(pageA.prefHeight(-1), pageB.prefHeight(-1));
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
        RXDualPane control = getSkinnable();
        pages.dispose(control == null ? null : control.getAnimation());
        if (control != null) {
            control.setTransitioning(false);
        }
        getChildren().clear();
    }
}
