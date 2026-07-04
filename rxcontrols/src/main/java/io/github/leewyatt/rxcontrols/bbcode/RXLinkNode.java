package io.github.leewyatt.rxcontrols.bbcode;

import java.util.List;
import java.util.Objects;

/**
 * A link over its inline children.
 *
 * <p>{@code href} has already passed the scheme allow-list and been sanitized at
 * parse time; a link node is only produced when validation succeeds, so this
 * node never carries an unsafe URL. Email links carry a normalized
 * {@code mailto:} href with {@link RXLinkKind#EMAIL}.
 *
 * @param href     the validated link target; never {@code null}
 * @param kind     the link kind; never {@code null}
 * @param children the link's inline children; never {@code null}
 * @throws NullPointerException if {@code href}, {@code kind}, or {@code children} is {@code null}
 */
public record RXLinkNode(String href, RXLinkKind kind, List<RXBBInlineNode> children)
        implements RXBBInlineNode {

    /**
     * Creates an immutable link node.
     */
    public RXLinkNode {
        Objects.requireNonNull(href, "href");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> R accept(RXBBInlineNodeVisitor<R> visitor) {
        return visitor.visitLink(this);
    }
}
