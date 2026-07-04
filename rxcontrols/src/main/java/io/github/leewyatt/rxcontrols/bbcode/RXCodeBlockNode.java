package io.github.leewyatt.rxcontrols.bbcode;

import java.util.Objects;

/**
 * A preformatted code block. {@code content} is stored verbatim and is never
 * re-parsed, so literal BBCode inside it is shown as text.
 *
 * @param content  the raw code content, preserved verbatim; never {@code null}
 * @param language the code language hint, or {@code null} if none was given
 * @throws NullPointerException if {@code content} is {@code null}
 */
public record RXCodeBlockNode(String content, String language) implements RXBBBlockNode {

    /**
     * Creates an immutable code block.
     */
    public RXCodeBlockNode {
        Objects.requireNonNull(content, "content");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> R accept(RXBBBlockNodeVisitor<R> visitor) {
        return visitor.visitCodeBlock(this);
    }
}
