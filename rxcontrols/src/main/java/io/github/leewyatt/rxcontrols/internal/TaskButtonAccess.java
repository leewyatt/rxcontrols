package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.RXTaskButton;
import javafx.beans.property.ReadOnlyBooleanProperty;

import java.util.function.Function;

/**
 * Lets the skin of a task button, which lives in another package, reach
 * what the button keeps to itself: what an activation would do right now,
 * and when that turns into a cancel. Both depend on a timer of the button,
 * so the skin cannot work them out from the public properties.
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
    private static Function<RXTaskButton, ReadOnlyBooleanProperty> cancelReadyProbe;

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

    /**
     * Installs the probe for the cancel readiness. Called once, when the
     * button class is loaded.
     *
     * @param probe returns the property that tells whether an activation of
     *              the given button would cancel its run right now
     */
    public static void setCancelReadyProbe(Function<RXTaskButton, ReadOnlyBooleanProperty> probe) {
        cancelReadyProbe = probe;
    }

    /**
     * Returns whether an activation of the button would cancel its run right
     * now, as a property that follows every change, including the end of the
     * time the pending face is protected from a cancel.
     *
     * @param button the button
     * @return the cancel readiness of the button
     */
    public static ReadOnlyBooleanProperty cancelReadyOf(RXTaskButton button) {
        return cancelReadyProbe.apply(button);
    }
}
