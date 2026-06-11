package io.github.leewyatt.rxcontrols.enums;

/**
 * Trigger source for decorative button animations: the animation plays
 * forward while the trigger state is active and reverses from the current
 * progress when it turns inactive.
 */
public enum RXAnimationTrigger {

    /**
     * Animate while the pointer hovers the control.
     */
    HOVER,

    /**
     * Animate while the primary mouse button is held down on the control.
     */
    PRESSED
}
