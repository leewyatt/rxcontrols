package io.github.leewyatt.rxcontrols.layout;

import javafx.css.PseudoClass;

/**
 * Shared breakpoint resolution and {@code :bp-<name>} pseudo-class management for
 * responsive RX layout panes such as {@link RXResponsiveRow} and
 * {@link RXMasonryPane}.
 *
 * <p>Centralizes the pseudo-class prefix and the swap logic so each pane does not
 * reimplement it. Because {@code Node.pseudoClassStateChanged} is protected, the
 * owning pane passes a {@link PseudoClassApplier} bound to its own method
 * reference; this helper only decides when to flip which pseudo-class.</p>
 */
final class RXBreakpointSupport {

    /**
     * Prefix for the active-breakpoint pseudo-class, e.g. {@code :bp-md}.
     */
    static final String PSEUDO_CLASS_PREFIX = "bp-";

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
     * Returns the pseudo-class for a breakpoint, e.g. {@code bp-md} for "md".
     *
     * @param breakpoint the breakpoint
     * @return the pseudo-class
     */
    static PseudoClass pseudoClassFor(RXBreakpoint breakpoint) {
        return PseudoClass.getPseudoClass(PSEUDO_CLASS_PREFIX + breakpoint.getName());
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
