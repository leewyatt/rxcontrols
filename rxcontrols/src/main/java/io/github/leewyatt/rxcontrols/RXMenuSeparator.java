package io.github.leewyatt.rxcontrols;

/**
 * A non-interactive divider between groups of menu items. It extends
 * {@link RXMenuItem} so it shares the {@code ObservableList<RXMenuItem>} item
 * type, but it carries no command semantics: its text, graphic, and action are
 * ignored, and it is never focusable, so keyboard navigation, type-ahead, and
 * assistive technologies skip it.
 */
public final class RXMenuSeparator extends RXMenuItem {

    /**
     * Creates a separator.
     */
    public RXMenuSeparator() {
    }

    /**
     * A separator is never focusable.
     *
     * @return {@code false} always
     */
    @Override
    public boolean isFocusable() {
        return false;
    }

    /**
     * Creates a separator.
     *
     * @return a new separator
     */
    public static RXMenuSeparator create() {
        return new RXMenuSeparator();
    }
}
