package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXLrcLineView;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import io.github.leewyatt.rxcontrols.lrc.RXLrcDocument;
import javafx.beans.value.ChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

/**
 * Skin for {@link RXLrcLineView}.
 *
 * <p>Two page wrappers are reused for all line transitions: the current page
 * shows (or is animating in) the current line, the spare page is the off-stage
 * node reused for the next transition. Transitions are driven by the shared
 * {@link PageTransitionEngine}, which carries the interrupt and cleanup
 * handling common to all {@link PageAnimation} hosts.</p>
 */
public class RXLrcLineViewSkin extends RXSkinBase<RXLrcLineView> {

    // ==================== Nodes ====================

    private final StackPane contentPane = new StackPane();
    private final Rectangle clip = new Rectangle();

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

    /**
     * Document the page texts were derived from; detects document swaps in the
     * current-line listener, which fires before the skin's document listener.
     */
    private RXLrcDocument shownDocument;

    // ==================== Listeners ====================

    private final ChangeListener<Number> currentLineIndexListener =
            (observable, oldValue, newValue) -> onCurrentLineIndexChanged(
                    oldValue.intValue(), newValue.intValue());
    private final ChangeListener<RXLrcDocument> documentListener =
            (observable, oldValue, newValue) -> onDocumentChanged();
    private final ChangeListener<Node> placeholderListener =
            (observable, oldValue, newValue) -> onPlaceholderChanged(oldValue, newValue);

    // ==================== Constructors ====================

    /**
     * Creates the skin for the given control.
     *
     * @param control the control
     */
    public RXLrcLineViewSkin(RXLrcLineView control) {
        super(control);

        contentPane.getStyleClass().add("content-pane");
        contentPane.setClip(clip);

        currentPage = pageA;
        currentLabel = labelA;
        sparePage = pageB;
        spareLabel = labelB;
        contentPane.getChildren().add(currentPage);
        getChildren().add(contentPane);
        installPlaceholder(control.getPlaceholder());

        shownDocument = control.getDocument();
        currentLabel.setText(textAt(control.getCurrentLineIndex()));

        disposer.registerListener(control.currentLineIndexProperty(), currentLineIndexListener);
        disposer.registerListener(control.documentProperty(), documentListener);
        disposer.registerListener(control.placeholderProperty(), placeholderListener);
        disposer.registerDisposeTask(() -> contentPane.setClip(null));

        updatePlaceholderState();
    }

