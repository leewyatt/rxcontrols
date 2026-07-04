package io.github.leewyatt.rxcontrols.bbcode;

/**
 * A block-level BBCode node: an element that stacks vertically and is rendered
 * as its own independent node.
 *
 * <p>Block nodes are dispatched through {@link RXBBBlockNodeVisitor}, whose
 * {@code visit*} methods are exhaustive over this sealed hierarchy.
 */
public sealed interface RXBBBlockNode extends RXBBNode
        permits RXParagraphNode, RXHeadingNode, RXQuoteNode, RXCodeBlockNode,
                RXListNode, RXTableNode, RXSpoilerNode, RXBackgroundNode,
                RXHorizontalRuleNode {

    /**
     * Dispatches to the matching {@code visit*} method of the given visitor.
     *
     * @param visitor the block node visitor
     * @param <R>     the visitor result type
     * @return the visitor result
     */
    <R> R accept(RXBBBlockNodeVisitor<R> visitor);
}
