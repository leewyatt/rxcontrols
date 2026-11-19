package io.github.leewyatt.rxcontrols.internal.feedback;

import io.github.leewyatt.rxcontrols.RXTaskButton;

import java.util.function.Predicate;

/**
 * Lets the skin of a task button, which lives in another package, ask the
 * one thing the button keeps to itself: whether an activation would do
 * something right now. The answer depends on a timer of the button, so the
 * skin cannot work it out from the public properties.
 */
public final class TaskButtonAccess {

    private static Predicate<RXTaskButton> activationProbe;

    private TaskButtonAccess() {
    }

    /**
     * Installs the probe. Called once, when the button class is loaded.
     *
     * @param probe answers whether an activation of the given button would
     *              reach its action handler or cancel its run
     */
    public static void setActivationProbe(Predicate<RXTaskButton> probe) {
        activationProbe = probe;
    }

    /**
     * Returns whether an activation of the button would do something: reach
     * its action handler, or cancel its run. It is {@code false} while the
     * button would swallow the activation.
     *
     * @param button the button
     * @return {@code true} when an activation would have an effect
     */
    public static boolean isActivationEffective(RXTaskButton button) {
        return activationProbe.test(button);
    }
}
