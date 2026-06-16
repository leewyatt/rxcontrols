package io.github.leewyatt.rxcontrols;

import javafx.beans.NamedArg;
import javafx.css.PseudoClass;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Button;

/**
 * An action item: runs its {@code onAction} command on activation and never
 * changes the sidebar selection. It extends {@link Button}, inheriting
 * {@code onAction}, {@code fire()}, keyboard activation, and ARIA; it is not a
 * member of the selection group.
 *
 * <p>{@code onAction} / {@code graphic} / {@code text} / {@code disabled} are
 * inherited from {@link Button} (-&gt; Labeled -&gt; Control).</p>
 */
public final class RXSidebarActionItem extends Button implements RXSidebarItem {

    private static final PseudoClass ACTION_PSEUDO_CLASS = PseudoClass.getPseudoClass("action");

    /**
     * Creates an action item with the given text.
     *
     * @param text the label text, or {@code null}
     */
    public RXSidebarActionItem(@NamedArg("text") String text) {
        super(text);
        init();
    }

    /**
     * Creates an action item with the given text and graphic.
     *
     * @param text    the label text, or {@code null}
     * @param graphic the icon node, or {@code null}
     */
    public RXSidebarActionItem(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
        super(text, graphic);
        init();
    }

    private void init() {
        getStyleClass().add(RXSidebarItem.STYLE_CLASS);
        setAccessibleRole(AccessibleRole.BUTTON);
        pseudoClassStateChanged(ACTION_PSEUDO_CLASS, true);
    }
}
