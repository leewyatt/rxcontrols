package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTransitionLabel;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

/**
 * Skin for {@link RXTransitionLabel}.
 *
 * <p>Two label pages are reused for all text transitions: the current page
 * shows (or is animating in) the current text, the spare page is the
 * off-stage node reused for the next transition. Transitions are driven by
 * the shared {@link PageTransitionEngine}.</p>
 */
public class RXTransitionLabelSkin extends RXSkinBase<RXTransitionLabel> {

    // ==================== Nodes ====================

    private final StackPane contentPane = new StackPane();
    private final Rectangle clip = new Rectangle();

    // Two interchangeable page buffers with no fixed meaning; the roles
    // rotate between them and live in the currentPage/sparePage fields.
    private final Label labelA = new Label();
    private final Label labelB = new Label();
    private final StackPane pageA = createPage(labelA);
    private final StackPane pageB = createPage(labelB);

    private StackPane currentPage;
    private Label currentLabel;
    private StackPane sparePage;
    private Label spareLabel;

    // ==================== Transition state ====================

    private final PageTransitionEngine engine = new PageTransitionEngine();

    // ==================== Listeners ====================

    private final ChangeListener<String> textListener =
            (observable, oldValue, newValue) -> onTextChanged(newValue);
    private final ChangeListener<Pos> alignmentListener =
            (observable, oldValue, newValue) -> applyAlignment();

    // ==================== Constructors ====================

    /**
     * Creates the skin for the given control.
     *
     * @param control the control
     */
    public RXTransitionLabelSkin(RXTransitionLabel control) {
        super(control);

        contentPane.getStyleClass().add("content-pane");
        contentPane.setClip(clip);

        currentPage = pageA;
        currentLabel = labelA;
        sparePage = pageB;
        spareLabel = labelB;
        contentPane.getChildren().add(currentPage);
        getChildren().add(contentPane);

        currentLabel.setText(safeText(control.getText()));
        applyAlignment();

        disposer.registerListener(control.textProperty(), textListener);
        disposer.registerListener(control.alignmentProperty(), alignmentListener);
        disposer.registerDisposeTask(() -> contentPane.setClip(null));
    }

    private static StackPane createPage(Label label) {
        StackPane page = new StackPane(label);
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

    private void onTextChanged(String newText) {
        engine.interrupt();

        RXTransitionLabel control = getSkinnable();
        PageAnimation animation = control.getAnimation();
        TransitionDirection direction = directionOrDefault(control);

        // Clear effects from the previous animation if the instance changed,
        // before deciding between animation and direct cut
        engine.clearEffectsIfChanged(animation,
                () -> buildContext(currentPage, sparePage, direction));

        if (!PageTransitionEngine.canAnimate(animation, control.isAnimated(), 2,
                control.getAnimationDuration(), false)) {
            directCut(newText);
            return;
        }

        StackPane outgoing = currentPage;
        Label outgoingLabel = currentLabel;
        currentPage = sparePage;
        currentLabel = spareLabel;
        sparePage = outgoing;
        spareLabel = outgoingLabel;

        currentLabel.setText(safeText(newText));
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
                    hideNonCurrentPages();
                },
                () -> control.setTransitioning(false));
    }

    private void directCut(String text) {
        currentLabel.setText(safeText(text));
        currentPage.setVisible(true);
        hideNonCurrentPages();
    }

    // Keep a single page in the tree while idle; the spare page is re-added
    // on the next transition.
    private void hideNonCurrentPages() {
        contentPane.getChildren().removeIf(child -> child != currentPage);
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

    private void applyAlignment() {
        Pos alignment = getSkinnable().getAlignment();
        Pos resolved = alignment == null ? Pos.CENTER : alignment;
        StackPane.setAlignment(labelA, resolved);
        StackPane.setAlignment(labelB, resolved);
    }

    private static String safeText(String text) {
        return text == null ? "" : text;
    }

    private static TransitionDirection directionOrDefault(RXTransitionLabel control) {
        TransitionDirection direction = control.getDirection();
        return direction == null ? TransitionDirection.FORWARD : direction;
    }

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        RXTransitionLabel control = getSkinnable();
        engine.dispose(control == null ? null : control.getAnimation());
        if (control != null) {
            control.setTransitioning(false);
        }
        contentPane.getChildren().clear();
        getChildren().clear();
    }
}
