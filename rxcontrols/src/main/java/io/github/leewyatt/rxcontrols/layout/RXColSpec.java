package io.github.leewyatt.rxcontrols.layout;

import java.util.Objects;

/**
 * Immutable responsive column override for a breakpoint.
 *
 * <p>Unset fields inherit the value resolved from the previous breakpoint or
 * from the column's base property. Use {@link #of(int, int)} with
 * {@code offset=0} to clear an inherited offset at a breakpoint, and use
 * {@link Builder#hidden(boolean)} with {@code false} to clear an inherited
 * hidden state.</p>
 */
public final class RXColSpec {

    private final Integer span;
    private final Integer offset;
    private final Integer order;
    private final Boolean hidden;

    private RXColSpec(Integer span, Integer offset, Integer order, Boolean hidden) {
        validateNonNegative(span, "span");
        validateNonNegative(offset, "offset");
        this.span = span;
        this.offset = offset;
        this.order = order;
        this.hidden = hidden;
    }

    /**
     * Creates a spec that overrides only the span.
     *
     * <p>Offset, order, and hidden remain unset and therefore inherit the
     * previous breakpoint's resolved values. Use {@link #of(int, int)} with
     * {@code offset=0} to clear an inherited offset.</p>
     *
     * @param span the span override
     * @return the spec
     * @throws IllegalArgumentException if {@code span < 0}
     */
    public static RXColSpec of(int span) {
        return new RXColSpec(span, null, null, null);
    }

    /**
     * Creates a spec that overrides span and offset.
     *
     * <p>Order and hidden remain unset and therefore inherit the previous
     * breakpoint's resolved values.</p>
     *
     * @param span   the span override
     * @param offset the offset override
     * @return the spec
     * @throws IllegalArgumentException if either value is negative
     */
    public static RXColSpec of(int span, int offset) {
        return new RXColSpec(span, offset, null, null);
    }

    /**
     * Creates a builder for a partial spec.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the span override.
     *
     * @return the span, or {@code null} to inherit
     */
    public Integer getSpan() {
        return span;
    }

    /**
     * Returns the offset override.
     *
     * @return the offset, or {@code null} to inherit
     */
    public Integer getOffset() {
        return offset;
    }

    /**
     * Returns the visual order override.
     *
     * @return the order, or {@code null} to inherit
     */
    public Integer getOrder() {
        return order;
    }

    /**
     * Returns the hidden override.
     *
     * @return the hidden flag, or {@code null} to inherit
     */
    public Boolean getHidden() {
        return hidden;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RXColSpec other)) {
            return false;
        }
        return Objects.equals(span, other.span)
                && Objects.equals(offset, other.offset)
                && Objects.equals(order, other.order)
                && Objects.equals(hidden, other.hidden);
    }

    @Override
    public int hashCode() {
        return Objects.hash(span, offset, order, hidden);
    }

    @Override
    public String toString() {
        return "RXColSpec[span=" + span
                + ", offset=" + offset
                + ", order=" + order
                + ", hidden=" + hidden
                + "]";
    }

    private static void validateNonNegative(Integer value, String name) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
    }

    /**
     * Builder for {@link RXColSpec}.
     */
    public static final class Builder {

        private Integer span;
        private Integer offset;
        private Integer order;
        private Boolean hidden;

        private Builder() {
        }

        /**
         * Sets the span override.
         *
         * @param span the span
         * @return this builder
         * @throws IllegalArgumentException if {@code span < 0}
         */
        public Builder span(int span) {
            validateNonNegative(span, "span");
            this.span = span;
            return this;
        }

        /**
         * Sets the offset override.
         *
         * @param offset the offset
         * @return this builder
         * @throws IllegalArgumentException if {@code offset < 0}
         */
        public Builder offset(int offset) {
            validateNonNegative(offset, "offset");
            this.offset = offset;
            return this;
        }

        /**
         * Sets the visual order override. Lower values are laid out first.
         *
         * @param order the order
         * @return this builder
         */
        public Builder order(int order) {
            this.order = order;
            return this;
        }

        /**
         * Sets the hidden override.
         *
         * @param hidden whether the column is hidden
         * @return this builder
         */
        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        /**
         * Builds the immutable spec.
         *
         * @return the spec
         */
        public RXColSpec build() {
            return new RXColSpec(span, offset, order, hidden);
        }
    }
}
