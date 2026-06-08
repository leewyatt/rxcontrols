package io.github.leewyatt.rxcontrols.drawer;

/**
 * The four states of a drawer's open/close lifecycle. This is the single
 * authoritative state of an
 * {@link io.github.leewyatt.rxcontrols.RXDrawerPane}: all open/close queries and
 * the {@code :open} pseudo-class are derived from it, never from the drawer's
 * transient {@code translate} value.
 *
 * <p>The state advances {@code CLOSED → OPENING → OPEN} on open and
 * {@code OPEN → CLOSING → CLOSED} on close. The transient {@code OPENING} /
 * {@code CLOSING} states are entered when an animation starts and resolve to
 * {@code OPEN} / {@code CLOSED} when it finishes (or immediately when animation
 * is disabled).</p>
 */
public enum RXDrawerState {

    /**
     * The drawer is fully closed and pushed off its edge.
     */
    CLOSED,

    /**
     * The drawer is animating from closed towards open.
     */
    OPENING,

    /**
     * The drawer is fully open and resting at its edge.
     */
    OPEN,

    /**
     * The drawer is animating from open towards closed.
     */
    CLOSING
}
