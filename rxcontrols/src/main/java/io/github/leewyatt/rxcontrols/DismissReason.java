package io.github.leewyatt.rxcontrols;

/**
 * Why a snackbar request left an {@link RXSnackbarHost} — delivered to the
 * request's {@code onDismissed} callback and carried by the {@code DISMISSED}
 * {@link io.github.leewyatt.rxcontrols.event.RXSnackbarEvent}.
 *
 * <p>A request that was displayed maps to {@link #PROGRAMMATIC} unless a more
 * specific interactive reason applies ({@link #TIMEOUT} / {@link #ACTION} /
 * {@link #CLOSE_ICON} / {@link #REPLACED}); a request that never displayed maps
 * to {@link #DISCARDED} unless it was superseded by a same-key update
 * ({@link #REPLACED}) or rejected by duplicate prevention ({@link #DUPLICATE}).</p>
 */
public enum DismissReason {

    /** The auto-hide duration elapsed. */
    TIMEOUT,

    /** The action button was activated. */
    ACTION,

    /** The close icon was activated (mouse or keyboard). */
    CLOSE_ICON,

    /**
     * Dismissed through the API ({@code dismiss()} / {@code dismiss(key)} /
     * {@code clear()} on the displayed request, or ESC), or the host left its scene
     * while the request was displayed.
     */
    PROGRAMMATIC,

    /**
     * Superseded by another request: preempted by the {@code REPLACE} strategy
     * while displayed, or updated in place by a request carrying the same key.
     */
    REPLACED,

    /**
     * Removed without ever being displayed: dropped by queue overflow, cleared
     * from the queue, discarded when the host left its scene, or never accepted
     * because the owner had no live scene.
     */
    DISCARDED,

    /** Rejected by duplicate prevention; the request was never displayed. */
    DUPLICATE
}
