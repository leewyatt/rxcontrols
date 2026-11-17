package io.github.leewyatt.rxcontrols;

/**
 * A section caption between the items of an {@link RXSidebar}. Only its
 * {@link #textProperty() text}, {@link #visibleProperty() visible},
 * {@link #idProperty() id} and style classes are used; it cannot be focused,
 * activated or selected. In {@link RXSidebar.SidebarMode#MINI}, where there is
 * no room for text, it is drawn as a divider line.
 */
public final class RXSidebarHeader extends RXSidebarItem {

    /**
     * Creates a caption without text.
     */
    public RXSidebarHeader() {
    }

    /**
     * Creates a caption with the given text.
     *
     * @param text the caption text, or {@code null}
     */
    public RXSidebarHeader(String text) {
        super(text);
    }

    @Override
    boolean canBeSelected() {
        return false;
    }
}
