package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTransitionLabel;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import io.github.leewyatt.rxcontrols.internal.transition.TransitionPages;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
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
 *
 * <p>Every text change is processed to completion before the next one
 * starts: a change made by a {@code transitioning} listener is picked up once
 * the current step has finished. The skin takes the control over when it is
 * installed on the control, not when it is constructed.</p>
 */
public class RXTransitionLabelSkin extends RXSkinBase<RXTransitionLabel> {

    private final TransitionPages pages = new TransitionPages("page");

    // Two interchangeable label buffers; the one currently shown is derived
    // from the surface's current-page pointer.
    private final Label labelA = new Label();
    private final Label labelB = new Label();

    // A step of the loop in sync() is in progress.
    private boolean busy;
    // The playing transition has ended and is still to be completed.
    private boolean settlePending;
    // Tags the playing transition, so callbacks of an ended one are ignored.
    private int transitionId;
    private boolean activated;
    private boolean disposed;

    private final InvalidationListener skinListener = observable -> activate();
    private final ChangeListener<String> textListener =
            (observable, oldValue, newValue) -> sync();
    private final ChangeListener<Pos> alignmentListener =
            (observable, oldValue, newValue) -> applyAlignment();

    /**
     * Creates the skin for the given control. The skin starts showing the
     * text once it is installed as the control's skin.
     *
     * @param control the control
     */
    public RXTransitionLabelSkin(RXTransitionLabel control) {
        super(control);
        // Mouse events pass through to the parent, as with Label
        consumeMouseEvents(false);

        pages.getPageA().getChildren().add(labelA);
        pages.getPageB().getChildren().add(labelB);

        // Weak: setSkin drops a skin of the installed class without disposing
        // it, and nothing else may keep such a skin reachable from the label.
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

    // ==================== Activation ====================

    // A skin is constructed before setSkin installs it, and setSkin ignores a
    // second skin of the installed class. Taking the control over only once
    // installed keeps a skin that never gets installed from adding a second
    // text or being kept reachable by the control's properties, and runs
    // after the replaced skin has been disposed.
    private void activate() {
        RXTransitionLabel control = getSkinnable();
        if (activated || disposed || control == null || control.getSkin() != this) {
            return;
        }
        activated = true;
        getChildren().add(pages.getContentPane());
        currentLabel().setText(safeText(control.getText()));
        applyAlignment();

        // Bind both pages so whichever is current or spare wraps consistently;
        // the control's wrapText is the single source of the labels' wrapText.
        disposer.registerBinding(labelA.wrapTextProperty(), control.wrapTextProperty());
        disposer.registerBinding(labelB.wrapTextProperty(), control.wrapTextProperty());

        disposer.registerListener(control.textProperty(), textListener);
        disposer.registerListener(control.alignmentProperty(), alignmentListener);
    }

    // ==================== Text Changes ====================

    // Converges the pages onto the current text. Called for text changes,
    // natural transition ends and deferred external stops. While a step is in
    // progress a further call returns at once: the loop re-reads the text
    // after the step.
    private void sync() {
        if (busy || disposed) {
            return;
        }
        busy = true;
        try {
            while (!disposed && step()) {
                // A step notifies transitioning listeners, which may change the text.
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
        String target = safeText(getSkinnable().getText());
        if (target.equals(currentLabel().getText())) {
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

    private void show(String text) {
        RXTransitionLabel control = getSkinnable();
        PageAnimation animation = control.getAnimation();
        TransitionDirection direction = directionOrDefault(control);
        Duration duration = control.getAnimationDuration();

        // Clear effects from the previous animation if the instance changed,
        // before deciding between animation and direct cut
        pages.clearEffectsIfChanged(animation, direction, duration);

        if (!PageTransitionEngine.canAnimate(animation, control.isAnimated(), 2,
                duration, false)) {
            currentLabel().setText(text);
            pages.directCutTo(pages.getCurrentPage());
            return;
        }

        spareLabel().setText(text);
        int id = ++transitionId;
        pages.transitionTo(pages.getSparePage(), animation, direction, duration,
                () -> control.setTransitioning(true),
                () -> onTransitionFinished(id),
                () -> onTransitionStopped(id));
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
        disposed = true;
        RXTransitionLabel control = getSkinnable();
        if (!activated || control == null) {
            // Never installed: the animation belongs to the installed skin.
            pages.dispose(null);
            return;
        }
        pages.dispose(control.getAnimation());
        control.setTransitioning(false);
        getChildren().remove(pages.getContentPane());
    }
}
