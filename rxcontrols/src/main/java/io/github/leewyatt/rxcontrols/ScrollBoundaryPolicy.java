package io.github.leewyatt.rxcontrols;

/**
 * Determines how a smooth scrolling surface behaves when wheel input reaches
 * its scroll boundary.
 */
public enum ScrollBoundaryPolicy {
    /**
     * Let an enclosing scroll surface receive the event only when both the
     * current offset and the pending smooth target are already at the boundary
     * in the input direction.
     */
    CHAIN,

    /**
     * Consume supported wheel input even at a boundary.
     */
    CONTAIN
}
