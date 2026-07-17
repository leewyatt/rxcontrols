package io.github.leewyatt.rxcontrols;

import javafx.beans.NamedArg;
import javafx.css.PseudoClass;
import javafx.scene.Node;

/**
 * A navigation item: participates in the sidebar's mutually-exclusive selection.
 * It extends {@link RXRadioToggleButton}, whose radio-like behaviour keeps one
 * destination always active — re-clicking the selected item does NOT clear it
 * (unlike a plain toggle) — while keeping a flat ToggleButton appearance. The
 * sidebar adds it to an internal selection group; activating it becomes the
 * sidebar's {@link RXSidebar#selectedItemProperty() selectedItem}. To select it
 * programmatically use {@link RXSidebar#selectItem(RXSidebarNavItem)}.
 *
 * <p>{@code selected} / {@code graphic} / {@code text} / {@code disabled} are
 * inherited (ToggleButton -&gt; Labeled -&gt; Control); the {@code :selected}
 * pseudo-class is maintained by {@code ToggleButton}.</p>
 */
public final class RXSidebarNavItem extends RXRadioToggleButton implements RXSidebarItem {

    private static final PseudoClass NAV_PSEUDO_CLASS = PseudoClass.getPseudoClass("nav");

    /**
     * Creates a navigation item with the given text.
     *
     * @param text the label text, or {@code null}
     */
    public RXSidebarNavItem(@NamedArg("text") String text) {
        super(text);
        init();
    }

    /**
     * Creates a navigation item with the given text and graphic.
     *
     * @param text    the label text, or {@code null}
     * @param graphic the icon node, or {@code null}
     */
    public RXSidebarNavItem(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
        super(text, graphic);
        init();
    }

    private void init() {
        getStyleClass().add(RXSidebarItem.STYLE_CLASS);
        // AccessibleRole.RADIO_BUTTON is already set by RXRadioToggleButton.
        pseudoClassStateChanged(NAV_PSEUDO_CLASS, true);
    }
}
