package io.github.leewyatt.rxcontrols.bbcode;

/**
 * A horizontal rule ({@code [hr]}).
 */
public record RXHorizontalRuleNode() implements RXBBBlockNode {

    /**
     * {@inheritDoc}
     */
    @Override
    public <R> R accept(RXBBBlockNodeVisitor<R> visitor) {
        return visitor.visitHorizontalRule(this);
    }
}
