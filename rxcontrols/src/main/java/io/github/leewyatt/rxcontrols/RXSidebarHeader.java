package io.github.leewyatt.rxcontrols;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * A section caption between the items of an {@link RXSidebar}. It cannot be
 * focused, activated or selected. In {@link RXSidebar.SidebarMode#MINI}, where
 * there is no room for text, it is drawn as a divider line.
 */
public final class RXSidebarHeader extends RXSidebarItemBase {

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
        setText(text);
    }

    // ==================== Text ====================

    private final StringProperty text = new SimpleStringProperty(this, "text");

    /**
     * The caption text. {@code null} renders as no text.
     *
     * @return the text property
     */
    public final StringProperty textProperty() {
        return text;
    }

    /**
     * @return the text, or {@code null}
     */
    public final String getText() {
        return text.get();
    }

    /**
     * @param value the text, or {@code null}
     */
    public final void setText(String value) {
        text.set(value);
    }
}
