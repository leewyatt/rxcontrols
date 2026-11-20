package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.RXTaskButton;

import java.util.function.Function;

/**
 * Lets the skin of a task button, which lives in another package, ask the
 * one thing the button keeps to itself: what an activation would do right
 * now. The answer depends on a timer of the button, so the skin cannot work
 * it out from the public properties.
 */
public final class TaskButtonAccess {

    /**
     * What an activation of a task button does.
     */
    public enum Activation {
        /** It reaches the action handler. */
        ACTION,
        /** It cancels the run in flight. */
        CANCEL
    }

    private static Function<RXTaskButton, Activation> activationProbe;

    private TaskButtonAccess() {
    }

    /**
     * Installs the probe. Called once, when the button class is loaded.
     *
     * @param probe answers what an activation of the given button would do,
     *              {@code null} while the button would swallow it
     */
    public static void setActivationProbe(Function<RXTaskButton, Activation> probe) {
        activationProbe = probe;
    }

    /**
     * Returns what an activation of the button would do right now.
     *
     * @param button the button
     * @return what an activation would do, or {@code null} while the button
     *         would swallow it
     */
    public static Activation activationOf(RXTaskButton button) {
        return activationProbe.apply(button);
    }
}
