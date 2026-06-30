package io.github.leewyatt.rxcontrols;

/**
 * Trigger source for decorative button animations: the animation plays
 * forward while the trigger state is active and reverses from the current
 * progress when it turns inactive.
 */
public enum AnimationTrigger {

    /**
     * Animate while the pointer hovers the control.
     */
    HOVER,

    /**
     * Animate while the primary mouse button is held down on the control.
     */
    PRESSED,

    /**
     * No automatic state source; the decoration moves only via programmatic
     * playback such as {@code playAnimation()}.
     */
    NONE
}
