package io.github.leewyatt.rxcontrols.skins;

/**
 * Reason for writing a virtual viewport scroll offset.
 *
 * <p>V1 keeps the reason at the call sites, but {@link RXVirtualViewportBase}
 * still treats all changed offsets as explicit scrolls. Anchor/reflow
 * reconciliation needs a separate mechanism before {@code SMOOTH_FRAME} can be
 * safely distinguished from direct user writes.
 */
enum ScrollOffsetWriteReason {
    SMOOTH_FRAME,
    DIRECT_WHEEL,
    SCROLL_BAR,
    PROGRAMMATIC_JUMP
}