    private static StackPane createPage(Label label) {
        StackPane page = new StackPane(label);
        page.getStyleClass().add("line");
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
        layoutPlaceholder(x, y, width, height);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + sizingNode().prefWidth(-1) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + sizingNode().prefHeight(-1) + bottomInset;
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
        return topInset + sizingNode().minHeight(-1) + bottomInset;
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

    // The spare page may still hold the previous line; size only by the page
    // that represents the current state (or the placeholder when empty).
    private Node sizingNode() {
        Node placeholder = getSkinnable().getPlaceholder();
        if (isDocumentEmpty() && placeholder != null) {
            return placeholder;
        }
        return currentPage;
    }

    // ==================== Current Line ====================

    private void onCurrentLineIndexChanged(int oldIndex, int newIndex) {
        if (getSkinnable().getDocument() != shownDocument) {
            // Document swap: refresh without animation. The control updates the
            // line index inside the document property's invalidated(), so this
            // listener fires before the skin's document listener.
            refreshDirect();
            return;
        }
        showLine(newIndex, newIndex > oldIndex ? TransitionDirection.FORWARD : TransitionDirection.BACKWARD);
    }

    private void onDocumentChanged() {
        updatePlaceholderState();
        if (getSkinnable().getDocument() != shownDocument) {
            refreshDirect();
        }
    }

    private void refreshDirect() {
        engine.interrupt();
        shownDocument = getSkinnable().getDocument();
        directCut(textAt(getSkinnable().getCurrentLineIndex()));
    }

    private String textAt(int index) {
        RXLrcDocument document = getSkinnable().getDocument();
        if (document == null || index < 0 || index >= document.lines().size()) {
            return "";
        }
        return document.lines().get(index).text();
    }

    // ==================== Transitions ====================

    private void showLine(int index, TransitionDirection direction) {
        engine.interrupt();

        RXLrcLineView control = getSkinnable();
        String text = textAt(index);
        PageAnimation animation = control.getAnimation();

        // Clear effects from the previous animation if the instance changed,
        // before deciding between animation and direct cut (as CarouselSkin does)
        engine.clearEffectsIfChanged(animation,
                () -> buildContext(currentPage, sparePage, direction));

        if (!PageTransitionEngine.canAnimate(animation, control.isAnimated(), 2,
                control.getAnimationDuration(), false)) {
            directCut(text);
            return;
        }

        StackPane outgoing = currentPage;
        Label outgoingLabel = currentLabel;
        currentPage = sparePage;
        currentLabel = spareLabel;
        sparePage = outgoing;
        spareLabel = outgoingLabel;

        currentLabel.setText(text);
        outgoing.setVisible(true);
        if (!contentPane.getChildren().contains(currentPage)) {
            currentPage.setVisible(false);
            contentPane.getChildren().add(currentPage);
        }

        TransitionContext context = buildContext(outgoing, currentPage, direction);
        engine.play(animation, context, null, this::hideNonCurrentPages, null);
    }

    private void directCut(String text) {
        currentLabel.setText(text);
        currentPage.setVisible(true);
        hideNonCurrentPages();
    }

    private void hideNonCurrentPages() {
        // Keep a single child while idle; the spare page is re-added on the
        // next transition. Animation finish actions have already removed
        // their own effect nodes and reset page visual properties.
        contentPane.getChildren().removeIf(child -> child != currentPage);
    }

    private TransitionContext buildContext(Node outgoing, Node incoming, TransitionDirection direction) {
        return new TransitionContext(
                outgoing, incoming,
                0, 1, 2,
                direction, getSkinnable().getAnimationDuration(),
                contentPane,
                index -> index == 0 ? outgoing : incoming,
                TransitionContext.LifecycleCallback.NOOP);
    }

    // ==================== Placeholder ====================

    private void onPlaceholderChanged(Node oldValue, Node newValue) {
        if (oldValue != null) {
            getChildren().remove(oldValue);
        }
        installPlaceholder(newValue);
        updatePlaceholderState();
        getSkinnable().requestLayout();
    }

    private void installPlaceholder(Node placeholder) {
        if (placeholder == null) {
            return;
        }
        if (!placeholder.getStyleClass().contains("placeholder")) {
            placeholder.getStyleClass().add("placeholder");
        }
        if (!getChildren().contains(placeholder)) {
            getChildren().add(placeholder);
        }
    }

    private void updatePlaceholderState() {
        Node placeholder = getSkinnable().getPlaceholder();
        boolean empty = isDocumentEmpty();
        if (placeholder != null) {
            placeholder.setVisible(empty);
            placeholder.setManaged(empty);
        }
        contentPane.setVisible(!empty);
    }

    private void layoutPlaceholder(double x, double y, double width, double height) {
        Node placeholder = getSkinnable().getPlaceholder();
        if (placeholder == null || !placeholder.isVisible()) {
            return;
        }
        layoutInArea(placeholder, x, y, width, height, 0.0, HPos.CENTER, VPos.CENTER);
    }

    private boolean isDocumentEmpty() {
        RXLrcDocument document = getSkinnable().getDocument();
        return document == null || document.isEmpty();
    }

    // ==================== Dispose ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected void disposeSkin() {
        RXLrcLineView control = getSkinnable();
        engine.dispose(control == null ? null : control.getAnimation());
        contentPane.getChildren().clear();
        getChildren().clear();
    }
}
