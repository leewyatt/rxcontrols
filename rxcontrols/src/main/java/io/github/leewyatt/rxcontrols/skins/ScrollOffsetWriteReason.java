package io.github.leewyatt.rxcontrols.skins;

/**
 * Reason for writing a virtual viewport scroll offset.
 */
enum ScrollOffsetWriteReason {
    SMOOTH_FRAME,
    DIRECT_WHEEL,
    SCROLL_BAR,
    PROGRAMMATIC_JUMP
}
