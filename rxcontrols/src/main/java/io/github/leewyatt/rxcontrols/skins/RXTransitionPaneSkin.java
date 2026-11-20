package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTransitionPane;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.event.RXTransitionPaneEvent;
import io.github.leewyatt.rxcontrols.internal.transition.PageTransitionEngine;
import io.github.leewyatt.rxcontrols.internal.transition.TransitionPages;
import javafx.application.Platform;
import javafx.event.EventType;
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
 *
 * <p>The skin fires the pane's content lifecycle events. Every content change
 * is processed to completion before the next one starts: a change made by an
 * event handler or a {@code transitioning} listener is picked up once the
 * current step has fired its events. The skin takes over the content when it
 * is installed on the pane, not when it is constructed.</p>
 */
public class RXTransitionPaneSkin extends RXSkinBase<RXTransitionPane> {

    private final TransitionPages pages = new TransitionPages("page");
    private final ContentLifecycleLedger ledger;

    // The node in the current page: shown, opening, or animating in.
    private Node shown;
    // The node animating out of the spare page while a transition plays.
    private Node leaving;
    // The playing transition has ended and its CLOSED / OPENED are still due.
    private boolean settlePending;
    // Tags the playing transition, so callbacks of an ended one are ignored.
    private int transitionId;
    private boolean activated;
    private boolean disposed;

