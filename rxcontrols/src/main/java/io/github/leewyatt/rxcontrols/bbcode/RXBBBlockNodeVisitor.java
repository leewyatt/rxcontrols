package io.github.leewyatt.rxcontrols.bbcode;

/**
 * Visitor over the block node hierarchy ({@link RXBBBlockNode}).
 *
 * <p>The set of {@code visit*} methods is exhaustive over the sealed
 * {@link RXBBBlockNode} permits list, so adding a new block node type is a
 * compile-time error until this interface is updated.
 *
 * @param <R> the type produced by visiting a node
 */
public interface RXBBBlockNodeVisitor<R> {

    /**
     * Visits a paragraph.
     *
     * @param node the paragraph node
     * @return the visit result
     */
    R visitParagraph(RXParagraphNode node);

    /**
     * Visits a block-level heading.
     *
     * @param node the heading node
     * @return the visit result
     */
    R visitHeading(RXHeadingNode node);

    /**
     * Visits a block quote.
     *
     * @param node the quote node
     * @return the visit result
     */
    R visitQuote(RXQuoteNode node);

    /**
     * Visits a preformatted code block.
     *
     * @param node the code-block node
     * @return the visit result
     */
    R visitCodeBlock(RXCodeBlockNode node);

    /**
     * Visits an ordered or unordered list.
     *
     * @param node the list node
     * @return the visit result
     */
    R visitList(RXListNode node);

    /**
     * Visits a table.
     *
     * @param node the table node
     * @return the visit result
     */
    R visitTable(RXTableNode node);

    /**
     * Visits a collapsible spoiler.
     *
     * @param node the spoiler node
     * @return the visit result
     */
    R visitSpoiler(RXSpoilerNode node);

    /**
     * Visits a coloured background block.
     *
     * @param node the background node
     * @return the visit result
     */
    R visitBackground(RXBackgroundNode node);

    /**
     * Visits a horizontal rule.
     *
     * @param node the horizontal-rule node
     * @return the visit result
     */
    R visitHorizontalRule(RXHorizontalRuleNode node);
}
