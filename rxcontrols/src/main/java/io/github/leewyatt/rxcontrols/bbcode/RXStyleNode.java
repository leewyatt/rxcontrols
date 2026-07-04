package io.github.leewyatt.rxcontrols.bbcode;

import java.util.List;
import java.util.Objects;

/**
 * A single node carrying one inline text style over its children.
 *
 * <p>For the switch-style variants ({@link RXStyleType#BOLD}, {@code ITALIC},
 * {@code UNDERLINE}, {@code STRIKETHROUGH}) {@code value} is {@code null}; for
 * {@link RXStyleType#COLOR}, {@code SIZE}, and {@code FONT} it is the
 * already-validated colour / size / family string produced at parse time.
 *
 * @param type     the style variant; never {@code null}
 * @param value    the validated style value, or {@code null} for switch-style variants
 * @param children the styled inline children; never {@code null}
 * @throws NullPointerException if {@code type} or {@code children} is {@code null}
 */
public record RXStyleNode(RXStyleType type, String value, List<RXBBInlineNode> children)
        implements RXBBInlineNode {

    /**
     * Creates an immutable style node.
     */
    public RXStyleNode {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> R accept(RXBBInlineNodeVisitor<R> visitor) {
        return visitor.visitStyle(this);
    }
}
