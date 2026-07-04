package io.github.leewyatt.rxcontrols.bbcode;

/**
 * Root of the immutable BBCode abstract syntax tree.
 *
 * <p>Every node is either an {@link RXBBInlineNode} (rendered into a
 * {@code TextFlow}) or an {@link RXBBBlockNode} (rendered as an independent
 * block-level node). The hierarchy is {@code sealed} so the two renderer
 * visitors stay exhaustive at compile time.
 */
public sealed interface RXBBNode permits RXBBInlineNode, RXBBBlockNode {
}
