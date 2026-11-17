package io.github.leewyatt.rxcontrols;

/**
 * A divider line between the items of an {@link RXSidebar}. Only its
 * {@link #visibleProperty() visible}, {@link #idProperty() id} and style classes
 * are used; it cannot be focused, activated or selected.
 */
public final class RXSidebarSeparator extends RXSidebarItem {

    /**
     * Creates a separator.
     */
    public RXSidebarSeparator() {
    }

    @Override
    boolean canBeSelected() {
        return false;
    }
}
