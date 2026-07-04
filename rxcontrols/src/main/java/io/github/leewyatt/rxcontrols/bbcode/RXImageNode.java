package io.github.leewyatt.rxcontrols.bbcode;

import java.util.Objects;

/**
 * An inline image.
 *
 * <p>{@code src} has already passed the image scheme allow-list at parse time.
 * {@code width} and {@code height} come from the optional {@code width} /
 * {@code height} attributes; a value of {@code 0} or negative means "use the
 * natural size" (following the {@code USE_COMPUTED_SIZE} convention).
 *
 * @param src    the validated image source; never {@code null}
 * @param alt    the alternate text, or {@code null} if none was given
 * @param width  the requested display width, or {@code <= 0} for the natural width
 * @param height the requested display height, or {@code <= 0} for the natural height
 * @throws NullPointerException if {@code src} is {@code null}
 */
public record RXImageNode(String src, String alt, double width, double height)
        implements RXBBInlineNode {

    /**
     * Creates an immutable image node.
     */
    public RXImageNode {
        Objects.requireNonNull(src, "src");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> R accept(RXBBInlineNodeVisitor<R> visitor) {
        return visitor.visitImage(this);
    }
}