    /**
     * Creates the skin for the given control. The skin starts showing the
     * content once it is installed as the control's skin.
     *
     * @param control the control
     */
    public RXTransitionPaneSkin(RXTransitionPane control) {
        super(control);
        ledger = ContentLifecycleLedger.of(control);

        disposer.registerListener(control.contentProperty(),
                (observable, oldValue, newValue) -> sync());
        disposer.registerListener(control.skinProperty(), this::activate);
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

    // ==================== Activation ====================

    // A skin is constructed before setSkin installs it, and setSkin ignores a
    // second skin of the installed class. Taking the content over only once
    // installed keeps a skin that never gets installed from stealing the
    // content nodes or firing events, and runs after the replaced skin has
    // been disposed.
    private void activate() {
        RXTransitionPane control = getSkinnable();
        if (activated || disposed || control == null || control.getSkin() != this) {
            return;
        }
        activated = true;
        ledger.activate(this, this::sync);
        getChildren().add(pages.getContentPane());
        sync();
    }

    // ==================== Content Changes ====================

    // Converges the pages and the lifecycle onto the current content. Called
    // for content changes, natural transition ends, deferred external stops
    // and activation. While a step is in progress, a further call only
    // records that the content changed; the loop then re-reads it.
    private void sync() {
        if (!activated || disposed) {
            return;
        }
        if (ledger.isBusy()) {
            ledger.requestSync();
            return;
        }
        ledger.enter();
        try {
            while (!disposed && step()) {
                // Each step fires events, which may change the content again.
            }
        } finally {
            ledger.exit();
        }
    }

    // Returns false once nothing is left to do.
    private boolean step() {
        ledger.clearSyncRequest();
        if (settlePending) {
            settle();
            return true;
        }
        Node target = getSkinnable().getContent();
        if (pages.isTransitioning()) {
            if (target == shown) {
                return false;
            }
            // Latest wins: the stop runs the animation's finish action before
            // returning, so the transition can be completed right away. An
            // animation that ignores jumpToEnd is treated as ended as well.
            pages.interrupt();
            settlePending = true;
            return true;
        }
        if (target == shown) {
            return false;
        }
        Node from = shown;
        if (from != null && !announce(RXTransitionPaneEvent.CLOSING, from)) {
            return true;
        }
        proceedFrom(from);
        return true;
    }

    // `from` has received CLOSING (or is null) and still shows in the current
    // page. Opens the latest content: animated from `from` when possible,
    // otherwise by closing `from` first and cutting to the content.
    private void proceedFrom(Node from) {
        RXTransitionPane control = getSkinnable();
        while (true) {
            Node target = control.getContent();
            PageAnimation animation = control.getAnimation();
            TransitionDirection direction = directionOrDefault(control);
            Duration duration = control.getAnimationDuration();

            // Clear effects from the previous animation if the instance
            // changed, before deciding between animation and direct cut
            pages.clearEffectsIfChanged(animation, direction, duration);

            // Edges (first content, to/from null) switch with a direct cut
            boolean animate = from != null && target != null && target != from
                    && PageTransitionEngine.canAnimate(animation, control.isAnimated(), 2,
                            duration, false);
            if (!animate) {
                if (from != null) {
                    StackPane page = pages.getCurrentPage();
                    page.setVisible(false);
                    shown = null;
                    if (!announce(RXTransitionPaneEvent.CLOSED, from)) {
                        return;
                    }
                    page.getChildren().remove(from);
                }
                openDirect();
                return;
            }

            StackPane spare = pages.getSparePage();
            spare.getChildren().setAll(target);
            pages.attachHidden(spare);
            if (!announce(RXTransitionPaneEvent.OPENING, target)) {
                return;
            }
            if (control.getContent() != target) {
                // Replaced before the animation started: never show it, and
                // try again from the same outgoing node.
                if (!abandon(spare, target)) {
                    return;
                }
                continue;
            }
            play(from, target, animation, direction, duration);
            return;
        }
    }

    // Nothing is shown. Cuts to the latest content.
    private void openDirect() {
        RXTransitionPane control = getSkinnable();
        StackPane page = pages.getCurrentPage();
        while (true) {
            Node target = control.getContent();
            if (target == null) {
                page.getChildren().clear();
                pages.directCutTo(page);
                return;
            }
            page.getChildren().setAll(target);
            page.setVisible(false);
            shown = target;
            if (!announce(RXTransitionPaneEvent.OPENING, target)) {
                return;
            }
            if (control.getContent() != target) {
                shown = null;
                if (!abandon(page, target)) {
                    return;
                }
                continue;
            }
            pages.directCutTo(page);
            announce(RXTransitionPaneEvent.OPENED, target);
            return;
        }
    }

    // Completes the pairs of a node that received OPENING but was replaced
    // before it showed, then detaches it.
    private boolean abandon(StackPane page, Node node) {
        if (!announce(RXTransitionPaneEvent.OPENED, node)
                || !announce(RXTransitionPaneEvent.CLOSING, node)
                || !announce(RXTransitionPaneEvent.CLOSED, node)) {
            return false;
        }
        page.getChildren().remove(node);
        return true;
    }

    private void play(Node from, Node target, PageAnimation animation,
                      TransitionDirection direction, Duration duration) {
        RXTransitionPane control = getSkinnable();
        leaving = from;
        shown = target;
        int id = ++transitionId;
        pages.transitionTo(pages.getSparePage(), animation, direction, duration,
                () -> control.setTransitioning(true),
                () -> onTransitionFinished(id),
                () -> onTransitionStopped(id));
    }

    // Natural end: in a pulse, or inside play() for a zero-length animation.
    // TransitionPages removes the outgoing page only after this returns, so
    // the outgoing node can still bubble its CLOSED to the pane.
    private void onTransitionFinished(int id) {
        if (id != transitionId || disposed) {
            return;
        }
        settlePending = true;
        if (ledger.isBusy()) {
            settle();
        } else {
            sync();
        }
    }

    // External stop: runs inside Animation.stop(), before the animation's
    // finish action restores the pages, so the completion must wait. A stop
    // the loop itself made is completed by the loop right after.
    private void onTransitionStopped(int id) {
        if (id != transitionId || disposed) {
            return;
        }
        settlePending = true;
        if (ledger.isBusy()) {
            ledger.requestSync();
        } else {
            Platform.runLater(this::sync);
        }
    }

    // Completes an ended transition: CLOSED for the outgoing node, OPENED for
    // the incoming one, then detaches the outgoing node.
    private void settle() {
        settlePending = false;
        transitionId++;
        Node closing = leaving;
        leaving = null;
        StackPane outgoingPage = pages.getSparePage();
        StackPane incomingPage = pages.getCurrentPage();
        outgoingPage.setVisible(false);
        incomingPage.setVisible(true);
        getSkinnable().setTransitioning(false);
        if (closing != null && !announce(RXTransitionPaneEvent.CLOSED, closing)) {
            return;
        }
        if (!announce(RXTransitionPaneEvent.OPENED, shown)) {
            return;
        }
        if (closing != null) {
            outgoingPage.getChildren().remove(closing);
        }
        pages.directCutTo(incomingPage);
    }

    // Fires through the ledger; returns false once this skin has been
    // disposed (a handler may replace the skin), so the caller stops touching
    // the pages and the content nodes.
    private boolean announce(EventType<RXTransitionPaneEvent> type, Node node) {
        if (disposed) {
            return false;
        }
        ledger.announce(this, type, node);
        return !disposed;
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
        disposed = true;
        ledger.release(this);
        pages.dispose(control == null ? null : control.getAnimation());
        if (control != null) {
            control.setTransitioning(false);
        }
        // A replacement skin is constructed before this one is disposed and has
        // already added its nodes: remove only our own.
        getChildren().remove(pages.getContentPane());
    }
}
