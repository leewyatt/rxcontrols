package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTransitionPane;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

/**
 * Skin for {@link RXTransitionPane}.
 *
 * <p>Two page wrappers are reused for all content transitions: the current
 * page shows (or is animating in) the current content, the spare page is the
 * off-stage node reused for the next transition. Wrapping isolates the
 * animation's visual side effects (translates, visibility, transforms) from
 * the user's content nodes. Transitions are driven by the shared
 * {@link PageTransitionEngine}.</p>
 */
public class RXTransitionPaneSkin extends RXSkinBase<RXTransitionPane> {

    // ==================== Nodes ====================

    private final StackPane contentPane = new StackPane();
    private final Rectangle clip = new Rectangle();

    private final StackPane pageA = createPage();
    private final StackPane pageB = createPage();

    private StackPane currentPage;
    private StackPane sparePage;

    // ==================== Transition state ====================

    private final PageTransitionEngine engine = new PageTransitionEngine();

    // ==================== Listeners ====================

    private final ChangeListener<Node> contentListener =
            (observable, oldValue, newValue) -> onContentChanged(oldValue, newValue);

    // ==================== Constructors ====================

    /**
     * Creates the skin for the given control.
     *
     * @param control the control
     */
    public RXTransitionPaneSkin(RXTransitionPane control) {
        super(control);

        contentPane.getStyleClass().add("content-pane");
        contentPane.setClip(clip);

        currentPage = pageA;
        sparePage = pageB;
        contentPane.getChildren().add(currentPage);
        getChildren().add(contentPane);

        setPageContent(currentPage, control.getContent());

        disposer.registerListener(control.contentProperty(), contentListener);
        disposer.registerDisposeTask(() -> contentPane.setClip(null));
    }

    private static StackPane createPage() {
        StackPane page = new StackPane();
        page.getStyleClass().add("page");
        return page;
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        double width = Math.max(0.0, w);
        double height = Math.max(0.0, h);
        contentPane.resizeRelocate(x, y, width, height);
        clip.setWidth(width);
        clip.setHeight(height);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + currentPage.prefWidth(-1) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + currentPage.prefHeight(-1) + bottomInset;
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
        return topInset + currentPage.minHeight(-1) + bottomInset;
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
        engine.interrupt();

        RXTransitionPane control = getSkinnable();
        PageAnimation animation = control.getAnimation();
        TransitionDirection direction = directionOrDefault(control);

        // Clear effects from the previous animation if the instance changed,
        // before deciding between animation and direct cut
        engine.clearEffectsIfChanged(animation,
                () -> buildContext(currentPage, sparePage, direction));

        // Edges (first content, to/from null) switch with a direct cut
        if (oldContent == null || newContent == null
                || !PageTransitionEngine.canAnimate(animation, control.isAnimated(), 2,
                        control.getAnimationDuration(), false)) {
            directCut(newContent);
            return;
        }

        StackPane outgoing = currentPage;
        currentPage = sparePage;
        sparePage = outgoing;

        setPageContent(currentPage, newContent);
        outgoing.setVisible(true);
        if (!contentPane.getChildren().contains(currentPage)) {
            currentPage.setVisible(false);
            contentPane.getChildren().add(currentPage);
        }

        TransitionContext context = buildContext(outgoing, currentPage, direction);
        engine.play(animation, context,
                () -> control.setTransitioning(true),
                () -> {
                    control.setTransitioning(false);
                    settleIdle();
                },
                () -> control.setTransitioning(false));
    }

    private void directCut(Node content) {
        setPageContent(currentPage, content);
        currentPage.setVisible(true);
        settleIdle();
    }

    // Keep a single page in the tree while idle and detach the previous
    // content from the spare wrapper, so user nodes never stay parented to
    // an off-stage wrapper.
    private void settleIdle() {
        sparePage.getChildren().clear();
        contentPane.getChildren().removeIf(child -> child != currentPage);
    }

    private static void setPageContent(StackPane page, Node content) {
        if (content == null) {
            page.getChildren().clear();
        } else {
            page.getChildren().setAll(content);
        }
    }

    private TransitionContext buildContext(Node outgoing, Node incoming,
                                           TransitionDirection direction) {
        return new TransitionContext(
                outgoing, incoming,
                0, 1, 2,
                direction, getSkinnable().getAnimationDuration(),
                contentPane,
                index -> index == 0 ? outgoing : incoming,
                TransitionContext.LifecycleCallback.NOOP);
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
        engine.dispose(control == null ? null : control.getAnimation());
        if (control != null) {
            control.setTransitioning(false);
        }
        contentPane.getChildren().clear();
        getChildren().clear();
    }
}
