package io.github.leewyatt.rxcontrols.bbcode;

/**
 * Visitor over the inline node hierarchy ({@link RXBBInlineNode}).
 *
 * <p>The set of {@code visit*} methods is exhaustive over the sealed
 * {@link RXBBInlineNode} permits list, so adding a new inline node type is a
 * compile-time error until this interface is updated — the same exhaustiveness a
 * {@code switch} pattern would give, without relying on a preview language
 * feature.
 *
 * @param <R> the type produced by visiting a node
 */
public interface RXBBInlineNodeVisitor<R> {

    /**
     * Visits a plain text run.
     *
     * @param node the text node
     * @return the visit result
     */
    R visitText(RXTextNode node);

    /**
     * Visits an explicit in-paragraph line break.
     *
     * @param node the line-break node
     * @return the visit result
     */
    R visitLineBreak(RXLineBreakNode node);

    /**
     * Visits an inline style scope.
     *
     * @param node the style node
     * @return the visit result
     */
    R visitStyle(RXStyleNode node);

    /**
     * Visits a link.
     *
     * @param node the link node
     * @return the visit result
     */
    R visitLink(RXLinkNode node);

    /**
     * Visits an inline image.
     *
     * @param node the image node
     * @return the visit result
     */
    R visitImage(RXImageNode node);

    /**
     * Visits a literal source echo.
     *
     * @param node the raw-text node
     * @return the visit result
     */
    R visitRawText(RXRawTextNode node);
}
