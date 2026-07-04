package io.github.leewyatt.rxcontrols.bbcode;

/**
 * An inline BBCode node: a run of content that flows within a paragraph.
 *
 * <p>Inline nodes are dispatched through {@link RXBBInlineNodeVisitor}, whose
 * {@code visit*} methods are exhaustive over this sealed hierarchy.
 */
public sealed interface RXBBInlineNode extends RXBBNode
        permits RXTextNode, RXLineBreakNode, RXStyleNode, RXLinkNode, RXImageNode, RXRawTextNode {

    /**
     * Dispatches to the matching {@code visit*} method of the given visitor.
     *
     * @param visitor the inline node visitor
     * @param <R>     the visitor result type
     * @return the visitor result
     */
    <R> R accept(RXBBInlineNodeVisitor<R> visitor);
}
