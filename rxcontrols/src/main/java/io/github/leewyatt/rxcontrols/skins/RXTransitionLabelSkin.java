package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTransitionLabel;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import io.github.leewyatt.rxcontrols.internal.transition.TransitionPages;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Skin for {@link RXTransitionLabel}.
 *
 * <p>Two label pages are reused for all text transitions: the current page
 * shows (or is animating in) the current text, the spare page is the
 * off-stage node reused for the next transition. The double-page surface and
 * transition glue live in the shared {@link TransitionPages}.</p>
 */
public class RXTransitionLabelSkin extends RXSkinBase<RXTransitionLabel> {

    private final TransitionPages pages = new TransitionPages("page");

    // Two interchangeable label buffers; the one currently shown is derived
    // from the surface's current-page pointer.
    private final Label labelA = new Label();
    private final Label labelB = new Label();

    private final ChangeListener<String> textListener =
            (observable, oldValue, newValue) -> onTextChanged(newValue);
    private final ChangeListener<Pos> alignmentListener =
            (observable, oldValue, newValue) -> applyAlignment();

    /**
     * Creates the skin for the given control.
     *
     * @param control the control
     */
    public RXTransitionLabelSkin(RXTransitionLabel control) {
        super(control);

        pages.getPageA().getChildren().add(labelA);
        pages.getPageB().getChildren().add(labelB);
        getChildren().add(pages.getContentPane());

        currentLabel().setText(safeText(control.getText()));
        applyAlignment();

        disposer.registerListener(control.textProperty(), textListener);
        disposer.registerListener(control.alignmentProperty(), alignmentListener);
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
        return leftInset + pages.getCurrentPage().prefWidth(-1) + rightInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + pages.getCurrentPage().prefHeight(-1) + bottomInset;
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
        return topInset + pages.getCurrentPage().minHeight(-1) + bottomInset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        // Labels do not stretch beyond their preferred size, matching
        // LabeledSkinBase: layout panes clamp to this unless the user
        // raises maxWidth explicitly.
        return getSkinnable().prefWidth(height);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    // ==================== Transitions ====================

    private void onTextChanged(String newText) {
        pages.interrupt();

        RXTransitionLabel control = getSkinnable();
        PageAnimation animation = control.getAnimation();
        TransitionDirection direction = directionOrDefault(control);
        Duration duration = control.getAnimationDuration();

        // Clear effects from the previous animation if the instance changed,
        // before deciding between animation and direct cut
        pages.clearEffectsIfChanged(animation, direction, duration);

        if (!PageTransitionEngine.canAnimate(animation, control.isAnimated(), 2,
                duration, false)) {
            directCut(newText);
            return;
        }

        spareLabel().setText(safeText(newText));
        pages.transitionTo(pages.getSparePage(), animation, direction, duration,
                () -> control.setTransitioning(true),
                () -> control.setTransitioning(false),
                () -> control.setTransitioning(false));
    }

    private void directCut(String text) {
        currentLabel().setText(safeText(text));
        pages.directCutTo(pages.getCurrentPage());
    }

    private Label currentLabel() {
        return pages.getCurrentPage() == pages.getPageA() ? labelA : labelB;
    }

    private Label spareLabel() {
        return pages.getCurrentPage() == pages.getPageA() ? labelB : labelA;
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
        pages.dispose(control == null ? null : control.getAnimation());
        if (control != null) {
            control.setTransitioning(false);
        }
        getChildren().clear();
    }
}
