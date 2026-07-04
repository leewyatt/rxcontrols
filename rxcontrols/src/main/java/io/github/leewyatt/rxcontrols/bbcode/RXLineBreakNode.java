package io.github.leewyatt.rxcontrols.bbcode;

/**
 * An explicit line break within a paragraph ({@code [br]} or a soft newline).
 */
public record RXLineBreakNode() implements RXBBInlineNode {

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> R accept(RXBBInlineNodeVisitor<R> visitor) {
        return visitor.visitLineBreak(this);
    }
}
