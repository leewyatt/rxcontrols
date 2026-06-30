package io.github.leewyatt.rxcontrols.event;

import javafx.event.Event;
import javafx.event.EventType;

/**
 * Event requesting one programmatic playback of a decorative animation,
 * fired on the host control by methods such as {@code playAnimation()} and
 * {@code playRipple()} and handled by the decoration layer.
 */
public class AnimationEvent extends Event {

    /**
     * Base type for all animation playback events.
     */
    public static final EventType<AnimationEvent> ANY = new EventType<>(Event.ANY, "ANIMATION");

    /**
     * Requests one decoration pulse: forward from the current progress, then
     * convergence back to the current trigger state.
     */
    public static final EventType<AnimationEvent> PLAY_ANIMATION = new EventType<>(ANY, "PLAY_ANIMATION");

    /**
     * Requests one centered ripple (press and immediate release).
     */
    public static final EventType<AnimationEvent> PLAY_RIPPLE = new EventType<>(ANY, "PLAY_RIPPLE");

    /**
     * Creates an animation playback event. Source and target are left unset;
     * firing the event on a node normalizes its target to that node.
     *
     * @param eventType the specific event type
     */
    public AnimationEvent(EventType<AnimationEvent> eventType) {
        super(eventType);
    }
}
