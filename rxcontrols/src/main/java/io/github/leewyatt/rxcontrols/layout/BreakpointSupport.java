package io.github.leewyatt.rxcontrols.layout;

import javafx.css.PseudoClass;

/**
 * Shared breakpoint resolution and {@code :<name>} pseudo-class management for
 * responsive RX layout panes such as {@link RXRow} and
 * {@link RXMasonryPane}.
 *
 * <p>Centralizes the swap logic so each pane does not reimplement it. Because
 * {@code Node.pseudoClassStateChanged} is protected, the owning pane passes a
 * {@link PseudoClassApplier} bound to its own method reference; this helper only
 * decides when to flip which pseudo-class.</p>
 *
 * <p>The pseudo-class is the tier's lowercase name (e.g. {@code :md}); the fixed
 * {@link RXBreakpoint} names never collide with JavaFX built-in pseudo-classes.</p>
 */
final class BreakpointSupport {

    /**
     * Applies a pseudo-class state on the owning node.
     */
    @FunctionalInterface
    interface PseudoClassApplier {

        /**
         * Sets or clears a pseudo-class on the owning node.
         *
         * @param pseudoClass the pseudo-class
         * @param active      whether the pseudo-class is active
         */
        void apply(PseudoClass pseudoClass, boolean active);
    }

    private PseudoClass activePseudoClass;

    /**
     * Returns the pseudo-class for a breakpoint, e.g. {@code md} for {@link RXBreakpoint#MD}.
     *
     * @param breakpoint the breakpoint
     * @return the pseudo-class
     */
    static PseudoClass pseudoClassFor(RXBreakpoint breakpoint) {
        return PseudoClass.getPseudoClass(breakpoint.cssName());
    }

    /**
     * Resolves the active breakpoint for {@code width} and swaps the pseudo-class
     * on the owning node when it changes.
     *
     * @param profile  the breakpoint profile
     * @param width    the width to resolve against
     * @param applier  applies the pseudo-class state on the owning node
     * @return the resolved breakpoint
     */
    RXBreakpoint update(RXBreakpointProfile profile, double width, PseudoClassApplier applier) {
        RXBreakpoint breakpoint = profile.resolve(width);
        PseudoClass next = pseudoClassFor(breakpoint);
        if (next != activePseudoClass) {
            if (activePseudoClass != null) {
                applier.apply(activePseudoClass, false);
            }
            applier.apply(next, true);
            activePseudoClass = next;
        }
        return breakpoint;
    }
}
