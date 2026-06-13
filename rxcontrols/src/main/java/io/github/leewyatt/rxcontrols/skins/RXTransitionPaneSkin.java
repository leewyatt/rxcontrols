package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTransitionPane;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import io.github.leewyatt.rxcontrols.internal.transition.TransitionPages;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Skin for {@link RXTransitionPane}.
 *
 * <p>Two page wrappers are reused for all content transitions: the current
 * page shows (or is animating in) the current content, the spare page is the
 * off-stage node reused for the next transition. Wrapping isolates the
 * animation's visual side effects (translates, visibility, transforms) from
 * the user's content nodes. The double-page surface and transition glue live
 * in the shared {@link TransitionPages}.</p>
 */
public class RXTransitionPaneSkin extends RXSkinBase<RXTransitionPane> {

    private final TransitionPages pages = new TransitionPages("page");

    private final ChangeListener<Node> contentListener =
            (observable, oldValue, newValue) -> onContentChanged(oldValue, newValue);

    /**
     * Creates the skin for the given control.
     *
     * @param control the control
     */
    public RXTransitionPaneSkin(RXTransitionPane control) {
        super(control);

        getChildren().add(pages.getContentPane());
        setPageContent(pages.getCurrentPage(), control.getContent());

        disposer.registerListener(control.contentProperty(), contentListener);
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
        return leftInset + pages.getCurrentPage().prefWidth(h) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        double w = (width < 0) ? -1 : Math.max(0.0, width - leftInset - rightInset);
        return topInset + pages.getCurrentPage().prefHeight(w) + bottomInset;
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
        double w = (width < 0) ? -1 : Math.max(0.0, width - leftInset - rightInset);
        return topInset + pages.getCurrentPage().minHeight(w) + bottomInset;
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

    // ==================== Transitions ====================

    private void onContentChanged(Node oldContent, Node newContent) {
        pages.interrupt();

        RXTransitionPane control = getSkinnable();
        PageAnimation animation = control.getAnimation();
        TransitionDirection direction = directionOrDefault(control);
        Duration duration = control.getAnimationDuration();

        // Clear effects from the previous animation if the instance changed,
        // before deciding between animation and direct cut
        pages.clearEffectsIfChanged(animation, direction, duration);

        // Edges (first content, to/from null) switch with a direct cut
        if (oldContent == null || newContent == null
                || !PageTransitionEngine.canAnimate(animation, control.isAnimated(), 2,
                        duration, false)) {
            directCut(newContent);
            return;
        }

        StackPane target = pages.getSparePage();
        setPageContent(target, newContent);
        pages.transitionTo(target, animation, direction, duration,
                () -> control.setTransitioning(true),
                () -> {
                    control.setTransitioning(false);
                    detachReplacedContent();
                },
                () -> control.setTransitioning(false));
    }

    private void directCut(Node content) {
        setPageContent(pages.getCurrentPage(), content);
        detachReplacedContent();
        pages.directCutTo(pages.getCurrentPage());
    }

    // Detach the previous content from the off-stage wrapper, so user nodes
    // never stay parented to a hidden page.
    private void detachReplacedContent() {
        pages.getSparePage().getChildren().clear();
    }

    private static void setPageContent(StackPane page, Node content) {
        if (content == null) {
            page.getChildren().clear();
        } else {
            page.getChildren().setAll(content);
        }
    }

    private static TransitionDirection directionOrDefault(RXTransitionPane control) {
        TransitionDirection direction = control.getDirection();
        return direction == null ? TransitionDirection.FORWARD : direction;
    }

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        RXTransitionPane control = getSkinnable();
        pages.dispose(control == null ? null : control.getAnimation());
        if (control != null) {
            control.setTransitioning(false);
        }
        getChildren().clear();
    }
}
