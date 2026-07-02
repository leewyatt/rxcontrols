package io.github.leewyatt.rxcontrols;

/**
 * Determines how a smooth scrolling surface behaves when wheel input reaches
 * its scroll boundary.
 */
public enum ScrollBoundaryPolicy {
    /**
     * Let an enclosing scroll surface receive the event when this surface cannot
     * absorb the current wheel input. Target mode also considers the pending
     * smooth target, while momentum and immediate paths use the current offset and
     * input direction.
     */
    CHAIN,

    /**
     * Consume supported wheel input even at a boundary.
     */
    CONTAIN
}
