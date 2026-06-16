package io.github.leewyatt.rxcontrols.enums;

/**
 * Width state of an {@link io.github.leewyatt.rxcontrols.RXSidebar RXSidebar}.
 * The sidebar is always present; only its width and label visibility change.
 */
public enum SidebarMode {

    /** Icon + text, full {@code expandedWidth}. */
    EXPANDED,

    /** Icon only, narrow {@code miniWidth}; labels hidden, exposed via tooltip. */
    MINI
}
