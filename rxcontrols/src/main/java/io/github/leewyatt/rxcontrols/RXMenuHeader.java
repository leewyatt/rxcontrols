package io.github.leewyatt.rxcontrols;

/**
 * A non-interactive group caption (a subheader / section title) rendered as
 * muted, emphasized text spanning the row. It extends {@link RXMenuItem} to
 * share the {@code ObservableList<RXMenuItem>} item type but carries no command
 * semantics: it has only {@link #textProperty() text}, is never focusable, and
 * assistive technologies skip it.
 */
public final class RXMenuHeader extends RXMenuItem {

    /**
     * Creates a header with no caption.
     */
    public RXMenuHeader() {
    }

    /**
     * Creates a header with the given caption text.
     *
     * @param text the caption text, or {@code null}
     */
    public RXMenuHeader(String text) {
        super(text);
    }

    /**
     * A header is never focusable.
     *
     * @return {@code false} always
     */
    @Override
    public boolean isFocusable() {
        return false;
    }

    /**
     * Creates a header with the given caption text.
     *
     * @param text the caption text, or {@code null}
     * @return a new header
     */
    public static RXMenuHeader of(String text) {
        return new RXMenuHeader(text);
    }
}
