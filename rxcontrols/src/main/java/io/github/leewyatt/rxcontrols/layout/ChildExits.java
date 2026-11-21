package io.github.leewyatt.rxcontrols.layout;

import javafx.animation.Interpolator;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Animated removal of a pane's children: a leaving child is unmanaged at once, so the
 * other children reflow into the space it frees, and fades out drifting up, and it
 * leaves the children list when the fade ends.
 *
 * <p>Removing or unmanaging a child notifies listeners synchronously, and a listener may
 * move the child to another parent, bind its properties or turn animation off. Every
 * step that can reach a listener is followed by a check of what it may have changed.
 * The pane reports each child that really leaves its children list to
 * {@link #removed(Node)}, so one removed from outside mid-exit gets its managed state
 * back.</p>
 */
final class ChildExits {

    private final Pane pane;
    private final PaneRelayoutAnimator animator;
    private final BooleanSupplier animationsActive;
    private final Supplier<Duration> duration;
    private final Supplier<Interpolator> interpolator;
    private final DoubleSupplier exitTranslateY;
    // leaving node -> its managed state before the exit animation, restored on finish
    private final Map<Node, Boolean> leavingNodes = new HashMap<>();

    /**
     * Creates the exits of a pane's children.
     *
     * @param pane             the pane whose children leave
     * @param animator         the pane's animator, which runs the exits
     * @param animationsActive whether the pane animates now
     * @param duration         the exit duration
     * @param interpolator     the exit interpolator
     * @param exitTranslateY   how far a leaving child drifts while it fades, negative upward
     */
    ChildExits(Pane pane, PaneRelayoutAnimator animator, BooleanSupplier animationsActive,
               Supplier<Duration> duration, Supplier<Interpolator> interpolator, DoubleSupplier exitTranslateY) {
        this.pane = pane;
        this.animator = animator;
        this.animationsActive = animationsActive;
        this.duration = duration;
        this.interpolator = interpolator;
        this.exitTranslateY = exitTranslateY;
    }

    /**
     * Removes a child with a fade-out, or at once when the pane does not animate now or
     * the child cannot fade out.
     *
     * @param child the child to remove
     * @return whether the child was a child of the pane
     */
    boolean removeAnimated(Node child) {
        if (child == null || !pane.getChildren().contains(child)) {
            return false;
        }
        if (!animationsActive.getAsBoolean() || !canLeaveAnimated(child)) {
            pane.getChildren().remove(child);
            return true;
        }
        startLeaving(child);
        pane.requestLayout();
        return true;
    }

    /**
     * Removes every child with a fade-out, or at once when the pane does not animate now.
     */
    void clearAnimated() {
        if (!animationsActive.getAsBoolean()) {
            pane.getChildren().clear();
            return;
        }
        // Removing a child notifies listeners synchronously, and one that turns animation
        // off finishes the running exits, which removes children inside that very
        // notification; JavaFX forbids that. So the children that cannot fade out go
        // first, before this call starts any exit.
        List<Node> immediate = new ArrayList<>();
        for (Node child : pane.getChildren()) {
            if (!canLeaveAnimated(child)) {
                immediate.add(child);
            }
        }
        pane.getChildren().removeAll(immediate);
        // Listeners may have moved, bound or removed children or turned animation off,
        // so each remaining child is checked again when it is reached.
        List<Node> late = new ArrayList<>();
        for (Node child : new ArrayList<>(pane.getChildren())) {
            if (child.getParent() != pane || leavingNodes.containsKey(child)) {
                continue;
            }
            if (animationsActive.getAsBoolean() && canLeaveAnimated(child)) {
                startLeaving(child);
            } else {
                late.add(child);
            }
        }
        pane.getChildren().removeAll(late);
        pane.requestLayout();
    }

    /**
     * Returns whether the child is fading out.
     *
     * @param child the child
     * @return whether it is leaving
     */
    boolean isLeaving(Node child) {
        return leavingNodes.containsKey(child);
    }

    /**
     * Notes that a child really left the pane's children list. A child removed from
     * outside mid-exit never reaches the end of its exit, so its original managed state
     * is restored here.
     *
     * @param child the child that left
     */
    void removed(Node child) {
        Boolean wasManaged = leavingNodes.remove(child);
        if (wasManaged != null && !child.managedProperty().isBound()) {
            child.setManaged(wasManaged);
        }
    }

    // Leaving unmanages the child and fades its translate and opacity; a binding on any
    // of them would throw.
    private static boolean canLeaveAnimated(Node child) {
        return !child.managedProperty().isBound() && !child.translateXProperty().isBound()
                && !child.translateYProperty().isBound() && !child.opacityProperty().isBound();
    }

    // Unmanaging the child notifies listeners synchronously; one may move the child to
    // another parent or turn animation off, so both are checked again before the exit
    // starts. A child moved away is no longer this pane's to touch (removed already
    // restored its managed state); one left behind with animation off finishes leaving
    // at once.
    private void startLeaving(Node child) {
        markLeaving(child);
        if (child.getParent() != pane) {
            return;
        }
        if (!animationsActive.getAsBoolean()) {
            finishLeaving(child);
            return;
        }
        animator.runExit(child, true, duration.get(), interpolator.get(),
                exitTranslateY.getAsDouble(), () -> finishLeaving(child));
    }

    // Callers check canLeaveAnimated first: unmanaging a bound managed would throw.
    private void markLeaving(Node child) {
        // Remember the original managed state and unmanage the node so survivors
        // reflow into the freed space; restored verbatim in finishLeaving.
        if (leavingNodes.putIfAbsent(child, child.isManaged()) == null) {
            child.setManaged(false);
        }
    }

    private void finishLeaving(Node child) {
        Boolean wasManaged = leavingNodes.remove(child);
        if (wasManaged != null && !child.managedProperty().isBound()) {
            child.setManaged(wasManaged);
        }
        pane.getChildren().remove(child);
    }
}
